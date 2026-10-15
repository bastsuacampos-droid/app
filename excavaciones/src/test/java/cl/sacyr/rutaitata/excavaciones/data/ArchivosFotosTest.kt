package cl.sacyr.rutaitata.excavaciones.data

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import cl.sacyr.rutaitata.excavaciones.dominio.Formato
import cl.sacyr.rutaitata.excavaciones.dominio.OrigenFoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArchivosFotosTest {

    private val raiz = ApplicationProvider.getApplicationContext<android.content.Context>().filesDir
    private val fotos = ArchivosFotos(File(raiz, "fotos"), zona = ZoneOffset.UTC)

    private fun jpeg(ancho: Int, alto: Int, orientacion: Int? = null, fecha: String? = null): File {
        val f = File(raiz, "origen-${ancho}x$alto.jpg")
        val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF8D6E63.toInt()) }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (orientacion != null || fecha != null) {
            ExifInterface(f).apply {
                orientacion?.let { setAttribute(ExifInterface.TAG_ORIENTATION, it.toString()) }
                fecha?.let { setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, it) }
                saveAttributes()
            }
        }
        return f
    }

    @Test
    fun giraSegunExifYGuardaLaFechaDeLaFoto() {
        val origen = jpeg(400, 200, ExifInterface.ORIENTATION_ROTATE_90, "2026:10:15 10:32:05")
        val foto = fotos.importar { origen.inputStream() }!!
        assertEquals(200 to 400, fotos.medidas(foto.archivo))
        assertEquals(LocalDateTime.of(2026, 10, 15, 10, 32, 5).toInstant(ZoneOffset.UTC).toEpochMilli(), foto.tomadaEn)
        assertTrue(origen.exists()) // el original no se toca
    }

    @Test
    fun reduceLasFotosGrandes() {
        val foto = fotos.importar { jpeg(4000, 3000).inputStream() }!!
        val (w, h) = fotos.medidas(foto.archivo)!!
        assertEquals(ArchivosFotos.LADO_MAXIMO, maxOf(w, h))
        assertEquals(4f / 3f, w.toFloat() / h, 0.01f)
        assertNull(foto.tomadaEn)
    }

    @Test
    fun capturaDeCamaraSinExifTomaLaHoraActualYSeBorra() {
        val captura = fotos.nuevaCaptura()
        jpeg(300, 300).copyTo(captura, overwrite = true)
        val foto = fotos.importarCaptura(captura, ahora = 123_456L)!!
        assertEquals(123_456L, foto.tomadaEn)
        assertFalse(captura.exists())
        // La cámara cancelada deja el archivo vacío: no es foto.
        assertNull(fotos.importarCaptura(fotos.nuevaCaptura().apply { writeBytes(ByteArray(0)) }))
    }

    @Test
    fun estampaElSelloAlPieYGuardaElOrigen() {
        val origen = jpeg(800, 600, fecha = "2026:10:15 10:32:05")
        var fechaRecibida: Long? = null
        val sello = { t: Long? ->
            fechaRecibida = t
            "Galería · 15-10-2026 10:32"
        }
        val foto = fotos.importar(OrigenFoto.GALERIA, sello = sello) { origen.inputStream() }!!
        assertEquals(OrigenFoto.GALERIA, foto.origen)
        assertEquals(foto.tomadaEn, fechaRecibida)
        val bmp = android.graphics.BitmapFactory.decodeFile(fotos.archivo(foto.archivo).path)
        assertEquals(800 to 600, bmp.width to bmp.height)
        // La franja del sello oscurece el pie; arriba la foto queda igual.
        val pie = bmp.getPixel(400, 595)
        val arriba = bmp.getPixel(400, 10)
        assertTrue(android.graphics.Color.red(pie) < android.graphics.Color.red(arriba) - 40)
    }

    @Test
    fun selloConFechaOrigenYCoordenadas() {
        val t = LocalDateTime.of(2026, 10, 15, 10, 32)
        assertEquals("15-10-2026 10:32 · -36.61234, -72.10345", Formato.selloFoto(t, OrigenFoto.CAMARA, -36.61234, -72.10345))
        assertEquals("Galería · Fecha desconocida", Formato.selloFoto(null, OrigenFoto.GALERIA, null, null))
    }

    @Test
    fun algoQueNoEsImagenNoSeImporta() {
        assertNull(fotos.importar { "no es una foto".byteInputStream() })
    }

    @Test
    fun limpiaSoloLasHuerfanasAntiguas() {
        val usada = fotos.importar { jpeg(100, 100).inputStream() }!!.archivo
        val vieja = fotos.importar { jpeg(100, 100).inputStream() }!!.archivo
        val reciente = fotos.importar { jpeg(100, 100).inputStream() }!!.archivo
        val ahora = System.currentTimeMillis()
        listOf(usada, vieja).forEach { fotos.archivo(it).setLastModified(ahora - 3 * 24 * 3600_000L) }

        fotos.limpiarHuerfanas(enUso = setOf(usada), ahora = ahora)

        assertTrue(fotos.archivo(usada).exists())
        assertFalse(fotos.archivo(vieja).exists())
        assertTrue(fotos.archivo(reciente).exists()) // puede ser de un formulario abierto
    }
}
