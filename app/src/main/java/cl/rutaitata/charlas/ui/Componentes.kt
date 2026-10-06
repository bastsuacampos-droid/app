package cl.rutaitata.charlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.rutaitata.charlas.data.Charla
import cl.rutaitata.charlas.data.Especialidad
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val FORMATO_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

@Composable
fun EtiquetaEspecialidad(especialidad: Especialidad, modifier: Modifier = Modifier) {
    Text(
        text = especialidad.nombre,
        color = especialidad.colorTexto,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(especialidad.color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun FilaCharla(
    charla: Charla,
    dictada: LocalDate?,
    onClick: () -> Unit,
    mostrarEspecialidad: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(charla.especialidad.color, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "N° ${charla.numero} · Semana ${charla.semana} · ${charla.diaNombre}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(charla.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (mostrarEspecialidad) {
                Text(
                    charla.especialidad.nombre,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (dictada != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Dictada el ${dictada.format(FORMATO_FECHA)}",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
