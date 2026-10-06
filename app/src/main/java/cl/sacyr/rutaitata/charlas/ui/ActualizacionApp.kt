package cl.sacyr.rutaitata.charlas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.sacyr.rutaitata.charlas.data.ActualizadorApp
import cl.sacyr.rutaitata.charlas.data.EstadoApp
import kotlinx.coroutines.launch

/** Aviso en la pantalla principal cuando hay una versión nueva de la app. */
@Composable
internal fun AvisoVersionApp(actualizador: ActualizadorApp) {
    val estado = actualizador.estado
    if (estado !is EstadoApp.Disponible && estado !is EstadoApp.Descargando && estado !is EstadoApp.Lista) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("NUEVA VERSIÓN DE LA APP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            ControlesActualizacion(actualizador)
        }
    }
}

/** Estado de la versión de la app y botones para buscar, descargar e instalar. */
@Composable
internal fun ControlesActualizacion(actualizador: ActualizadorApp) {
    val scope = rememberCoroutineScope()
    var faltaPermiso by remember { mutableStateOf(false) }

    when (val estado = actualizador.estado) {
        is EstadoApp.Disponible -> {
            Text("Versión ${estado.version.version} disponible (tienes la ${actualizador.versionActual}).")
            if (estado.version.notas.isNotBlank()) {
                Text(estado.version.notas, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { scope.launch { actualizador.descargar(estado.version) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val megas = estado.version.bytes / 1_000_000.0
                Text(if (megas > 0) "Descargar actualización (%.0f MB)".format(megas) else "Descargar actualización")
            }
        }
        is EstadoApp.Descargando -> {
            Text("Descargando versión ${estado.version.version}…")
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { estado.avance }, modifier = Modifier.fillMaxWidth())
        }
        is EstadoApp.Lista -> {
            Text("Versión ${estado.version.version} descargada. Tus charlas, planes e historial se mantienen.")
            if (faltaPermiso && !actualizador.puedeInstalar()) {
                Text(
                    "Android pide autorizar esta app para instalar actualizaciones: activa 'Permitir de esta fuente', vuelve y toca Instalar.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (actualizador.puedeInstalar()) {
                        actualizador.instalar(estado.apk)
                    } else {
                        faltaPermiso = true
                        actualizador.pedirPermisoInstalacion()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Instalar versión ${estado.version.version}")
            }
        }
        else -> {
            Text(
                when (estado) {
                    EstadoApp.Buscando -> "Buscando una versión nueva de la app…"
                    EstadoApp.AlDia -> "Tienes la última versión publicada de la app."
                    EstadoApp.SinPublicaciones ->
                        "Todavía no hay versiones de la app publicadas en GitHub. Cuando se publique una, aparecerá aquí."
                    is EstadoApp.Error -> "${estado.mensaje}."
                    else -> "La app revisa si hay una versión nueva cada vez que se abre."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { scope.launch { actualizador.buscar() } },
                enabled = estado != EstadoApp.Buscando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Buscar versión nueva de la app")
            }
        }
    }
}
