package cl.sacyr.rutaitata.charlas.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** Avance del capataz: charlas realizadas, puntos verificados y preferencias. */
class Progreso(context: Context, private val charlaIds: List<Int>) {

    private val prefs = context.getSharedPreferences("progreso", Context.MODE_PRIVATE)

    private val realizadas = mutableStateMapOf<Int, LocalDate>()
    private val verificados = mutableStateMapOf<Int, Set<Int>>()

    var inicioCiclo by mutableStateOf(LocalDate.now())
        private set

    var escalaTexto by mutableFloatStateOf(prefs.getFloat(KEY_ESCALA, 1f))
        private set

    init {
        val guardado = prefs.getLong(KEY_INICIO, Long.MIN_VALUE)
        if (guardado == Long.MIN_VALUE) {
            reiniciarCiclo(LocalDate.now())
        } else {
            inicioCiclo = LocalDate.ofEpochDay(guardado)
        }
        for (id in charlaIds) {
            val dia = prefs.getLong(keyRealizada(id), Long.MIN_VALUE)
            if (dia != Long.MIN_VALUE) realizadas[id] = LocalDate.ofEpochDay(dia)
            prefs.getStringSet(keyChecks(id), null)?.let { set ->
                verificados[id] = set.mapNotNull { it.toIntOrNull() }.toSet()
            }
        }
    }

    fun realizada(id: Int): LocalDate? = realizadas[id]

    val totalRealizadas: Int get() = realizadas.size

    fun marcarRealizada(id: Int, fecha: LocalDate?) {
        if (fecha == null) {
            realizadas.remove(id)
            prefs.edit().remove(keyRealizada(id)).apply()
        } else {
            realizadas[id] = fecha
            prefs.edit().putLong(keyRealizada(id), fecha.toEpochDay()).apply()
        }
    }

    fun verificados(id: Int): Set<Int> = verificados[id].orEmpty()

    fun alternarPunto(id: Int, indice: Int) {
        val actual = verificados(id)
        val nuevo = if (indice in actual) actual - indice else actual + indice
        verificados[id] = nuevo
        prefs.edit().putStringSet(keyChecks(id), nuevo.map(Int::toString).toSet()).apply()
    }

    fun cambiarEscala(delta: Float) {
        escalaTexto = (escalaTexto + delta).coerceIn(ESCALA_MIN, ESCALA_MAX)
        prefs.edit().putFloat(KEY_ESCALA, escalaTexto).apply()
    }

    /** Comienza un nuevo ciclo de 4 semanas en la semana de [fecha] y borra el avance. */
    fun reiniciarCiclo(fecha: LocalDate) {
        inicioCiclo = Calendario.lunesDe(fecha)
        realizadas.clear()
        verificados.clear()
        val editor = prefs.edit()
        charlaIds.forEach { editor.remove(keyRealizada(it)).remove(keyChecks(it)) }
        editor.putLong(KEY_INICIO, inicioCiclo.toEpochDay()).apply()
    }

    private fun keyRealizada(id: Int) = "realizada_$id"
    private fun keyChecks(id: Int) = "checks_$id"

    companion object {
        private const val KEY_INICIO = "inicio_ciclo"
        private const val KEY_ESCALA = "escala_texto"
        const val ESCALA_MIN = 0.85f
        const val ESCALA_MAX = 1.6f
    }
}
