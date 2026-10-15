package cl.sacyr.rutaitata.excavaciones

import android.app.Application
import cl.sacyr.rutaitata.excavaciones.data.ArchivosFotos
import cl.sacyr.rutaitata.excavaciones.data.Preferencias
import cl.sacyr.rutaitata.excavaciones.data.ProveedorUbicacion
import cl.sacyr.rutaitata.excavaciones.data.RepositorioExcavaciones
import cl.sacyr.rutaitata.excavaciones.data.local.BaseDatos
import cl.sacyr.rutaitata.excavaciones.reporte.GeneradorReportePdf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Contenedor de dependencias de la app (sin framework de inyección: son pocas y todas locales). */
class AppExcavaciones : Application() {
    val baseDatos: BaseDatos by lazy { BaseDatos.crear(this) }
    val repositorio: RepositorioExcavaciones by lazy { RepositorioExcavaciones(baseDatos) }
    val fotos: ArchivosFotos by lazy { ArchivosFotos(File(filesDir, "fotos")) }
    val ubicacion: ProveedorUbicacion by lazy { ProveedorUbicacion(this) }
    val preferencias: Preferencias by lazy { Preferencias(this) }
    val generadorPdf: GeneradorReportePdf by lazy { GeneradorReportePdf(File(filesDir, "reportes")) }

    /** Trabajo que no depende de una pantalla (limpieza de archivos). */
    val segundoPlano = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        segundoPlano.launch {
            runCatching { fotos.limpiarHuerfanas(repositorio.archivosDeFotosEnUso()) }
        }
    }
}
