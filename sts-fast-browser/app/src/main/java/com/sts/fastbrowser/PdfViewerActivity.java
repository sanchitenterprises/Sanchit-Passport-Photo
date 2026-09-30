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
import android.graphics.Canvas;
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
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDAction;
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfViewerActivity extends Activity {
    private static final int REQ_STORAGE = 701;

    private String sourceUrl;
    private Uri sourceUri;
    private String fileName;
    private String cookie;
    private String userAgent;
    private int browserSlot = 1;

    private File sourceFile;
    private File pdfFile;
    private ParcelFileDescriptor pdfPfd;
    private PdfRenderer pdfRenderer;
    private PDDocument textDocument;

    private LinearLayout pages;
    private TextView status;
    private boolean destroyed = false;
    private final Map<Integer, List<PdfLink>> pageLinks = new HashMap<>();
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
        browserSlot = getIntent().getIntExtra("pdf_slot", 1);
        if (browserSlot != 2) browserSlot = 1;

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
                extractPdfLinks();
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

    private void extractPdfLinks() {
        pageLinks.clear();
        if (textDocument == null) return;

        try {
            int pageCount = textDocument.getNumberOfPages();
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                PDPage page = textDocument.getPage(pageIndex);
                PDRectangle box = page.getCropBox();
                if (box == null) box = page.getMediaBox();
                if (box == null) continue;

                float pageW = box.getWidth();
                float pageH = box.getHeight();
                float boxLeft = box.getLowerLeftX();
                float boxBottom = box.getLowerLeftY();
                List<PdfLink> links = new ArrayList<>();

                List<PDAnnotation> annotations = page.getAnnotations();
                if (annotations == null) continue;

                for (PDAnnotation annotation : annotations) {
                    if (!(annotation instanceof PDAnnotationLink)) continue;
                    PDAnnotationLink link = (PDAnnotationLink) annotation;
                    PDAction action = link.getAction();
                    if (!(action instanceof PDActionURI)) continue;

                    String uri = ((PDActionURI) action).getURI();
                    if (TextUtils.isEmpty(uri)) continue;
                    String lower = uri.toLowerCase(Locale.ROOT);
                    if (!(lower.startsWith("http://") || lower.startsWith("https://"))) continue;

                    PDRectangle rect = link.getRectangle();
                    if (rect == null) continue;

                    float left = rect.getLowerLeftX() - boxLeft;
                    float right = rect.getUpperRightX() - boxLeft;
                    float bottom = rect.getLowerLeftY() - boxBottom;
                    float top = rect.getUpperRightY() - boxBottom;

                    links.add(new PdfLink(
                            uri,
                            Math.min(left, right),
                            Math.max(left, right),
                            Math.min(bottom, top),
                            Math.max(bottom, top),
                            pageW,
                            pageH
                    ));
                }

                if (!links.isEmpty()) pageLinks.put(pageIndex, links);
            }
        } catch (Exception ignored) {
            pageLinks.clear();
        }
    }

    private void openPdfLinkInBrowser(String url) {
        if (TextUtils.isEmpty(url)) return;
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("browser_open_url", url);
            intent.putExtra("browser_slot", browserSlot);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Link open नहीं हो पाया", Toast.LENGTH_SHORT).show();
        }
    }

    private static class PdfLink {
        final String url;
        final float left;
        final float right;
        final float bottom;
        final float top;
        final float pageWidth;
        final float pageHeight;

        PdfLink(String url, float left, float right, float bottom, float top,
                float pageWidth, float pageHeight) {
            this.url = url;
            this.left = left;
            this.right = right;
            this.bottom = bottom;
            this.top = top;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
        }

        boolean contains(float x, float y) {
            return x >= left && x <= right && y >= bottom && y <= top;
        }
    }

    private void renderAllPages() throws Exception {
        runOnUiThread(() -> pages.removeAllViews());

        int count;
        synchronized (this) {
            count = pdfRenderer.getPageCount();
        }

        for (int i = 0; i < count && !destroyed; i++) {
            final int pageIndex = i;
            final float aspect = getPageAspectRatio(pageIndex);
            runOnUiThread(() -> addPageView(pageIndex, aspect));
        }
    }

    private synchronized float getPageAspectRatio(int pageIndex) throws Exception {
        if (pdfRenderer == null) throw new IllegalStateException("PDF renderer unavailable");
        PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);
        try {
            return page.getHeight() / (float) Math.max(1, page.getWidth());
        } finally {
            page.close();
        }
    }

    private synchronized Bitmap renderPageBitmap(int pageIndex, int targetW) throws Exception {
        if (pdfRenderer == null) throw new IllegalStateException("PDF renderer unavailable");
        PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);
        try {
            int targetH = Math.max(1, Math.round(targetW * (page.getHeight() / (float) page.getWidth())));
            Bitmap bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            return bmp;
        } finally {
            page.close();
        }
    }

    private synchronized Bitmap renderPageViewport(int pageIndex, int viewW, int viewH,
                                                    float zoom, float offsetX, float offsetY) throws Exception {
        if (pdfRenderer == null) throw new IllegalStateException("PDF renderer unavailable");
        PdfRenderer.Page page = pdfRenderer.openPage(pageIndex);
        try {
            Bitmap bmp = Bitmap.createBitmap(Math.max(1, viewW), Math.max(1, viewH), Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);

            float fitScale = viewW / (float) Math.max(1, page.getWidth());
            Matrix transform = new Matrix();
            transform.postScale(fitScale * zoom, fitScale * zoom);
            transform.postTranslate(offsetX, offsetY);
            page.render(bmp, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bmp;
        } finally {
            page.close();
        }
    }

    private void addPageView(int pageIndex, float aspect) {
        if (destroyed) return;

        ZoomPageView iv = new ZoomPageView(this, pageIndex);
        iv.setBackgroundColor(Color.WHITE);
        iv.setContentDescription("PDF page " + (pageIndex + 1));

        int pageWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels - dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(dp(120), Math.round(pageWidth * aspect))
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
                int width = 3000;
                bmp = renderPageBitmap(pageIndex, width);
                String base = fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")
                        ? fileName.substring(0, fileName.length() - 4)
                        : fileName;

                boolean png = "png".equalsIgnoreCase(format);
                String ext = png ? ".png" : ".jpg";
                String mime = png ? "image/png" : "image/jpeg";
                String outName = sanitize(base) + "_Page_" + (pageIndex + 1) + ext;
                Bitmap.CompressFormat compressFormat = png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
                int quality = 100;

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
        if (pages != null) {
            for (int i = 0; i < pages.getChildCount(); i++) {
                View child = pages.getChildAt(i);
                if (child instanceof ZoomPageView) ((ZoomPageView) child).releaseBitmap();
            }
        }
        closePdfResources();
        super.onDestroy();
    }

    private class ZoomPageView extends View {
        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector gestureDetector;
        private final int pageIndex;

        private Bitmap renderedBitmap;
        private float renderedScale = 1f;
        private float renderedOffsetX = 0f;
        private float renderedOffsetY = 0f;

        private float currentScale = 1f;
        private float offsetX = 0f;
        private float offsetY = 0f;
        private float lastX;
        private float lastY;
        private int renderGeneration = 0;

        ZoomPageView(Context context, int pageIndex) {
            super(context);
            this.pageIndex = pageIndex;
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

                    float focusX = detector.getFocusX();
                    float focusY = detector.getFocusY();
                    offsetX = focusX - ((focusX - offsetX) * applied);
                    offsetY = focusY - ((focusY - offsetY) * applied);
                    clampOffsets();
                    invalidate();
                    return true;
                }

                @Override
                public void onScaleEnd(ScaleGestureDetector detector) {
                    if (currentScale <= 1.02f) resetZoom();
                    else {
                        clampOffsets();
                        requestSharpRender();
                    }
                    getParent().requestDisallowInterceptTouchEvent(currentScale > 1.02f);
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
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    String url = findLinkAt(e.getX(), e.getY());
                    if (url != null) {
                        openPdfLinkInBrowser(url);
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (currentScale > 1.05f) {
                        resetZoom();
                    } else {
                        float applied = 2f / currentScale;
                        currentScale = 2f;
                        offsetX = e.getX() - ((e.getX() - offsetX) * applied);
                        offsetY = e.getY() - ((e.getY() - offsetY) * applied);
                        clampOffsets();
                        invalidate();
                        requestSharpRender();
                    }
                    return true;
                }
            });
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w <= 0 || h <= 0) return;
            if (oldw != w || oldh != h) {
                currentScale = 1f;
                offsetX = 0f;
                offsetY = 0f;
                requestSharpRender();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.WHITE);
            Bitmap bmp = renderedBitmap;
            if (bmp == null || bmp.isRecycled()) return;

            float safeRenderedScale = Math.max(0.0001f, renderedScale);
            float ratio = currentScale / safeRenderedScale;
            float translateX = offsetX - (renderedOffsetX * ratio);
            float translateY = offsetY - (renderedOffsetY * ratio);

            canvas.save();
            canvas.translate(translateX, translateY);
            canvas.scale(ratio, ratio);
            canvas.drawBitmap(bmp, 0f, 0f, null);
            canvas.restore();
        }

        private void requestSharpRender() {
            if (destroyed || getWidth() <= 0 || getHeight() <= 0) return;

            final int generation = ++renderGeneration;
            final int width = getWidth();
            final int height = getHeight();
            final float scale = currentScale;
            final float tx = offsetX;
            final float ty = offsetY;

            new Thread(() -> {
                Bitmap fresh = null;
                try {
                    fresh = renderPageViewport(pageIndex, width, height, scale, tx, ty);
                    final Bitmap result = fresh;
                    runOnUiThread(() -> {
                        if (destroyed || generation != renderGeneration || getWidth() != width || getHeight() != height) {
                            if (!result.isRecycled()) result.recycle();
                            return;
                        }

                        Bitmap old = renderedBitmap;
                        renderedBitmap = result;
                        renderedScale = scale;
                        renderedOffsetX = tx;
                        renderedOffsetY = ty;
                        invalidate();
                        if (old != null && old != result && !old.isRecycled()) old.recycle();
                    });
                } catch (Exception e) {
                    if (fresh != null && !fresh.isRecycled()) fresh.recycle();
                }
            }, "SFB-PDF-Render-" + pageIndex + "-" + generation).start();
        }

        private void resetZoom() {
            currentScale = 1f;
            offsetX = 0f;
            offsetY = 0f;
            clampOffsets();
            invalidate();
            requestSharpRender();
            getParent().requestDisallowInterceptTouchEvent(false);
        }

        private void clampOffsets() {
            if (getWidth() <= 0 || getHeight() <= 0) return;

            float contentW = getWidth() * currentScale;
            float contentH = getHeight() * currentScale;

            if (contentW <= getWidth()) {
                offsetX = (getWidth() - contentW) * 0.5f;
            } else {
                float minX = getWidth() - contentW;
                offsetX = Math.max(minX, Math.min(0f, offsetX));
            }

            if (contentH <= getHeight()) {
                offsetY = (getHeight() - contentH) * 0.5f;
            } else {
                float minY = getHeight() - contentH;
                offsetY = Math.max(minY, Math.min(0f, offsetY));
            }
        }

        private String findLinkAt(float viewX, float viewY) {
            List<PdfLink> links = pageLinks.get(pageIndex);
            if (links == null || links.isEmpty() || getWidth() <= 0 || getHeight() <= 0) return null;

            float contentX = (viewX - offsetX) / currentScale;
            float contentY = (viewY - offsetY) / currentScale;
            if (contentX < 0f || contentY < 0f || contentX > getWidth() || contentY > getHeight()) return null;

            for (PdfLink link : links) {
                float pdfX = (contentX / getWidth()) * link.pageWidth;
                float pdfY = link.pageHeight - ((contentY / getHeight()) * link.pageHeight);
                if (link.contains(pdfX, pdfY)) return link.url;
            }
            return null;
        }

        void releaseBitmap() {
            renderGeneration++;
            Bitmap bmp = renderedBitmap;
            renderedBitmap = null;
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    if (currentScale > 1.02f) getParent().requestDisallowInterceptTouchEvent(true);
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress() && currentScale > 1.02f) {
                        float dx = event.getX() - lastX;
                        float dy = event.getY() - lastY;
                        offsetX += dx;
                        offsetY += dy;
                        clampOffsets();
                        invalidate();
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    lastX = event.getX();
                    lastY = event.getY();
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    clampOffsets();
                    invalidate();
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (currentScale <= 1.02f) {
                        if (currentScale != 1f || offsetX != 0f || offsetY != 0f) resetZoom();
                        else getParent().requestDisallowInterceptTouchEvent(false);
                    } else {
                        clampOffsets();
                        invalidate();
                        requestSharpRender();
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
