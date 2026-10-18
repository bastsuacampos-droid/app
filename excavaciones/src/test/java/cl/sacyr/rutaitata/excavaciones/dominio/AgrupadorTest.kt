package cl.sacyr.rutaitata.excavaciones.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgrupadorTest {

    @Test
    fun armaSectorLadoSentidoEnOrden() {
        val lista = listOf(
            ejemplo(2, lado = Lado.SUR, sentido = Sentido.CONCEPCION_CHILLAN),
            ejemplo(3),
            ejemplo(1, sector = "Sector B"),
            ejemplo(1),
            ejemplo(1, lado = Lado.SUR, sentido = Sentido.CONCEPCION_CHILLAN),
            ejemplo(1, lado = Lado.SUR, sentido = Sentido.CHILLAN_CONCEPCION),
        )
        val grupos = Agrupador.agrupar(lista)

        assertEquals(listOf("Sector A", "Sector B"), grupos.map { it.nombre })
        val a = grupos[0]
        assertEquals(listOf(Lado.NORTE, Lado.SUR), a.lados.map { it.lado })
        assertEquals(listOf(1, 3), a.lados[0].sentidos.single().excavaciones.map { it.numero })
        assertEquals(
            listOf(Sentido.CHILLAN_CONCEPCION, Sentido.CONCEPCION_CHILLAN),
            a.lados[1].sentidos.map { it.sentido },
        )
        assertEquals(listOf(1, 2), a.lados[1].sentidos[1].excavaciones.map { it.numero })
        assertEquals(5, a.cantidad)
        assertEquals(lista.sumOf { it.volumen }, grupos.sumOf { it.volumen }, 1e-9)
    }

    @Test
    fun sectoresEnOrdenNatural() {
        val nombres = listOf("Km 12+400 a 14+200", "Sector 10", "km 9+800 a 12+400", "Sector 2")
        val grupos = Agrupador.agrupar(nombres.map { ejemplo(1, sector = it) })
        assertEquals(listOf("km 9+800 a 12+400", "Km 12+400 a 14+200", "Sector 2", "Sector 10"), grupos.map { it.nombre })
    }

    @Test
    fun listaVaciaNoTieneGrupos() {
        assertTrue(Agrupador.agrupar(emptyList()).isEmpty())
    }
}
