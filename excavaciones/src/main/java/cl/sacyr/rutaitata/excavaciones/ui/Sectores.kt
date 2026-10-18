@file:OptIn(ExperimentalMaterial3Api::class)

package cl.sacyr.rutaitata.excavaciones.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.sacyr.rutaitata.excavaciones.data.Renumeracion
import cl.sacyr.rutaitata.excavaciones.data.SectorExistente
import cl.sacyr.rutaitata.excavaciones.data.local.dao.SectorConConteo
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.SectorEntity
import cl.sacyr.rutaitata.excavaciones.dominio.Formato
import kotlinx.coroutines.launch

private fun cantidadExcavaciones(n: Int) = if (n == 1) "1 excavación" else "$n excavaciones"

/** "N° 01 → N° 08 (Lado Norte, Chillán-Concepción)" por cada excavación que cambió de número. */
private fun resumenRenumeracion(cambios: List<Renumeracion>): String =
    if (cambios.isEmpty()) {
        ""
    } else {
        " Cambiaron de número: " + cambios.joinToString("; ") {
            "N° ${Formato.numero(it.antes)} → N° ${Formato.numero(it.despues)} (${it.lado.etiqueta}, ${it.sentido.etiquetaCorta})"
        }
    }

/**
 * Sectores registrados: corregir cómo se escribió uno, unir un duplicado con el correcto o borrar
 * uno que quedó vacío. Un sector con excavaciones en días cerrados no se toca (su reporte ya salió).
 */
@Composable
fun PantallaSectores(onVolver: () -> Unit) {
    val repo = appExcavaciones().repositorio
    val sectores by remember(repo) { repo.sectoresConConteo() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val avisos = remember { SnackbarHostState() }
    var editando by remember { mutableStateOf<SectorConConteo?>(null) }
    var unir by remember { mutableStateOf<Pair<SectorConConteo, SectorEntity>?>(null) }
    var borrar by remember { mutableStateOf<SectorConConteo?>(null) }

    val avisar = { texto: String -> scope.launch { avisos.showSnackbar(texto) }; Unit }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sectores", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { relleno ->
        if (sectores.isEmpty()) {
            Column(Modifier.padding(relleno)) {
                EstadoVacio("Sin sectores", "Los sectores se crean al registrar la primera excavación de cada uno.")
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(relleno)) {
            item {
                Text(
                    "Toca un sector para corregir su nombre. Si lo cambias al de otro existente, se ofrece unirlos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(sectores, key = { it.id }) { s ->
                ListItem(
                    headlineContent = { Text(s.nombre, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(cantidadExcavaciones(s.excavaciones)) },
                    trailingContent = {
                        if (s.excavaciones == 0) {
                            IconButton(onClick = { borrar = s }) { Icon(Icons.Default.Delete, contentDescription = "Borrar ${s.nombre}") }
                        }
                    },
                    modifier = Modifier.clickable { editando = s },
                )
                HorizontalDivider()
            }
        }
    }

    editando?.let { s ->
        var nombre by rememberSaveable(s.id) { mutableStateOf(s.nombre) }
        var error by remember(s.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editando = null },
            title = { Text("Corregir sector") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it; error = null },
                        label = { Text("Nombre") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().testTag("nombreSector"),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            repo.renombrarSector(s.id, nombre)
                            editando = null
                        } catch (e: SectorExistente) {
                            editando = null
                            unir = s to e.otro
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editando = null }) { Text("Cancelar") } },
        )
    }

    unir?.let { (origen, destino) ->
        AlertDialog(
            onDismissRequest = { unir = null },
            title = { Text("¿Unir con \"${destino.nombre}\"?") },
            text = {
                Text(
                    "Ya existe \"${destino.nombre}\". Las ${cantidadExcavaciones(origen.excavaciones)} de \"${origen.nombre}\" pasarán a " +
                        "\"${destino.nombre}\" con su lado y sentido, y \"${origen.nombre}\" desaparecerá. Si un número ya está " +
                        "usado, la excavación que se mueve toma el siguiente libre.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    unir = null
                    scope.launch {
                        runCatching { repo.unirSectores(origen.id, destino.id) }
                            .onSuccess { avisar("Sectores unidos." + resumenRenumeracion(it)) }
                            .onFailure { avisar(it.message ?: "No se pudieron unir") }
                    }
                }) { Text("Unir") }
            },
            dismissButton = { TextButton(onClick = { unir = null }) { Text("Cancelar") } },
        )
    }

    borrar?.let { s ->
        AlertDialog(
            onDismissRequest = { borrar = null },
            title = { Text("¿Borrar \"${s.nombre}\"?") },
            text = { Text("No tiene excavaciones. Dejará de aparecer en las sugerencias del formulario.") },
            confirmButton = {
                TextButton(onClick = {
                    borrar = null
                    scope.launch { runCatching { repo.eliminarSector(s.id) }.onFailure { avisar(it.message ?: "No se pudo borrar") } }
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } },
        )
    }
}
