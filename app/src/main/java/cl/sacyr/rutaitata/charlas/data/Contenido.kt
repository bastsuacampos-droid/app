package cl.sacyr.rutaitata.charlas.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface EstadoActualizacion {
    data object SinRevisar : EstadoActualizacion
    data object Buscando : EstadoActualizacion
    data object AlDia : EstadoActualizacion
    data class Actualizado(val version: Int) : EstadoActualizacion
    data class Error(val mensaje: String) : EstadoActualizacion
}

/**
 * Banco de charlas vigente. Parte con el que viene dentro de la app (o el último
 * descargado, si es más nuevo) y se actualiza descargando [urlRemota].
 * Un banco descargado solo se aplica si su versión es mayor y pasa la validación.
 */
class Contenido(context: Context, private val urlRemota: String) {

    private val assets = context.assets
    private val cache = File(context.filesDir, ARCHIVO)

    var banco by mutableStateOf(cargarLocal())
        private set

    var estado by mutableStateOf<EstadoActualizacion>(EstadoActualizacion.SinRevisar)
        private set

    /** Banco recién recibido cuyas novedades aún no ve el capataz. */
    var novedadPendiente by mutableStateOf<Banco?>(null)
        private set

    fun descartarNovedad() {
        novedadPendiente = null
    }

    private var ultimaRevision = 0L

    /** Revisión automática al volver a la app; como máximo una vez cada [INTERVALO_MS]. */
    suspend fun revisarSiCorresponde() {
        if (System.currentTimeMillis() - ultimaRevision < INTERVALO_MS) return
        buscarActualizacion()
    }

    suspend fun buscarActualizacion() {
        if (estado == EstadoActualizacion.Buscando) return
        estado = EstadoActualizacion.Buscando
        ultimaRevision = System.currentTimeMillis()
        estado = try {
            val texto = withContext(Dispatchers.IO) { descargar() }
            val remoto = BancoParser.parse(texto)
            val errores = BancoParser.validar(remoto)
            when {
                errores.isNotEmpty() -> EstadoActualizacion.Error("El banco publicado tiene errores: ${errores.first()}")
                remoto.version <= banco.version -> EstadoActualizacion.AlDia
                else -> {
                    // Si no se puede guardar, igual se usa en esta sesión y se vuelve a bajar la próxima vez.
                    withContext(Dispatchers.IO) { runCatching { guardar(texto) } }
                    banco = remoto
                    novedadPendiente = remoto
                    EstadoActualizacion.Actualizado(remoto.version)
                }
            }
        } catch (e: SinPublicacion) {
            EstadoActualizacion.Error("No hay un banco publicado en el servidor (HTTP ${e.codigo})")
        } catch (e: java.io.IOException) {
            EstadoActualizacion.Error("Sin conexión con el servidor de actualizaciones")
        } catch (e: Exception) {
            EstadoActualizacion.Error("No se pudo leer el banco publicado")
        }
    }

    private fun cargarLocal(): Banco {
        val incluido = BancoParser.parse(assets.open(ARCHIVO).bufferedReader().use { it.readText() })
        val descargado = runCatching { BancoParser.parse(cache.readText()) }
            .getOrNull()
            ?.takeIf { BancoParser.validar(it).isEmpty() }
        // Si una versión nueva de la app trae un banco más reciente, el de la app manda.
        return if (descargado != null && descargado.version > incluido.version) descargado else incluido
    }

    private fun descargar(): String {
        val conexion = URL(urlRemota).openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 10_000
            conexion.readTimeout = 15_000
            conexion.useCaches = false
            conexion.setRequestProperty("Cache-Control", "no-cache")
            if (conexion.responseCode != HttpURLConnection.HTTP_OK) {
                throw SinPublicacion(conexion.responseCode)
            }
            return conexion.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conexion.disconnect()
        }
    }

    private fun guardar(texto: String) {
        val temporal = File(cache.parentFile, "$ARCHIVO.tmp")
        temporal.writeText(texto)
        check(temporal.renameTo(cache)) { "No se pudo guardar el banco descargado" }
    }

    private class SinPublicacion(val codigo: Int) : java.io.IOException("HTTP $codigo")

    private companion object {
        const val ARCHIVO = "charlas.json"
        const val INTERVALO_MS = 30 * 60 * 1000L
    }
}
