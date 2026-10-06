package cl.sacyr.rutaitata.charlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.sacyr.rutaitata.charlas.data.ActualizadorApp
import cl.sacyr.rutaitata.charlas.data.Banco
import cl.sacyr.rutaitata.charlas.data.Calendario
import cl.sacyr.rutaitata.charlas.data.Charla
import cl.sacyr.rutaitata.charlas.data.Progreso
import cl.sacyr.rutaitata.charlas.data.SEMANAS_CICLO
import cl.sacyr.rutaitata.charlas.data.Uso
import cl.sacyr.rutaitata.charlas.data.Usos
import cl.sacyr.rutaitata.charlas.data.nombreDia
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val PESTANAS = listOf("Semana", "Temas", "Plan mes", "Historial")
private val DIAS_CORTOS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PantallaInicio(
    banco: Banco,
    progreso: Progreso,
    pestana: Int,
    onPestana: (Int) -> Unit,
    especialidad: String,
    onEspecialidad: (String) -> Unit,
    actualizador: ActualizadorApp,
    novedad: Banco?,
    onDescartarNovedad: () -> Unit,
    onAbrir: (Charla) -> Unit,
    onAcerca: () -> Unit,
) {
    var ocultarUsadas by rememberSaveable { mutableStateOf(false) }
    var mesHistorial by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var lunesSemana by rememberSaveable { mutableStateOf(Calendario.semanaVigente(LocalDate.now()).toString()) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Charlas de 5 minutos", fontWeight = FontWeight.Bold)
                            Text(banco.proyecto, style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    actions = {
                        IconButton(onClick = onAcerca) {
                            Icon(Icons.Filled.Info, contentDescription = "Plan, actualizaciones y normativa")
                        }
                    },
                    colors = coloresBarra(),
                )
                TabRow(selectedTabIndex = pestana) {
                    PESTANAS.forEachIndexed { i, titulo ->
                        Tab(selected = pestana == i, onClick = { onPestana(i) }, text = { Text(titulo, maxLines = 1) })
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "version-app") { AvisoVersionApp(actualizador) }
            if (novedad != null) {
                item { AvisoNovedad(novedad, onDescartarNovedad) }
            }
            when (pestana) {
                0 -> listaSemana(
                    banco, progreso, LocalDate.parse(lunesSemana), { lunesSemana = it.toString() }, onAbrir,
                )
                1 -> listaPorEspecialidad(
                    banco, progreso, especialidad, onEspecialidad,
                    ocultarUsadas, { ocultarUsadas = it }, onAbrir,
                )
                2 -> listaPlan(banco, progreso, onAbrir)
                else -> listaHistorial(
                    banco, progreso, YearMonth.parse(mesHistorial), { mesHistorial = it.toString() }, onAbrir,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Especialidades

private fun LazyListScope.listaPorEspecialidad(
    banco: Banco,
    progreso: Progreso,
    especialidad: String,
    onEspecialidad: (String) -> Unit,
    ocultarUsadas: Boolean,
    onOcultarUsadas: (Boolean) -> Unit,
    onAbrir: (Charla) -> Unit,
) {
    val mes = YearMonth.now()
    val esp = banco.especialidad(especialidad)
    val charlas = banco.charlasDe(especialidad)
    val disponibles = Usos.disponibles(charlas, progreso.usos, mes)

    item {
        SelectorEspecialidad(banco, progreso, mes, especialidad, onEspecialidad)
    }

    item {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                "${nombreMes(mes)}: ${charlas.size - disponibles.size} usadas · ${disponibles.size} disponibles de ${charlas.size}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { if (charlas.isEmpty()) 0f else (charlas.size - disponibles.size).toFloat() / charlas.size },
                modifier = Modifier.fillMaxWidth(),
                color = colorDe(esp.colorHex),
            )
            Spacer(Modifier.height(10.dp))
            val siguiente = disponibles.firstOrNull()
            if (siguiente != null) {
                Button(onClick = { onAbrir(siguiente) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Siguiente disponible: ${siguiente.codigo}")
                }
            } else {
                Text(
                    "Ya usaste todas las charlas de esta especialidad este mes. Se liberan el día 1 del próximo mes.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("Ocultar las usadas este mes", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = ocultarUsadas, onCheckedChange = onOcultarUsadas)
            }
        }
    }

    val visibles = if (ocultarUsadas) disponibles else charlas
    items(visibles, key = { it.id }) { charla ->
        val fecha = Usos.fechaEnMes(progreso.usos, charla.id, mes)
        FilaCharla(
            banco = banco,
            charla = charla,
            detalle = fecha?.let { "Usada el ${it.format(FORMATO_DIA_MES)}" } ?: "Disponible este mes",
            usada = fecha != null,
            onClick = { onAbrir(charla) },
        )
    }
}

/** Especialidad elegida, siempre visible; al tocarla se despliegan todas con su avance del mes. */
@Composable
private fun SelectorEspecialidad(
    banco: Banco,
    progreso: Progreso,
    mes: YearMonth,
    especialidad: String,
    onEspecialidad: (String) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    fun avance(id: String): String {
        val total = banco.charlasDe(id)
        return "${total.size - Usos.disponibles(total, progreso.usos, mes).size}/${total.size}"
    }
    val esp = banco.especialidad(especialidad)
    Box {
        OutlinedCard(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Punto(colorDe(esp.colorHex))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Especialidad", style = MaterialTheme.typography.labelMedium)
                    Text(esp.nombre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Elegir especialidad")
            }
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            banco.especialidades.forEach { e ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${e.nombre} · ${avance(e.id)} usadas",
                            fontWeight = if (e.id == especialidad) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = { Punto(colorDe(e.colorHex)) },
                    onClick = {
                        abierto = false
                        onEspecialidad(e.id)
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Plan

private fun LazyListScope.listaPlan(banco: Banco, progreso: Progreso, onAbrir: (Charla) -> Unit) {
    val hoy = LocalDate.now()
    val mes = YearMonth.from(hoy)
    val diaCiclo = Calendario.diaCiclo(progreso.inicioCiclo, hoy)
    val charlaHoy = banco.charla(diaCiclo.semana, diaCiclo.dia)

    if (charlaHoy != null) {
        item {
            TarjetaHoy(
                banco = banco,
                charla = charlaHoy,
                semana = diaCiclo.semana,
                dia = diaCiclo.dia,
                esDomingo = hoy.dayOfWeek == DayOfWeek.SUNDAY,
                usadaEl = Usos.fechaEnMes(progreso.usos, charlaHoy.id, mes),
                onAbrir = { onAbrir(charlaHoy) },
            )
        }
    }
    item {
        Text(
            "Plan sugerido de $SEMANAS_CICLO semanas que cubre todas las especialidades. " +
                "Si una charla del plan ya la usaste este mes, elige otra en la pestaña Temas.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    banco.plan.groupBy { it.semana }.forEach { (semana, entradas) ->
        item(key = "semana$semana") {
            Text(
                text = "Semana $semana · ${rangoSemana(progreso.inicioCiclo, semana)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        items(entradas, key = { "plan${it.semana}-${it.dia}" }) { entrada ->
            val charla = banco.charla(entrada.charlaId) ?: return@items
            val fecha = Usos.fechaEnMes(progreso.usos, charla.id, mes)
            FilaCharla(
                banco = banco,
                charla = charla,
                insignia = nombreDia(entrada.dia).take(3).uppercase(),
                detalle = "${charla.codigo} · ${banco.especialidad(charla.especialidadId).nombre}" +
                    (fecha?.let { " · usada el ${it.format(FORMATO_DIA_MES)}" } ?: ""),
                usada = fecha != null,
                resaltar = charla == charlaHoy && entrada.semana == diaCiclo.semana && entrada.dia == diaCiclo.dia,
                onClick = { onAbrir(charla) },
            )
        }
    }
}

@Composable
private fun TarjetaHoy(
    banco: Banco,
    charla: Charla,
    semana: Int,
    dia: Int,
    esDomingo: Boolean,
    usadaEl: LocalDate?,
    onAbrir: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAbrir),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (esDomingo) "PREPARA LA CHARLA DEL LUNES" else "SUGERIDA PARA HOY · 08:00",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(charla.titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Semana $semana · ${nombreDia(dia)} · ${charla.codigo} · ${banco.especialidad(charla.especialidadId).nombre}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (usadaEl != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ya la usaste el ${usadaEl.format(FORMATO_DIA_MES)}: elige otra de la misma especialidad.",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun rangoSemana(inicioCiclo: LocalDate, semana: Int): String {
    val hoy = LocalDate.now()
    val actual = Calendario.diaCiclo(inicioCiclo, hoy).semana
    // Lunes de la próxima ocurrencia (o la actual) de esa semana del plan.
    val lunesActual = Calendario.lunesDe(if (hoy.dayOfWeek == DayOfWeek.SUNDAY) hoy.plusDays(1) else hoy)
    val desfase = Math.floorMod(semana - actual, SEMANAS_CICLO)
    val lunes = lunesActual.plusWeeks(desfase.toLong())
    return "${lunes.format(FORMATO_DIA_MES)} al ${lunes.plusDays(5).format(FORMATO_DIA_MES)}"
}

// ---------------------------------------------------------------- Historial

private fun LazyListScope.listaHistorial(
    banco: Banco,
    progreso: Progreso,
    mes: YearMonth,
    onMes: (YearMonth) -> Unit,
    onAbrir: (Charla) -> Unit,
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { onMes(mes.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
            }
            Text(
                nombreMes(mes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMes(mes.plusMonths(1)) }, enabled = mes < YearMonth.now()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
            }
        }
    }

    val usos = Usos.delMes(progreso.usos, mes)
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("${usos.size} charlas dictadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                banco.especialidades.forEach { e ->
                    val n = usos.count { banco.charla(it.charlaId)?.especialidadId == e.id }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Punto(colorDe(e.colorHex))
                        Spacer(Modifier.width(8.dp))
                        Text("${e.nombre}: $n de ${banco.charlasDe(e.id).size}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (usos.isEmpty()) {
        item {
            Text(
                "No hay charlas registradas en este mes. Al terminar una charla, abre su ficha y toca 'Registrar charla dictada hoy'.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    items(usos, key = { "${it.charlaId}@${it.fecha}" }) { uso ->
        FilaUso(banco, uso, progreso, onAbrir)
    }
}

@Composable
private fun FilaUso(banco: Banco, uso: Uso, progreso: Progreso, onAbrir: (Charla) -> Unit) {
    val charla = banco.charla(uso.charlaId)
    var confirmar by remember { mutableStateOf(false) }
    val dia = "${DIAS_CORTOS[uso.fecha.dayOfWeek.value - 1]} ${uso.fecha.format(FORMATO_DIA_MES)}"
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (charla != null) {
            Column(Modifier.weight(1f)) {
                FilaCharla(
                    banco = banco,
                    charla = charla,
                    detalle = "$dia · ${banco.especialidad(charla.especialidadId).nombre}",
                    usada = false,
                    onClick = { onAbrir(charla) },
                )
            }
        } else {
            Text(
                "$dia · charla ${uso.charlaId} (ya no está en el banco)",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.weight(1f),
            )
        }
        IconButton(onClick = { confirmar = true }) {
            Icon(Icons.Filled.Delete, contentDescription = "Borrar registro")
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Borrar este registro?") },
            text = { Text("La charla volverá a aparecer como disponible si no tiene otro registro en el mes.") },
            confirmButton = {
                TextButton(onClick = {
                    progreso.eliminarUso(uso)
                    confirmar = false
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun AvisoNovedad(novedad: Banco, onDescartar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = Color.White,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "BANCO DE CHARLAS ACTUALIZADO · VERSIÓN ${novedad.version}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            if (novedad.novedades.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(novedad.novedades, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onDescartar, modifier = Modifier.align(Alignment.End)) {
                Text("Entendido", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
