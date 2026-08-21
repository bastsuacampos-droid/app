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
        String base64 = call.getString("data");
        String fileName = call.getString("fileName", "archivo");
        String mimeType = call.getString("mimeType", "*/*");

        if (base64 == null || base64.isEmpty()) {
            call.reject("Missing data");
            return;
        }

        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            File dir = new File(getContext().getCacheDir(), "compartidos");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
            }

            Uri contentUri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(contentUri, mimeType);
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(viewIntent, "Abrir con");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooser);

            call.resolve(new JSObject());
        } catch (Exception e) {
            call.reject("No se pudo abrir el archivo: " + e.getMessage(), e);
        }
    }
}
