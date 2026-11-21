package dev.pgr4port.android;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

final class GameSourceHelper {
    private static final long[] XGD_OFFSETS = {
        0x00000000L, 0x0000FB20L, 0x00020600L, 0x02080000L, 0x0FD90000L
    };
    private static final long SECTOR32_BYTES = 32L * 2048L;
    private static final byte[] XBOX_MEDIA_MAGIC =
            "MICROSOFT*XBOX*MEDIA".getBytes(StandardCharsets.US_ASCII);

    static final class ValidationResult {
        final boolean valid;
        final boolean isIso;
        final String resolvedPath;
        final long sizeBytes;
        final String detail;

        ValidationResult(boolean valid, boolean isIso, String resolvedPath,
                         long sizeBytes, String detail) {
            this.valid = valid;
            this.isIso = isIso;
            this.resolvedPath = resolvedPath;
            this.sizeBytes = sizeBytes;
            this.detail = detail;
        }
    }

    static boolean hasStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    static Intent createStoragePermissionIntent(Context context) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                return intent;
            } catch (Exception e) {
                return new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            }
        }
        return null;
    }

    static String resolveEffectiveGamePath(Context context, String configuredPath) {
        if (configuredPath != null && !configuredPath.isEmpty()) {
            File f = new File(configuredPath);
            if (f.exists()) {
                return f.getAbsolutePath();
            }
        }
        File internal = new File(context.getFilesDir(), "game");
        if (new File(internal, "default.xex").canRead()) {
            return internal.getAbsolutePath();
        }
        File externalRoot = context.getExternalFilesDir(null);
        if (externalRoot != null) {
            File externalGame = new File(externalRoot, "game");
            if (new File(externalGame, "default.xex").canRead()) {
                return externalGame.getAbsolutePath();
            }
        }
        return configuredPath != null && !configuredPath.isEmpty()
                ? configuredPath
                : internal.getAbsolutePath();
    }

    static ValidationResult validateGameSource(Context context, String path, LauncherStrings s) {
        String effective = resolveEffectiveGamePath(context, path);
        File target = new File(effective);
        if (!target.exists()) {
            return new ValidationResult(false, false, effective, 0, s.gameInvalidLabel());
        }
        if (target.isDirectory()) {
            File xex = new File(target, "default.xex");
            if (!xex.isFile()) {
                xex = new File(target, "DEFAULT.XEX");
            }
            if (xex.isFile() && xex.canRead()) {
                return new ValidationResult(true, false, target.getAbsolutePath(),
                        xex.length(), s.gameFolderMode() + " · default.xex");
            }
            return new ValidationResult(false, false, target.getAbsolutePath(), 0,
                    s.t("default.xex not found in folder", "В папке не найден файл default.xex"));
        }
        if (target.isFile()) {
            String lower = target.getName().toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".iso")) {
                return new ValidationResult(false, true, target.getAbsolutePath(),
                        target.length(), s.t("Expected a .iso file", "Нужен файл с расширением .iso"));
            }
            if (!target.canRead()) {
                return new ValidationResult(false, true, target.getAbsolutePath(),
                        target.length(), s.t("Cannot read file (check storage access)", "Нет доступа к файлу (проверьте права)"));
            }
            if (verifyXboxIsoMagic(target)) {
                return new ValidationResult(true, true, target.getAbsolutePath(),
                        target.length(), s.gameIsoMode() + " · " + formatSize(target.length()));
            }
            return new ValidationResult(false, true, target.getAbsolutePath(),
                    target.length(), s.t("Not a valid Xbox 360 XISO/XGD image", "Файл не похож на образ Xbox 360 (XISO/XGD)"));
        }
        return new ValidationResult(false, false, effective, 0, s.gameInvalidLabel());
    }

    private static boolean verifyXboxIsoMagic(File isoFile) {
        long size = isoFile.length();
        byte[] buf = new byte[XBOX_MEDIA_MAGIC.length];
        try (RandomAccessFile raf = new RandomAccessFile(isoFile, "r")) {
            for (long base : XGD_OFFSETS) {
                long offset = base + SECTOR32_BYTES;
                if (offset + buf.length > size) continue;
                raf.seek(offset);
                raf.readFully(buf);
                if (Arrays.equals(buf, XBOX_MEDIA_MAGIC)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    static String resolveTreeUriToPath(Uri treeUri) {
        if (treeUri == null) return null;
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            return docIdToPath(docId);
        } catch (Exception e) {
            return null;
        }
    }

    static String resolveDocumentUriToPath(Context context, Uri docUri) {
        if (docUri == null) return null;
        if ("file".equalsIgnoreCase(docUri.getScheme())) {
            return docUri.getPath();
        }
        try {
            if (DocumentsContract.isDocumentUri(context, docUri)) {
                String docId = DocumentsContract.getDocumentId(docUri);
                String direct = docIdToPath(docId);
                if (direct != null) return direct;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String docIdToPath(String docId) {
        if (docId == null) return null;
        int colon = docId.indexOf(':');
        if (colon < 0) return null;
        String type = docId.substring(0, colon);
        String rel = docId.substring(colon + 1);
        if ("primary".equalsIgnoreCase(type)) {
            String base = Environment.getExternalStorageDirectory().getAbsolutePath();
            return rel.isEmpty() ? base : base + "/" + rel;
        }
        if ("raw".equalsIgnoreCase(type) && rel.startsWith("/")) {
            return rel;
        }
        if (!type.isEmpty()) {
            String candidate = "/storage/" + type + (rel.isEmpty() ? "" : "/" + rel);
            if (new File(candidate).exists()) {
                return candidate;
            }
        }
        return null;
    }

    static String queryDisplayName(Context context, Uri uri) {
        if (uri == null) return "";
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "";
    }

    static File findLatestLogFile(Context context) {
        File logsDir = new File(context.getFilesDir(), "logs");
        File[] files = logsDir.listFiles();
        if (files == null || files.length == 0) return null;
        File latest = null;
        for (File f : files) {
            if (f.isFile() && (latest == null || f.lastModified() > latest.lastModified())) {
                latest = f;
            }
        }
        return latest;
    }

    static long clearShaderCache(Context context) {
        File cacheDir = new File(context.getFilesDir(), "cache");
        long freed = dirSize(cacheDir);
        deleteChildren(cacheDir);
        return freed;
    }

    static long dirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();
        long sum = 0;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File c : children) sum += dirSize(c);
        }
        return sum;
    }

    private static void deleteChildren(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory()) deleteChildren(c);
            c.delete();
        }
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.ROOT, "%.2f GB", gb);
    }
}
