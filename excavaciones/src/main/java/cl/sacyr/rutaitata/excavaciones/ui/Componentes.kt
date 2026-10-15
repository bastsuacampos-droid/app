@file:OptIn(ExperimentalMaterial3Api::class)

package cl.sacyr.rutaitata.excavaciones.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.sacyr.rutaitata.excavaciones.data.ArchivosFotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CHILE = Locale.forLanguageTag("es-CL")
private val FORMATO_DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", CHILE)

/** "Jueves 15 de octubre" */
fun diaLargo(fecha: LocalDate): String = fecha.format(FORMATO_DIA).replaceFirstChar { it.uppercase() }

/** El DatePicker de Material trabaja con milisegundos UTC a medianoche. */
private fun LocalDate.aMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.aFechaUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun DialogoFecha(inicial: LocalDate, onElegir: (LocalDate) -> Unit, onCerrar: () -> Unit) {
    val estado = rememberDatePickerState(initialSelectedDateMillis = inicial.aMillisUtc())
    DatePickerDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(onClick = {
                estado.selectedDateMillis?.let { onElegir(it.aFechaUtc()) }
                onCerrar()
            }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    ) {
        DatePicker(state = estado)
    }
}

/**
 * Opciones excluyentes en botones grandes (se tocan bien con guantes y se distinguen al sol): la
 * elegida va rellena y con ✓. Para TalkBack se anuncian como botones de radio.
 */
@Composable
fun <T> SelectorOpciones(
    opciones: List<T>,
    elegida: T?,
    etiqueta: (T) -> String,
    onElegir: (T) -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { opcion ->
            val marcada = opcion == elegida
            val semantica = Modifier.weight(1f).heightIn(min = 56.dp).semantics {
                selected = marcada
                role = Role.RadioButton
            }
            if (marcada) {
                Button(
                    onClick = { onElegir(opcion) },
                    modifier = semantica,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(etiqueta(opcion), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = { onElegir(opcion) },
                    modifier = semantica,
                    border = BorderStroke(1.dp, if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        etiqueta(opcion),
                        textAlign = TextAlign.Center,
                        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** Foto de files/fotos reducida para pantalla; se lee fuera del hilo principal. */
@Composable
fun MiniaturaFoto(archivos: ArchivosFotos, nombre: String, lado: Int, modifier: Modifier = Modifier, descripcion: String? = null) {
    val bitmap by produceState<Bitmap?>(null, nombre, lado) {
        value = withContext(Dispatchers.IO) { archivos.miniatura(nombre, lado) }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = descripcion, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

@Composable
fun Titulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/** Mensaje centrado para listas vacías, con una acción opcional. */
@Composable
fun EstadoVacio(titulo: String, detalle: String, accion: String? = null, onAccion: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            detalle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (accion != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Button(onClick = onAccion) { Text(accion) }
            }
        }
    }
}
