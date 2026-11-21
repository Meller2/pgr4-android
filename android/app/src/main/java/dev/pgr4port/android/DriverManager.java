package dev.pgr4port.android;

import android.content.Context;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONObject;

final class DriverManager {
    static final class DriverEntry {
        final String id;
        final String name;
        final String version;
        final String description;
        final String dirPath;
        final String libraryName;
        final boolean isSystem;

        DriverEntry(String id, String name, String version, String description,
                    String dirPath, String libraryName, boolean isSystem) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.description = description;
            this.dirPath = dirPath;
            this.libraryName = libraryName;
            this.isSystem = isSystem;
        }
    }

    private final Context context;

    DriverManager(Context context) {
        this.context = context.getApplicationContext();
    }

    File gpuRootDir() {
        return new File(context.getFilesDir(), "gpu");
    }

    List<DriverEntry> listDrivers(LauncherStrings s) {
        List<DriverEntry> list = new ArrayList<>();
        list.add(new DriverEntry(
                "", s.systemDriverName(), "Android Vulkan", s.systemDriverDesc(),
                "", "", true));

        File root = gpuRootDir();
        File[] dirs = root.listFiles();
        if (dirs == null) return list;

        Arrays.sort(dirs, Comparator.comparing(File::getName));
        for (File dir : dirs) {
            if (!dir.isDirectory()) continue;
            DriverEntry entry = inspectDriverDir(dir);
            if (entry != null) {
                list.add(entry);
            }
        }
        return list;
    }

    DriverEntry findPreferredCustom() {
        File root = gpuRootDir();
        File[] dirs = root.listFiles();
        if (dirs == null) return null;
        Arrays.sort(dirs, Comparator.comparing(File::getName));
        for (File dir : dirs) {
            if (!dir.isDirectory()) continue;
            DriverEntry entry = inspectDriverDir(dir);
            if (entry != null) return entry;
        }
        return null;
    }

    DriverEntry findByIdOrPath(String driverId, String driverDir, LauncherStrings s) {
        List<DriverEntry> all = listDrivers(s);
        for (DriverEntry entry : all) {
            if (!entry.isSystem) {
                if ((driverId != null && !driverId.isEmpty() && entry.id.equals(driverId))
                        || (driverDir != null && !driverDir.isEmpty() && entry.dirPath.equals(driverDir))) {
                    return entry;
                }
            }
        }
        return all.get(0);
    }

    private DriverEntry inspectDriverDir(File dir) {
        String id = dir.getName();
        String name = id;
        String version = "";
        String description = "";
        String libName = "";

        File meta = new File(dir, "meta.json");
        if (meta.isFile()) {
            try {
                String text = readFileUtf8(meta);
                JSONObject json = new JSONObject(text);
                name = json.optString("name", id);
                version = json.optString("driverVersion", json.optString("version", ""));
                description = json.optString("description", "");
                libName = json.optString("libraryName", "");
            } catch (Exception ignored) {
            }
        }

        if (libName.isEmpty() || !new File(dir, libName).isFile()) {
            libName = findSoFile(dir);
        }
        if (libName.isEmpty()) {
            return null;
        }
        return new DriverEntry(id, name, version, description, dir.getAbsolutePath(), libName, false);
    }

    private static String findSoFile(File dir) {
        File freedreno = new File(dir, "libvulkan_freedreno.so");
        if (freedreno.isFile()) {
            return freedreno.getName();
        }
        File[] files = dir.listFiles();
        if (files == null) return "";
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".so")) {
                return f.getName();
            }
        }
        return "";
    }

    DriverEntry installFromZip(Uri uri, String displayName) throws IOException {
        String baseName = displayName != null ? displayName : "custom-driver";
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) baseName = baseName.substring(0, dot);
        String cleanId = baseName.replaceAll("[^a-zA-Z0-9._-]+", "_");
        if (cleanId.isEmpty()) cleanId = "driver";

        File root = gpuRootDir();
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("Cannot create gpu directory");
        }
        File targetDir = new File(root, cleanId);
        int suffix = 2;
        while (targetDir.exists()) {
            targetDir = new File(root, cleanId + "-" + suffix++);
        }
        if (!targetDir.mkdirs()) {
            throw new IOException("Cannot create target driver folder");
        }

        boolean hasSo = false;
        try (InputStream rawIn = context.getContentResolver().openInputStream(uri)) {
            if (rawIn == null) {
                throw new IOException("Cannot open selected archive");
            }
            try (ZipInputStream zis = new ZipInputStream(rawIn)) {
                byte[] buffer = new byte[16384];
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        zis.closeEntry();
                        continue;
                    }
                    String entryName = entry.getName().replace('\\', '/');
                    String fileName = new File(entryName).getName();
                    if (fileName.isEmpty() || fileName.contains("..")) {
                        zis.closeEntry();
                        continue;
                    }
                    // Extract .so and meta.json into driver root so AdrenoTools finds libraryName directly
                    if (fileName.endsWith(".so") || "meta.json".equalsIgnoreCase(fileName)) {
                        File outFile = new File(targetDir, fileName);
                        if (!outFile.getCanonicalPath().startsWith(targetDir.getCanonicalPath() + File.separator)) {
                            throw new IOException("Unsafe zip entry: " + entryName);
                        }
                        try (FileOutputStream out = new FileOutputStream(outFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                out.write(buffer, 0, len);
                            }
                        }
                        if (fileName.endsWith(".so")) {
                            hasSo = true;
                        }
                    }
                    zis.closeEntry();
                }
            }
        } catch (IOException e) {
            deleteRecursively(targetDir);
            throw e;
        }

        if (!hasSo) {
            deleteRecursively(targetDir);
            throw new IOException("No .so Vulkan driver library found inside archive");
        }

        DriverEntry installed = inspectDriverDir(targetDir);
        if (installed == null) {
            deleteRecursively(targetDir);
            throw new IOException("Driver package is missing a usable .so library");
        }
        return installed;
    }

    boolean deleteDriver(DriverEntry entry) {
        if (entry == null || entry.isSystem) return false;
        return deleteRecursively(new File(entry.dirPath));
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return false;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }

    private static String readFileUtf8(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
