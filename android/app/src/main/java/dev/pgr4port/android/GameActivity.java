package dev.pgr4port.android;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import org.libsdl.app.SDLActivity;

public final class GameActivity extends SDLActivity {
    static final String EXTRA_GAME_PATH = "dev.pgr4port.android.GAME_PATH";
    static final String EXTRA_FROM_LAUNCHER = "dev.pgr4port.android.FROM_LAUNCHER";

    private TouchPad touchPad;
    private PerformanceHud performanceHud;
    private boolean launchedFromLauncher;

    @Override
    public void setOrientationBis(int width, int height, boolean resizable, String hint) {
        // SDL otherwise allows portrait for its resizable window, overriding the manifest.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    @Override
    protected void onCreate(Bundle state) {
        try {
            Os.setenv("REX_APP_FOLDER", getFilesDir().getAbsolutePath(), true);
        } catch (ErrnoException error) {
            Log.w("PGR4", "Could not set the ReXGlue app folder", error);
        }
        Intent intent = getIntent();
        launchedFromLauncher = intent != null && intent.getBooleanExtra(EXTRA_FROM_LAUNCHER, false);

        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!mBrokenLibraries) {
            Pgr4Config cfg = new Pgr4Config(this);
            if (cfg.touchEnabled) {
                touchPad = new TouchPad(this, cfg);
                addContentView(touchPad, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            if (cfg.hudEnabled) {
                performanceHud = new PerformanceHud(this);
                FrameLayout.LayoutParams hudLayout = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                hudLayout.topMargin = Math.round(8 * getResources().getDisplayMetrics().density);
                addContentView(performanceHud, hudLayout);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (touchPad != null) {
            touchPad.resume();
        }
        if (performanceHud != null) {
            performanceHud.resume();
        }
    }

    @Override
    protected void onPause() {
        if (performanceHud != null) {
            performanceHud.pause();
        }
        if (touchPad != null) {
            touchPad.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (launchedFromLauncher && isFinishing()) {
            // Ensure ":game" process tears down cleanly so driver/config changes apply on next launch.
            Process.killProcess(Process.myPid());
        }
    }

    @Override
    protected String[] getArguments() {
        Intent intent = getIntent();
        String explicit = intent != null ? intent.getStringExtra(EXTRA_GAME_PATH) : null;
        Pgr4Config cfg = new Pgr4Config(this);
        String gameRoot = (explicit != null && !explicit.isEmpty())
                ? explicit
                : GameSourceHelper.resolveEffectiveGamePath(this, cfg.gamePath);
        return new String[] {
            "--game_data_root=" + gameRoot,
            "--user_data_root=" + getFilesDir().getAbsolutePath(),
            "--android_native_lib_dir=" + getApplicationInfo().nativeLibraryDir,
            "--android_tmp_dir=" + getCacheDir().getAbsolutePath(),
            "--gpu_plugin=xenos",
            "--execute_unclipped_draw_vs_on_cpu=true"
        };
    }
}
