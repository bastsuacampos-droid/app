package cl.sacyr.rutaitata.excavaciones.reporte

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.scale
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

    data class ReporteGenerado(val archivo: File, val paginas: Int)

    /** Genera el reporte en [carpeta] (reemplaza uno anterior del mismo nombre). */
    fun generar(datos: DatosReporte): ReporteGenerado {
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
                val fotos = dibujar(hoja.canvas, pagina.trazos)
                pdf.finishPage(hoja)
                // Recién ahora: la página guarda referencias a los bitmaps hasta que se termina.
                fotos.forEach(Bitmap::recycle)
            }
            temporal.outputStream().use(pdf::writeTo)
            Files.move(temporal.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            pdf.close()
            temporal.delete()
        }
        return ReporteGenerado(destino, paginas.size)
    }

    /**
     * Pinta una página en cualquier Canvas (el del PDF o, en pruebas, un Bitmap). Devuelve los
     * bitmaps de fotos usados, para liberarlos cuando la página esté terminada.
     */
    internal fun dibujar(canvas: Canvas, trazos: List<Trazo>): List<Bitmap> {
        val fotos = mutableListOf<Bitmap>()
        for (trazo in trazos) {
            when (trazo) {
                is Trazo.Imagen -> {
                    val destino = RectF(trazo.x, trazo.y, trazo.x + trazo.ancho, trazo.y + trazo.alto)
                    val foto = cargarFoto(trazo.ruta, (trazo.ancho * PIXELES_POR_PUNTO).toInt(), (trazo.alto * PIXELES_POR_PUNTO).toInt())
                    if (foto != null) {
                        canvas.drawBitmap(foto, null, destino, Pinceles.imagen)
                        fotos += foto
                    } else {
                        canvas.drawRect(destino, Pinceles.relleno(Colores.ENCABEZADO_TABLA))
                        val estilo = Estilo(8f, color = Colores.SECUNDARIO)
                        canvas.drawText("Foto no disponible", destino.centerX(), destino.centerY(), Pinceles.texto(estilo, Alineacion.CENTRO))
                    }
                }
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
        return fotos
    }

    /**
     * Lee la foto reducida al tamaño con que se imprime: el PDF guarda los píxeles tal cual, así
     * que una foto de 12 MP lo haría pesar decenas de MB. null si el archivo falta o está dañado.
     */
    private fun cargarFoto(ruta: String, anchoPx: Int, altoPx: Int): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ruta, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        var muestreo = 1
        while (limites.outWidth / (muestreo * 2) >= anchoPx && limites.outHeight / (muestreo * 2) >= altoPx) muestreo *= 2
        val leida = BitmapFactory.decodeFile(ruta, BitmapFactory.Options().apply { inSampleSize = muestreo }) ?: return null
        if (leida.width <= anchoPx && leida.height <= altoPx) return leida
        return leida.scale(anchoPx.coerceAtLeast(1), altoPx.coerceAtLeast(1))
            .also { if (it !== leida) leida.recycle() }
    }

    companion object {
        /** Resolución de las fotos en el PDF: 1,6 píxeles por punto ≈ 115 ppp, nítido en pantalla e impreso. */
        const val PIXELES_POR_PUNTO = 1.6f
    }

    /** Paint reutilizables. Mide con el mismo Paint con que se dibuja, para que el texto calce en las celdas. */
    internal object Pinceles : MedidorTexto {
        private val normal = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        private val negrita = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        private val textos = HashMap<Pair<Estilo, Alineacion>, Paint>()
        private val lineas = HashMap<Pair<Int, Float>, Paint>()
        private val rellenos = HashMap<Int, Paint>()
        val imagen = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

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
