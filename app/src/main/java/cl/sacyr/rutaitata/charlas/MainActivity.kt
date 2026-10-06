package cl.sacyr.rutaitata.charlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.sacyr.rutaitata.charlas.data.Contenido
import cl.sacyr.rutaitata.charlas.data.Progreso
import cl.sacyr.rutaitata.charlas.ui.AppCharlas
import cl.sacyr.rutaitata.charlas.ui.TemaCharlas

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val contenido = Contenido(applicationContext, BuildConfig.URL_CONTENIDO)
        val progreso = Progreso(applicationContext)
        setContent {
            TemaCharlas {
                AppCharlas(contenido, progreso)
            }
        }
    }
}
