package cl.sacyr.rutaitata.charlas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cl.sacyr.rutaitata.charlas.data.ActualizadorApp
import cl.sacyr.rutaitata.charlas.data.Banco
import cl.sacyr.rutaitata.charlas.data.Charla
import cl.sacyr.rutaitata.charlas.data.Contenido
import cl.sacyr.rutaitata.charlas.data.Progreso
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private const val RUTA_INICIO = "inicio"
private const val RUTA_ACERCA = "acerca"
private const val PREFIJO_DETALLE = "detalle/"
private const val PREFIJO_RECOMENDAR = "recomendar/"

internal val FORMATO_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
internal val FORMATO_DIA_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
private val FORMATO_MES = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es-CL"))

internal fun nombreMes(mes: YearMonth): String = mes.format(FORMATO_MES).replaceFirstChar { it.uppercase() }

@Composable
fun AppCharlas(contenido: Contenido, progreso: Progreso, actualizador: ActualizadorApp) {
    val banco = contenido.banco
    var ruta by rememberSaveable { mutableStateOf(RUTA_INICIO) }
    // Pestaña y especialidad elegidas se conservan al volver del detalle.
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    var especialidad by rememberSaveable { mutableStateOf(banco.especialidades.first().id) }
    // Pantalla desde la que se abrió la ficha de una charla, para volver a ella.
    var origenDetalle by rememberSaveable { mutableStateOf(RUTA_INICIO) }
    val volver = { ruta = if (ruta.startsWith(PREFIJO_DETALLE)) origenDetalle else RUTA_INICIO }
    BackHandler(enabled = ruta != RUTA_INICIO) { volver() }

    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        scope.launch { contenido.revisarSiCorresponde() }
        scope.launch { actualizador.revisarSiCorresponde() }
    }

    val densidad = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(densidad.density, densidad.fontScale * progreso.escalaTexto),
    ) {
        val abrir: (Charla) -> Unit = {
            if (!ruta.startsWith(PREFIJO_DETALLE)) origenDetalle = ruta
            ruta = PREFIJO_DETALLE + it.id
        }
        val detalle = ruta.takeIf { it.startsWith(PREFIJO_DETALLE) }
            ?.removePrefix(PREFIJO_DETALLE)?.toIntOrNull()?.let(banco::charla)
        val fechaRecomendar = ruta.takeIf { it.startsWith(PREFIJO_RECOMENDAR) }
            ?.removePrefix(PREFIJO_RECOMENDAR)?.toLongOrNull()?.let(LocalDate::ofEpochDay)
        when {
            ruta == RUTA_ACERCA -> PantallaAcerca(contenido, progreso, actualizador, onVolver = { ruta = RUTA_INICIO })
            detalle != null -> PantallaDetalle(
                banco = banco,
                charla = detalle,
                progreso = progreso,
                onAbrir = abrir,
                onVolver = volver,
            )
            fechaRecomendar != null -> PantallaRecomendar(
                banco = banco,
                progreso = progreso,
                fecha = fechaRecomendar,
                onAbrir = abrir,
                onVolver = { ruta = RUTA_INICIO },
            )
            else -> PantallaInicio(
                banco = banco,
                progreso = progreso,
                pestana = pestana,
                onPestana = { pestana = it },
                especialidad = especialidad.takeIf { id -> banco.especialidades.any { it.id == id } }
                    ?: banco.especialidades.first().id,
                onEspecialidad = { especialidad = it },
                actualizador = actualizador,
                novedad = contenido.novedadPendiente,
                onDescartarNovedad = contenido::descartarNovedad,
                onAbrir = abrir,
                onRecomendar = { ruta = PREFIJO_RECOMENDAR + it.toEpochDay() },
                onAcerca = { ruta = RUTA_ACERCA },
            )
        }
    }
}

// ---------------------------------------------------------------- Componentes comunes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun coloresBarra(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primary,
    titleContentColor = MaterialTheme.colorScheme.onPrimary,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
internal fun Seccion(
    titulo: String,
    fondo: Color = MaterialTheme.colorScheme.surfaceVariant,
    contenido: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    cuerpo: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = fondo, contentColor = contenido),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            cuerpo()
        }
    }
}

@Composable
internal fun Punto(color: Color) {
    Box(
        Modifier
            .size(12.dp)
            .background(color, CircleShape),
    )
}

/** Código de la charla sobre el color de su especialidad. */
@Composable
internal fun InsigniaCodigo(texto: String, color: Color) {
    Box(
        modifier = Modifier
            .widthIn(min = 56.dp)
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Fila de una charla. [usada] atenúa la fila para que el capataz vea de un vistazo
 * cuáles ya dictó este mes.
 */
@Composable
internal fun FilaCharla(
    banco: Banco,
    charla: Charla,
    detalle: String,
    usada: Boolean,
    resaltar: Boolean = false,
    insignia: String = charla.codigo,
    onClick: () -> Unit,
) {
    val esp = banco.especialidad(charla.especialidadId)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (resaltar) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .alpha(if (usada) 0.6f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InsigniaCodigo(insignia, colorDe(esp.colorHex))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    charla.titulo,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(detalle, style = MaterialTheme.typography.labelMedium)
            }
            if (usada) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Usada este mes",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
