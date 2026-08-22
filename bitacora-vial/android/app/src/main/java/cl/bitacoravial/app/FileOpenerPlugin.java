package cl.bitacoravial.app;

import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Writes a file to cacheDir/compartidos in bounded-size chunks (see writeChunk — a single huge
 * base64 string handed to a Capacitor plugin call, e.g. a multi-MB respaldo completo with
 * embedded photos, has been observed to crash the app; the JS-to-native bridge just isn't built
 * for large single messages), then hands the finished file to Android's "abrir con" chooser
 * (ACTION_VIEW, for previewing a document the user picked) or its share sheet (ACTION_SEND, for
 * an export the user wants to save or send elsewhere).
 */
@CapacitorPlugin(name = "FileOpener")
public class FileOpenerPlugin extends Plugin {

    /** Appends one base64-decoded chunk to cacheDir/compartidos/<fileName> — call repeatedly
     * (append=false on the first chunk to truncate/create, true after) from
     * writeFileChunked() in nativeFileOpener.ts, then open()/share() once all chunks are
     * written. */
    @PluginMethod
    public void writeChunk(PluginCall call) {
        String base64 = call.getString("data");
        String fileName = call.getString("fileName", "archivo");
        boolean append = Boolean.TRUE.equals(call.getBoolean("append", false));

        if (base64 == null) {
            call.reject("Missing data");
            return;
        }

        try {
            byte[] bytes = base64.isEmpty() ? new byte[0] : Base64.decode(base64, Base64.DEFAULT);
            File dir = new File(getContext().getCacheDir(), "compartidos");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file, append)) {
                fos.write(bytes);
            }
            call.resolve(new JSObject());
        } catch (Exception e) {
            call.reject("No se pudo escribir el archivo: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void open(PluginCall call) {
        try {
            Uri contentUri = contentUriFor(call.getString("fileName", "archivo"));

            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(contentUri, call.getString("mimeType", "*/*"));
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(viewIntent, "Abrir con");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooser);

            call.resolve(new JSObject());
        } catch (Exception e) {
            call.reject("No se pudo abrir el archivo: " + e.getMessage(), e);
        }
    }

    /**
     * For exports (respaldo, CSV, PDF) rather than "abrir con": ACTION_SEND instead of
     * ACTION_VIEW, since the point is to save or send the file somewhere (Drive, Gmail, Files/
     * "Guardar en el dispositivo", WhatsApp…), not to preview it — and unlike a document the
     * user picked themselves, there's no guarantee any app on the phone can even *view* a plain
     * .csv or .json, while every one of those targets accepts ACTION_SEND regardless of type.
     */
    @PluginMethod
    public void share(PluginCall call) {
        try {
            Uri contentUri = contentUriFor(call.getString("fileName", "archivo"));

            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType(call.getString("mimeType", "*/*"));
            sendIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(sendIntent, "Guardar o compartir");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooser);

            call.resolve(new JSObject());
        } catch (Exception e) {
            call.reject("No se pudo exportar el archivo: " + e.getMessage(), e);
        }
    }

    private Uri contentUriFor(String fileName) {
        File file = new File(new File(getContext().getCacheDir(), "compartidos"), fileName);
        return FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
    }
}
