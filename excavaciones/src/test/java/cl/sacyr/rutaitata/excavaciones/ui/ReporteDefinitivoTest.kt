package cl.sacyr.rutaitata.excavaciones.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cl.sacyr.rutaitata.excavaciones.AppExcavaciones
import cl.sacyr.rutaitata.excavaciones.MainActivity
import cl.sacyr.rutaitata.excavaciones.dominio.BorradorExcavacion
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Al generar el reporte se puede cerrar el día: sale CERRADO y el día queda cerrado. */
// Pantalla de un teléfono común: en la mínima de Robolectric el resumen del día no alcanza a verse.
@Config(qualifiers = "w392dp-h850dp")
@RunWith(RobolectricTestRunner::class)
class ReporteDefinitivoTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun esperarTexto(texto: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun cerrarElDiaAlGenerarEmiteElReporteDefinitivo() {
        val app = compose.activity.application as AppExcavaciones
        val hoy = LocalDate.now()
        runBlocking {
            val terreno = app.repositorio.tiposTerreno().first().first().id
            app.repositorio.guardar(
                BorradorExcavacion(
                    sector = "Sector A", lado = Lado.NORTE, sentido = Sentido.CHILLAN_CONCEPCION,
                    largo = 2.0, ancho = 1.0, profundidad = 1.5, tipoTerrenoId = terreno,
                ),
            )
        }

        esperarTexto("01")
        compose.onNodeWithText("Reporte PDF").performClick()
        esperarTexto("sale como BORRADOR")

        compose.onNodeWithTag("cerrarDia").performScrollTo().performClick()
        esperarTexto("saldrá como CERRADO")
        compose.onNodeWithText("Cerrar y generar reporte definitivo").performScrollTo().performClick()
        compose.onNodeWithText("Se cerrará el día", substring = true).assertExists()
        compose.onNodeWithText("Cerrar y emitir").performClick()

        esperarTexto("Reporte listo")
        assertNotNull(runBlocking { app.repositorio.cierre(hoy).first() })
        // Ya cerrado: no se ofrece cerrarlo de nuevo y el estado lo dice.
        esperarTexto("Días cerrados")
        assertTrue(compose.onAllNodesWithText("Cerrar el día al generar").fetchSemanticsNodes().isEmpty())
    }
}
