package dev.pgr4port.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.PowerManager;
import android.widget.TextView;
import java.util.Locale;

final class PerformanceHud extends TextView {
    private static final long REFRESH_MS = 1000;
    private static final String EXPANDED = "performance_hud_expanded";

    private final SharedPreferences preferences;
    private final PowerManager power;
    private final Runnable refresh = this::refresh;
    private boolean expanded;
    private boolean resumed;

    PerformanceHud(Context context) {
        super(context);
        preferences = context.getSharedPreferences("ui", Context.MODE_PRIVATE);
        power = context.getSystemService(PowerManager.class);
        expanded = preferences.getBoolean(EXPANDED, true);
        setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        setTextSize(12);
        setTextColor(Color.WHITE);
        setBackgroundColor(0xd0202020);
        float density = getResources().getDisplayMetrics().density;
        int horizontal = Math.round(10 * density);
        int vertical = Math.round(6 * density);
        setPadding(horizontal, vertical, horizontal, vertical);
        setOnClickListener(view -> {
            expanded = !expanded;
            preferences.edit().putBoolean(EXPANDED, expanded).apply();
            resume();
        });
    }

    void resume() {
        removeCallbacks(refresh);
        resumed = true;
        refresh();
    }

    void pause() {
        resumed = false;
        removeCallbacks(refresh);
    }

    private void refresh() {
        if (!resumed) {
            return;
        }
        if (!expanded) {
            setText("FPS");
            return;
        }
        float fps = nativeGuestFps();
        String timing = fps > 0 ? String.format(Locale.ROOT, "Game %.1f FPS  |  %.1f ms avg",
                fps, 1000.0f / fps) : "Game FPS: waiting";
        if (Build.VERSION.SDK_INT >= 29 && power != null) {
            timing += "  |  Heat " + power.getCurrentThermalStatus() + "/6";
        }
        setText(timing);
        postDelayed(refresh, REFRESH_MS);
    }

    private static native float nativeGuestFps();
}
