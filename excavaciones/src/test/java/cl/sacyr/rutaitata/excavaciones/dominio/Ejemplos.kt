package cl.sacyr.rutaitata.excavaciones.dominio

import java.time.LocalDate

/** Excavación de ejemplo para las pruebas; solo se indica lo que importa en cada caso. */
fun ejemplo(
    numero: Int,
    sector: String = "Sector A",
    sectorId: Long = sector.hashCode().toLong(),
    lado: Lado = Lado.NORTE,
    sentido: Sentido = Sentido.CHILLAN_CONCEPCION,
    fecha: LocalDate = LocalDate.of(2026, 10, 15),
    largo: Double = 2.0,
    ancho: Double = 1.0,
    profundidad: Double = 1.5,
    observaciones: String? = null,
    progresiva: String? = "Km 12+${100 + numero * 10}",
    fotos: Int = 0,
) = ExcavacionDetalle(
    id = numero.toLong() * 1000 + sectorId % 1000,
    fecha = fecha,
    sectorId = sectorId,
    sector = sector,
    lado = lado,
    sentido = sentido,
    numero = numero,
    progresiva = progresiva,
    largo = largo,
    ancho = ancho,
    profundidad = profundidad,
    tipoTerreno = "Suelo Tipo II",
    latitud = -36.61234,
    longitud = -72.10345,
    observaciones = observaciones,
    fotos = fotos,
)
