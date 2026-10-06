package cl.rutaitata.charlas.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.rutaitata.charlas.data.Charla
import cl.rutaitata.charlas.data.Registro
import cl.rutaitata.charlas.data.comoTexto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalle(charla: Charla, registro: Registro, volver: () -> Unit) {
    val context = LocalContext.current
    val grande = registro.textoGrande
    val cuerpo = if (grande) TextStyle(fontSize = 21.sp, lineHeight = 30.sp) else MaterialTheme.typography.bodyLarge
    val revisados = rememberSaveable(
        charla.numero,
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { List(charla.puntosControl.size) { false }.toMutableStateList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Charla N° ${charla.numero}") },
                navigationIcon = {
                    IconButton(onClick = volver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = { registro.cambiarTextoGrande(!grande) }) {
                        Text(if (grande) "A-" else "A+", fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = {
                        val envio = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, charla.titulo)
                            putExtra(Intent.EXTRA_TEXT, charla.comoTexto())
                        }
                        context.startActivity(Intent.createChooser(envio, "Compartir charla"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Compartir charla")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Semana ${charla.semana} · ${charla.diaNombre}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EtiquetaEspecialidad(charla.especialidad)
                    Text(
                        charla.titulo,
                        style = if (grande) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item {
                Seccion("1. El por qué", "Mensaje clave · ~1 min", MaterialTheme.colorScheme.primaryContainer) {
                    Text(charla.porQue, style = cuerpo)
                }
            }
            item {
                Seccion("2. Puntos de control", "Checklist de terreno · ~3 min", MaterialTheme.colorScheme.surfaceVariant) {
                    charla.puntosControl.forEachIndexed { i, punto ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { revisados[i] = !revisados[i] }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Checkbox(checked = revisados[i], onCheckedChange = { revisados[i] = it })
                            Text("${i + 1}. $punto", style = cuerpo, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                    if (revisados.all { it }) {
                        Text(
                            "✔ Los 3 puntos fueron verificados en terreno.",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            item {
                Seccion("3. Respaldo estándar", "Regla de Oro y normativa · ~30 s", MaterialTheme.colorScheme.secondaryContainer) {
                    Text(charla.respaldo, style = cuerpo)
                }
            }
            item {
                Seccion("4. Pregunta de cierre", "Verifica que el mensaje llegó · ~30 s", MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(charla.preguntaCierre, style = cuerpo, fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                val dictada = registro.dictadas[charla.numero]
                if (dictada == null) {
                    Button(onClick = { registro.marcarDictada(charla.numero) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Marcar como dictada hoy")
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("✔ Dictada el ${dictada.format(FORMATO_FECHA)}", fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = { registro.desmarcar(charla.numero) }) { Text("Desmarcar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Seccion(titulo: String, subtitulo: String, fondo: Color, contenido: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = fondo), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitulo, style = MaterialTheme.typography.labelMedium)
            contenido()
        }
    }
}
