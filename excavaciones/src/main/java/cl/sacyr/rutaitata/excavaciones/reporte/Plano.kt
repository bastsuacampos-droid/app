package cl.sacyr.rutaitata.excavaciones.reporte

/*
 * Primitivas de dibujo del reporte, sin dependencias de Android: MaquetadorReporte produce una lista
 * de páginas con estos trazos y GeneradorReportePdf los escribe como PDF. Así toda la
 * maquetación (agrupación, saltos de página, totales, firmas) se prueba en la JVM.
 *
 * Coordenadas en puntos PostScript (1/72"), origen arriba a la izquierda. En Texto, y es la línea
 * base, y x es el borde izquierdo, el centro o el borde derecho según la alineación.
 */

data class Estilo(val tamano: Float, val negrita: Boolean = false, val color: Int = Colores.TEXTO)

enum class Alineacion { IZQUIERDA, CENTRO, DERECHA }

sealed interface Trazo {
    data class Texto(
        val x: Float,
        val y: Float,
        val texto: String,
        val estilo: Estilo,
        val alineacion: Alineacion = Alineacion.IZQUIERDA,
    ) : Trazo

    data class Linea(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val grosor: Float = 0.5f,
        val color: Int = Colores.BORDE,
    ) : Trazo

    /** Foto en [ruta], ya ajustada a este rectángulo con su proporción original. */
    data class Imagen(
        val x: Float,
        val y: Float,
        val ancho: Float,
        val alto: Float,
        val ruta: String,
    ) : Trazo

    data class Rectangulo(
        val x: Float,
        val y: Float,
        val ancho: Float,
        val alto: Float,
        val relleno: Int? = null,
        val borde: Int? = null,
        val grosor: Float = 0.5f,
    ) : Trazo
}

data class Pagina(val numero: Int, val trazos: List<Trazo>)

/** Ancho en puntos de un texto con un estilo. En Android lo da Paint.measureText. */
fun interface MedidorTexto {
    fun ancho(texto: String, estilo: Estilo): Float
}

/** Paleta sobria, legible también impresa en blanco y negro. Colores ARGB. */
object Colores {
    const val TEXTO = 0xFF1A1A1A.toInt()
    const val SECUNDARIO = 0xFF5A5F66.toInt()
    const val BORDE = 0xFF9AA0A6.toInt()
    const val BLANCO = 0xFFFFFFFF.toInt()
    const val BANDA_SECTOR = 0xFF37474F.toInt()
    const val ENCABEZADO_TABLA = 0xFFDDE3E8.toInt()
    const val FILA_ALTERNA = 0xFFF5F7F8.toInt()
    const val SUBTOTAL = 0xFFECEFF1.toInt()
    const val SIN_FOTO = 0xFFB3261E.toInt()
    const val CERRADO = 0xFF2E7D32.toInt()
}
