package com.sts.fastbrowser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class ImageViewerActivity extends Activity {
    private static final int REQ_STORAGE = 801;

    private Uri sourceUri;
    private String fileName;
    private File sourceFile;
    private File shareFile;
    private Bitmap bitmap;

    private ImageCanvas imageCanvas;
    private LinearLayout editBar;
    private boolean cropMode = false;
    private String pendingSaveAction;
    private int targetKb = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#355C62"));
        getWindow().setNavigationBarColor(Color.parseColor("#F2F5F4"));

        sourceUri = getIntent().getData();
        fileName = resolveDisplayName(sourceUri);
        if (TextUtils.isEmpty(fileName)) fileName = "Image.jpg";

        setTitle(fileName);
        setContentView(buildUi());
        loadImage();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

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

        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.BLACK);
        imageCanvas = new ImageCanvas(this);
        stage.addView(imageCanvas, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        editBar = new LinearLayout(this);
        editBar.setOrientation(LinearLayout.HORIZONTAL);
        editBar.setGravity(Gravity.CENTER);
        editBar.setPadding(dp(6), dp(4), dp(6), dp(4));
        editBar.setBackgroundColor(Color.parseColor("#E3EAF4"));
        editBar.setVisibility(View.GONE);
        root.addView(editBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

        return root;
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor, Gravity.END);
        pm.getMenu().add("Share");
        pm.getMenu().add("Print");
        pm.getMenu().add("Save");
        pm.getMenu().add("Edit");
        pm.setOnMenuItemClickListener(item -> {
            if (bitmap == null) {
                Toast.makeText(this, "Image अभी तैयार हो रही है", Toast.LENGTH_SHORT).show();
                return true;
            }
            String t = String.valueOf(item.getTitle());
            if ("Share".equals(t)) shareImage();
            else if ("Print".equals(t)) printImage();
            else if ("Save".equals(t)) showSaveFormats();
            else if ("Edit".equals(t)) enterEditMode();
            return true;
        });
        pm.show();
    }

    private void loadImage() {
        new Thread(() -> {
            try {
                if (sourceUri == null) throw new Exception("Missing image URI");
                File dir = new File(getCacheDir(), "image");
                if (!dir.exists()) dir.mkdirs();
                sourceFile = new File(dir, "source_" + System.currentTimeMillis() + "_" + sanitize(fileName));

                try (InputStream in = getContentResolver().openInputStream(sourceUri);
                     OutputStream out = new FileOutputStream(sourceFile)) {
                    if (in == null) throw new Exception("Unable to read image");
                    copy(in, out);
                }

                Bitmap decoded = BitmapFactory.decodeFile(sourceFile.getAbsolutePath());
                if (decoded == null) throw new Exception("Unsupported image");
                decoded = applyExifOrientation(decoded, sourceFile);

                final Bitmap loaded = decoded;
                runOnUiThread(() -> setBitmap(loaded));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Image open नहीं हो पाई", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private Bitmap applyExifOrientation(Bitmap src, File file) {
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix m = new Matrix();
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) m.postRotate(90);
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) m.postRotate(180);
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) m.postRotate(270);
            else return src;

            Bitmap rotated = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
            if (rotated != src) src.recycle();
            return rotated;
        } catch (Exception ignored) {
            return src;
        }
    }

    private void setBitmap(Bitmap newBitmap) {
        Bitmap old = bitmap;
        bitmap = newBitmap;
        imageCanvas.setBitmap(bitmap);
        if (old != null && old != newBitmap && !old.isRecycled()) old.recycle();
    }

    private void enterEditMode() {
        cropMode = false;
        imageCanvas.setCropMode(false);
        buildMainEditBar();
        editBar.setVisibility(View.VISIBLE);
    }

    private void buildMainEditBar() {
        editBar.removeAllViews();
        addEditIcon(R.drawable.ic_crop, "Crop", v -> beginCrop());
        addEditIcon(R.drawable.ic_rotate, "Rotate", v -> rotateImage());
        addEditIcon(R.drawable.ic_resize, "Resize", v -> showResizeDialog());
        addEditIcon(R.drawable.ic_done, "Done", v -> {
            cropMode = false;
            imageCanvas.setCropMode(false);
            editBar.setVisibility(View.GONE);
        });
    }

    private void buildCropBar() {
        editBar.removeAllViews();
        addEditIcon(R.drawable.ic_done, "Apply Crop", v -> applyCrop());
        addEditIcon(R.drawable.ic_cancel, "Cancel Crop", v -> {
            cropMode = false;
            imageCanvas.setCropMode(false);
            buildMainEditBar();
        });
    }

    private void addEditIcon(int resId, String description, View.OnClickListener listener) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setGravity(Gravity.CENTER);
        holder.setPadding(dp(1), dp(2), dp(1), dp(1));

        ImageButton b = new ImageButton(this);
        b.setImageResource(resId);
        b.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        b.setPadding(dp(7), dp(5), dp(7), dp(5));
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setContentDescription(description);
        b.setOnClickListener(listener);

        TextView label = new TextView(this);
        label.setText(description);
        label.setTextSize(9);
        label.setTextColor(Color.parseColor("#162326"));
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setOnClickListener(listener);

        holder.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        holder.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        editBar.addView(holder, lp);
    }

    private void beginCrop() {
        if (bitmap == null) return;
        cropMode = true;
        imageCanvas.setCropMode(true);
        buildCropBar();
    }

    private void applyCrop() {
        if (!cropMode || bitmap == null) return;
        Bitmap cropped = imageCanvas.createCroppedBitmap();
        if (cropped == null) {
            Toast.makeText(this, "Crop area सही नहीं है", Toast.LENGTH_SHORT).show();
            return;
        }
        cropMode = false;
        imageCanvas.setCropMode(false);
        setBitmap(cropped);
        buildMainEditBar();
    }

    private void rotateImage() {
        if (bitmap == null) return;
        Matrix m = new Matrix();
        m.postRotate(90);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
        setBitmap(rotated);
    }

    private void showResizeDialog() {
        if (bitmap == null) return;

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(4), dp(20), 0);

        EditText width = new EditText(this);
        width.setHint("Width");
        width.setText(String.valueOf(bitmap.getWidth()));
        width.setInputType(InputType.TYPE_CLASS_NUMBER);

        EditText height = new EditText(this);
        height.setHint("Height");
        height.setText(String.valueOf(bitmap.getHeight()));
        height.setInputType(InputType.TYPE_CLASS_NUMBER);

        EditText kb = new EditText(this);
        kb.setHint("Target Size KB (optional)");
        if (targetKb > 0) kb.setText(String.valueOf(targetKb));
        kb.setInputType(InputType.TYPE_CLASS_NUMBER);

        body.addView(width, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        body.addView(height, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        body.addView(kb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Resize Image")
                .setMessage("Width, Height pixel में और जरूरत हो तो target KB डालें")
                .setView(body)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Resize", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int w = Integer.parseInt(width.getText().toString().trim());
                int h = Integer.parseInt(height.getText().toString().trim());
                if (w < 32 || h < 32 || w > 12000 || h > 12000) {
                    Toast.makeText(this, "Size 32 से 12000 px के बीच रखें", Toast.LENGTH_SHORT).show();
                    return;
                }
                String kbText = kb.getText().toString().trim();
                int requestedKb = kbText.isEmpty() ? 0 : Integer.parseInt(kbText);
                if (requestedKb < 0 || requestedKb > 50000) {
                    Toast.makeText(this, "KB 1 से 50000 के बीच रखें या खाली छोड़ें", Toast.LENGTH_SHORT).show();
                    return;
                }
                targetKb = requestedKb;
                Bitmap resized = Bitmap.createScaledBitmap(bitmap, w, h, true);
                setBitmap(resized);
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(this, "सही Width और Height डालें", Toast.LENGTH_SHORT).show();
            }
        }));
        dialog.show();
    }

    private void shareImage() {
        try {
            shareFile = writeCurrentBitmapToCache();
            Uri uri = Uri.parse("content://" + getPackageName() + ".pdfshare/image/" + Uri.encode(shareFile.getName()));
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(getImageMime(shareFile.getName()));
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share Image"));
        } catch (Exception e) {
            Toast.makeText(this, "Share नहीं हो पाया", Toast.LENGTH_SHORT).show();
        }
    }

    private void printImage() {
        if (bitmap == null) return;
        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
        pm.print(fileName, new ImagePrintAdapter(this, bitmap, fileName), new PrintAttributes.Builder().build());
    }

    private void showSaveFormats() {
        new AlertDialog.Builder(this)
                .setTitle("Save Image")
                .setItems(new String[]{"JPG", "PNG", "PDF"}, (d, which) -> {
                    if (which == 0) saveCurrentAs("jpg");
                    else if (which == 1) saveCurrentAs("png");
                    else saveCurrentAs("pdf");
                })
                .show();
    }

    private void saveCurrentAs(String format) {
        if (android.os.Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingSaveAction = "save:" + format;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }

        new Thread(() -> {
            try {
                if ("pdf".equals(format)) {
                    saveAsPdf();
                } else {
                    saveAsImage(format);
                }
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Image " + format.toUpperCase(Locale.ROOT) + " में save हो गई",
                        Toast.LENGTH_LONG
                ).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Save नहीं हो पाया", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void saveAsImage(String format) throws Exception {
        boolean png = "png".equals(format);
        String ext = png ? ".png" : ".jpg";
        String mime = png ? "image/png" : "image/jpeg";
        String outName = baseName(fileName) + "_edited" + ext;
        byte[] data = encodeForSave(bitmap, png, targetKb);

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, outName);
            v.put(MediaStore.Images.Media.MIME_TYPE, mime);
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/STS Fast Browser");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("Unable to create file");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("Unable to write image");
                out.write(data);
                out.flush();
            }
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "STS Fast Browser");
            if (!dir.exists()) dir.mkdirs();
            File out = uniqueFile(dir, outName, ext);
            try (OutputStream os = new FileOutputStream(out)) {
                os.write(data);
                os.flush();
            }
        }
    }

    private void saveAsPdf() throws Exception {
        String outName = baseName(fileName) + "_edited.pdf";
        File tmp = new File(getCacheDir(), "image_pdf_" + System.currentTimeMillis() + ".pdf");
        PdfDocument document = new PdfDocument();
        try {
            int pageW = Math.max(1, bitmap.getWidth());
            int pageH = Math.max(1, bitmap.getHeight());
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageW, pageH, 1).create();
            PdfDocument.Page page = document.startPage(info);
            page.getCanvas().drawBitmap(bitmap, 0, 0, null);
            document.finishPage(page);
            try (OutputStream out = new FileOutputStream(tmp)) {
                document.writeTo(out);
            }
        } finally {
            document.close();
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
            File out = uniqueFile(dir, outName, ".pdf");
            try (InputStream in = new FileInputStream(tmp);
                 OutputStream os = new FileOutputStream(out)) {
                copy(in, os);
            }
        }
        tmp.delete();
    }

    private byte[] encodeForSave(Bitmap source, boolean png, int targetKb) throws Exception {
        if (png) {
            Bitmap working = source;
            boolean owns = false;
            byte[] data;
            while (true) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                if (!working.compress(Bitmap.CompressFormat.PNG, 100, baos)) throw new Exception("PNG encode failed");
                data = baos.toByteArray();
                if (targetKb <= 0 || data.length <= targetKb * 1024L || working.getWidth() < 160 || working.getHeight() < 160) break;
                Bitmap smaller = Bitmap.createScaledBitmap(
                        working,
                        Math.max(1, Math.round(working.getWidth() * 0.90f)),
                        Math.max(1, Math.round(working.getHeight() * 0.90f)),
                        true
                );
                if (owns && working != source) working.recycle();
                working = smaller;
                owns = true;
            }
            if (owns && working != source) working.recycle();
            return data;
        }

        int lo = 18, hi = 95, bestQ = 18;
        byte[] best = null;
        while (lo <= hi) {
            int q = (lo + hi) / 2;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (!source.compress(Bitmap.CompressFormat.JPEG, q, baos)) throw new Exception("JPG encode failed");
            byte[] data = baos.toByteArray();
            if (targetKb <= 0) return data;
            if (data.length <= targetKb * 1024L) {
                best = data;
                bestQ = q;
                lo = q + 1;
            } else {
                hi = q - 1;
            }
        }
        if (best != null) return best;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        source.compress(Bitmap.CompressFormat.JPEG, bestQ, baos);
        return baos.toByteArray();
    }

    private File writeCurrentBitmapToCache() throws Exception {
        File dir = new File(getCacheDir(), "image");
        if (!dir.exists()) dir.mkdirs();

        boolean png = fileName.toLowerCase(Locale.ROOT).endsWith(".png");
        String ext = png ? ".png" : ".jpg";
        File out = new File(dir, "share_" + System.currentTimeMillis() + ext);
        try (OutputStream os = new FileOutputStream(out)) {
            if (!bitmap.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, png ? 100 : 95, os)) {
                throw new Exception("Unable to prepare image");
            }
        }
        return out;
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
        return TextUtils.isEmpty(last) ? null : last;
    }

    private String baseName(String name) {
        String safe = sanitize(name);
        int dot = safe.lastIndexOf('.');
        return dot > 0 ? safe.substring(0, dot) : safe;
    }

    private String sanitize(String s) {
        return s == null ? "Image" : s.replaceAll("[^a-zA-Z0-9._ -]", "_");
    }

    private String getImageMime(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
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
        byte[] buf = new byte[32768];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            String action = pendingSaveAction;
            pendingSaveAction = null;
            if (action != null && action.startsWith("save:")) {
                saveCurrentAs(action.substring(5));
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        bitmap = null;
        super.onDestroy();
    }

    private class ImageCanvas extends View {
        private Bitmap image;
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint shadePaint = new Paint();
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF imageRect = new RectF();
        private final RectF baseRect = new RectF();
        private final Path cropPath = new Path();
        private final float[][] handles = new float[8][2];

        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector gestureDetector;

        private boolean cropEnabled = false;
        private int dragMode = -1;
        private float lastX;
        private float lastY;
        private float startAspect = 1f;
        private float zoom = 1f;
        private float panX = 0f;
        private float panY = 0f;
        private final float handleRadius = dp(12);
        private final float minCrop = dp(48);

        ImageCanvas(Activity context) {
            super(context);
            shadePaint.setColor(0x99000000);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(2));
            borderPaint.setColor(Color.WHITE);
            handlePaint.setColor(Color.WHITE);
            setBackgroundColor(Color.BLACK);

            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScaleBegin(ScaleGestureDetector detector) {
                    return !cropEnabled;
                }

                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    if (cropEnabled || image == null) return false;
                    float factor = detector.getScaleFactor();
                    if (Float.isNaN(factor) || Float.isInfinite(factor)) return false;

                    float oldZoom = zoom;
                    float newZoom = Math.max(1f, Math.min(5f, oldZoom * factor));
                    if (Math.abs(newZoom - oldZoom) < 0.0001f) return true;

                    float cx = getWidth() * 0.5f;
                    float cy = getHeight() * 0.5f;
                    float ratio = newZoom / oldZoom;
                    panX = detector.getFocusX() - cx - (detector.getFocusX() - cx - panX) * ratio;
                    panY = detector.getFocusY() - cy - (detector.getFocusY() - cy - panY) * ratio;
                    zoom = newZoom;
                    clampPan();
                    invalidate();
                    return true;
                }

                @Override
                public void onScaleEnd(ScaleGestureDetector detector) {
                    if (zoom <= 1.02f) resetZoom();
                    else {
                        clampPan();
                        invalidate();
                    }
                }
            });

            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (cropEnabled) return false;
                    if (zoom > 1.05f) {
                        resetZoom();
                    } else {
                        zoom = 2f;
                        float cx = getWidth() * 0.5f;
                        float cy = getHeight() * 0.5f;
                        panX = -(e.getX() - cx);
                        panY = -(e.getY() - cy);
                        clampPan();
                        invalidate();
                    }
                    return true;
                }
            });
        }

        void setBitmap(Bitmap bitmap) {
            this.image = bitmap;
            cropEnabled = false;
            dragMode = -1;
            resetZoom();
            invalidate();
        }

        void setCropMode(boolean enabled) {
            cropEnabled = enabled;
            dragMode = -1;
            if (enabled) {
                resetZoom();
                resetCropHandles();
            }
            invalidate();
        }

        private void resetZoom() {
            zoom = 1f;
            panX = 0f;
            panY = 0f;
            updateImageRect();
            invalidate();
        }

        private void updateImageRect() {
            imageRect.setEmpty();
            baseRect.setEmpty();
            if (image == null || getWidth() <= 0 || getHeight() <= 0) return;

            float fit = Math.min(getWidth() / (float) image.getWidth(), getHeight() / (float) image.getHeight());
            float bw = image.getWidth() * fit;
            float bh = image.getHeight() * fit;
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            baseRect.set(cx - bw * 0.5f, cy - bh * 0.5f, cx + bw * 0.5f, cy + bh * 0.5f);

            float w = bw * zoom;
            float h = bh * zoom;
            imageRect.set(
                    cx - w * 0.5f + panX,
                    cy - h * 0.5f + panY,
                    cx + w * 0.5f + panX,
                    cy + h * 0.5f + panY
            );
        }

        private void clampPan() {
            if (image == null || getWidth() <= 0 || getHeight() <= 0) return;
            if (zoom <= 1f) {
                panX = 0f;
                panY = 0f;
                zoom = 1f;
                return;
            }

            float fit = Math.min(getWidth() / (float) image.getWidth(), getHeight() / (float) image.getHeight());
            float scaledW = image.getWidth() * fit * zoom;
            float scaledH = image.getHeight() * fit * zoom;
            float maxX = Math.max(0f, (scaledW - getWidth()) * 0.5f);
            float maxY = Math.max(0f, (scaledH - getHeight()) * 0.5f);
            panX = Math.max(-maxX, Math.min(maxX, panX));
            panY = Math.max(-maxY, Math.min(maxY, panY));
        }

        private void resetCropHandles() {
            updateImageRect();
            if (imageRect.isEmpty()) return;
            float mx = imageRect.width() * 0.08f;
            float my = imageRect.height() * 0.08f;
            float l = imageRect.left + mx;
            float t = imageRect.top + my;
            float r = imageRect.right - mx;
            float b = imageRect.bottom - my;
            handles[0][0] = l; handles[0][1] = t;
            handles[1][0] = (l+r)*0.5f; handles[1][1] = t;
            handles[2][0] = r; handles[2][1] = t;
            handles[3][0] = r; handles[3][1] = (t+b)*0.5f;
            handles[4][0] = r; handles[4][1] = b;
            handles[5][0] = (l+r)*0.5f; handles[5][1] = b;
            handles[6][0] = l; handles[6][1] = b;
            handles[7][0] = l; handles[7][1] = (t+b)*0.5f;
        }

        private void rebuildCropPath() {
            cropPath.reset();
            cropPath.moveTo(handles[0][0], handles[0][1]);
            for (int i = 1; i < 8; i++) cropPath.lineTo(handles[i][0], handles[i][1]);
            cropPath.close();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            updateImageRect();
            if (image == null || imageRect.isEmpty()) return;

            canvas.drawBitmap(image, null, imageRect, bitmapPaint);

            if (cropEnabled) {
                rebuildCropPath();
                canvas.drawRect(imageRect, shadePaint);

                canvas.save();
                canvas.clipPath(cropPath);
                canvas.drawBitmap(image, null, imageRect, bitmapPaint);
                canvas.restore();

                canvas.drawPath(cropPath, borderPaint);
                float hr = handleRadius * 0.55f;
                for (int i = 0; i < 8; i++) {
                    canvas.drawCircle(handles[i][0], handles[i][1], hr, handlePaint);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (image == null) return true;

            if (!cropEnabled) {
                scaleDetector.onTouchEvent(event);
                gestureDetector.onTouchEvent(event);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getX();
                        lastY = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (!scaleDetector.isInProgress() && event.getPointerCount() == 1 && zoom > 1.02f) {
                            panX += event.getX() - lastX;
                            panY += event.getY() - lastY;
                            clampPan();
                            invalidate();
                        }
                        lastX = event.getX();
                        lastY = event.getY();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        clampPan();
                        invalidate();
                        break;
                }
                return true;
            }

            float x = event.getX();
            float y = event.getY();

            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                dragMode = detectDragMode(x, y);
                if (dragMode >= 0 && dragMode <= 7) {
                    int opposite = oppositeCornerForHandle(dragMode);
                    if (opposite >= 0) {
                        startAspect = Math.max(
                                0.01f,
                                Math.abs(handles[dragMode][0] - handles[opposite][0]) /
                                        Math.max(1f, Math.abs(handles[dragMode][1] - handles[opposite][1]))
                        );
                    }
                }
                lastX = x;
                lastY = y;
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && dragMode != -1) {
                float dx = x - lastX;
                float dy = y - lastY;
                moveCrop(dx, dy);
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                dragMode = -1;
                return true;
            }
            return true;
        }

        private int detectDragMode(float x, float y) {
            float r = handleRadius * 1.8f;
            for (int i = 0; i < 8; i++) {
                if (distance(x, y, handles[i][0], handles[i][1]) <= r) return i;
            }
            if (pointInCropBounds(x, y)) return 8;
            return -1;
        }

        private boolean pointInCropBounds(float x, float y) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                minX = Math.min(minX, handles[i][0]);
                minY = Math.min(minY, handles[i][1]);
                maxX = Math.max(maxX, handles[i][0]);
                maxY = Math.max(maxY, handles[i][1]);
            }
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }

        private int oppositeCornerForHandle(int index) {
            if (index == 0) return 4;
            if (index == 2) return 6;
            if (index == 4) return 0;
            if (index == 6) return 2;
            return -1;
        }

        private float distance(float x1, float y1, float x2, float y2) {
            float dx = x1 - x2;
            float dy = y1 - y2;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private void moveCrop(float dx, float dy) {
            if (dragMode == 8) {
                moveAllHandles(dx, dy);
                return;
            }

            // Middle handles (1,3,5,7) are free: only the grabbed middle point moves.
            if (dragMode == 1 || dragMode == 3 || dragMode == 5 || dragMode == 7) {
                handles[dragMode][0] = clamp(handles[dragMode][0] + dx, imageRect.left, imageRect.right);
                handles[dragMode][1] = clamp(handles[dragMode][1] + dy, imageRect.top, imageRect.bottom);
                return;
            }

            // Four corners are linked as one crop rectangle.
            // Moving one corner also moves the two adjacent corners on the same X/Y edges.
            if (dragMode == 0) { // top-left; bottom-right stays anchored
                float newX = clamp(handles[0][0] + dx, imageRect.left, handles[4][0] - minCrop);
                float newY = clamp(handles[0][1] + dy, imageRect.top, handles[4][1] - minCrop);
                handles[0][0] = newX; handles[0][1] = newY;
                handles[2][1] = newY; // top-right follows top edge
                handles[6][0] = newX; // bottom-left follows left edge
            } else if (dragMode == 2) { // top-right; bottom-left stays anchored
                float newX = clamp(handles[2][0] + dx, handles[6][0] + minCrop, imageRect.right);
                float newY = clamp(handles[2][1] + dy, imageRect.top, handles[6][1] - minCrop);
                handles[2][0] = newX; handles[2][1] = newY;
                handles[0][1] = newY; // top-left follows top edge
                handles[4][0] = newX; // bottom-right follows right edge
            } else if (dragMode == 4) { // bottom-right; top-left stays anchored
                float newX = clamp(handles[4][0] + dx, handles[0][0] + minCrop, imageRect.right);
                float newY = clamp(handles[4][1] + dy, handles[0][1] + minCrop, imageRect.bottom);
                handles[4][0] = newX; handles[4][1] = newY;
                handles[2][0] = newX; // top-right follows right edge
                handles[6][1] = newY; // bottom-left follows bottom edge
            } else if (dragMode == 6) { // bottom-left; top-right stays anchored
                float newX = clamp(handles[6][0] + dx, imageRect.left, handles[2][0] - minCrop);
                float newY = clamp(handles[6][1] + dy, handles[2][1] + minCrop, imageRect.bottom);
                handles[6][0] = newX; handles[6][1] = newY;
                handles[0][0] = newX; // top-left follows left edge
                handles[4][1] = newY; // bottom-right follows bottom edge
            }

            // After any corner drag, all middle handles sit at the exact middle of their edge.
            snapMiddleHandlesToEdges();
        }

        private void snapMiddleHandlesToEdges() {
            setMidpoint(1, 0, 2); // top
            setMidpoint(3, 2, 4); // right
            setMidpoint(5, 4, 6); // bottom
            setMidpoint(7, 6, 0); // left
        }

        private void setMidpoint(int middle, int cornerA, int cornerB) {
            handles[middle][0] = (handles[cornerA][0] + handles[cornerB][0]) * 0.5f;
            handles[middle][1] = (handles[cornerA][1] + handles[cornerB][1]) * 0.5f;
        }

        private void moveAllHandles(float dx, float dy) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                minX = Math.min(minX, handles[i][0]);
                minY = Math.min(minY, handles[i][1]);
                maxX = Math.max(maxX, handles[i][0]);
                maxY = Math.max(maxY, handles[i][1]);
            }

            if (minX + dx < imageRect.left) dx = imageRect.left - minX;
            if (maxX + dx > imageRect.right) dx = imageRect.right - maxX;
            if (minY + dy < imageRect.top) dy = imageRect.top - minY;
            if (maxY + dy > imageRect.bottom) dy = imageRect.bottom - maxY;

            for (int i = 0; i < 8; i++) {
                handles[i][0] += dx;
                handles[i][1] += dy;
            }
        }

        private float clamp(float v, float min, float max) {
            return Math.max(min, Math.min(max, v));
        }

        Bitmap createCroppedBitmap() {
            if (image == null || imageRect.isEmpty()) return null;

            float sx = image.getWidth() / imageRect.width();
            float sy = image.getHeight() / imageRect.height();

            float[] px = new float[8];
            float[] py = new float[8];
            for (int i = 0; i < 8; i++) {
                px[i] = clamp((handles[i][0] - imageRect.left) * sx, 0f, image.getWidth());
                py[i] = clamp((handles[i][1] - imageRect.top) * sy, 0f, image.getHeight());
            }

            float topW = distance(px[0], py[0], px[1], py[1]) + distance(px[1], py[1], px[2], py[2]);
            float bottomW = distance(px[6], py[6], px[5], py[5]) + distance(px[5], py[5], px[4], py[4]);
            float leftH = distance(px[0], py[0], px[7], py[7]) + distance(px[7], py[7], px[6], py[6]);
            float rightH = distance(px[2], py[2], px[3], py[3]) + distance(px[3], py[3], px[4], py[4]);

            int outW = Math.max(2, Math.round(Math.max(topW, bottomW)));
            int outH = Math.max(2, Math.round(Math.max(leftH, rightH)));
            outW = Math.min(outW, 12000);
            outH = Math.min(outH, 12000);

            Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            canvas.drawColor(Color.WHITE);

            float[] dx = new float[]{
                    0f, outW * 0.5f, outW,
                    outW, outW,
                    outW * 0.5f, 0f, 0f
            };
            float[] dy = new float[]{
                    0f, 0f, 0f,
                    outH * 0.5f, outH,
                    outH, outH, outH * 0.5f
            };

            float srcCx = 0f, srcCy = 0f;
            for (int i = 0; i < 8; i++) {
                srcCx += px[i];
                srcCy += py[i];
            }
            srcCx /= 8f;
            srcCy /= 8f;
            float dstCx = outW * 0.5f;
            float dstCy = outH * 0.5f;

            // Eight triangles map the selected 8-handle crop border into one filled rectangle.
            for (int i = 0; i < 8; i++) {
                int j = (i + 1) % 8;
                float[] src = new float[]{
                        srcCx, srcCy,
                        px[i], py[i],
                        px[j], py[j]
                };
                float[] dst = new float[]{
                        dstCx, dstCy,
                        dx[i], dy[i],
                        dx[j], dy[j]
                };

                Matrix m = new Matrix();
                if (!m.setPolyToPoly(src, 0, dst, 0, 3)) continue;

                Path tri = new Path();
                tri.moveTo(dstCx, dstCy);
                tri.lineTo(dx[i], dy[i]);
                tri.lineTo(dx[j], dy[j]);
                tri.close();

                canvas.save();
                canvas.clipPath(tri);
                canvas.drawBitmap(image, m, bitmapPaint);
                canvas.restore();
            }

            return out;
        }
    }

    private static class ImagePrintAdapter extends PrintDocumentAdapter {
        private final android.content.Context context;
        private final Bitmap bitmap;
        private final String name;
        private PrintAttributes attributes;

        ImagePrintAdapter(android.content.Context context, Bitmap bitmap, String name) {
            this.context = context;
            this.bitmap = bitmap;
            this.name = name;
        }

        @Override
        public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                             android.os.CancellationSignal cancellationSignal,
                             LayoutResultCallback callback, Bundle extras) {
            attributes = newAttributes;
            if (cancellationSignal.isCanceled()) {
                callback.onLayoutCancelled();
                return;
            }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder(name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
                    .setPageCount(1)
                    .build();
            callback.onLayoutFinished(info, true);
        }

        @Override
        public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                            android.os.CancellationSignal cancellationSignal,
                            WriteResultCallback callback) {
            android.print.pdf.PrintedPdfDocument document =
                    new android.print.pdf.PrintedPdfDocument(context, attributes);
            try {
                PdfDocument.Page page = document.startPage(0);
                RectF dst = new RectF(page.getInfo().getContentRect());
                float scale = Math.min(dst.width() / bitmap.getWidth(), dst.height() / bitmap.getHeight());
                float w = bitmap.getWidth() * scale;
                float h = bitmap.getHeight() * scale;
                float l = dst.left + (dst.width() - w) * 0.5f;
                float t = dst.top + (dst.height() - h) * 0.5f;
                page.getCanvas().drawBitmap(bitmap, null, new RectF(l, t, l + w, t + h), null);
                document.finishPage(page);

                try (OutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
                    document.writeTo(out);
                }
                callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            } catch (Exception e) {
                callback.onWriteFailed(e.getMessage());
            } finally {
                document.close();
            }
        }

    }
}
