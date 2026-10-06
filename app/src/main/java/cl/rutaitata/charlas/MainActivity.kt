package cl.rutaitata.charlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import cl.rutaitata.charlas.data.BancoCharlas
import cl.rutaitata.charlas.data.Registro
import cl.rutaitata.charlas.ui.PantallaDetalle
import cl.rutaitata.charlas.ui.PantallaEspecialidades
import cl.rutaitata.charlas.ui.PantallaHoy
import cl.rutaitata.charlas.ui.PantallaMarco
import cl.rutaitata.charlas.ui.PantallaPlan
import cl.rutaitata.charlas.ui.TemaCharlas

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TemaCharlas { App() } }
    }
}

private enum class Pestana(val etiqueta: String, val icono: ImageVector) {
    HOY("Hoy", Icons.Filled.Home),
    PLAN("Plan mensual", Icons.Filled.DateRange),
    ESPECIALIDADES("Especialidades", Icons.AutoMirrored.Filled.List),
    MARCO("Marco", Icons.Filled.Info),
}

@Composable
private fun App() {
    val context = LocalContext.current
    val registro = remember { Registro(context.applicationContext) }
    var pestana by rememberSaveable { mutableStateOf(Pestana.HOY) }
    var abierta by rememberSaveable { mutableStateOf<Int?>(null) }

    val charla = abierta?.let { BancoCharlas.porNumero(it) }
    if (charla != null) {
        BackHandler { abierta = null }
        PantallaDetalle(charla, registro, volver = { abierta = null })
        return
    }

    val abrir: (Int) -> Unit = { abierta = it }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Pestana.entries.forEach { p ->
                    NavigationBarItem(
                        selected = p == pestana,
                        onClick = { pestana = p },
                        icon = { Icon(p.icono, contentDescription = null) },
                        label = { Text(p.etiqueta) },
                    )
                }
            }
        },
    ) { padding ->
        when (pestana) {
            Pestana.HOY -> PantallaHoy(registro, padding, abrir)
            Pestana.PLAN -> PantallaPlan(registro, padding, abrir)
            Pestana.ESPECIALIDADES -> PantallaEspecialidades(registro, padding, abrir)
            Pestana.MARCO -> PantallaMarco(padding)
        }
    }
}
