package cl.rutaitata.charlas.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Ubica una fecha dentro del ciclo de 4 semanas (lunes a sábado). */
object Ciclo {
    const val SEMANAS = 4

    fun lunesDe(fecha: LocalDate): LocalDate =
        fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Semana del ciclo (1..4) para [fecha], contando desde el lunes de [inicio]. */
    fun semanaDelCiclo(inicio: LocalDate, fecha: LocalDate): Int {
        val semanas = ChronoUnit.WEEKS.between(lunesDe(inicio), lunesDe(fecha))
        return Math.floorMod(semanas, SEMANAS.toLong()).toInt() + 1
    }

    /** Charla programada para [fecha], o null si es domingo. */
    fun charlaDelDia(inicio: LocalDate, fecha: LocalDate): Charla? {
        if (fecha.dayOfWeek == DayOfWeek.SUNDAY) return null
        return BancoCharlas.deDia(semanaDelCiclo(inicio, fecha), fecha.dayOfWeek.value - 1)
    }

    /** Lunes desde el cual el ciclo queda en [semana] durante la semana de [hoy]. */
    fun inicioParaSemana(hoy: LocalDate, semana: Int): LocalDate =
        lunesDe(hoy).minusWeeks((semana - 1).toLong())
}
