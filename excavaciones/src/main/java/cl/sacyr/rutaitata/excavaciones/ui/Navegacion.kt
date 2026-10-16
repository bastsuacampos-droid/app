package cl.sacyr.rutaitata.excavaciones.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.sacyr.rutaitata.excavaciones.AppExcavaciones
import java.io.File

/** Mensaje que una pantalla deja para mostrarlo en el dashboard al volver ("Excavación N° 05 guardada"). */
const val CLAVE_MENSAJE = "mensaje"

/** ViewModel creado con el contenedor de la app y el estado guardado de la pantalla (sobrevive si Android cierra la app). */
@Composable
inline fun <reified VM : ViewModel> vistaModelo(crossinline crear: (AppExcavaciones, SavedStateHandle) -> VM): VM =
    viewModel(
        factory = viewModelFactory {
            initializer { crear(this[APPLICATION_KEY] as AppExcavaciones, createSavedStateHandle()) }
        },
    )

@Composable
fun appExcavaciones(): AppExcavaciones = LocalContext.current.applicationContext as AppExcavaciones

@Composable
fun AppNavegacion() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "inicio") {
        composable("inicio") { entrada ->
            PantallaInicio(
                mensajes = entrada.savedStateHandle,
                onNueva = { fecha -> nav.navigate("formulario?id=0&fecha=${fecha.toEpochDay()}") },
                onAbrir = { id -> nav.navigate("formulario?id=$id") },
                onReporte = { fecha -> nav.navigate("reporte?fecha=${fecha.toEpochDay()}") },
                onTiposTerreno = { nav.navigate("tipos") },
                onTiposExcavacion = { nav.navigate("tiposExcavacion") },
                onRespaldo = { nav.navigate("respaldo") },
                onSectores = { nav.navigate("sectores") },
            )
        }
        composable(
            "formulario?id={id}&fecha={fecha}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                navArgument("fecha") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
            ),
        ) {
            PantallaFormulario(
                onTerminar = { mensaje ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(CLAVE_MENSAJE, mensaje)
                    nav.popBackStack()
                },
                onVolver = { nav.popBackStack() },
                onTiposTerreno = { nav.navigate("tipos") },
                onTiposExcavacion = { nav.navigate("tiposExcavacion") },
            )
        }
        composable(
            "reporte?fecha={fecha}",
            arguments = listOf(navArgument("fecha") { type = NavType.LongType; defaultValue = Long.MIN_VALUE }),
        ) {
            PantallaReporte(
                onVolver = { nav.popBackStack() },
                onVisualizar = { archivo -> nav.navigate("visor?ruta=${Uri.encode(archivo.path)}") },
            )
        }
        composable("visor?ruta={ruta}", arguments = listOf(navArgument("ruta") { type = NavType.StringType })) { entrada ->
            PantallaVisorPdf(File(entrada.arguments?.getString("ruta").orEmpty()), onVolver = { nav.popBackStack() })
        }
        composable("sectores") {
            PantallaSectores(onVolver = { nav.popBackStack() })
        }
        composable("respaldo") {
            PantallaRespaldo(onVolver = { nav.popBackStack() })
        }
        composable("tiposExcavacion") {
            PantallaTiposExcavacion(onVolver = { nav.popBackStack() })
        }
        composable("tipos") {
            PantallaTiposTerreno(onVolver = { nav.popBackStack() })
        }
    }
}
