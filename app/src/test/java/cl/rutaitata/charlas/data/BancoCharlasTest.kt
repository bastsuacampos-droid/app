package cl.rutaitata.charlas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BancoCharlasTest {

    private val charlas = BancoCharlas.charlas

    @Test
    fun bancoCubreCuatroSemanasDeLunesASabado() {
        assertEquals(24, charlas.size)
        assertEquals((1..24).toList(), charlas.map { it.numero })
        for (semana in 1..4) for (dia in 0..5) {
            assertNotNull("Falta semana $semana día $dia", BancoCharlas.deDia(semana, dia))
        }
        assertEquals(24, charlas.map { it.semana to it.dia }.toSet().size)
    }

    @Test
    fun cadaCharlaTieneLaEstructuraCompleta() {
        charlas.forEach { c ->
            assertEquals("N° ${c.numero}: deben ser 3 puntos de control", 3, c.puntosControl.size)
            assertTrue(c.titulo.isNotBlank() && c.porQue.isNotBlank() && c.preguntaCierre.isNotBlank())
            assertTrue("N° ${c.numero}: respaldo sin regla de oro", c.respaldo.contains("Regla", ignoreCase = true))
        }
        assertEquals(charlas.size, charlas.map { it.titulo }.toSet().size)
    }

    @Test
    fun todasLasEspecialidadesEstanCubiertas() {
        Especialidad.entries.forEach { e ->
            assertTrue("$e con pocas charlas", BancoCharlas.deEspecialidad(e).size >= 4)
        }
    }

    @Test
    fun charlaModeloEsAtropelloHombreMaquina() {
        val ejemplo = BancoCharlas.porNumero(BancoCharlas.NUMERO_EJEMPLO)!!
        assertTrue(ejemplo.titulo.contains("atropello", ignoreCase = true))
        assertEquals(1, ejemplo.semana)
        assertEquals(0, ejemplo.dia)
    }

    @Test
    fun cicloAvanzaPorSemanaYVuelveAlInicio() {
        val inicio = LocalDate.of(2026, 10, 5) // lunes
        assertEquals(1, Ciclo.semanaDelCiclo(inicio, inicio))
        assertEquals(1, Ciclo.semanaDelCiclo(inicio, LocalDate.of(2026, 10, 10))) // sábado
        assertEquals(2, Ciclo.semanaDelCiclo(inicio, LocalDate.of(2026, 10, 12)))
        assertEquals(4, Ciclo.semanaDelCiclo(inicio, LocalDate.of(2026, 10, 26)))
        assertEquals(1, Ciclo.semanaDelCiclo(inicio, LocalDate.of(2026, 11, 2)))
        assertEquals(4, Ciclo.semanaDelCiclo(inicio, LocalDate.of(2026, 9, 28))) // semana anterior al inicio
    }

    @Test
    fun charlaDelDiaRespetaDiaDeSemanaYDomingo() {
        val inicio = LocalDate.of(2026, 10, 5)
        assertEquals(1, Ciclo.charlaDelDia(inicio, inicio)?.numero)
        assertEquals(9, Ciclo.charlaDelDia(inicio, LocalDate.of(2026, 10, 14))?.numero) // miércoles semana 2
        assertNull(Ciclo.charlaDelDia(inicio, LocalDate.of(2026, 10, 11))) // domingo
    }

    @Test
    fun fijarSemanaActualAjustaElInicio() {
        val hoy = LocalDate.of(2026, 10, 8) // jueves
        for (s in 1..4) {
            assertEquals(s, Ciclo.semanaDelCiclo(Ciclo.inicioParaSemana(hoy, s), hoy))
        }
    }
}
