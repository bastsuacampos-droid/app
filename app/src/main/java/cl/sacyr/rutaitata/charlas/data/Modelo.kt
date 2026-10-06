package cl.sacyr.rutaitata.charlas.data

data class Especialidad(
    val id: String,
    val nombre: String,
    val colorHex: String,
)

data class Charla(
    val id: Int,
    /** Código corto para ubicarla en terreno, por ejemplo "MP-07". */
    val codigo: String,
    val especialidadId: String,
    val titulo: String,
    val porQue: String,
    val checklist: List<String>,
    val normativa: String,
    val reglaOro: String,
    val preguntaCierre: String,
)

/**
 * Actividad típica de la obra, para recomendar charlas acordes a lo que hará la cuadrilla.
 * [palabras] se buscan en el texto de las charlas y [especialidades] suman puntos extra.
 */
data class Actividad(
    val id: String,
    val nombre: String,
    val palabras: List<String>,
    val especialidades: List<String>,
)

/** Posición de una charla en el plan sugerido de 4 semanas (día 1 = lunes … 6 = sábado). */
data class EntradaPlan(val semana: Int, val dia: Int, val charlaId: Int)

data class Banco(
    val proyecto: String,
    /** Versión del contenido; se incrementa cada vez que se publica un cambio. */
    val version: Int,
    /** Fecha de publicación (AAAA-MM-DD), informativa. */
    val fecha: String,
    /** Resumen de los cambios de esta versión, para mostrar al capataz. */
    val novedades: String,
    val especialidades: List<Especialidad>,
    val charlas: List<Charla>,
    val plan: List<EntradaPlan>,
    val actividades: List<Actividad> = emptyList(),
) {
    private val especialidadesPorId = especialidades.associateBy { it.id }
    private val charlasPorId = charlas.associateBy { it.id }

    fun especialidad(id: String): Especialidad =
        especialidadesPorId[id] ?: error("Especialidad desconocida: $id")

    fun charla(id: Int): Charla? = charlasPorId[id]

    fun charla(semana: Int, dia: Int): Charla? =
        plan.firstOrNull { it.semana == semana && it.dia == dia }?.let { charla(it.charlaId) }

    fun charlasDe(especialidadId: String): List<Charla> =
        charlas.filter { it.especialidadId == especialidadId }

    fun entradaPlan(charlaId: Int): EntradaPlan? = plan.firstOrNull { it.charlaId == charlaId }
}

val NOMBRES_DIA = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")

fun nombreDia(dia: Int): String = NOMBRES_DIA[dia - 1]

/** Texto plano listo para compartir por WhatsApp o correo. */
fun Charla.comoTexto(banco: Banco): String = buildString {
    appendLine("CHARLA 5 MINUTOS – ${banco.proyecto}")
    appendLine("$codigo · ${banco.especialidad(especialidadId).nombre}")
    appendLine()
    appendLine("▶ $titulo")
    appendLine()
    appendLine("EL PORQUÉ")
    appendLine(porQue)
    appendLine()
    appendLine("PUNTOS DE CONTROL")
    checklist.forEachIndexed { i, punto -> appendLine("☐ ${i + 1}. $punto") }
    appendLine()
    appendLine("RESPALDO ESTÁNDAR")
    appendLine("Regla de Oro: $reglaOro")
    appendLine("Normativa: $normativa")
    appendLine()
    appendLine("PREGUNTA DE CIERRE")
    append(preguntaCierre)
}
