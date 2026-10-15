package cl.sacyr.rutaitata.excavaciones.reporte

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * Entrega un reporte generado a otras apps sin pedir permisos de almacenamiento:
 *
 * - [intentCompartir]: selector nativo (WhatsApp, Gmail, Drive…) con Intent.ACTION_SEND.
 * - [intentVisualizar]: abre el visor de PDF del teléfono (ACTION_VIEW). Lanzarlo dentro de
 *   try/catch de ActivityNotFoundException: hay teléfonos sin visor.
 * - [copiar]: guarda una copia donde el usuario elija (Descargas, Drive) con el selector del
 *   sistema: ActivityResultContracts.CreateDocument("application/pdf") entrega la Uri de destino.
 *
 * Los archivos se exponen con el FileProvider declarado en el manifiesto (res/xml/rutas_archivos.xml).
 */
object CompartirReporte {

    const val TIPO_PDF = "application/pdf"

    fun uri(context: Context, archivo: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)

    fun intentCompartir(context: Context, archivo: File, asunto: String = archivo.nameWithoutExtension.replace('_', ' ')): Intent {
        val uri = uri(context, archivo)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = TIPO_PDF
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, asunto)
            // ClipData: sin ella algunas apps no reciben el permiso de lectura a través del selector.
            clipData = ClipData.newRawUri(archivo.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(envio, "Compartir reporte")
    }

    fun intentVisualizar(context: Context, archivo: File): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri(context, archivo), TIPO_PDF)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Copia el reporte a [destino] (Uri elegida por el usuario). Llamar fuera del hilo principal. */
    fun copiar(context: Context, archivo: File, destino: Uri) {
        val salida = context.contentResolver.openOutputStream(destino, "wt")
            ?: throw IOException("No se pudo abrir el destino para guardar el reporte")
        salida.use { out -> archivo.inputStream().use { it.copyTo(out) } }
    }
}
