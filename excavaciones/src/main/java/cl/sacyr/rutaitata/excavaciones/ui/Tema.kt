package cl.sacyr.rutaitata.excavaciones.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Misma paleta que la app de charlas: grafito y naranja de seguridad, con buen contraste al sol.
private val NaranjaSeguridad = Color(0xFFE8710A)
private val Grafito = Color(0xFF263238)

private val Claro = lightColorScheme(
    primary = Grafito,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3E8),
    onPrimaryContainer = Grafito,
    secondary = NaranjaSeguridad,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0C2),
    onSecondaryContainer = Color(0xFF3A1D00),
    tertiary = Color(0xFF2E7D32),
    background = Color(0xFFF7F7F5),
    surface = Color(0xFFF7F7F5),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFB0BEC5),
    onPrimary = Color(0xFF102027),
    primaryContainer = Color(0xFF37474F),
    onPrimaryContainer = Color(0xFFE3E8EB),
    secondary = Color(0xFFFFA351),
    onSecondary = Color(0xFF3A1D00),
    secondaryContainer = Color(0xFF5A2E00),
    onSecondaryContainer = Color(0xFFFFE0C2),
    tertiary = Color(0xFF81C784),
)

@Composable
fun TemaExcavaciones(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        content = content,
    )
}
