package cl.sacyr.rutaitata.charlas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.sacyr.rutaitata.charlas.data.Contenido
import cl.sacyr.rutaitata.charlas.data.EstadoActualizacion
import cl.sacyr.rutaitata.charlas.data.Progreso
import cl.sacyr.rutaitata.charlas.data.SEMANAS_CICLO
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PantallaAcerca(contenido: Contenido, progreso: Progreso, onVolver: () -> Unit) {
    val banco = contenido.banco
    var confirmar by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan, actualizaciones y normativa") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
            Seccion(titulo = "Cómo elegir la charla") {
                Text(
                    "• En Semana marcas las especialidades que vas a trabajar y los días; la app arma el plan " +
                        "con charlas que no has dictado ni planificado en el mes. Cada día se puede cambiar desde ⋮.\n" +
                        "• En Temas eliges el foco de la faena del día y la app te muestra cuáles ya usaste este mes.\n" +
                        "• 'Siguiente disponible' abre la primera charla de esa especialidad que aún no dictas este mes.\n" +
                        "• Al terminar la charla, toca 'Registrar charla dictada hoy'. Queda en el Historial.\n" +
                        "• Cada día 1 las charlas vuelven a quedar disponibles; el historial de meses anteriores se conserva.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Seccion(titulo = "Plan sugerido de $SEMANAS_CICLO semanas") {
                Text(
                    "Para quien prefiere no elegir: ${banco.plan.size} charlas de lunes a sábado que cubren todas las " +
                        "especialidades. La semana 1 comenzó el lunes ${progreso.inicioCiclo.format(FORMATO_FECHA)}; " +
                        "después de la semana 4 vuelve a la semana 1.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { confirmar = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Empezar el plan esta semana")
                }
            }

            Seccion(titulo = "Actualizaciones") {
                val fecha = runCatching { LocalDate.parse(banco.fecha).format(FORMATO_FECHA) }.getOrNull()
                Text(
                    "Banco de charlas versión ${banco.version}" + (fecha?.let { " · publicado el $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (banco.novedades.isNotBlank()) {
                    Text(banco.novedades, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    when (val estado = contenido.estado) {
                        EstadoActualizacion.SinRevisar -> "La app revisa si hay charlas nuevas cada vez que se abre."
                        EstadoActualizacion.Buscando -> "Buscando actualizaciones…"
                        EstadoActualizacion.AlDia -> "Tienes la última versión publicada."
                        is EstadoActualizacion.Actualizado -> "Se descargó la versión ${estado.version}."
                        is EstadoActualizacion.Error -> "${estado.mensaje}. Se mantiene la versión guardada en el teléfono."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { scope.launch { contenido.buscarActualizacion() } },
                    enabled = contenido.estado != EstadoActualizacion.Buscando,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Buscar actualizaciones")
                }
            }

            Seccion(titulo = "Cómo dictar cada charla (5 minutos)") {
                Text(
                    "1. Lee el porqué con tus palabras, mirando a la cuadrilla (1 min).\n" +
                        "2. Recorre los 3 puntos de control y verifícalos en terreno antes de partir (2 min).\n" +
                        "3. Cierra con la Regla de Oro y la normativa que la respalda (1 min).\n" +
                        "4. Haz la pregunta de cierre y escucha la respuesta (1 min).\n" +
                        "5. Registra la asistencia en el formato oficial del proyecto.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Seccion(titulo = "Banco por especialidad") {
                banco.especialidades.forEach { esp ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Punto(colorDe(esp.colorHex))
                        Spacer(Modifier.width(8.dp))
                        Text("${esp.nombre}: ${banco.charlasDe(esp.id).size} charlas", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Seccion(titulo = "Marco normativo de referencia") {
                Text(
                    "• Ley 16.744 – Seguro social contra accidentes del trabajo y enfermedades profesionales.\n" +
                        "• Código del Trabajo Arts. 184 y 184 bis (Ley 21.012) – deber de protección y derecho a interrumpir labores.\n" +
                        "• DS 594 – Condiciones sanitarias y ambientales básicas en los lugares de trabajo.\n" +
                        "• DS 40 – reemplazado por el DS 44/2024 (vigente desde febrero de 2025): gestión preventiva e información de riesgos.\n" +
                        "• Ley 18.290 de Tránsito, Ley 20.580 (Tolerancia Cero) y Ley 20.770 (Ley Emilia).\n" +
                        "• Manual de Carreteras MOP, Vol. 6 (Seguridad Vial) y Manual de Señalización de Tránsito, Cap. 5.\n" +
                        "• Ley 21.643 (Ley Karin), Ley 20.123 y DS 76 (subcontratación), DS 43 y DS 57 (sustancias químicas), DS 148 (residuos peligrosos).\n" +
                        "• Normas NCh 349, 351, 1258, 2245, 2501 y protocolos MINSAL (PREXOR, PLANESI, TMERT, radiación UV).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Seccion(
                titulo = "Importante",
                fondo = MaterialTheme.colorScheme.secondaryContainer,
                contenido = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    "Esta app es una guía de apoyo para capataces y supervisores. Las Reglas de Oro se presentan " +
                        "como principios; valida su redacción oficial y los valores del proyecto (distancias, velocidades, " +
                        "relevos) con el Departamento de Prevención de Riesgos y el sistema de gestión SSOMA vigente. " +
                        "No reemplaza los procedimientos de trabajo seguro, el plan de desvío aprobado ni el registro oficial de asistencia. " +
                        "El historial de charlas se guarda solo en este teléfono.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Empezar el plan esta semana?") },
            text = { Text("La semana actual pasará a ser la semana 1 del plan sugerido. El historial de charlas dictadas no se borra.") },
            confirmButton = {
                TextButton(onClick = {
                    progreso.reiniciarPlan(LocalDate.now())
                    confirmar = false
                }) { Text("Empezar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = false }) { Text("Cancelar") }
            },
        )
    }
}
