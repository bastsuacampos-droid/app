package cl.sacyr.rutaitata.excavaciones.dominio

/**
 * Calzada en que se ejecuta la excavación. El [codigo] es lo que se guarda en la base de datos:
 * nunca cambiarlo (la etiqueta sí se puede corregir). El orden de declaración es el del informe.
 */
enum class Lado(val codigo: String, val etiqueta: String) {
    NORTE("N", "Lado Norte"),
    SUR("S", "Lado Sur");

    companion object {
        fun desdeCodigo(codigo: String): Lado =
            entries.firstOrNull { it.codigo == codigo } ?: error("Lado desconocido: $codigo")
    }
}

/**
 * Sentido de avance en que se numeran las excavaciones de un frente. Mismas reglas que [Lado]
 * para el [codigo]. La [etiquetaCorta] cabe en encabezados y botones.
 */
enum class Sentido(val codigo: String, val etiqueta: String, val etiquetaCorta: String) {
    CHILLAN_CONCEPCION("CH-CO", "Chillán → Concepción", "Chillán-Concepción"),
    CONCEPCION_CHILLAN("CO-CH", "Concepción → Chillán", "Concepción-Chillán");

    companion object {
        fun desdeCodigo(codigo: String): Sentido =
            entries.firstOrNull { it.codigo == codigo } ?: error("Sentido desconocido: $codigo")
    }
}
