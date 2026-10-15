package cl.sacyr.rutaitata.excavaciones.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import cl.sacyr.rutaitata.excavaciones.AppExcavaciones
import cl.sacyr.rutaitata.excavaciones.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** Recorre la app como en terreno: dashboard → nueva excavación → guardar → aparece en el día. */
@RunWith(RobolectricTestRunner::class)
class FlujoRegistroTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun esperarTexto(texto: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun registrarUnaExcavacionLaMuestraEnElResumenDelDia() {
        esperarTexto("Sin excavaciones")
        compose.onNodeWithTag("nueva").performClick()
        esperarTexto("Ubicación en la obra")

        compose.onNodeWithTag("sector").performTextInput("Sector A")
        // Como en el teléfono: se desplaza hasta el botón (la barra de Guardar tapa el borde inferior).
        compose.onNodeWithText("Lado Norte").performScrollTo().performClick()
        compose.onNodeWithText("Chillán → Concepción").performScrollTo().performClick()
        compose.onNodeWithTag("Largo").performScrollTo().performTextInput("4")
        compose.onNodeWithTag("Ancho").performTextInput("1,2")
        compose.onNodeWithTag("Prof. media").performTextInput("1.5")
        compose.onNodeWithTag("volumen").performScrollTo().assertTextEquals("7,20 m³")
        compose.onNodeWithTag("tipoTerreno").performScrollTo().performClick()
        compose.onNodeWithText("Roca").performClick()

        compose.onNodeWithText("Guardar").performClick()
        // Sin foto pide confirmación: la foto es el respaldo.
        compose.onNodeWithText("¿Guardar sin foto?").assertExists()
        compose.onNodeWithText("Guardar sin foto").performClick()

        esperarTexto("Excavación N° 01 guardada")
        compose.onNodeWithText("Sector A").assertExists()
        compose.onNodeWithText("Sentido Chillán → Concepción").assertExists()
        compose.onNodeWithText("01").assertExists()
        assertEquals(2, compose.onAllNodesWithText("7,20 m³").fetchSemanticsNodes().size) // indicador del día y fila
        compose.onNodeWithText("Sin foto").assertExists()

        val app = compose.activity.application as AppExcavaciones
        val guardada = runBlocking { app.repositorio.resumenDelDia(LocalDate.now()).first() }.excavaciones.single()
        assertEquals("Roca", guardada.tipoTerreno)
        assertEquals(1.5, guardada.profundidad, 0.0)
    }

    @Test
    fun guardarIncompletoMuestraQueFalta() {
        esperarTexto("Sin excavaciones")
        compose.onNodeWithTag("nueva").performClick()
        esperarTexto("Ubicación en la obra")
        compose.onNodeWithText("Guardar").performClick()
        compose.onNodeWithText("¿Guardar sin foto?").assertExists()
        compose.onNodeWithText("Guardar sin foto").performClick()

        compose.onNodeWithText("Indica el sector o tramo").assertExists()
        compose.onNodeWithText("Elige Lado Norte o Lado Sur").assertExists()
        // Sigue en el formulario.
        compose.onNodeWithText("Nueva excavación").assertExists()
    }
}
