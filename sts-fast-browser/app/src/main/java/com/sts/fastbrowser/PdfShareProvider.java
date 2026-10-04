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
        if (n.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (n.endsWith(".xls")) return "application/vnd.ms-excel";
        if (n.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (n.endsWith(".doc")) return "application/msword";
        if (n.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (n.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (n.endsWith(".csv")) return "text/csv";
        if (n.endsWith(".txt")) return "text/plain";
        if (n.endsWith(".rtf")) return "application/rtf";
        if (n.endsWith(".odt")) return "application/vnd.oasis.opendocument.text";
        if (n.endsWith(".ods")) return "application/vnd.oasis.opendocument.spreadsheet";
        if (n.endsWith(".odp")) return "application/vnd.oasis.opendocument.presentation";
        if (n.endsWith(".ofd")) return "application/ofd";
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
            String folder = "pdf";
            if (segments != null && segments.size() >= 2) {
                String first = segments.get(0);
                if ("image".equals(first)) folder = "image";
                else if ("office".equals(first)) folder = "office";
            }
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
