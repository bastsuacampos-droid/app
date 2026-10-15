package cl.sacyr.rutaitata.excavaciones.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import cl.sacyr.rutaitata.excavaciones.dominio.FotoBorrador
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Fotos de las excavaciones en files/fotos (privadas de la app; se respaldan con ella).
 *
 * Toda foto que entra (cámara o galería) se normaliza: se gira según su EXIF, se reduce a
 * [LADO_MAXIMO] px y se guarda como JPEG. Así ocupa ~300-600 KB en vez de varios MB, se ve
 * derecha en cualquier visor y el reporte no tiene que interpretar EXIF.
 */
class ArchivosFotos(val carpeta: File, private val zona: ZoneId = ZoneId.systemDefault()) {

    /** Fotos de la cámara a medio procesar (la app de cámara escribe aquí vía FileProvider). */
    private val capturas = File(carpeta, "capturas")

    fun archivo(nombre: String) = File(carpeta, nombre)

    /** Archivo vacío donde la app de cámara dejará la foto. */
    fun nuevaCaptura(): File {
        capturas.mkdirs()
        return File(capturas, "${UUID.randomUUID()}.jpg")
    }

    /** Procesa la foto que dejó la cámara en [captura] y la borra. null si la cámara no guardó nada. */
    fun importarCaptura(captura: File, ahora: Long = System.currentTimeMillis()): FotoBorrador? = try {
        if (captura.length() == 0L) {
            null
        } else {
            // De la cámara: si el EXIF no trae la hora, es "ahora".
            importar({ captura.inputStream() }) ?.let { it.copy(tomadaEn = it.tomadaEn ?: ahora) }
        }
    } finally {
        captura.delete()
    }

    /**
     * Copia y normaliza una foto. [abrir] debe entregar un flujo nuevo en cada llamada (se lee dos
     * veces: EXIF e imagen). La fecha sale del EXIF; si no tiene, queda null.
     */
    fun importar(abrir: () -> InputStream?): FotoBorrador? {
        val exif = abrir()?.use { runCatching { ExifInterface(it) }.getOrNull() } ?: return null
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        abrir()?.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

        var muestreo = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (muestreo * 2) >= LADO_MAXIMO) muestreo *= 2
        val leida = abrir()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestreo }) }
            ?: return null
        val final = orientar(reducir(leida), exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))

        carpeta.mkdirs()
        val destino = File(carpeta, "${UUID.randomUUID()}.jpg")
        try {
            destino.outputStream().use { if (!final.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, it)) throw IOException("No se pudo guardar la foto") }
        } catch (e: IOException) {
            destino.delete()
            throw e
        } finally {
            final.recycle()
        }
        return FotoBorrador(destino.name, tomadaEn = fechaExif(exif))
    }

    /** Ancho y alto en píxeles, o null si el archivo no existe o no es una imagen. */
    fun medidas(nombre: String): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo(nombre).path, o)
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    }

    /** Miniatura de alrededor de [lado] px para mostrar en pantalla. */
    fun miniatura(nombre: String, lado: Int): Bitmap? {
        val (w, h) = medidas(nombre) ?: return null
        var muestreo = 1
        while (minOf(w, h) / (muestreo * 2) >= lado) muestreo *= 2
        return BitmapFactory.decodeFile(archivo(nombre).path, BitmapFactory.Options().apply { inSampleSize = muestreo })
    }

    fun eliminar(nombres: Collection<String>) {
        nombres.forEach { archivo(it).delete() }
    }

    /**
     * Borra fotos que ningún registro usa (de formularios abandonados) y capturas a medias. Solo
     * las de más de un día: una foto recién tomada puede ser de un formulario aún abierto.
     */
    fun limpiarHuerfanas(enUso: Set<String>, ahora: Long = System.currentTimeMillis()) {
        val limite = ahora - UN_DIA_MS
        carpeta.listFiles()?.filter { it.isFile && it.name !in enUso && it.lastModified() < limite }?.forEach { it.delete() }
        capturas.listFiles()?.filter { it.lastModified() < limite }?.forEach { it.delete() }
    }

    private fun reducir(b: Bitmap): Bitmap {
        val lado = maxOf(b.width, b.height)
        if (lado <= LADO_MAXIMO) return b
        val f = LADO_MAXIMO.toFloat() / lado
        return b.scale((b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1))
            .also { if (it !== b) b.recycle() }
    }

    private fun orientar(b: Bitmap, orientacion: Int): Bitmap {
        val m = Matrix()
        when (orientacion) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return b
        }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true).also { if (it !== b) b.recycle() }
    }

    /** "2026:10:15 10:32:05" (hora local del teléfono que tomó la foto). */
    private fun fechaExif(exif: ExifInterface): Long? {
        val texto = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME) ?: return null
        return runCatching { LocalDateTime.parse(texto.trim(), FORMATO_EXIF).atZone(zona).toInstant().toEpochMilli() }.getOrNull()
    }

    companion object {
        const val LADO_MAXIMO = 1600
        const val CALIDAD_JPEG = 85
        private const val UN_DIA_MS = 24 * 60 * 60 * 1000L
        private val FORMATO_EXIF = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    }
}
