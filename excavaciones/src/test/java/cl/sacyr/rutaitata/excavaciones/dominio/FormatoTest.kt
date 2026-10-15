package cl.sacyr.rutaitata.excavaciones.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FormatoTest {

    @Test
    fun numerosConComaDecimalYPuntoDeMiles() {
        assertEquals("1.234,50", Formato.decimal(1234.5))
        assertEquals("0,00", Formato.decimal(0.0))
        assertEquals("12,35 m³", Formato.volumen(12.345))
        assertEquals("4,00 × 1,20 × 1,50", Formato.dimensiones(4.0, 1.2, 1.5))
    }

    @Test
    fun numeroCorrelativoConDosDigitos() {
        assertEquals("01", Formato.numero(1))
        assertEquals("12", Formato.numero(12))
        assertEquals("125", Formato.numero(125))
    }

    @Test
    fun fechasYCoordenadas() {
        assertEquals("05-10-2026", Formato.fecha(LocalDate.of(2026, 10, 5)))
        assertEquals("-36.61234, -72.10345", Formato.coordenadas(-36.612341, -72.103449))
    }

    @Test
    fun leeDecimalesConComaOPunto() {
        assertEquals(1.5, Formato.leerDecimal("1,5")!!, 0.0)
        assertEquals(1.5, Formato.leerDecimal(" 1.5 ")!!, 0.0)
        assertNull(Formato.leerDecimal(""))
        assertNull(Formato.leerDecimal("uno"))
    }

    @Test
    fun volumenEsLargoPorAnchoPorProfundidad() {
        assertEquals(7.2, Volumen.de(4.0, 1.2, 1.5), 1e-9)
        assertEquals(7.2, BorradorExcavacion(largo = 4.0, ancho = 1.2, profundidad = 1.5).volumen!!, 1e-9)
        assertNull(BorradorExcavacion(largo = 4.0, ancho = 1.2).volumen)
    }

    @Test
    fun validadorExigeLoObligatorio() {
        val errores = ValidadorExcavacion.validar(BorradorExcavacion())
        assertEquals(
            setOf(Campo.SECTOR, Campo.LADO, Campo.SENTIDO, Campo.LARGO, Campo.ANCHO, Campo.PROFUNDIDAD, Campo.TIPO_TERRENO),
            errores.keys,
        )
        val completo = BorradorExcavacion(
            sector = "Sector A", lado = Lado.NORTE, sentido = Sentido.CHILLAN_CONCEPCION,
            largo = 4.0, ancho = 1.2, profundidad = 1.5, tipoTerrenoId = 1,
        )
        assertTrue(ValidadorExcavacion.validar(completo).isEmpty())
        // 150 en vez de 1,50: se avisa.
        assertTrue(Campo.PROFUNDIDAD in ValidadorExcavacion.validar(completo.copy(profundidad = 150.0)))
        assertTrue(Campo.UBICACION in ValidadorExcavacion.validar(completo.copy(latitud = -36.6)))
        assertTrue(Campo.FOTOS in ValidadorExcavacion.validar(completo.copy(fotos = listOf("a", "b", "c", "d"))))
    }
}
