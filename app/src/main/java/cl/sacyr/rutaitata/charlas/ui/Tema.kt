package cl.sacyr.rutaitata.charlas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NaranjaSeguridad = Color(0xFFE8710A)
private val Grafito = Color(0xFF263238)

private val Claro = lightColorScheme(
    primary = Grafito,
    onPrimary = Color.White,
    secondary = NaranjaSeguridad,
    onSecondary = Color.White,
    tertiary = Color(0xFF2E7D32),
    secondaryContainer = Color(0xFFFFE0C2),
    onSecondaryContainer = Color(0xFF3A1D00),
    background = Color(0xFFF7F7F5),
    surface = Color(0xFFF7F7F5),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFB0BEC5),
    onPrimary = Color(0xFF102027),
    secondary = Color(0xFFFFA351),
    onSecondary = Color(0xFF3A1D00),
    tertiary = Color(0xFF81C784),
    secondaryContainer = Color(0xFF5A2E00),
    onSecondaryContainer = Color(0xFFFFE0C2),
)

@Composable
fun TemaCharlas(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        content = content,
    )
}

fun colorDe(hex: String): Color = Color(android.graphics.Color.parseColor(hex))
