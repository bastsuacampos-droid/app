package cl.sacyr.rutaitata.excavaciones.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import cl.sacyr.rutaitata.excavaciones.AppExcavaciones
import cl.sacyr.rutaitata.excavaciones.MainActivity
import cl.sacyr.rutaitata.excavaciones.dominio.BorradorExcavacion
import cl.sacyr.rutaitata.excavaciones.dominio.FotoBorrador
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.OrigenFoto
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Tocar una foto del formulario la abre a pantalla completa, con zoom, y se cierra. */
@Config(qualifiers = "w392dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class VisorFotoTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun esperar(condicion: () -> Boolean) = compose.waitUntil(5_000, condicion)

    @Test
    fun fotoAPantallaCompletaConZoom() {
        val app = compose.activity.application as AppExcavaciones
        app.fotos.carpeta.mkdirs()
        val archivo = File(app.fotos.carpeta, "prueba.jpg")
        val bmp = Bitmap.createBitmap(2560, 1920, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF8D6E63.toInt()) }
        archivo.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val terreno = runBlocking { app.repositorio.tiposTerreno().first() }.first().id
        runBlocking {
            app.repositorio.guardar(
                BorradorExcavacion(
                    sector = "Sector A", lado = Lado.NORTE, sentido = Sentido.CHILLAN_CONCEPCION,
                    largo = 2.0, ancho = 1.0, profundidad = 1.5, tipoTerrenoId = terreno,
                    fotos = listOf(FotoBorrador(archivo.name, tomadaEn = null, origen = OrigenFoto.CAMARA)),
                ),
            )
        }

        esperar { compose.onAllNodesWithText("01").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("01").performClick()
        esperar { compose.onAllNodesWithContentDescription("Foto 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Foto 1").performScrollTo().performClick()

        // Se abre a resolución completa y el doble toque la amplía sin cerrarla.
        esperar { compose.onAllNodesWithText("Separa dos dedos", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("visorImagen").performTouchInput { doubleClick() }
        compose.onNodeWithTag("visorImagen").assertExists()

        compose.onNodeWithContentDescription("Cerrar").performClick()
        esperar { compose.onAllNodesWithText("Separa dos dedos", substring = true).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Excavación N° 01").assertExists()
    }
}
