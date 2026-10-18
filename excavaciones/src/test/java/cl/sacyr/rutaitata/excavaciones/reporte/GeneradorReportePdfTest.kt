package cl.sacyr.rutaitata.excavaciones.reporte

import android.graphics.Bitmap
import cl.sacyr.rutaitata.excavaciones.dominio.ejemplo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.InflaterInputStream
import kotlin.random.Random

/**
 * Genera PDFs reales y revisa su estructura: fotos JPEG intactas, fuentes y referencias. El
 * generador es Kotlin puro; Robolectric solo se usa para crear los JPEG de prueba con Bitmap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeneradorReportePdfTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val fuentes = FuentesPdf(fuente("Roboto-Regular"), fuente("Roboto-Bold"))
    private val hoy = LocalDate.of(2026, 10, 15)

    private fun fuente(nombre: String) = FuenteTrueType(File("src/main/assets/fuentes/$nombre.ttf").readBytes(), nombre)

    /** JPEG con ruido, como una foto de terreno: el ruido no se comprime y deja ver si se recodifica. */
    private fun jpeg(ancho: Int, alto: Int, semilla: Int): File {
        val azar = Random(semilla)
        val pixeles = IntArray(ancho * alto) { i ->
            val x = i % ancho
            val y = i / ancho
            (0xFF shl 24) or ((x * 255 / ancho) shl 16 or ((y * 255 / alto) shl 8) or 0x60) xor azar.nextInt(0x202020)
        }
        val bmp = Bitmap.createBitmap(pixeles, ancho, alto, Bitmap.Config.ARGB_8888)
        return carpeta.newFile("foto$semilla.jpg").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
    }

    private fun datos(fotos: Map<Long, List<FotoReporte>>, lista: List<cl.sacyr.rutaitata.excavaciones.dominio.ExcavacionDetalle>) = DatosReporte(
        desde = hoy,
        responsable = "Juan Pérez Ñuñez",
        emitidoEn = LocalDateTime.of(2026, 10, 15, 18, 30),
        excavaciones = lista,
        incluirFotos = true,
        fotos = fotos,
    )

    private fun generar(datos: DatosReporte, fotos: (String) -> ByteArray? = { File(it).takeIf(File::exists)?.readBytes() }) =
        GeneradorReportePdf(carpeta.newFolder(), fuentes, fotos).generar(datos).also { r ->
            // Para revisarlo a mano: PDF_PRUEBA_DIR=/ruta ./gradlew :excavaciones:testDebugUnitTest
            System.getenv("PDF_PRUEBA_DIR")?.let { r.archivo.copyTo(File(it, r.archivo.name), overwrite = true) }
        }

    @Test
    fun lasFotosVanComoJpegSinRecodificarYElPdfEsValido() {
        val horizontal = jpeg(1600, 1200, 1)
        val vertical = jpeg(900, 1200, 2)
        val lista = listOf(ejemplo(1, observaciones = "Entibación revisada → OK 👍"), ejemplo(2))
        val fotos = mapOf(
            lista[0].id to listOf(
                FotoReporte(horizontal.path, 1600, 1200, LocalDateTime.of(2026, 10, 15, 10, 1)),
                FotoReporte(vertical.path, 900, 1200, null),
            ),
        )
        val generado = generar(datos(fotos, lista))
        val pdf = generado.archivo.readBytes()

        assertEquals(2, generado.paginas)
        assertTrue(String(pdf, 0, 8, Charsets.ISO_8859_1).startsWith("%PDF-1.4"))
        assertTrue(String(pdf, pdf.size - 6, 6, Charsets.ISO_8859_1).trim().endsWith("%%EOF"))
        // Cada foto entra con sus bytes exactos: sin pérdida adicional ni reducción.
        for (f in listOf(horizontal, vertical)) assertTrue("Falta ${f.name}", indice(pdf, f.readBytes()) >= 0)
        val texto = String(pdf, Charsets.ISO_8859_1)
        assertEquals(2, Regex("/Filter /DCTDecode").findAll(texto).count())
        assertTrue("/Width 1600 /Height 1200" in texto)
        assertTrue("/Width 900 /Height 1200" in texto)
        assertEquals(2, Regex("/FontFile2").findAll(texto).count())
        assertTrue("/Count 2" in texto)
        verificarReferencias(pdf)
    }

    @Test
    fun adyacentesAgrupadasConSusFotosJuntas() {
        val lista = listOf(
            ejemplo(1, observaciones = "Matriz de agua potable").copy(grupo = 3),
            ejemplo(2, largo = 2.5).copy(grupo = 3),
            ejemplo(3),
        )
        val fotos = mapOf(
            lista[0].id to listOf(FotoReporte(jpeg(1600, 1200, 4).path, 1600, 1200, null)),
            lista[1].id to listOf(FotoReporte(jpeg(1600, 1200, 5).path, 1600, 1200, null)),
        )
        val pdf = generar(datos(fotos, lista)).archivo.readBytes()
        assertEquals(2, Regex("/Filter /DCTDecode").findAll(String(pdf, Charsets.ISO_8859_1)).count())
        verificarReferencias(pdf)
    }

    @Test
    fun fotoQueFaltaQuedaMarcadaYElReporteIgualSale() {
        val lista = listOf(ejemplo(1))
        val fotos = mapOf(lista[0].id to listOf(FotoReporte(File(carpeta.root, "no-existe.jpg").path, 1600, 1200, null)))
        val pdf = generar(datos(fotos, lista)).archivo.readBytes()
        val texto = String(pdf, Charsets.ISO_8859_1)
        assertTrue("/DCTDecode" !in texto)
        // El texto "Foto no disponible" está en un flujo comprimido: se busca descomprimiendo.
        val glifos = "Foto no disponible".map { "%04X".format(fuentes.regular.glifo(it.code)) }.joinToString("")
        assertTrue(flujos(pdf).any { "<$glifos> Tj" in it })
        verificarReferencias(pdf)
    }

    @Test
    fun soloSeIncrustanLosGlifosUsados() {
        val regular = fuentes.regular
        val glifos = "Sector A".map { regular.glifo(it.code) }.toSet()
        val sub = regular.subconjunto(glifos)
        assertTrue("Subconjunto de ${sub.size} bytes", sub.size < 40_000)
        // La "á" de Roboto es compuesta: arrastra la "a" y la tilde.
        val conTilde = regular.subconjunto(setOf(regular.glifo('á'.code)))
        assertTrue(conTilde.size > regular.subconjunto(emptySet()).size)
    }

    @Test
    fun medidasDeTexto() {
        val e = Estilo(10f)
        assertEquals(0f, fuentes.ancho("", e))
        assertTrue(fuentes.ancho("WWW", e) > fuentes.ancho("iii", e))
        assertTrue(fuentes.ancho("Sector", Estilo(10f, negrita = true)) > fuentes.ancho("Sector", e))
        // "→" no existe en Roboto: se le reserva su ancho fijo para dibujarla.
        assertEquals(fuentes.ancho("A", e) + FuentesPdf.ANCHO_FLECHA * 10f + fuentes.ancho("B", e), fuentes.ancho("A→B", e), 0.01f)
        // Un carácter sin glifo mide como "?".
        assertEquals(fuentes.ancho("?", e), fuentes.ancho("👍", e), 0.01f)
    }

    @Test
    fun leeElTamanoDeUnJpeg() {
        val f = jpeg(320, 200, 3)
        assertEquals(InfoJpeg(320, 200, 3), InfoJpeg.de(f.readBytes()))
        assertNull(InfoJpeg.de(byteArrayOf(1, 2, 3, 4)))
        assertNotNull(InfoJpeg.de(f.readBytes()))
    }

    @Test
    fun numerosSinComaDecimal() {
        val anterior = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-CL"))
            assertEquals("12.5", EscritorPdf.numero(12.5f))
            assertEquals("-3", EscritorPdf.numero(-3f))
            assertEquals("0.102 0.102 0.102", EscritorPdf.color(Colores.TEXTO))
        } finally {
            java.util.Locale.setDefault(anterior)
        }
        assertEquals("41FF", EscritorPdf.hex(byteArrayOf(0x41, -1)))
    }

    /** Cada entrada de la tabla xref apunta al inicio de su objeto. */
    private fun verificarReferencias(pdf: ByteArray) {
        val texto = String(pdf, Charsets.ISO_8859_1)
        val inicio = Regex("startxref\\n(\\d+)").find(texto)!!.groupValues[1].toInt()
        val tabla = texto.substring(inicio).lines()
        assertEquals("xref", tabla[0])
        val total = tabla[1].split(" ")[1].toInt()
        for (n in 1 until total) {
            val posicion = tabla[2 + n].substring(0, 10).toInt()
            assertTrue("Objeto $n en $posicion", texto.startsWith("$n 0 obj\n", posicion))
        }
        assertTrue("/Size $total" in texto)
    }

    /** Contenido descomprimido de los flujos con FlateDecode. */
    private fun flujos(pdf: ByteArray): List<String> {
        val texto = String(pdf, Charsets.ISO_8859_1)
        return Regex("/FlateDecode /Length (\\d+) >>\\nstream\\n").findAll(texto).map { m ->
            val desde = m.range.last + 1
            val datos = pdf.copyOfRange(desde, desde + m.groupValues[1].toInt())
            val salida = ByteArrayOutputStream()
            InflaterInputStream(ByteArrayInputStream(datos)).use { it.copyTo(salida) }
            salida.toString(Charsets.ISO_8859_1.name())
        }.toList()
    }

    private fun indice(en: ByteArray, buscado: ByteArray): Int {
        outer@ for (i in 0..en.size - buscado.size) {
            for (j in buscado.indices) if (en[i + j] != buscado[j]) continue@outer
            return i
        }
        return -1
    }
}
