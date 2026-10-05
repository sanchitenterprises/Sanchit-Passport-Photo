package com.sts.fastbrowser;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * One Android entry point for Open with / Share / Edit.
 * It accepts almost any file, resolves the real type using MIME + extension +
 * light signature sniffing, then sends it to the correct STS built-in viewer.
 */
public class IntentRouterActivity extends Activity {
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
            showMessage("कोई file नहीं मिली।");
            return;
        }

        final String action = intent.getAction();

        if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> uris = extractMultipleUris(intent);
            if (uris.isEmpty()) {
                showSharedText(intent);
            } else if (uris.size() == 1) {
                routeSingle(uris.get(0), resolveMime(intent, uris.get(0)), action);
            } else {
                showMultiple(uris, intent.getType());
            }
            return;
        }

        if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = extractSingleUri(intent);
            if (uri != null) {
                routeSingle(uri, resolveMime(intent, uri), action);
            } else {
                showSharedText(intent);
            }
            return;
        }

        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)) {
            Uri uri = intent.getData();
            if (uri == null) uri = extractSingleUri(intent);
            if (uri == null) {
                showMessage("कोई file नहीं मिली।");
                return;
            }
            routeSingle(uri, resolveMime(intent, uri), action);
            return;
        }

        showMessage("यह Android action अभी supported नहीं है।");
    }

    private Uri extractSingleUri(Intent intent) {
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
        return intent.getData();
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

    private void routeSingle(Uri uri, String rawMime, String sourceAction) {
        if (uri == null) {
            showMessage("कोई file नहीं मिली।");
            return;
        }

        String name = displayName(uri);
        String ext = extension(name);
        String mime = normalizeMime(rawMime);

        // Extension wins when a sender supplies a vague/wrong generic MIME.
        String extMime = mimeFromExtension(ext);
        if (isGenericMime(mime) && !TextUtils.isEmpty(extMime)) mime = extMime;

        // Some apps send application/octet-stream even for normal images/PDFs.
        if (isGenericMime(mime)) {
            String sniffed = sniffMime(uri);
            if (!TextUtils.isEmpty(sniffed)) mime = sniffed;
        }

        // If resolver knows a better type than the sender, use it.
        if (isGenericMime(mime)) {
            try {
                String resolved = normalizeMime(getContentResolver().getType(uri));
                if (!isGenericMime(resolved)) mime = resolved;
            } catch (Exception ignored) {}
        }

        if (TextUtils.isEmpty(mime)) mime = "application/octet-stream";

        Class<?> targetClass = null;
        if (isImage(mime, ext)) {
            targetClass = ImageViewerActivity.class;
        } else if (isPdf(mime, ext)) {
            targetClass = PdfViewerActivity.class;
        } else if (isAudio(mime, ext) || isVideo(mime, ext)) {
            targetClass = MediaPlayerActivity.class;
        } else if (isOfficeOrText(mime, ext)) {
            targetClass = OfficeViewerActivity.class;
        }

        if (targetClass == null) {
            showFriendlyFallback(uri, name, mime, sourceAction);
            return;
        }

        try {
            Intent target = new Intent(this, targetClass);
            target.setDataAndType(uri, mime);
            target.putExtra("incoming_action", sourceAction);
            target.putExtra("incoming_edit", Intent.ACTION_EDIT.equals(sourceAction));

            if (targetClass == PdfViewerActivity.class) {
                target.putExtra("pdf_name", name);
                target.putExtra("pdf_slot", 1);
            } else if (targetClass == OfficeViewerActivity.class) {
                target.putExtra("office_name", name);
            } else if (targetClass == MediaPlayerActivity.class) {
                target.putExtra("media_name", name);
                target.putExtra("media_mime", mime);
                target.putExtra("media_mode", isVideo(mime, ext) ? "video" : "audio");
            }

            // Propagate the exact URI grant to the viewer. Do not force a new task here:
            // OEM Android builds can reject/lose grants when a received share is re-launched
            // into another task.
            target.setClipData(ClipData.newRawUri("STS Fast Browser file", uri));
            target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (Intent.ACTION_EDIT.equals(sourceAction)) {
                target.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }

            startActivity(target);
            finish();
        } catch (Exception launchError) {
            // Never show raw bytes for a normal file. Keep a clean fallback screen.
            showFriendlyFallback(uri, name, mime, sourceAction);
        }
    }

    private void showMultiple(ArrayList<Uri> uris, String sharedMime) {
        setTitle("STS Fast Browser");
        LinearLayout root = baseColumn();
        root.addView(titleView(uris.size() + " files"));

        TextView hint = bodyView("किसी file पर tap करें। File type के अनुसार सही STS viewer खुलेगा।");
        hint.setPadding(dp(16), dp(4), dp(16), dp(12));
        root.addView(hint);

        for (Uri uri : uris) {
            String name = displayName(uri);
            String mime = resolveMime(null, uri);
            if (isGenericMime(mime)) mime = normalizeMime(sharedMime);
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
            row.setOnClickListener(v -> routeSingle(uri, useMime, Intent.ACTION_SEND_MULTIPLE));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void showSharedText(Intent intent) {
        CharSequence shared = null;
        try { shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT); } catch (Exception ignored) {}
        String text = shared == null ? "" : shared.toString().trim();

        if (TextUtils.isEmpty(text)) {
            showMessage("Share आया, लेकिन readable file या text नहीं मिला।");
            return;
        }

        if (text.startsWith("http://") || text.startsWith("https://")) {
            Intent browser = new Intent(this, MainActivity.class);
            browser.putExtra("browser_open_url", text);
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

    private void showFriendlyFallback(Uri uri, String name, String mime, String sourceAction) {
        setTitle("STS Fast Browser");
        LinearLayout root = baseColumn();
        root.addView(titleView(name));

        String actionLabel = Intent.ACTION_EDIT.equals(sourceAction) ? "Edit with" :
                (Intent.ACTION_SEND.equals(sourceAction) || Intent.ACTION_SEND_MULTIPLE.equals(sourceAction))
                        ? "Shared to" : "Opened with";

        long size = fileSize(uri);
        root.addView(bodyView(actionLabel + " STS Fast Browser\nType: " + mime +
                (size >= 0 ? "\nSize: " + humanSize(size) : "")));

        String ext = extension(name);
        String archive = "";
        if (in(ext, "zip", "jar", "apk", "cbz")) archive = zipPreview(uri);

        TextView message;
        if (!TextUtils.isEmpty(archive)) {
            message = bodyView(archive);
            message.setTextIsSelectable(true);
        } else {
            message = bodyView("File STS Fast Browser ने receive कर ली है। इस format का built-in visual preview उपलब्ध नहीं है।");
        }
        message.setPadding(dp(16), dp(14), dp(16), dp(24));
        root.addView(message);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private String resolveMime(Intent intent, Uri uri) {
        String intentMime = normalizeMime(intent == null ? null : intent.getType());
        String resolverMime = "";
        try { resolverMime = normalizeMime(getContentResolver().getType(uri)); } catch (Exception ignored) {}

        String ext = extension(displayName(uri));
        String extMime = mimeFromExtension(ext);

        if (!isGenericMime(intentMime)) return intentMime;
        if (!isGenericMime(resolverMime)) return resolverMime;
        if (!TextUtils.isEmpty(extMime)) return extMime;

        String sniffed = sniffMime(uri);
        return TextUtils.isEmpty(sniffed) ? "application/octet-stream" : sniffed;
    }

    private String normalizeMime(String mime) {
        if (mime == null) return "";
        mime = mime.trim().toLowerCase(Locale.ROOT);
        int semi = mime.indexOf(';');
        if (semi >= 0) mime = mime.substring(0, semi).trim();
        return mime;
    }

    private boolean isGenericMime(String mime) {
        return TextUtils.isEmpty(mime) || "*/*".equals(mime) ||
                "application/octet-stream".equals(mime) ||
                "binary/octet-stream".equals(mime);
    }

    private String sniffMime(Uri uri) {
        byte[] h = new byte[16];
        int n = 0;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) n = in.read(h);
        } catch (Exception ignored) {}

        if (n >= 3 && (h[0] & 0xff) == 0xff && (h[1] & 0xff) == 0xd8 && (h[2] & 0xff) == 0xff)
            return "image/jpeg";
        if (n >= 8 && (h[0] & 0xff) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G')
            return "image/png";
        if (n >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F')
            return "image/gif";
        if (n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' &&
                h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P')
            return "image/webp";
        if (n >= 5 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-')
            return "application/pdf";
        if (n >= 4 && h[0] == 'P' && h[1] == 'K' && (h[2] == 3 || h[2] == 5 || h[2] == 7) &&
                (h[3] == 4 || h[3] == 6 || h[3] == 8))
            return "application/zip";
        if (n >= 4 && h[0] == 'O' && h[1] == 'g' && h[2] == 'g' && h[3] == 'S')
            return "audio/ogg";
        if (n >= 3 && h[0] == 'I' && h[1] == 'D' && h[2] == '3')
            return "audio/mpeg";
        if (n >= 8 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p')
            return "video/mp4";
        return "";
    }

    private String mimeFromExtension(String ext) {
        if (in(ext, "jpg", "jpeg", "jpe")) return "image/jpeg";
        if ("png".equals(ext)) return "image/png";
        if ("webp".equals(ext)) return "image/webp";
        if ("gif".equals(ext)) return "image/gif";
        if ("bmp".equals(ext)) return "image/bmp";
        if ("avif".equals(ext)) return "image/avif";
        if (in(ext, "heic", "heif")) return "image/heic";
        if (in(ext, "tif", "tiff")) return "image/tiff";
        if ("pdf".equals(ext)) return "application/pdf";

        if ("mp3".equals(ext)) return "audio/mpeg";
        if ("m4a".equals(ext)) return "audio/mp4";
        if ("aac".equals(ext)) return "audio/aac";
        if ("wav".equals(ext)) return "audio/wav";
        if ("ogg".equals(ext)) return "audio/ogg";
        if ("flac".equals(ext)) return "audio/flac";
        if ("opus".equals(ext)) return "audio/opus";
        if ("amr".equals(ext)) return "audio/amr";

        if ("mp4".equals(ext) || "m4v".equals(ext)) return "video/mp4";
        if ("mkv".equals(ext)) return "video/x-matroska";
        if ("webm".equals(ext)) return "video/webm";
        if ("3gp".equals(ext)) return "video/3gpp";
        if ("mov".equals(ext)) return "video/quicktime";
        if ("avi".equals(ext)) return "video/x-msvideo";
        if ("wmv".equals(ext)) return "video/x-ms-wmv";
        if (in(ext, "mpeg", "mpg")) return "video/mpeg";

        if ("docx".equals(ext)) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if ("doc".equals(ext)) return "application/msword";
        if ("xlsx".equals(ext)) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if ("xls".equals(ext)) return "application/vnd.ms-excel";
        if ("pptx".equals(ext)) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if ("ppt".equals(ext)) return "application/vnd.ms-powerpoint";
        if ("txt".equals(ext)) return "text/plain";
        if ("csv".equals(ext)) return "text/csv";
        if ("rtf".equals(ext)) return "application/rtf";
        if ("odt".equals(ext)) return "application/vnd.oasis.opendocument.text";
        if ("ods".equals(ext)) return "application/vnd.oasis.opendocument.spreadsheet";
        if ("odp".equals(ext)) return "application/vnd.oasis.opendocument.presentation";
        if ("ofd".equals(ext)) return "application/ofd";
        if ("json".equals(ext)) return "application/json";
        if ("xml".equals(ext)) return "application/xml";
        if (in(ext, "html", "htm")) return "text/html";
        if ("md".equals(ext)) return "text/markdown";
        if ("log".equals(ext)) return "text/plain";

        if ("zip".equals(ext)) return "application/zip";
        if ("rar".equals(ext)) return "application/vnd.rar";
        if ("7z".equals(ext)) return "application/x-7z-compressed";
        if ("apk".equals(ext)) return "application/vnd.android.package-archive";
        return "";
    }

    private boolean isImage(String mime, String ext) {
        return mime.startsWith("image/") ||
                in(ext, "jpg","jpeg","jpe","png","webp","heic","heif","gif","bmp","avif","tif","tiff");
    }

    private boolean isPdf(String mime, String ext) {
        return "application/pdf".equals(mime) || "application/x-pdf".equals(mime) || "pdf".equals(ext);
    }

    private boolean isAudio(String mime, String ext) {
        return mime.startsWith("audio/") ||
                in(ext, "mp3","m4a","aac","wav","ogg","flac","opus","amr","wma");
    }

    private boolean isVideo(String mime, String ext) {
        return mime.startsWith("video/") ||
                in(ext, "mp4","mkv","webm","3gp","mov","avi","wmv","m4v","mpeg","mpg","ts");
    }

    private boolean isOfficeOrText(String mime, String ext) {
        if (mime.startsWith("text/")) return true;
        if (in(mime,
                "application/msword",
                "application/vnd.ms-word",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/rtf",
                "application/vnd.oasis.opendocument.text",
                "application/vnd.oasis.opendocument.spreadsheet",
                "application/vnd.oasis.opendocument.presentation",
                "application/ofd",
                "application/json",
                "application/xml")) return true;
        return in(ext, "doc","docx","xls","xlsx","ppt","pptx","txt","csv","rtf","odt","ods","odp","ofd",
                "json","xml","html","htm","md","log");
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

    private boolean in(String value, String... values) {
        if (value == null) return false;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
