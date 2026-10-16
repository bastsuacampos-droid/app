package cl.sacyr.rutaitata.excavaciones.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cl.sacyr.rutaitata.excavaciones.AppExcavaciones
import cl.sacyr.rutaitata.excavaciones.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** Un día cerrado se ve como tal en el dashboard y solo se reabre con un motivo. */
@RunWith(RobolectricTestRunner::class)
class CierreDiaTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun esperar(condicion: () -> Boolean) = compose.waitUntil(5_000, condicion)

    @Test
    fun diaCerradoSinBotonNuevaYReaperturaConMotivo() {
        val app = compose.activity.application as AppExcavaciones
        val hoy = LocalDate.now()
        runBlocking { app.repositorio.cerrarDias(hoy, hoy, "María González") }

        esperar { compose.onAllNodesWithText("Día cerrado").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, compose.onAllNodesWithTag("nueva").fetchSemanticsNodes().size)

        compose.onNodeWithText("Reabrir").performClick()
        compose.onNodeWithTag("motivo").performTextInput("Faltó registrar la N° 04")
        compose.onAllNodesWithText("Reabrir")[1].performClick()

        esperar { compose.onAllNodesWithTag("nueva").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Día cerrado").assertDoesNotExist()
        val reaperturas = runBlocking { app.repositorio.cierresYReaperturas(hoy, hoy) }.second
        assertEquals("Faltó registrar la N° 04", reaperturas.single().motivo)
    }
}
