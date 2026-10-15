package cl.sacyr.rutaitata.excavaciones.reporte

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Lo que el PDF necesita de una fuente TrueType (.ttf), en Kotlin puro: avance de cada glifo para
 * medir el texto, la tabla de caracteres y un subconjunto con solo los glifos usados para
 * incrustarlo (Roboto completa pesa ~300 KB; el subconjunto de un reporte, unos 30 KB).
 */
class FuenteTrueType(private val datos: ByteArray, val nombre: String) {

    private val tablas: Map<String, IntRange>
    val unidadesPorEm: Int
    private val formatoLoca: Int
    val numGlifos: Int
    private val avances: IntArray
    private val glifoDe: Map<Int, Int>

    // Métricas para el descriptor de la fuente, en unidades de la fuente.
    val ascendente: Int
    val descendente: Int
    val alturaMayusculas: Int
    val caja: IntArray

    init {
        val b = ByteBuffer.wrap(datos)
        val cantidad = b.getShort(4).toInt() and 0xFFFF
        tablas = (0 until cantidad).associate { i ->
            val r = 12 + 16 * i
            val etiqueta = String(datos, r, 4, Charsets.US_ASCII)
            val inicio = b.getInt(r + 8)
            etiqueta to (inicio until inicio + b.getInt(r + 12))
        }
        for (t in TABLAS_INCRUSTADAS.filter { it in OBLIGATORIAS } + "cmap") require(t in tablas) { "$nombre: falta la tabla $t" }
        val head = tabla("head").first
        unidadesPorEm = u16(head + 18)
        caja = intArrayOf(s16(head + 36), s16(head + 38), s16(head + 40), s16(head + 42))
        formatoLoca = s16(head + 50)
        numGlifos = u16(tabla("maxp").first + 4)
        val hhea = tabla("hhea").first
        val metricas = u16(hhea + 34)
        val hmtx = tabla("hmtx").first
        avances = IntArray(numGlifos) { g -> u16(hmtx + 4 * minOf(g, metricas - 1)) }
        val os2 = tablas["OS/2"]?.first
        ascendente = os2?.let { s16(it + 68) } ?: s16(hhea + 4)
        descendente = os2?.let { s16(it + 70) } ?: s16(hhea + 6)
        alturaMayusculas = os2?.takeIf { u16(it) >= 2 }?.let { s16(it + 88) } ?: ascendente
        glifoDe = leerCmap()
    }

    /** Glifo del carácter, o 0 (.notdef) si la fuente no lo tiene. */
    fun glifo(caracter: Int): Int = glifoDe[caracter] ?: 0

    fun tiene(caracter: Int): Boolean = caracter in glifoDe

    /** Avance del glifo en milésimas de eme, la unidad de los anchos en el PDF. */
    fun ancho(glifo: Int): Float = avances[glifo] * 1000f / unidadesPorEm

    fun milesimas(unidades: Int): Int = Math.round(unidades * 1000f / unidadesPorEm)

    /**
     * Fuente con solo [glifos] (más .notdef y las piezas de los glifos compuestos, como la "a" y la
     * tilde de "á"). Los números de glifo no cambian, así el PDF los usa tal cual. Lleva las tablas
     * que pide la norma PDF para una TrueType incrustada.
     */
    fun subconjunto(glifos: Set<Int>): ByteArray {
        val usados = HashSet<Int>()
        val pendientes = ArrayDeque(glifos.filter { it in 0 until numGlifos } + 0)
        while (pendientes.isNotEmpty()) {
            val g = pendientes.removeFirst()
            if (usados.add(g)) pendientes.addAll(componentes(g))
        }
        val glyf = ByteArrayOutputStream()
        val loca = ByteBuffer.allocate(4 * (numGlifos + 1))
        for (g in 0 until numGlifos) {
            loca.putInt(glyf.size())
            if (g in usados) {
                val r = rangoGlifo(g)
                glyf.write(datos, r.first, r.last - r.first + 1)
                repeat((4 - glyf.size() % 4) % 4) { glyf.write(0) }
            }
        }
        loca.putInt(glyf.size())

        val nuevas = TABLAS_INCRUSTADAS.mapNotNull { etiqueta ->
            val contenido = when (etiqueta) {
                "glyf" -> glyf.toByteArray()
                "loca" -> loca.array()
                "head" -> copia("head").also { h ->
                    ByteBuffer.wrap(h).putInt(8, 0).putShort(50, 1) // sin ajuste de suma todavía; loca larga
                }
                else -> tablas[etiqueta]?.let { copia(etiqueta) }
            }
            contenido?.let { etiqueta to it }
        }
        return armar(nuevas)
    }

    private fun tabla(etiqueta: String) = tablas.getValue(etiqueta)

    private fun copia(etiqueta: String): ByteArray = tabla(etiqueta).let { datos.copyOfRange(it.first, it.last + 1) }

    private fun u16(i: Int) = ((datos[i].toInt() and 0xFF) shl 8) or (datos[i + 1].toInt() and 0xFF)

    private fun s16(i: Int) = u16(i).toShort().toInt()

    private fun u32(i: Int) = (u16(i).toLong() shl 16 or u16(i + 2).toLong()).toInt()

    private fun rangoGlifo(g: Int): IntRange {
        val loca = tabla("loca").first
        val (desde, hasta) = if (formatoLoca == 0) {
            2 * u16(loca + 2 * g) to 2 * u16(loca + 2 * g + 2)
        } else {
            u32(loca + 4 * g) to u32(loca + 4 * g + 4)
        }
        val inicio = tabla("glyf").first
        return inicio + desde until inicio + hasta
    }

    /** Glifos que usa un glifo compuesto. */
    private fun componentes(g: Int): List<Int> {
        val r = rangoGlifo(g)
        if (r.isEmpty() || s16(r.first) >= 0) return emptyList()
        val lista = mutableListOf<Int>()
        var i = r.first + 10
        while (true) {
            val banderas = u16(i)
            lista += u16(i + 2)
            i += 4 + (if (banderas and 0x0001 != 0) 4 else 2)
            i += when {
                banderas and 0x0008 != 0 -> 2
                banderas and 0x0040 != 0 -> 4
                banderas and 0x0080 != 0 -> 8
                else -> 0
            }
            if (banderas and 0x0020 == 0) break
        }
        return lista
    }

    /** Unicode → glifo, desde la subtabla de Windows (formato 12 si existe, si no la 4). */
    private fun leerCmap(): Map<Int, Int> {
        val cmap = tabla("cmap").first
        val subtablas = (0 until u16(cmap + 2)).map { i ->
            val r = cmap + 4 + 8 * i
            Triple(u16(r), u16(r + 2), cmap + u32(r + 4))
        }
        val elegida = subtablas.firstOrNull { it.first == 3 && it.second == 10 && u16(it.third) == 12 }
            ?: subtablas.firstOrNull { it.first == 3 && it.second == 1 && u16(it.third) == 4 }
            ?: subtablas.firstOrNull { it.first == 0 && u16(it.third) in setOf(4, 12) }
            ?: error("$nombre: sin tabla de caracteres Unicode")
        val t = elegida.third
        val mapa = HashMap<Int, Int>()
        if (u16(t) == 12) {
            for (k in 0 until u32(t + 12)) {
                val r = t + 16 + 12 * k
                val glifo = u32(r + 8)
                for (c in u32(r)..u32(r + 4)) mapa[c] = glifo + (c - u32(r))
            }
        } else {
            val segmentos = u16(t + 6) / 2
            val finales = t + 14
            val iniciales = finales + 2 * segmentos + 2
            val deltas = iniciales + 2 * segmentos
            val desplazamientos = deltas + 2 * segmentos
            for (s in 0 until segmentos) {
                val inicio = u16(iniciales + 2 * s)
                val fin = u16(finales + 2 * s)
                if (inicio == 0xFFFF) continue
                val delta = s16(deltas + 2 * s)
                val desplazamiento = u16(desplazamientos + 2 * s)
                for (c in inicio..fin) {
                    val glifo = if (desplazamiento == 0) {
                        (c + delta) and 0xFFFF
                    } else {
                        val g = u16(desplazamientos + 2 * s + desplazamiento + 2 * (c - inicio))
                        if (g == 0) 0 else (g + delta) and 0xFFFF
                    }
                    if (glifo != 0) mapa[c] = glifo
                }
            }
        }
        return mapa.filterValues { it in 1 until numGlifos }
    }

    companion object {
        /** Tablas que la norma PDF pide para una TrueType incrustada (cvt, fpgm y prep si existen). */
        private val TABLAS_INCRUSTADAS = listOf("cvt ", "fpgm", "glyf", "head", "hhea", "hmtx", "loca", "maxp", "prep")
        private val OBLIGATORIAS = setOf("glyf", "head", "hhea", "hmtx", "loca", "maxp")

        private fun sumaVerificacion(b: ByteArray): Int {
            var suma = 0
            val relleno = b.copyOf((b.size + 3) / 4 * 4)
            val buf = ByteBuffer.wrap(relleno)
            for (i in relleno.indices step 4) suma += buf.getInt(i)
            return suma
        }

        /** Arma el archivo .ttf: directorio de tablas ordenado, cada tabla alineada a 4 bytes. */
        private fun armar(tablas: List<Pair<String, ByteArray>>): ByteArray {
            val n = tablas.size
            var potencia = 1
            var exponente = 0
            while (potencia * 2 <= n) {
                potencia *= 2
                exponente++
            }
            val cabecera = 12 + 16 * n
            val total = cabecera + tablas.sumOf { (it.second.size + 3) / 4 * 4 }
            val salida = ByteBuffer.allocate(total)
            salida.putInt(0x00010000).putShort(n.toShort()).putShort((potencia * 16).toShort())
                .putShort(exponente.toShort()).putShort((n * 16 - potencia * 16).toShort())
            var posicion = cabecera
            var posicionHead = -1
            for ((etiqueta, contenido) in tablas.sortedBy { it.first }) {
                salida.put(etiqueta.toByteArray(Charsets.US_ASCII)).putInt(sumaVerificacion(contenido))
                    .putInt(posicion).putInt(contenido.size)
                if (etiqueta == "head") posicionHead = posicion
                System.arraycopy(contenido, 0, salida.array(), posicion, contenido.size)
                posicion += (contenido.size + 3) / 4 * 4
            }
            val archivo = salida.array()
            if (posicionHead >= 0) ByteBuffer.wrap(archivo).putInt(posicionHead + 8, (0xB1B0AFBA - sumaVerificacion(archivo)).toInt())
            return archivo
        }
    }
}
