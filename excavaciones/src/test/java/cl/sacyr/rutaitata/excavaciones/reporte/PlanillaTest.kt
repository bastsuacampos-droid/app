package cl.sacyr.rutaitata.excavaciones.reporte

import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.ejemplo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanillaTest {

    @Test
    fun csvParaExcelEnEspanol() {
        val csv = Planilla.csv(
            listOf(
                ejemplo(2, lado = Lado.SUR, observaciones = "Napa; agua \"turbia\"\nse agotó"),
                ejemplo(1, largo = 1234.5),
            ),
        )
        assertTrue("BOM para que Excel lea las tildes", csv.startsWith(Planilla.BOM))
        val lineas = csv.removePrefix(Planilla.BOM.toString()).split("\r\n").filter { it.isNotEmpty() }
        assertEquals(Planilla.COLUMNAS.joinToString(";"), lineas[0])
        // Mismo orden que el reporte (Norte antes que Sur) y números con coma decimal sin miles.
        assertTrue(lineas[1].startsWith("15-10-2026;Sector A;Lado Norte;Chillán-Concepción;1;Km 12+110;1234,50;1,00;1,50;1851,75;Suelo Tipo II;-36,612340;-72,103450;0;"))
        assertTrue(csv.contains(";\"Napa; agua \"\"turbia\"\"\nse agotó\""))
    }

    @Test
    fun nombreDelArchivo() {
        val d = LocalDate.of(2026, 10, 1)
        assertEquals("Excavaciones_2026-10-01.csv", Planilla.nombreArchivo(d, d))
        assertEquals("Excavaciones_2026-10-01_a_2026-10-15.csv", Planilla.nombreArchivo(d, d.plusDays(14)))
    }
}
