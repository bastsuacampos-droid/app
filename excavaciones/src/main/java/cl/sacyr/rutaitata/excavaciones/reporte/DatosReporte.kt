package cl.sacyr.rutaitata.excavaciones.reporte

import cl.sacyr.rutaitata.excavaciones.dominio.ExcavacionDetalle
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime

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
) {
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
