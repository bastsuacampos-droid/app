package cl.sacyr.rutaitata.charlas.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.sacyr.rutaitata.charlas.data.Banco
import cl.sacyr.rutaitata.charlas.data.Charla
import cl.sacyr.rutaitata.charlas.data.Progreso
import cl.sacyr.rutaitata.charlas.data.Uso
import cl.sacyr.rutaitata.charlas.data.Usos
import cl.sacyr.rutaitata.charlas.data.comoTexto
import cl.sacyr.rutaitata.charlas.data.nombreDia
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PantallaDetalle(
    banco: Banco,
    charla: Charla,
    progreso: Progreso,
    onAbrir: (Charla) -> Unit,
    onVolver: () -> Unit,
) {
    val context = LocalContext.current
    val esp = banco.especialidad(charla.especialidadId)
    val verificados = progreso.verificados(charla.id)
    val hoy = LocalDate.now()
    val mes = YearMonth.from(hoy)
    val usadaEl = Usos.fechaEnMes(progreso.usos, charla.id, mes)
    val usadaHoy = Uso(charla.id, hoy) in progreso.usos
    val otraDisponible = Usos.disponibles(banco.charlasDe(esp.id), progreso.usos, mes)
        .firstOrNull { it.id != charla.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(charla.codigo) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = { progreso.cambiarEscala(-0.1f) }) {
                        Text("A−", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    TextButton(onClick = { progreso.cambiarEscala(0.1f) }) {
                        Text("A+", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = {
                        val envio = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, charla.titulo)
                            putExtra(Intent.EXTRA_TEXT, charla.comoTexto(banco))
                        }
                        context.startActivity(Intent.createChooser(envio, "Compartir charla"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Compartir")
                    }
                },
                colors = coloresBarra(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Punto(colorDe(esp.colorHex))
                Spacer(Modifier.width(8.dp))
                Text(esp.nombre.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(charla.titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            banco.entradaPlan(charla.id)?.let {
                Text(
                    "En el plan sugerido: semana ${it.semana}, ${nombreDia(it.dia).lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (usadaEl != null && !usadaHoy) {
                Seccion(
                    titulo = "Ya usada este mes",
                    fondo = MaterialTheme.colorScheme.errorContainer,
                    contenido = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(
                        "La dictaste el ${usadaEl.format(FORMATO_FECHA)}. Para no repetirla, elige otra de ${esp.nombre}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (otraDisponible != null) {
                        TextButton(onClick = { onAbrir(otraDisponible) }) {
                            Text("Ver ${otraDisponible.codigo}: ${otraDisponible.titulo}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Seccion(
                titulo = "El porqué · mensaje clave",
                fondo = MaterialTheme.colorScheme.secondaryContainer,
                contenido = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(charla.porQue, style = MaterialTheme.typography.bodyLarge)
            }

            Seccion(titulo = "Puntos de control · checklist de terreno (${verificados.size}/${charla.checklist.size})") {
                charla.checklist.forEachIndexed { i, punto ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { progreso.alternarPunto(charla.id, i) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(
                            checked = i in verificados,
                            onCheckedChange = { progreso.alternarPunto(charla.id, i) },
                        )
                        Text(
                            "${i + 1}. $punto",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }

            Seccion(titulo = "Respaldo estándar") {
                Text("Regla de Oro", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(charla.reglaOro, style = MaterialTheme.typography.bodyLarge)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Normativa aplicable", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(charla.normativa, style = MaterialTheme.typography.bodyMedium)
            }

            Seccion(titulo = "Pregunta de cierre a la cuadrilla") {
                Text("“${charla.preguntaCierre}”", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }

            when {
                usadaHoy -> {
                    Text(
                        "✔ Registrada como dictada hoy, ${hoy.format(FORMATO_FECHA)}",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedButton(
                        onClick = { progreso.eliminarUso(Uso(charla.id, hoy)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Deshacer registro de hoy")
                    }
                }
                usadaEl != null -> OutlinedButton(
                    onClick = { progreso.registrarUso(charla.id, hoy) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Registrar igual (se repite este mes)")
                }
                else -> Button(
                    onClick = { progreso.registrarUso(charla.id, hoy) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val completa = verificados.size == charla.checklist.size
                    Text(if (completa) "Registrar charla dictada hoy" else "Registrar dictada hoy (checklist incompleto)")
                }
            }
        }
    }
}
