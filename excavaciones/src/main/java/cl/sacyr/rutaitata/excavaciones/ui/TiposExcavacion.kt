@file:OptIn(ExperimentalMaterial3Api::class)

package cl.sacyr.rutaitata.excavaciones.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoTerrenoEntity
import cl.sacyr.rutaitata.excavaciones.dominio.Formato
import kotlinx.coroutines.launch

/** "1,00 × 1,00 × 1,50 m · Suelo Tipo II"; una medida sin definir sale como "?" (se escribe cada vez). */
fun resumenTipo(t: TipoExcavacionEntity, terrenos: List<TipoTerrenoEntity>): String {
    val medida = { m: Double? -> m?.let(Formato::decimal) ?: "?" }
    return listOfNotNull(
        "${medida(t.largo)} × ${medida(t.ancho)} × ${medida(t.profundidad)} m",
        terrenos.firstOrNull { it.id == t.tipoTerrenoId }?.nombre,
    ).joinToString(" · ")
}

/**
 * Lista de tipos de excavación preestablecidos: se crean, editan y borran. Borrar un tipo no
 * cambia las excavaciones ya registradas (solo eran una plantilla para llenarlas).
 */
@Composable
fun PantallaTiposExcavacion(onVolver: () -> Unit) {
    val repo = appExcavaciones().repositorio
    val tipos by remember(repo) { repo.tiposExcavacion() }.collectAsStateWithLifecycle(emptyList())
    val terrenos by remember(repo) { repo.tiposTerreno() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var editando by remember { mutableStateOf<TipoExcavacionEntity?>(null) }
    var borrar by remember { mutableStateOf<TipoExcavacionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tipos de excavación", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editando = TipoExcavacionEntity(nombre = "", largo = null, ancho = null, profundidad = null, tipoTerrenoId = null, observaciones = null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo tipo") },
            )
        },
    ) { relleno ->
        if (tipos.isEmpty()) {
            Column(Modifier.padding(relleno)) {
                EstadoVacio(
                    "Sin tipos de excavación",
                    "Crea los que más repites (pocillo de inspección, zanja de 0,60 m…). Al registrar, eliges el tipo y las medidas se llenan solas.",
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(relleno), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(tipos, key = { it.id }) { t ->
                ListItem(
                    headlineContent = { Text(t.nombre, fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Text(listOfNotNull(resumenTipo(t, terrenos), t.observaciones).joinToString("\n"))
                    },
                    trailingContent = {
                        IconButton(onClick = { borrar = t }) { Icon(Icons.Default.Delete, contentDescription = "Borrar ${t.nombre}") }
                    },
                    modifier = Modifier.clickable { editando = t },
                )
                HorizontalDivider()
            }
        }
    }

    editando?.let { t ->
        DialogoTipoExcavacion(
            inicial = t,
            terrenos = terrenos,
            onGuardar = { nuevo -> runCatching { repo.guardarTipoExcavacion(nuevo) }.exceptionOrNull()?.message },
            onCerrar = { editando = null },
        )
    }
    borrar?.let { t ->
        AlertDialog(
            onDismissRequest = { borrar = null },
            title = { Text("¿Borrar \"${t.nombre}\"?") },
            text = { Text("Las excavaciones ya registradas no cambian.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { repo.eliminarTipoExcavacion(t.id) }; borrar = null }) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } },
        )
    }
}

/**
 * Crear o editar un tipo de excavación. [inicial] trae los valores de partida (desde el formulario,
 * las medidas que ya se escribieron). [onGuardar] devuelve el error a mostrar, o null si se guardó.
 */
@Composable
fun DialogoTipoExcavacion(
    inicial: TipoExcavacionEntity,
    terrenos: List<TipoTerrenoEntity>,
    onGuardar: suspend (TipoExcavacionEntity) -> String?,
    onCerrar: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val editable = { m: Double? -> m?.let(FormularioViewModel::decimalEditable).orEmpty() }
    var nombre by rememberSaveable { mutableStateOf(inicial.nombre) }
    var largo by rememberSaveable { mutableStateOf(editable(inicial.largo)) }
    var ancho by rememberSaveable { mutableStateOf(editable(inicial.ancho)) }
    var profundidad by rememberSaveable { mutableStateOf(editable(inicial.profundidad)) }
    var terreno by rememberSaveable { mutableStateOf(inicial.tipoTerrenoId) }
    var observaciones by rememberSaveable { mutableStateOf(inicial.observaciones.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(if (inicial.id == 0L) "Nuevo tipo de excavación" else "Editar tipo de excavación") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it; error = null },
                    label = { Text("Nombre") },
                    placeholder = { Text("Pocillo de inspección") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("nombreTipo"),
                )
                Text(
                    "Medidas en metros. Deja vacía la que cambia cada vez (el largo de una zanja, por ejemplo).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple("Largo", largo) { v: String -> largo = v },
                        Triple("Ancho", ancho) { v: String -> ancho = v },
                        Triple("Prof.", profundidad) { v: String -> profundidad = v },
                    ).forEach { (etiqueta, valor, cambiar) ->
                        OutlinedTextField(
                            value = valor,
                            onValueChange = { v -> cambiar(v.filter { it.isDigit() || it == ',' || it == '.' }.take(7)); error = null },
                            label = { Text(etiqueta) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                SelectorTerrenoOpcional(terrenos, terreno) { terreno = it }
                OutlinedTextField(
                    value = observaciones,
                    onValueChange = { observaciones = it },
                    label = { Text("Observaciones (opcional)") },
                    placeholder = { Text("Entibado con paneles…") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !guardando,
                modifier = Modifier.testTag("guardarTipo"),
                onClick = {
                    val medidas = listOf(largo, ancho, profundidad).map { it.trim() }
                    val leidas = medidas.map { if (it.isEmpty()) null else Formato.leerDecimal(it) }
                    if (medidas.zip(leidas).any { (texto, valor) -> texto.isNotEmpty() && valor == null }) {
                        error = "Revisa las medidas: deben ser números"
                        return@TextButton
                    }
                    guardando = true
                    scope.launch {
                        val resultado = onGuardar(
                            inicial.copy(
                                nombre = nombre,
                                largo = leidas[0],
                                ancho = leidas[1],
                                profundidad = leidas[2],
                                tipoTerrenoId = terreno,
                                observaciones = observaciones,
                            ),
                        )
                        guardando = false
                        if (resultado == null) onCerrar() else error = resultado
                    }
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun SelectorTerrenoOpcional(terrenos: List<TipoTerrenoEntity>, elegido: Long?, onElegir: (Long?) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
        OutlinedTextField(
            value = terrenos.firstOrNull { it.id == elegido }?.nombre ?: "Sin definir",
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo de terreno") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(text = { Text("Sin definir") }, onClick = { onElegir(null); abierto = false })
            terrenos.forEach { t -> DropdownMenuItem(text = { Text(t.nombre) }, onClick = { onElegir(t.id); abierto = false }) }
        }
    }
}
