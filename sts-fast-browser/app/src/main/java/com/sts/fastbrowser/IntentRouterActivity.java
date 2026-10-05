package com.sts.fastbrowser;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Single Android entry-point for Open with / Share / Edit.
 * Keeps only one STS Fast Browser target in Android choosers and then routes
 * the received content to the app's built-in viewers.
 */
public class IntentRouterActivity extends Activity {
    private static final int MAX_TEXT_PREVIEW = 256 * 1024;
    private static final int MAX_BINARY_PREVIEW = 4096;
    private static final int MAX_ZIP_ENTRIES = 250;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIncoming(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);
    }

    private void handleIncoming(Intent intent) {
        if (intent == null) {
            showMessage("No file received.");
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> uris = extractMultipleUris(intent);
            if (uris.isEmpty()) {
                showSharedText(intent);
            } else if (uris.size() == 1) {
                openSingle(uris.get(0), guessMime(intent, uris.get(0)), action);
            } else {
                showMultiple(uris, intent.getType());
            }
            return;
        }

        if (Intent.ACTION_SEND.equals(action)) {
            Uri stream = extractSingleStream(intent);
            if (stream != null) {
                openSingle(stream, guessMime(intent, stream), action);
            } else {
                showSharedText(intent);
            }
            return;
        }

        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)) {
            Uri uri = intent.getData();
            if (uri == null) {
                showMessage("No file received.");
                return;
            }
            openSingle(uri, guessMime(intent, uri), action);
            return;
        }

        showMessage("Unsupported Android action.");
    }

    private Uri extractSingleStream(Intent intent) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
                if (u != null) return u;
            } else {
                @SuppressWarnings("deprecation")
                Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (u != null) return u;
            }
        } catch (Exception ignored) {}

        ClipData clip = intent.getClipData();
        if (clip != null && clip.getItemCount() > 0) {
            Uri u = clip.getItemAt(0).getUri();
            if (u != null) return u;
        }
        return null;
    }

    private ArrayList<Uri> extractMultipleUris(Intent intent) {
        ArrayList<Uri> out = new ArrayList<>();
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
                if (list != null) out.addAll(list);
            } else {
                @SuppressWarnings("deprecation")
                ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (list != null) out.addAll(list);
            }
        } catch (Exception ignored) {}

        ClipData clip = intent.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null && !out.contains(u)) out.add(u);
            }
        }
        return out;
    }

    private void openSingle(Uri uri, String mime, String sourceAction) {
        if (uri == null) {
            showMessage("No file received.");
            return;
        }

        String name = displayName(uri);
        String ext = extension(name);

        try {
            Intent target;
            if (isImage(mime, ext)) {
                target = new Intent(this, ImageViewerActivity.class);
                target.setData(uri);
                target.putExtra("incoming_action", sourceAction);
            } else if (isPdf(mime, ext)) {
                target = new Intent(this, PdfViewerActivity.class);
                target.setData(uri);
                target.putExtra("pdf_name", name);
                target.putExtra("pdf_slot", 1);
                target.putExtra("incoming_action", sourceAction);
            } else if (isAudio(mime, ext) || isVideo(mime, ext)) {
                target = new Intent(this, MediaPlayerActivity.class);
                target.setData(uri);
                target.putExtra("media_name", name);
                target.putExtra("media_mime", mime);
                target.putExtra("media_mode", isVideo(mime, ext) ? "video" : "audio");
                target.putExtra("incoming_action", sourceAction);
            } else if (isOfficeOrText(mime, ext)) {
                target = new Intent(this, OfficeViewerActivity.class);
                target.setData(uri);
                target.putExtra("office_name", name);
                target.putExtra("incoming_action", sourceAction);
            } else {
                showGenericFile(uri, name, mime, sourceAction);
                return;
            }

            target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            startActivity(target);
            finish();
        } catch (Exception e) {
            showGenericFile(uri, name, mime, sourceAction);
        }
    }

    private void showMultiple(ArrayList<Uri> uris, String sharedMime) {
        setTitle("STS Fast Browser");
        LinearLayout root = baseColumn();
        TextView title = titleView(uris.size() + " shared files");
        root.addView(title);

        TextView hint = bodyView("किसी file पर tap करें। STS Fast Browser उसे सही built-in viewer में खोलेगा।");
        hint.setPadding(dp(16), dp(4), dp(16), dp(12));
        root.addView(hint);

        for (Uri uri : uris) {
            String name = displayName(uri);
            String mime = getContentResolver().getType(uri);
            if (TextUtils.isEmpty(mime)) mime = sharedMime;
            final String useMime = TextUtils.isEmpty(mime) ? "application/octet-stream" : mime;

            TextView row = new TextView(this);
            row.setText(name + "\n" + useMime);
            row.setTextColor(Color.parseColor("#162326"));
            row.setTextSize(15);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            row.setBackgroundColor(Color.parseColor("#EEF4F3"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(12), dp(5), dp(12), dp(5));
            root.addView(row, lp);
            row.setOnClickListener(v -> openSingle(uri, useMime, Intent.ACTION_SEND_MULTIPLE));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void showSharedText(Intent intent) {
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (TextUtils.isEmpty(text)) {
            CharSequence cs = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (cs != null) text = cs.toString();
        }
        if (TextUtils.isEmpty(text)) {
            showMessage("Share received, but no readable file or text was supplied.");
            return;
        }

        String trimmed = text.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            Intent browser = new Intent(this, MainActivity.class);
            browser.putExtra("browser_open_url", trimmed);
            browser.putExtra("browser_slot", 1);
            startActivity(browser);
            finish();
            return;
        }

        setTitle("STS Fast Browser");
        LinearLayout root = baseColumn();
        root.addView(titleView("Shared text"));
        TextView body = bodyView(text);
        body.setTextIsSelectable(true);
        body.setPadding(dp(16), dp(8), dp(16), dp(20));
        root.addView(body);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void showGenericFile(Uri uri, String name, String mime, String sourceAction) {
        setTitle("STS Fast Browser");
        LinearLayout root = baseColumn();
        root.addView(titleView(name));

        long size = fileSize(uri);
        String actionLabel = Intent.ACTION_EDIT.equals(sourceAction) ? "Edit with" :
                (Intent.ACTION_SEND.equals(sourceAction) || Intent.ACTION_SEND_MULTIPLE.equals(sourceAction))
                        ? "Shared to" : "Opened with";
        root.addView(bodyView(actionLabel + " STS Fast Browser\nType: " +
                (TextUtils.isEmpty(mime) ? "Unknown" : mime) +
                (size >= 0 ? "\nSize: " + humanSize(size) : "")));

        TextView preview = bodyView(buildPreview(uri, name, mime));
        preview.setTextIsSelectable(true);
        preview.setTypeface(android.graphics.Typeface.MONOSPACE);
        preview.setPadding(dp(16), dp(14), dp(16), dp(24));
        root.addView(preview);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private String buildPreview(Uri uri, String name, String mime) {
        String ext = extension(name);
        if ("zip".equals(ext) || "apk".equals(ext) || "jar".equals(ext)) {
            String zip = zipPreview(uri);
            if (!TextUtils.isEmpty(zip)) return zip;
        }

        if (isTextLike(mime, ext)) {
            byte[] data = readPrefix(uri, MAX_TEXT_PREVIEW);
            if (data.length == 0) return "File is empty or could not be read.";
            try {
                return new String(data, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }

        byte[] data = readPrefix(uri, MAX_BINARY_PREVIEW);
        if (data.length == 0) return "File received successfully. No visual preview is available for this format.";
        return "Binary preview (first " + data.length + " bytes):\n\n" + hexDump(data);
    }

    private String zipPreview(Uri uri) {
        StringBuilder sb = new StringBuilder("Archive contents:\n\n");
        int count = 0;
        try (InputStream raw = getContentResolver().openInputStream(uri);
             ZipInputStream zin = raw == null ? null : new ZipInputStream(raw)) {
            if (zin == null) return "";
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null && count < MAX_ZIP_ENTRIES) {
                sb.append(e.isDirectory() ? "[DIR] " : "      ").append(e.getName()).append('\n');
                count++;
            }
            if (count == 0) return "";
            if (count >= MAX_ZIP_ENTRIES) sb.append("\n…more entries not shown");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private byte[] readPrefix(Uri uri, int limit) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) return new byte[0];
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while (total < limit && (n = in.read(buf, 0, Math.min(buf.length, limit - total))) > 0) {
                out.write(buf, 0, n);
                total += n;
            }
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private String hexDump(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i += 16) {
            sb.append(String.format(Locale.US, "%08X  ", i));
            StringBuilder ascii = new StringBuilder();
            for (int j = 0; j < 16; j++) {
                if (i + j < data.length) {
                    int b = data[i + j] & 0xFF;
                    sb.append(String.format(Locale.US, "%02X ", b));
                    ascii.append(b >= 32 && b < 127 ? (char) b : '.');
                } else {
                    sb.append("   ");
                    ascii.append(' ');
                }
            }
            sb.append(" ").append(ascii).append('\n');
        }
        return sb.toString();
    }

    private String guessMime(Intent intent, Uri uri) {
        String mime = intent == null ? null : intent.getType();
        if (TextUtils.isEmpty(mime) && uri != null) {
            try { mime = getContentResolver().getType(uri); } catch (Exception ignored) {}
        }
        return TextUtils.isEmpty(mime) ? "application/octet-stream" : mime;
    }

    private String displayName(Uri uri) {
        if (uri == null) return "File";
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String p = uri.getPath();
            if (!TextUtils.isEmpty(p)) return new File(p).getName();
        }
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String n = c.getString(idx);
                    if (!TextUtils.isEmpty(n)) return n;
                }
            }
        } catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        return TextUtils.isEmpty(last) ? "File" : last;
    }

    private long fileSize(Uri uri) {
        if (uri == null) return -1;
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            try {
                File f = new File(uri.getPath());
                return f.exists() ? f.length() : -1;
            } catch (Exception ignored) {}
        }
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private String extension(String name) {
        if (TextUtils.isEmpty(name)) return "";
        int q = name.indexOf('?');
        if (q >= 0) name = name.substring(0, q);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isImage(String mime, String ext) {
        return mime.startsWith("image/") || in(ext, "jpg","jpeg","png","webp","heic","heif","gif","bmp","avif","tif","tiff");
    }

    private boolean isPdf(String mime, String ext) {
        return "application/pdf".equalsIgnoreCase(mime) || "application/x-pdf".equalsIgnoreCase(mime) || "pdf".equals(ext);
    }

    private boolean isAudio(String mime, String ext) {
        return mime.startsWith("audio/") || in(ext, "mp3","m4a","aac","wav","ogg","flac","opus","amr","wma");
    }

    private boolean isVideo(String mime, String ext) {
        return mime.startsWith("video/") || in(ext, "mp4","mkv","webm","3gp","mov","avi","wmv","m4v","mpeg","mpg","ts");
    }

    private boolean isOfficeOrText(String mime, String ext) {
        if (mime.startsWith("text/")) return true;
        return in(ext, "doc","docx","xls","xlsx","ppt","pptx","txt","csv","rtf","odt","ods","odp","ofd");
    }

    private boolean isTextLike(String mime, String ext) {
        return mime.startsWith("text/") ||
                in(ext, "txt","csv","rtf","json","xml","html","htm","css","js","java","kt","md","log","ini","conf","yaml","yml","sql");
    }

    private boolean in(String value, String... values) {
        for (String v : values) if (v.equalsIgnoreCase(value)) return true;
        return false;
    }

    private LinearLayout baseColumn() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(0, dp(12), 0, dp(12));
        return root;
    }

    private TextView titleView(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.parseColor("#162326"));
        v.setTextSize(20);
        v.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        v.setPadding(dp(16), dp(10), dp(16), dp(8));
        v.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return v;
    }

    private TextView bodyView(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.parseColor("#26383B"));
        v.setTextSize(14);
        v.setPadding(dp(16), dp(6), dp(16), dp(8));
        return v;
    }

    private void showMessage(String message) {
        setTitle("STS Fast Browser");
        LinearLayout root = baseColumn();
        root.addView(titleView("STS Fast Browser"));
        root.addView(bodyView(message));
        setContentView(root);
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.getDefault(), "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb);
        return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
