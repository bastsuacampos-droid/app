package cl.sacyr.rutaitata.excavaciones.reporte

import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.OrigenFoto
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import cl.sacyr.rutaitata.excavaciones.dominio.ejemplo
import cl.sacyr.rutaitata.excavaciones.reporte.MaquetadorReporte.Companion.ALTO_PAGINA
import cl.sacyr.rutaitata.excavaciones.reporte.MaquetadorReporte.Companion.ANCHO_PAGINA
import cl.sacyr.rutaitata.excavaciones.reporte.MaquetadorReporte.Companion.MARGEN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MaquetadorReporteTest {

    /** Medidor aproximado de una fuente sans: media eme por letra, algo más en negrita. */
    private val medidor = MedidorTexto { texto, estilo -> texto.length * estilo.tamano * if (estilo.negrita) 0.56f else 0.5f }
    private val maquetador = MaquetadorReporte(medidor)
    private val hoy = LocalDate.of(2026, 10, 15)

    private fun datos(excavaciones: List<cl.sacyr.rutaitata.excavaciones.dominio.ExcavacionDetalle>, hasta: LocalDate = hoy) =
        DatosReporte(
            desde = hoy,
            hasta = hasta,
            responsable = "Juan Pérez",
            emitidoEn = LocalDateTime.of(2026, 10, 15, 18, 30),
            excavaciones = excavaciones,
        )

    private fun Pagina.textos() = trazos.filterIsInstance<Trazo.Texto>().map { it.texto }

    @Test
    fun unDiaCortoCabeEnUnaPaginaConTodoLoExigido() {
        val paginas = maquetador.maquetar(
            datos(
                listOf(
                    ejemplo(1),
                    ejemplo(2),
                    ejemplo(1, lado = Lado.SUR, sentido = Sentido.CONCEPCION_CHILLAN),
                ),
            ),
        )
        assertEquals(1, paginas.size)
        val t = paginas[0].textos()
        for (esperado in listOf(
            MaquetadorReporte.TITULO, "FECHA DEL REPORTE", "15-10-2026", "Juan Pérez", "Emitido: 15-10-2026 18:30",
            "SECTOR: Sector A", "Lado Norte", "Lado Sur",
            "Sentido de avance: Chillán → Concepción", "Sentido de avance: Concepción → Chillán",
            "N°", "Volumen (m³)", "Tipo de terreno", "Observaciones", "01", "02", "3,00",
            "CANTIDAD TOTAL DE EXCAVACIONES", "3", "VOLUMEN ACUMULADO DEL DÍA", "9,00 m³",
            MaquetadorReporte.FIRMA_JEFE, MaquetadorReporte.FIRMA_ITO, "Página 1 de 1",
        )) {
            assertTrue("Falta \"$esperado\" en $t", esperado in t)
        }
        // Un solo día: sin columna de fecha.
        assertFalse("Fecha" in t)
        assertDentroDeLaPagina(paginas)
    }

    @Test
    fun tablaLargaSigueEnOtraPaginaRepitiendoTitulos() {
        val muchas = (1..80).map { ejemplo(it) } + (1..5).map { ejemplo(it, lado = Lado.SUR) }
        val paginas = maquetador.maquetar(datos(muchas))

        assertTrue(paginas.size >= 3)
        paginas.forEachIndexed { i, p ->
            val t = p.textos()
            assertTrue("Pie de la página ${i + 1}", "Página ${i + 1} de ${paginas.size}" in t)
            if (i > 0) assertTrue("Títulos repetidos en la página ${i + 1}", "N°" in t)
        }
        assertTrue(paginas[1].textos().any { it.endsWith("(continuación)") })

        // Cada excavación aparece una sola vez, en orden, en la columna N°.
        val numeros = paginas.flatMap { p ->
            p.trazos.filterIsInstance<Trazo.Texto>().filter { it.x == MARGEN + 17f && Regex("\\d{2}").matches(it.texto) }.map { it.texto }
        }
        assertEquals((1..80).map { "%02d".format(it) } + (1..5).map { "%02d".format(it) }, numeros)

        // Los cuadros de firma van juntos, en la última página.
        val ultima = paginas.last().textos()
        assertTrue(MaquetadorReporte.FIRMA_JEFE in ultima && MaquetadorReporte.FIRMA_ITO in ultima)
        assertDentroDeLaPagina(paginas)
    }

    @Test
    fun ningunTituloDeSentidoQuedaSinFilasEnSuPagina() {
        // Muchos frentes cortos: los saltos caen en cualquier parte.
        val lista = (1..12).flatMap { s ->
            Lado.entries.flatMap { lado ->
                Sentido.entries.map { sentido -> ejemplo(1, sector = "Sector $s", lado = lado, sentido = sentido) }
            }
        }
        val paginas = maquetador.maquetar(datos(lista))
        assertTrue(paginas.size > 2)
        for (p in paginas) {
            val textos = p.trazos.filterIsInstance<Trazo.Texto>()
            textos.filter { it.texto.startsWith("Sentido de avance") }.forEach { titulo ->
                assertTrue(
                    "\"${titulo.texto}\" sin filas en la página ${p.numero}",
                    textos.any { it.y > titulo.y && it.texto == "01" },
                )
            }
        }
        assertDentroDeLaPagina(paginas)
    }

    @Test
    fun variosDiasAgregaColumnaFechaYPeriodo() {
        val paginas = maquetador.maquetar(
            datos(listOf(ejemplo(1), ejemplo(2, fecha = hoy.plusDays(1))), hasta = hoy.plusDays(1)),
        )
        val t = paginas.single().textos()
        assertTrue("Fecha" in t)
        assertTrue("PERÍODO DEL REPORTE" in t)
        assertTrue("15-10-2026 al 16-10-2026" in t)
        assertTrue("16-10-2026" in t)
        assertTrue("VOLUMEN ACUMULADO DEL PERÍODO" in t)
    }

    @Test
    fun sinExcavacionesIgualTieneTotalesYFirmas() {
        val t = maquetador.maquetar(datos(emptyList())).single().textos()
        assertTrue(MaquetadorReporte.SIN_DATOS in t)
        assertTrue("0" in t && "0,00 m³" in t)
        assertTrue(MaquetadorReporte.FIRMA_ITO in t)
    }

    @Test
    fun observacionEnormeSeRecortaParaCaberEnUnaPagina() {
        val larga = (1..2000).joinToString(" ") { "palabra$it" }
        val paginas = maquetador.maquetar(datos(listOf(ejemplo(1, observaciones = larga, fotos = 2))))
        assertTrue(paginas.flatMap { it.textos() }.any { it.endsWith("…") })
        assertDentroDeLaPagina(paginas)
    }

    @Test
    fun envolverRespetaElAnchoYNoPierdeTexto() {
        val estilo = Estilo(10f)
        val texto = "Excavación con presencia de napa a 1,2 m\nSuperantidisestablecimentarianismo"
        val lineas = maquetador.envolver(texto, 60f, estilo)
        assertTrue(lineas.all { medidor.ancho(it, estilo) <= 60f })
        assertEquals(texto.replace("\n", "").replace(" ", ""), lineas.joinToString("").replace(" ", ""))
    }

    @Test
    fun nombreDeArchivoSinCaracteresProblematicos() {
        assertEquals("Reporte_Excavaciones_2026-10-15.pdf", datos(emptyList()).nombreArchivo())
        assertEquals(
            "Reporte_Excavaciones_2026-10-15_a_2026-10-20_Km-12-400-a-14-200-Concepcion.pdf",
            datos(emptyList(), hasta = hoy.plusDays(5)).copy(sector = "Km 12+400 a 14+200 (Concepción)").nombreArchivo(),
        )
    }

    private fun foto(n: Int, ancho: Int = 1600, alto: Int = 1200) =
        FotoReporte("/fotos/$n.jpg", ancho, alto, LocalDateTime.of(2026, 10, 15, 10, n))

    @Test
    fun anexoFotograficoConLasFotosDeCadaExcavacion() {
        val lista = listOf(ejemplo(1), ejemplo(2), ejemplo(3))
        val fotos = mapOf(
            lista[0].id to listOf(foto(1), foto(2, ancho = 900, alto = 1600)),
            lista[2].id to listOf(foto(3)),
        )
        val paginas = maquetador.maquetar(datos(lista).copy(incluirFotos = true, fotos = fotos))
        val todos = paginas.flatMap { it.textos() }

        // La tabla dice dónde está el respaldo y marca la que no tiene.
        assertTrue("[2 fotos en anexo]" in todos)
        assertTrue("[1 foto en anexo]" in todos)
        assertTrue(MaquetadorReporte.SIN_FOTO in todos)

        // El anexo empieza en página aparte, después de las firmas.
        val inicioAnexo = paginas.indexOfFirst { MaquetadorReporte.TITULO_ANEXO in it.textos() }
        assertTrue(inicioAnexo > paginas.indexOfFirst { MaquetadorReporte.FIRMA_ITO in it.textos() })
        val anexo = paginas.drop(inicioAnexo)
        val imagenes = anexo.flatMap { it.trazos.filterIsInstance<Trazo.Imagen>() }
        assertEquals(listOf("/fotos/1.jpg", "/fotos/2.jpg", "/fotos/3.jpg"), imagenes.map { it.ruta })
        // Sin deformar: la proporción de la foto se mantiene (1600×1200 y una vertical 900×1600).
        assertEquals(1600f / 1200f, imagenes[0].ancho / imagenes[0].alto, 0.01f)
        assertEquals(900f / 1600f, imagenes[1].ancho / imagenes[1].alto, 0.01f)
        val textosAnexo = anexo.flatMap { it.textos() }
        assertTrue(textosAnexo.any { it.startsWith("N° 01 · Sector A · Lado Norte") })
        assertFalse(textosAnexo.any { it.startsWith("N° 02 ·") })
        assertTrue("Foto 1 · tomada el 15-10-2026 10:01" in textosAnexo)
        assertDentroDeLaPagina(paginas)
    }

    @Test
    fun estadoCerradoOBorradorYConstanciaDeReaperturas() {
        val lista = listOf(ejemplo(1), ejemplo(2, fecha = hoy.plusDays(1)))
        val borrador = maquetador.maquetar(datos(lista, hasta = hoy.plusDays(1))).first().textos()
        assertTrue("BORRADOR" in borrador)
        assertTrue("Días abiertos: 15-10, 16-10" in borrador)

        val cierre = { d: LocalDate -> CierreReporte(d, LocalDateTime.of(2026, 10, 16, 18, 0), "María González") }
        val cerrado = maquetador.maquetar(
            datos(lista, hasta = hoy.plusDays(1)).copy(
                cierres = listOf(cierre(hoy), cierre(hoy.plusDays(1))),
                reaperturas = listOf(
                    ReaperturaReporte(hoy, LocalDateTime.of(2026, 10, 15, 18, 0), LocalDateTime.of(2026, 10, 16, 9, 12), "Corregir N° 01"),
                ),
            ),
        )
        val t = cerrado.first().textos()
        assertTrue("CERRADO" in t)
        assertTrue("16-10-2026 18:00 · María González" in t)
        assertTrue(t.any { it.startsWith("Reapertura: el 15-10-2026") && it.contains("Motivo: Corregir N° 01") })
        assertDentroDeLaPagina(cerrado)
    }

    @Test
    fun laLeyendaDeCadaFotoDiceSiEsDeCamaraODeGaleria() {
        val e = ejemplo(1)
        val fotos = mapOf(e.id to listOf(foto(1).copy(origen = OrigenFoto.CAMARA), foto(2).copy(origen = OrigenFoto.GALERIA)))
        val t = maquetador.maquetar(datos(listOf(e)).copy(incluirFotos = true, fotos = fotos)).flatMap { it.textos() }
        assertTrue("Foto 1 · cámara · tomada el 15-10-2026 10:01" in t)
        assertTrue("Foto 2 · galería · tomada el 15-10-2026 10:02" in t)
    }

    @Test
    fun anexoPoneDosExcavacionesPorPagina() {
        val lista = (1..5).map { ejemplo(it) }
        val fotos = lista.associate { it.id to (1..3).map { n -> foto(n) } }
        val paginas = maquetador.maquetar(datos(lista).copy(incluirFotos = true, fotos = fotos))
        val anexo = paginas.filter { p -> p.textos().any { it.startsWith(MaquetadorReporte.TITULO_ANEXO) } }
        assertEquals(3, anexo.size)
        assertEquals(listOf(6, 6, 3), anexo.map { p -> p.trazos.count { it is Trazo.Imagen } })
        assertTrue(anexo[1].textos().contains(MaquetadorReporte.TITULO_ANEXO + " (continuación)"))
        assertDentroDeLaPagina(paginas)
    }

    @Test
    fun sinFotosIncluidasNoHayAnexo() {
        val lista = listOf(ejemplo(1, fotos = 2))
        val paginas = maquetador.maquetar(datos(lista).copy(fotos = mapOf(lista[0].id to listOf(foto(1)))))
        assertTrue(paginas.none { p -> p.trazos.any { it is Trazo.Imagen } })
        assertTrue("[2 fotos]" in paginas.single().textos())
    }

    /** Nada se sale de los márgenes, y salvo el pie, nada invade la franja del pie. */
    private fun assertDentroDeLaPagina(paginas: List<Pagina>) {
        val limiteCuerpo = ALTO_PAGINA - MARGEN - 22f
        for (p in paginas) {
            for (t in p.trazos) {
                when (t) {
                    is Trazo.Texto -> {
                        val ancho = medidor.ancho(t.texto, t.estilo)
                        val izq = when (t.alineacion) {
                            Alineacion.IZQUIERDA -> t.x
                            Alineacion.CENTRO -> t.x - ancho / 2
                            Alineacion.DERECHA -> t.x - ancho
                        }
                        assertTrue("\"${t.texto}\" se sale por los lados en la página ${p.numero}", izq >= MARGEN - 0.01f && izq + ancho <= ANCHO_PAGINA - MARGEN + 0.01f)
                        assertTrue("\"${t.texto}\" se sale por arriba o abajo en la página ${p.numero}", t.y >= MARGEN && t.y <= ALTO_PAGINA - MARGEN)
                    }
                    is Trazo.Rectangulo ->
                        assertTrue("Rectángulo fuera del cuerpo en la página ${p.numero}: $t", t.y >= MARGEN && t.y + t.alto <= limiteCuerpo + 0.01f)
                    is Trazo.Linea -> assertTrue(t.y1 <= ALTO_PAGINA - MARGEN && t.x2 <= ANCHO_PAGINA - MARGEN)
                    is Trazo.Imagen ->
                        assertTrue(
                            "Foto fuera del cuerpo en la página ${p.numero}: $t",
                            t.x >= MARGEN && t.x + t.ancho <= ANCHO_PAGINA - MARGEN + 0.01f && t.y >= MARGEN && t.y + t.alto <= limiteCuerpo + 0.01f,
                        )
                }
            }
        }
    }
}
