package cl.sacyr.rutaitata.excavaciones.reporte

import cl.sacyr.rutaitata.excavaciones.dominio.Agrupador
import cl.sacyr.rutaitata.excavaciones.dominio.ExcavacionDetalle
import cl.sacyr.rutaitata.excavaciones.dominio.Formato
import java.time.LocalDate
import java.util.Locale

/**
 * Planilla de excavaciones para la oficina técnica, en CSV que Excel en español abre directo:
 * separador ";", coma decimal y BOM UTF-8 (sin él, Excel muestra mal las tildes). Una fila por
 * excavación, en el mismo orden que el reporte. Números sin separador de miles, para que Excel
 * los tome como números y se puedan sumar.
 */
object Planilla {

    /** Marca de UTF-8 al inicio: Excel la necesita para leer bien las tildes. */
    const val BOM = '\uFEFF'

    val COLUMNAS = listOf(
        "Fecha", "Sector", "Lado", "Sentido", "N°", "Progresiva", "Largo (m)", "Ancho (m)", "Profundidad (m)",
        "Volumen (m³)", "Tipo de terreno", "Latitud", "Longitud", "Fotos", "Observaciones",
    )

    fun csv(excavaciones: List<ExcavacionDetalle>): String = buildString {
        append(BOM)
        linea(COLUMNAS)
        val ordenadas = Agrupador.agrupar(excavaciones)
            .flatMap { s -> s.lados.flatMap { l -> l.sentidos.flatMap { it.excavaciones } } }
        for (e in ordenadas) {
            linea(
                listOf(
                    Formato.fecha(e.fecha),
                    e.sector,
                    e.lado.etiqueta,
                    e.sentido.etiquetaCorta,
                    e.numero.toString(),
                    e.progresiva.orEmpty(),
                    numero(e.largo),
                    numero(e.ancho),
                    numero(e.profundidad),
                    numero(e.volumen),
                    e.tipoTerreno.orEmpty(),
                    e.latitud?.let { coordenada(it) }.orEmpty(),
                    e.longitud?.let { coordenada(it) }.orEmpty(),
                    e.fotos.toString(),
                    e.observaciones.orEmpty(),
                ),
            )
        }
    }

    /** "Excavaciones_2026-10-15.csv", "Excavaciones_2026-10-01_a_2026-10-15.csv" */
    fun nombreArchivo(desde: LocalDate, hasta: LocalDate): String =
        "Excavaciones_$desde" + (if (desde != hasta) "_a_$hasta" else "") + ".csv"

    private fun StringBuilder.linea(campos: List<String>) {
        campos.joinTo(this, ";") { campo(it) }
        append("\r\n")
    }

    /** Entre comillas si trae ";", comillas o saltos de línea (las comillas internas se duplican). */
    private fun campo(texto: String): String =
        if (texto.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + texto.replace("\"", "\"\"") + "\"" else texto

    private fun numero(valor: Double): String = String.format(Locale.ROOT, "%.2f", valor).replace('.', ',')

    private fun coordenada(valor: Double): String = String.format(Locale.ROOT, "%.6f", valor).replace('.', ',')
}
