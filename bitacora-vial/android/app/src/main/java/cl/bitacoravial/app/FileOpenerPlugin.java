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
 * Writes a documento's bytes to a cache file and hands it to Android's "abrir con" chooser
 * (ACTION_VIEW wrapped in createChooser) — the WebView can preview images/PDFs itself, but for
 * everything else (Word, Excel, etc.) the user needs whatever app they already have installed.
 */
@CapacitorPlugin(name = "FileOpener")
public class FileOpenerPlugin extends Plugin {

    @PluginMethod
    public void open(PluginCall call) {
        try {
            Uri contentUri = writeToCache(call);
            if (contentUri == null) return; // writeToCache already rejected the call

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
            Uri contentUri = writeToCache(call);
            if (contentUri == null) return;

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

    /** Decodes call's base64 "data" into cacheDir/compartidos/<fileName> and returns its
     * FileProvider content URI, or rejects the call and returns null if "data" is missing. */
    private Uri writeToCache(PluginCall call) throws Exception {
        String base64 = call.getString("data");
        String fileName = call.getString("fileName", "archivo");

        if (base64 == null || base64.isEmpty()) {
            call.reject("Missing data");
            return null;
        }

        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        File dir = new File(getContext().getCacheDir(), "compartidos");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
        }

        return FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
    }
}
