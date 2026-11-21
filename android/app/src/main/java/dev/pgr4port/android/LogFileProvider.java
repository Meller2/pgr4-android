package dev.pgr4port.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lightweight ContentProvider for sharing game logs from app-private files/logs
 * without requiring AndroidX FileProvider.
 */
public final class LogFileProvider extends ContentProvider {
    static final String AUTHORITY = "dev.pgr4port.android.logs";

    static Uri uriForLog(File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = resolveFile(uri);
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
                row[i] = file != null ? file.getName() : "pgr4.log";
            } else if (OpenableColumns.SIZE.equals(cols[i])) {
                row[i] = file != null ? file.length() : 0L;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = resolveFile(uri);
        if (file == null || !file.isFile()) {
            throw new FileNotFoundException("Log file not found");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File resolveFile(Uri uri) {
        if (getContext() == null || uri == null) return null;
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("..") || name.contains("/")) return null;
        File logsDir = new File(getContext().getFilesDir(), "logs");
        return new File(logsDir, name);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
