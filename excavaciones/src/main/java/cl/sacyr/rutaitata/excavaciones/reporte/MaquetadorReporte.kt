package cl.sacyr.rutaitata.excavaciones.reporte

import cl.sacyr.rutaitata.excavaciones.dominio.Agrupador
import cl.sacyr.rutaitata.excavaciones.dominio.ExcavacionDetalle
import cl.sacyr.rutaitata.excavaciones.dominio.Formato
import cl.sacyr.rutaitata.excavaciones.dominio.GrupoLado
import cl.sacyr.rutaitata.excavaciones.dominio.GrupoSector
import cl.sacyr.rutaitata.excavaciones.dominio.GrupoSentido
import cl.sacyr.rutaitata.excavaciones.dominio.Volumen

/**
 * Arma el "Reporte Diario de Control de Excavaciones" en páginas A4 horizontales:
 *
 * - Encabezado formal (título, obra, fecha del reporte, sector, responsable, emisión). Las páginas
 *   siguientes llevan un encabezado corto.
 * - Por cada Sector una banda, por cada Lado un subtítulo y por cada Sentido una tabla con sus
 *   excavaciones en orden correlativo y un subtotal.
 * - Si una tabla no cabe, sigue en la página siguiente repitiendo "Sector · Lado · Sentido
 *   (continuación)" y la fila de títulos. Un título nunca queda solo al pie de una página.
 * - Al final, totales del reporte y cuadros de firma (Jefe de Terreno e ITO), siempre juntos.
 * - Pie con "Página X de N" en todas las páginas.
 */
class MaquetadorReporte(private val medidor: MedidorTexto) {

    fun maquetar(datos: DatosReporte): List<Pagina> = Armado(datos).paginas()

    companion object {
        /** A4 horizontal, en puntos. */
        const val ANCHO_PAGINA = 842f
        const val ALTO_PAGINA = 595f
        const val MARGEN = 36f
        const val TITULO = "Reporte Diario de Control de Excavaciones"
        const val SIN_DATOS = "No hay excavaciones registradas para el período y sector seleccionados."
        const val FIRMA_JEFE = "Jefe de Terreno"
        const val FIRMA_ITO = "Inspector Técnico de Obra (ITO)"

        private const val IZQ = MARGEN
        private const val DER = ANCHO_PAGINA - MARGEN
        private const val ANCHO_UTIL = DER - IZQ
        private const val ALTO_PIE = 22f
        private const val LIMITE = ALTO_PAGINA - MARGEN - ALTO_PIE

        private const val PAD = 4f
        private const val ALTO_BANDA_SECTOR = 22f
        private const val ALTO_LADO = 22f
        private const val ALTO_SENTIDO = 17f
        private const val ALTO_FIRMAS = 90f

        private val TITULO_GRANDE = Estilo(16f, negrita = true)
        private val TITULO_CORTO = Estilo(10f, negrita = true)
        private val NORMAL = Estilo(9f)
        private val ETIQUETA = Estilo(7f, color = Colores.SECUNDARIO)
        private val DATO = Estilo(9.5f, negrita = true)
        private val SECTOR = Estilo(10.5f, negrita = true, color = Colores.BLANCO)
        private val SECTOR_TOTAL = Estilo(9f, color = Colores.BLANCO)
        private val LADO = Estilo(10.5f, negrita = true)
        private val LADO_TOTAL = Estilo(8.5f, color = Colores.SECUNDARIO)
        private val SENTIDO = Estilo(9f, negrita = true, color = Colores.SECUNDARIO)
        private val TABLA = Estilo(8.5f)
        private val TABLA_TITULO = Estilo(8f, negrita = true)
        private val TABLA_TOTAL = Estilo(8.5f, negrita = true)
        private val PIE = Estilo(7.5f, color = Colores.SECUNDARIO)

        /** Alto de una línea de texto y posición de su línea base desde el borde superior. */
        private fun interlinea(e: Estilo) = e.tamano * 1.25f
        private fun base(e: Estilo) = e.tamano * 0.95f
    }

    private class Columna(
        val titulo: String,
        val ancho: Float,
        val alineacion: Alineacion,
        val valor: (ExcavacionDetalle) -> String,
    )

    /** Una fila ya dividida en líneas por columna. */
    private class Fila(val lineas: List<List<String>>, val alto: Float)

    private inner class Armado(private val datos: DatosReporte) {

        private val terminadas = mutableListOf<MutableList<Trazo>>()
        private var trazos = mutableListOf<Trazo>()
        private var y = 0f

        private val columnas = columnas()
        private val encabezadoTabla = filaDeTitulos()
        private val altoSubtotal = interlinea(TABLA_TOTAL) + 2 * PAD

        /** La fila más alta que cabe en una página nueva bajo todos los títulos y con el subtotal. */
        private val altoMaximoFila = LIMITE - (MARGEN + altoEncabezadoCorto()) -
            ALTO_BANDA_SECTOR - ALTO_LADO - ALTO_SENTIDO - encabezadoTabla.alto - altoSubtotal

        fun paginas(): List<Pagina> {
            encabezadoPrincipal()
            val sectores = Agrupador.agrupar(datos.excavaciones)
            if (sectores.isEmpty()) {
                y += 8f
                texto(IZQ, y + base(NORMAL), SIN_DATOS, NORMAL)
                y += interlinea(NORMAL) + 8f
            }
            sectores.forEach(::sector)
            totalesYFirmas()
            terminadas += trazos
            val total = terminadas.size
            return terminadas.mapIndexed { i, t -> Pagina(i + 1, t + pie(i + 1, total)) }
        }

        // ---------------------------------------------------------------- páginas y encabezados

        private fun nuevaPagina() {
            terminadas += trazos
            trazos = mutableListOf()
            encabezadoCorto()
        }

        private fun cabe(alto: Float) = y + alto <= LIMITE

        private fun asegurar(alto: Float) {
            if (!cabe(alto)) nuevaPagina()
        }

        private fun periodo(): String =
            if (datos.variosDias) "${Formato.fecha(datos.desde)} al ${Formato.fecha(datos.hasta)}" else Formato.fecha(datos.desde)

        private fun encabezadoPrincipal() {
            y = MARGEN
            texto(IZQ, y + base(TITULO_GRANDE), TITULO, TITULO_GRANDE)
            texto(DER, y + base(PIE), "Emitido: ${Formato.fechaHora(datos.emitidoEn)}", PIE, Alineacion.DERECHA)
            y += interlinea(TITULO_GRANDE) + 2f
            texto(IZQ, y + base(NORMAL), "Obra: ${datos.obra}", NORMAL)
            y += interlinea(NORMAL) + 6f

            // Recuadro de datos del reporte: tres celdas con etiqueta y valor.
            val celdas = listOf(
                (if (datos.variosDias) "PERÍODO DEL REPORTE" else "FECHA DEL REPORTE") to periodo(),
                "SECTOR / TRAMO" to (datos.sector ?: "Todos los sectores"),
                "RESPONSABLE / INSPECTOR DE OBRA" to datos.responsable.trim().ifEmpty { "Sin indicar" },
            )
            val anchos = listOf(0.25f, 0.33f, 0.42f).map { it * ANCHO_UTIL }
            val alto = PAD + interlinea(ETIQUETA) + interlinea(DATO) + PAD
            var x = IZQ
            celdas.forEachIndexed { i, (etiqueta, valor) ->
                trazos += Trazo.Rectangulo(x, y, anchos[i], alto, borde = Colores.BORDE)
                texto(x + PAD + 2f, y + PAD + base(ETIQUETA), etiqueta, ETIQUETA)
                texto(
                    x + PAD + 2f,
                    y + PAD + interlinea(ETIQUETA) + base(DATO),
                    recortar(valor, anchos[i] - 2 * PAD - 4f, DATO),
                    DATO,
                )
                x += anchos[i]
            }
            y += alto + 12f
        }

        private fun altoEncabezadoCorto() = interlinea(TITULO_CORTO) + 10f

        private fun encabezadoCorto() {
            y = MARGEN
            texto(IZQ, y + base(TITULO_CORTO), TITULO, TITULO_CORTO)
            texto(DER, y + base(TITULO_CORTO), "${datos.obra} · ${periodo()}", PIE, Alineacion.DERECHA)
            y += interlinea(TITULO_CORTO) + 3f
            trazos += Trazo.Linea(IZQ, y, DER, y, grosor = 0.8f, color = Colores.TEXTO)
            y += 7f
        }

        private fun pie(numero: Int, total: Int): List<Trazo> {
            val linea = ALTO_PAGINA - MARGEN - ALTO_PIE + 8f
            val base = linea + 4f + base(PIE)
            val izquierda = buildString {
                append("Control de excavaciones · ").append(datos.obra)
                datos.sector?.let { append(" · ").append(it) }
            }
            return listOf(
                Trazo.Linea(IZQ, linea, DER, linea),
                Trazo.Texto(IZQ, base, recortar(izquierda, ANCHO_UTIL * 0.7f, PIE), PIE),
                Trazo.Texto(DER, base, "Página $numero de $total", PIE, Alineacion.DERECHA),
            )
        }

        // ---------------------------------------------------------------- jerarquía

        private fun sector(sector: GrupoSector) {
            sector.lados.forEachIndexed { iLado, lado ->
                lado.sentidos.forEachIndexed { iSentido, sentido ->
                    sentido(sector, lado, sentido, primeroDelSector = iLado == 0 && iSentido == 0, primeroDelLado = iSentido == 0)
                }
            }
            y += 6f
        }

        private fun sentido(
            sector: GrupoSector,
            lado: GrupoLado,
            sentido: GrupoSentido,
            primeroDelSector: Boolean,
            primeroDelLado: Boolean,
        ) {
            val filas = sentido.excavaciones.map(::fila)
            val altoTitulos = (if (primeroDelSector) ALTO_BANDA_SECTOR else 0f) +
                (if (primeroDelLado) ALTO_LADO else 0f) + ALTO_SENTIDO + encabezadoTabla.alto
            // Los títulos van con al menos su primera fila (y el subtotal, si es la única).
            val primera = filas.first().alto + if (filas.size == 1) altoSubtotal else 0f
            // En una página nueva se repiten sector y lado, para que la tabla no quede sin contexto.
            val repetir = !cabe(altoTitulos + primera)
            if (repetir) nuevaPagina()
            if (primeroDelSector || repetir) bandaSector(sector, continuacion = !primeroDelSector)
            if (primeroDelLado || repetir) tituloLado(lado, continuacion = !primeroDelLado)
            tituloSentido(sentido, continuacion = null)
            dibujarFila(encabezadoTabla, titulos = true, alterna = false)

            filas.forEachIndexed { i, fila ->
                val alto = fila.alto + if (i == filas.lastIndex) altoSubtotal else 0f
                if (!cabe(alto)) {
                    nuevaPagina()
                    tituloSentido(sentido, continuacion = "${sector.nombre} · ${lado.lado.etiqueta}")
                    dibujarFila(encabezadoTabla, titulos = true, alterna = false)
                }
                dibujarFila(fila, titulos = false, alterna = i % 2 == 1)
            }
            subtotal(sentido)
            y += 8f
        }

        private fun bandaSector(sector: GrupoSector, continuacion: Boolean) {
            val alto = ALTO_BANDA_SECTOR - 4f
            trazos += Trazo.Rectangulo(IZQ, y, ANCHO_UTIL, alto, relleno = Colores.BANDA_SECTOR)
            val resumen = "${cantidad(sector.cantidad)} · ${Formato.volumen(sector.volumen)}"
            val anchoResumen = medidor.ancho(resumen, SECTOR_TOTAL)
            val bas = y + (alto - SECTOR.tamano) / 2 + base(SECTOR)
            texto(
                IZQ + 8f,
                bas,
                recortar(
                    "SECTOR: ${sector.nombre}" + if (continuacion) " (continuación)" else "",
                    ANCHO_UTIL - anchoResumen - 32f,
                    SECTOR,
                ),
                SECTOR,
            )
            texto(DER - 8f, bas, resumen, SECTOR_TOTAL, Alineacion.DERECHA)
            y += ALTO_BANDA_SECTOR
        }

        private fun tituloLado(lado: GrupoLado, continuacion: Boolean) {
            val bas = y + 4f + base(LADO)
            texto(IZQ + 2f, bas, lado.lado.etiqueta + if (continuacion) " (continuación)" else "", LADO)
            texto(DER, bas, "${cantidad(lado.cantidad)} · ${Formato.volumen(lado.volumen)}", LADO_TOTAL, Alineacion.DERECHA)
            val linea = y + 4f + interlinea(LADO) + 1f
            trazos += Trazo.Linea(IZQ, linea, DER, linea, grosor = 0.8f, color = Colores.TEXTO)
            y += ALTO_LADO
        }

        /** [continuacion]: "Sector · Lado" cuando la tabla viene de la página anterior. */
        private fun tituloSentido(sentido: GrupoSentido, continuacion: String?) {
            val texto = if (continuacion == null) {
                "Sentido de avance: ${sentido.sentido.etiqueta}"
            } else {
                "$continuacion · ${sentido.sentido.etiqueta} (continuación)"
            }
            texto(IZQ + 2f, y + 3f + base(SENTIDO), recortar(texto, ANCHO_UTIL - 4f, SENTIDO), SENTIDO)
            y += ALTO_SENTIDO
        }

        private fun subtotal(sentido: GrupoSentido) {
            trazos += Trazo.Rectangulo(IZQ, y, ANCHO_UTIL, altoSubtotal, relleno = Colores.SUBTOTAL, borde = Colores.BORDE)
            val bas = y + PAD + base(TABLA_TOTAL)
            texto(IZQ + PAD, bas, "Subtotal ${sentido.sentido.etiquetaCorta}: ${cantidad(sentido.cantidad)}", TABLA_TOTAL)
            // El volumen queda bajo su columna.
            val iVolumen = columnas.indexOfFirst { it.titulo == COL_VOLUMEN }
            val derechaVolumen = IZQ + columnas.take(iVolumen + 1).sumOf { it.ancho.toDouble() }.toFloat()
            texto(derechaVolumen - PAD, bas, Formato.decimal(sentido.volumen), TABLA_TOTAL, Alineacion.DERECHA)
            y += altoSubtotal
        }

        // ---------------------------------------------------------------- tabla

        private fun columnas(): List<Columna> {
            val fijas = buildList {
                add(Columna("N°", 34f, Alineacion.CENTRO) { Formato.numero(it.numero) })
                if (datos.variosDias) add(Columna("Fecha", 58f, Alineacion.CENTRO) { Formato.fecha(it.fecha) })
                add(Columna("Progresiva / Ubicación GPS", 140f, Alineacion.IZQUIERDA, ::ubicacion))
                add(Columna("Dimensiones L × A × P (m)", 124f, Alineacion.CENTRO) {
                    Formato.dimensiones(it.largo, it.ancho, it.profundidad)
                })
                add(Columna(COL_VOLUMEN, 66f, Alineacion.DERECHA) { Formato.decimal(it.volumen) })
                add(Columna("Tipo de terreno", 96f, Alineacion.IZQUIERDA) { it.tipoTerreno ?: "—" })
            }
            val resto = ANCHO_UTIL - fijas.sumOf { it.ancho.toDouble() }.toFloat()
            return fijas + Columna("Observaciones", resto, Alineacion.IZQUIERDA) { observaciones(it) }
        }

        private fun ubicacion(e: ExcavacionDetalle): String {
            val gps = if (e.latitud != null && e.longitud != null) Formato.coordenadas(e.latitud, e.longitud) else null
            return listOfNotNull(e.progresiva, gps).joinToString("\n").ifEmpty { "—" }
        }

        private fun observaciones(e: ExcavacionDetalle): String {
            val fotos = when (e.fotos) {
                0 -> null
                1 -> "[1 foto]"
                else -> "[${e.fotos} fotos]"
            }
            return listOfNotNull(e.observaciones, fotos).joinToString(" ")
        }

        private fun filaDeTitulos(): Fila =
            armarFila(columnas.map { it.titulo }, TABLA_TITULO, maximo = Float.MAX_VALUE)

        private fun fila(e: ExcavacionDetalle): Fila =
            armarFila(columnas.map { it.valor(e) }, TABLA, maximo = altoMaximoFila)

        private fun armarFila(valores: List<String>, estilo: Estilo, maximo: Float): Fila {
            val maxLineas = ((maximo - 2 * PAD) / interlinea(estilo)).toInt().coerceAtLeast(1)
            val lineas = valores.mapIndexed { i, valor ->
                val todas = envolver(valor, columnas[i].ancho - 2 * PAD, estilo)
                if (todas.size <= maxLineas) {
                    todas
                } else {
                    todas.take(maxLineas - 1) + recortar(todas[maxLineas - 1] + " …", columnas[i].ancho - 2 * PAD, estilo)
                }
            }
            val alto = lineas.maxOf { it.size } * interlinea(estilo) + 2 * PAD
            return Fila(lineas, alto)
        }

        private fun dibujarFila(fila: Fila, titulos: Boolean, alterna: Boolean) {
            val estilo = if (titulos) TABLA_TITULO else TABLA
            val relleno = when {
                titulos -> Colores.ENCABEZADO_TABLA
                alterna -> Colores.FILA_ALTERNA
                else -> null
            }
            var x = IZQ
            columnas.forEachIndexed { i, col ->
                trazos += Trazo.Rectangulo(x, y, col.ancho, fila.alto, relleno = relleno, borde = Colores.BORDE)
                // Los títulos van centrados; los datos según su columna.
                val alineacion = if (titulos) Alineacion.CENTRO else col.alineacion
                val xTexto = when (alineacion) {
                    Alineacion.IZQUIERDA -> x + PAD
                    Alineacion.CENTRO -> x + col.ancho / 2
                    Alineacion.DERECHA -> x + col.ancho - PAD
                }
                // Títulos centrados también en vertical.
                val desplazamiento = if (titulos) (fila.alto - 2 * PAD - fila.lineas[i].size * interlinea(estilo)) / 2 else 0f
                fila.lineas[i].forEachIndexed { n, linea ->
                    texto(xTexto, y + PAD + desplazamiento + n * interlinea(estilo) + base(estilo), linea, estilo, alineacion)
                }
                x += col.ancho
            }
            y += fila.alto
        }

        // ---------------------------------------------------------------- cierre

        private fun totalesYFirmas() {
            val sectores = Agrupador.agrupar(datos.excavaciones)
            val desglose = if (sectores.size > 1) sectores else emptyList()
            val altoRecuadro = PAD + interlinea(ETIQUETA) + interlinea(DATO) + PAD
            val altoDesglose = if (desglose.isEmpty()) 0f else 4f + desglose.size * interlinea(NORMAL)
            asegurar(4f + altoRecuadro + altoDesglose + 14f + ALTO_FIRMAS)
            y += 4f

            // Totales: recuadro de una fila, como el del encabezado.
            val celdas = listOf(
                "TOTALES DEL REPORTE" to "",
                "CANTIDAD TOTAL DE EXCAVACIONES" to datos.excavaciones.size.toString(),
                (if (datos.variosDias) "VOLUMEN ACUMULADO DEL PERÍODO" else "VOLUMEN ACUMULADO DEL DÍA") to
                    Formato.volumen(Volumen.total(datos.excavaciones)),
            )
            val anchos = listOf(0.25f, 0.33f, 0.42f).map { it * ANCHO_UTIL }
            var x = IZQ
            celdas.forEachIndexed { i, (etiqueta, valor) ->
                trazos += Trazo.Rectangulo(
                    x, y, anchos[i], altoRecuadro,
                    relleno = if (i == 0) Colores.ENCABEZADO_TABLA else null,
                    borde = Colores.TEXTO, grosor = 0.8f,
                )
                if (i == 0) {
                    texto(x + PAD + 2f, y + (altoRecuadro - TITULO_CORTO.tamano) / 2 + base(TITULO_CORTO), etiqueta, TITULO_CORTO)
                } else {
                    texto(x + PAD + 2f, y + PAD + base(ETIQUETA), etiqueta, ETIQUETA)
                    texto(x + PAD + 2f, y + PAD + interlinea(ETIQUETA) + base(DATO), valor, DATO)
                }
                x += anchos[i]
            }
            y += altoRecuadro
            if (desglose.isNotEmpty()) {
                y += 4f
                desglose.forEach { s ->
                    texto(
                        IZQ + 4f,
                        y + base(NORMAL),
                        recortar("${s.nombre}: ${cantidad(s.cantidad)} · ${Formato.volumen(s.volumen)}", ANCHO_UTIL - 8f, NORMAL),
                        NORMAL,
                    )
                    y += interlinea(NORMAL)
                }
            }
            y += 14f

            // Firmas
            val separacion = 40f
            val ancho = (ANCHO_UTIL - separacion) / 2
            cuadroFirma(IZQ, ancho, FIRMA_JEFE)
            cuadroFirma(IZQ + ancho + separacion, ancho, FIRMA_ITO)
            y += ALTO_FIRMAS
        }

        private fun cuadroFirma(x: Float, ancho: Float, cargo: String) {
            trazos += Trazo.Rectangulo(x, y, ancho, ALTO_FIRMAS, borde = Colores.TEXTO, grosor = 0.8f)
            trazos += Trazo.Rectangulo(x, y, ancho, 18f, relleno = Colores.ENCABEZADO_TABLA, borde = Colores.TEXTO, grosor = 0.8f)
            texto(x + ancho / 2, y + 4f + base(TITULO_CORTO), cargo, TITULO_CORTO, Alineacion.CENTRO)
            listOf("Nombre:" to 36f, "Firma:" to 62f, "Fecha:" to 82f).forEach { (etiqueta, alto) ->
                val bas = y + alto
                texto(x + 10f, bas, etiqueta, NORMAL)
                trazos += Trazo.Linea(x + 54f, bas + 1f, x + ancho - 12f, bas + 1f, color = Colores.SECUNDARIO)
            }
        }

        // ---------------------------------------------------------------- texto

        private fun cantidad(n: Int) = if (n == 1) "1 excavación" else "$n excavaciones"

        private fun texto(x: Float, y: Float, texto: String, estilo: Estilo, alineacion: Alineacion = Alineacion.IZQUIERDA) {
            if (texto.isNotEmpty()) trazos += Trazo.Texto(x, y, texto, estilo, alineacion)
        }
    }

    /** Corta [texto] con "…" para que no pase de [ancho]. */
    internal fun recortar(texto: String, ancho: Float, estilo: Estilo): String {
        if (medidor.ancho(texto, estilo) <= ancho) return texto
        var fin = texto.length
        while (fin > 0 && medidor.ancho(texto.substring(0, fin).trimEnd() + "…", estilo) > ancho) fin--
        return texto.substring(0, fin).trimEnd() + "…"
    }

    /**
     * Divide [texto] en líneas que caben en [ancho]: respeta los saltos de línea, corta entre
     * palabras y, si una palabra sola no cabe (un código largo), la corta por letras.
     */
    internal fun envolver(texto: String, ancho: Float, estilo: Estilo): List<String> {
        val lineas = mutableListOf<String>()
        for (parrafo in texto.split('\n')) {
            var actual = ""
            for (palabra in parrafo.split(' ').filter { it.isNotEmpty() }) {
                val candidata = if (actual.isEmpty()) palabra else "$actual $palabra"
                when {
                    medidor.ancho(candidata, estilo) <= ancho -> actual = candidata
                    medidor.ancho(palabra, estilo) <= ancho -> {
                        lineas += actual
                        actual = palabra
                    }
                    else -> {
                        if (actual.isNotEmpty()) lineas += actual
                        var resto = palabra
                        while (medidor.ancho(resto, estilo) > ancho && resto.length > 1) {
                            var corte = resto.length - 1
                            while (corte > 1 && medidor.ancho(resto.substring(0, corte), estilo) > ancho) corte--
                            lineas += resto.substring(0, corte)
                            resto = resto.substring(corte)
                        }
                        actual = resto
                    }
                }
            }
            lineas += actual
        }
        return lineas
    }
}

private const val COL_VOLUMEN = "Volumen (m³)"
