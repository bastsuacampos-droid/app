package cl.sacyr.rutaitata.excavaciones.reporte

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.FilterOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Escritor PDF 1.4 mínimo: objetos numerados, flujos y la tabla de referencias al final. Escribe a
 * medida que avanza, así una foto se suelta apenas se incrusta y un reporte largo no se junta en
 * memoria.
 */
internal class EscritorPdf(salida: OutputStream) : Closeable {

    private val out = Contador(BufferedOutputStream(salida, 64 * 1024))
    private val posiciones = HashMap<Int, Long>()
    private var siguiente = 1

    init {
        // La segunda línea, con bytes altos, avisa a los programas que el archivo es binario.
        texto("%PDF-1.4\n")
        out.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
    }

    /** Número para un objeto que se escribirá más adelante (para referenciarlo antes). */
    fun reservar(): Int = siguiente++

    fun objeto(numero: Int, cuerpo: String) {
        abrir(numero)
        texto(cuerpo)
        texto("\nendobj\n")
    }

    /** Flujo con [diccionario] (sin << >> ni /Length). Si [comprimir], va con FlateDecode. */
    fun flujo(numero: Int, diccionario: String, datos: ByteArray, comprimir: Boolean = true) {
        val contenido = if (comprimir) desinflar(datos) else datos
        abrir(numero)
        texto("<< $diccionario${if (comprimir) " /Filter /FlateDecode" else ""} /Length ${contenido.size} >>\nstream\n")
        out.write(contenido)
        texto("\nendstream\nendobj\n")
    }

    /** Cierra el documento: tabla de referencias y trailer. */
    fun terminar(raiz: Int, info: Int) {
        val xref = out.cuenta
        val total = siguiente
        texto("xref\n0 $total\n0000000000 65535 f \n")
        for (n in 1 until total) {
            val p = posiciones[n] ?: error("Objeto $n reservado y nunca escrito")
            texto(p.toString().padStart(10, '0') + " 00000 n \n")
        }
        texto("trailer\n<< /Size $total /Root $raiz 0 R /Info $info 0 R >>\nstartxref\n$xref\n%%EOF\n")
        out.flush()
    }

    override fun close() = out.close()

    private fun abrir(numero: Int) {
        check(numero !in posiciones) { "Objeto $numero escrito dos veces" }
        posiciones[numero] = out.cuenta
        texto("$numero 0 obj\n")
    }

    private fun texto(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))

    private class Contador(destino: OutputStream) : FilterOutputStream(destino) {
        var cuenta = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            cuenta++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            cuenta += len
        }
    }

    companion object {
        fun desinflar(datos: ByteArray): ByteArray {
            val salida = ByteArrayOutputStream(datos.size / 3 + 64)
            DeflaterOutputStream(salida, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(datos) }
            return salida.toByteArray()
        }

        /** Número con punto decimal y hasta 2 decimales, sin depender del idioma del teléfono. */
        fun numero(v: Float): String = BigDecimal.valueOf(Math.round(v * 100.0), 2).stripTrailingZeros().toPlainString()

        /** Color ARGB como "r g b" entre 0 y 1. */
        fun color(argb: Int): String = listOf(16, 8, 0).joinToString(" ") { corrimiento ->
            BigDecimal.valueOf(Math.round(((argb shr corrimiento) and 0xFF) * 1000 / 255.0), 3).stripTrailingZeros().toPlainString()
        }

        /** Texto para el diccionario de información (UTF-16 con marca, así lleva tildes). */
        fun textoUnicode(s: String): String =
            "<FEFF" + hex(s.toByteArray(Charsets.UTF_16BE)) + ">"

        private const val DIGITOS = "0123456789ABCDEF"

        fun hex(b: ByteArray): String = buildString(b.size * 2) {
            for (x in b) append(DIGITOS[(x.toInt() shr 4) and 0xF]).append(DIGITOS[x.toInt() and 0xF])
        }
    }
}

/** Ancho, alto y componentes de color de un JPEG, leídos de su marcador SOF. null si no es JPEG. */
internal data class InfoJpeg(val ancho: Int, val alto: Int, val componentes: Int) {
    companion object {
        fun de(b: ByteArray): InfoJpeg? {
            fun u8(i: Int) = b[i].toInt() and 0xFF
            fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
            if (b.size < 4 || u8(0) != 0xFF || u8(1) != 0xD8) return null
            var i = 2
            while (i + 4 <= b.size) {
                if (u8(i) != 0xFF) return null
                val marca = u8(i + 1)
                when {
                    marca == 0xFF -> i++
                    marca == 0x01 || marca in 0xD0..0xD8 -> i += 2
                    marca in 0xC0..0xCF && marca != 0xC4 && marca != 0xC8 && marca != 0xCC -> {
                        if (i + 9 >= b.size) return null
                        val info = InfoJpeg(ancho = u16(i + 7), alto = u16(i + 5), componentes = u8(i + 9))
                        return info.takeIf { it.ancho > 0 && it.alto > 0 }
                    }
                    else -> i += 2 + u16(i + 2)
                }
            }
            return null
        }
    }
}
