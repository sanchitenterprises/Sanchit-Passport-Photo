package com.sanchit.passportphoto;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA = 101;
    private static final int REQ_GALLERY = 102;
    private static final int REQ_PERMISSIONS_CAMERA = 103;
    private static final int REQ_PERMISSION_SAVE = 104;
    private static final int BLUE = Color.rgb(74, 144, 194);
    private static final int MAX_IMAGE_SIDE = 1600;

    private ImageView imageView;
    private ProgressBar progress;
    private TextView statusText;
    private SeekBar brightnessSeek;
    private SeekBar smoothSeek;
    private SeekBar brushSeek;
    private Button objectRemoveButton;
    private Button compareButton;
    private Button undoButton;
    private Button redoButton;
    private Button saveButton;

    private Bitmap originalBitmap;
    private Bitmap processedBitmap;
    private Bitmap manualRemoveMask;
    private float[] personMask;
    private int maskWidth;
    private int maskHeight;
    private Uri cameraUri;
    private boolean objectRemoveOn = true;
    private boolean showingOriginal = false;
    private int renderGeneration = 0;

    private final ArrayDeque<Bitmap> undoMasks = new ArrayDeque<>();
    private final ArrayDeque<Bitmap> redoMasks = new ArrayDeque<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private Segmenter segmenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        SelfieSegmenterOptions options = new SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build();
        segmenter = Segmentation.getClient(options);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView title = new TextView(this);
        title.setText("Sanchit Passport Photo V1.2.0");
        title.setTextSize(28);
        title.setTextColor(Color.DKGRAY);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Auto BG • Blue #4A90C2 • Offline processing");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.GRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(3), 0, dp(8));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.setBackgroundColor(0xFFE6E6E6);
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setAdjustViewBounds(true);
        imageView.setBackgroundColor(0xFFE6E6E6);
        previewFrame.addView(imageView, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams pParams = new FrameLayout.LayoutParams(dp(46), dp(46));
        pParams.gravity = Gravity.CENTER;
        previewFrame.addView(progress, pParams);

        statusText = new TextView(this);
        statusText.setText("फोटो चुनें");
        statusText.setTextSize(13);
        statusText.setTextColor(Color.DKGRAY);
        statusText.setBackgroundColor(0xCCFFFFFF);
        statusText.setPadding(dp(8), dp(4), dp(8), dp(4));
        FrameLayout.LayoutParams sParams = new FrameLayout.LayoutParams(-2, -2);
        sParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        sParams.bottomMargin = dp(8);
        previewFrame.addView(statusText, sParams);

        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(previewFrame, previewParams);

        ScrollView scroll = new ScrollView(this);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(0, dp(8), 0, 0);
        scroll.addView(controls, new ScrollView.LayoutParams(-1, -2));

        LinearLayout row1 = row();
        Button camera = button("📷 कैमरा");
        Button gallery = button("🖼 फोटो चुनें");
        row1.addView(camera, rowButtonParams());
        row1.addView(gallery, rowButtonParams());
        controls.addView(row1);

        controls.addView(label("Fairness / Brightness"));
        brightnessSeek = new SeekBar(this);
        brightnessSeek.setMax(100);
        brightnessSeek.setProgress(22);
        controls.addView(brightnessSeek, new LinearLayout.LayoutParams(-1, -2));

        controls.addView(label("Smooth BG"));
        smoothSeek = new SeekBar(this);
        smoothSeek.setMax(100);
        smoothSeek.setProgress(35);
        controls.addView(smoothSeek, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row2 = row();
        compareButton = button("COMPARE");
        objectRemoveButton = button("OBJECT REMOVE ON ✓");
        row2.addView(compareButton, rowButtonParams());
        row2.addView(objectRemoveButton, rowButtonParams());
        controls.addView(row2);

        LinearLayout row3 = row();
        undoButton = button("UNDO");
        redoButton = button("REDO");
        row3.addView(undoButton, rowButtonParams());
        row3.addView(redoButton, rowButtonParams());
        controls.addView(row3);

        controls.addView(label("Object Remove Brush Size"));
        brushSeek = new SeekBar(this);
        brushSeek.setMax(100);
        brushSeek.setProgress(42);
        controls.addView(brushSeek, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row4 = row();
        Button process = button("फिर से PROCESS");
        Button reset = button("RESET");
        row4.addView(process, rowButtonParams());
        row4.addView(reset, rowButtonParams());
        controls.addView(row4);

        saveButton = button("फोटो सेव करें");
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(58));
        saveParams.topMargin = dp(5);
        controls.addView(saveButton, saveParams);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        camera.setOnClickListener(v -> openCamera());
        gallery.setOnClickListener(v -> openGallery());
        process.setOnClickListener(v -> runSegmentation());
        reset.setOnClickListener(v -> resetEdits());
        compareButton.setOnClickListener(v -> toggleCompare());
        objectRemoveButton.setOnClickListener(v -> toggleObjectRemove());
        undoButton.setOnClickListener(v -> undoRemove());
        redoButton.setOnClickListener(v -> redoRemove());
        saveButton.setOnClickListener(v -> savePhoto());

        SeekBar.OnSeekBarChangeListener renderListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (personMask != null && originalBitmap != null) renderFromMask();
            }
        };
        brightnessSeek.setOnSeekBarChangeListener(renderListener);
        smoothSeek.setOnSeekBarChangeListener(renderListener);

        imageView.setOnTouchListener(this::onImageTouch);
        updateUndoRedoButtons();
        setControlsEnabled(false);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(3), 0, dp(3));
        return r;
    }

    private LinearLayout.LayoutParams rowButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(58), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        return b;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setTextColor(Color.DKGRAY);
        t.setPadding(dp(5), dp(7), 0, 0);
        return t;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setControlsEnabled(boolean enabled) {
        brightnessSeek.setEnabled(enabled);
        smoothSeek.setEnabled(enabled);
        brushSeek.setEnabled(enabled);
        compareButton.setEnabled(enabled);
        objectRemoveButton.setEnabled(enabled);
        saveButton.setEnabled(enabled);
        updateUndoRedoButtons();
    }

    private void openGallery() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, REQ_GALLERY);
        } catch (Exception e) {
            toast("Gallery नहीं खुली");
        }
    }

    private void openCamera() {
        if (Build.VERSION.SDK_INT >= 23) {
            boolean needCamera = checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
            boolean needWrite = Build.VERSION.SDK_INT <= 28 &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
            if (needCamera || needWrite) {
                if (needWrite) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERMISSIONS_CAMERA);
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERMISSIONS_CAMERA);
                }
                return;
            }
        }
        launchCamera();
    }

    private void launchCamera() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "Sanchit_Passport_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Sanchit Passport Photo/Camera");
            }
            cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (cameraUri == null) {
                toast("Camera file नहीं बन पाया");
                return;
            }

            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("camera-output", cameraUri));
            if (i.resolveActivity(getPackageManager()) == null) {
                toast("Camera उपलब्ध नहीं है");
                return;
            }
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            toast("Camera error: " + safeMessage(e));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS_CAMERA) {
            boolean ok = grantResults.length > 0;
            for (int result : grantResults) ok &= result == PackageManager.PERMISSION_GRANTED;
            if (ok) launchCamera();
            else toast("Camera permission जरूरी है");
        } else if (requestCode == REQ_PERMISSION_SAVE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) savePhoto();
            else toast("Save permission नहीं मिला");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        Uri uri = null;
        if (requestCode == REQ_CAMERA) uri = cameraUri;
        if (requestCode == REQ_GALLERY && data != null) uri = data.getData();
        if (uri == null) return;

        if (requestCode == REQ_GALLERY && data != null) {
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
        }
        loadSelectedPhoto(uri);
    }

    private void loadSelectedPhoto(Uri uri) {
        setBusy(true, "फोटो लोड हो रही है…");
        worker.execute(() -> {
            try {
                Bitmap bitmap = decodeBitmap(uri);
                if (bitmap == null) throw new IllegalStateException("Photo decode failed");
                runOnUiThread(() -> {
                    releasePhotoBitmaps();
                    originalBitmap = bitmap;
                    imageView.setImageBitmap(originalBitmap);
                    showingOriginal = false;
                    personMask = null;
                    clearManualRemove();
                    clearHistory();
                    setControlsEnabled(true);
                    runSegmentation();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "फोटो नहीं खुली");
                    toast("Photo open error: " + safeMessage(e));
                });
            }
        });
    }

    private Bitmap decodeBitmap(Uri uri) throws Exception {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
            Bitmap decoded = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                int w = info.getSize().getWidth();
                int h = info.getSize().getHeight();
                int max = Math.max(w, h);
                if (max > MAX_IMAGE_SIDE) {
                    int sample = (int) Math.ceil((double) max / MAX_IMAGE_SIDE);
                    decoder.setTargetSampleSize(Math.max(1, sample));
                }
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setMutableRequired(true);
            });
            Bitmap copy = decoded.copy(Bitmap.Config.ARGB_8888, true);
            if (decoded != copy && !decoded.isRecycled()) decoded.recycle();
            return copy;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_IMAGE_SIDE) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        opts.inMutable = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            return b == null ? null : b.copy(Bitmap.Config.ARGB_8888, true);
        }
    }

    private void runSegmentation() {
        if (originalBitmap == null || segmenter == null) {
            toast("पहले फोटो चुनें");
            return;
        }
        setBusy(true, "Auto background remove…");
        InputImage input = InputImage.fromBitmap(originalBitmap, 0);
        segmenter.process(input)
                .addOnSuccessListener(this::acceptSegmentationMask)
                .addOnFailureListener(e -> {
                    toast("Auto BG model fallback use हो रहा है");
                    worker.execute(() -> {
                        float[] fallback = makeBorderFallbackMask(originalBitmap);
                        runOnUiThread(() -> {
                            if (originalBitmap == null) return;
                            personMask = fallback;
                            maskWidth = originalBitmap.getWidth();
                            maskHeight = originalBitmap.getHeight();
                            renderFromMask();
                        });
                    });
                });
    }

    private void acceptSegmentationMask(SegmentationMask mask) {
        final int w = mask.getWidth();
        final int h = mask.getHeight();
        final ByteBuffer source = mask.getBuffer().duplicate().order(ByteOrder.nativeOrder());

        worker.execute(() -> {
            try {
                source.rewind();
                float[] data = new float[w * h];
                int count = Math.min(data.length, source.remaining() / 4);
                for (int i = 0; i < count; i++) data[i] = source.getFloat();
                if (count != data.length) throw new IllegalStateException("Incomplete segmentation mask");

                runOnUiThread(() -> {
                    if (originalBitmap == null) return;
                    personMask = data;
                    maskWidth = w;
                    maskHeight = h;
                    renderFromMask();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "BG processing error");
                    toast("BG processing error: " + safeMessage(e));
                });
            }
        });
    }

    private float[] makeBorderFallbackMask(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        long sr = 0, sg = 0, sb = 0, n = 0;
        int step = Math.max(1, Math.min(w, h) / 200);
        for (int x = 0; x < w; x += step) {
            int c1 = pixels[x];
            int c2 = pixels[(h - 1) * w + x];
            sr += Color.red(c1) + Color.red(c2);
            sg += Color.green(c1) + Color.green(c2);
            sb += Color.blue(c1) + Color.blue(c2);
            n += 2;
        }
        for (int y = 0; y < h; y += step) {
            int c1 = pixels[y * w];
            int c2 = pixels[y * w + (w - 1)];
            sr += Color.red(c1) + Color.red(c2);
            sg += Color.green(c1) + Color.green(c2);
            sb += Color.blue(c1) + Color.blue(c2);
            n += 2;
        }

        float br = sr / (float) Math.max(1, n);
        float bg = sg / (float) Math.max(1, n);
        float bb = sb / (float) Math.max(1, n);
        float[] out = new float[w * h];

        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            float dr = Color.red(c) - br;
            float dg = Color.green(c) - bg;
            float db = Color.blue(c) - bb;
            float dist = (float) Math.sqrt(dr * dr + dg * dg + db * db);
            out[i] = clamp01((dist - 22f) / 100f);
        }
        return out;
    }

    private void renderFromMask() {
        if (originalBitmap == null || personMask == null) return;

        final int generation = ++renderGeneration;
        final Bitmap original = originalBitmap;
        final float[] mask = personMask;
        final int mw = maskWidth;
        final int mh = maskHeight;
        final int brightness = brightnessSeek.getProgress();
        final int smooth = smoothSeek.getProgress();
        final Bitmap removeMask = manualRemoveMask == null
                ? null : manualRemoveMask.copy(Bitmap.Config.ALPHA_8, false);

        setBusy(true, "Blue background तैयार हो रहा है…");

        worker.execute(() -> {
            Bitmap result = null;
            try {
                int w = original.getWidth();
                int h = original.getHeight();
                int[] src = new int[w * h];
                int[] dst = new int[w * h];
                int[] rm = null;

                original.getPixels(src, 0, w, 0, 0, w, h);
                if (removeMask != null && removeMask.getWidth() == w && removeMask.getHeight() == h) {
                    rm = new int[w * h];
                    removeMask.getPixels(rm, 0, w, 0, 0, w, h);
                }

                float s = smooth / 100f;
                float threshold = 0.42f + 0.24f * s;
                float feather = 0.16f + 0.08f * s;
                float low = threshold - feather * 0.5f;
                float high = threshold + feather * 0.5f;
                float brighten = brightness / 100f * 0.34f;

                int bgR = Color.red(BLUE);
                int bgG = Color.green(BLUE);
                int bgB = Color.blue(BLUE);

                for (int y = 0; y < h; y++) {
                    int my = mh == h ? y :
                            Math.min(mh - 1, Math.round(y * (mh - 1f) / Math.max(1f, h - 1f)));

                    for (int x = 0; x < w; x++) {
                        int idx = y * w + x;

                        if (rm != null && Color.alpha(rm[idx]) > 10) {
                            dst[idx] = BLUE;
                            continue;
                        }

                        int mx = mw == w ? x :
                                Math.min(mw - 1, Math.round(x * (mw - 1f) / Math.max(1f, w - 1f)));

                        float conf = mask[Math.min(mask.length - 1, my * mw + mx)];
                        float alpha = smoothStep(low, high, conf);

                        int c = src[idx];
                        int r = Color.red(c);
                        int g = Color.green(c);
                        int b = Color.blue(c);

                        if (brighten > 0f && alpha > 0.03f) {
                            r = clamp255(Math.round(r + (255 - r) * brighten));
                            g = clamp255(Math.round(g + (255 - g) * brighten));
                            b = clamp255(Math.round(b + (255 - b) * brighten));
                        }

                        int or = clamp255(Math.round(r * alpha + bgR * (1f - alpha)));
                        int og = clamp255(Math.round(g * alpha + bgG * (1f - alpha)));
                        int ob = clamp255(Math.round(b * alpha + bgB * (1f - alpha)));
                        dst[idx] = Color.rgb(or, og, ob);
                    }
                }

                result = Bitmap.createBitmap(dst, w, h, Bitmap.Config.ARGB_8888);
                final Bitmap finalResult = result;

                runOnUiThread(() -> {
                    if (generation != renderGeneration) {
                        if (!finalResult.isRecycled()) finalResult.recycle();
                        return;
                    }
                    replaceProcessed(finalResult);
                    showingOriginal = false;
                    imageView.setImageBitmap(processedBitmap);
                    setBusy(false, "Ready • Blue BG applied");
                    compareButton.setText("COMPARE");
                });
            } catch (Exception e) {
                if (result != null && !result.isRecycled()) result.recycle();
                runOnUiThread(() -> {
                    setBusy(false, "Processing error");
                    toast("Processing error: " + safeMessage(e));
                });
            } finally {
                if (removeMask != null && !removeMask.isRecycled()) removeMask.recycle();
            }
        });
    }

    private float smoothStep(float edge0, float edge1, float x) {
        if (edge1 <= edge0) return x >= edge1 ? 1f : 0f;
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private void replaceProcessed(Bitmap bitmap) {
        if (processedBitmap != null && !processedBitmap.isRecycled()) processedBitmap.recycle();
        processedBitmap = bitmap;
    }

    private void toggleCompare() {
        if (originalBitmap == null || processedBitmap == null) return;
        showingOriginal = !showingOriginal;
        imageView.setImageBitmap(showingOriginal ? originalBitmap : processedBitmap);
        compareButton.setText(showingOriginal ? "SHOW RESULT" : "COMPARE");
    }

    private void toggleObjectRemove() {
        objectRemoveOn = !objectRemoveOn;
        objectRemoveButton.setText(objectRemoveOn ? "OBJECT REMOVE ON ✓" : "OBJECT REMOVE OFF");
    }

    private boolean onImageTouch(View view, MotionEvent event) {
        if (!objectRemoveOn || processedBitmap == null || showingOriginal) return false;

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pushUndoMask();
            recycleDeque(redoMasks);
            updateUndoRedoButtons();
            paintRemove(event);
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            paintRemove(event);
            return true;
        }

        return event.getAction() == MotionEvent.ACTION_UP;
    }

    private void paintRemove(MotionEvent event) {
        if (processedBitmap == null) return;

        Matrix inverse = new Matrix();
        if (!imageView.getImageMatrix().invert(inverse)) return;

        float[] point = new float[]{event.getX(), event.getY()};
        inverse.mapPoints(point);
        float x = point[0];
        float y = point[1];

        if (x < 0 || y < 0 || x >= processedBitmap.getWidth() || y >= processedBitmap.getHeight()) return;

        ensureManualRemoveMask();
        float radius = Math.max(
                6f,
                brushSeek.getProgress() / 100f *
                        Math.min(processedBitmap.getWidth(), processedBitmap.getHeight()) * 0.09f);

        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setColor(Color.WHITE);
        new Canvas(manualRemoveMask).drawCircle(x, y, radius, maskPaint);

        Paint bluePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bluePaint.setColor(BLUE);
        new Canvas(processedBitmap).drawCircle(x, y, radius, bluePaint);

        imageView.setImageBitmap(processedBitmap);
        imageView.invalidate();
    }

    private void ensureManualRemoveMask() {
        if (originalBitmap == null) return;

        if (manualRemoveMask == null ||
                manualRemoveMask.getWidth() != originalBitmap.getWidth() ||
                manualRemoveMask.getHeight() != originalBitmap.getHeight()) {

            if (manualRemoveMask != null && !manualRemoveMask.isRecycled()) manualRemoveMask.recycle();
            manualRemoveMask = Bitmap.createBitmap(
                    originalBitmap.getWidth(),
                    originalBitmap.getHeight(),
                    Bitmap.Config.ALPHA_8);
        }
    }

    private void pushUndoMask() {
        ensureManualRemoveMask();
        if (manualRemoveMask == null) return;

        if (undoMasks.size() >= 6) {
            Bitmap old = undoMasks.removeLast();
            if (!old.isRecycled()) old.recycle();
        }
        undoMasks.push(manualRemoveMask.copy(Bitmap.Config.ALPHA_8, true));
    }

    private void undoRemove() {
        if (undoMasks.isEmpty()) return;

        ensureManualRemoveMask();
        if (manualRemoveMask != null) {
            redoMasks.push(manualRemoveMask.copy(Bitmap.Config.ALPHA_8, true));
        }

        Bitmap previous = undoMasks.pop();
        if (manualRemoveMask != null && !manualRemoveMask.isRecycled()) manualRemoveMask.recycle();
        manualRemoveMask = previous;

        updateUndoRedoButtons();
        renderFromMask();
    }

    private void redoRemove() {
        if (redoMasks.isEmpty()) return;

        ensureManualRemoveMask();
        if (manualRemoveMask != null) {
            if (undoMasks.size() >= 6) {
                Bitmap old = undoMasks.removeLast();
                if (!old.isRecycled()) old.recycle();
            }
            undoMasks.push(manualRemoveMask.copy(Bitmap.Config.ALPHA_8, true));
        }

        Bitmap next = redoMasks.pop();
        if (manualRemoveMask != null && !manualRemoveMask.isRecycled()) manualRemoveMask.recycle();
        manualRemoveMask = next;

        updateUndoRedoButtons();
        renderFromMask();
    }

    private void updateUndoRedoButtons() {
        if (undoButton != null) {
            undoButton.setEnabled(originalBitmap != null && !undoMasks.isEmpty());
        }
        if (redoButton != null) {
            redoButton.setEnabled(originalBitmap != null && !redoMasks.isEmpty());
        }
    }

    private void resetEdits() {
        if (originalBitmap == null) return;

        brightnessSeek.setProgress(22);
        smoothSeek.setProgress(35);
        brushSeek.setProgress(42);

        objectRemoveOn = true;
        objectRemoveButton.setText("OBJECT REMOVE ON ✓");

        clearManualRemove();
        clearHistory();
        if (personMask != null) renderFromMask();
        else runSegmentation();
    }

    private void clearManualRemove() {
        if (manualRemoveMask != null && !manualRemoveMask.isRecycled()) manualRemoveMask.recycle();
        manualRemoveMask = null;
    }

    private void clearHistory() {
        recycleDeque(undoMasks);
        recycleDeque(redoMasks);
        updateUndoRedoButtons();
    }

    private void recycleDeque(ArrayDeque<Bitmap> deque) {
        while (!deque.isEmpty()) {
            Bitmap b = deque.pop();
            if (b != null && !b.isRecycled()) b.recycle();
        }
    }

    private void savePhoto() {
        if (processedBitmap == null) {
            toast("पहले फोटो तैयार करें");
            return;
        }

        if (Build.VERSION.SDK_INT <= 28 &&
                Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQ_PERMISSION_SAVE);
            return;
        }

        final Bitmap toSave = processedBitmap.copy(Bitmap.Config.ARGB_8888, false);
        setBusy(true, "फोटो सेव हो रही है…");

        worker.execute(() -> {
            Uri uri = null;
            try {
                String name = "Sanchit_Passport_" + System.currentTimeMillis() + ".jpg";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

                if (Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Sanchit Passport Photo");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                } else {
                    File dir = new File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "Sanchit Passport Photo");

                    if (!dir.exists() && !dir.mkdirs()) {
                        throw new IllegalStateException("Folder create failed");
                    }

                    File file = new File(dir, name);
                    values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
                }

                uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("MediaStore insert failed");

                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null ||
                            !toSave.compress(Bitmap.CompressFormat.JPEG, 100, out)) {
                        throw new IllegalStateException("JPEG write failed");
                    }
                }

                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(uri, ready, null, null);
                }

                runOnUiThread(() -> {
                    setBusy(false, "Saved");
                    toast("फोटो सेव हो गई");
                });

            } catch (Exception e) {
                if (uri != null) {
                    try {
                        getContentResolver().delete(uri, null, null);
                    } catch (Exception ignored) {}
                }

                runOnUiThread(() -> {
                    setBusy(false, "Save failed");
                    toast("Save error: " + safeMessage(e));
                });
            } finally {
                if (!toSave.isRecycled()) toSave.recycle();
            }
        });
    }

    private void setBusy(boolean busy, String text) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        statusText.setText(text);
    }

    private String safeMessage(Exception e) {
        String m = e == null ? null : e.getMessage();
        return m == null || m.trim().isEmpty() ? "unknown" : m;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void releasePhotoBitmaps() {
        renderGeneration++;

        if (originalBitmap != null && !originalBitmap.isRecycled()) originalBitmap.recycle();
        if (processedBitmap != null &&
                processedBitmap != originalBitmap &&
                !processedBitmap.isRecycled()) {
            processedBitmap.recycle();
        }

        originalBitmap = null;
        processedBitmap = null;
        personMask = null;

        clearManualRemove();
        clearHistory();
    }

    @Override
    protected void onDestroy() {
        renderGeneration++;

        if (segmenter != null) {
            try {
                segmenter.close();
            } catch (Exception ignored) {}
        }

        releasePhotoBitmaps();
        worker.shutdownNow();
        super.onDestroy();
    }
}
