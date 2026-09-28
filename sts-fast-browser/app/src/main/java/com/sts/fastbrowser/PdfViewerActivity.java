package com.sts.fastbrowser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PdfViewerActivity extends Activity {
    private static final int REQ_STORAGE = 701;
    private String sourceUrl;
    private Uri sourceUri;
    private String fileName;
    private String cookie;
    private String userAgent;
    private File pdfFile;
    private LinearLayout pages;
    private TextView status;
    private boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#355C62"));
        getWindow().setNavigationBarColor(Color.parseColor("#F2F5F4"));

        sourceUrl = getIntent().getStringExtra("pdf_url");
        sourceUri = getIntent().getData();
        fileName = getIntent().getStringExtra("pdf_name");
        cookie = getIntent().getStringExtra("pdf_cookie");
        userAgent = getIntent().getStringExtra("pdf_user_agent");
        if (sourceUri != null) {
            String externalName = resolveDisplayName(sourceUri);
            if (!TextUtils.isEmpty(externalName)) fileName = externalName;
        }
        if (TextUtils.isEmpty(fileName)) fileName = "Document.pdf";
        if (!fileName.toLowerCase().endsWith(".pdf")) fileName += ".pdf";
        setTitle(fileName);
        setContentView(buildUi());
        loadPdf();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#EEF2F3"));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(3), dp(4), dp(3));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#D9F0EE"), Color.parseColor("#E3EAF4"), Color.parseColor("#EEE8F4")}
        );
        top.setBackground(bg);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        TextView title = new TextView(this);
        title.setText(fileName);
        title.setTextColor(Color.parseColor("#162326"));
        title.setTextSize(14);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(10), 0, dp(8), 0);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView menu = new TextView(this);
        menu.setText("⋮");
        menu.setTextSize(25);
        menu.setTextColor(Color.parseColor("#162326"));
        menu.setGravity(Gravity.CENTER);
        menu.setClickable(true);
        top.addView(menu, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.MATCH_PARENT));
        menu.setOnClickListener(this::showMenu);

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        pages = new LinearLayout(this);
        pages.setOrientation(LinearLayout.VERTICAL);
        pages.setGravity(Gravity.CENTER_HORIZONTAL);
        pages.setPadding(dp(6), dp(6), dp(6), dp(12));
        sc.addView(pages, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setText("PDF loading...");
        status.setTextColor(Color.DKGRAY);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(32), dp(12), dp(32));
        pages.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110)));
        return root;
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor, Gravity.END);
        pm.getMenu().add("Share");
        pm.getMenu().add("Print");
        pm.getMenu().add("Download");
        pm.setOnMenuItemClickListener(item -> {
            if (pdfFile == null || !pdfFile.exists()) {
                Toast.makeText(this, "PDF अभी तैयार हो रहा है", Toast.LENGTH_SHORT).show();
                return true;
            }
            String t = String.valueOf(item.getTitle());
            if ("Share".equals(t)) sharePdf();
            else if ("Print".equals(t)) printPdf();
            else if ("Download".equals(t)) saveToDownloads();
            return true;
        });
        pm.show();
    }

    private void loadPdf() {
        new Thread(() -> {
            try {
                File dir = new File(getCacheDir(), "pdf");
                if (!dir.exists()) dir.mkdirs();
                String safe = sanitize(fileName);
                pdfFile = new File(dir, System.currentTimeMillis() + "_" + safe);
                if (sourceUri != null && ("content".equalsIgnoreCase(sourceUri.getScheme()) || "file".equalsIgnoreCase(sourceUri.getScheme()))) {
                    copyUriToFile(sourceUri, pdfFile);
                } else {
                    download(sourceUrl, pdfFile);
                }
                if (destroyed) return;
                render(pdfFile);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("PDF open नहीं हो पाया");
                    Toast.makeText(this, "PDF open नहीं हो पाया", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private String resolveDisplayName(Uri uri) {
        if (uri == null) return null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (i >= 0) return c.getString(i);
                }
            } catch (Exception ignored) {}
        }
        String last = uri.getLastPathSegment();
        if (last != null && !last.trim().isEmpty()) return last;
        return null;
    }

    private void copyUriToFile(Uri uri, File out) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new Exception("Unable to open PDF");
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.flush();
        }
    }

    private void download(String urlString, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(45000);
        c.setInstanceFollowRedirects(true);
        if (!TextUtils.isEmpty(cookie)) c.setRequestProperty("Cookie", cookie);
        if (!TextUtils.isEmpty(userAgent)) c.setRequestProperty("User-Agent", userAgent);
        c.setRequestProperty("Accept", "application/pdf,*/*");
        int code = c.getResponseCode();
        if (code < 200 || code >= 400) throw new Exception("HTTP " + code);
        try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } finally {
            c.disconnect();
        }
    }

    private void render(File file) throws Exception {
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer renderer = new PdfRenderer(pfd);
        runOnUiThread(() -> pages.removeAllViews());
        int screen = getResources().getDisplayMetrics().widthPixels;
        int targetW = Math.min(Math.max(480, screen - dp(12)), 1000);
        for (int i = 0; i < renderer.getPageCount() && !destroyed; i++) {
            PdfRenderer.Page page = renderer.openPage(i);
            int targetH = Math.max(1, Math.round(targetW * (page.getHeight() / (float) page.getWidth())));
            Bitmap bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            final Bitmap pageBmp = bmp;
            runOnUiThread(() -> {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(pageBmp);
                iv.setAdjustViewBounds(true);
                iv.setBackgroundColor(Color.WHITE);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, dp(6));
                pages.addView(iv, lp);
            });
        }
        renderer.close();
        pfd.close();
    }

    private void sharePdf() {
        Uri uri = Uri.parse("content://" + getPackageName() + ".pdfshare/" + Uri.encode(pdfFile.getName()));
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Share PDF"));
    }

    private void printPdf() {
        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
        pm.print(fileName, new RawPdfAdapter(pdfFile, fileName), new PrintAttributes.Builder().build());
    }

    private void saveToDownloads() {
        if (android.os.Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        new Thread(() -> {
            try {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/STS Fast Browser");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("insert failed");
                    try (InputStream in = new FileInputStream(pdfFile); OutputStream out = getContentResolver().openOutputStream(uri)) {
                        copy(in, out);
                    }
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    File out = uniqueFile(dir, fileName);
                    try (InputStream in = new FileInputStream(pdfFile); OutputStream os = new FileOutputStream(out)) {
                        copy(in, os);
                    }
                }
                runOnUiThread(() -> Toast.makeText(this, "PDF Downloads में save हो गया", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Download नहीं हो पाया", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name.toLowerCase().endsWith(".pdf") ? name.substring(0, name.length() - 4) : name;
        int n = 2;
        do { f = new File(dir, base + " (" + n++ + ").pdf"); } while (f.exists());
        return f;
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        if (out == null) throw new Exception("no output");
        byte[] buf = new byte[32768];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
    }

    private String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._ -]", "_");
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveToDownloads();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private static class RawPdfAdapter extends PrintDocumentAdapter {
        private final File file;
        private final String name;
        RawPdfAdapter(File file, String name) { this.file = file; this.name = name; }

        @Override
        public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                             android.os.CancellationSignal cancellationSignal,
                             LayoutResultCallback callback, Bundle extras) {
            if (cancellationSignal.isCanceled()) { callback.onLayoutCancelled(); return; }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder(name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build();
            callback.onLayoutFinished(info, true);
        }

        @Override
        public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                            android.os.CancellationSignal cancellationSignal,
                            WriteResultCallback callback) {
            new Thread(() -> {
                try (InputStream in = new FileInputStream(file);
                     OutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
                    byte[] buf = new byte[32768];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (cancellationSignal.isCanceled()) { callback.onWriteCancelled(); return; }
                        out.write(buf, 0, n);
                    }
                    callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                } catch (Exception e) {
                    callback.onWriteFailed(e.getMessage());
                }
            }).start();
        }
    }
}
