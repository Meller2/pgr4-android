package dev.pgr4port.android;

import java.io.File;
import org.libsdl.app.SDLActivity;

public final class GameActivity extends SDLActivity {
    @Override
    protected String[] getArguments() {
        File appStorage = getExternalFilesDir(null);
        if (appStorage == null) {
            appStorage = getFilesDir();
        }
        return new String[] {
            "--game_data_root=" + new File(appStorage, "game").getAbsolutePath(),
            "--user_data_root=" + getFilesDir().getAbsolutePath(),
            "--gpu_plugin=xenos",
            "--execute_unclipped_draw_vs_on_cpu=true"
        };
    }
}
