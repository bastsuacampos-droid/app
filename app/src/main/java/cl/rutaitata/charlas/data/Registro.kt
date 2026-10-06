package cl.rutaitata.charlas.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** Estado persistente: inicio del ciclo, charlas dictadas y preferencia de texto grande. */
class Registro(context: Context) {
    private val prefs = context.getSharedPreferences("registro_charlas", Context.MODE_PRIVATE)

    var inicioCiclo: LocalDate by mutableStateOf(cargarInicio())
        private set

    var textoGrande: Boolean by mutableStateOf(prefs.getBoolean(KEY_TEXTO_GRANDE, false))
        private set

    /** numero de charla -> fecha en que se dictó */
    val dictadas = mutableStateMapOf<Int, LocalDate>().apply {
        BancoCharlas.charlas.forEach { c ->
            prefs.getString(KEY_DICTADA + c.numero, null)?.let { put(c.numero, LocalDate.parse(it)) }
        }
    }

    private fun cargarInicio(): LocalDate {
        prefs.getString(KEY_INICIO, null)?.let { return LocalDate.parse(it) }
        val inicio = Ciclo.lunesDe(LocalDate.now())
        prefs.edit().putString(KEY_INICIO, inicio.toString()).apply()
        return inicio
    }

    fun fijarSemanaActual(semana: Int, hoy: LocalDate = LocalDate.now()) {
        inicioCiclo = Ciclo.inicioParaSemana(hoy, semana)
        prefs.edit().putString(KEY_INICIO, inicioCiclo.toString()).apply()
    }

    fun cambiarTextoGrande(valor: Boolean) {
        textoGrande = valor
        prefs.edit().putBoolean(KEY_TEXTO_GRANDE, valor).apply()
    }

    fun marcarDictada(numero: Int, fecha: LocalDate = LocalDate.now()) {
        dictadas[numero] = fecha
        prefs.edit().putString(KEY_DICTADA + numero, fecha.toString()).apply()
    }

    fun desmarcar(numero: Int) {
        dictadas.remove(numero)
        prefs.edit().remove(KEY_DICTADA + numero).apply()
    }

    fun reiniciarRegistro() {
        val editor = prefs.edit()
        dictadas.keys.forEach { editor.remove(KEY_DICTADA + it) }
        editor.apply()
        dictadas.clear()
    }

    private companion object {
        const val KEY_INICIO = "inicio_ciclo"
        const val KEY_TEXTO_GRANDE = "texto_grande"
        const val KEY_DICTADA = "dictada_"
    }
}
