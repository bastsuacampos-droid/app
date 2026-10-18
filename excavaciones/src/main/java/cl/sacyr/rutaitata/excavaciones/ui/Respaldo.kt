@file:OptIn(ExperimentalMaterial3Api::class)

package cl.sacyr.rutaitata.excavaciones.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.sacyr.rutaitata.excavaciones.data.Preferencias
import cl.sacyr.rutaitata.excavaciones.data.Respaldo
import cl.sacyr.rutaitata.excavaciones.data.ResumenRespaldo
import cl.sacyr.rutaitata.excavaciones.dominio.Formato
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Días sin respaldo a partir de los cuales el dashboard lo recuerda. */
const val DIAS_AVISO_RESPALDO = 7

/** Días desde el último respaldo (null si nunca se ha respaldado). */
fun diasSinRespaldo(ultimo: Long?, ahora: Long = System.currentTimeMillis()): Long? =
    ultimo?.let { ChronoUnit.DAYS.between(Instant.ofEpochMilli(it), Instant.ofEpochMilli(ahora)) }

private fun fechaHora(ms: Long) = Formato.fechaHora(LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()))

class RespaldoViewModel(
    private val respaldo: Respaldo,
    private val preferencias: Preferencias,
    private val contenido: ContentResolver,
    cantidad: kotlinx.coroutines.flow.Flow<Int>,
) : ViewModel() {

    val excavaciones: StateFlow<Int?> = cantidad.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _ultimo = MutableStateFlow(preferencias.ultimoRespaldo)
    val ultimo: StateFlow<Long?> = _ultimo

    private val _trabajando = MutableStateFlow<String?>(null)
    val trabajando: StateFlow<String?> = _trabajando

    /** Respaldo elegido para restaurar, esperando confirmación. */
    private val _porRestaurar = MutableStateFlow<Pair<Uri, ResumenRespaldo>?>(null)
    val porRestaurar: StateFlow<Pair<Uri, ResumenRespaldo>?> = _porRestaurar

    private val _avisos = Channel<String>(Channel.BUFFERED)
    val avisos = _avisos.receiveAsFlow()

    fun nombreArchivo() = "Respaldo_Excavaciones_${LocalDate.now()}.zip"

    fun guardar(destino: Uri) = trabajar("Guardando respaldo…") {
        val r = withContext(Dispatchers.IO) {
            contenido.openOutputStream(destino, "wt")?.use { respaldo.exportar(it) }
                ?: error("No se pudo abrir el destino")
        }
        val ahora = System.currentTimeMillis()
        preferencias.ultimoRespaldo = ahora
        _ultimo.value = ahora
        "Respaldo guardado: ${r.excavaciones} excavaciones y ${r.fotos} fotos"
    }

    fun elegirParaRestaurar(origen: Uri) = trabajar("Revisando el respaldo…") {
        val resumen = withContext(Dispatchers.IO) {
            contenido.openInputStream(origen)?.use { respaldo.leerResumen(it) } ?: error("No se pudo abrir el archivo")
        }
        _porRestaurar.value = origen to resumen
        null
    }

    fun cancelarRestauracion() {
        _porRestaurar.value = null
    }

    fun restaurar() {
        val (origen, _) = _porRestaurar.value ?: return
        _porRestaurar.value = null
        trabajar("Restaurando…") {
            val r = withContext(Dispatchers.IO) {
                contenido.openInputStream(origen)?.use { respaldo.restaurar(it) } ?: error("No se pudo abrir el archivo")
            }
            "Restaurado: ${r.excavaciones} excavaciones y ${r.fotos} fotos"
        }
    }

    private fun trabajar(mensaje: String, tarea: suspend () -> String?) {
        if (_trabajando.value != null) return
        viewModelScope.launch {
            _trabajando.value = mensaje
            try {
                tarea()?.let { _avisos.send(it) }
            } catch (e: Exception) {
                _avisos.send(e.message ?: "No se pudo completar")
            } finally {
                _trabajando.value = null
            }
        }
    }
}

/**
 * Respaldo completo (.zip con registros y fotos) y restauración. Los registros viven solo en el
 * teléfono: este archivo, guardado en Drive o en un computador, es lo que permite recuperarlos.
 */
@Composable
fun PantallaRespaldo(onVolver: () -> Unit) {
    val vm = vistaModelo { app, _ ->
        RespaldoViewModel(app.respaldo, app.preferencias, app.contentResolver, app.baseDatos.respaldo().observarCantidad())
    }
    val cantidad by vm.excavaciones.collectAsStateWithLifecycle()
    val ultimo by vm.ultimo.collectAsStateWithLifecycle()
    val trabajando by vm.trabajando.collectAsStateWithLifecycle()
    val porRestaurar by vm.porRestaurar.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.avisos.collect { avisos.showSnackbar(it) } }

    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::guardar)
    }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::elegirParaRestaurar)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Respaldo de datos", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val dias = diasSinRespaldo(ultimo)
            val atrasado = (cantidad ?: 0) > 0 && (dias == null || dias >= DIAS_AVISO_RESPALDO)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (atrasado) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Los registros y fotos viven solo en este teléfono",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Si se pierde o se daña, solo se recuperan desde un respaldo. Guarda uno al menos una vez por semana en Drive o en el computador.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Último respaldo: " + (ultimo?.let { fechaHora(it) + if (dias != null && dias > 0) " (hace $dias días)" else "" } ?: "nunca"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    cantidad?.let { Text("Registros en el teléfono: $it excavaciones", style = MaterialTheme.typography.bodySmall) }
                }
            }

            Button(
                onClick = { guardar.launch(vm.nombreArchivo()) },
                enabled = trabajando == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) { Text("Guardar respaldo completo (.zip)", fontWeight = FontWeight.Bold) }
            Text(
                "Incluye todas las excavaciones, fotos, sectores y tipos. Elige Drive para que quede fuera del teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Titulo("Restaurar")
            Text(
                "Para pasar los registros a otro teléfono o recuperarlos. Reemplaza todo lo que hay en este teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { abrir.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
                enabled = trabajando == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Restaurar desde un respaldo…") }

            trabajando?.let {
                androidx.compose.foundation.layout.Row {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(it)
                }
            }
        }
    }

    porRestaurar?.let { (_, r) ->
        AlertDialog(
            onDismissRequest = vm::cancelarRestauracion,
            title = { Text("¿Restaurar este respaldo?") },
            text = {
                Text(
                    "Respaldo del ${fechaHora(r.creadoEn)}: ${r.excavaciones} excavaciones y ${r.fotos} fotos" +
                        (if (r.desde != null && r.hasta != null) ", del ${Formato.fecha(r.desde)} al ${Formato.fecha(r.hasta)}" else "") +
                        ".\n\nSe reemplazarán los ${cantidad ?: 0} registros que hay ahora en el teléfono. Si tienen algo que el respaldo no trae, guarda antes un respaldo de este teléfono.",
                )
            },
            confirmButton = {
                TextButton(onClick = vm::restaurar) { Text("Restaurar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = vm::cancelarRestauracion) { Text("Cancelar") } },
        )
    }
}
