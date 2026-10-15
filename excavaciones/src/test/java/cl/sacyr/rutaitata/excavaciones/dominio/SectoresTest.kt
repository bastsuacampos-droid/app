package cl.sacyr.rutaitata.excavaciones.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SectoresTest {

    private val existentes = listOf("Sector A", "Km 12+400 a 14+200", "Km 1", "Puente Itata")

    @Test
    fun ofreceElExistenteCuandoParecerUnErrorDeTipeo() {
        assertEquals("Sector A", Sectores.parecido("Sectro A", existentes))
        assertEquals("Sector A", Sectores.parecido("sector  a", existentes))
        assertEquals("Sector A", Sectores.parecido("Séctor A", existentes))
        assertEquals("Puente Itata", Sectores.parecido("Puente Itat", existentes))
        assertEquals("Km 12+400 a 14+200", Sectores.parecido("km 12+400 a 14 200", existentes))
    }

    @Test
    fun noConfundeSectoresDistintos() {
        assertNull("Ya existe tal cual", Sectores.parecido("sector a", existentes))
        assertNull("Otro tramo: cambian los números", Sectores.parecido("Km 12+600 a 14+200", existentes))
        assertNull("Nombre corto: Km 2 es otro sector", Sectores.parecido("Km 2", existentes))
        assertNull(Sectores.parecido("Sector B", existentes.filter { it != "Sector A" } ))
        assertNull(Sectores.parecido("Botadero norte", existentes))
        assertNull(Sectores.parecido("", existentes))
    }
}
