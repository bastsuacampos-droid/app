package cl.sacyr.rutaitata.charlas.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalendarioTest {

    // Lunes 5 de octubre de 2026.
    private val inicio = LocalDate.of(2026, 10, 5)

    @Test
    fun primerDiaEsSemanaUnoLunes() {
        assertEquals(DiaCiclo(1, 1), Calendario.diaCiclo(inicio, inicio))
    }

    @Test
    fun sabadoDeLaSemanaDos() {
        assertEquals(DiaCiclo(2, 6), Calendario.diaCiclo(inicio, LocalDate.of(2026, 10, 17)))
    }

    @Test
    fun domingoApuntaAlLunesSiguiente() {
        assertEquals(DiaCiclo(2, 1), Calendario.diaCiclo(inicio, LocalDate.of(2026, 10, 11)))
        assertEquals(DiaCiclo(1, 1), Calendario.diaCiclo(inicio, LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun elCicloSeRepiteCadaCuatroSemanas() {
        assertEquals(DiaCiclo(1, 3), Calendario.diaCiclo(inicio, LocalDate.of(2026, 11, 4)))
    }

    @Test
    fun inicioAMitadDeSemanaSeAlineaAlLunes() {
        assertEquals(DiaCiclo(1, 1), Calendario.diaCiclo(LocalDate.of(2026, 10, 8), inicio))
    }
}
