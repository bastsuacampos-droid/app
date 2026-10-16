package cl.sacyr.rutaitata.excavaciones

import android.app.Application
import cl.sacyr.rutaitata.excavaciones.data.ActualizadorApp
import cl.sacyr.rutaitata.excavaciones.data.ArchivosFotos
import cl.sacyr.rutaitata.excavaciones.data.Preferencias
import cl.sacyr.rutaitata.excavaciones.data.ProveedorUbicacion
import cl.sacyr.rutaitata.excavaciones.data.RepositorioExcavaciones
import cl.sacyr.rutaitata.excavaciones.data.Respaldo
import cl.sacyr.rutaitata.excavaciones.data.local.BaseDatos
import cl.sacyr.rutaitata.excavaciones.reporte.FuenteTrueType
import cl.sacyr.rutaitata.excavaciones.reporte.FuentesPdf
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
    val generadorPdf: GeneradorReportePdf by lazy {
        fun fuente(nombre: String) = FuenteTrueType(assets.open("fuentes/$nombre.ttf").use { it.readBytes() }, nombre)
        GeneradorReportePdf(File(filesDir, "reportes"), FuentesPdf(fuente("Roboto-Regular"), fuente("Roboto-Bold")), fotos::paraReporte)
    }
    val respaldo: Respaldo by lazy { Respaldo(baseDatos, fotos) }
    val actualizador: ActualizadorApp by lazy { ActualizadorApp(this, BuildConfig.URL_VERSIONES, BuildConfig.VERSION_NAME) }

    /**
     * Solo la versión publicada (firmada) busca actualizaciones: una de prueba (debug) tiene otra
     * firma y Android no la dejaría actualizarse con la publicada.
     */
    val buscaActualizaciones: Boolean get() = !BuildConfig.DEBUG

    /** Trabajo que no depende de una pantalla (limpieza de archivos). */
    val segundoPlano = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        segundoPlano.launch {
            runCatching { fotos.limpiarHuerfanas(repositorio.archivosDeFotosEnUso()) }
        }
    }
}
