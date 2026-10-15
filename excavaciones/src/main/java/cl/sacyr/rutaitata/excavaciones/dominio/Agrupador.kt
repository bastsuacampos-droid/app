package cl.sacyr.rutaitata.excavaciones.dominio

/** Nivel 3: excavaciones de un sentido de avance, en orden correlativo. */
data class GrupoSentido(val sentido: Sentido, val excavaciones: List<ExcavacionDetalle>) {
    val cantidad: Int get() = excavaciones.size
    val volumen: Double get() = Volumen.total(excavaciones)
}

/** Nivel 2: una calzada del sector. */
data class GrupoLado(val lado: Lado, val sentidos: List<GrupoSentido>) {
    val cantidad: Int get() = sentidos.sumOf { it.cantidad }
    val volumen: Double get() = sentidos.sumOf { it.volumen }
}

/** Nivel 1: sector o tramo. */
data class GrupoSector(val sectorId: Long, val nombre: String, val lados: List<GrupoLado>) {
    val cantidad: Int get() = lados.sumOf { it.cantidad }
    val volumen: Double get() = lados.sumOf { it.volumen }
}

/**
 * Arma la jerarquía Sector → Lado → Sentido → Excavación que usan el resumen diario y el PDF, para
 * que ambos muestren lo mismo en el mismo orden: sectores en orden natural ("Km 9" antes que
 * "Km 12"), lados y sentidos en el orden de sus enum, excavaciones por número (y fecha, si un
 * reporte de varios días trae el mismo número en dos fechas, lo que solo pasa si se corrigió uno).
 */
object Agrupador {

    fun agrupar(excavaciones: List<ExcavacionDetalle>): List<GrupoSector> =
        excavaciones.groupBy { it.sectorId }
            .map { (sectorId, delSector) ->
                GrupoSector(
                    sectorId = sectorId,
                    nombre = delSector.first().sector,
                    lados = delSector.groupBy { it.lado }.toSortedMap().map { (lado, delLado) ->
                        GrupoLado(
                            lado = lado,
                            sentidos = delLado.groupBy { it.sentido }.toSortedMap().map { (sentido, delSentido) ->
                                GrupoSentido(sentido, delSentido.sortedWith(compareBy({ it.numero }, { it.fecha })))
                            },
                        )
                    },
                )
            }
            .sortedWith(compareBy(OrdenNatural) { it.nombre })
}

/**
 * Compara textos tratando cada grupo de dígitos como número y sin distinguir mayúsculas:
 * "Sector 2" < "Sector 10", "Km 9+800" < "Km 12+400".
 */
object OrdenNatural : Comparator<String> {
    private val trozos = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val ta = trozos.findAll(a.lowercase()).map { it.value }.toList()
        val tb = trozos.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ta.size, tb.size)) {
            val x = ta[i]
            val y = tb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) {
                x.trimStart('0').length.compareTo(y.trimStart('0').length)
                    .takeIf { it != 0 } ?: x.trimStart('0').compareTo(y.trimStart('0'))
            } else {
                x.compareTo(y)
            }
            if (c != 0) return c
        }
        return ta.size.compareTo(tb.size).takeIf { it != 0 } ?: a.compareTo(b)
    }
}
