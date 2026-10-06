package cl.rutaitata.charlas.data

enum class Especialidad(val nombre: String, val descripcion: String) {
    MAQUINARIA(
        "Maquinaria Pesada",
        "Operadores, señaleros y personal de apoyo a excavadoras, motoniveladoras, rodillos y camiones tolva."
    ),
    TRANSITO(
        "Control de Tránsito / Paleteros",
        "Paleteros, banderilleros y cuadrillas de instalación de señalización y desvíos en ruta."
    ),
    ASFALTO(
        "Cuadrillas de Asfalto",
        "Pavimentación asfáltica: riegos, terminadora, compactación y rastrillado."
    ),
    OBRAS_DE_ARTE(
        "Obras de Arte / Manuales",
        "Alcantarillas, muros, fosos, hormigones, moldajes y trabajos manuales."
    ),
    TRANSVERSAL(
        "Riesgos Transversales / Clima",
        "Riesgos que aplican a toda la obra: clima del sur, fatiga, traslados y emergencias."
    ),
}

val DIAS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")

data class Charla(
    val numero: Int,
    val semana: Int,
    /** 0 = lunes … 5 = sábado */
    val dia: Int,
    val especialidad: Especialidad,
    val titulo: String,
    val porQue: String,
    val puntosControl: List<String>,
    val respaldo: String,
    val preguntaCierre: String,
) {
    val diaNombre: String get() = DIAS[dia]
}

fun Charla.comoTexto(): String = buildString {
    appendLine("CHARLA 5 MINUTOS N° $numero – Mejoramiento Ruta Itata")
    appendLine("Semana $semana · $diaNombre · ${especialidad.nombre}")
    appendLine()
    appendLine(titulo.uppercase())
    appendLine()
    appendLine("EL POR QUÉ:")
    appendLine(porQue)
    appendLine()
    appendLine("PUNTOS DE CONTROL (antes de partir):")
    puntosControl.forEachIndexed { i, p -> appendLine("${i + 1}. $p") }
    appendLine()
    appendLine("RESPALDO ESTÁNDAR:")
    appendLine(respaldo)
    appendLine()
    appendLine("PREGUNTA DE CIERRE:")
    append(preguntaCierre)
}
