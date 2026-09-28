package com.sts.fastbrowser;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Locale;

public class PdfShareProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) return "application/octet-stream";
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        return "application/pdf";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String name = uri.getLastPathSegment();
        File f = safeFile(uri);
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{name, f != null && f.exists() ? f.length() : 0});
        return c;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = safeFile(uri);
        if (f == null || !f.exists()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File safeFile(Uri uri) {
        try {
            List<String> segments = uri.getPathSegments();
            String folder = (segments != null && segments.size() >= 2 && "image".equals(segments.get(0)))
                    ? "image" : "pdf";
            String name = uri.getLastPathSegment();
            File dir = new File(getContext().getCacheDir(), folder);
            File f = new File(dir, name == null ? "" : name);
            if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) return null;
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
