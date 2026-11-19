package dev.pgr4port.android;

import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import java.io.File;
import org.libsdl.app.SDLActivity;

public final class GameActivity extends SDLActivity {
    @Override
    protected void onCreate(Bundle state) {
        try {
            Os.setenv("REX_APP_FOLDER", getFilesDir().getAbsolutePath(), true);
        } catch (ErrnoException error) {
            Log.w("PGR4", "Could not set the ReXGlue app folder", error);
        }
        super.onCreate(state);
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
            "--gpu_plugin=xenos",
            "--execute_unclipped_draw_vs_on_cpu=true"
        };
    }
}
