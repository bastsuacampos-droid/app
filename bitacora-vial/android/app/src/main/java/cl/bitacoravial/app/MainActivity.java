package cl.bitacoravial.app;

import android.os.Bundle;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AppUpdaterPlugin.class);
        registerPlugin(FileOpenerPlugin.class);
        super.onCreate(savedInstanceState);
        setupSafeAreaFallback();
    }

    /**
     * Belt-and-suspenders alongside Capacitor's own SystemBars auto-injection
     * (--safe-area-inset-*): that mechanism is brand new (Capacitor 8) and its behavior branches
     * on the installed WebView's build number, so it can't be fully trusted on every device. We
     * also read the real window insets ourselves and feed --native-safe-top/--native-safe-bottom;
     * global.css takes the max of every source, so whichever one actually reports a value wins.
     */
    private void setupSafeAreaFallback() {
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            float density = getResources().getDisplayMetrics().density;
            int topDp = Math.round(bars.top / density);
            int bottomDp = Math.round(bars.bottom / density);
            if (bridge != null && bridge.getWebView() != null) {
                String script = "document.documentElement.style.setProperty('--native-safe-top','"
                    + topDp + "px');document.documentElement.style.setProperty('--native-safe-bottom','" + bottomDp + "px');";
                bridge.getWebView().evaluateJavascript(script, null);
            }
            return insets;
        });
    }
}
