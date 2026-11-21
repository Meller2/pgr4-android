package dev.pgr4port.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class Pgr4Config {
    static final String PREFS_NAME = "pgr4_launcher";

    static final String PRESET_FAST = "fast";
    static final String PRESET_COMPATIBLE = "compatible";
    static final String PRESET_QUALITY = "quality";
    static final String PRESET_CUSTOM = "custom";

    private static final String KEY_GAME_PATH = "game_path";
    private static final String KEY_PRESET = "preset";
    private static final String KEY_DRIVER_ID = "driver_id";
    private static final String KEY_HUD_ENABLED = "hud_enabled";
    private static final String KEY_TOUCH_ENABLED = "touch_enabled";
    private static final String KEY_TOUCH_OPACITY = "touch_opacity";
    private static final String KEY_STEERING_MODE = "steering_mode";
    private static final String KEY_GYRO_SENSITIVITY = "gyro_sensitivity";
    private static final String KEY_UI_LANGUAGE = "ui_language";
    private static final String KEY_AUTO_CONFIGURED = "auto_configured";

    static final String STEER_STICK = "stick";
    static final String STEER_GYRO = "gyro";
    static final String STEER_BUTTONS = "buttons";

    // Engine TOML settings
    String driverDir = "";
    String driverName = "";
    boolean singleTile = true;
    boolean sinMsaa = true;
    boolean atalhoResolve = true;
    boolean vsync = false;
    boolean asyncSkipIncomplete = false;
    boolean bdaSharedMemory = true;
    int renderScale = 100;

    // Launcher & overlay settings
    String gamePath = "";
    String driverId = "";
    String preset = PRESET_FAST;
    boolean hudEnabled = true;
    boolean touchEnabled = true;
    int touchOpacity = 65; // percent 20..100
    String steeringMode = STEER_STICK;
    int gyroSensitivity = 135;
    String uiLanguage = "auto";
    boolean autoConfigured = false;

    private final Context context;
    private final SharedPreferences prefs;

    Pgr4Config(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
    }

    File tomlFile() {
        return new File(context.getFilesDir(), "pgr4_recompiled.toml");
    }

    void load() {
        gamePath = prefs.getString(KEY_GAME_PATH, "");
        driverId = prefs.getString(KEY_DRIVER_ID, "");
        preset = prefs.getString(KEY_PRESET, PRESET_FAST);
        hudEnabled = prefs.getBoolean(KEY_HUD_ENABLED, true);
        touchEnabled = prefs.getBoolean(KEY_TOUCH_ENABLED, true);
        touchOpacity = prefs.getInt(KEY_TOUCH_OPACITY, 65);
        steeringMode = prefs.getString(KEY_STEERING_MODE, STEER_STICK);
        gyroSensitivity = prefs.getInt(KEY_GYRO_SENSITIVITY, 135);
        uiLanguage = prefs.getString(KEY_UI_LANGUAGE, "auto");
        autoConfigured = prefs.getBoolean(KEY_AUTO_CONFIGURED, false);

        File toml = tomlFile();
        if (toml.isFile()) {
            readToml(toml);
            if (driverId.isEmpty() && !driverDir.isEmpty()) {
                driverId = new File(driverDir).getName();
            }
            if (!autoConfigured) {
                autoConfigured = true;
                savePrefsOnly();
            }
        }
    }

    void applyAutoProfileIfNeeded(GpuProbe.GpuInfo sysGpu, DriverManager drivers) {
        if (autoConfigured) return;

        if (sysGpu != null && sysGpu.isAdreno()) {
            // Check if a Turnip driver already exists (e.g. turnip-v39)
            DriverManager.DriverEntry firstCustom = drivers.findPreferredCustom();
            if (firstCustom != null) {
                driverId = firstCustom.id;
                driverDir = firstCustom.dirPath;
                driverName = firstCustom.libraryName;
            } else {
                driverId = "";
                driverDir = "";
                driverName = "";
            }
            applyPreset(PRESET_FAST, sysGpu);
        } else {
            // Mali / Dimensity / other non-Adreno GPU: always safe system driver
            driverId = "";
            driverDir = "";
            driverName = "";
            applyPreset(PRESET_COMPATIBLE, sysGpu);
        }
        autoConfigured = true;
        saveAll();
    }

    void applyPreset(String newPreset, GpuProbe.GpuInfo gpuInfo) {
        preset = newPreset;
        if (PRESET_FAST.equals(newPreset)) {
            singleTile = true;
            sinMsaa = true;
            atalhoResolve = true;
            vsync = false;
            asyncSkipIncomplete = false;
            renderScale = 100;
            bdaSharedMemory = (gpuInfo == null || !gpuInfo.ok || gpuInfo.bdaSupported);
        } else if (PRESET_COMPATIBLE.equals(newPreset)) {
            singleTile = true;
            sinMsaa = true;
            atalhoResolve = false;
            vsync = false;
            asyncSkipIncomplete = false;
            renderScale = 100;
            bdaSharedMemory = (gpuInfo != null && gpuInfo.ok && gpuInfo.bdaSupported);
        } else if (PRESET_QUALITY.equals(newPreset)) {
            singleTile = false;
            sinMsaa = false;
            atalhoResolve = true;
            vsync = false;
            asyncSkipIncomplete = false;
            renderScale = 100;
            bdaSharedMemory = (gpuInfo == null || !gpuInfo.ok || gpuInfo.bdaSupported);
        }
    }

    void setDriver(DriverManager.DriverEntry entry) {
        if (entry == null || entry.isSystem) {
            driverId = "";
            driverDir = "";
            driverName = "";
        } else {
            driverId = entry.id;
            driverDir = entry.dirPath;
            driverName = entry.libraryName;
        }
    }

    void savePrefsOnly() {
        prefs.edit()
                .putString(KEY_GAME_PATH, gamePath)
                .putString(KEY_DRIVER_ID, driverId)
                .putString(KEY_PRESET, preset)
                .putBoolean(KEY_HUD_ENABLED, hudEnabled)
                .putBoolean(KEY_TOUCH_ENABLED, touchEnabled)
                .putInt(KEY_TOUCH_OPACITY, touchOpacity)
                .putString(KEY_STEERING_MODE, steeringMode)
                .putInt(KEY_GYRO_SENSITIVITY, gyroSensitivity)
                .putString(KEY_UI_LANGUAGE, uiLanguage)
                .putBoolean(KEY_AUTO_CONFIGURED, autoConfigured)
                .commit();
    }

    void saveAll() {
        savePrefsOnly();
        writeToml(tomlFile());
    }

    private void readToml(File file) {
        Map<String, String> values = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int hash = line.indexOf('#');
                if (hash >= 0) {
                    line = line.substring(0, hash);
                }
                line = line.trim();
                if (line.isEmpty()) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                values.put(key, unquote(val));
            }
        } catch (IOException ignored) {
            return;
        }

        if (values.containsKey("android_gpu_driver_dir")) {
            driverDir = values.get("android_gpu_driver_dir");
        }
        if (values.containsKey("android_gpu_driver_name")) {
            driverName = values.get("android_gpu_driver_name");
        }
        if (values.containsKey("pgr4_single_tile")) {
            singleTile = parseBool(values.get("pgr4_single_tile"), singleTile);
        }
        if (values.containsKey("gpu_sin_msaa")) {
            sinMsaa = parseBool(values.get("gpu_sin_msaa"), sinMsaa);
        }
        if (values.containsKey("gpu_atalho_resolve")) {
            atalhoResolve = parseBool(values.get("gpu_atalho_resolve"), atalhoResolve);
        }
        if (values.containsKey("vsync")) {
            vsync = parseBool(values.get("vsync"), vsync);
        }
        if (values.containsKey("vulkan_async_skip_incomplete_frames")) {
            asyncSkipIncomplete = parseBool(values.get("vulkan_async_skip_incomplete_frames"), asyncSkipIncomplete);
        }
        if (values.containsKey("vulkan_bda_shared_memory")) {
            bdaSharedMemory = parseBool(values.get("vulkan_bda_shared_memory"), bdaSharedMemory);
        }
        if (values.containsKey("gpu_escala_render")) {
            renderScale = parseInt(values.get("gpu_escala_render"), renderScale);
        }
    }

    private void writeToml(File file) {
        // Preserve any extra/unknown keys from an existing file (for developer profiling flags)
        Map<String, String> extras = new LinkedHashMap<>();
        if (file.isFile()) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int hash = line.indexOf('#');
                    String clean = (hash >= 0 ? line.substring(0, hash) : line).trim();
                    if (clean.isEmpty()) continue;
                    int eq = clean.indexOf('=');
                    if (eq <= 0) continue;
                    String key = clean.substring(0, eq).trim();
                    String val = clean.substring(eq + 1).trim();
                    if (!isManagedKey(key)) {
                        extras.put(key, val);
                    }
                }
            } catch (IOException ignored) {
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# PGR4 Android Launcher Config (LF line endings)\n");
        if (!driverName.isEmpty() && !driverDir.isEmpty()) {
            sb.append("android_gpu_driver_name = \"").append(escapeToml(driverName)).append("\"\n");
            sb.append("android_gpu_driver_dir = \"").append(escapeToml(driverDir)).append("\"\n");
        } else {
            sb.append("android_gpu_driver_name = \"\"\n");
            sb.append("android_gpu_driver_dir = \"\"\n");
        }
        sb.append("pgr4_single_tile = ").append(singleTile).append('\n');
        sb.append("gpu_sin_msaa = ").append(sinMsaa).append('\n');
        sb.append("gpu_atalho_resolve = ").append(atalhoResolve).append('\n');
        sb.append("vsync = ").append(vsync).append('\n');
        sb.append("vulkan_async_skip_incomplete_frames = ").append(asyncSkipIncomplete).append('\n');
        sb.append("vulkan_bda_shared_memory = ").append(bdaSharedMemory).append('\n');
        sb.append("gpu_escala_render = ").append(renderScale).append('\n');
        for (Map.Entry<String, String> entry : extras.entrySet()) {
            sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private static boolean isManagedKey(String key) {
        return "android_gpu_driver_name".equals(key)
                || "android_gpu_driver_dir".equals(key)
                || "pgr4_single_tile".equals(key)
                || "gpu_sin_msaa".equals(key)
                || "gpu_atalho_resolve".equals(key)
                || "vsync".equals(key)
                || "vulkan_async_skip_incomplete_frames".equals(key)
                || "vulkan_bda_shared_memory".equals(key)
                || "gpu_escala_render".equals(key);
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String escapeToml(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean parseBool(String v, boolean fallback) {
        if ("true".equalsIgnoreCase(v)) return true;
        if ("false".equalsIgnoreCase(v)) return false;
        return fallback;
    }

    private static int parseInt(String v, int fallback) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
