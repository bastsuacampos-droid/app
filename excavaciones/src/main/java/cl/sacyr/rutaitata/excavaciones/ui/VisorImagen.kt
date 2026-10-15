package cl.sacyr.rutaitata.excavaciones.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ampliación máxima: con una foto de 2560 px, sobre 8× ya no aparece más detalle. */
private const val ZOOM_MAXIMO = 8f
private const val ZOOM_DOBLE_TOQUE = 3f

/**
 * Imagen a pantalla completa, sobre fondo negro, que se amplía con dos dedos o con doble toque
 * (otro doble toque vuelve al tamaño completo). [cargar] se ejecuta fuera del hilo principal.
 */
@Composable
fun VisorImagen(titulo: String, pie: String?, onCerrar: () -> Unit, cargar: suspend () -> Bitmap?) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // null mientras carga; si falla (archivo dañado o sin memoria), un aviso en vez de la imagen.
        val imagen by produceState<Result<ImageBitmap>?>(null) {
            value = withContext(Dispatchers.IO) {
                runCatching { cargar()?.asImageBitmap() ?: error("Imagen no disponible") }
            }
        }
        Surface(color = Color.Black, contentColor = Color.White, modifier = Modifier.fillMaxSize().testTag("visorImagen")) {
            Box(Modifier.fillMaxSize()) {
                val cargada = imagen
                when {
                    cargada == null -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
                    cargada.isSuccess -> ImagenAmpliable(cargada.getOrThrow(), titulo, Modifier.fillMaxSize())
                    else -> Text("No se pudo abrir la imagen", modifier = Modifier.align(Alignment.Center))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().safeDrawingPadding().align(Alignment.TopCenter)
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                ) {
                    Text(titulo, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = onCerrar) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
                }
                Column(Modifier.fillMaxWidth().safeDrawingPadding().align(Alignment.BottomCenter).padding(16.dp)) {
                    pie?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        "Separa dos dedos o toca dos veces para ampliar",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/**
 * Imagen ajustada al espacio que se amplía alrededor de los dedos y se desplaza sin salirse de
 * los bordes.
 */
@Composable
fun ImagenAmpliable(imagen: ImageBitmap, descripcion: String?, modifier: Modifier = Modifier) {
    var escala by remember(imagen) { mutableFloatStateOf(1f) }
    var corrimiento by remember(imagen) { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.clipToBounds()) {
        val ancho = constraints.maxWidth.toFloat()
        val alto = constraints.maxHeight.toFloat()
        val centro = Offset(ancho / 2, alto / 2)
        // Con la imagen ampliada, su borde no puede entrar más allá del borde de la pantalla.
        fun limitar(o: Offset, e: Float): Offset {
            val x = ancho * (e - 1) / 2
            val y = alto * (e - 1) / 2
            return Offset(o.x.coerceIn(-x, x), o.y.coerceIn(-y, y))
        }
        Image(
            imagen,
            contentDescription = descripcion,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(ancho, alto) {
                    detectTapGestures(onDoubleTap = { toque ->
                        if (escala > 1.01f) {
                            escala = 1f
                            corrimiento = Offset.Zero
                        } else {
                            escala = ZOOM_DOBLE_TOQUE
                            corrimiento = limitar((centro - toque) * (ZOOM_DOBLE_TOQUE - 1), ZOOM_DOBLE_TOQUE)
                        }
                    })
                }
                .pointerInput(ancho, alto) {
                    detectTransformGestures { dedos, arrastre, zoom, _ ->
                        val nueva = (escala * zoom).coerceIn(1f, ZOOM_MAXIMO)
                        // El punto bajo los dedos queda fijo mientras cambia la escala.
                        val fijo = corrimiento + (dedos - centro - corrimiento) * (1 - nueva / escala)
                        escala = nueva
                        corrimiento = limitar(fijo + arrastre, nueva)
                    }
                }
                .graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                    translationX = corrimiento.x
                    translationY = corrimiento.y
                },
        )
    }
}
