@file:OptIn(ExperimentalMaterial3Api::class)

package cl.sacyr.rutaitata.excavaciones.ui

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.sacyr.rutaitata.excavaciones.R
import cl.sacyr.rutaitata.excavaciones.dominio.Formato
import cl.sacyr.rutaitata.excavaciones.reporte.CompartirReporte
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun PantallaReporte(onVolver: () -> Unit, onVisualizar: (File) -> Unit) {
    val vm = vistaModelo { app, estado -> ReporteViewModel(app, estado) }
    val desde = LocalDate.ofEpochDay(vm.desde.collectAsStateWithLifecycle().value)
    val hasta = LocalDate.ofEpochDay(vm.hasta.collectAsStateWithLifecycle().value)
    val sectorId by vm.sectorId.collectAsStateWithLifecycle()
    val sectores by vm.sectores.collectAsStateWithLifecycle()
    val responsable by vm.responsable.collectAsStateWithLifecycle()
    val incluirFotos by vm.incluirFotos.collectAsStateWithLifecycle()
    val previa by vm.previa.collectAsStateWithLifecycle()
    val generacion by vm.generacion.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val avisos = remember { SnackbarHostState() }
    var fechaAElegir by rememberSaveable { mutableStateOf<String?>(null) } // "desde" | "hasta"
    val listo = generacion as? EstadoGeneracion.Listo
    val guardarCopia = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CompartirReporte.TIPO_PDF)) { uri ->
        if (uri != null && listo != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { CompartirReporte.copiar(context, listo.archivo, uri) }.isSuccess }
                avisos.showSnackbar(if (ok) "Copia guardada" else "No se pudo guardar la copia")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reporte PDF", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Titulo("Período")
            val hoy = LocalDate.now()
            val lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = desde == hoy && hasta == hoy, onClick = { vm.periodo(hoy, hoy) }, label = { Text("Hoy") })
                val ayer = hoy.minusDays(1)
                FilterChip(selected = desde == ayer && hasta == ayer, onClick = { vm.periodo(ayer, ayer) }, label = { Text("Ayer") })
                FilterChip(selected = desde == lunes && hasta == hoy, onClick = { vm.periodo(lunes, hoy) }, label = { Text("Esta semana") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BotonFecha("Desde", desde, Modifier.weight(1f)) { fechaAElegir = "desde" }
                BotonFecha("Hasta", hasta, Modifier.weight(1f)) { fechaAElegir = "hasta" }
            }

            Titulo("Sector")
            SelectorSector(
                opciones = listOf<Pair<Long?, String>>(null to "Todos los sectores") + sectores.map { it.id to it.nombre },
                elegido = sectorId.takeIf { it > 0 },
                onElegir = vm::sector,
            )

            Titulo("Datos del reporte")
            OutlinedTextField(
                value = responsable,
                onValueChange = vm::responsable,
                label = { Text("Responsable / inspector de obra") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Incluir registro fotográfico", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Anexo con las fotos de cada excavación. Desactívalo para un PDF liviano.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = incluirFotos, onCheckedChange = vm::incluirFotos)
            }

            previa?.let { p ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${p.excavaciones} ${if (p.excavaciones == 1) "excavación" else "excavaciones"} · ${Formato.volumen(p.volumen)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${p.fotos} ${if (p.fotos == 1) "foto" else "fotos"}", style = MaterialTheme.typography.bodyMedium)
                        if (p.sinFoto > 0) {
                            Text(
                                if (p.sinFoto == 1) {
                                    "1 sin foto: saldrá marcada \"Sin foto\" en el reporte"
                                } else {
                                    "${p.sinFoto} sin foto: saldrán marcadas \"Sin foto\" en el reporte"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = vm::generar,
                enabled = generacion != EstadoGeneracion.Generando,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) {
                if (generacion == EstadoGeneracion.Generando) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                    Spacer(Modifier.width(10.dp))
                    Text("Generando…")
                } else {
                    Icon(painterResource(R.drawable.ic_reporte), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Generar reporte PDF", fontWeight = FontWeight.Bold)
                }
            }

            when (val g = generacion) {
                is EstadoGeneracion.Fallo -> Text("No se pudo generar el reporte: ${g.mensaje}", color = MaterialTheme.colorScheme.error)
                is EstadoGeneracion.Listo -> Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Reporte listo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${g.archivo.name}\n${g.paginas} ${if (g.paginas == 1) "página" else "páginas"} · ${Formatter.formatShortFileSize(context, g.archivo.length())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { onVisualizar(g.archivo) }, modifier = Modifier.weight(1f)) { Text("Visualizar") }
                            FilledTonalButton(
                                onClick = { context.startActivity(CompartirReporte.intentCompartir(context, g.archivo)) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Compartir")
                            }
                        }
                        OutlinedButton(onClick = { guardarCopia.launch(g.archivo.name) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Guardar copia en el teléfono…")
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    fechaAElegir?.let { cual ->
        DialogoFecha(
            inicial = if (cual == "desde") desde else hasta,
            onElegir = { f -> if (cual == "desde") vm.periodo(f, maxOf(f, hasta)) else vm.periodo(minOf(desde, f), f) },
            onCerrar = { fechaAElegir = null },
        )
    }
}

@Composable
private fun BotonFecha(etiqueta: String, fecha: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 52.dp)) {
        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(etiqueta, style = MaterialTheme.typography.labelSmall)
            Text(Formato.fecha(fecha))
        }
    }
}

@Composable
private fun SelectorSector(opciones: List<Pair<Long?, String>>, elegido: Long?, onElegir: (Long?) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
        OutlinedTextField(
            value = opciones.firstOrNull { it.first == elegido }?.second ?: opciones.first().second,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (id, nombre) ->
                DropdownMenuItem(text = { Text(nombre) }, onClick = { onElegir(id); abierto = false })
            }
        }
    }
}

/**
 * Vista previa del PDF dentro de la app (PdfRenderer), para revisarlo en terreno aunque el
 * teléfono no tenga visor de PDF. Desde aquí se comparte o se abre en otra app.
 */
@Composable
fun PantallaVisorPdf(archivo: File, onVolver: () -> Unit) {
    val context = LocalContext.current
    val avisos = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // PdfRenderer abre una página a la vez y no es seguro entre hilos: un Mutex lo protege.
    val documento = remember(archivo) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(archivo, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
    }
    val candado = remember { Mutex() }
    // Al salir, el alcance de la pantalla ya está cancelado: el cierre va en el de la app, y espera
    // a que termine la página que se esté dibujando.
    val segundoPlano = appExcavaciones().segundoPlano
    DisposableEffect(documento) {
        onDispose { segundoPlano.launch { candado.withLock { documento?.close() } } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(archivo.nameWithoutExtension.replace('_', ' '), maxLines = 1) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
                actions = {
                    IconButton(onClick = { context.startActivity(CompartirReporte.intentCompartir(context, archivo)) }) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir")
                    }
                    TextButton(onClick = {
                        try {
                            context.startActivity(CompartirReporte.intentVisualizar(context, archivo))
                        } catch (e: ActivityNotFoundException) {
                            scope.launch { avisos.showSnackbar("No hay otra app para abrir PDF en este teléfono") }
                        }
                    }) { Text("Abrir con…") }
                },
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { relleno ->
        if (documento == null) {
            EstadoVacio("No se pudo abrir el reporte", "Vuelve a generarlo desde la pantalla anterior.")
            return@Scaffold
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(relleno).background(MaterialTheme.colorScheme.surfaceVariant)) {
            val anchoPx = with(LocalDensity.current) { (maxWidth - 16.dp).roundToPx() }.coerceIn(200, 2000)
            LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(documento.pageCount) { i ->
                    val pagina by produceState<Bitmap?>(null, i, anchoPx) {
                        value = withContext(Dispatchers.IO) {
                            candado.withLock {
                                documento.openPage(i).use { p ->
                                    val alto = (anchoPx.toFloat() * p.height / p.width).toInt()
                                    createBitmap(anchoPx, alto).also { bmp ->
                                        bmp.eraseColor(Color.WHITE)
                                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(842f / 595f).background(androidx.compose.ui.graphics.Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        pagina?.let { Image(it.asImageBitmap(), contentDescription = "Página ${i + 1}", modifier = Modifier.fillMaxSize()) }
                            ?: CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
