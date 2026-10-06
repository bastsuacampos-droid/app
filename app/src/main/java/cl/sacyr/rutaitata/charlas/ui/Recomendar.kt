package cl.sacyr.rutaitata.charlas.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.sacyr.rutaitata.charlas.data.Banco
import cl.sacyr.rutaitata.charlas.data.Calendario
import cl.sacyr.rutaitata.charlas.data.Charla
import cl.sacyr.rutaitata.charlas.data.Planificador
import cl.sacyr.rutaitata.charlas.data.Progreso
import cl.sacyr.rutaitata.charlas.data.Recomendacion
import cl.sacyr.rutaitata.charlas.data.Recomendador
import cl.sacyr.rutaitata.charlas.data.nombreDia
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

/** Cuántas opciones se muestran además de la recomendada. */
private const val OTRAS_OPCIONES = 8

/**
 * El capataz cuenta qué actividad hará (eligiendo una de la lista o escribiéndola) y la app
 * recomienda charlas acordes. Si la semana tiene plan, la elegida se puede dejar en ese día.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PantallaRecomendar(
    banco: Banco,
    progreso: Progreso,
    fecha: LocalDate,
    onAbrir: (Charla) -> Unit,
    onVolver: () -> Unit,
) {
    val context = LocalContext.current
    val lista = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val plan = progreso.plan(Calendario.lunesDe(fecha))
    val dia = fecha.dayOfWeek.value.takeIf { fecha.dayOfWeek != DayOfWeek.SUNDAY }
    var texto by rememberSaveable(fecha) { mutableStateOf(plan?.actividades?.get(dia).orEmpty()) }
    var actividadId by rememberSaveable(fecha) { mutableStateOf<String?>(null) }
    var elegidaId by rememberSaveable(fecha) { mutableStateOf<Int?>(null) }

    val actividad = banco.actividades.firstOrNull { it.id == actividadId }
    val ocupadas = if (plan != null && dia != null) {
        Planificador.ocupadas(plan, dia, progreso.usos, progreso.planes)
    } else {
        Recomendador.dictadasEn(YearMonth.from(fecha), progreso.usos)
    }
    val resultados = Recomendador.recomendar(banco, texto, actividad, ocupadas)
    val elegida = resultados.firstOrNull { it.charla.id == elegidaId } ?: resultados.firstOrNull { !it.ocupada }
    // Para "otra al azar": las libres que mejor calzan con la actividad.
    val candidatas = resultados.filter { !it.ocupada }.let { libres ->
        val mejor = libres.firstOrNull()?.puntaje ?: 0
        libres.filter { it.puntaje * 2 >= mejor }.take(OTRAS_OPCIONES + 4)
    }
    val descripcion = texto.trim().ifBlank { actividad?.nombre.orEmpty() }

    fun usar(charla: Charla) {
        if (plan == null || dia == null) return
        progreso.guardarPlan(
            Planificador.anotarActividad(Planificador.asignar(plan, dia, charla.id), dia, descripcion),
        )
        Toast.makeText(context, "${charla.codigo} quedó en el plan del ${nombreDia(dia).lowercase()}", Toast.LENGTH_SHORT).show()
        onVolver()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("¿Qué actividad harás?")
                        Text(
                            (if (fecha == LocalDate.now()) "Hoy, " else "") +
                                "${nombreDiaCompleto(fecha)} ${fecha.format(FORMATO_DIA_MES)}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = coloresBarra(),
            )
        },
    ) { padding ->
        LazyColumn(
            state = lista,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "buscar") {
                OutlinedTextField(
                    value = texto,
                    onValueChange = {
                        texto = it
                        elegidaId = null
                    },
                    label = { Text("Escribe la actividad") },
                    placeholder = { Text("Ej.: hormigonado del cabezal") },
                    singleLine = true,
                    trailingIcon = {
                        if (texto.isNotEmpty()) {
                            IconButton(onClick = { texto = ""; elegidaId = null }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Borrar")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val hayConsulta = texto.isNotBlank() || actividad != null
            if (hayConsulta && elegida == null) {
                item(key = "sin-resultados") {
                    Text(
                        if (resultados.isEmpty()) {
                            "No encontré charlas para esa actividad. Prueba con otras palabras o elige una de la lista."
                        } else {
                            "Todas las charlas que calzan ya se usaron o están planificadas este mes."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else if (elegida != null) {
                item(key = "elegida") {
                    TarjetaRecomendada(
                        banco = banco,
                        recomendacion = elegida,
                        puedeUsar = plan != null && dia != null,
                        dia = dia,
                        hayOtras = candidatas.any { it.charla.id != elegida.charla.id },
                        onOtra = {
                            elegidaId = candidatas.filter { it.charla.id != elegida.charla.id }.randomOrNull()?.charla?.id
                        },
                        onUsar = { usar(elegida.charla) },
                        onAbrir = { onAbrir(elegida.charla) },
                    )
                }
                if (plan == null) {
                    item(key = "sin-plan") {
                        Text(
                            "Esta semana no tiene plan: abre la charla y regístrala al dictarla.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            val otras = resultados.filter { it.charla.id != elegida?.charla?.id }.take(OTRAS_OPCIONES)
            if (otras.isNotEmpty()) {
                item(key = "otras-titulo") {
                    Text(
                        "Otras charlas que calzan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(otras, key = { "otra-${it.charla.id}" }) { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            FilaCharla(
                                banco = banco,
                                charla = r.charla,
                                detalle = banco.especialidad(r.charla.especialidadId).nombre +
                                    if (r.ocupada) " · ya usada o planificada este mes" else "",
                                usada = r.ocupada,
                                onClick = {
                                    elegidaId = r.charla.id
                                    scope.launch { lista.animateScrollToItem(0) }
                                },
                            )
                        }
                        if (plan != null && dia != null) {
                            IconButton(onClick = { usar(r.charla) }) {
                                Icon(Icons.Filled.Add, contentDescription = "Usar ${r.charla.codigo} en el plan")
                            }
                        }
                    }
                }
            }
            item(key = "actividades") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        if (hayConsulta) "O elige otra actividad:" else "O elige una actividad:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        banco.actividades.forEach { a ->
                            FilterChip(
                                selected = a.id == actividadId,
                                onClick = {
                                    actividadId = if (a.id == actividadId) null else a.id
                                    elegidaId = null
                                    // La recomendación aparece arriba: volver al inicio de la lista.
                                    scope.launch { lista.animateScrollToItem(0) }
                                },
                                label = { Text(a.nombre) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaRecomendada(
    banco: Banco,
    recomendacion: Recomendacion,
    puedeUsar: Boolean,
    dia: Int?,
    hayOtras: Boolean,
    onOtra: () -> Unit,
    onUsar: () -> Unit,
    onAbrir: () -> Unit,
) {
    val charla = recomendacion.charla
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("CHARLA RECOMENDADA", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FilaCharla(
                banco = banco,
                charla = charla,
                detalle = banco.especialidad(charla.especialidadId).nombre +
                    if (recomendacion.ocupada) " · ya usada o planificada este mes" else "",
                usada = recomendacion.ocupada,
                onClick = onAbrir,
            )
            Spacer(Modifier.height(10.dp))
            if (puedeUsar && dia != null) {
                Button(onClick = onUsar, modifier = Modifier.fillMaxWidth()) {
                    Text("Usar en el plan del ${nombreDia(dia).lowercase()}")
                }
            } else {
                Button(onClick = onAbrir, modifier = Modifier.fillMaxWidth()) {
                    Text("Ver charla")
                }
            }
            if (hayOtras) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onOtra, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Otra al azar para esta actividad")
                }
            }
        }
    }
}

private fun nombreDiaCompleto(fecha: LocalDate): String =
    if (fecha.dayOfWeek == DayOfWeek.SUNDAY) "domingo" else nombreDia(fecha.dayOfWeek.value).lowercase()
