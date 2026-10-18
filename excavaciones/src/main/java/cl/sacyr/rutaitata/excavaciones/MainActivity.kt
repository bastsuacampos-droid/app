package cl.sacyr.rutaitata.excavaciones

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.sacyr.rutaitata.excavaciones.ui.AppNavegacion
import cl.sacyr.rutaitata.excavaciones.ui.TemaExcavaciones

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaExcavaciones {
                AppNavegacion()
            }
        }
    }
}
