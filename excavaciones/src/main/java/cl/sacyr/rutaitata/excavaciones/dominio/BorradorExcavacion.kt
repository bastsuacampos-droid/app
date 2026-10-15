package cl.sacyr.rutaitata.excavaciones.dominio

import java.io.Serializable
import java.time.LocalDate

/** Máximo de fotos por excavación. */
const val MAX_FOTOS = 3

/** Una foto ya guardada en files/fotos/[archivo]. [tomadaEn] en epoch ms, si se conoce. */
data class FotoBorrador(val archivo: String, val tomadaEn: Long? = null) : Serializable

/**
 * Lo que el formulario entrega para guardar (registro nuevo si [id] es 0). Los campos son
 * anulables porque así quedan mientras se llena; [ValidadorExcavacion] decide qué falta.
 */
data class BorradorExcavacion(
    val id: Long = 0,
    val fecha: LocalDate = LocalDate.now(),
    val sector: String = "",
    val lado: Lado? = null,
    val sentido: Sentido? = null,
    /** null: el repositorio asigna el siguiente número del frente (sector + lado + sentido). */
    val numero: Int? = null,
    val progresiva: String? = null,
    val largo: Double? = null,
    val ancho: Double? = null,
    val profundidad: Double? = null,
    val tipoTerrenoId: Long? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val precisionGps: Float? = null,
    val observaciones: String? = null,
    /** Fotos en el orden en que se muestran (y salen en el reporte). */
    val fotos: List<FotoBorrador> = emptyList(),
) {
    val volumen: Double?
        get() = if (largo != null && ancho != null && profundidad != null) Volumen.de(largo, ancho, profundidad) else null
}

enum class Campo { SECTOR, LADO, SENTIDO, NUMERO, LARGO, ANCHO, PROFUNDIDAD, TIPO_TERRENO, UBICACION, FOTOS }

class ExcavacionInvalida(val errores: Map<Campo, String>) :
    IllegalArgumentException(errores.values.joinToString("; "))

object ValidadorExcavacion {

    // Topes para detectar errores de digitación (150 en vez de 1,50), no límites técnicos.
    const val LARGO_MAXIMO = 1000.0
    const val ANCHO_MAXIMO = 100.0
    const val PROFUNDIDAD_MAXIMA = 30.0

    /** Errores por campo, con el texto que ve el usuario. Vacío si se puede guardar. */
    fun validar(b: BorradorExcavacion): Map<Campo, String> = buildMap {
        if (b.sector.isBlank()) put(Campo.SECTOR, "Indica el sector o tramo")
        if (b.lado == null) put(Campo.LADO, "Elige Lado Norte o Lado Sur")
        if (b.sentido == null) put(Campo.SENTIDO, "Elige el sentido de avance")
        if (b.numero != null && b.numero < 1) put(Campo.NUMERO, "El número debe ser 1 o mayor")
        medida(b.largo, LARGO_MAXIMO, "largo")?.let { put(Campo.LARGO, it) }
        medida(b.ancho, ANCHO_MAXIMO, "ancho")?.let { put(Campo.ANCHO, it) }
        medida(b.profundidad, PROFUNDIDAD_MAXIMA, "profundidad")?.let { put(Campo.PROFUNDIDAD, it) }
        if (b.tipoTerrenoId == null) put(Campo.TIPO_TERRENO, "Elige el tipo de terreno")
        if ((b.latitud == null) != (b.longitud == null) ||
            (b.latitud != null && (b.latitud !in -90.0..90.0 || b.longitud!! !in -180.0..180.0))
        ) {
            put(Campo.UBICACION, "Ubicación GPS inválida; vuelve a obtenerla")
        }
        if (b.fotos.size > MAX_FOTOS) put(Campo.FOTOS, "Máximo $MAX_FOTOS fotos por excavación")
    }

    private fun medida(valor: Double?, maximo: Double, nombre: String): String? = when {
        valor == null || valor.isNaN() -> "Indica el $nombre en metros"
        valor <= 0 -> "El $nombre debe ser mayor que 0"
        valor > maximo -> "El $nombre supera ${Formato.decimal(maximo)} m; revisa la coma decimal"
        else -> null
    }
}
