package cl.sacyr.rutaitata.excavaciones.dominio

import java.time.LocalDate

/**
 * Una excavación con los nombres de su sector y tipo de terreno ya resueltos: lo que muestran el
 * resumen diario y el reporte PDF. Room la llena con `ExcavacionDao.SELECT_DETALLE` (los nombres de
 * las propiedades son las columnas de esa consulta).
 */
data class ExcavacionDetalle(
    val id: Long,
    val fecha: LocalDate,
    val sectorId: Long,
    val sector: String,
    val lado: Lado,
    val sentido: Sentido,
    val numero: Int,
    /** Ubicación según el kilometraje de la ruta ("Km 12+450"), si se anotó. */
    val progresiva: String?,
    val largo: Double,
    val ancho: Double,
    val profundidad: Double,
    val tipoTerreno: String?,
    val latitud: Double?,
    val longitud: Double?,
    val observaciones: String?,
    val fotos: Int,
) {
    val volumen: Double get() = Volumen.de(largo, ancho, profundidad)
}

object Volumen {
    /** Volumen geométrico en m³ (prisma rectangular). Se suma sin redondear; se redondea solo al mostrar. */
    fun de(largo: Double, ancho: Double, profundidad: Double): Double = largo * ancho * profundidad

    fun total(excavaciones: Iterable<ExcavacionDetalle>): Double = excavaciones.sumOf { it.volumen }
}
