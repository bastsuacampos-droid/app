package cl.sacyr.rutaitata.excavaciones.reporte

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.format.DateTimeFormatter

/**
 * Genera el PDF escribiéndolo directamente, sin PdfDocument ni librerías externas. El PdfDocument
 * de Android guarda las fotos como píxeles sin compresión con pérdida, así que para que el reporte
 * no pesara decenas de MB había que reducirlas a ~350 px y no se veían los detalles. Aquí cada foto
 * va como JPEG (lo que da [fotos], ya reducida a [ArchivosFotos.LADO_PDF]) y se puede ampliar en
 * el visor para revisar la excavación.
 *
 * La maquetación la hace [MaquetadorReporte]; el texto se mide con las mismas fuentes que se
 * incrustan (Roboto, solo los glifos usados), así calza igual en el teléfono y en las pruebas.
 *
 * Todo es Kotlin puro: se prueba completo en la JVM. Llamar fuera del hilo principal.
 */
class GeneradorReportePdf(
    private val carpeta: File,
    private val fuentes: FuentesPdf,
    /** JPEG de la foto en esa ruta para incrustar, o null si falta o está dañada. */
    private val fotos: (String) -> ByteArray?,
) {

    data class ReporteGenerado(val archivo: File, val paginas: Int)

    /** Genera el reporte en [carpeta] (reemplaza uno anterior del mismo nombre). */
    fun generar(datos: DatosReporte): ReporteGenerado {
        val paginas = MaquetadorReporte(fuentes).maquetar(datos)
        carpeta.mkdirs()
        val destino = File(carpeta, datos.nombreArchivo())
        // Se escribe aparte y se mueve al final: si algo falla no queda un PDF a medias para compartir.
        val temporal = File(carpeta, "${destino.name}.tmp")
        try {
            temporal.outputStream().use { escribir(paginas, datos, it) }
            Files.move(temporal.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporal.delete()
        }
        return ReporteGenerado(destino, paginas.size)
    }

    internal fun escribir(paginas: List<Pagina>, datos: DatosReporte, salida: OutputStream) {
        EscritorPdf(salida).use { pdf ->
            val catalogo = pdf.reservar()
            val arbol = pdf.reservar()
            val info = pdf.reservar()
            val regular = FuenteIncrustada(fuentes.regular, "F1", "AAAAAA", pdf.reservar())
            val negrita = FuenteIncrustada(fuentes.negrita, "F2", "AAAAAB", pdf.reservar())
            val hojas = paginas.map { pagina ->
                val lienzo = Lienzo(regular, negrita)
                val imagenes = StringBuilder()
                for (trazo in pagina.trazos) {
                    if (trazo is Trazo.Imagen) {
                        val jpeg = fotos(trazo.ruta)
                        val formato = jpeg?.let(InfoJpeg::de)?.takeIf { it.componentes == 1 || it.componentes == 3 }
                        if (jpeg != null && formato != null) {
                            val n = pdf.reservar()
                            val espacio = if (formato.componentes == 1) "/DeviceGray" else "/DeviceRGB"
                            pdf.flujo(
                                n,
                                "/Type /XObject /Subtype /Image /Width ${formato.ancho} /Height ${formato.alto} " +
                                    "/ColorSpace $espacio /BitsPerComponent 8 /Filter /DCTDecode",
                                jpeg,
                                comprimir = false,
                            )
                            val nombre = "Im$n"
                            imagenes.append(" /").append(nombre).append(' ').append(n).append(" 0 R")
                            lienzo.imagen(trazo, nombre)
                        } else {
                            lienzo.fotoNoDisponible(trazo)
                        }
                    } else {
                        lienzo.trazo(trazo)
                    }
                }
                val contenido = pdf.reservar()
                pdf.flujo(contenido, "", lienzo.contenido())
                val hoja = pdf.reservar()
                pdf.objeto(
                    hoja,
                    "<< /Type /Page /Parent $arbol 0 R /MediaBox [0 0 ${EscritorPdf.numero(ANCHO)} ${EscritorPdf.numero(ALTO)}] " +
                        "/Resources << /Font << /F1 ${regular.numero} 0 R /F2 ${negrita.numero} 0 R >>" +
                        (if (imagenes.isEmpty()) "" else " /XObject <<$imagenes >>") +
                        " >> /Contents $contenido 0 R >>",
                )
                hoja
            }
            regular.escribir(pdf)
            negrita.escribir(pdf)
            pdf.objeto(arbol, "<< /Type /Pages /Kids [${hojas.joinToString(" ") { "$it 0 R" }}] /Count ${hojas.size} >>")
            pdf.objeto(catalogo, "<< /Type /Catalog /Pages $arbol 0 R >>")
            val fecha = datos.emitidoEn.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            pdf.objeto(
                info,
                "<< /Title ${EscritorPdf.textoUnicode("${MaquetadorReporte.TITULO} · ${datos.obra}")} " +
                    "/Author ${EscritorPdf.textoUnicode(datos.responsable)} /Creator (Excavaciones Ruta Itata) " +
                    "/CreationDate (D:$fecha) >>",
            )
            pdf.terminar(catalogo, info)
        }
    }

    /** Operadores de dibujo de una página. El PDF tiene el origen abajo; el maquetador, arriba. */
    private inner class Lienzo(private val regular: FuenteIncrustada, private val negrita: FuenteIncrustada) {
        private val s = StringBuilder()

        fun contenido(): ByteArray = s.toString().toByteArray(Charsets.ISO_8859_1)

        fun trazo(t: Trazo) {
            when (t) {
                is Trazo.Texto -> texto(t.x, t.y, t.texto, t.estilo, t.alineacion)
                is Trazo.Linea -> {
                    s.append(EscritorPdf.color(t.color)).append(" RG ").append(n(t.grosor)).append(" w ")
                        .append(n(t.x1)).append(' ').append(n(ALTO - t.y1)).append(" m ")
                        .append(n(t.x2)).append(' ').append(n(ALTO - t.y2)).append(" l S\n")
                }
                is Trazo.Rectangulo -> rectangulo(t.x, t.y, t.ancho, t.alto, t.relleno, t.borde, t.grosor)
                is Trazo.Imagen -> fotoNoDisponible(t)
            }
        }

        fun imagen(t: Trazo.Imagen, nombre: String) {
            s.append("q ").append(n(t.ancho)).append(" 0 0 ").append(n(t.alto)).append(' ')
                .append(n(t.x)).append(' ').append(n(ALTO - t.y - t.alto)).append(" cm /").append(nombre).append(" Do Q\n")
        }

        fun fotoNoDisponible(t: Trazo.Imagen) {
            rectangulo(t.x, t.y, t.ancho, t.alto, Colores.ENCABEZADO_TABLA, null, 0f)
            texto(t.x + t.ancho / 2, t.y + t.alto / 2, "Foto no disponible", Estilo(8f, color = Colores.SECUNDARIO), Alineacion.CENTRO)
        }

        private fun rectangulo(x: Float, y: Float, ancho: Float, alto: Float, relleno: Int?, borde: Int?, grosor: Float) {
            val caja = "${n(x)} ${n(ALTO - y - alto)} ${n(ancho)} ${n(alto)} re"
            relleno?.let { s.append(EscritorPdf.color(it)).append(" rg ").append(caja).append(" f\n") }
            borde?.let { s.append(EscritorPdf.color(it)).append(" RG ").append(n(grosor)).append(" w ").append(caja).append(" S\n") }
        }

        private fun texto(x: Float, y: Float, texto: String, estilo: Estilo, alineacion: Alineacion) {
            if (texto.isEmpty()) return
            val fuente = if (estilo.negrita) negrita else regular
            val ancho = fuentes.ancho(texto, estilo)
            var cx = when (alineacion) {
                Alineacion.IZQUIERDA -> x
                Alineacion.CENTRO -> x - ancho / 2
                Alineacion.DERECHA -> x - ancho
            }
            val base = ALTO - y
            val color = EscritorPdf.color(estilo.color)
            for (tramo in FuentesPdf.tramos(texto, fuente.fuente)) {
                if (tramo == null) {
                    flecha(cx, base, estilo, color)
                    cx += FuentesPdf.ANCHO_FLECHA * estilo.tamano
                    continue
                }
                val glifos = FuentesPdf.glifos(tramo, fuente.fuente)
                s.append("BT /").append(fuente.recurso).append(' ').append(n(estilo.tamano)).append(" Tf ")
                    .append(color).append(" rg 1 0 0 1 ").append(n(cx)).append(' ').append(n(base)).append(" Tm <")
                for ((glifo, caracter) in glifos) {
                    fuente.usar(glifo, caracter)
                    s.append(HEX[(glifo shr 12) and 0xF]).append(HEX[(glifo shr 8) and 0xF])
                        .append(HEX[(glifo shr 4) and 0xF]).append(HEX[glifo and 0xF])
                }
                s.append("> Tj ET\n")
                cx += glifos.sumOf { fuente.fuente.ancho(it.first).toDouble() }.toFloat() * estilo.tamano / 1000f
            }
        }

        /** "→" no está en Roboto: se dibuja con trazos en el espacio que el medidor le reservó. */
        private fun flecha(x: Float, base: Float, estilo: Estilo, color: String) {
            val t = estilo.tamano
            val eje = base + 0.3f * t
            val desde = x + 0.1f * t
            val hasta = x + FuentesPdf.ANCHO_FLECHA * t - 0.12f * t
            val punta = 0.22f * t
            s.append("q ").append(color).append(" RG ").append(n(t * if (estilo.negrita) 0.09f else 0.07f)).append(" w 1 J 1 j ")
                .append(n(desde)).append(' ').append(n(eje)).append(" m ").append(n(hasta)).append(' ').append(n(eje)).append(" l ")
                .append(n(hasta - punta)).append(' ').append(n(eje + punta)).append(" m ")
                .append(n(hasta)).append(' ').append(n(eje)).append(" l ")
                .append(n(hasta - punta)).append(' ').append(n(eje - punta)).append(" l S Q\n")
        }

        private fun n(v: Float) = EscritorPdf.numero(v)
    }

    /** Una fuente dentro del PDF: anota los glifos usados y al final incrusta solo esos. */
    private class FuenteIncrustada(val fuente: FuenteTrueType, val recurso: String, etiqueta: String, val numero: Int) {
        private val usados = sortedMapOf<Int, Int>()
        private val nombre = "$etiqueta+${fuente.nombre}"

        fun usar(glifo: Int, caracter: Int) {
            usados.putIfAbsent(glifo, caracter)
        }

        fun escribir(pdf: EscritorPdf) {
            val descendiente = pdf.reservar()
            val descriptor = pdf.reservar()
            val archivo = pdf.reservar()
            val unicode = pdf.reservar()
            pdf.objeto(
                numero,
                "<< /Type /Font /Subtype /Type0 /BaseFont /$nombre /Encoding /Identity-H " +
                    "/DescendantFonts [$descendiente 0 R] /ToUnicode $unicode 0 R >>",
            )
            val anchos = usados.keys.joinToString(" ") { "$it [${Math.round(fuente.ancho(it))}]" }
            pdf.objeto(
                descendiente,
                "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /$nombre " +
                    "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> " +
                    "/FontDescriptor $descriptor 0 R /CIDToGIDMap /Identity /DW 0 /W [$anchos] >>",
            )
            val c = fuente.caja.map(fuente::milesimas)
            pdf.objeto(
                descriptor,
                "<< /Type /FontDescriptor /FontName /$nombre /Flags 32 /FontBBox [${c.joinToString(" ")}] " +
                    "/ItalicAngle 0 /Ascent ${fuente.milesimas(fuente.ascendente)} /Descent ${fuente.milesimas(fuente.descendente)} " +
                    "/CapHeight ${fuente.milesimas(fuente.alturaMayusculas)} /StemV 80 /FontFile2 $archivo 0 R >>",
            )
            val ttf = fuente.subconjunto(usados.keys)
            pdf.flujo(archivo, "/Length1 ${ttf.size}", ttf)
            pdf.flujo(unicode, "", toUnicode().toByteArray(Charsets.ISO_8859_1))
        }

        /** Para copiar y buscar texto en el PDF: de cada glifo, su carácter. */
        private fun toUnicode(): String = buildString {
            append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
            append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
            append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
            append("1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n")
            for (bloque in usados.entries.chunked(100)) {
                append(bloque.size).append(" beginbfchar\n")
                for ((glifo, caracter) in bloque) {
                    val utf16 = String(Character.toChars(caracter)).toByteArray(Charsets.UTF_16BE)
                    append('<').append(EscritorPdf.hex(byteArrayOf((glifo shr 8).toByte(), glifo.toByte())))
                        .append("> <").append(EscritorPdf.hex(utf16)).append(">\n")
                }
                append("endbfchar\n")
            }
            append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
        }
    }

    companion object {
        private const val ANCHO = MaquetadorReporte.ANCHO_PAGINA
        private const val ALTO = MaquetadorReporte.ALTO_PAGINA
        private const val HEX = "0123456789ABCDEF"
    }
}

/**
 * Las dos fuentes del reporte y la medida del texto con ellas. "→" (sentido de avance) no está en
 * Roboto: se mide con un ancho fijo y el generador la dibuja con trazos. Otro carácter que falte
 * (un emoji en las observaciones) sale como "?".
 */
class FuentesPdf(val regular: FuenteTrueType, val negrita: FuenteTrueType) : MedidorTexto {

    override fun ancho(texto: String, estilo: Estilo): Float {
        val fuente = if (estilo.negrita) negrita else regular
        var milesimas = 0f
        for (tramo in tramos(texto, fuente)) {
            milesimas += if (tramo == null) ANCHO_FLECHA * 1000f else glifos(tramo, fuente).sumOf { fuente.ancho(it.first).toDouble() }.toFloat()
        }
        return milesimas * estilo.tamano / 1000f
    }

    companion object {
        const val FLECHA = '→'.code
        /** Ancho de la flecha dibujada, en emes. */
        const val ANCHO_FLECHA = 0.9f

        /** Parte el texto en tramos que se escriben con la fuente; null donde va una flecha dibujada. */
        internal fun tramos(texto: String, fuente: FuenteTrueType): List<String?> {
            if (FLECHA.toChar() !in texto || fuente.tiene(FLECHA)) return listOf(texto)
            val partes = texto.split(FLECHA.toChar())
            return partes.flatMapIndexed { i, p -> if (i == 0) listOf(p) else listOf(null, p) }.filter { it == null || it.isNotEmpty() }
        }

        /** Glifo y carácter de cada letra (por punto de código; "?" si la fuente no lo tiene). */
        internal fun glifos(texto: String, fuente: FuenteTrueType): List<Pair<Int, Int>> {
            val lista = ArrayList<Pair<Int, Int>>(texto.length)
            var i = 0
            while (i < texto.length) {
                val c = texto.codePointAt(i)
                i += Character.charCount(c)
                if (Character.isISOControl(c)) continue
                val caracter = if (fuente.tiene(c)) c else '?'.code
                lista += fuente.glifo(caracter) to caracter
            }
            return lista
        }
    }
}
