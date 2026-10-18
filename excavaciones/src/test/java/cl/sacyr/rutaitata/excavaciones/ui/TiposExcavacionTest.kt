package cl.sacyr.rutaitata.excavaciones.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import cl.sacyr.rutaitata.excavaciones.AppExcavaciones
import cl.sacyr.rutaitata.excavaciones.MainActivity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoExcavacionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** Tipos de excavación preestablecidos: se eligen una vez y la siguiente excavación nueva ya viene llena. */
// Tamaño por defecto a propósito: con "qualifiers", Robolectric no deja inactivo un diálogo con campos de texto.
@RunWith(RobolectricTestRunner::class)
class TiposExcavacionTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = compose.activity.application as AppExcavaciones

    private fun esperarTexto(texto: String) = esperar("aparezca \"$texto\"") {
        compose.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun esperarQueSeCierre(titulo: String) = esperar("se cierre \"$titulo\"") {
        compose.onAllNodesWithText(titulo).fetchSemanticsNodes().isEmpty()
    }

    /**
     * Si la espera vence, deja la pantalla completa en el registro (en el CI es la única pista:
     * por ejemplo, si el diálogo mostró un error en vez de cerrarse).
     */
    private fun esperar(que: String, condicion: () -> Boolean) = try {
        compose.waitUntil(5_000, condicion)
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        println("No se cumplió que $que. Pantalla:\n" + compose.onRoot(useUnmergedTree = true).printToString())
        throw e
    }

    private fun abrirNueva() {
        compose.onNodeWithTag("nueva").performClick()
        esperarTexto("Ubicación en la obra")
    }

    private fun guardarSinFoto(otra: Boolean) {
        compose.onNodeWithText(if (otra) "Guardar y otra" else "Guardar").performClick()
        compose.onNodeWithText("Guardar sin foto").performClick()
    }

    @Test
    fun elTipoElegidoLlenaLasMedidasYSeRecuerdaEnLaSiguiente() {
        val roca = runBlocking { app.repositorio.tiposTerreno().first().first { it.nombre == "Roca" } }
        runBlocking {
            app.repositorio.guardarTipoExcavacion(
                TipoExcavacionEntity(nombre = "Pocillo de inspección", largo = 1.0, ancho = 1.0, profundidad = 1.5, tipoTerrenoId = roca.id, observaciones = "Con entibado"),
            )
        }
        esperarTexto("Sin excavaciones")
        abrirNueva()

        // Se elige el tipo una vez: llena medidas, terreno y observaciones.
        compose.onNodeWithTag("tipoExcavacion").performClick()
        compose.onNodeWithText("Pocillo de inspección").performClick()
        compose.onNodeWithTag("volumen").performScrollTo().assertTextEquals("1,50 m³")
        compose.onNodeWithTag("tipoTerreno").assertTextContains("Roca")

        compose.onNodeWithTag("sector").performScrollTo().performTextInput("Sector A")
        compose.onNodeWithText("Lado Norte").performScrollTo().performClick()
        compose.onNodeWithText("Chillán → Concepción").performScrollTo().performClick()
        guardarSinFoto(otra = true)

        // "Guardar y otra": la siguiente ya trae el tipo y sus medidas.
        esperarTexto("Registra la siguiente")
        compose.onNodeWithTag("tipoExcavacion").assertTextContains("Pocillo de inspección")
        compose.onNodeWithTag("volumen").performScrollTo().assertTextEquals("1,50 m³")
        guardarSinFoto(otra = false)
        esperarTexto("Excavación N° 02 guardada")

        // Y también al abrir una nueva desde el dashboard otro día.
        abrirNueva()
        compose.onNodeWithTag("tipoExcavacion").assertTextContains("Pocillo de inspección")
        compose.onNodeWithTag("volumen").performScrollTo().assertTextEquals("1,50 m³")

        val guardadas = runBlocking { app.repositorio.resumenDelDia(LocalDate.now()).first() }.excavaciones
        assertEquals(listOf(1, 2), guardadas.map { it.numero })
        assertEquals(listOf("Con entibado", "Con entibado"), guardadas.map { it.observaciones })
        assertEquals(listOf("Roca", "Roca"), guardadas.map { it.tipoTerreno })
    }

    @Test
    fun lasMedidasEscritasSeGuardanComoTipoNuevo() {
        esperarTexto("Sin excavaciones")
        abrirNueva()
        compose.onNodeWithTag("Largo").performScrollTo().performTextInput("6")
        compose.onNodeWithTag("Ancho").performTextInput("0,6")
        compose.onNodeWithTag("Prof.").performTextInput("1,2")

        compose.onNodeWithTag("tipoExcavacion").performScrollTo().performClick()
        compose.onNodeWithText("+ Guardar medidas actuales como tipo…").performClick()
        compose.onNodeWithTag("nombreTipo").performTextInput("Zanja 0,60")
        compose.onNodeWithTag("guardarTipo").performClick()

        compose.waitUntil(5_000) { runBlocking { app.repositorio.tiposExcavacion().first() }.isNotEmpty() }
        val tipo = runBlocking { app.repositorio.tiposExcavacion().first() }.single()
        assertEquals("Zanja 0,60", tipo.nombre)
        assertEquals(listOf(6.0, 0.6, 1.2), listOf(tipo.largo, tipo.ancho, tipo.profundidad))
        // Queda elegido en el formulario.
        compose.onNodeWithTag("tipoExcavacion").assertTextContains("Zanja 0,60")
    }

    @Test
    fun administrarTiposCrearEditarYBorrar() {
        esperarTexto("Sin excavaciones")
        compose.onNode(hasContentDescription("Más opciones")).performClick()
        compose.onNodeWithText("Tipos de excavación").performClick()
        esperarTexto("Sin tipos de excavación")

        compose.onNodeWithText("Nuevo tipo", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("nombreTipo").performTextInput("Calicata")
        compose.onNodeWithTag("guardarTipo").performClick()
        esperarQueSeCierre("Nuevo tipo de excavación")
        compose.onNodeWithText("? × ? × ? m", substring = true).assertExists()

        // Editar: tocar la fila abre el mismo diálogo con sus datos.
        compose.onNodeWithText("Calicata").performClick()
        compose.onNodeWithText("Editar tipo de excavación").assertExists()
        compose.onNodeWithTag("nombreTipo").performTextReplacement("Calicata profunda")
        compose.onNodeWithTag("guardarTipo").performClick()
        esperarQueSeCierre("Editar tipo de excavación")
        compose.onNodeWithText("Calicata profunda").assertExists()

        compose.onNodeWithContentDescription("Borrar Calicata profunda").performClick()
        compose.onNodeWithText("Borrar").performClick()
        esperarTexto("Sin tipos de excavación")
    }
}
