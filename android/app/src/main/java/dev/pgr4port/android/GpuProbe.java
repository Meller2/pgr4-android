package dev.pgr4port.android;

import android.content.Context;
import android.os.Build;
import android.util.Log;

final class GpuProbe {
    private static final String TAG = "PGR4Probe";
    private static volatile boolean loaded;

    static final class GpuInfo {
        final boolean ok;
        final String vendorId;
        final String deviceName;
        final String apiVersion;
        final String driverVersion;
        final boolean bdaSupported;
        final String error;

        GpuInfo(boolean ok, String vendorId, String deviceName, String apiVersion,
                String driverVersion, boolean bdaSupported, String error) {
            this.ok = ok;
            this.vendorId = vendorId;
            this.deviceName = deviceName;
            this.apiVersion = apiVersion;
            this.driverVersion = driverVersion;
            this.bdaSupported = bdaSupported;
            this.error = error;
        }

        boolean isAdreno() {
            return "0x5143".equalsIgnoreCase(vendorId)
                    || (deviceName != null && deviceName.toLowerCase().contains("adreno"));
        }

        boolean isMali() {
            return "0x13B5".equalsIgnoreCase(vendorId)
                    || (deviceName != null && deviceName.toLowerCase().contains("mali"));
        }

        static GpuInfo parse(String raw) {
            if (raw == null || raw.isEmpty()) {
                return new GpuInfo(false, "", "", "", "", false, "Empty probe output");
            }
            String[] parts = raw.split("\\|", -1);
            if (parts.length < 7) {
                return new GpuInfo(false, "", "", "", "", false, raw);
            }
            boolean ok = "1".equals(parts[0]);
            boolean bda = "1".equals(parts[5]);
            return new GpuInfo(ok, parts[1], parts[2], parts[3], parts[4], bda, parts[6]);
        }
    }

    static String deviceModelLabel() {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.trim() : "";
        String model = Build.MODEL != null ? Build.MODEL.trim() : "";
        if (!manufacturer.isEmpty() && !model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            return manufacturer + " " + model;
        }
        return model.isEmpty() ? "Android Device" : model;
    }

    static synchronized boolean ensureLoaded() {
        if (loaded) return true;
        try {
            System.loadLibrary("c++_shared");
        } catch (UnsatisfiedLinkError ignored) {
        }
        try {
            System.loadLibrary("rex_android_gpu_driver");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Failed to load rex_android_gpu_driver", e);
        }
        try {
            System.loadLibrary("pgr4_probe");
            loaded = true;
            return true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load pgr4_probe", e);
            return false;
        }
    }

    static GpuInfo probeNow(Context context, String driverDir, String driverName) {
        if (!ensureLoaded()) {
            return new GpuInfo(false, "", "", "", "", false, "Native probe library unavailable");
        }
        String hooksDir = context.getApplicationInfo().nativeLibraryDir;
        String tmpDir = context.getCacheDir().getAbsolutePath();
        String raw = nativeProbeVulkan(
                hooksDir != null ? hooksDir : "",
                tmpDir != null ? tmpDir : "",
                driverDir != null ? driverDir : "",
                driverName != null ? driverName : "");
        return GpuInfo.parse(raw);
    }

    private static native String nativeProbeVulkan(
            String hooksDir, String tmpDir, String driverDir, String driverName);
}
