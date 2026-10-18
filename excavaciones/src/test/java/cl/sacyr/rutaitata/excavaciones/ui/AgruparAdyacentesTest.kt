package cl.sacyr.rutaitata.excavaciones.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cl.sacyr.rutaitata.excavaciones.AppExcavaciones
import cl.sacyr.rutaitata.excavaciones.MainActivity
import cl.sacyr.rutaitata.excavaciones.dominio.BorradorExcavacion
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Desde el dashboard se agrupan dos excavaciones adyacentes y se vuelven a separar. */
// Pantalla de un teléfono común: en la mínima de Robolectric el resumen del día no alcanza a verse.
@Config(qualifiers = "w392dp-h850dp")
@RunWith(RobolectricTestRunner::class)
class AgruparAdyacentesTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = compose.activity.application as AppExcavaciones

    private fun esperarTexto(texto: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun grupos() = runBlocking { app.repositorio.paraReporte(LocalDate.now(), LocalDate.now(), null) }
        .associate { it.numero to it.grupo }

    @Test
    fun agruparYSepararDesdeElDashboard() {
        runBlocking {
            val terreno = app.repositorio.tiposTerreno().first().first().id
            repeat(3) {
                app.repositorio.guardar(
                    BorradorExcavacion(
                        sector = "Sector A", lado = Lado.NORTE, sentido = Sentido.CHILLAN_CONCEPCION,
                        largo = 2.0, ancho = 1.0, profundidad = 1.5, tipoTerrenoId = terreno,
                    ),
                )
            }
        }

        esperarTexto("Agrupar adyacentes")
        compose.onNodeWithTag("agrupar").performClick()
        compose.onNodeWithText("N° 01 ·", substring = true).performClick()
        compose.onNodeWithText("N° 02 ·", substring = true).performClick()
        compose.onNodeWithText("Agrupar").performClick()

        esperarTexto("Agrupada con N° 02")
        val g = grupos()
        assertTrue(g[1] != null && g[1] == g[2])
        assertEquals(null, g[3])

        compose.onNodeWithTag("agrupar").performClick()
        compose.onNodeWithText("N° 01 ·", substring = true).performClick()
        compose.onNodeWithText("Separar").performClick()
        compose.waitUntil(10_000) { grupos().values.all { it == null } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Agrupada con", substring = true).fetchSemanticsNodes().isEmpty() }
    }
}
