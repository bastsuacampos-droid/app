package cl.sacyr.rutaitata.charlas.data

import java.time.LocalDate
import java.time.YearMonth

/** Una charla dictada en una fecha. */
data class Uso(val charlaId: Int, val fecha: LocalDate)

/** Consultas sobre el registro de charlas dictadas, para no repetirlas dentro del mes. */
object Usos {

    fun delMes(usos: Collection<Uso>, mes: YearMonth): List<Uso> =
        usos.filter { YearMonth.from(it.fecha) == mes }
            .sortedWith(compareByDescending<Uso> { it.fecha }.thenBy { it.charlaId })

    /** Fecha más reciente en que se usó [charlaId] durante [mes], o null si está disponible. */
    fun fechaEnMes(usos: Collection<Uso>, charlaId: Int, mes: YearMonth): LocalDate? =
        usos.filter { it.charlaId == charlaId && YearMonth.from(it.fecha) == mes }.maxOfOrNull { it.fecha }

    /** Charlas de [charlas] que todavía no se usan en [mes], en el orden recibido. */
    fun disponibles(charlas: List<Charla>, usos: Collection<Uso>, mes: YearMonth): List<Charla> {
        val usadas = usos.filter { YearMonth.from(it.fecha) == mes }.map { it.charlaId }.toSet()
        return charlas.filter { it.id !in usadas }
    }
}
