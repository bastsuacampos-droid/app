package cl.rutaitata.charlas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import cl.rutaitata.charlas.data.Especialidad

private val Claro = lightColorScheme(
    primary = Color(0xFFD35400),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF3A1300),
    secondary = Color(0xFF1F3A5F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E3FF),
    onSecondaryContainer = Color(0xFF001B3C),
    background = Color(0xFFFAF8F6),
    surface = Color(0xFFFAF8F6),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFFFB68C),
    onPrimary = Color(0xFF552100),
    primaryContainer = Color(0xFF793100),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFA8C8FF),
    onSecondary = Color(0xFF00305F),
    secondaryContainer = Color(0xFF1F3A5F),
    onSecondaryContainer = Color(0xFFD5E3FF),
)

@Composable
fun TemaCharlas(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        content = content,
    )
}

val Especialidad.color: Color
    get() = when (this) {
        Especialidad.MAQUINARIA -> Color(0xFFF2B705)
        Especialidad.TRANSITO -> Color(0xFFE4572E)
        Especialidad.ASFALTO -> Color(0xFF5D6D7E)
        Especialidad.OBRAS_DE_ARTE -> Color(0xFF2E8B57)
        Especialidad.TRANSVERSAL -> Color(0xFF2F6FB5)
    }

val Especialidad.colorTexto: Color
    get() = if (this == Especialidad.MAQUINARIA) Color(0xFF1A1A1A) else Color.White
