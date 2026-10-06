package cl.sacyr.rutaitata.charlas.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

const val SEMANAS_CICLO = 4

data class DiaCiclo(val semana: Int, val dia: Int)

object Calendario {

    fun lunesDe(fecha: LocalDate): LocalDate =
        fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Posición del ciclo de 4 semanas (lunes a sábado) que corresponde a [hoy],
     * contando desde la semana de [inicioCiclo]. El domingo apunta a la charla
     * del lunes siguiente para que el capataz la prepare con anticipación.
     */
    fun diaCiclo(inicioCiclo: LocalDate, hoy: LocalDate): DiaCiclo {
        val fecha = if (hoy.dayOfWeek == DayOfWeek.SUNDAY) hoy.plusDays(1) else hoy
        val semanas = ChronoUnit.WEEKS.between(lunesDe(inicioCiclo), lunesDe(fecha))
        val semana = Math.floorMod(semanas, SEMANAS_CICLO.toLong()).toInt() + 1
        return DiaCiclo(semana, fecha.dayOfWeek.value)
    }
}
