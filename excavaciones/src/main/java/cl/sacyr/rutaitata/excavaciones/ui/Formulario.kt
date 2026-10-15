@file:OptIn(ExperimentalMaterial3Api::class)

package cl.sacyr.rutaitata.excavaciones.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.sacyr.rutaitata.excavaciones.R
import cl.sacyr.rutaitata.excavaciones.data.ArchivosFotos
import cl.sacyr.rutaitata.excavaciones.data.ProveedorUbicacion
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoExcavacionEntity
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.TipoTerrenoEntity
import cl.sacyr.rutaitata.excavaciones.dominio.Campo
import cl.sacyr.rutaitata.excavaciones.dominio.Formato
import cl.sacyr.rutaitata.excavaciones.dominio.FotoBorrador
import cl.sacyr.rutaitata.excavaciones.dominio.Lado
import cl.sacyr.rutaitata.excavaciones.dominio.MAX_FOTOS
import cl.sacyr.rutaitata.excavaciones.dominio.OrigenFoto
import cl.sacyr.rutaitata.excavaciones.dominio.Sentido
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Registro y edición de una excavación, pensado para una mano: todo en una columna, opciones en
 * botones grandes, teclado numérico en las medidas y "Guardar" siempre visible abajo.
 */
@Composable
fun PantallaFormulario(
    onTerminar: (String) -> Unit,
    onVolver: () -> Unit,
    onTiposTerreno: () -> Unit,
    onTiposExcavacion: () -> Unit,
) {
    val app = appExcavaciones()
    val vm = vistaModelo { a, estado ->
        FormularioViewModel(a.repositorio, a.fotos, a.ubicacion, a.preferencias, a.contentResolver, estado)
    }
    val estado by vm.estado.collectAsStateWithLifecycle()
    val erroresValidacion by vm.errores.collectAsStateWithLifecycle()
    val erroresGuardado by vm.erroresGuardado.collectAsStateWithLifecycle()
    val errores = erroresValidacion + erroresGuardado
    val tipos by vm.tiposTerreno.collectAsStateWithLifecycle()
    val tiposExcavacion by vm.tiposExcavacion.collectAsStateWithLifecycle()
    val sectores by vm.sectores.collectAsStateWithLifecycle()
    val siguiente by vm.siguienteNumero.collectAsStateWithLifecycle()
    val buscandoUbicacion by vm.buscandoUbicacion.collectAsStateWithLifecycle()
    val procesandoFoto by vm.procesandoFoto.collectAsStateWithLifecycle()
    val guardando by vm.guardando.collectAsStateWithLifecycle()
    val cierre by vm.cierre.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.eventos.collect { evento ->
            when (evento) {
                is EventoFormulario.Terminado -> onTerminar(evento.mensaje)
                is EventoFormulario.Aviso -> {
                    val r = avisos.showSnackbar(
                        evento.texto,
                        actionLabel = if (evento.accion != null) "Ajustes" else null,
                        duration = if (evento.accion != null) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (r == SnackbarResult.ActionPerformed) {
                        val intent = when (evento.accion) {
                            AccionAviso.AJUSTES_UBICACION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            AccionAviso.AJUSTES_APP -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            null -> null
                        }
                        intent?.let { runCatching { context.startActivity(it) } }
                    }
                }
            }
        }
    }

    // Cámara del teléfono (sin permiso CAMERA) y selector de fotos del sistema (sin permiso de almacenamiento).
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.capturaTerminada(ok) }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::fotoDeGaleria) }
    // Con o sin permiso concedido se intenta: sin permiso, el ViewModel avisa y ofrece ir a los ajustes.
    val permisoUbicacion = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.obtenerUbicacion() }
    val tomarFoto = {
        val archivo = vm.prepararCaptura()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)
        try {
            camara.launch(uri)
        } catch (e: ActivityNotFoundException) {
            vm.capturaTerminada(false)
        }
    }

    var confirmarEliminar by rememberSaveable { mutableStateOf(false) }
    var confirmarSinFoto by rememberSaveable { mutableStateOf<Boolean?>(null) } // valor: "guardar y otra"
    var elegirFecha by rememberSaveable { mutableStateOf(false) }
    val guardar = { otra: Boolean ->
        if (estado.fotos.isEmpty()) confirmarSinFoto = otra else vm.guardar(otra)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (estado.nueva) "Nueva excavación" else "Excavación N° ${estado.numero.toIntOrNull()?.let(Formato::numero) ?: estado.numero}",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
                actions = {
                    if (!estado.nueva && cierre == null) {
                        IconButton(onClick = { confirmarEliminar = true }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar excavación") }
                    }
                },
            )
        },
        bottomBar = {
            val c = cierre
            if (c != null) {
                // Día cerrado: no se puede guardar. Se explica en vez de mostrar botones que fallarían.
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        "Día ${Formato.fecha(c.fecha)} cerrado el ${Formato.fechaHora(LocalDateTime.ofInstant(Instant.ofEpochMilli(c.cerradoEn), ZoneId.systemDefault()))} " +
                            "(reporte emitido). Para modificarlo, reábrelo desde el dashboard indicando el motivo.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    )
                }
            } else {
                Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (estado.nueva) {
                            OutlinedButton(
                                onClick = { guardar(true) },
                                enabled = !guardando && estado.cargado,
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            ) { Text("Guardar y otra") }
                        }
                        Button(
                            onClick = { guardar(false) },
                            enabled = !guardando && estado.cargado,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { relleno ->
        if (!estado.cargado) {
            Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------------------------------------------------------- plantilla
            SelectorTipoExcavacion(
                tipos = tiposExcavacion,
                terrenos = tipos,
                estado = estado,
                onElegir = vm::elegirTipoExcavacion,
                onGuardarNuevo = vm::guardarTipoExcavacion,
                onAdministrar = onTiposExcavacion,
            )

            // ---------------------------------------------------------- dónde
            Titulo("Ubicación en la obra")
            OutlinedButton(onClick = { elegirFecha = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(diaLargo(LocalDate.ofEpochDay(estado.fecha)) + " · " + Formato.fecha(LocalDate.ofEpochDay(estado.fecha)))
            }
            CampoSector(estado.sector, sectores, errores[Campo.SECTOR]) { vm.actualizar { copy(sector = it) } }
            Text("Calzada", style = MaterialTheme.typography.labelLarge)
            SelectorOpciones(
                opciones = Lado.entries,
                elegida = estado.lado,
                etiqueta = { it.etiqueta },
                onElegir = { vm.actualizar { copy(lado = it) } },
                error = Campo.LADO in errores,
            )
            errores[Campo.LADO]?.let { TextoError(it) }
            Text("Sentido de avance", style = MaterialTheme.typography.labelLarge)
            SelectorOpciones(
                opciones = Sentido.entries,
                elegida = estado.sentido,
                etiqueta = { it.etiqueta },
                onElegir = { vm.actualizar { copy(sentido = it) } },
                error = Campo.SENTIDO in errores,
            )
            errores[Campo.SENTIDO]?.let { TextoError(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = estado.numero,
                    onValueChange = { v -> vm.actualizar { copy(numero = v.filter(Char::isDigit).take(4)) } },
                    label = { Text("N° excavación") },
                    placeholder = { Text(siguiente?.let { Formato.numero(it) } ?: "auto") },
                    supportingText = {
                        Text(
                            errores[Campo.NUMERO] ?: when {
                                estado.numero.isEmpty() -> "Automático"
                                estado.nueva -> "Manual"
                                else -> "Correlativo"
                            },
                        )
                    },
                    isError = Campo.NUMERO in errores,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(0.42f).testTag("numero"),
                )
                OutlinedTextField(
                    value = estado.progresiva,
                    onValueChange = { v -> vm.actualizar { copy(progresiva = v) } },
                    label = { Text("Progresiva") },
                    placeholder = { Text("Km 12+450") },
                    supportingText = { Text("Opcional") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(0.58f),
                )
            }

            // ---------------------------------------------------------- medidas
            HorizontalDivider()
            Titulo("Dimensiones (metros)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoMedida("Largo", estado.largo, errores[Campo.LARGO], Modifier.weight(1f)) { vm.actualizar { copy(largo = it) } }
                CampoMedida("Ancho", estado.ancho, errores[Campo.ANCHO], Modifier.weight(1f)) { vm.actualizar { copy(ancho = it) } }
                CampoMedida("Prof. media", estado.profundidad, errores[Campo.PROFUNDIDAD], Modifier.weight(1f)) {
                    vm.actualizar { copy(profundidad = it) }
                }
            }
            listOf(Campo.LARGO, Campo.ANCHO, Campo.PROFUNDIDAD).mapNotNull { errores[it] }.distinct().forEach { TextoError(it) }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Volumen estimado", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        estado.borrador().volumen?.let(Formato::volumen) ?: "— m³",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("volumen"),
                    )
                }
            }
            SelectorTipoTerreno(
                tipos = tipos,
                elegido = estado.tipoTerrenoId,
                error = errores[Campo.TIPO_TERRENO],
                onElegir = { vm.actualizar { copy(tipoTerrenoId = it) } },
                onAgregar = vm::agregarTipoTerreno,
                onAdministrar = onTiposTerreno,
            )

            // ---------------------------------------------------------- GPS
            HorizontalDivider()
            Titulo("Ubicación GPS")
            SeccionUbicacion(
                estado = estado,
                buscando = buscandoUbicacion,
                error = errores[Campo.UBICACION],
                onObtener = {
                    if (app.ubicacion.tienePermiso()) vm.obtenerUbicacion() else permisoUbicacion.launch(ProveedorUbicacion.PERMISOS)
                },
                onQuitar = vm::quitarUbicacion,
            )

            // ---------------------------------------------------------- fotos
            HorizontalDivider()
            Titulo("Fotos de respaldo (${estado.fotos.size} de $MAX_FOTOS)")
            Text(
                "Fotografía la excavación terminada: las fotos van en el anexo del reporte como respaldo de cómo quedó.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SeccionFotos(
                archivos = app.fotos,
                fotos = estado.fotos,
                procesando = procesandoFoto,
                onCamara = tomarFoto,
                onGaleria = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onQuitar = vm::quitarFoto,
            )

            // ---------------------------------------------------------- observaciones
            HorizontalDivider()
            OutlinedTextField(
                value = estado.observaciones,
                onValueChange = { v -> vm.actualizar { copy(observaciones = v) } },
                label = { Text("Observaciones") },
                placeholder = { Text("Napa, interferencias, estado del entibado…") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (elegirFecha) {
        DialogoFecha(
            LocalDate.ofEpochDay(estado.fecha),
            onElegir = { f -> vm.actualizar { copy(fecha = f.toEpochDay()) } },
            onCerrar = { elegirFecha = false },
        )
    }
    if (confirmarEliminar) {
        AlertDialog(
            onDismissRequest = { confirmarEliminar = false },
            title = { Text("¿Eliminar esta excavación?") },
            text = { Text("Se borran sus datos y sus fotos. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { confirmarEliminar = false; vm.eliminar() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarEliminar = false }) { Text("Cancelar") } },
        )
    }
    confirmarSinFoto?.let { otra ->
        AlertDialog(
            onDismissRequest = { confirmarSinFoto = null },
            title = { Text("¿Guardar sin foto?") },
            text = { Text("Sin foto no queda respaldo de cómo quedó la excavación; el reporte la marcará \"Sin foto\".") },
            confirmButton = {
                TextButton(onClick = { confirmarSinFoto = null; tomarFoto() }) { Text("Tomar foto") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarSinFoto = null; vm.guardar(otra) }) { Text("Guardar sin foto") }
            },
        )
    }
}

@Composable
private fun TextoError(texto: String) {
    Text(texto, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

/**
 * Tipo de excavación preestablecido: llena medidas, terreno y observaciones. Se recuerda el último
 * elegido para la próxima excavación nueva. "Guardar como tipo" crea uno con lo ya escrito.
 */
@Composable
private fun SelectorTipoExcavacion(
    tipos: List<TipoExcavacionEntity>,
    terrenos: List<TipoTerrenoEntity>,
    estado: EstadoFormulario,
    onElegir: (Long?) -> Unit,
    onGuardarNuevo: suspend (TipoExcavacionEntity) -> String?,
    onAdministrar: () -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    var crear by rememberSaveable { mutableStateOf(false) }
    val elegido = tipos.firstOrNull { it.id == estado.tipoExcavacionId }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
                OutlinedTextField(
                    value = elegido?.nombre ?: "Ninguno (ingreso manual)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tipo de excavación") },
                    supportingText = {
                        Text(
                            when {
                                elegido != null -> resumenTipo(elegido, terrenos)
                                tipos.isEmpty() -> "Guarda las medidas que más repites como tipo y se llenarán solas"
                                else -> "Elige un tipo para llenar las medidas"
                            },
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("tipoExcavacion"),
                )
                ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                    DropdownMenuItem(text = { Text("Ninguno (ingreso manual)") }, onClick = { onElegir(null); abierto = false })
                    tipos.forEach { t ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(t.nombre, fontWeight = FontWeight.Medium)
                                    Text(resumenTipo(t, terrenos), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = { onElegir(t.id); abierto = false },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("+ Guardar medidas actuales como tipo…") }, onClick = { abierto = false; crear = true })
                    DropdownMenuItem(text = { Text("Administrar tipos") }, onClick = { abierto = false; onAdministrar() })
                }
            }
            if (elegido == null && tipos.isEmpty()) {
                TextButton(onClick = { crear = true }) { Text("+ Guardar medidas actuales como tipo") }
            }
        }
    }
    if (crear) {
        val b = estado.borrador()
        DialogoTipoExcavacion(
            inicial = TipoExcavacionEntity(
                nombre = "",
                largo = b.largo,
                ancho = b.ancho,
                profundidad = b.profundidad,
                tipoTerrenoId = estado.tipoTerrenoId,
                observaciones = null,
            ),
            terrenos = terrenos,
            onGuardar = onGuardarNuevo,
            onCerrar = { crear = false },
        )
    }
}

/** Sector: se escribe libre o se elige uno ya usado. */
@Composable
private fun CampoSector(valor: String, sectores: List<String>, error: String?, onCambio: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val sugeridos = sectores.filter { valor.isBlank() || it.contains(valor.trim(), ignoreCase = true) }.take(8)
    ExposedDropdownMenuBox(expanded = abierto && sugeridos.isNotEmpty(), onExpandedChange = { abierto = it }) {
        OutlinedTextField(
            value = valor,
            onValueChange = { onCambio(it); abierto = true },
            label = { Text("Sector / tramo") },
            placeholder = { Text("Sector A, Km 12+400 a 14+200…") },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
            trailingIcon = { if (sectores.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable).testTag("sector"),
        )
        ExposedDropdownMenu(expanded = abierto && sugeridos.isNotEmpty(), onDismissRequest = { abierto = false }) {
            sugeridos.forEach { s ->
                DropdownMenuItem(text = { Text(s) }, onClick = { onCambio(s); abierto = false })
            }
        }
    }
}

@Composable
private fun CampoMedida(etiqueta: String, valor: String, error: String?, modifier: Modifier, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = { v -> onCambio(v.filter { it.isDigit() || it == ',' || it == '.' }.take(7)) },
        label = { Text(etiqueta) },
        suffix = { Text("m") },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier.testTag(etiqueta),
    )
}

@Composable
private fun SelectorTipoTerreno(
    tipos: List<TipoTerrenoEntity>,
    elegido: Long?,
    error: String?,
    onElegir: (Long) -> Unit,
    onAgregar: (String) -> Unit,
    onAdministrar: () -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    var agregar by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
        OutlinedTextField(
            value = tipos.firstOrNull { it.id == elegido }?.nombre ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo de material / terreno") },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("tipoTerreno"),
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            tipos.forEach { t ->
                DropdownMenuItem(text = { Text(t.nombre) }, onClick = { onElegir(t.id); abierto = false })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("+ Agregar tipo…") }, onClick = { abierto = false; agregar = true })
            DropdownMenuItem(text = { Text("Administrar tipos") }, onClick = { abierto = false; onAdministrar() })
        }
    }
    if (agregar) DialogoNuevoTipo(onAgregar = onAgregar, onCerrar = { agregar = false })
}

@Composable
fun DialogoNuevoTipo(onAgregar: (String) -> Unit, onCerrar: () -> Unit) {
    var nombre by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Nuevo tipo de terreno") },
        text = {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre") },
                placeholder = { Text("Arcilla, Ripio, Escombro…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(enabled = nombre.isNotBlank(), onClick = { onAgregar(nombre.trim()); onCerrar() }) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun SeccionUbicacion(
    estado: EstadoFormulario,
    buscando: Boolean,
    error: String?,
    onObtener: () -> Unit,
    onQuitar: () -> Unit,
) {
    val lat = estado.latitud
    val lon = estado.longitud
    if (lat != null && lon != null) {
        val precision = estado.precision
        val baja = precision != null && precision > FormularioViewModel.PRECISION_ACEPTABLE_M
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(Formato.coordenadas(lat, lon), fontWeight = FontWeight.Medium)
                    Text(
                        precision?.let { "Precisión ±${it.toInt()} m" + if (baja) " · baja, conviene repetir" else "" } ?: "Precisión no informada",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (baja) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onQuitar) { Text("Quitar") }
            }
        }
    }
    error?.let { TextoError(it) }
    FilledTonalButton(onClick = onObtener, enabled = !buscando, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        if (buscando) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Buscando señal GPS…")
        } else {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (lat == null) "Obtener ubicación" else "Volver a obtener ubicación")
        }
    }
}

@Composable
private fun SeccionFotos(
    archivos: ArchivosFotos,
    fotos: List<FotoBorrador>,
    procesando: Boolean,
    onCamara: () -> Unit,
    onGaleria: () -> Unit,
    onQuitar: (FotoBorrador) -> Unit,
) {
    var ampliada by remember { mutableStateOf<FotoBorrador?>(null) }
    if (fotos.isNotEmpty() || procesando) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fotos.forEachIndexed { i, foto ->
                Box(Modifier.weight(1f).height(110.dp)) {
                    Surface(
                        onClick = { ampliada = foto },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxSize().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                    ) {
                        MiniaturaFoto(archivos, foto.archivo, lado = 320, modifier = Modifier.fillMaxSize(), descripcion = "Foto ${i + 1}")
                    }
                    if (foto.origen == OrigenFoto.GALERIA) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                        ) {
                            Text("Galería", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Surface(
                        onClick = { onQuitar(foto) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Quitar foto ${i + 1}", modifier = Modifier.padding(6.dp))
                    }
                }
            }
            if (procesando) {
                Box(Modifier.weight(1f).height(110.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            repeat(MAX_FOTOS - fotos.size - if (procesando) 1 else 0) { Spacer(Modifier.weight(1f)) }
        }
    }
    val lleno = fotos.size >= MAX_FOTOS
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(onClick = onCamara, enabled = !lleno && !procesando, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
            Icon(painterResource(R.drawable.ic_camara), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Cámara")
        }
        OutlinedButton(onClick = onGaleria, enabled = !lleno && !procesando, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
            Icon(painterResource(R.drawable.ic_galeria), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Galería")
        }
    }
    ampliada?.let { foto ->
        AlertDialog(
            onDismissRequest = { ampliada = null },
            confirmButton = { TextButton(onClick = { ampliada = null }) { Text("Cerrar") } },
            text = {
                Column {
                    MiniaturaFoto(archivos, foto.archivo, lado = 1080, modifier = Modifier.fillMaxWidth().height(320.dp).clip(MaterialTheme.shapes.medium))
                    foto.tomadaEn?.let {
                        Text(
                            "Tomada el ${Formato.fechaHora(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()))}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
        )
    }
}
