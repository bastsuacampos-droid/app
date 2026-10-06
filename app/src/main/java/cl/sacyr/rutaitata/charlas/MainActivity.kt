package cl.sacyr.rutaitata.charlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.sacyr.rutaitata.charlas.data.BancoParser
import cl.sacyr.rutaitata.charlas.data.Progreso
import cl.sacyr.rutaitata.charlas.ui.AppCharlas
import cl.sacyr.rutaitata.charlas.ui.TemaCharlas

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val banco = BancoParser.parse(assets.open("charlas.json").bufferedReader().use { it.readText() })
        val progreso = Progreso(applicationContext, banco.charlas.map { it.id })
        setContent {
            TemaCharlas {
                AppCharlas(banco, progreso)
            }
        }
    }
}
