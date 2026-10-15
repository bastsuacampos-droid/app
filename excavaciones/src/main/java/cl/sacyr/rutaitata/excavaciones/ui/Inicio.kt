@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)

package cl.sacyr.rutaitata.excavaciones.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.sacyr.rutaitata.excavaciones.R
import cl.sacyr.rutaitata.excavaciones.data.RepositorioExcavaciones
import cl.sacyr.rutaitata.excavaciones.data.ResumenDia
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
import java.time.LocalDate

class InicioViewModel(repo: RepositorioExcavaciones, private val estado: SavedStateHandle) : ViewModel() {

    /** Fecha activa del dashboard, como días desde 1970 (se conserva si Android cierra la app). */
    val fecha: StateFlow<Long> = estado.getStateFlow(CLAVE_FECHA, LocalDate.now().toEpochDay())

    val resumen: StateFlow<ResumenDia?> = fecha
        .flatMapLatest { repo.resumenDelDia(LocalDate.ofEpochDay(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun elegirFecha(nueva: LocalDate) {
        estado[CLAVE_FECHA] = nueva.toEpochDay()
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
) {
    val vm = vistaModelo { app, estado -> InicioViewModel(app.repositorio, estado) }
    val fecha = LocalDate.ofEpochDay(vm.fecha.collectAsStateWithLifecycle().value)
    val resumen by vm.resumen.collectAsStateWithLifecycle()
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
    var elegirFecha by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Excavaciones", fontWeight = FontWeight.Bold)
                        Text("Ruta Itata · control diario", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    TextButton(onClick = { onReporte(fecha) }) {
                        Icon(painterResource(R.drawable.ic_reporte), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("Reporte PDF", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Tipos de excavación") }, onClick = { menu = false; onTiposExcavacion() })
                            DropdownMenuItem(text = { Text("Tipos de terreno") }, onClick = { menu = false; onTiposTerreno() })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
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
            item { SelectorDia(fecha, onCambiar = vm::elegirFecha, onElegir = { elegirFecha = true }) }
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
                        item(key = "s${sector.sectorId}-${lado.lado.codigo}-${sentido.sentido.codigo}") { EncabezadoSentido(sentido) }
                        items(sentido.excavaciones, key = { it.id }) { FilaExcavacion(it, onClick = { onAbrir(it.id) }) }
                    }
                }
            }
        }
    }
    if (elegirFecha) DialogoFecha(fecha, onElegir = vm::elegirFecha, onCerrar = { elegirFecha = false })
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
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                sector.nombre,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${cantidad(sector.cantidad)} · ${Formato.volumen(sector.volumen)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
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
private fun EncabezadoSentido(sentido: GrupoSentido) {
    Text(
        "Sentido ${sentido.sentido.etiqueta}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun FilaExcavacion(e: ExcavacionDetalle, onClick: () -> Unit) {
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
            Column(Modifier.weight(1f)) {
                Text(
                    "${Formato.dimensiones(e.largo, e.ancho, e.profundidad)} m",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
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
            }
            Text(Formato.volumen(e.volumen), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
