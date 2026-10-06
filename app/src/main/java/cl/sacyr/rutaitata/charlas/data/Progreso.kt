package cl.sacyr.rutaitata.charlas.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/**
 * Lo que el capataz registra en el teléfono: charlas dictadas (con fecha, para no
 * repetirlas en el mes), puntos de control verificados hoy y preferencias.
 */
class Progreso(context: Context) {

    private val prefs = context.getSharedPreferences("progreso", Context.MODE_PRIVATE)

    private val _usos = mutableStateListOf<Uso>()
    val usos: List<Uso> get() = _usos

    private val verificados = mutableStateMapOf<Int, Set<Int>>()
    private val hoy = LocalDate.now()

    var inicioCiclo by mutableStateOf(LocalDate.now())
        private set

    var escalaTexto by mutableFloatStateOf(prefs.getFloat(KEY_ESCALA, 1f))
        private set

    init {
        val guardado = prefs.getLong(KEY_INICIO, Long.MIN_VALUE)
        if (guardado == Long.MIN_VALUE) {
            reiniciarPlan(LocalDate.now())
        } else {
            inicioCiclo = LocalDate.ofEpochDay(guardado)
        }

        prefs.getStringSet(KEY_USOS, emptySet())!!.mapNotNullTo(_usos, ::leerUso)

        val editor = prefs.edit()
        for ((clave, valor) in prefs.all) {
            when {
                // Versión anterior: una fecha por charla. Se pasa al registro de usos.
                clave.startsWith(PREFIJO_REALIZADA_ANTIGUO) && valor is Long -> {
                    clave.removePrefix(PREFIJO_REALIZADA_ANTIGUO).toIntOrNull()?.let {
                        _usos += Uso(it, LocalDate.ofEpochDay(valor))
                    }
                    editor.remove(clave)
                }
                // Los checklists son de la charla del día: se conservan solo los de hoy.
                clave.startsWith(PREFIJO_CHECKS) -> {
                    val (dia, id) = clave.removePrefix(PREFIJO_CHECKS).split('_')
                        .let { it.getOrNull(0)?.toLongOrNull() to it.getOrNull(1)?.toIntOrNull() }
                    if (dia == hoy.toEpochDay() && id != null && valor is Set<*>) {
                        verificados[id] = valor.mapNotNull { (it as? String)?.toIntOrNull() }.toSet()
                    } else {
                        editor.remove(clave)
                    }
                }
            }
        }
        editor.putStringSet(KEY_USOS, _usos.map(::escribirUso).toSet()).apply()
    }

    fun registrarUso(charlaId: Int, fecha: LocalDate) {
        val uso = Uso(charlaId, fecha)
        if (uso in _usos) return
        _usos += uso
        guardarUsos()
    }

    fun eliminarUso(uso: Uso) {
        if (_usos.remove(uso)) guardarUsos()
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

    /** La semana de [fecha] pasa a ser la semana 1 del plan sugerido. No borra el historial. */
    fun reiniciarPlan(fecha: LocalDate) {
        inicioCiclo = Calendario.lunesDe(fecha)
        prefs.edit().putLong(KEY_INICIO, inicioCiclo.toEpochDay()).apply()
    }

    private fun guardarUsos() {
        prefs.edit().putStringSet(KEY_USOS, _usos.map(::escribirUso).toSet()).apply()
    }

    private fun keyChecks(id: Int) = "$PREFIJO_CHECKS${hoy.toEpochDay()}_$id"

    companion object {
        private const val KEY_INICIO = "inicio_ciclo"
        private const val KEY_ESCALA = "escala_texto"
        private const val KEY_USOS = "usos"
        private const val PREFIJO_REALIZADA_ANTIGUO = "realizada_"
        private const val PREFIJO_CHECKS = "checks_"
        const val ESCALA_MIN = 0.85f
        const val ESCALA_MAX = 1.6f

        internal fun escribirUso(uso: Uso) = "${uso.charlaId}:${uso.fecha.toEpochDay()}"

        internal fun leerUso(texto: String): Uso? {
            val partes = texto.split(':')
            val id = partes.getOrNull(0)?.toIntOrNull() ?: return null
            val dia = partes.getOrNull(1)?.toLongOrNull() ?: return null
            return Uso(id, LocalDate.ofEpochDay(dia))
        }
    }
}
