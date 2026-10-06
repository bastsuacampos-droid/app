package cl.sacyr.rutaitata.charlas.data

data class Especialidad(
    val id: String,
    val nombre: String,
    val colorHex: String,
)

data class Charla(
    val id: Int,
    val semana: Int,
    /** 1 = lunes … 6 = sábado. */
    val dia: Int,
    val especialidadId: String,
    val titulo: String,
    val porQue: String,
    val checklist: List<String>,
    val normativa: String,
    val reglaOro: String,
    val preguntaCierre: String,
)

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
) {
    private val especialidadesPorId = especialidades.associateBy { it.id }

    fun especialidad(id: String): Especialidad =
        especialidadesPorId[id] ?: error("Especialidad desconocida: $id")

    fun charla(id: Int): Charla? = charlas.firstOrNull { it.id == id }

    fun charla(semana: Int, dia: Int): Charla? =
        charlas.firstOrNull { it.semana == semana && it.dia == dia }
}

val NOMBRES_DIA = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")

fun nombreDia(dia: Int): String = NOMBRES_DIA[dia - 1]

/** Texto plano listo para compartir por WhatsApp o correo. */
fun Charla.comoTexto(banco: Banco): String = buildString {
    appendLine("CHARLA 5 MINUTOS – ${banco.proyecto}")
    appendLine("Semana $semana · ${nombreDia(dia)} · ${banco.especialidad(especialidadId).nombre}")
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
