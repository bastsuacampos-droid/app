package cl.sacyr.rutaitata.charlas.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import cl.sacyr.rutaitata.charlas.data.NOMBRES_DIA
import cl.sacyr.rutaitata.charlas.data.PlanSemanal
import cl.sacyr.rutaitata.charlas.data.Planificador
import cl.sacyr.rutaitata.charlas.data.Progreso
import cl.sacyr.rutaitata.charlas.data.Uso
import cl.sacyr.rutaitata.charlas.data.Usos
import cl.sacyr.rutaitata.charlas.data.nombreDia
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/** Cuántas semanas hacia adelante se pueden planificar. */
private const val SEMANAS_FUTURAS = 4L

internal fun LazyListScope.listaSemana(
    banco: Banco,
    progreso: Progreso,
    lunes: LocalDate,
    onLunes: (LocalDate) -> Unit,
    onAbrir: (Charla) -> Unit,
    onRecomendar: (LocalDate) -> Unit,
) {
    val hoy = LocalDate.now()
    val vigente = Calendario.semanaVigente(hoy)
    if (lunes == vigente) {
        // El domingo se prepara la charla del lunes.
        val dia = if (hoy.dayOfWeek == DayOfWeek.SUNDAY) hoy.plusDays(1) else hoy
        item(key = "actividad-hoy") { TarjetaActividad(dia, onRecomendar) }
    }
    item(key = "selector-semana") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { onLunes(lunes.minusWeeks(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Semana anterior")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Semana del ${lunes.format(FORMATO_DIA_MES)} al ${lunes.plusDays(5).format(FORMATO_DIA_MES)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when {
                        lunes == vigente -> "Esta semana"
                        lunes == vigente.plusWeeks(1) -> "Próxima semana"
                        lunes < vigente -> "Semana pasada"
                        else -> "En ${java.time.temporal.ChronoUnit.WEEKS.between(vigente, lunes)} semanas"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            IconButton(
                onClick = { onLunes(lunes.plusWeeks(1)) },
                enabled = lunes < vigente.plusWeeks(SEMANAS_FUTURAS),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Semana siguiente")
            }
        }
    }

    val plan = progreso.plan(lunes)
    if (plan == null) {
        item(key = "armar-$lunes") { ArmarSemana(banco, progreso, lunes) }
        return
    }

    items(plan.dias, key = { "dia-$lunes-$it" }) { dia ->
        DiaDelPlan(banco, progreso, plan, dia, onAbrir, onRecomendar)
    }
    item(key = "acciones-$lunes") { AccionesPlan(banco, progreso, plan) }
}

/** Acceso a la recomendación de charla según la actividad del día. */
@Composable
private fun TarjetaActividad(fecha: LocalDate, onRecomendar: (LocalDate) -> Unit) {
    val esHoy = fecha == LocalDate.now()
    OutlinedCard(onClick = { onRecomendar(fecha) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (esHoy) "¿Qué actividad harás hoy?" else "¿Qué actividad harás el ${nombreDia(fecha.dayOfWeek.value).lowercase()}?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Cuéntale a la app y te recomienda una charla acorde.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Formulario para elegir especialidades y días, y generar el plan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArmarSemana(banco: Banco, progreso: Progreso, lunes: LocalDate) {
    val validas = banco.especialidades.map { it.id }.toSet()
    var elegidas by rememberSaveable(lunes) {
        mutableStateOf(progreso.ultimasEspecialidades.filter { it in validas })
    }
    var dias by rememberSaveable(lunes) { mutableStateOf((1..NOMBRES_DIA.size).toList()) }
    val mes = YearMonth.from(lunes)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ARMAR EL PLAN DE LA SEMANA", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                "1. ¿Qué especialidades vas a trabajar esta semana? Se reparten en el orden en que las marques.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                banco.especialidades.forEach { esp ->
                    val posicion = elegidas.indexOf(esp.id)
                    val libres = Usos.disponibles(banco.charlasDe(esp.id), progreso.usos, mes).size
                    FilterChip(
                        selected = posicion >= 0,
                        onClick = { elegidas = if (posicion >= 0) elegidas - esp.id else elegidas + esp.id },
                        label = {
                            Text((if (posicion >= 0) "${posicion + 1}. " else "") + "${esp.nombre} · $libres disp.")
                        },
                        leadingIcon = { Punto(colorDe(esp.colorHex)) },
                    )
                }
            }
            Text("2. ¿Qué días hay charla?", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..NOMBRES_DIA.size).forEach { dia ->
                    FilterChip(
                        selected = dia in dias,
                        onClick = { dias = if (dia in dias) dias - dia else (dias + dia).sorted() },
                        label = { Text("${nombreDia(dia).take(3)} ${lunes.plusDays(dia - 1L).format(FORMATO_DIA_MES)}") },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    progreso.guardarPlan(
                        Planificador.generar(banco, lunes, elegidas, dias, progreso.usos, progreso.planes),
                    )
                },
                enabled = elegidas.isNotEmpty() && dias.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Generar plan de la semana")
            }
            Text(
                "La app elige charlas que no hayas dictado ni planificado este mes. Después puedes cambiar cualquier día.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DiaDelPlan(
    banco: Banco,
    progreso: Progreso,
    plan: PlanSemanal,
    dia: Int,
    onAbrir: (Charla) -> Unit,
    onRecomendar: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val fecha = plan.fecha(dia)
    val charla = plan.charlas[dia]?.let(banco::charla)
    val esHoy = fecha == LocalDate.now()
    var menu by remember { mutableStateOf(false) }

    fun cambiarA(especialidad: String) {
        menu = false
        val nuevo = Planificador.cambiar(banco, plan, dia, especialidad, progreso.usos, progreso.planes, Random.Default)
        if (nuevo != null) {
            progreso.guardarPlan(nuevo)
        } else {
            Toast.makeText(
                context,
                "No quedan charlas disponibles de ${banco.especialidad(especialidad).nombre} este mes",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Column {
        Text(
            "${nombreDia(dia)} ${fecha.format(FORMATO_DIA_MES)}" + if (esHoy) " · HOY" else "",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (esHoy) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
        plan.actividades[dia]?.let {
            Text("Actividad: $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (charla != null) {
                    val dictada = Uso(charla.id, fecha) in progreso.usos
                    FilaCharla(
                        banco = banco,
                        charla = charla,
                        detalle = banco.especialidad(charla.especialidadId).nombre +
                            if (dictada) " · dictada" else "",
                        usada = dictada,
                        resaltar = esHoy,
                        onClick = { onAbrir(charla) },
                    )
                } else {
                    Text(
                        "Sin charla disponible. Elige otra especialidad en el menú.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Cambiar charla del ${nombreDia(dia)}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Elegir según la actividad del día…", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        onClick = {
                            menu = false
                            onRecomendar(fecha)
                        },
                    )
                    if (charla != null) {
                        DropdownMenuItem(
                            text = { Text("Otra al azar de ${banco.especialidad(charla.especialidadId).nombre}") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = { cambiarA(charla.especialidadId) },
                        )
                    }
                    HorizontalDivider()
                    banco.especialidades
                        .filter { it.id != charla?.especialidadId }
                        .forEach { esp ->
                            DropdownMenuItem(
                                text = { Text("Al azar de ${esp.nombre}") },
                                leadingIcon = { Punto(colorDe(esp.colorHex)) },
                                onClick = { cambiarA(esp.id) },
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun AccionesPlan(banco: Banco, progreso: Progreso, plan: PlanSemanal) {
    val context = LocalContext.current
    var confirmar by remember { mutableStateOf(false) }
    val dictadas = plan.dias.count { dia -> plan.charlas[dia]?.let { Uso(it, plan.fecha(dia)) in progreso.usos } == true }

    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Especialidades: " + plan.especialidades.joinToString { banco.especialidad(it).nombre } +
                " · $dictadas de ${plan.dias.size} dictadas",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = {
                val envio = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Plan de charlas de la semana")
                    putExtra(Intent.EXTRA_TEXT, Planificador.comoTexto(plan, banco))
                }
                context.startActivity(Intent.createChooser(envio, "Compartir plan"))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Text("  Compartir plan de la semana")
        }
        OutlinedButton(onClick = { confirmar = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Rehacer con otras especialidades")
        }
        Text(
            "Toca ⋮ en un día para elegir la charla según la actividad o cambiarla por otra al azar. Al dictarla, regístrala desde su ficha.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Rehacer el plan?") },
            text = { Text("Se borra el plan de esta semana para elegir de nuevo. Las charlas ya registradas como dictadas se mantienen en el historial.") },
            confirmButton = {
                TextButton(onClick = {
                    progreso.borrarPlan(plan.lunes)
                    confirmar = false
                }) { Text("Rehacer") }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}
