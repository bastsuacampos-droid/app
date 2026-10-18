package cl.sacyr.rutaitata.excavaciones.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import cl.sacyr.rutaitata.excavaciones.AppExcavaciones
import cl.sacyr.rutaitata.excavaciones.MainActivity
import cl.sacyr.rutaitata.excavaciones.dominio.BorradorExcavacion
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SectoresTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = compose.activity.application as AppExcavaciones

    private fun registrar(sector: String) = runBlocking {
        val tipo = app.repositorio.tiposTerreno().first().first().id
        app.repositorio.guardar(
            BorradorExcavacion(
                fecha = LocalDate.now(), sector = sector, lado = Lado.NORTE, sentido = Sentido.CHILLAN_CONCEPCION,
                largo = 1.0, ancho = 1.0, profundidad = 1.0, tipoTerrenoId = tipo,
            ),
        )
    }

    private fun esperarTexto(t: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun elFormularioSugiereElSectorExistenteAnteUnErrorDeTipeo() {
        registrar("Sector A")
        esperarTexto("Sector A")
        compose.onNodeWithTag("nueva").performClick()
        esperarTexto("Ubicación en la obra")
        compose.onNodeWithTag("sector").performTextReplacement("Sectro A")
        compose.onNodeWithTag("sugerenciaSector").performScrollTo().performClick()
        compose.onNodeWithTag("sector").assertTextContains("Sector A")
    }

    @Test
    fun corregirUnSectorDuplicadoLoUneConElExistente() {
        registrar("Sector A")
        registrar("Sectro A")
        esperarTexto("Excavaciones del día")
        compose.onNode(hasContentDescription("Más opciones")).performClick()
        compose.onNodeWithText("Sectores").performClick()
        esperarTexto("Toca un sector")

        compose.onNodeWithText("Sectro A").performClick()
        compose.onNodeWithTag("nombreSector").performTextReplacement("Sector A")
        compose.onNodeWithText("Guardar").performClick()
        esperarTexto("¿Unir con")
        compose.onNodeWithText("Unir").performClick()

        esperarTexto("Sectores unidos")
        compose.onNodeWithText("Cambiaron de número", substring = true).assertExists() // N° 01 → N° 02
        val sectores = runBlocking { app.repositorio.sectoresConConteo().first() }
        assertEquals(listOf("Sector A" to 2), sectores.map { it.nombre to it.excavaciones })
    }
}
