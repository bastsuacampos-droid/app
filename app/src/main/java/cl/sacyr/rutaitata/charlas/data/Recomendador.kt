package cl.sacyr.rutaitata.charlas.data

import java.text.Normalizer
import java.time.YearMonth

/**
 * Charla sugerida para una actividad.
 * @property ocupada ya dictada en el mes, o planificada para otro día del mes.
 */
data class Recomendacion(val charla: Charla, val puntaje: Int, val ocupada: Boolean)

/**
 * Recomienda charlas acordes a la actividad del día, buscando las palabras de la actividad
 * (o lo que escribió el capataz) en el título y el contenido de cada charla.
 */
object Recomendador {

    private const val LARGO_RAIZ = 5
    private const val PUNTOS_TITULO = 3
    private const val PUNTOS_TEXTO = 1
    private const val PUNTOS_ESPECIALIDAD = 2

    private val PALABRAS_VACIAS = setOf(
        "para", "por", "con", "los", "las", "del", "una", "uno", "unos", "unas", "que", "hoy", "voy",
        "vamos", "hacer", "haremos", "hare", "dia", "obra", "faena", "trabajo", "trabajos", "trabajar",
        "tarea", "tareas", "actividad", "cuadrilla", "esta", "este", "sobre", "entre", "desde", "como",
        "todo", "toda", "todos", "todas", "mas", "muy", "sin", "son", "ser", "hay",
    )

    /**
     * Ordena las charlas de mejor a peor coincidencia; las [ocupadas] quedan al final.
     * Solo devuelve charlas con alguna coincidencia.
     */
    fun recomendar(
        banco: Banco,
        texto: String,
        actividad: Actividad?,
        ocupadas: Set<Int>,
    ): List<Recomendacion> {
        val consulta = raices(texto) + actividad?.palabras.orEmpty().flatMap(::raices)
        if (consulta.isEmpty() && actividad == null) return emptyList()
        // Con texto libre, las actividades conocidas que coinciden también aportan sus especialidades.
        val escritas = raices(texto).toSet()
        val preferidas = actividad?.especialidades.orEmpty().toSet() + banco.actividades
            .filter { a -> a.palabras.flatMap(::raices).any { it in escritas } }
            .flatMap { it.especialidades }
        return banco.charlas
            .map { charla ->
                val titulo = raices(charla.titulo).toSet()
                val resto = raices(charla.porQue + " " + charla.checklist.joinToString(" ")).toSet()
                var puntaje = consulta.toSet().sumOf { raiz ->
                    when (raiz) {
                        in titulo -> PUNTOS_TITULO
                        in resto -> PUNTOS_TEXTO
                        else -> 0
                    }
                }
                if (puntaje > 0 && charla.especialidadId in preferidas) puntaje += PUNTOS_ESPECIALIDAD
                Recomendacion(charla, puntaje, charla.id in ocupadas)
            }
            .filter { it.puntaje > 0 }
            .sortedWith(compareBy<Recomendacion> { it.ocupada }.thenByDescending { it.puntaje }.thenBy { it.charla.codigo })
    }

    /** Charlas dictadas en [mes]; para recomendar sin un plan semanal. */
    fun dictadasEn(mes: YearMonth, usos: Collection<Uso>): Set<Int> =
        usos.filter { YearMonth.from(it.fecha) == mes }.map { it.charlaId }.toSet()

    /** Palabras normalizadas (sin tildes ni plurales) y recortadas a su raíz. */
    internal fun raices(texto: String): List<String> =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .split(Regex("[^a-zñ0-9]+"))
            .filter { it.length >= 3 && it !in PALABRAS_VACIAS }
            .map { palabra ->
                val singular = when {
                    palabra.endsWith("es") && palabra.length > 5 -> palabra.dropLast(2)
                    palabra.endsWith("s") && palabra.length > 4 -> palabra.dropLast(1)
                    else -> palabra
                }
                singular.take(LARGO_RAIZ)
            }
}
