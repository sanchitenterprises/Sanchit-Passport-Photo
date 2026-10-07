package com.sanchit.passportfresh;

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
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.RejectedExecutionException;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA = 201;
    private static final int REQ_GALLERY = 202;
    private static final int REQ_CAMERA_PERMISSION = 203;
    private static final int REQ_SAVE_PERMISSION = 204;

    private static final int BLUE = Color.rgb(74, 144, 194);
    private static final int MAX_SIDE = 1440;
    private static final int MAX_HISTORY = 8;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayDeque<Bitmap> undoMasks = new ArrayDeque<>();
    private final ArrayDeque<Bitmap> redoMasks = new ArrayDeque<>();

    private ImageView imageView;
    private ProgressBar progress;
    private TextView status;
    private SeekBar brightnessSeek;
    private SeekBar smoothSeek;
    private SeekBar brushSeek;
    private Button compareButton;
    private Button objectButton;
    private Button undoButton;
    private Button redoButton;
    private Button processButton;
    private Button resetButton;
    private Button saveButton;

    private Segmenter segmenter;
    private Bitmap originalBitmap;
    private Bitmap resultBitmap;
    private Bitmap eraseMask;
    private float[] personMask;
    private int maskWidth;
    private int maskHeight;
    private Uri cameraUri;
    private boolean compareOriginal;
    private boolean objectRemoveOn = true;
    private boolean strokeChanged;
    private volatile boolean destroyed;
    private int renderToken;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView title = new TextView(this);
        title.setText("Sanchit Passport Photo New");
        title.setTextColor(0xFF454545);
        title.setTextSize(27);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Auto BG Remove • Blue #4A90C2 • Offline");
        subtitle.setTextColor(0xFF777777);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(2), 0, dp(7));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout preview = new FrameLayout(this);
        preview.setBackgroundColor(0xFFE9E9E9);

        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(0xFFE9E9E9);
        preview.addView(imageView, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(48), dp(48));
        pp.gravity = Gravity.CENTER;
        preview.addView(progress, pp);

        status = new TextView(this);
        status.setText("फोटो चुनें");
        status.setTextSize(13);
        status.setTextColor(0xFF333333);
        status.setBackgroundColor(0xDDFFFFFF);
        status.setPadding(dp(9), dp(5), dp(9), dp(5));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2);
        sp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        sp.bottomMargin = dp(8);
        preview.addView(status, sp);

        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1f));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(0, dp(6), 0, 0);
        scroll.addView(controls, new ScrollView.LayoutParams(-1, -2));

        LinearLayout row1 = row();
        Button camera = button("📷 कैमरा");
        Button gallery = button("🖼 फोटो चुनें");
        row1.addView(camera, rowButton());
        row1.addView(gallery, rowButton());
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
        objectButton = button("OBJECT REMOVE ON ✓");
        row2.addView(compareButton, rowButton());
        row2.addView(objectButton, rowButton());
        controls.addView(row2);

        LinearLayout row3 = row();
        undoButton = button("UNDO");
        redoButton = button("REDO");
        row3.addView(undoButton, rowButton());
        row3.addView(redoButton, rowButton());
        controls.addView(row3);

        controls.addView(label("Object Remove Brush Size"));
        brushSeek = new SeekBar(this);
        brushSeek.setMax(100);
        brushSeek.setProgress(42);
        controls.addView(brushSeek, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row4 = row();
        processButton = button("फिर से PROCESS");
        resetButton = button("RESET");
        row4.addView(processButton, rowButton());
        row4.addView(resetButton, rowButton());
        controls.addView(row4);

        saveButton = button("फोटो सेव करें");
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(58));
        saveLp.setMargins(dp(3), dp(3), dp(3), dp(3));
        controls.addView(saveButton, saveLp);

        int controlsHeight = Math.max(dp(330), (int)(getResources().getDisplayMetrics().heightPixels * 0.43f));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, controlsHeight));
        setContentView(root);

        camera.setOnClickListener(v -> openCamera());
        gallery.setOnClickListener(v -> openGallery());
        compareButton.setOnClickListener(v -> toggleCompare());
        objectButton.setOnClickListener(v -> toggleObjectRemove());
        undoButton.setOnClickListener(v -> undo());
        redoButton.setOnClickListener(v -> redo());
        processButton.setOnClickListener(v -> segmentPhoto());
        resetButton.setOnClickListener(v -> resetEdits());
        saveButton.setOnClickListener(v -> savePhoto());

        SeekBar.OnSeekBarChangeListener redraw = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                if (fromUser && seekBar == smoothSeek && status != null) {
                    status.setText("Smooth BG " + p + "%");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (personMask != null && originalBitmap != null) renderResult();
            }
        };
        brightnessSeek.setOnSeekBarChangeListener(redraw);
        smoothSeek.setOnSeekBarChangeListener(redraw);

        imageView.setOnTouchListener(this::handleEraseTouch);
        setEditingEnabled(false);
        updateHistoryButtons();
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(2), 0, dp(2));
        return r;
    }

    private LinearLayout.LayoutParams rowButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(57), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
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
        t.setTextColor(0xFF666666);
        t.setPadding(dp(5), dp(7), 0, 0);
        return t;
    }

    private int dp(int px) {
        return Math.round(px * getResources().getDisplayMetrics().density);
    }

    private void setEditingEnabled(boolean enabled) {
        brightnessSeek.setEnabled(enabled);
        smoothSeek.setEnabled(enabled);
        brushSeek.setEnabled(enabled);
        compareButton.setEnabled(enabled);
        objectButton.setEnabled(enabled);
        processButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
        saveButton.setEnabled(enabled);
        updateHistoryButtons();
    }

    private void setBusy(boolean busy, String text) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        status.setText(text);
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
            boolean cameraDenied = checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
            boolean storageDenied = Build.VERSION.SDK_INT <= 28 &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
            if (cameraDenied || storageDenied) {
                if (storageDenied) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_CAMERA_PERMISSION);
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
                }
                return;
            }
        }
        launchCamera();
    }

    private void launchCamera() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "Passport_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Sanchit Passport Photo New/Camera");
            }

            cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (cameraUri == null) {
                toast("Camera file नहीं बन पाया");
                return;
            }

            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("camera", cameraUri));
            if (i.resolveActivity(getPackageManager()) == null) {
                toast("Camera उपलब्ध नहीं है");
                return;
            }
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            toast("Camera error");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_CAMERA_PERMISSION) {
            boolean ok = results.length > 0;
            for (int r : results) ok &= r == PackageManager.PERMISSION_GRANTED;
            if (ok) launchCamera();
            else toast("Camera permission जरूरी है");
        } else if (requestCode == REQ_SAVE_PERMISSION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) savePhoto();
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
                int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {}
        }
        loadPhoto(uri);
    }

    private void loadPhoto(Uri uri) {
        setBusy(true, "फोटो लोड हो रही है…");
        setEditingEnabled(false);
        worker.execute(() -> {
            try {
                Bitmap b = decode(uri);
                if (b == null) throw new IllegalStateException("decode failed");
                runOnUiThread(() -> {
                    releasePhoto();
                    originalBitmap = b;
                    imageView.setImageBitmap(originalBitmap);
                    compareOriginal = false;
                    createEmptyEraseMask();
                    clearHistory();
                    setEditingEnabled(true);
                    segmentPhoto();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "फोटो नहीं खुली");
                    toast("Photo open error");
                });
            }
        });
    }

    private Bitmap decode(Uri uri) throws Exception {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
            Bitmap decoded = ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                if (max > MAX_SIDE) {
                    int sample = (int)Math.ceil(max / (double)MAX_SIDE);
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
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_SIDE) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        opts.inMutable = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            return b == null ? null : b.copy(Bitmap.Config.ARGB_8888, true);
        }
    }

    private void ensureSegmenter() {
        if (segmenter != null || destroyed) return;
        SelfieSegmenterOptions options = new SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build();
        segmenter = Segmentation.getClient(options);
    }

    private void segmentPhoto() {
        if (destroyed || originalBitmap == null) {
            if (!destroyed) toast("पहले फोटो चुनें");
            return;
        }

        ensureSegmenter();
        if (segmenter == null) {
            toast("BG remover तैयार नहीं हुआ");
            return;
        }

        final Bitmap inputBitmap = originalBitmap;
        final int token = ++renderToken;
        setBusy(true, "Auto background remove…");

        try {
            InputImage image = InputImage.fromBitmap(inputBitmap, 0);
            segmenter.process(image)
                    .addOnSuccessListener(mask -> {
                        if (destroyed || token != renderToken) return;
                        copyMaskAndRender(mask);
                    })
                    .addOnFailureListener(e -> {
                        if (destroyed || token != renderToken) return;
                        setBusy(false, "Auto BG failed");
                        toast("Auto BG process नहीं हुआ");
                    });
        } catch (Exception e) {
            if (!destroyed) {
                setBusy(false, "Auto BG failed");
                toast("Auto BG process नहीं हुआ");
            }
        }
    }

    private void copyMaskAndRender(SegmentationMask mask) {
        try {
            maskWidth = mask.getWidth();
            maskHeight = mask.getHeight();
            FloatBuffer fb = mask.getBuffer().duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer();
            personMask = new float[fb.remaining()];
            fb.get(personMask);
            renderResult();
        } catch (Exception e) {
            setBusy(false, "BG mask error");
            toast("BG mask error");
        }
    }

    private void renderResult() {
        if (destroyed || originalBitmap == null || personMask == null || worker.isShutdown()) return;

        final Bitmap source = originalBitmap;
        final float[] mask = personMask;
        final int mw = maskWidth;
        final int mh = maskHeight;
        final int brightness = brightnessSeek.getProgress();
        final int smooth = smoothSeek.getProgress();
        final Bitmap localErase = eraseMask == null ? null : eraseMask.copy(Bitmap.Config.ALPHA_8, false);
        final int token = ++renderToken;

        setBusy(true, "Blue background तैयार हो रहा है…");

        try {
            worker.execute(() -> {
            Bitmap out = null;
            try {
                int w = source.getWidth();
                int h = source.getHeight();
                int[] src = new int[w * h];
                int[] dst = new int[w * h];
                int[] erase = null;
                source.getPixels(src, 0, w, 0, 0, w, h);

                if (localErase != null) {
                    erase = new int[w * h];
                    localErase.getPixels(erase, 0, w, 0, 0, w, h);
                }

                float smoothAmount = smooth / 100f;
                int blurRadius = Math.round(smoothAmount * 8f);
                float[] smoothMask = blurMask(mask, mw, mh, blurRadius);

                // Smooth is dedicated to BG edge cleanup:
                // blur removes jagged edges, higher threshold cuts background fringe inward,
                // and de-fringe below lets blue dominate semi-transparent edge pixels.
                float threshold = 0.42f + (0.18f * smoothAmount);
                float feather = 0.18f - (0.06f * smoothAmount);
                float low = threshold - feather * 0.5f;
                float high = threshold + feather * 0.5f;
                float brighten = (brightness / 100f) * 0.32f;

                int br = Color.red(BLUE);
                int bg = Color.green(BLUE);
                int bb = Color.blue(BLUE);

                for (int y = 0; y < h; y++) {
                    int my = Math.min(mh - 1, Math.max(0, Math.round(y * (mh - 1f) / Math.max(1f, h - 1f))));
                    for (int x = 0; x < w; x++) {
                        int idx = y * w + x;

                        if (erase != null && Color.alpha(erase[idx]) > 8) {
                            dst[idx] = BLUE;
                            continue;
                        }

                        int mx = Math.min(mw - 1, Math.max(0, Math.round(x * (mw - 1f) / Math.max(1f, w - 1f))));
                        float confidence = smoothMask[Math.min(smoothMask.length - 1, my * mw + mx)];
                        float a = smoothStep(low, high, confidence);
                        if (smoothAmount > 0f && a > 0f && a < 1f) {
                            a = (float)Math.pow(a, 1.0f + 1.6f * smoothAmount);
                        }

                        int c = src[idx];
                        int r = Color.red(c);
                        int g = Color.green(c);
                        int b = Color.blue(c);

                        if (a > 0.03f && brighten > 0f) {
                            r = clamp255(Math.round(r + (255 - r) * brighten));
                            g = clamp255(Math.round(g + (255 - g) * brighten));
                            b = clamp255(Math.round(b + (255 - b) * brighten));
                        }

                        int rr = clamp255(Math.round(r * a + br * (1f - a)));
                        int gg = clamp255(Math.round(g * a + bg * (1f - a)));
                        int bbv = clamp255(Math.round(b * a + bb * (1f - a)));
                        dst[idx] = Color.rgb(rr, gg, bbv);
                    }
                }

                out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                out.setPixels(dst, 0, w, 0, 0, w, h);
                final Bitmap ready = out;
                runOnUiThread(() -> {
                    if (destroyed || isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed()) || token != renderToken) {
                        return;
                    }
                    replaceResult(ready);
                    compareOriginal = false;
                    imageView.setImageBitmap(resultBitmap);
                    compareButton.setText("COMPARE");
                    setBusy(false, "Ready • Blue BG applied");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!destroyed) {
                        setBusy(false, "Processing error");
                        toast("Processing error");
                    }
                });
            }
        });
        } catch (RejectedExecutionException ignored) {
            // Activity/worker already closed; ignore late render requests safely.
        }
    }

    private float[] blurMask(float[] src, int w, int h, int radius) {
        if (src == null || radius <= 0 || w <= 1 || h <= 1) return src;

        float[] tmp = new float[src.length];
        float[] out = new float[src.length];
        int window = radius * 2 + 1;

        for (int y = 0; y < h; y++) {
            int row = y * w;
            float sum = 0f;
            for (int k = -radius; k <= radius; k++) {
                int x = Math.max(0, Math.min(w - 1, k));
                sum += src[row + x];
            }
            for (int x = 0; x < w; x++) {
                tmp[row + x] = sum / window;
                int removeX = Math.max(0, x - radius);
                int addX = Math.min(w - 1, x + radius + 1);
                sum += src[row + addX] - src[row + removeX];
            }
        }

        for (int x = 0; x < w; x++) {
            float sum = 0f;
            for (int k = -radius; k <= radius; k++) {
                int y = Math.max(0, Math.min(h - 1, k));
                sum += tmp[y * w + x];
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = sum / window;
                int removeY = Math.max(0, y - radius);
                int addY = Math.min(h - 1, y + radius + 1);
                sum += tmp[addY * w + x] - tmp[removeY * w + x];
            }
        }
        return out;
    }

    private float smoothStep(float edge0, float edge1, float x) {
        if (edge1 <= edge0) return x >= edge1 ? 1f : 0f;
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    private int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private void replaceResult(Bitmap b) {
        resultBitmap = b;
    }

    private void toggleCompare() {
        if (originalBitmap == null || resultBitmap == null) return;
        compareOriginal = !compareOriginal;
        imageView.setImageBitmap(compareOriginal ? originalBitmap : resultBitmap);
        compareButton.setText(compareOriginal ? "SHOW RESULT" : "COMPARE");
    }

    private void toggleObjectRemove() {
        objectRemoveOn = !objectRemoveOn;
        objectButton.setText(objectRemoveOn ? "OBJECT REMOVE ON ✓" : "OBJECT REMOVE OFF");
    }

    private boolean handleEraseTouch(View v, MotionEvent e) {
        if (!objectRemoveOn || resultBitmap == null || originalBitmap == null || compareOriginal) return false;

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            pushUndo();
            clearDeque(redoMasks);
            strokeChanged = false;
            drawErase(e);
            updateHistoryButtons();
            return true;
        }

        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            drawErase(e);
            return true;
        }

        if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
            if (strokeChanged) renderResult();
            return true;
        }
        return false;
    }

    private void drawErase(MotionEvent e) {
        try {
            if (eraseMask == null) createEmptyEraseMask();
            if (eraseMask == null || resultBitmap == null) return;

            Matrix inv = new Matrix();
            if (!imageView.getImageMatrix().invert(inv)) return;

            float[] p = new float[]{e.getX(), e.getY()};
            inv.mapPoints(p);
            float x = p[0];
            float y = p[1];

            if (x < 0 || y < 0 || x >= eraseMask.getWidth() || y >= eraseMask.getHeight()) return;

            float radius = Math.max(8f,
                    (brushSeek.getProgress() / 100f) *
                    Math.min(eraseMask.getWidth(), eraseMask.getHeight()) * 0.085f);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            Canvas maskCanvas = new Canvas(eraseMask);
            maskCanvas.drawCircle(x, y, radius, paint);
            strokeChanged = true;

            // Result is created mutable in renderResult(). If Android ever supplies
            // an immutable bitmap unexpectedly, skip preview drawing instead of crashing.
            if (resultBitmap.isMutable()) {
                Paint blue = new Paint(Paint.ANTI_ALIAS_FLAG);
                blue.setColor(BLUE);
                Canvas previewCanvas = new Canvas(resultBitmap);
                previewCanvas.drawCircle(x, y, radius, blue);
                imageView.setImageBitmap(resultBitmap);
                imageView.invalidate();
            }
        } catch (Throwable t) {
            // Touch must never close the app. The erase mask is the source of truth;
            // final rendering still happens safely on ACTION_UP.
            strokeChanged = true;
        }
    }

    private void createEmptyEraseMask() {
        if (originalBitmap == null) return;
        eraseMask = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ALPHA_8);
    }

    private void pushUndo() {
        if (eraseMask == null) createEmptyEraseMask();
        if (eraseMask == null) return;
        if (undoMasks.size() >= MAX_HISTORY) {
            undoMasks.removeLast();
        }
        undoMasks.push(eraseMask.copy(Bitmap.Config.ALPHA_8, true));
    }

    private void undo() {
        if (undoMasks.isEmpty()) return;
        if (eraseMask != null) redoMasks.push(eraseMask.copy(Bitmap.Config.ALPHA_8, true));
        Bitmap previous = undoMasks.pop();
        eraseMask = previous;
        updateHistoryButtons();
        renderResult();
    }

    private void redo() {
        if (redoMasks.isEmpty()) return;
        if (eraseMask != null) {
            if (undoMasks.size() >= MAX_HISTORY) {
                undoMasks.removeLast();
            }
            undoMasks.push(eraseMask.copy(Bitmap.Config.ALPHA_8, true));
        }
        Bitmap next = redoMasks.pop();
        eraseMask = next;
        updateHistoryButtons();
        renderResult();
    }

    private void updateHistoryButtons() {
        if (undoButton != null) undoButton.setEnabled(originalBitmap != null && !undoMasks.isEmpty());
        if (redoButton != null) redoButton.setEnabled(originalBitmap != null && !redoMasks.isEmpty());
    }

    private void resetEdits() {
        if (originalBitmap == null) return;
        brightnessSeek.setProgress(22);
        smoothSeek.setProgress(35);
        brushSeek.setProgress(42);
        objectRemoveOn = true;
        objectButton.setText("OBJECT REMOVE ON ✓");
        createEmptyEraseMask();
        clearHistory();
        if (personMask != null) renderResult();
        else segmentPhoto();
    }

    private void clearHistory() {
        clearDeque(undoMasks);
        clearDeque(redoMasks);
        updateHistoryButtons();
    }

    private void clearDeque(ArrayDeque<Bitmap> q) {
        q.clear();
    }

    private void savePhoto() {
        if (resultBitmap == null) {
            toast("पहले फोटो तैयार करें");
            return;
        }

        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_SAVE_PERMISSION);
            return;
        }

        final Bitmap copy = resultBitmap.copy(Bitmap.Config.ARGB_8888, false);
        setBusy(true, "फोटो सेव हो रही है…");

        worker.execute(() -> {
            Uri uri = null;
            try {
                String name = "Sanchit_Passport_" + System.currentTimeMillis() + ".jpg";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

                if (Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Sanchit Passport Photo New");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "Sanchit Passport Photo New");
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("folder");
                    File file = new File(dir, name);
                    values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
                }

                uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("insert");

                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null || !copy.compress(Bitmap.CompressFormat.JPEG, 100, out)) {
                        throw new IllegalStateException("write");
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
            } catch (Exception ex) {
                if (uri != null) {
                    try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) {}
                }
                runOnUiThread(() -> {
                    setBusy(false, "Save failed");
                    toast("फोटो सेव नहीं हुई");
                });
            } finally {
                if (!copy.isRecycled()) copy.recycle();
            }
        });
    }

    private void releasePhoto() {
        renderToken++;
        // Do not manually recycle bitmaps here: an ML/render callback may still hold a reference.
        // Nulling references lets Android reclaim them safely after pending work finishes.
        originalBitmap = null;
        resultBitmap = null;
        eraseMask = null;
        personMask = null;
        clearHistory();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        renderToken++;
        if (segmenter != null) {
            try { segmenter.close(); } catch (Exception ignored) {}
            segmenter = null;
        }
        originalBitmap = null;
        resultBitmap = null;
        eraseMask = null;
        personMask = null;
        undoMasks.clear();
        redoMasks.clear();
        worker.shutdownNow();
        super.onDestroy();
    }
}
