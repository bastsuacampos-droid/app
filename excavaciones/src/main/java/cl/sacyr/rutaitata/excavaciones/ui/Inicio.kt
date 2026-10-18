@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)

package cl.sacyr.rutaitata.excavaciones.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.sacyr.rutaitata.excavaciones.R
import cl.sacyr.rutaitata.excavaciones.data.RepositorioExcavaciones
import cl.sacyr.rutaitata.excavaciones.data.ResumenDia
import cl.sacyr.rutaitata.excavaciones.data.local.entidades.CierreDiaEntity
import cl.sacyr.rutaitata.excavaciones.dominio.ExcavacionDetalle
import cl.sacyr.rutaitata.excavaciones.dominio.Formato
import cl.sacyr.rutaitata.excavaciones.dominio.GrupoLado
import cl.sacyr.rutaitata.excavaciones.dominio.GrupoSector
import cl.sacyr.rutaitata.excavaciones.dominio.GrupoSentido
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class InicioViewModel(private val repo: RepositorioExcavaciones, private val estado: SavedStateHandle) : ViewModel() {

    /** Fecha activa del dashboard, como días desde 1970 (se conserva si Android cierra la app). */
    val fecha: StateFlow<Long> = estado.getStateFlow(CLAVE_FECHA, LocalDate.now().toEpochDay())

    val resumen: StateFlow<ResumenDia?> = fecha
        .flatMapLatest { repo.resumenDelDia(LocalDate.ofEpochDay(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Cierre del día activo (reporte definitivo emitido), o null si está abierto. */
    val cierre: StateFlow<CierreDiaEntity?> = fecha
        .flatMapLatest { repo.cierre(LocalDate.ofEpochDay(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun elegirFecha(nueva: LocalDate) {
        estado[CLAVE_FECHA] = nueva.toEpochDay()
    }

    fun reabrir(motivo: String) {
        viewModelScope.launch { repo.reabrirDia(LocalDate.ofEpochDay(fecha.value), motivo) }
    }

    private companion object {
        const val CLAVE_FECHA = "fecha"
    }
}

@Composable
fun PantallaInicio(
    mensajes: SavedStateHandle,
    onNueva: (LocalDate) -> Unit,
    onAbrir: (Long) -> Unit,
    onReporte: (LocalDate) -> Unit,
    onTiposTerreno: () -> Unit,
    onTiposExcavacion: () -> Unit,
    onRespaldo: () -> Unit,
    onSectores: () -> Unit,
) {
    val vm = vistaModelo { app, estado -> InicioViewModel(app.repositorio, estado) }
    val fecha = LocalDate.ofEpochDay(vm.fecha.collectAsStateWithLifecycle().value)
    val resumen by vm.resumen.collectAsStateWithLifecycle()
    val cierre by vm.cierre.collectAsStateWithLifecycle()
    var reabrir by rememberSaveable { mutableStateOf(false) }
    val avisos = remember { SnackbarHostState() }
    // Mensaje que deja el formulario al volver. Se borra antes de mostrarlo (para que no se repita al
    // girar la pantalla) sin cancelar el aviso: por eso se recolecta el flujo y no se usa como clave.
    LaunchedEffect(mensajes) {
        mensajes.getStateFlow<String?>(CLAVE_MENSAJE, null).filterNotNull().collect {
            mensajes[CLAVE_MENSAJE] = null
            avisos.showSnackbar(it)
        }
    }
    var menu by remember { mutableStateOf(false) }
    var verVersion by rememberSaveable { mutableStateOf(false) }
    val app = appExcavaciones()
    val scope = rememberCoroutineScope()
    // Al abrir o volver a la app (como máximo cada 30 min) revisa si hay una versión nueva publicada.
    // Se relee al volver (por ejemplo, desde la pantalla de respaldo).
    var ultimoRespaldo by remember { mutableStateOf(app.preferencias.ultimoRespaldo) }
    val totalExcavaciones by remember(app) { app.baseDatos.respaldo().observarCantidad() }.collectAsStateWithLifecycle(0)
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        ultimoRespaldo = app.preferencias.ultimoRespaldo
        if (app.buscaActualizaciones) scope.launch { app.actualizador.revisarSiCorresponde() }
    }
    var elegirFecha by rememberSaveable { mutableStateOf(false) }
    /** Tabla (sector-lado-sentido) cuyas excavaciones se están agrupando. */
    var agrupando by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Excavaciones", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Ruta Itata · control diario",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    if (letraGrande()) {
                        // Con letra grande, solo el ícono: el texto dejaba sin espacio al título.
                        IconButton(onClick = { onReporte(fecha) }) {
                            Icon(painterResource(R.drawable.ic_reporte), contentDescription = "Reporte PDF")
                        }
                    } else {
                        TextButton(onClick = { onReporte(fecha) }) {
                            Icon(painterResource(R.drawable.ic_reporte), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("Reporte PDF", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Sectores") }, onClick = { menu = false; onSectores() })
                            DropdownMenuItem(text = { Text("Tipos de excavación") }, onClick = { menu = false; onTiposExcavacion() })
                            DropdownMenuItem(text = { Text("Tipos de terreno") }, onClick = { menu = false; onTiposTerreno() })
                            DropdownMenuItem(text = { Text("Respaldo de datos") }, onClick = { menu = false; onRespaldo() })
                            DropdownMenuItem(text = { Text("Versión de la app") }, onClick = { menu = false; verVersion = true })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // En un día cerrado no se agregan excavaciones (el aviso explica cómo reabrirlo).
            if (cierre == null) ExtendedFloatingActionButton(
                onClick = { onNueva(fecha) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva excavación") },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.testTag("nueva"),
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { relleno ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = relleno.calculateTopPadding(),
                bottom = relleno.calculateBottomPadding() + 96.dp, // espacio para el botón flotante
            ),
        ) {
            item {
                Box(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp)) { AvisoVersionApp(app.actualizador) }
            }
            val dias = diasSinRespaldo(ultimoRespaldo)
            if (totalExcavaciones > 0 && (dias == null || dias >= DIAS_AVISO_RESPALDO)) {
                item {
                    AvisoRespaldo(dias, onRespaldo)
                }
            }
            item { SelectorDia(fecha, onCambiar = vm::elegirFecha, onElegir = { elegirFecha = true }) }
            cierre?.let { c -> item { AvisoDiaCerrado(c, onReabrir = { reabrir = true }) } }
            item { Indicadores(resumen) }
            val r = resumen
            if (r != null && r.cantidad == 0) {
                item {
                    EstadoVacio(
                        titulo = "Sin excavaciones el ${Formato.fecha(fecha)}",
                        detalle = "Registra cada excavación al terminarla: medidas, ubicación y una foto de cómo quedó.",
                    )
                }
            }
            r?.sectores?.forEach { sector ->
                item(key = "s${sector.sectorId}") { EncabezadoSector(sector) }
                sector.lados.forEach { lado ->
                    item(key = "s${sector.sectorId}-${lado.lado.codigo}") { EncabezadoLado(lado) }
                    lado.sentidos.forEach { sentido ->
                        val clave = "s${sector.sectorId}-${lado.lado.codigo}-${sentido.sentido.codigo}"
                        // Agrupar adyacentes: solo con dos o más en la tabla y con el día abierto.
                        val puedeAgrupar = cierre == null && sentido.excavaciones.size >= 2
                        item(key = clave) { EncabezadoSentido(sentido, onAgrupar = if (puedeAgrupar) ({ agrupando = clave }) else null) }
                        val companeras = companerasDeGrupo(sentido.excavaciones)
                        items(sentido.excavaciones, key = { it.id }) {
                            FilaExcavacion(it, agrupadaCon = companeras[it.id], onClick = { onAbrir(it.id) })
                        }
                    }
                }
            }
        }
    }
    if (elegirFecha) DialogoFecha(fecha, onElegir = vm::elegirFecha, onCerrar = { elegirFecha = false })
    agrupando?.let { clave ->
        val sentido = resumen?.sectores?.flatMap { s -> s.lados.flatMap { l -> l.sentidos.map { "s${s.sectorId}-${l.lado.codigo}-${it.sentido.codigo}" to it } } }
            ?.firstOrNull { it.first == clave }?.second
        if (sentido == null) {
            agrupando = null
        } else {
            DialogoAgrupar(
                sentido,
                onAgrupar = { ids ->
                    agrupando = null
                    scope.launch {
                        runCatching { app.repositorio.agrupar(ids) }
                            .onSuccess {
                                val numeros = sentido.excavaciones.filter { it.id in ids }.joinToString(" + ") { Formato.numero(it.numero) }
                                avisos.showSnackbar("N° $numeros saldrán en una sola fila del reporte")
                            }
                            .onFailure { avisos.showSnackbar(it.message ?: "No se pudieron agrupar") }
                    }
                },
                onSeparar = { ids ->
                    agrupando = null
                    scope.launch {
                        runCatching { app.repositorio.separar(ids) }
                            .onSuccess { avisos.showSnackbar("Separadas: cada una sale en su fila") }
                            .onFailure { avisos.showSnackbar(it.message ?: "No se pudieron separar") }
                    }
                },
                onCerrar = { agrupando = null },
            )
        }
    }
    if (reabrir) {
        DialogoReabrir(fecha, onReabrir = { motivo -> vm.reabrir(motivo); reabrir = false }, onCerrar = { reabrir = false })
    }
    if (verVersion) {
        AlertDialog(
            onDismissRequest = { verVersion = false },
            title = { Text("Versión ${app.actualizador.versionActual}" + if (app.buscaActualizaciones) "" else " (de prueba)") },
            text = {
                Column {
                    if (app.buscaActualizaciones) {
                        ControlesActualizacion(app.actualizador)
                    } else {
                        Text(
                            "Esta es una versión de prueba: no se actualiza sola. Para recibir actualizaciones, " +
                                "instala la versión publicada (excavaciones-ruta-itata-….apk de GitHub Releases).",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { verVersion = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun AvisoDiaCerrado(c: CierreDiaEntity, onReabrir: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Día cerrado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Reporte definitivo emitido el ${Formato.fechaHora(LocalDateTime.ofInstant(Instant.ofEpochMilli(c.cerradoEn), ZoneId.systemDefault()))}" +
                        " por ${c.responsable}. Sus excavaciones no se pueden modificar.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onReabrir) { Text("Reabrir") }
        }
    }
}

/** Reabrir exige un motivo: queda registrado y sale en los reportes de ese día. */
@Composable
private fun DialogoReabrir(fecha: LocalDate, onReabrir: (String) -> Unit, onCerrar: () -> Unit) {
    var motivo by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Reabrir el ${Formato.fecha(fecha)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "El reporte de ese día ya se emitió. La reapertura y su motivo quedarán anotados en los próximos reportes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it },
                    label = { Text("Motivo") },
                    placeholder = { Text("Corrección de la medida de la N° 03") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag("motivo"),
                )
            }
        },
        confirmButton = { TextButton(enabled = motivo.isNotBlank(), onClick = { onReabrir(motivo.trim()) }) { Text("Reabrir") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

/** Recordatorio de respaldo: los registros viven solo en el teléfono. */
@Composable
private fun AvisoRespaldo(dias: Long?, onRespaldo: () -> Unit) {
    Card(
        onClick = onRespaldo,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (dias == null) "Aún no hay respaldo" else "Sin respaldo hace $dias días",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Los registros viven solo en este teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text("Respaldar", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun SelectorDia(fecha: LocalDate, onCambiar: (LocalDate) -> Unit, onElegir: () -> Unit) {
    val hoy = LocalDate.now()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onCambiar(fecha.minusDays(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Día anterior")
        }
        Column(
            Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable(onClick = onElegir).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(diaLargo(fecha), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (fecha == hoy) "Hoy · ${Formato.fecha(fecha)}" else "${Formato.fecha(fecha)} · toca para cambiar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onCambiar(fecha.plusDays(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Día siguiente")
        }
    }
    if (fecha != hoy) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            AssistChip(onClick = { onCambiar(hoy) }, label = { Text("Volver a hoy") })
        }
    }
}

@Composable
private fun Indicadores(resumen: ResumenDia?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Indicador("Excavaciones del día", resumen?.cantidad?.toString() ?: "–", Modifier.weight(1f))
        Indicador("Volumen acumulado", resumen?.let { Formato.volumen(it.volumen) } ?: "–", Modifier.weight(1f))
    }
}

@Composable
private fun Indicador(etiqueta: String, valor: String, modifier: Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(valor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private fun cantidad(n: Int) = if (n == 1) "1 excavación" else "$n excavaciones"

@Composable
private fun EncabezadoSector(sector: GrupoSector) {
    Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        val nombre = @Composable { m: Modifier ->
            Text(
                sector.nombre,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = m,
            )
        }
        val totales = @Composable {
            Text(
                "${cantidad(sector.cantidad)} · ${Formato.volumen(sector.volumen)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        // Con letra grande, los totales bajo el nombre: al lado lo cortaban ("Km 12+40…").
        if (letraGrande()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                nombre(Modifier)
                totales()
            }
        } else {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                nombre(Modifier.weight(1f))
                totales()
            }
        }
    }
}

@Composable
private fun EncabezadoLado(lado: GrupoLado) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(lado.lado.etiqueta, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(
            "${cantidad(lado.cantidad)} · ${Formato.volumen(lado.volumen)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun EncabezadoSentido(sentido: GrupoSentido, onAgrupar: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 2.dp)) {
        Text(
            "Sentido ${sentido.sentido.etiqueta}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
        )
        if (onAgrupar != null) {
            TextButton(onClick = onAgrupar, modifier = Modifier.testTag("agrupar")) { Text("Agrupar adyacentes") }
        }
    }
}

/** Para cada excavación agrupada, los números de las otras de su grupo ("04", "04 + 05"). */
private fun companerasDeGrupo(excavaciones: List<ExcavacionDetalle>): Map<Long, String> {
    val grupos = excavaciones.filter { it.grupo != null }.groupBy { it.grupo }
    return excavaciones.filter { it.grupo != null }.associate { e ->
        e.id to grupos.getValue(e.grupo).filter { it.id != e.id }.joinToString(" + ") { Formato.numero(it.numero) }
    }
}

/**
 * Elegir excavaciones adyacentes de una misma tabla para que el reporte las muestre en una sola
 * fila, o separar las que ya estaban agrupadas.
 */
@Composable
private fun DialogoAgrupar(
    sentido: GrupoSentido,
    onAgrupar: (List<Long>) -> Unit,
    onSeparar: (List<Long>) -> Unit,
    onCerrar: () -> Unit,
) {
    var elegidas by rememberSaveable { mutableStateOf(listOf<Long>()) }
    val companeras = companerasDeGrupo(sentido.excavaciones)
    val hayAgrupadas = sentido.excavaciones.any { it.id in elegidas && it.grupo != null }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Agrupar adyacentes") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Las elegidas salen en el reporte en una sola fila (\"03 + 04\"), con el volumen sumado y sus fotos " +
                        "juntas. En el registro siguen separadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                sentido.excavaciones.forEach { e ->
                    val marcada = e.id in elegidas
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                            value = marcada,
                            role = Role.Checkbox,
                            onValueChange = { elegidas = if (it) elegidas + e.id else elegidas - e.id },
                        ),
                    ) {
                        Checkbox(checked = marcada, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "N° ${Formato.numero(e.numero)} · ${Formato.dimensiones(e.largo, e.ancho, e.profundidad)} m",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            companeras[e.id]?.let {
                                Text("Agrupada con N° $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAgrupar(elegidas) }, enabled = elegidas.size >= 2) { Text("Agrupar") }
        },
        dismissButton = {
            Row {
                if (hayAgrupadas) TextButton(onClick = { onSeparar(elegidas) }) { Text("Separar") }
                TextButton(onClick = onCerrar) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun FilaExcavacion(e: ExcavacionDetalle, agrupadaCon: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        Formato.numero(e.numero),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // Con letra grande el volumen va bajo las medidas, para que no las parta en dos líneas.
            val grande = letraGrande()
            Column(Modifier.weight(1f)) {
                Text(
                    "${Formato.dimensiones(e.largo, e.ancho, e.profundidad)} m",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (grande) {
                    Text(Formato.volumen(e.volumen), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    listOfNotNull(e.tipoTerreno, e.progresiva).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (e.fotos > 0) {
                        Icon(painterResource(R.drawable.ic_camara), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(" ${e.fotos}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    } else {
                        Text("Sin foto", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    if (e.latitud != null) {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Default.LocationOn, contentDescription = "Con ubicación GPS", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                agrupadaCon?.let {
                    Text(
                        "Agrupada con N° $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (!grande) Text(Formato.volumen(e.volumen), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
