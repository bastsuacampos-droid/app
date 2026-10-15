package cl.sacyr.rutaitata.excavaciones

import android.app.Application
import cl.sacyr.rutaitata.excavaciones.data.RepositorioExcavaciones
import cl.sacyr.rutaitata.excavaciones.data.local.BaseDatos
import cl.sacyr.rutaitata.excavaciones.reporte.GeneradorReportePdf
import java.io.File

/** Contenedor de dependencias de la app (sin framework de inyección: son pocas y todas locales). */
class AppExcavaciones : Application() {
    val baseDatos: BaseDatos by lazy { BaseDatos.crear(this) }
    val repositorio: RepositorioExcavaciones by lazy { RepositorioExcavaciones(baseDatos) }
    val generadorPdf: GeneradorReportePdf by lazy { GeneradorReportePdf(File(filesDir, "reportes")) }
}
