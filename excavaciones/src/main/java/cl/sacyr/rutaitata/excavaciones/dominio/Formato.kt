package cl.sacyr.rutaitata.excavaciones.dominio

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cómo se muestran números, fechas y ubicaciones en la app y en el reporte (formato chileno). */
object Formato {

    private val chile: Locale = Locale.forLanguageTag("es-CL")
    private val simbolos = DecimalFormatSymbols(chile).apply {
        decimalSeparator = ','
        groupingSeparator = '.'
    }
    private val fecha = DateTimeFormatter.ofPattern("dd-MM-yyyy", chile)
    private val fechaHora = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm", chile)

    // DecimalFormat no es seguro entre hilos: uno por llamada.
    private fun dosDecimales() = DecimalFormat("#,##0.00", simbolos)

    /** "1.234,50" */
    fun decimal(valor: Double): String = dosDecimales().format(valor)

    fun volumen(m3: Double): String = "${decimal(m3)} m³"

    /** "4,00 × 1,20 × 1,50" (largo × ancho × profundidad, en metros). */
    fun dimensiones(largo: Double, ancho: Double, profundidad: Double): String =
        "${decimal(largo)} × ${decimal(ancho)} × ${decimal(profundidad)}"

    /** Número correlativo con dos dígitos mínimo: 1 → "01", 125 → "125". */
    fun numero(numero: Int): String = numero.toString().padStart(2, '0')

    fun fecha(valor: LocalDate): String = valor.format(fecha)

    fun fechaHora(valor: LocalDateTime): String = valor.format(fechaHora)

    /** "-36.61234, -72.10345": con punto decimal, porque la coma separa latitud y longitud. */
    fun coordenadas(latitud: Double, longitud: Double): String =
        String.format(Locale.ROOT, "%.5f, %.5f", latitud, longitud)

    /**
     * Lee lo que se escribe en el formulario: acepta coma o punto decimal ("1,5", "1.5") y
     * espacios. Devuelve null si no es un número.
     */
    fun leerDecimal(texto: String): Double? =
        texto.trim().replace(" ", "").replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()
}
