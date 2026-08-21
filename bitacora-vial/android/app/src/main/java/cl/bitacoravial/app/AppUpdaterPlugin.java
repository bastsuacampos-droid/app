package cl.bitacoravial.app;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;

/**
 * Downloads a newer APK (from a GitHub release, see src/lib/appUpdater.ts) via Android's
 * DownloadManager and hands it to the system package installer. Android never allows a fully
 * silent install for an app installed outside Play Store, so the OS install-confirmation dialog
 * is unavoidable — this only automates the "find + download" half.
 */
@CapacitorPlugin(name = "AppUpdater")
public class AppUpdaterPlugin extends Plugin {

    private long activeDownloadId = -1;
    private File activeDownloadFile;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressPoller;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id != activeDownloadId) return;
            stopProgressPolling();
            handleDownloadComplete(id);
        }
    };

    @Override
    public void load() {
        super.load();
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getContext().registerReceiver(downloadReceiver, filter);
        }
    }

    @PluginMethod
    public void canInstallPackages(PluginCall call) {
        boolean can = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            can = getContext().getPackageManager().canRequestPackageInstalls();
        }
        JSObject ret = new JSObject();
        ret.put("value", can);
        call.resolve(ret);
    }

    @PluginMethod
    public void requestInstallPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        }
        call.resolve();
    }

    @PluginMethod
    public void downloadAndInstall(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("Missing url");
            return;
        }
        String fileName = call.getString("fileName", "update.apk");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getContext().getPackageManager().canRequestPackageInstalls()) {
            call.reject("Install permission not granted");
            return;
        }

        try {
            File dir = getContext().getExternalFilesDir(null);
            if (dir == null) dir = getContext().getCacheDir();
            File dest = new File(dir, fileName);
            if (dest.exists()) dest.delete();
            activeDownloadFile = dest;

            DownloadManager manager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("Bitácora Vial");
            request.setDescription("Descargando actualización");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(getContext(), null, fileName);
            request.setMimeType("application/vnd.android.package-archive");

            activeDownloadId = manager.enqueue(request);
            startProgressPolling(manager);

            JSObject ret = new JSObject();
            ret.put("started", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Download failed: " + e.getMessage(), e);
        }
    }

    private void startProgressPolling(DownloadManager manager) {
        stopProgressPolling();
        progressPoller = new Runnable() {
            @Override
            public void run() {
                if (activeDownloadId == -1) return;
                long id = activeDownloadId;
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
                try (Cursor cursor = manager.query(query)) {
                    if (cursor == null || !cursor.moveToFirst()) {
                        // The row is gone before we ever saw it finish — treat as a failure
                        // instead of polling a download that will never report back.
                        stopProgressPolling();
                        handleDownloadComplete(id);
                        return;
                    }

                    int bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                    int totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                    long bytes = bytesIdx >= 0 ? cursor.getLong(bytesIdx) : 0;
                    long total = totalIdx >= 0 ? cursor.getLong(totalIdx) : 0;
                    if (total > 0) {
                        JSObject data = new JSObject();
                        data.put("percent", (double) bytes / (double) total * 100.0);
                        notifyListeners("downloadProgress", data);
                    }

                    // Don't rely solely on the ACTION_DOWNLOAD_COMPLETE broadcast to know the
                    // download finished — some devices (aggressive battery/background restrictions
                    // on several OEM Android builds) never deliver it, leaving the download
                    // visibly at 100% forever with no installer ever launching. Polling the status
                    // column directly here means completion is detected either way.
                    int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = statusIdx >= 0 ? cursor.getInt(statusIdx) : -1;
                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        stopProgressPolling();
                        handleDownloadComplete(id);
                        return;
                    }
                }
                progressHandler.postDelayed(this, 400);
            }
        };
        progressHandler.post(progressPoller);
    }

    private void stopProgressPolling() {
        if (progressPoller != null) {
            progressHandler.removeCallbacks(progressPoller);
            progressPoller = null;
        }
    }

    private void handleDownloadComplete(long downloadId) {
        DownloadManager manager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = manager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) return;
            int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = statusIdx >= 0 ? cursor.getInt(statusIdx) : DownloadManager.STATUS_FAILED;
            if (status == DownloadManager.STATUS_SUCCESSFUL && activeDownloadFile != null && activeDownloadFile.exists()) {
                launchInstaller(activeDownloadFile);
            } else {
                JSObject data = new JSObject();
                data.put("message", "La descarga falló (status " + status + ")");
                notifyListeners("downloadError", data);
            }
        } finally {
            activeDownloadId = -1;
            activeDownloadFile = null;
        }
    }

    private void launchInstaller(File file) {
        Uri contentUri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }
}
