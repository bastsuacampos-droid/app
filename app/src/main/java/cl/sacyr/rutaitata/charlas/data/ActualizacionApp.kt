package cl.sacyr.rutaitata.charlas.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Versión de la app publicada en GitHub Releases. */
data class VersionPublicada(
    val version: String,
    val notas: String,
    val urlApk: String,
    val bytes: Long,
)

sealed interface EstadoApp {
    data object SinRevisar : EstadoApp
    data object Buscando : EstadoApp
    data object AlDia : EstadoApp
    /** El repositorio todavía no tiene ninguna release de esta app. */
    data object SinPublicaciones : EstadoApp
    data class Disponible(val version: VersionPublicada) : EstadoApp
    data class Descargando(val version: VersionPublicada, val avance: Float) : EstadoApp
    /** Descargada; falta que el capataz confirme en el instalador de Android. */
    data class Lista(val version: VersionPublicada, val apk: File) : EstadoApp
    data class Error(val mensaje: String) : EstadoApp
}

object Versiones {

    /** Prefijo de los tags de esta app; el repositorio también publica releases de otras apps. */
    const val PREFIJO_TAG = "charlas-v"
    private const val PREFIJO_APK = "charlas-ruta-itata"

    /**
     * Lee la lista de releases de la API de GitHub y devuelve la versión más nueva de esta app.
     * Ignora borradores, versiones de prueba, releases de otras apps y las que no traen la APK.
     */
    fun leerReleases(json: String): VersionPublicada? = runCatching {
        val lista = JSONArray(json)
        (0 until lista.length()).map { lista.getJSONObject(it) }
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
            .filter { it.optString("tag_name").startsWith(PREFIJO_TAG) }
            .mapNotNull(::leerRelease)
            .reduceOrNull { a, b -> if (esMasNueva(b.version, a.version)) b else a }
    }.getOrNull()

    private fun leerRelease(o: JSONObject): VersionPublicada? {
        val assets = o.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").let { n -> n.startsWith(PREFIJO_APK) && n.endsWith(".apk") } }
            ?: return null
        return VersionPublicada(
            version = o.getString("tag_name").removePrefix(PREFIJO_TAG),
            notas = o.optString("body").trim(),
            urlApk = apk.getString("browser_download_url"),
            bytes = apk.optLong("size"),
        )
    }

    /** true si [remota] ("1.10") es posterior a [actual] ("1.9"); compara número por número. */
    fun esMasNueva(remota: String, actual: String): Boolean {
        val r = remota.split('.').map { it.toIntOrNull() ?: 0 }
        val a = actual.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, a.size)) {
            val diferencia = r.getOrElse(i) { 0 } - a.getOrElse(i) { 0 }
            if (diferencia != 0) return diferencia > 0
        }
        return false
    }
}

/**
 * Busca versiones nuevas de la app, descarga la APK y abre el instalador de Android.
 * Android solo acepta la actualización si viene firmada con la misma clave que la app instalada.
 */
class ActualizadorApp(
    private val context: Context,
    private val urlVersiones: String,
    val versionActual: String,
) {
    var estado by mutableStateOf<EstadoApp>(EstadoApp.SinRevisar)
        private set

    private var ultimaRevision = 0L

    suspend fun revisarSiCorresponde() {
        if (System.currentTimeMillis() - ultimaRevision < INTERVALO_MS) return
        buscar()
    }

    suspend fun buscar() {
        if (estado is EstadoApp.Buscando || estado is EstadoApp.Descargando) return
        estado = EstadoApp.Buscando
        ultimaRevision = System.currentTimeMillis()
        estado = try {
            val publicada = withContext(Dispatchers.IO) { leerUltima() }
            when {
                publicada == null -> EstadoApp.SinPublicaciones
                Versiones.esMasNueva(publicada.version, versionActual) -> EstadoApp.Disponible(publicada)
                else -> EstadoApp.AlDia
            }
        } catch (e: IOException) {
            EstadoApp.Error("No se pudo consultar si hay una versión nueva de la app")
        }
    }

    suspend fun descargar(version: VersionPublicada) {
        estado = EstadoApp.Descargando(version, 0f)
        estado = try {
            val apk = withContext(Dispatchers.IO) {
                descargarApk(version) { avance -> estado = EstadoApp.Descargando(version, avance) }
            }
            EstadoApp.Lista(version, apk)
        } catch (e: IOException) {
            EstadoApp.Error("La descarga no terminó. Revisa la señal y vuelve a intentarlo")
        }
    }

    /** Android pide una vez permiso para que esta app pueda instalar actualizaciones. */
    fun puedeInstalar(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun pedirPermisoInstalacion() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun instalar(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun leerUltima(): VersionPublicada? {
        val conexion = abrir(urlVersiones)
        try {
            conexion.setRequestProperty("Accept", "application/vnd.github+json")
            if (conexion.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conexion.responseCode}")
            return Versiones.leerReleases(conexion.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conexion.disconnect()
        }
    }

    private fun descargarApk(version: VersionPublicada, onAvance: (Float) -> Unit): File {
        val carpeta = File(context.cacheDir, CARPETA).apply { mkdirs() }
        carpeta.listFiles()?.forEach { it.delete() }
        val destino = File(carpeta, "charlas-ruta-itata-${version.version}.apk")
        val conexion = abrir(version.urlApk)
        try {
            if (conexion.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conexion.responseCode}")
            val total = conexion.contentLengthLong.takeIf { it > 0 } ?: version.bytes
            var leidos = 0L
            var ultimoAviso = 0L
            conexion.inputStream.use { entrada ->
                destino.outputStream().use { salida ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = entrada.read(buffer)
                        if (n < 0) break
                        salida.write(buffer, 0, n)
                        leidos += n
                        if (total > 0 && leidos - ultimoAviso > 256 * 1024) {
                            ultimoAviso = leidos
                            onAvance(leidos.toFloat() / total)
                        }
                    }
                }
            }
            if (total > 0 && leidos != total) throw IOException("Descarga incompleta")
            return destino
        } catch (e: IOException) {
            destino.delete()
            throw e
        } finally {
            conexion.disconnect()
        }
    }

    private fun abrir(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CharlasRutaItata/$versionActual")
        }

    private companion object {
        const val CARPETA = "actualizaciones"
        const val INTERVALO_MS = 30 * 60 * 1000L
    }
}
