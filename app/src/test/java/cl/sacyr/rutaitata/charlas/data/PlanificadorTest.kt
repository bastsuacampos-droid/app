package cl.sacyr.rutaitata.charlas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PlanificadorTest {

    private val banco = BancoParser.parse(File("src/main/assets/charlas.json").readText())
    private val lunes = LocalDate.of(2026, 10, 5)
    private val todosLosDias = (1..6).toList()

    private fun especialidadDe(id: Int?) = banco.charla(id!!)!!.especialidadId

    @Test
    fun reparteLasEspecialidadesEnElOrdenElegido() {
        val plan = Planificador.generar(banco, lunes, listOf("CT", "MP"), todosLosDias, emptyList(), emptyList())
        assertEquals(listOf("CT", "MP", "CT", "MP", "CT", "MP"), todosLosDias.map { especialidadDe(plan.charlas[it]) })
        assertEquals("Sin repetir dentro de la semana", 6, plan.charlas.values.toSet().size)
    }

    @Test
    fun soloLosDiasElegidos() {
        val plan = Planificador.generar(banco, lunes, listOf("AS"), listOf(5, 1, 3), emptyList(), emptyList())
        assertEquals(listOf(1, 3, 5), plan.dias)
        assertEquals(setOf(1, 3, 5), plan.charlas.keys)
    }

    @Test
    fun noUsaCharlasDictadasEnElMes() {
        val mp = banco.charlasDe("MP")
        val usos = listOf(Uso(mp[0].id, LocalDate.of(2026, 10, 1)), Uso(mp[1].id, LocalDate.of(2026, 10, 2)))
        val plan = Planificador.generar(banco, lunes, listOf("MP"), listOf(1), usos, emptyList())
        assertEquals(mp[2].id, plan.charlas[1])
    }

    @Test
    fun noRepiteLoPlanificadoEnOtraSemanaDelMismoMes() {
        val semana1 = Planificador.generar(banco, lunes, listOf("MP"), todosLosDias, emptyList(), emptyList())
        val semana2 = Planificador.generar(banco, lunes.plusWeeks(1), listOf("MP"), todosLosDias, emptyList(), listOf(semana1))
        assertTrue(semana1.charlas.values.intersect(semana2.charlas.values.toSet()).isEmpty())
    }

    @Test
    fun loDelMesAnteriorNoBloqueaElMesNuevo() {
        val mp = banco.charlasDe("MP")
        // Semana del lunes 28/09 al sábado 03/10: el lunes es septiembre y el jueves ya es octubre.
        val usos = listOf(Uso(mp[0].id, LocalDate.of(2026, 9, 10)))
        val plan = Planificador.generar(banco, LocalDate.of(2026, 9, 28), listOf("MP"), listOf(1, 4), usos, emptyList())
        assertEquals(mp[1].id, plan.charlas[1])
        assertEquals(mp[0].id, plan.charlas[4])
    }

    @Test
    fun siUnaEspecialidadSeAgotaUsaOtraDeLasElegidas() {
        val usos = banco.charlasDe("RT").map { Uso(it.id, LocalDate.of(2026, 10, 1)) }
        val plan = Planificador.generar(banco, lunes, listOf("RT", "OA"), listOf(1, 2), usos, emptyList())
        assertEquals(listOf("OA", "OA"), listOf(1, 2).map { especialidadDe(plan.charlas[it]) })
    }

    @Test
    fun cambiarRecorreLasOpcionesSinChocarConLaSemana() {
        val plan = Planificador.generar(banco, lunes, listOf("MP"), listOf(1, 2), emptyList(), emptyList())
        val cambiado = Planificador.cambiar(banco, plan, 1, "MP", emptyList(), emptyList())!!
        assertNotEquals(plan.charlas[1], cambiado.charlas[1])
        assertNotEquals(cambiado.charlas[2], cambiado.charlas[1])
        assertEquals(plan.charlas[2], cambiado.charlas[2])

        val otraEspecialidad = Planificador.cambiar(banco, plan, 1, "CT", emptyList(), emptyList())!!
        assertEquals("CT", especialidadDe(otraEspecialidad.charlas[1]))
    }

    @Test
    fun cambiarDevuelveNullSiNoQuedanDisponibles() {
        val plan = Planificador.generar(banco, lunes, listOf("MP"), listOf(1), emptyList(), emptyList())
        val usos = banco.charlasDe("MP").map { Uso(it.id, LocalDate.of(2026, 10, 1)) }
        assertNull(Planificador.cambiar(banco, plan, 1, "MP", usos, emptyList()))
    }

    @Test
    fun guardarYLeerElPlanDaLoMismo() {
        val plan = Planificador.generar(banco, lunes, listOf("OA", "AS"), listOf(1, 2, 4), emptyList(), emptyList())
        assertEquals(plan, Planificador.deJson(Planificador.aJson(plan)))
        assertNull(Planificador.deJson("{roto"))
    }

    @Test
    fun textoParaCompartir() {
        val plan = Planificador.generar(banco, lunes, listOf("MP"), listOf(1, 2), emptyList(), emptyList())
        val texto = Planificador.comoTexto(plan, banco)
        assertTrue(texto.contains("Semana del 05/10 al 10/10"))
        assertTrue(texto.contains("Lunes 05/10: MP-01"))
        assertFalse(texto.contains("Miércoles"))
    }
}
