package com.sts.fastbrowser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
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
import android.text.InputType;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class PdfViewerActivity extends Activity {
    private static final int REQ_STORAGE = 701;

    private String sourceUrl;
    private Uri sourceUri;
    private String fileName;
    private String cookie;
    private String userAgent;

    private File sourceFile;
    private File pdfFile;
    private ParcelFileDescriptor pdfPfd;
    private PdfRenderer pdfRenderer;
    private PDDocument textDocument;

    private LinearLayout pages;
    private TextView status;
    private boolean destroyed = false;
    private int pendingImagePage = -1;
    private String pendingPageFormat = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PDFBoxResourceLoader.init(getApplicationContext());

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
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) fileName += ".pdf";

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

        ImageButton menu = new ImageButton(this);
        menu.setImageResource(R.drawable.ic_more);
        menu.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        menu.setPadding(dp(10), dp(10), dp(10), dp(10));
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setContentDescription("Menu");
        top.addView(menu, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.MATCH_PARENT));
        menu.setOnClickListener(this::showMenu);

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setSmoothScrollingEnabled(true);
        pages = new LinearLayout(this);
        pages.setOrientation(LinearLayout.VERTICAL);
        pages.setGravity(Gravity.CENTER_HORIZONTAL);
        pages.setPadding(dp(6), dp(6), dp(6), dp(12));
        pages.setClipChildren(false);
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

                sourceFile = new File(dir, "source_" + System.currentTimeMillis() + "_" + sanitize(fileName));
                if (sourceUri != null && ("content".equalsIgnoreCase(sourceUri.getScheme()) || "file".equalsIgnoreCase(sourceUri.getScheme()))) {
                    copyUriToFile(sourceUri, sourceFile);
                } else {
                    download(sourceUrl, sourceFile);
                }

                openDocumentWithPassword(null, false);
            } catch (Exception e) {
                showOpenError();
            }
        }).start();
    }

    private void openDocumentWithPassword(String password, boolean fromPrompt) {
        new Thread(() -> {
            PDDocument doc = null;
            try {
                doc = PDDocument.load(sourceFile, password == null ? "" : password);
                if (destroyed) {
                    doc.close();
                    return;
                }

                closePdfResources();
                textDocument = doc;

                if (doc.isEncrypted()) {
                    File dir = new File(getCacheDir(), "pdf");
                    pdfFile = new File(dir, "view_" + System.currentTimeMillis() + "_" + sanitize(fileName));
                    doc.setAllSecurityToBeRemoved(true);
                    doc.save(pdfFile);
                } else {
                    pdfFile = sourceFile;
                }

                pdfPfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(pdfPfd);
                renderAllPages();
            } catch (InvalidPasswordException e) {
                if (doc != null) try { doc.close(); } catch (Exception ignored) {}
                runOnUiThread(() -> showPasswordDialog(fromPrompt));
            } catch (Exception e) {
                if (doc != null && doc != textDocument) try { doc.close(); } catch (Exception ignored) {}
                showOpenError();
            }
        }).start();
    }

    private void showPasswordDialog(boolean wasWrong) {
        if (isFinishing() || destroyed) return;

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Password");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(dp(16), dp(4), dp(16), dp(4));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Password Protected PDF")
                .setMessage(wasWrong ? "Password गलत है। दोबारा डालें।" : "इस PDF को खोलने के लिए password डालें।")
                .setView(input)
                .setNegativeButton("Close", (d, w) -> finish())
                .setPositiveButton("Open", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String password = input.getText().toString();
                dialog.dismiss();
                status.setText("PDF opening...");
                openDocumentWithPassword(password, true);
            });
        });
        dialog.show();
    }

    private void renderAllPages() throws Exception {
        runOnUiThread(() -> pages.removeAllViews());

        int screen = getResources().getDisplayMetrics().widthPixels;
        int targetW = Math.min(Math.max(640, screen - dp(12)), 1200);
        int count;
        synchronized (this) {
            count = pdfRenderer.getPageCount();
        }

        for (int i = 0; i < count && !destroyed; i++) {
            final int pageIndex = i;
            Bitmap bmp = renderPageBitmap(pageIndex, targetW);
            runOnUiThread(() -> addPageView(pageIndex, bmp));
        }
    }

    private synchronized Bitmap renderPageBitmap(int pageIndex, int targetW) throws Exception {
        if (pdfRenderer == null) throw new IllegalStateException("PDF renderer unavailable");
        PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);
        try {
            int targetH = Math.max(1, Math.round(targetW * (page.getHeight() / (float) page.getWidth())));
            Bitmap bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bmp;
        } finally {
            page.close();
        }
    }

    private void addPageView(int pageIndex, Bitmap bitmap) {
        if (destroyed) {
            bitmap.recycle();
            return;
        }

        ZoomPageView iv = new ZoomPageView(this, pageIndex);
        iv.setImageBitmap(bitmap);
        iv.setBackgroundColor(Color.WHITE);
        iv.setContentDescription("PDF page " + (pageIndex + 1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(dp(120), Math.round((getResources().getDisplayMetrics().widthPixels - dp(12)) *
                        (bitmap.getHeight() / (float) bitmap.getWidth())))
        );
        lp.setMargins(0, 0, 0, dp(7));
        pages.addView(iv, lp);
    }

    private void showPageActions(int pageIndex) {
        if (isFinishing() || destroyed) return;
        new AlertDialog.Builder(this)
                .setTitle("Page " + (pageIndex + 1))
                .setItems(new String[]{"Save Page", "Copy Text"}, (d, which) -> {
                    if (which == 0) showSavePageFormats(pageIndex);
                    else showPageText(pageIndex);
                })
                .show();
    }

    private void showSavePageFormats(int pageIndex) {
        new AlertDialog.Builder(this)
                .setTitle("Save Page " + (pageIndex + 1))
                .setItems(new String[]{"JPG", "PNG", "PDF"}, (d, which) -> {
                    if (which == 0) savePageAsImage(pageIndex, "jpg");
                    else if (which == 1) savePageAsImage(pageIndex, "png");
                    else savePageAsPdf(pageIndex);
                })
                .show();
    }

    private void showPageText(int pageIndex) {
        new Thread(() -> {
            try {
                String text;
                synchronized (this) {
                    if (textDocument == null) throw new IllegalStateException("PDF text unavailable");
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setStartPage(pageIndex + 1);
                    stripper.setEndPage(pageIndex + 1);
                    text = stripper.getText(textDocument);
                }
                final String finalText = text == null ? "" : text.trim();
                runOnUiThread(() -> {
                    if (finalText.isEmpty()) {
                        Toast.makeText(this, "इस page में selectable text नहीं मिला", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    EditText box = new EditText(this);
                    box.setText(finalText);
                    box.setTextSize(15);
                    box.setTextColor(Color.parseColor("#172326"));
                    box.setBackgroundColor(Color.WHITE);
                    box.setPadding(dp(14), dp(10), dp(14), dp(10));
                    box.setTextIsSelectable(true);
                    box.setKeyListener(null);

                    ScrollView scroll = new ScrollView(this);
                    scroll.addView(box, new ScrollView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    ));

                    new AlertDialog.Builder(this)
                            .setTitle("Page " + (pageIndex + 1) + " Text")
                            .setView(scroll)
                            .setNegativeButton("Close", null)
                            .setPositiveButton("Copy All", (dialog, which) -> {
                                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                cm.setPrimaryClip(ClipData.newPlainText("PDF Page Text", finalText));
                                Toast.makeText(this, "Text copied", Toast.LENGTH_SHORT).show();
                            })
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Text copy नहीं हो पाया", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void savePageAsImage(int pageIndex, String format) {
        if (android.os.Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingImagePage = pageIndex;
            pendingPageFormat = format;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }

        new Thread(() -> {
            Bitmap bmp = null;
            try {
                int width = 2000;
                bmp = renderPageBitmap(pageIndex, width);
                String base = fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")
                        ? fileName.substring(0, fileName.length() - 4)
                        : fileName;

                boolean png = "png".equalsIgnoreCase(format);
                String ext = png ? ".png" : ".jpg";
                String mime = png ? "image/png" : "image/jpeg";
                String outName = sanitize(base) + "_Page_" + (pageIndex + 1) + ext;
                Bitmap.CompressFormat compressFormat = png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
                int quality = png ? 100 : 95;

                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Images.Media.DISPLAY_NAME, outName);
                    v.put(MediaStore.Images.Media.MIME_TYPE, mime);
                    v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/STS Fast Browser");
                    Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("Unable to create image");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null || !bmp.compress(compressFormat, quality, out)) {
                            throw new Exception("Unable to save image");
                        }
                    }
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "STS Fast Browser");
                    if (!dir.exists()) dir.mkdirs();
                    File outFile = uniqueFile(dir, outName, ext);
                    try (OutputStream out = new FileOutputStream(outFile)) {
                        if (!bmp.compress(compressFormat, quality, out)) {
                            throw new Exception("Unable to save image");
                        }
                    }
                }

                final String label = png ? "PNG" : "JPG";
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Page " + (pageIndex + 1) + " " + label + " में save हो गया",
                        Toast.LENGTH_LONG
                ).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Page image save नहीं हो पाया", Toast.LENGTH_SHORT).show());
            } finally {
                if (bmp != null && !bmp.isRecycled()) bmp.recycle();
            }
        }).start();
    }

    private void savePageAsPdf(int pageIndex) {
        if (android.os.Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingImagePage = pageIndex;
            pendingPageFormat = "pdf";
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }

        new Thread(() -> {
            File tmp = null;
            try {
                String base = fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")
                        ? fileName.substring(0, fileName.length() - 4)
                        : fileName;
                String outName = sanitize(base) + "_Page_" + (pageIndex + 1) + ".pdf";

                tmp = new File(getCacheDir(), "single_page_" + System.currentTimeMillis() + ".pdf");
                synchronized (this) {
                    if (textDocument == null) throw new IllegalStateException("PDF unavailable");
                    PDDocument single = new PDDocument();
                    try {
                        single.importPage(textDocument.getPage(pageIndex));
                        single.save(tmp);
                    } finally {
                        single.close();
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, outName);
                    v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/STS Fast Browser");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("Unable to create PDF");
                    try (InputStream in = new FileInputStream(tmp);
                         OutputStream out = getContentResolver().openOutputStream(uri)) {
                        copy(in, out);
                    }
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "STS Fast Browser");
                    if (!dir.exists()) dir.mkdirs();
                    File outFile = uniqueFile(dir, outName, ".pdf");
                    try (InputStream in = new FileInputStream(tmp);
                         OutputStream out = new FileOutputStream(outFile)) {
                        copy(in, out);
                    }
                }

                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Page " + (pageIndex + 1) + " PDF में save हो गया",
                        Toast.LENGTH_LONG
                ).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Page PDF save नहीं हो पाया", Toast.LENGTH_SHORT).show());
            } finally {
                if (tmp != null) tmp.delete();
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
            copy(in, os);
        }
    }

    private void download(String urlString, File out) throws Exception {
        if (TextUtils.isEmpty(urlString)) throw new Exception("Missing PDF URL");
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
            copy(in, os);
        } finally {
            c.disconnect();
        }
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
            pendingImagePage = -2;
            pendingPageFormat = null;
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
                    File out = uniqueFile(dir, fileName, ".pdf");
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

    private File uniqueFile(File dir, String name, String extension) {
        File f = new File(dir, name);
        if (!f.exists()) return f;

        String base = name;
        if (name.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))) {
            base = name.substring(0, name.length() - extension.length());
        }
        int n = 2;
        do {
            f = new File(dir, base + " (" + n++ + ")" + extension);
        } while (f.exists());
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

    private void showOpenError() {
        runOnUiThread(() -> {
            if (status != null) status.setText("PDF open नहीं हो पाया");
            Toast.makeText(this, "PDF open नहीं हो पाया", Toast.LENGTH_SHORT).show();
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            int pending = pendingImagePage;
            String format = pendingPageFormat;
            pendingImagePage = -1;
            pendingPageFormat = null;
            if (pending >= 0) {
                if ("pdf".equalsIgnoreCase(format)) savePageAsPdf(pending);
                else savePageAsImage(pending, "png".equalsIgnoreCase(format) ? "png" : "jpg");
            } else if (pending == -2) {
                saveToDownloads();
            }
        }
    }

    private synchronized void closePdfResources() {
        if (pdfRenderer != null) {
            try { pdfRenderer.close(); } catch (Exception ignored) {}
            pdfRenderer = null;
        }
        if (pdfPfd != null) {
            try { pdfPfd.close(); } catch (Exception ignored) {}
            pdfPfd = null;
        }
        if (textDocument != null) {
            try { textDocument.close(); } catch (Exception ignored) {}
            textDocument = null;
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        closePdfResources();
        super.onDestroy();
    }

    private class ZoomPageView extends ImageView {
        private final Matrix drawMatrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector gestureDetector;
        private final float[] matrixValues = new float[9];

        private float currentScale = 1f;
        private float baseScale = 1f;
        private float lastX;
        private float lastY;
        private final int pageIndex;

        ZoomPageView(Context context, int pageIndex) {
            super(context);
            this.pageIndex = pageIndex;
            setScaleType(ScaleType.MATRIX);
            setClickable(true);
            setLongClickable(true);

            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScaleBegin(ScaleGestureDetector detector) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }

                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float factor = detector.getScaleFactor();
                    if (Float.isNaN(factor) || Float.isInfinite(factor)) return false;

                    float next = Math.max(1f, Math.min(5f, currentScale * factor));
                    float applied = next / currentScale;
                    currentScale = next;

                    drawMatrix.postScale(applied, applied, detector.getFocusX(), detector.getFocusY());
                    clampMatrix();
                    setImageMatrix(drawMatrix);
                    return true;
                }

                @Override
                public void onScaleEnd(ScaleGestureDetector detector) {
                    if (currentScale <= 1.02f) {
                        resetMatrix();
                    } else {
                        clampMatrix();
                        setImageMatrix(drawMatrix);
                    }
                }
            });

            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }

                @Override
                public void onLongPress(MotionEvent e) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    showPageActions(pageIndex);
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (currentScale > 1.05f) {
                        resetMatrix();
                    } else {
                        currentScale = 2f;
                        drawMatrix.postScale(2f, 2f, e.getX(), e.getY());
                        clampMatrix();
                        setImageMatrix(drawMatrix);
                    }
                    return true;
                }
            });
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            resetMatrix();
        }

        private void resetMatrix() {
            if (getDrawable() == null || getWidth() <= 0 || getHeight() <= 0) return;

            float dw = getDrawable().getIntrinsicWidth();
            float dh = getDrawable().getIntrinsicHeight();
            if (dw <= 0 || dh <= 0) return;

            baseScale = Math.min(getWidth() / dw, getHeight() / dh);
            float dx = (getWidth() - dw * baseScale) * 0.5f;
            float dy = (getHeight() - dh * baseScale) * 0.5f;

            drawMatrix.reset();
            drawMatrix.postScale(baseScale, baseScale);
            drawMatrix.postTranslate(dx, dy);
            currentScale = 1f;
            setImageMatrix(drawMatrix);
            getParent().requestDisallowInterceptTouchEvent(false);
        }

        private void clampMatrix() {
            if (getDrawable() == null || getWidth() <= 0 || getHeight() <= 0) return;

            drawMatrix.getValues(matrixValues);
            float scaleX = matrixValues[Matrix.MSCALE_X];
            float scaleY = matrixValues[Matrix.MSCALE_Y];
            float transX = matrixValues[Matrix.MTRANS_X];
            float transY = matrixValues[Matrix.MTRANS_Y];

            float contentW = getDrawable().getIntrinsicWidth() * scaleX;
            float contentH = getDrawable().getIntrinsicHeight() * scaleY;

            float targetX;
            if (contentW <= getWidth()) {
                targetX = (getWidth() - contentW) * 0.5f;
            } else {
                float minX = getWidth() - contentW;
                targetX = Math.max(minX, Math.min(0f, transX));
            }

            float targetY;
            if (contentH <= getHeight()) {
                targetY = (getHeight() - contentH) * 0.5f;
            } else {
                float minY = getHeight() - contentH;
                targetY = Math.max(minY, Math.min(0f, transY));
            }

            drawMatrix.postTranslate(targetX - transX, targetY - transY);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    if (currentScale > 1.02f) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress() && currentScale > 1.02f) {
                        float dx = event.getX() - lastX;
                        float dy = event.getY() - lastY;
                        drawMatrix.postTranslate(dx, dy);
                        clampMatrix();
                        setImageMatrix(drawMatrix);
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    lastX = event.getX();
                    lastY = event.getY();
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    clampMatrix();
                    setImageMatrix(drawMatrix);
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (currentScale <= 1.02f) {
                        resetMatrix();
                    } else {
                        clampMatrix();
                        setImageMatrix(drawMatrix);
                    }
                    break;
            }
            return true;
        }
    }

    private static class RawPdfAdapter extends PrintDocumentAdapter {
        private final File file;
        private final String name;

        RawPdfAdapter(File file, String name) {
            this.file = file;
            this.name = name;
        }

        @Override
        public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                             android.os.CancellationSignal cancellationSignal,
                             LayoutResultCallback callback, Bundle extras) {
            if (cancellationSignal.isCanceled()) {
                callback.onLayoutCancelled();
                return;
            }
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
                        if (cancellationSignal.isCanceled()) {
                            callback.onWriteCancelled();
                            return;
                        }
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
