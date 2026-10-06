package cl.rutaitata.charlas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.rutaitata.charlas.data.BancoCharlas
import cl.rutaitata.charlas.data.Ciclo
import cl.rutaitata.charlas.data.Especialidad
import cl.rutaitata.charlas.data.Marco
import cl.rutaitata.charlas.data.Registro
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FORMATO_LARGO = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-CL"))

@Composable
fun PantallaHoy(registro: Registro, contentPadding: PaddingValues, abrir: (Int) -> Unit) {
    val hoy = remember { LocalDate.now() }
    val semana = Ciclo.semanaDelCiclo(registro.inicioCiclo, hoy)
    val charlaHoy = Ciclo.charlaDelDia(registro.inicioCiclo, hoy)
    var confirmarReinicio by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Mejoramiento Ruta Itata", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Banco mensual de charlas de 5 minutos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${hoy.format(FORMATO_LARGO).replaceFirstChar { it.uppercase() }} · Semana $semana del ciclo",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (charlaHoy != null) {
                        Text("Charla de hoy", style = MaterialTheme.typography.titleSmall)
                        Text(charlaHoy.titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        EtiquetaEspecialidad(charlaHoy.especialidad)
                        val dictada = registro.dictadas[charlaHoy.numero]
                        if (dictada != null) Text("✔ Dictada el ${dictada.format(FORMATO_FECHA)}")
                        Button(onClick = { abrir(charlaHoy.numero) }) { Text("Abrir charla") }
                    } else {
                        val proxima = Ciclo.charlaDelDia(registro.inicioCiclo, hoy.plusDays(1))
                        Text("Domingo: sin charla programada.", style = MaterialTheme.typography.titleMedium)
                        if (proxima != null) {
                            Text("Mañana lunes: ${proxima.titulo}")
                            OutlinedButton(onClick = { abrir(proxima.numero) }) { Text("Preparar charla") }
                        }
                    }
                }
            }
        }
        item {
            val total = BancoCharlas.charlas.size
            val hechas = registro.dictadas.size
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Avance del mes: $hechas de $total charlas dictadas", style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(progress = { hechas / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                    if (hechas > 0) {
                        TextButton(onClick = { confirmarReinicio = true }) { Text("Reiniciar registro para un nuevo mes") }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿En qué semana del ciclo estamos?", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Ajusta la semana si partiste el banco en otra fecha. La charla del día se calcula de lunes a sábado.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        (1..Ciclo.SEMANAS).forEach { s ->
                            SegmentedButton(
                                selected = s == semana,
                                onClick = { registro.fijarSemanaActual(s, hoy) },
                                shape = SegmentedButtonDefaults.itemShape(index = s - 1, count = Ciclo.SEMANAS),
                            ) { Text("S$s") }
                        }
                    }
                }
            }
        }
        item {
            val ejemplo = BancoCharlas.porNumero(BancoCharlas.NUMERO_EJEMPLO)!!
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Charla modelo (validación de formato)", style = MaterialTheme.typography.titleSmall)
                    Text(ejemplo.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Título · El por qué · 3 puntos de control · Respaldo estándar · Pregunta de cierre",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { abrir(ejemplo.numero) }) { Text("Ver charla modelo") }
                }
            }
        }
    }

    if (confirmarReinicio) {
        AlertDialog(
            onDismissRequest = { confirmarReinicio = false },
            title = { Text("Reiniciar registro") },
            text = { Text("Se borrarán las marcas de charlas dictadas en este teléfono. El contenido del banco no cambia.") },
            confirmButton = {
                TextButton(onClick = { registro.reiniciarRegistro(); confirmarReinicio = false }) { Text("Reiniciar") }
            },
            dismissButton = { TextButton(onClick = { confirmarReinicio = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun PantallaPlan(registro: Registro, contentPadding: PaddingValues, abrir: (Int) -> Unit) {
    val hoy = remember { LocalDate.now() }
    val semanaActual = Ciclo.semanaDelCiclo(registro.inicioCiclo, hoy)
    val inicioSemanaActual = Ciclo.lunesDe(hoy)

    LazyColumn(contentPadding = contentPadding) {
        (1..Ciclo.SEMANAS).forEach { semana ->
            item(key = "semana$semana") {
                val lunes = inicioSemanaActual.plusWeeks((semana - semanaActual).toLong())
                val sabado = lunes.with(DayOfWeek.SATURDAY)
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
                    Text(
                        "Semana $semana" + if (semana == semanaActual) " (actual)" else "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (semana == semanaActual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${lunes.format(FORMATO_FECHA)} al ${sabado.format(FORMATO_FECHA)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(BancoCharlas.deSemana(semana), key = { it.numero }) { charla ->
                FilaCharla(charla, registro.dictadas[charla.numero], onClick = { abrir(charla.numero) }, mostrarEspecialidad = true)
                HorizontalDivider(Modifier.padding(start = 40.dp))
            }
        }
    }
}

@Composable
fun PantallaEspecialidades(registro: Registro, contentPadding: PaddingValues, abrir: (Int) -> Unit) {
    var seleccion by rememberSaveable { mutableStateOf(Especialidad.MAQUINARIA) }
    val lista = BancoCharlas.deEspecialidad(seleccion)

    LazyColumn(contentPadding = contentPadding) {
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Especialidad.entries.forEach { e ->
                    FilterChip(
                        selected = e == seleccion,
                        onClick = { seleccion = e },
                        label = { Text("${e.nombre} (${BancoCharlas.deEspecialidad(e).size})") },
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EtiquetaEspecialidad(seleccion)
                Text(seleccion.descripcion, style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(lista, key = { it.numero }) { charla ->
            FilaCharla(charla, registro.dictadas[charla.numero], onClick = { abrir(charla.numero) })
            HorizontalDivider(Modifier.padding(start = 40.dp))
        }
    }
}

@Composable
fun PantallaMarco(contentPadding: PaddingValues) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Rol y alcance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(Marco.ROL, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(Marco.secciones) { seccion ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(seccion.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    seccion.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
