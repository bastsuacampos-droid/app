package cl.sacyr.rutaitata.excavaciones.reporte

import cl.sacyr.rutaitata.excavaciones.dominio.ExcavacionDetalle
import cl.sacyr.rutaitata.excavaciones.dominio.OrigenFoto
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime

/** Una foto para el anexo: archivo ya orientado, su tamaño en píxeles, cuándo se tomó y de dónde vino. */
data class FotoReporte(
    val ruta: String,
    val ancho: Int,
    val alto: Int,
    val tomadaEn: LocalDateTime?,
    val origen: OrigenFoto? = null,
) {
    init {
        require(ancho > 0 && alto > 0) { "Foto sin tamaño: $ruta" }
    }
}

/** Día cerrado al emitir un reporte definitivo. */
data class CierreReporte(val fecha: LocalDate, val cerradoEn: LocalDateTime, val responsable: String)

/** Un día cerrado que se reabrió para corregirlo: queda constancia en el reporte. */
data class ReaperturaReporte(val fecha: LocalDate, val cerradoEn: LocalDateTime, val reabiertoEn: LocalDateTime, val motivo: String)

/** Todo lo que necesita el reporte. Las excavaciones ya vienen filtradas por fecha y sector. */
data class DatosReporte(
    val desde: LocalDate,
    val hasta: LocalDate = desde,
    /** Nombre del sector filtrado, o null si el reporte incluye todos. */
    val sector: String? = null,
    /** Responsable o inspector de obra que emite el reporte. */
    val responsable: String,
    val obra: String = OBRA,
    val emitidoEn: LocalDateTime = LocalDateTime.now(),
    val excavaciones: List<ExcavacionDetalle>,
    /** Si el reporte lleva el anexo fotográfico. */
    val incluirFotos: Boolean = false,
    /** Fotos por id de excavación, en orden (solo se usan con [incluirFotos]). */
    val fotos: Map<Long, List<FotoReporte>> = emptyMap(),
    /** Días cerrados del período. */
    val cierres: List<CierreReporte> = emptyList(),
    val reaperturas: List<ReaperturaReporte> = emptyList(),
) {
    /**
     * Días del reporte que siguen abiertos (sus registros todavía pueden cambiar): los que tienen
     * excavaciones, o todos los del período si no hay ninguna.
     */
    val diasAbiertos: List<LocalDate>
        get() {
            val dias = excavaciones.map { it.fecha }.distinct().sorted()
                .ifEmpty { generateSequence(desde) { it.plusDays(1) }.takeWhile { !it.isAfter(hasta) }.toList() }
            val cerrados = cierres.map { it.fecha }.toSet()
            return dias.filter { it !in cerrados }
        }

    /** Definitivo: todos sus días están cerrados. */
    val cerrado: Boolean get() = diasAbiertos.isEmpty()

    init {
        require(!hasta.isBefore(desde)) { "El reporte termina ($hasta) antes de empezar ($desde)" }
    }

    val variosDias: Boolean get() = desde != hasta

    /** "Reporte_Excavaciones_2026-10-15.pdf", "Reporte_Excavaciones_2026-10-01_a_2026-10-15_Sector-A.pdf" */
    fun nombreArchivo(): String = buildString {
        append("Reporte_Excavaciones_").append(desde)
        if (variosDias) append("_a_").append(hasta)
        sector?.let { append('_').append(simplificar(it)) }
        append(".pdf")
    }

    companion object {
        const val OBRA = "Mejoramiento de la Ruta Itata"

        /** Solo letras ASCII, dígitos y guiones: algunos destinos (correo, WhatsApp) cambian el resto. */
        private fun simplificar(texto: String): String =
            Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^A-Za-z0-9]+"), "-")
                .trim('-')
                .take(40)
                .ifEmpty { "sector" }
    }
}
