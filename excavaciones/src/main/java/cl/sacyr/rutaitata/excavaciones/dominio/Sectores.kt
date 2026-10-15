package cl.sacyr.rutaitata.excavaciones.dominio

import java.text.Normalizer

/**
 * Detecta sectores escritos casi igual que uno existente ("Sectro A", "sector  a", "Séctor A" en
 * vez de "Sector A"), para ofrecer el existente antes de crear un duplicado.
 */
object Sectores {

    /** Sin tildes, mayúsculas ni signos, y espacios simples: "Km 12+400" → "km 12 400". */
    fun normalizar(nombre: String): String =
        Normalizer.normalize(nombre, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /**
     * El sector existente que probablemente se quiso escribir, o null si [escrito] ya existe tal
     * cual (sin distinguir mayúsculas) o no se parece a ninguno. Parecido: igual al normalizar, o a
     * una o dos letras de distancia en nombres de 5 letras o más (en uno corto, "Km 1" y "Km 2"
     * son sectores distintos de verdad).
     */
    fun parecido(escrito: String, existentes: List<String>): String? {
        val limpio = escrito.trim()
        if (limpio.isEmpty() || existentes.any { it.equals(limpio, ignoreCase = true) }) return null
        val n = normalizar(limpio)
        existentes.firstOrNull { normalizar(it) == n }?.let { return it }
        if (n.length < 5) return null
        return existentes
            .map { it to distancia(n, normalizar(it)) }
            .filter { (otro, d) -> d <= 2 && normalizar(otro).length >= 5 && !difierenEnNumero(n, normalizar(otro)) }
            .minByOrNull { it.second }
            ?.first
    }

    /** "km 12 400" y "km 12 600" son tramos distintos: si cambian los números, no es un error de tipeo. */
    private fun difierenEnNumero(a: String, b: String): Boolean =
        Regex("\\d+").findAll(a).map { it.value }.toList() != Regex("\\d+").findAll(b).map { it.value }.toList()

    /** Distancia de edición (Levenshtein) con trasposición de letras vecinas ("Sectro" → "Sector" = 1). */
    private fun distancia(a: String, b: String): Int {
        val d = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
        for (i in 1..a.length) for (j in 1..b.length) {
            val costo = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + costo)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }
}
