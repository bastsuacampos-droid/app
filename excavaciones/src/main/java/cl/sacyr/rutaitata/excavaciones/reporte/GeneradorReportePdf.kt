package cl.sacyr.rutaitata.excavaciones.reporte

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Genera el PDF con el PdfDocument nativo de Android (sin librerías ni licencias externas).
 * La maquetación la hace [MaquetadorReporte]; aquí solo se mide el texto con la misma fuente con
 * que se dibuja y se pinta cada trazo.
 *
 * Llamar fuera del hilo principal (Dispatchers.IO): un reporte de varios días puede tener decenas
 * de páginas.
 */
class GeneradorReportePdf(private val carpeta: File) {

    /** Genera el reporte en [carpeta] (reemplaza uno anterior del mismo nombre) y devuelve el archivo. */
    fun generar(datos: DatosReporte): File {
        val paginas = MaquetadorReporte(Pinceles).maquetar(datos)
        carpeta.mkdirs()
        val destino = File(carpeta, datos.nombreArchivo())
        // Se escribe aparte y se mueve al final: si algo falla no queda un PDF a medias para compartir.
        val temporal = File(carpeta, "${destino.name}.tmp")
        val pdf = PdfDocument()
        try {
            for (pagina in paginas) {
                val info = PdfDocument.PageInfo.Builder(
                    MaquetadorReporte.ANCHO_PAGINA.toInt(),
                    MaquetadorReporte.ALTO_PAGINA.toInt(),
                    pagina.numero,
                ).create()
                val hoja = pdf.startPage(info)
                dibujar(hoja.canvas, pagina.trazos)
                pdf.finishPage(hoja)
            }
            temporal.outputStream().use(pdf::writeTo)
            Files.move(temporal.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            pdf.close()
            temporal.delete()
        }
        return destino
    }

    /** Pinta una página en cualquier Canvas (el del PDF o, en pruebas, un Bitmap). */
    internal fun dibujar(canvas: Canvas, trazos: List<Trazo>) {
        for (trazo in trazos) {
            when (trazo) {
                is Trazo.Rectangulo -> {
                    trazo.relleno?.let {
                        canvas.drawRect(trazo.x, trazo.y, trazo.x + trazo.ancho, trazo.y + trazo.alto, Pinceles.relleno(it))
                    }
                    trazo.borde?.let {
                        canvas.drawRect(trazo.x, trazo.y, trazo.x + trazo.ancho, trazo.y + trazo.alto, Pinceles.linea(it, trazo.grosor))
                    }
                }
                is Trazo.Linea -> canvas.drawLine(trazo.x1, trazo.y1, trazo.x2, trazo.y2, Pinceles.linea(trazo.color, trazo.grosor))
                is Trazo.Texto -> canvas.drawText(trazo.texto, trazo.x, trazo.y, Pinceles.texto(trazo.estilo, trazo.alineacion))
            }
        }
    }

    /** Paint reutilizables. Mide con el mismo Paint con que se dibuja, para que el texto calce en las celdas. */
    internal object Pinceles : MedidorTexto {
        private val normal = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        private val negrita = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        private val textos = HashMap<Pair<Estilo, Alineacion>, Paint>()
        private val lineas = HashMap<Pair<Int, Float>, Paint>()
        private val rellenos = HashMap<Int, Paint>()

        @Synchronized
        fun texto(estilo: Estilo, alineacion: Alineacion = Alineacion.IZQUIERDA): Paint =
            textos.getOrPut(estilo to alineacion) {
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                    typeface = if (estilo.negrita) negrita else normal
                    textSize = estilo.tamano
                    color = estilo.color
                    textAlign = when (alineacion) {
                        Alineacion.IZQUIERDA -> Paint.Align.LEFT
                        Alineacion.CENTRO -> Paint.Align.CENTER
                        Alineacion.DERECHA -> Paint.Align.RIGHT
                    }
                }
            }

        @Synchronized
        fun linea(color: Int, grosor: Float): Paint = lineas.getOrPut(color to grosor) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = grosor
                this.color = color
            }
        }

        @Synchronized
        fun relleno(color: Int): Paint = rellenos.getOrPut(color) {
            Paint().apply {
                style = Paint.Style.FILL
                this.color = color
            }
        }

        override fun ancho(texto: String, estilo: Estilo): Float = texto(estilo).measureText(texto)
    }
}
