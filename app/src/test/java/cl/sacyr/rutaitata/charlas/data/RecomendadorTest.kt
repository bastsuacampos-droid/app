package cl.sacyr.rutaitata.charlas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

class RecomendadorTest {

    private val banco = BancoParser.parse(File("src/main/assets/charlas.json").readText())
    private fun actividad(id: String) = banco.actividades.first { it.id == id }

    @Test
    fun raicesIgnoranTildesPluralesYPalabrasVacias() {
        assertEquals(listOf("hormi", "zanja", "excav"), Recomendador.raices("Hormigón en las zanjas para excavación"))
        assertEquals(Recomendador.raices("hormigonado"), Recomendador.raices("HORMIGÓN"))
    }

    @Test
    fun cadaActividadRecomiendaVariasCharlasDeSusEspecialidades() {
        banco.actividades.forEach { a ->
            val r = Recomendador.recomendar(banco, "", a, emptySet())
            assertTrue("${a.nombre}: solo ${r.size} resultados", r.size >= 2)
            assertTrue(
                "${a.nombre}: la primera (${r.first().charla.codigo}) no es de ${a.especialidades}",
                r.first().charla.especialidadId in a.especialidades,
            )
        }
    }

    @Test
    fun textoLibreEncuentraLaCharlaDelTema() {
        val r = Recomendador.recomendar(banco, "voy a cortar fierro con esmeril", null, emptySet())
        assertEquals("EN-12", r.first().charla.codigo)
        val h = Recomendador.recomendar(banco, "hormigonado del cabezal", null, emptySet())
        assertTrue(h.take(3).any { it.charla.especialidadId == "HO" })
    }

    @Test
    fun lasOcupadasQuedanAlFinal() {
        val sinFiltro = Recomendador.recomendar(banco, "", actividad("hormigon"), emptySet())
        val primera = sinFiltro.first().charla.id
        val conFiltro = Recomendador.recomendar(banco, "", actividad("hormigon"), setOf(primera))
        assertNotEquals(primera, conFiltro.first().charla.id)
        assertTrue(conFiltro.last().ocupada)
    }

    @Test
    fun sinActividadNiTextoNoRecomiendaNada() {
        assertTrue(Recomendador.recomendar(banco, "  de la  ", null, emptySet()).isEmpty())
    }

    @Test
    fun cambiarAlAzarEligeOtraLibreDeLaMismaEspecialidad() {
        val lunes = LocalDate.of(2026, 10, 5)
        val plan = Planificador.generar(banco, lunes, listOf("MP"), listOf(1, 2), emptyList(), emptyList())
        val vistas = (1..20).mapNotNull {
            Planificador.cambiar(banco, plan, 1, "MP", emptyList(), emptyList(), Random(it))?.charlas?.get(1)
        }.toSet()
        assertTrue("El azar debería dar varias opciones", vistas.size > 3)
        vistas.forEach {
            assertEquals("MP", banco.charla(it)!!.especialidadId)
            assertNotEquals(plan.charlas[1], it)
            assertNotEquals(plan.charlas[2], it)
        }
    }

    @Test
    fun asignarYAnotarActividad() {
        val lunes = LocalDate.of(2026, 10, 5)
        val plan = Planificador.generar(banco, lunes, listOf("MP"), listOf(1), emptyList(), emptyList())
        val ho = banco.charlasDe("HO").first().id
        val nuevo = Planificador.anotarActividad(Planificador.asignar(plan, 3, ho), 3, " Hormigonado de cabezal ")
        assertEquals(listOf(1, 3), nuevo.dias)
        assertEquals(ho, nuevo.charlas[3])
        assertEquals("Hormigonado de cabezal", nuevo.actividades[3])
        assertEquals(nuevo, Planificador.deJson(Planificador.aJson(nuevo)))
        assertTrue(Planificador.anotarActividad(nuevo, 3, "").actividades.isEmpty())
    }
}
