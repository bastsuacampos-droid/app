package cl.sacyr.rutaitata.charlas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

class UsosTest {

    private val banco = BancoParser.parse(File("src/main/assets/charlas.json").readText())
    private val maquinaria = banco.charlasDe("MP")
    private val octubre = YearMonth.of(2026, 10)

    private val usos = listOf(
        Uso(maquinaria[0].id, LocalDate.of(2026, 10, 5)),
        Uso(maquinaria[1].id, LocalDate.of(2026, 10, 6)),
        Uso(maquinaria[2].id, LocalDate.of(2026, 9, 28)),
    )

    @Test
    fun lasUsadasEnElMesNoQuedanDisponibles() {
        val disponibles = Usos.disponibles(maquinaria, usos, octubre)
        assertEquals(maquinaria.size - 2, disponibles.size)
        // La usada en septiembre vuelve a estar disponible en octubre.
        assertEquals(maquinaria[2], disponibles.first())
    }

    @Test
    fun fechaEnMesDevuelveLaMasReciente() {
        val conRepeticion = usos + Uso(maquinaria[0].id, LocalDate.of(2026, 10, 20))
        assertEquals(LocalDate.of(2026, 10, 20), Usos.fechaEnMes(conRepeticion, maquinaria[0].id, octubre))
        assertNull(Usos.fechaEnMes(usos, maquinaria[2].id, octubre))
    }

    @Test
    fun delMesOrdenaDelMasRecienteAlMasAntiguo() {
        assertEquals(
            listOf(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 5)),
            Usos.delMes(usos, octubre).map { it.fecha },
        )
    }

    @Test
    fun elRegistroGuardadoSeLeeIgual() {
        val uso = Uso(105, LocalDate.of(2026, 10, 6))
        assertEquals(uso, Progreso.leerUso(Progreso.escribirUso(uso)))
        assertNull(Progreso.leerUso("basura"))
    }
}
