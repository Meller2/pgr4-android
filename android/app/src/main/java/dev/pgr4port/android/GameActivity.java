package dev.pgr4port.android;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.view.ViewGroup;
import java.io.File;
import org.libsdl.app.SDLActivity;

public final class GameActivity extends SDLActivity {
    private TouchPad touchPad;
    private PerformanceHud performanceHud;

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
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!mBrokenLibraries) {
            touchPad = new TouchPad(this);
            addContentView(touchPad, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            performanceHud = new PerformanceHud(this);
            FrameLayout.LayoutParams hudLayout = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            hudLayout.topMargin = Math.round(8 * getResources().getDisplayMetrics().density);
            addContentView(performanceHud, hudLayout);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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
            touchPad.reset();
        }
        super.onPause();
    }

    @Override
    protected String[] getArguments() {
        File gameRoot = new File(getFilesDir(), "game");
        File externalRoot = getExternalFilesDir(null);
        if (!new File(gameRoot, "default.xex").canRead() && externalRoot != null) {
            File externalGame = new File(externalRoot, "game");
            if (new File(externalGame, "default.xex").canRead()) {
                gameRoot = externalGame;
            }
        }
        return new String[] {
            "--game_data_root=" + gameRoot.getAbsolutePath(),
            "--user_data_root=" + getFilesDir().getAbsolutePath(),
            "--android_native_lib_dir=" + getApplicationInfo().nativeLibraryDir,
            "--android_tmp_dir=" + getCacheDir().getAbsolutePath(),
            "--gpu_plugin=xenos",
            "--execute_unclipped_draw_vs_on_cpu=true"
        };
    }
}
