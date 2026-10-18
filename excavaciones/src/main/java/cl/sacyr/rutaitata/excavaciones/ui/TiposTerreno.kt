@file:OptIn(ExperimentalMaterial3Api::class)

package cl.sacyr.rutaitata.excavaciones.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoTerrenoEntity
import kotlinx.coroutines.launch

/**
 * Catálogo de tipos de terreno del menú desplegable. Quitar un tipo solo lo oculta del menú: las
 * excavaciones ya registradas lo conservan.
 */
@Composable
fun PantallaTiposTerreno(onVolver: () -> Unit) {
    val repo = appExcavaciones().repositorio
    val tipos by remember(repo) { repo.tiposTerreno() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var agregar by rememberSaveable { mutableStateOf(false) }
    var quitar by remember { mutableStateOf<TipoTerrenoEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tipos de terreno", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { agregar = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Agregar tipo") },
            )
        },
    ) { relleno ->
        LazyColumn(Modifier.fillMaxSize().padding(relleno)) {
            items(tipos, key = { it.id }) { t ->
                ListItem(
                    headlineContent = { Text(t.nombre) },
                    trailingContent = {
                        IconButton(onClick = { quitar = t }) { Icon(Icons.Default.Delete, contentDescription = "Quitar ${t.nombre}") }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (agregar) {
        DialogoNuevoTipo(onAgregar = { scope.launch { runCatching { repo.agregarTipoTerreno(it) } } }, onCerrar = { agregar = false })
    }
    quitar?.let { t ->
        AlertDialog(
            onDismissRequest = { quitar = null },
            title = { Text("¿Quitar \"${t.nombre}\" del menú?") },
            text = { Text("Las excavaciones ya registradas con este tipo no cambian. Si lo vuelves a agregar con el mismo nombre, reaparece.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { repo.desactivarTipoTerreno(t.id) }; quitar = null }) {
                    Text("Quitar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { quitar = null }) { Text("Cancelar") } },
        )
    }
}
