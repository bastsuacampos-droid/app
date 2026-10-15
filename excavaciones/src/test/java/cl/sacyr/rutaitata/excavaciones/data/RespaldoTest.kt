package cl.sacyr.rutaitata.excavaciones.data

import androidx.test.core.app.ApplicationProvider
import cl.sacyr.rutaitata.excavaciones.data.local.BaseDatos
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.dominio.BorradorExcavacion
import cl.sacyr.rutaitata.excavaciones.dominio.FotoBorrador
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class RespaldoTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val bases = mutableListOf<BaseDatos>()
    private val hoy = LocalDate.of(2026, 10, 15)

    /** Un "teléfono": base y carpeta de fotos propias. */
    private inner class Telefono(nombre: String) {
        val bd = BaseDatos.enMemoria(context).also { bases += it }
        val fotos = ArchivosFotos(File(context.filesDir, "fotos-$nombre").apply { deleteRecursively() })
        val repo = RepositorioExcavaciones(bd) { 5_000L }
        val respaldo = Respaldo(bd, fotos)

        fun foto(nombre: String, contenido: String): FotoBorrador {
            fotos.carpeta.mkdirs()
            fotos.archivo(nombre).writeText(contenido)
            return FotoBorrador(nombre, tomadaEn = 1234L)
        }
    }

    @After
    fun cerrar() = bases.forEach { it.close() }

    private fun borrador(t: Telefono, sector: String, fotos: List<FotoBorrador> = emptyList(), lado: Lado = Lado.NORTE) = runBlocking {
        BorradorExcavacion(
            fecha = hoy, sector = sector, lado = lado, sentido = Sentido.CHILLAN_CONCEPCION,
            largo = 4.0, ancho = 1.2, profundidad = 1.5, tipoTerrenoId = t.repo.tiposTerreno().first()[2].id,
            latitud = -36.6, longitud = -72.1, precisionGps = 3.5f, observaciones = "Napa; \"alta\"", progresiva = null, fotos = fotos,
        )
    }

    private fun exportar(t: Telefono): ByteArray = runBlocking {
        ByteArrayOutputStream().also { t.respaldo.exportar(it, ahora = 99L) }.toByteArray()
    }

    @Test
    fun respaldoYRestauracionEnOtroTelefonoDejanTodoIgual() = runBlocking {
        val a = Telefono("a")
        val id1 = a.repo.guardar(borrador(a, "Sector A", listOf(a.foto("f1.jpg", "uno"), a.foto("f2.jpg", "dos"))))
        val id2 = a.repo.guardar(borrador(a, "Km 12", lado = Lado.SUR))
        a.repo.agregarTipoTerreno("Arcilla")
        a.repo.guardarTipoExcavacion(TipoExcavacionEntity(nombre = "Zanja", largo = null, ancho = 0.6, profundidad = 1.2, tipoTerrenoId = null, observaciones = null))
        val zip = exportar(a)

        // Un teléfono nuevo con sus propios datos de prueba, que se reemplazan.
        val b = Telefono("b")
        b.repo.guardar(borrador(b, "Otro", listOf(b.foto("vieja.jpg", "x"))))
        val resumen = b.respaldo.leerResumen(ByteArrayInputStream(zip))
        assertEquals(2, resumen.excavaciones)
        assertEquals(2, resumen.fotos)
        assertEquals(hoy, resumen.desde)

        b.respaldo.restaurar(ByteArrayInputStream(zip))

        assertEquals(a.repo.borrador(id1), b.repo.borrador(id1))
        assertEquals(a.repo.borrador(id2), b.repo.borrador(id2))
        assertEquals(a.repo.paraReporte(hoy, hoy), b.repo.paraReporte(hoy, hoy))
        assertEquals(a.repo.tiposTerreno().first(), b.repo.tiposTerreno().first())
        assertEquals(a.repo.tiposExcavacion().first(), b.repo.tiposExcavacion().first())
        assertEquals("uno", b.fotos.archivo("f1.jpg").readText())
        assertFalse("La foto del registro reemplazado se borra", b.fotos.archivo("vieja.jpg").exists())
        // Y la numeración sigue donde iba.
        assertEquals(2, b.repo.siguienteNumero("Sector A", Lado.NORTE, Sentido.CHILLAN_CONCEPCION))
    }

    @Test
    fun unArchivoQueNoEsRespaldoNoTocaNada() = runBlocking {
        val t = Telefono("c")
        val id = t.repo.guardar(borrador(t, "Sector A"))
        val basura = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry("otra.txt")); it.write("hola".toByteArray()) }
        }.toByteArray()
        for (malo in listOf(basura, "no es zip".toByteArray())) {
            assertThrows(java.io.IOException::class.java) { runBlocking { t.respaldo.restaurar(ByteArrayInputStream(malo)) } }
        }
        assertTrue(t.repo.borrador(id) != null)
    }

    @Test
    fun ignoraRutasPeligrosasDentroDelZip() = runBlocking {
        val a = Telefono("d")
        a.repo.guardar(borrador(a, "Sector A", listOf(a.foto("f1.jpg", "uno"))))
        val original = exportar(a)
        // Se agrega una entrada que intenta escribir fuera de la carpeta de fotos.
        val manipulado = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                java.util.zip.ZipInputStream(ByteArrayInputStream(original)).use { zin ->
                    while (true) {
                        val e = zin.nextEntry ?: break
                        z.putNextEntry(ZipEntry(e.name)); zin.copyTo(z); z.closeEntry()
                    }
                }
                z.putNextEntry(ZipEntry("fotos/../../malo.txt")); z.write("x".toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        val b = Telefono("e")
        b.respaldo.restaurar(ByteArrayInputStream(manipulado))
        assertFalse(File(context.filesDir, "malo.txt").exists())
        assertArrayEquals("uno".toByteArray(), b.fotos.archivo("f1.jpg").readBytes())
    }
}
