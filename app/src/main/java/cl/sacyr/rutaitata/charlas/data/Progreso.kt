package cl.sacyr.rutaitata.charlas.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** Avance del capataz: charlas realizadas, puntos verificados y preferencias. */
class Progreso(context: Context) {

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
        // Se lee todo lo guardado: el banco puede cambiar con una actualización.
        for ((clave, valor) in prefs.all) {
            val id = clave.substringAfter('_').toIntOrNull() ?: continue
            when {
                clave.startsWith(PREFIJO_REALIZADA) && valor is Long -> realizadas[id] = LocalDate.ofEpochDay(valor)
                clave.startsWith(PREFIJO_CHECKS) && valor is Set<*> ->
                    verificados[id] = valor.mapNotNull { (it as? String)?.toIntOrNull() }.toSet()
            }
        }
    }

    fun realizada(id: Int): LocalDate? = realizadas[id]

    /** Cuántas de [ids] están realizadas en el ciclo actual. */
    fun totalRealizadas(ids: Collection<Int>): Int = ids.count { it in realizadas }

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
        prefs.all.keys
            .filter { it.startsWith(PREFIJO_REALIZADA) || it.startsWith(PREFIJO_CHECKS) }
            .forEach(editor::remove)
        editor.putLong(KEY_INICIO, inicioCiclo.toEpochDay()).apply()
    }

    private fun keyRealizada(id: Int) = "$PREFIJO_REALIZADA$id"
    private fun keyChecks(id: Int) = "$PREFIJO_CHECKS$id"

    companion object {
        private const val KEY_INICIO = "inicio_ciclo"
        private const val KEY_ESCALA = "escala_texto"
        private const val PREFIJO_REALIZADA = "realizada_"
        private const val PREFIJO_CHECKS = "checks_"
        const val ESCALA_MIN = 0.85f
        const val ESCALA_MAX = 1.6f
    }
}
