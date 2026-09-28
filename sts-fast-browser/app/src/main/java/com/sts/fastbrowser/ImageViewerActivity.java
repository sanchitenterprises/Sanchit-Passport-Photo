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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

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

        TextView menu = new TextView(this);
        menu.setText("⋮");
        menu.setTextSize(25);
        menu.setTextColor(Color.parseColor("#162326"));
        menu.setGravity(Gravity.CENTER);
        menu.setClickable(true);
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
        root.addView(editBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        return root;
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor, Gravity.END);
        pm.getMenu().add("Share");
        pm.getMenu().add("Print");
        pm.getMenu().add("Download");
        pm.getMenu().add("Edit");
        pm.setOnMenuItemClickListener(item -> {
            if (bitmap == null) {
                Toast.makeText(this, "Image अभी तैयार हो रही है", Toast.LENGTH_SHORT).show();
                return true;
            }
            String t = String.valueOf(item.getTitle());
            if ("Share".equals(t)) shareImage();
            else if ("Print".equals(t)) printImage();
            else if ("Download".equals(t)) saveImageToDownloads();
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
        addEditButton("Crop", v -> beginCrop());
        addEditButton("Rotate", v -> rotateImage());
        addEditButton("Resize", v -> showResizeDialog());
        addEditButton("Done", v -> {
            cropMode = false;
            imageCanvas.setCropMode(false);
            editBar.setVisibility(View.GONE);
        });
    }

    private void buildCropBar() {
        editBar.removeAllViews();
        addEditButton("Apply Crop", v -> applyCrop());
        addEditButton("Cancel Crop", v -> {
            cropMode = false;
            imageCanvas.setCropMode(false);
            buildMainEditBar();
        });
    }

    private void addEditButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setTextColor(Color.parseColor("#162326"));
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        editBar.addView(b, lp);
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

        body.addView(width, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        body.addView(height, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Resize Image")
                .setMessage("Width और Height pixel में डालें")
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

    private void saveImageToDownloads() {
        if (android.os.Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingSaveAction = "download";
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }

        new Thread(() -> {
            try {
                boolean png = fileName.toLowerCase(Locale.ROOT).endsWith(".png");
                String ext = png ? ".png" : ".jpg";
                String mime = png ? "image/png" : "image/jpeg";
                String outName = baseName(fileName) + "_edited" + ext;
                Bitmap.CompressFormat format = png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;

                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, outName);
                    v.put(MediaStore.Downloads.MIME_TYPE, mime);
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/STS Fast Browser");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("Unable to create file");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null || !bitmap.compress(format, png ? 100 : 95, out)) {
                            throw new Exception("Unable to write image");
                        }
                    }
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "STS Fast Browser");
                    if (!dir.exists()) dir.mkdirs();
                    File out = uniqueFile(dir, outName, ext);
                    try (OutputStream os = new FileOutputStream(out)) {
                        if (!bitmap.compress(format, png ? 100 : 95, os)) throw new Exception("Unable to write image");
                    }
                }

                runOnUiThread(() -> Toast.makeText(this, "Image Downloads में save हो गई", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Download नहीं हो पाया", Toast.LENGTH_SHORT).show());
            }
        }).start();
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
            if ("download".equals(pendingSaveAction)) saveImageToDownloads();
            pendingSaveAction = null;
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
        private final RectF cropRect = new RectF();
        private boolean cropEnabled = false;
        private int dragMode = 0;
        private float lastX;
        private float lastY;
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
        }

        void setBitmap(Bitmap bitmap) {
            this.image = bitmap;
            cropEnabled = false;
            dragMode = 0;
            invalidate();
        }

        void setCropMode(boolean enabled) {
            cropEnabled = enabled;
            dragMode = 0;
            if (enabled) resetCropRect();
            invalidate();
        }

        private void updateImageRect() {
            imageRect.setEmpty();
            if (image == null || getWidth() <= 0 || getHeight() <= 0) return;
            float scale = Math.min(getWidth() / (float) image.getWidth(), getHeight() / (float) image.getHeight());
            float w = image.getWidth() * scale;
            float h = image.getHeight() * scale;
            float l = (getWidth() - w) * 0.5f;
            float t = (getHeight() - h) * 0.5f;
            imageRect.set(l, t, l + w, t + h);
        }

        private void resetCropRect() {
            updateImageRect();
            if (imageRect.isEmpty()) return;
            float mx = imageRect.width() * 0.08f;
            float my = imageRect.height() * 0.08f;
            cropRect.set(imageRect.left + mx, imageRect.top + my, imageRect.right - mx, imageRect.bottom - my);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            updateImageRect();
            if (image == null || imageRect.isEmpty()) return;

            canvas.drawBitmap(image, null, imageRect, bitmapPaint);

            if (cropEnabled) {
                if (cropRect.isEmpty()) resetCropRect();

                canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, cropRect.top, shadePaint);
                canvas.drawRect(imageRect.left, cropRect.bottom, imageRect.right, imageRect.bottom, shadePaint);
                canvas.drawRect(imageRect.left, cropRect.top, cropRect.left, cropRect.bottom, shadePaint);
                canvas.drawRect(cropRect.right, cropRect.top, imageRect.right, cropRect.bottom, shadePaint);

                canvas.drawRect(cropRect, borderPaint);
                canvas.drawCircle(cropRect.left, cropRect.top, handleRadius * 0.55f, handlePaint);
                canvas.drawCircle(cropRect.right, cropRect.top, handleRadius * 0.55f, handlePaint);
                canvas.drawCircle(cropRect.left, cropRect.bottom, handleRadius * 0.55f, handlePaint);
                canvas.drawCircle(cropRect.right, cropRect.bottom, handleRadius * 0.55f, handlePaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!cropEnabled || image == null) return true;
            float x = event.getX();
            float y = event.getY();

            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                dragMode = detectDragMode(x, y);
                lastX = x;
                lastY = y;
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && dragMode != 0) {
                float dx = x - lastX;
                float dy = y - lastY;
                moveCrop(dx, dy);
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                dragMode = 0;
                return true;
            }
            return true;
        }

        private int detectDragMode(float x, float y) {
            float r = handleRadius * 1.7f;
            if (distance(x, y, cropRect.left, cropRect.top) <= r) return 2;
            if (distance(x, y, cropRect.right, cropRect.top) <= r) return 3;
            if (distance(x, y, cropRect.left, cropRect.bottom) <= r) return 4;
            if (distance(x, y, cropRect.right, cropRect.bottom) <= r) return 5;
            if (cropRect.contains(x, y)) return 1;
            return 0;
        }

        private float distance(float x1, float y1, float x2, float y2) {
            float dx = x1 - x2;
            float dy = y1 - y2;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private void moveCrop(float dx, float dy) {
            if (dragMode == 1) {
                float nx = dx;
                float ny = dy;
                if (cropRect.left + nx < imageRect.left) nx = imageRect.left - cropRect.left;
                if (cropRect.right + nx > imageRect.right) nx = imageRect.right - cropRect.right;
                if (cropRect.top + ny < imageRect.top) ny = imageRect.top - cropRect.top;
                if (cropRect.bottom + ny > imageRect.bottom) ny = imageRect.bottom - cropRect.bottom;
                cropRect.offset(nx, ny);
                return;
            }

            RectF r = new RectF(cropRect);
            if (dragMode == 2 || dragMode == 4) r.left += dx;
            if (dragMode == 3 || dragMode == 5) r.right += dx;
            if (dragMode == 2 || dragMode == 3) r.top += dy;
            if (dragMode == 4 || dragMode == 5) r.bottom += dy;

            r.left = Math.max(imageRect.left, Math.min(r.left, r.right - minCrop));
            r.right = Math.min(imageRect.right, Math.max(r.right, r.left + minCrop));
            r.top = Math.max(imageRect.top, Math.min(r.top, r.bottom - minCrop));
            r.bottom = Math.min(imageRect.bottom, Math.max(r.bottom, r.top + minCrop));
            cropRect.set(r);
        }

        Bitmap createCroppedBitmap() {
            if (image == null || cropRect.isEmpty() || imageRect.isEmpty()) return null;

            float sx = image.getWidth() / imageRect.width();
            float sy = image.getHeight() / imageRect.height();

            int left = Math.max(0, Math.round((cropRect.left - imageRect.left) * sx));
            int top = Math.max(0, Math.round((cropRect.top - imageRect.top) * sy));
            int right = Math.min(image.getWidth(), Math.round((cropRect.right - imageRect.left) * sx));
            int bottom = Math.min(image.getHeight(), Math.round((cropRect.bottom - imageRect.top) * sy));

            int w = right - left;
            int h = bottom - top;
            if (w <= 1 || h <= 1) return null;
            return Bitmap.createBitmap(image, left, top, w, h);
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
