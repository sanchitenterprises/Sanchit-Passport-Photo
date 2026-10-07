package com.sanchit.passportfresh;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
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
    private ImageView processingLogo;
    private View processingShade;
    private View brushCursor;
    private AnimatorSet processingAnimator;
    private SeekBar brightnessSeek;
    private SeekBar smoothSeek;
    private SeekBar brushSeek;
    private Button compareButton;
    private Button brushButton;
    private Button objectButton;
    private Button undoButton;
    private Button redoButton;
    private Button processButton;
    private Button saveButton;

    private Segmenter segmenter;
    private Bitmap originalBitmap;
    private Bitmap resultBitmap;
    private Bitmap eraseMask;
    private float[] personMask;
    private int maskWidth;
    private int maskHeight;
    private Uri cameraUri;
    private Uri sourceUri;
    private boolean compareOriginal;
    private boolean brushModeOn = false;
    private boolean colorCleanOn = false;
    private boolean brushStrokeStarted = false;
    private boolean brushStrokeChanged = false;
    private float lastBrushViewX;
    private float lastBrushViewY;
    private TextView toolSeekLabel;
    private int colorToleranceValue = 22;
    private int brushSizeValue = 30;
    private volatile boolean destroyed;

    private final Matrix photoMatrix = new Matrix();
    private ScaleGestureDetector scaleGestureDetector;
    private float zoomFactor = 1f;
    private float lastPanX;
    private float lastPanY;
    private boolean panMoved;
    private boolean gestureWasScaling;
    private int renderToken;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 248, 250));
        root.setPadding(dp(8), dp(6), dp(8), dp(7));

        // Compact professional header: fixed, no scrolling.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(3), dp(8), dp(3));
        header.setBackground(rounded(Color.WHITE, 16));

        ImageView headerLogo = new ImageView(this);
        headerLogo.setImageResource(com.sanchit.passportfresh.R.drawable.ic_passport_logo);
        headerLogo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        header.addView(headerLogo, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);
        headerText.setGravity(Gravity.CENTER_VERTICAL);
        headerText.setPadding(dp(8), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText("STS Photo Background Remover");
        title.setTextColor(0xFF1F2933);
        title.setTextSize(18);
        title.setSingleLine(true);
        headerText.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Created by Sanchit Kumar");
        subtitle.setTextColor(0xFF6B7280);
        subtitle.setTextSize(10);
        subtitle.setSingleLine(true);
        headerText.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        header.addView(headerText, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, dp(60));
        headerLp.bottomMargin = dp(6);
        root.addView(header, headerLp);

        // Preview area gets most free space and never scrolls.
        FrameLayout preview = new FrameLayout(this);
        preview.setBackground(rounded(0xFFE7EBEF, 18));

        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        imageView.setBackgroundColor(0xFFE7EBEF);
        preview.addView(imageView, new FrameLayout.LayoutParams(-1, -1));

        brushCursor = new View(this);
        GradientDrawable brushCursorBg = new GradientDrawable();
        brushCursorBg.setShape(GradientDrawable.OVAL);
        brushCursorBg.setColor(0x22FFFFFF);
        brushCursorBg.setStroke(dp(2), 0xFF111111);
        brushCursor.setBackground(brushCursorBg);
        brushCursor.setVisibility(View.GONE);
        FrameLayout.LayoutParams brushCursorLp = new FrameLayout.LayoutParams(dp(30), dp(30));
        brushCursorLp.gravity = Gravity.TOP | Gravity.LEFT;
        preview.addView(brushCursor, brushCursorLp);

        processingShade = new View(this);
        processingShade.setBackgroundColor(0x66FFFFFF);
        processingShade.setVisibility(View.GONE);
        preview.addView(processingShade, new FrameLayout.LayoutParams(-1, -1));

        processingLogo = new ImageView(this);
        processingLogo.setImageResource(com.sanchit.passportfresh.R.drawable.ic_passport_logo);
        processingLogo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        processingLogo.setVisibility(View.GONE);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(dp(82), dp(82));
        logoLp.gravity = Gravity.CENTER;
        preview.addView(processingLogo, logoLp);

        status = new TextView(this);
        status.setText("फोटो चुनें");
        status.setTextSize(12);
        status.setTextColor(0xFF1F2933);
        status.setBackground(rounded(0xEFFFFFFF, 12));
        status.setPadding(dp(10), dp(5), dp(10), dp(5));
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(-2, -2);
        statusLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        statusLp.bottomMargin = dp(8);
        preview.addView(status, statusLp);

        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, 0, 0.58f);
        previewLp.bottomMargin = dp(6);
        root.addView(preview, previewLp);

        // Fixed-height weighted control panel; there is intentionally NO ScrollView.
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(5), dp(5), dp(5), dp(5));
        controls.setBackground(rounded(Color.WHITE, 18));
        root.addView(controls, new LinearLayout.LayoutParams(-1, 0, 0.42f));

        LinearLayout row1 = buttonRow();
        Button camera = button("📷 कैमरा", false);
        Button gallery = button("🖼 फोटो चुनें", true);
        row1.addView(camera, weightedButton());
        row1.addView(gallery, weightedButton());
        controls.addView(row1, weightedControlRow(1.0f));

        brightnessSeek = compactSlider(controls, "Brightness", 22);
        smoothSeek = compactSlider(controls, "Smooth BG", 35);

        LinearLayout row2 = buttonRow();
        compareButton = button("COMPARE", false);
        brushButton = button("BRUSH", false);
        objectButton = button("LOCAL COLOR CLEAN", false);
        compareButton.setTextSize(11);
        brushButton.setTextSize(11);
        objectButton.setTextSize(10);
        row2.addView(compareButton, weightedButton());
        row2.addView(brushButton, weightedButton());
        row2.addView(objectButton, weightedButton());
        controls.addView(row2, weightedControlRow(1.0f));

        LinearLayout row3 = buttonRow();
        undoButton = button("UNDO", false);
        redoButton = button("REDO", false);
        row3.addView(undoButton, weightedButton());
        row3.addView(redoButton, weightedButton());
        controls.addView(row3, weightedControlRow(1.0f));

        brushSeek = compactSlider(controls, "Color Tolerance", 22);

        LinearLayout row4 = buttonRow();
        processButton = button("SHARE", true);
        saveButton = button("SAVE", false);
        row4.addView(processButton, weightedButton());
        row4.addView(saveButton, weightedButton());
        controls.addView(row4, weightedControlRow(1.0f));

        setContentView(root);

        camera.setOnClickListener(v -> openCamera());
        gallery.setOnClickListener(v -> openGallery());
        compareButton.setOnClickListener(v -> toggleCompare());
        brushButton.setOnClickListener(v -> toggleBrushMode());
        objectButton.setOnClickListener(v -> toggleColorClean());
        undoButton.setOnClickListener(v -> undo());
        redoButton.setOnClickListener(v -> redo());
        processButton.setOnClickListener(v -> sharePhoto());
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
        brushSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                if (brushModeOn) {
                    brushSizeValue = p;
                    if (brushCursor != null && brushCursor.getVisibility() == View.VISIBLE) {
                        showBrushCursor(lastBrushViewX > 0 ? lastBrushViewX : imageView.getWidth() * 0.5f,
                                lastBrushViewY > 0 ? lastBrushViewY : imageView.getHeight() * 0.5f);
                    }
                    if (fromUser && status != null) status.setText("Brush Size " + p + "%");
                } else {
                    colorToleranceValue = p;
                    if (fromUser && status != null) status.setText("Color Tolerance " + p + "%");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

                scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                gestureWasScaling = true;
                return resultBitmap != null || originalBitmap != null;
            }

            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (imageView.getDrawable() == null) return false;
                float wanted = zoomFactor * detector.getScaleFactor();
                float limited = Math.max(1f, Math.min(20f, wanted));
                float factor = limited / Math.max(0.0001f, zoomFactor);
                photoMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                zoomFactor = limited;
                constrainPhotoMatrix();
                imageView.setImageMatrix(photoMatrix);
                if (brushModeOn && brushCursor != null && brushCursor.getVisibility() == View.VISIBLE) {
                    showBrushCursor(lastBrushViewX > 0 ? lastBrushViewX : detector.getFocusX(),
                            lastBrushViewY > 0 ? lastBrushViewY : detector.getFocusY());
                }
                if (status != null) status.setText("Zoom " + Math.round(zoomFactor * 100f) + "%");
                return true;
            }
        });

        imageView.setOnTouchListener(this::handlePhotoTouch);
        setEditingEnabled(false);
        updateHistoryButtons();
    }

    private LinearLayout buttonRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private LinearLayout.LayoutParams weightedControlRow(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, weight);
        lp.setMargins(0, dp(1), 0, dp(1));
        return lp;
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(dp(3), dp(2), dp(3), dp(2));
        return lp;
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : 0xFF1F2933);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setBackground(rounded(primary ? 0xFF5B7FA3 : 0xFFE9EEF3, 13));
        return b;
    }

    private SeekBar compactSlider(LinearLayout parent, String name, int progressValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(5), 0, dp(5), 0);

        TextView label = new TextView(this);
        label.setText(name);
        label.setTextSize(13);
        label.setTextColor(0xFF374151);
        label.setGravity(Gravity.CENTER_VERTICAL);
        if ("Color Tolerance".equals(name)) toolSeekLabel = label;
        row.addView(label, new LinearLayout.LayoutParams(dp(92), -1));

        SeekBar seek = new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(progressValue);
        row.addView(seek, new LinearLayout.LayoutParams(0, -1, 1f));

        parent.addView(row, weightedControlRow(0.82f));
        return seek;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int px) {
        return Math.round(px * getResources().getDisplayMetrics().density);
    }

    private void setEditingEnabled(boolean enabled) {
        brightnessSeek.setEnabled(enabled);
        smoothSeek.setEnabled(enabled);
        brushSeek.setEnabled(enabled);
        compareButton.setEnabled(enabled);
        brushButton.setEnabled(enabled);
        objectButton.setEnabled(enabled);
        processButton.setEnabled(enabled);
        saveButton.setEnabled(enabled);
        updateHistoryButtons();
    }

    private void setBusy(boolean busy, String text) {
        status.setText(text);
        if (busy) startProcessingAnimation();
        else stopProcessingAnimation();
    }

    private void startProcessingAnimation() {
        if (processingLogo == null || processingShade == null) return;
        processingShade.setVisibility(View.VISIBLE);
        processingLogo.setVisibility(View.VISIBLE);

        if (processingAnimator != null && processingAnimator.isRunning()) return;

        ObjectAnimator rotation = ObjectAnimator.ofFloat(processingLogo, View.ROTATION, 0f, 360f);
        rotation.setDuration(1500);
        rotation.setRepeatCount(ObjectAnimator.INFINITE);
        rotation.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(processingLogo, View.SCALE_X, 0.88f, 1.10f, 0.88f);
        scaleX.setDuration(1100);
        scaleX.setRepeatCount(ObjectAnimator.INFINITE);

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(processingLogo, View.SCALE_Y, 0.88f, 1.10f, 0.88f);
        scaleY.setDuration(1100);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);

        processingAnimator = new AnimatorSet();
        processingAnimator.playTogether(rotation, scaleX, scaleY);
        processingAnimator.start();
    }

    private void stopProcessingAnimation() {
        if (processingAnimator != null) {
            processingAnimator.cancel();
            processingAnimator = null;
        }
        if (processingLogo != null) {
            processingLogo.setRotation(0f);
            processingLogo.setScaleX(1f);
            processingLogo.setScaleY(1f);
            processingLogo.setVisibility(View.GONE);
        }
        if (processingShade != null) processingShade.setVisibility(View.GONE);
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
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/STS Photo Background Remover/Camera");
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
                    sourceUri = uri;
                    originalBitmap = b;
                    imageView.setImageBitmap(originalBitmap);
                    compareOriginal = false;
                    imageView.post(this::resetZoom);
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
                int blurRadius = Math.round(smoothAmount * 10f);
                float[] smoothMask = blurMask(mask, mw, mh, blurRadius);
                if (smoothAmount > 0.35f) {
                    smoothMask = blurMask(smoothMask, mw, mh,
                            Math.max(1, Math.round(smoothAmount * 3f)));
                }

                // Hair-safe refinement:
                // use a small max/dilation support mask to close tiny holes inside hair,
                // but only inside the detected upper/head region.
                int hairRadius = 2 + Math.round(smoothAmount * 2f);
                float[] hairSupportMask = maxFilterMask(smoothMask, mw, mh, hairRadius);
                int[] personBounds = findMaskBounds(mask, mw, mh, 0.55f);

                // Normal body edge cleanup.
                float threshold = 0.51f + (0.07f * smoothAmount);
                float feather = 0.14f + (0.08f * smoothAmount);
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

                        float manualErase = erase == null ? 0f : (Color.alpha(erase[idx]) / 255f);

                        int mx = Math.min(mw - 1, Math.max(0, Math.round(x * (mw - 1f) / Math.max(1f, w - 1f))));

                        int c = src[idx];
                        int r = Color.red(c);
                        int g = Color.green(c);
                        int b = Color.blue(c);
                        float luminance = 0.299f * r + 0.587f * g + 0.114f * b;

                        int maskIndex = Math.min(smoothMask.length - 1, my * mw + mx);
                        float confidence = smoothMask[maskIndex];
                        float hairSupport = hairSupportMask[maskIndex];

                        boolean headZone = isInsideHeadZone(mx, my, personBounds, mw, mh);
                        // Dark/medium pixels near a strong person mask are likely hair.
                        // This protects hair strands and fills small blue holes inside the hair mass.
                        boolean skinLike = isSkinLikeColor(r, g, b);
                        boolean hairCandidate = headZone
                                && !skinLike
                                && luminance < 165f
                                && hairSupport > 0.30f;

                        float a;
                        if (hairCandidate) {
                            confidence = Math.max(confidence, Math.min(1f, hairSupport * 0.92f + 0.10f));
                            float hairThreshold = 0.31f + 0.04f * smoothAmount;
                            float hairFeather = 0.30f + 0.06f * smoothAmount;
                            a = smoothStep(hairThreshold - hairFeather * 0.5f,
                                    hairThreshold + hairFeather * 0.5f,
                                    confidence);

                            // If the surrounding head mask is very strong, never punch a hard
                            // blue hole through a dark hair cluster.
                            if (hairSupport > 0.62f && a < 0.58f) a = 0.58f;
                        } else {
                            a = smoothStep(low, high, confidence);
                            if (smoothAmount > 0f && a > 0f && a < 1f) {
                                float softened = smoothStep(0f, 1f, a);
                                float softMix = 0.25f + 0.45f * smoothAmount;
                                a = a * (1f - softMix) + softened * softMix;
                                float edgeCut = 0.06f * smoothAmount;
                                a = Math.max(0f, Math.min(1f,
                                        (a - edgeCut) / Math.max(0.01f, 1f - edgeCut)));
                            }
                        }

                        // Ear / cheek / jaw skin-edge refinement:
                        // skin pixels must not inherit hair-preservation. Use a tighter,
                        // narrower feather and a small inward cut to remove the pale halo.
                        boolean skinEdge = skinLike && headZone && a > 0.03f && a < 0.985f;
                        if (skinEdge) {
                            float skinA = smoothStep(0.44f, 0.62f, confidence);
                            float keep = 0.30f + 0.35f * (1f - smoothAmount);
                            a = Math.max(0f, Math.min(1f, skinA - keep * (1f - skinA) * 0.18f));
                        }

                        // Proper local matte decontamination:
                        // The original source often has a white/light background baked into
                        // semi-transparent boundary pixels. Sample both directions of the mask:
                        // inward = true subject color, outward = old background color. Remove
                        // the old background contribution before compositing over blue.
                        if (a > 0.025f && a < 0.995f) {
                            int inner = sampleInnerForegroundColor(src, w, h, x, y,
                                    smoothMask, mw, mh, mx, my, confidence);
                            int outer = sampleOuterBackgroundColor(src, w, h, x, y,
                                    smoothMask, mw, mh, mx, my, confidence);

                            if (inner != -1) {
                                int ir = Color.red(inner);
                                int ig = Color.green(inner);
                                int ib = Color.blue(inner);

                                float edgeBand = 1f - Math.abs(a * 2f - 1f);
                                float matchStrength = 0.20f + 0.35f * edgeBand;

                                if (outer != -1) {
                                    int or = Color.red(outer);
                                    int og = Color.green(outer);
                                    int ob = Color.blue(outer);

                                    float dInner = colorDistanceSq(r, g, b, ir, ig, ib);
                                    float dOuter = colorDistanceSq(r, g, b, or, og, ob);

                                    // Recover foreground from C = A*F + (1-A)*B.
                                    // Clamp A away from zero to avoid unstable color explosions.
                                    float solveA = Math.max(0.34f, Math.min(0.96f, a));
                                    int fr = clamp255(Math.round((r - (1f - solveA) * or) / solveA));
                                    int fg = clamp255(Math.round((g - (1f - solveA) * og) / solveA));
                                    int fb = clamp255(Math.round((b - (1f - solveA) * ob) / solveA));

                                    // Recovered color is blended toward a real inward subject
                                    // sample, which prevents false colors on skin and white clothes.
                                    float recover = 0.48f + 0.34f * edgeBand;
                                    fr = clamp255(Math.round(fr * recover + ir * (1f - recover)));
                                    fg = clamp255(Math.round(fg * recover + ig * (1f - recover)));
                                    fb = clamp255(Math.round(fb * recover + ib * (1f - recover)));

                                    boolean outerContaminated = dOuter + 120f < dInner;
                                    if (outerContaminated) {
                                        matchStrength = Math.max(matchStrength, 0.78f);
                                        if (!hairCandidate) {
                                            // Trim a little farther inward on skin/clothes so
                                            // the old white matte cannot remain as a visible rim.
                                            a *= skinEdge ? 0.78f : 0.84f;
                                        }
                                    }

                                    r = clamp255(Math.round(r * (1f - matchStrength) + fr * matchStrength));
                                    g = clamp255(Math.round(g * (1f - matchStrength) + fg * matchStrength));
                                    b = clamp255(Math.round(b * (1f - matchStrength) + fb * matchStrength));
                                } else {
                                    if (hairCandidate) matchStrength = Math.max(matchStrength, 0.58f * edgeBand);
                                    if (skinEdge) matchStrength = Math.max(matchStrength, 0.72f * edgeBand);
                                    r = clamp255(Math.round(r * (1f - matchStrength) + ir * matchStrength));
                                    g = clamp255(Math.round(g * (1f - matchStrength) + ig * matchStrength));
                                    b = clamp255(Math.round(b * (1f - matchStrength) + ib * matchStrength));
                                }
                            }
                        }

                        // Manual BRUSH / Local Color Clean mask. A full-alpha mask removes
                        // the pixel completely; the soft outer brush ring only reduces alpha,
                        // producing a smooth natural edge instead of a hard cut.
                        if (manualErase > 0f) {
                            a *= (1f - Math.max(0f, Math.min(1f, manualErase)));
                        }

                        // Do not brighten the semi-transparent edge itself; that was creating
                        // a visible white outline. Brightness fades in only toward solid foreground.
                        if (a > 0.03f && brighten > 0f) {
                            float interior = smoothStep(0.48f, 0.93f, a);
                            float localBrighten = brighten * interior;
                            r = clamp255(Math.round(r + (255 - r) * localBrighten));
                            g = clamp255(Math.round(g + (255 - g) * localBrighten));
                            b = clamp255(Math.round(b + (255 - b) * localBrighten));
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

    private boolean isSkinLikeColor(int r, int g, int b) {
        if (r < 45 || g < 25 || b < 15) return false;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max - min < 10) return false;

        // YCbCr skin range works better across fair/wheatish/darker Indian skin
        // than a fixed RGB threshold.
        float cb = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b;
        float cr = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b;
        return cb >= 72f && cb <= 132f && cr >= 128f && cr <= 184f && r >= g * 0.92f;
    }

    private int sampleInnerForegroundColor(int[] src, int w, int h, int x, int y,
            float[] mask, int mw, int mh, int mx, int my, float currentConfidence) {
        if (src == null || mask == null || mw < 2 || mh < 2) return -1;

        int left = Math.max(0, mx - 1);
        int right = Math.min(mw - 1, mx + 1);
        int top = Math.max(0, my - 1);
        int bottom = Math.min(mh - 1, my + 1);

        float gx = mask[my * mw + right] - mask[my * mw + left];
        float gy = mask[bottom * mw + mx] - mask[top * mw + mx];
        float len = (float)Math.sqrt(gx * gx + gy * gy);

        // If the gradient is weak, search the compact neighbourhood for the strongest
        // foreground confidence and use that direction.
        if (len < 0.015f) {
            float best = currentConfidence;
            int bestX = mx;
            int bestY = my;
            for (int yy = Math.max(0, my - 2); yy <= Math.min(mh - 1, my + 2); yy++) {
                for (int xx = Math.max(0, mx - 2); xx <= Math.min(mw - 1, mx + 2); xx++) {
                    float v = mask[yy * mw + xx];
                    if (v > best) {
                        best = v;
                        bestX = xx;
                        bestY = yy;
                    }
                }
            }
            gx = bestX - mx;
            gy = bestY - my;
            len = (float)Math.sqrt(gx * gx + gy * gy);
        }

        if (len < 0.001f) return -1;
        gx /= len;
        gy /= len;

        int bestColor = -1;
        float bestConfidence = currentConfidence;

        // Walk inward on the person mask. Sampling 1..6 mask pixels is cheap and
        // preserves the local hair/skin/clothing color instead of smearing unrelated areas.
        for (int step = 1; step <= 6; step++) {
            int smx = Math.max(0, Math.min(mw - 1, Math.round(mx + gx * step)));
            int smy = Math.max(0, Math.min(mh - 1, Math.round(my + gy * step)));
            float conf = mask[smy * mw + smx];

            if (conf >= bestConfidence + 0.035f || (bestColor == -1 && conf > 0.58f)) {
                int sx = Math.max(0, Math.min(w - 1,
                        Math.round(smx * (w - 1f) / Math.max(1f, mw - 1f))));
                int sy = Math.max(0, Math.min(h - 1,
                        Math.round(smy * (h - 1f) / Math.max(1f, mh - 1f))));
                int candidate = src[sy * w + sx];

                if (bestColor == -1
                        || isSkinLikeColor(Color.red(candidate), Color.green(candidate), Color.blue(candidate))
                        || conf > bestConfidence + 0.08f) {
                    bestColor = candidate;
                    bestConfidence = conf;
                }
            }

            if (bestConfidence > 0.88f) break;
        }

        return bestColor;
    }

    private float colorDistanceSq(int r1, int g1, int b1, int r2, int g2, int b2) {
        float dr = r1 - r2;
        float dg = g1 - g2;
        float db = b1 - b2;
        return dr * dr + dg * dg + db * db;
    }

    private int sampleOuterBackgroundColor(int[] src, int w, int h, int x, int y,
            float[] mask, int mw, int mh, int mx, int my, float currentConfidence) {
        if (src == null || mask == null || mw < 2 || mh < 2) return -1;

        int left = Math.max(0, mx - 1);
        int right = Math.min(mw - 1, mx + 1);
        int top = Math.max(0, my - 1);
        int bottom = Math.min(mh - 1, my + 1);

        float gx = mask[my * mw + right] - mask[my * mw + left];
        float gy = mask[bottom * mw + mx] - mask[top * mw + mx];
        float len = (float)Math.sqrt(gx * gx + gy * gy);

        if (len < 0.015f) {
            float worst = currentConfidence;
            int worstX = mx;
            int worstY = my;
            for (int yy = Math.max(0, my - 2); yy <= Math.min(mh - 1, my + 2); yy++) {
                for (int xx = Math.max(0, mx - 2); xx <= Math.min(mw - 1, mx + 2); xx++) {
                    float v = mask[yy * mw + xx];
                    if (v < worst) {
                        worst = v;
                        worstX = xx;
                        worstY = yy;
                    }
                }
            }
            gx = mx - worstX;
            gy = my - worstY;
            len = (float)Math.sqrt(gx * gx + gy * gy);
        }

        if (len < 0.001f) return -1;
        gx /= len;
        gy /= len;

        int bestColor = -1;
        float bestConfidence = currentConfidence;

        // Walk opposite the foreground gradient to find a clean patch of the
        // original background immediately outside the person.
        for (int step = 1; step <= 7; step++) {
            int smx = Math.max(0, Math.min(mw - 1, Math.round(mx - gx * step)));
            int smy = Math.max(0, Math.min(mh - 1, Math.round(my - gy * step)));
            float conf = mask[smy * mw + smx];

            if (conf <= bestConfidence - 0.035f || (bestColor == -1 && conf < 0.42f)) {
                int sx = Math.max(0, Math.min(w - 1,
                        Math.round(smx * (w - 1f) / Math.max(1f, mw - 1f))));
                int sy = Math.max(0, Math.min(h - 1,
                        Math.round(smy * (h - 1f) / Math.max(1f, mh - 1f))));
                bestColor = src[sy * w + sx];
                bestConfidence = conf;
            }

            if (bestConfidence < 0.12f) break;
        }

        return bestColor;
    }

    private int[] findMaskBounds(float[] mask, int w, int h, float threshold) {
        if (mask == null || mask.length < w * h) return null;
        int minX = w, minY = h, maxX = -1, maxY = -1;

        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                if (mask[row + x] >= threshold) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        return maxX >= minX && maxY >= minY
                ? new int[]{minX, minY, maxX, maxY}
                : null;
    }

    private boolean isInsideHeadZone(int x, int y, int[] bounds, int w, int h) {
        if (bounds == null) return y < Math.round(h * 0.46f);

        int bw = Math.max(1, bounds[2] - bounds[0] + 1);
        int bh = Math.max(1, bounds[3] - bounds[1] + 1);
        int left = Math.max(0, bounds[0] - Math.round(bw * 0.12f));
        int right = Math.min(w - 1, bounds[2] + Math.round(bw * 0.12f));
        int top = Math.max(0, bounds[1] - Math.round(bh * 0.04f));
        int bottom = Math.min(h - 1, bounds[1] + Math.round(bh * 0.38f));

        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private float[] maxFilterMask(float[] src, int w, int h, int radius) {
        if (src == null || radius <= 0 || w <= 1 || h <= 1) return src;

        float[] tmp = new float[src.length];
        float[] out = new float[src.length];

        // Horizontal max filter.
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float m = 0f;
                int from = Math.max(0, x - radius);
                int to = Math.min(w - 1, x + radius);
                for (int xx = from; xx <= to; xx++) {
                    float v = src[row + xx];
                    if (v > m) m = v;
                }
                tmp[row + x] = m;
            }
        }

        // Vertical max filter.
        for (int y = 0; y < h; y++) {
            int from = Math.max(0, y - radius);
            int to = Math.min(h - 1, y + radius);
            for (int x = 0; x < w; x++) {
                float m = 0f;
                for (int yy = from; yy <= to; yy++) {
                    float v = tmp[yy * w + x];
                    if (v > m) m = v;
                }
                out[y * w + x] = m;
            }
        }
        return out;
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

    private void toggleBrushMode() {
        if (resultBitmap == null) return;
        brushModeOn = !brushModeOn;
        if (brushModeOn) {
            colorCleanOn = false;
            objectButton.setText("LOCAL COLOR CLEAN");
            brushButton.setText("BRUSH ✓");
            if (toolSeekLabel != null) toolSeekLabel.setText("Brush Size");
            brushSeek.setProgress(brushSizeValue);
            status.setText("Brush ON • circle देखकर edge/छूटा colour manually साफ करें");
            imageView.post(() -> showBrushCursor(imageView.getWidth() * 0.5f, imageView.getHeight() * 0.5f));
        } else {
            brushButton.setText("BRUSH");
            if (brushCursor != null) brushCursor.setVisibility(View.GONE);
            if (toolSeekLabel != null) toolSeekLabel.setText("Color Tolerance");
            brushSeek.setProgress(colorToleranceValue);
            status.setText("Brush OFF");
        }
    }

    private void toggleColorClean() {
        if (resultBitmap == null) return;
        colorCleanOn = !colorCleanOn;
        if (colorCleanOn) {
            brushModeOn = false;
            brushButton.setText("BRUSH");
            if (toolSeekLabel != null) toolSeekLabel.setText("Color Tolerance");
            brushSeek.setProgress(colorToleranceValue);
        }
        if (compareOriginal && colorCleanOn) {
            compareOriginal = false;
            imageView.setImageBitmap(resultBitmap);
            compareButton.setText("COMPARE");
        }
        objectButton.setText(colorCleanOn ? "LOCAL COLOR CLEAN ✓" : "LOCAL COLOR CLEAN");
        status.setText(colorCleanOn
                ? "Zoom करें और हटाने वाले colour पर एक बार tap करें"
                : "Local Color Clean OFF");
    }

    private boolean handlePhotoTouch(View v, MotionEvent e) {
        if (scaleGestureDetector != null) scaleGestureDetector.onTouchEvent(e);

        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            gestureWasScaling = true;
            brushStrokeStarted = false;
            brushStrokeChanged = false;
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP) return true;

        if (action == MotionEvent.ACTION_DOWN) {
            lastPanX = e.getX();
            lastPanY = e.getY();
            lastBrushViewX = e.getX();
            lastBrushViewY = e.getY();
            if (brushModeOn) showBrushCursor(e.getX(), e.getY());
            panMoved = false;
            gestureWasScaling = false;
            brushStrokeStarted = false;
            brushStrokeChanged = false;
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (e.getPointerCount() > 1 || (scaleGestureDetector != null && scaleGestureDetector.isInProgress())) {
                gestureWasScaling = true;
                brushStrokeStarted = false;
                brushStrokeChanged = false;
                return true;
            }

            if (brushModeOn && !compareOriginal && resultBitmap != null) {
                showBrushCursor(e.getX(), e.getY());
                float dx = e.getX() - lastBrushViewX;
                float dy = e.getY() - lastBrushViewY;
                if (Math.abs(dx) > dp(1) || Math.abs(dy) > dp(1)) {
                    if (!brushStrokeStarted) {
                        pushUndo();
                        clearDeque(redoMasks);
                        brushStrokeStarted = true;
                    }
                    drawBrushSegment(lastBrushViewX, lastBrushViewY, e.getX(), e.getY());
                    lastBrushViewX = e.getX();
                    lastBrushViewY = e.getY();
                }
                return true;
            }

            float dx = e.getX() - lastPanX;
            float dy = e.getY() - lastPanY;
            if (zoomFactor > 1.001f) {
                if (Math.abs(dx) > dp(1) || Math.abs(dy) > dp(1)) panMoved = true;
                photoMatrix.postTranslate(dx, dy);
                constrainPhotoMatrix();
                imageView.setImageMatrix(photoMatrix);
            }
            lastPanX = e.getX();
            lastPanY = e.getY();
            return true;
        }

        if (action == MotionEvent.ACTION_UP) {
            if (brushModeOn && !compareOriginal && resultBitmap != null && !gestureWasScaling) {
                showBrushCursor(e.getX(), e.getY());
                if (!brushStrokeStarted) {
                    pushUndo();
                    clearDeque(redoMasks);
                    brushStrokeStarted = true;
                    drawBrushSegment(e.getX(), e.getY(), e.getX(), e.getY());
                }
                if (brushStrokeChanged) {
                    updateHistoryButtons();
                    status.setText("Brush apply • edge smooth / leftover clean");
                    renderResult();
                } else if (!undoMasks.isEmpty()) {
                    undoMasks.pop();
                    updateHistoryButtons();
                }
                brushStrokeStarted = false;
                brushStrokeChanged = false;
            } else if (colorCleanOn && !compareOriginal && resultBitmap != null
                    && !panMoved && !gestureWasScaling
                    && (scaleGestureDetector == null || !scaleGestureDetector.isInProgress())) {
                applyLocalColorCleanAt(e.getX(), e.getY());
            }
            gestureWasScaling = false;
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            brushStrokeStarted = false;
            brushStrokeChanged = false;
            gestureWasScaling = false;
            return true;
        }

        return true;
    }

    private float currentBrushScreenRadius() {
        float density = getResources().getDisplayMetrics().density;
        return density * (6f + 22f * (brushSizeValue / 100f));
    }

    private float currentImageScale() {
        float[] values = new float[9];
        photoMatrix.getValues(values);
        float sx = values[Matrix.MSCALE_X];
        float sy = values[Matrix.MSKEW_Y];
        return Math.max(0.0001f, (float)Math.sqrt(sx * sx + sy * sy));
    }

    private void showBrushCursor(float viewX, float viewY) {
        if (!brushModeOn || brushCursor == null || imageView == null) return;
        float radius = currentBrushScreenRadius();
        int size = Math.max(dp(12), Math.round(radius * 2f));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) brushCursor.getLayoutParams();
        lp.width = size;
        lp.height = size;
        lp.leftMargin = Math.round(viewX - radius);
        lp.topMargin = Math.round(viewY - radius);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        brushCursor.setLayoutParams(lp);
        brushCursor.setVisibility(View.VISIBLE);
        brushCursor.bringToFront();
        if (status != null) status.bringToFront();
    }

    private void drawBrushSegment(float vx1, float vy1, float vx2, float vy2) {
        try {
            if (eraseMask == null) createEmptyEraseMask();
            if (eraseMask == null) return;

            Matrix inv = new Matrix();
            if (!photoMatrix.invert(inv)) return;

            float[] pts = new float[]{vx1, vy1, vx2, vy2};
            inv.mapPoints(pts);

            float x1 = pts[0], y1 = pts[1], x2 = pts[2], y2 = pts[3];
            float distance = (float)Math.hypot(x2 - x1, y2 - y1);
            float radius = Math.max(2f, currentBrushScreenRadius() / currentImageScale());
            int steps = Math.max(1, (int)Math.ceil(distance / Math.max(1f, radius * 0.35f)));

            for (int i = 0; i <= steps; i++) {
                float t = steps == 0 ? 0f : i / (float)steps;
                float x = x1 + (x2 - x1) * t;
                float y = y1 + (y2 - y1) * t;
                paintSoftErasePoint(x, y, radius);
            }
            brushStrokeChanged = true;
        } catch (Throwable ignored) {
        }
    }

    private void paintSoftErasePoint(float cx, float cy, float radius) {
        int w = eraseMask.getWidth();
        int h = eraseMask.getHeight();
        int left = Math.max(0, (int)Math.floor(cx - radius));
        int top = Math.max(0, (int)Math.floor(cy - radius));
        int right = Math.min(w - 1, (int)Math.ceil(cx + radius));
        int bottom = Math.min(h - 1, (int)Math.ceil(cy + radius));
        if (right < left || bottom < top) return;

        int rw = right - left + 1;
        int rh = bottom - top + 1;
        int[] px = new int[rw * rh];
        eraseMask.getPixels(px, 0, rw, left, top, rw, rh);

        float inner = radius * 0.58f;
        float feather = Math.max(1f, radius - inner);

        for (int yy = 0; yy < rh; yy++) {
            float py = top + yy + 0.5f;
            for (int xx = 0; xx < rw; xx++) {
                float pxX = left + xx + 0.5f;
                float d = (float)Math.hypot(pxX - cx, py - cy);
                if (d > radius) continue;

                float strength;
                if (d <= inner) strength = 1f;
                else {
                    float u = 1f - (d - inner) / feather;
                    strength = u * u * (3f - 2f * u);
                }

                int index = yy * rw + xx;
                int oldA = Color.alpha(px[index]);
                int newA = Math.max(oldA, Math.round(255f * strength));
                px[index] = Color.argb(newA, 255, 255, 255);
            }
        }

        eraseMask.setPixels(px, 0, rw, left, top, rw, rh);
    }

    private void applyLocalColorCleanAt(float viewX, float viewY) {
        try {
            if (resultBitmap == null || originalBitmap == null) return;
            if (eraseMask == null) createEmptyEraseMask();
            if (eraseMask == null) return;

            Matrix inv = new Matrix();
            if (!photoMatrix.invert(inv)) return;
            float[] point = new float[]{viewX, viewY};
            inv.mapPoints(point);
            int sx = Math.round(point[0]);
            int sy = Math.round(point[1]);

            int w = resultBitmap.getWidth();
            int h = resultBitmap.getHeight();
            if (sx < 0 || sy < 0 || sx >= w || sy >= h) return;

            int target = resultBitmap.getPixel(sx, sy);
            int tr = Color.red(target);
            int tg = Color.green(target);
            int tb = Color.blue(target);

            int br = Color.red(BLUE);
            int bg = Color.green(BLUE);
            int bb = Color.blue(BLUE);
            if (colorDistanceSq(tr, tg, tb, br, bg, bb) < 900f) {
                status.setText("यह पहले से background है • बचा हुआ colour select करें");
                return;
            }

            pushUndo();
            clearDeque(redoMasks);

            int localRadius = Math.min(120, Math.max(36,
                    Math.round(Math.min(w, h) * 0.055f)));
            int minX = Math.max(0, sx - localRadius);
            int maxX = Math.min(w - 1, sx + localRadius);
            int minY = Math.max(0, sy - localRadius);
            int maxY = Math.min(h - 1, sy + localRadius);
            int boxW = maxX - minX + 1;
            int boxH = maxY - minY + 1;

            boolean[] visited = new boolean[boxW * boxH];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add((sy - minY) * boxW + (sx - minX));

            float tolerance = 6f + brushSeek.getProgress() * 0.28f;
            float toleranceSq = tolerance * tolerance;
            float targetLum = 0.299f * tr + 0.587f * tg + 0.114f * tb;
            boolean targetSkin = isSkinLikeColor(tr, tg, tb);
            int[] resultPixels = new int[w * h];
            int[] maskPixels = new int[w * h];
            resultBitmap.getPixels(resultPixels, 0, w, 0, 0, w, h);
            eraseMask.getPixels(maskPixels, 0, w, 0, 0, w, h);

            int changed = 0;
            final int maxChanged = 35000;

            while (!queue.isEmpty() && changed < maxChanged) {
                int q = queue.removeFirst();
                if (q < 0 || q >= visited.length || visited[q]) continue;
                visited[q] = true;

                int lx = q % boxW;
                int ly = q / boxW;
                int x = minX + lx;
                int y = minY + ly;

                int dx = x - sx;
                int dy = y - sy;
                if (dx * dx + dy * dy > localRadius * localRadius) continue;

                int c = resultPixels[y * w + x];
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                if (colorDistanceSq(r, g, b, tr, tg, tb) > toleranceSq) continue;

                float channelLimit = tolerance * 0.92f + 2f;
                if (Math.abs(r - tr) > channelLimit
                        || Math.abs(g - tg) > channelLimit
                        || Math.abs(b - tb) > channelLimit) continue;

                float lum = 0.299f * r + 0.587f * g + 0.114f * b;
                if (Math.abs(lum - targetLum) > tolerance * 0.90f + 2f) continue;

                boolean pixelSkin = isSkinLikeColor(r, g, b);
                if (!targetSkin && pixelSkin) continue;

                maskPixels[y * w + x] = Color.argb(255, 255, 255, 255);
                changed++;

                if (lx > 0) queue.add(q - 1);
                if (lx + 1 < boxW) queue.add(q + 1);
                if (ly > 0) queue.add(q - boxW);
                if (ly + 1 < boxH) queue.add(q + boxW);

                // Diagonals keep thin ear/hair fringe connected without jumping to
                // another same-colour object elsewhere in the photo.
                if (lx > 0 && ly > 0) queue.add(q - boxW - 1);
                if (lx + 1 < boxW && ly > 0) queue.add(q - boxW + 1);
                if (lx > 0 && ly + 1 < boxH) queue.add(q + boxW - 1);
                if (lx + 1 < boxW && ly + 1 < boxH) queue.add(q + boxW + 1);
            }

            if (changed == 0) {
                if (!undoMasks.isEmpty()) undoMasks.pop();
                updateHistoryButtons();
                status.setText("इस target पर matching edge colour नहीं मिला");
                return;
            }

            eraseMask.setPixels(maskPixels, 0, w, 0, 0, w, h);
            updateHistoryButtons();
            status.setText("Local clean • " + changed + " pixels • सिर्फ target area");
            renderResult();
        } catch (Throwable ignored) {
            status.setText("Color clean apply नहीं हुआ");
        }
    }

    private boolean isNearSubjectBoundary(int x, int y, int imageW, int imageH) {
        if (personMask == null || maskWidth <= 0 || maskHeight <= 0) return true;

        int mx = Math.max(0, Math.min(maskWidth - 1,
                Math.round(x * (maskWidth - 1f) / Math.max(1f, imageW - 1f))));
        int my = Math.max(0, Math.min(maskHeight - 1,
                Math.round(y * (maskHeight - 1f) / Math.max(1f, imageH - 1f))));

        float min = 1f;
        float max = 0f;
        int radius = 3;
        for (int yy = Math.max(0, my - radius); yy <= Math.min(maskHeight - 1, my + radius); yy++) {
            for (int xx = Math.max(0, mx - radius); xx <= Math.min(maskWidth - 1, mx + radius); xx++) {
                float v = personMask[yy * maskWidth + xx];
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        return max > 0.06f && min < 0.985f;
    }

    private void resetZoom() {
        Bitmap shown = compareOriginal ? originalBitmap : (resultBitmap != null ? resultBitmap : originalBitmap);
        if (shown == null || imageView.getWidth() <= 0 || imageView.getHeight() <= 0) return;

        float vw = imageView.getWidth();
        float vh = imageView.getHeight();
        float bw = shown.getWidth();
        float bh = shown.getHeight();
        float fit = Math.min(vw / bw, vh / bh);
        float dx = (vw - bw * fit) * 0.5f;
        float dy = (vh - bh * fit) * 0.5f;

        photoMatrix.reset();
        photoMatrix.postScale(fit, fit);
        photoMatrix.postTranslate(dx, dy);
        zoomFactor = 1f;
        imageView.setImageMatrix(photoMatrix);
    }

    private void constrainPhotoMatrix() {
        Bitmap shown = compareOriginal ? originalBitmap : (resultBitmap != null ? resultBitmap : originalBitmap);
        if (shown == null || imageView.getWidth() <= 0 || imageView.getHeight() <= 0) return;

        RectF rect = new RectF(0, 0, shown.getWidth(), shown.getHeight());
        photoMatrix.mapRect(rect);

        float vw = imageView.getWidth();
        float vh = imageView.getHeight();
        float dx = 0f;
        float dy = 0f;

        if (rect.width() <= vw) dx = vw * 0.5f - rect.centerX();
        else if (rect.left > 0f) dx = -rect.left;
        else if (rect.right < vw) dx = vw - rect.right;

        if (rect.height() <= vh) dy = vh * 0.5f - rect.centerY();
        else if (rect.top > 0f) dy = -rect.top;
        else if (rect.bottom < vh) dy = vh - rect.bottom;

        photoMatrix.postTranslate(dx, dy);
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

    private void clearHistory() {
        clearDeque(undoMasks);
        clearDeque(redoMasks);
        updateHistoryButtons();
    }

    private void clearDeque(ArrayDeque<Bitmap> q) {
        q.clear();
    }

    private void sharePhoto() {
        if (resultBitmap == null) {
            toast("पहले फोटो तैयार करें");
            return;
        }

        final Bitmap copy = resultBitmap.copy(Bitmap.Config.ARGB_8888, false);
        setBusy(true, "Share तैयार हो रहा है…");

        try {
            worker.execute(() -> {
                Uri uri = null;
                try {
                    String name = "STS_Photo_Background_Remover_Share_" + System.currentTimeMillis() + ".jpg";
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

                    if (Build.VERSION.SDK_INT >= 29) {
                        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/STS Photo Background Remover/Shared");
                        values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    } else {
                        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                                "STS Passport Size Photo/Shared");
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

                    final Uri shareUri = uri;
                    runOnUiThread(() -> {
                        if (destroyed) return;
                        stopProcessingAnimation();
                        status.setText("Ready • Share");
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("image/jpeg");
                        send.putExtra(Intent.EXTRA_STREAM, shareUri);
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try {
                            startActivity(Intent.createChooser(send, "Photo share करें"));
                        } catch (Exception e) {
                            toast("Share नहीं खुला");
                        }
                    });
                } catch (Exception e) {
                    final Uri failedUri = uri;
                    if (failedUri != null) {
                        try { getContentResolver().delete(failedUri, null, null); } catch (Exception ignored) {}
                    }
                    runOnUiThread(() -> {
                        if (!destroyed) {
                            setBusy(false, "Share failed");
                            toast("Share तैयार नहीं हुआ");
                        }
                    });
                }
            });
        } catch (RejectedExecutionException ignored) {
            setBusy(false, "Share failed");
        }
    }

    private void savePhoto() {
        if (resultBitmap == null || originalBitmap == null || personMask == null) {
            toast("पहले फोटो तैयार करें");
            return;
        }

        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_SAVE_PERMISSION);
            return;
        }

        final Uri saveSourceUri = sourceUri;
        final Bitmap previewSource = originalBitmap.copy(Bitmap.Config.ARGB_8888, false);
        final Bitmap saveErase = eraseMask == null ? null : eraseMask.copy(Bitmap.Config.ALPHA_8, false);
        final float[] saveMask = personMask.clone();
        final int saveMaskW = maskWidth;
        final int saveMaskH = maskHeight;
        final int saveBrightness = brightnessSeek.getProgress();
        final int saveSmooth = smoothSeek.getProgress();

        setBusy(true, "Original size में फोटो सेव हो रही है…");

        worker.execute(() -> {
            Uri uri = null;
            Bitmap full = null;
            try {
                if (saveSourceUri == null) throw new IllegalStateException("source uri missing");

                full = decodeOriginalFull(saveSourceUri);
                if (full == null) throw new IllegalStateException("full decode failed");

                renderFullResolutionForSave(full, previewSource, saveMask, saveMaskW, saveMaskH,
                        saveErase, saveBrightness, saveSmooth);

                String name = "STS_Photo_Background_Remover_" + System.currentTimeMillis() + ".jpg";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.WIDTH, full.getWidth());
                values.put(MediaStore.Images.Media.HEIGHT, full.getHeight());

                if (Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/STS Photo Background Remover");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "STS Photo Background Remover");
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("folder");
                    File file = new File(dir, name);
                    values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
                }

                uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("insert");

                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null || !full.compress(Bitmap.CompressFormat.JPEG, 100, out)) {
                        throw new IllegalStateException("write");
                    }
                }

                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(uri, ready, null, null);
                }

                final int savedW = full.getWidth();
                final int savedH = full.getHeight();
                runOnUiThread(() -> {
                    setBusy(false, "Saved • Original Size " + savedW + "×" + savedH);
                    toast("Original size में फोटो सेव हो गई");
                });
            } catch (OutOfMemoryError oom) {
                if (uri != null) {
                    try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) {}
                }
                runOnUiThread(() -> {
                    setBusy(false, "Original-size save memory error");
                    toast("Original size save के लिए memory कम है");
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
                if (full != null && !full.isRecycled()) full.recycle();
                if (previewSource != null && !previewSource.isRecycled()) previewSource.recycle();
                if (saveErase != null && !saveErase.isRecycled()) saveErase.recycle();
            }
        });
    }

    private Bitmap decodeOriginalFull(Uri uri) throws Exception {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
            Bitmap decoded = ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setMutableRequired(true);
            });
            if (decoded.getConfig() == Bitmap.Config.ARGB_8888 && decoded.isMutable()) return decoded;
            Bitmap copy = decoded.copy(Bitmap.Config.ARGB_8888, true);
            if (decoded != copy && !decoded.isRecycled()) decoded.recycle();
            return copy;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        opts.inMutable = true;
        opts.inSampleSize = 1;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            if (b == null) return null;
            if (b.getConfig() == Bitmap.Config.ARGB_8888 && b.isMutable()) return b;
            Bitmap copy = b.copy(Bitmap.Config.ARGB_8888, true);
            if (b != copy && !b.isRecycled()) b.recycle();
            return copy;
        }
    }

    private void renderFullResolutionForSave(Bitmap full, Bitmap previewSource,
            float[] mask, int mw, int mh, Bitmap manualMask,
            int brightness, int smooth) {

        int w = full.getWidth();
        int h = full.getHeight();
        int pw = previewSource.getWidth();
        int ph = previewSource.getHeight();

        int[] previewSrc = new int[pw * ph];
        previewSource.getPixels(previewSrc, 0, pw, 0, 0, pw, ph);

        int[] manual = null;
        int manualW = 0;
        int manualH = 0;
        if (manualMask != null) {
            manualW = manualMask.getWidth();
            manualH = manualMask.getHeight();
            manual = new int[manualW * manualH];
            manualMask.getPixels(manual, 0, manualW, 0, 0, manualW, manualH);
        }

        float smoothAmount = smooth / 100f;
        int blurRadius = Math.round(smoothAmount * 7f);
        float[] smoothMask = blurMask(mask, mw, mh, blurRadius);
        int hairRadius = 2 + Math.round(smoothAmount * 2f);
        float[] hairSupportMask = maxFilterMask(smoothMask, mw, mh, hairRadius);
        int[] personBounds = findMaskBounds(mask, mw, mh, 0.55f);

        float threshold = 0.50f + (0.10f * smoothAmount);
        float feather = 0.12f - (0.025f * smoothAmount);
        float low = threshold - feather * 0.5f;
        float high = threshold + feather * 0.5f;
        float brighten = (brightness / 100f) * 0.32f;

        int br = Color.red(BLUE);
        int bg = Color.green(BLUE);
        int bb = Color.blue(BLUE);

        int[] row = new int[w];

        for (int y = 0; y < h; y++) {
            full.getPixels(row, 0, w, 0, y, w, 1);

            int my = Math.min(mh - 1, Math.max(0,
                    Math.round(y * (mh - 1f) / Math.max(1f, h - 1f))));
            int py = Math.min(ph - 1, Math.max(0,
                    Math.round(y * (ph - 1f) / Math.max(1f, h - 1f))));

            for (int x = 0; x < w; x++) {
                int mx = Math.min(mw - 1, Math.max(0,
                        Math.round(x * (mw - 1f) / Math.max(1f, w - 1f))));
                int px = Math.min(pw - 1, Math.max(0,
                        Math.round(x * (pw - 1f) / Math.max(1f, w - 1f))));

                int color = row[x];
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                float luminance = 0.299f * r + 0.587f * g + 0.114f * b;

                int maskIndex = Math.min(smoothMask.length - 1, my * mw + mx);
                float confidence = smoothMask[maskIndex];
                float hairSupport = hairSupportMask[maskIndex];

                boolean headZone = isInsideHeadZone(mx, my, personBounds, mw, mh);
                boolean skinLike = isSkinLikeColor(r, g, b);
                boolean hairCandidate = headZone
                        && !skinLike
                        && luminance < 165f
                        && hairSupport > 0.30f;

                float a;
                if (hairCandidate) {
                    confidence = Math.max(confidence, Math.min(1f, hairSupport * 0.92f + 0.10f));
                    float hairThreshold = 0.31f + 0.04f * smoothAmount;
                    float hairFeather = 0.30f + 0.06f * smoothAmount;
                    a = smoothStep(hairThreshold - hairFeather * 0.5f,
                            hairThreshold + hairFeather * 0.5f, confidence);
                    if (hairSupport > 0.62f && a < 0.58f) a = 0.58f;
                } else {
                    a = smoothStep(low, high, confidence);
                    if (smoothAmount > 0f && a > 0f && a < 1f) {
                        a = (float)Math.pow(a, 1.0f + 1.8f * smoothAmount);
                        float edgeCut = 0.18f * smoothAmount;
                        a = Math.max(0f, Math.min(1f,
                                (a - edgeCut) / Math.max(0.01f, 1f - edgeCut)));
                    }
                }

                boolean skinEdge = skinLike && headZone && a > 0.03f && a < 0.985f;
                if (skinEdge) {
                    float skinA = smoothStep(0.44f, 0.62f, confidence);
                    float keep = 0.30f + 0.35f * (1f - smoothAmount);
                    a = Math.max(0f, Math.min(1f, skinA - keep * (1f - skinA) * 0.18f));
                }

                if (a > 0.025f && a < 0.995f) {
                    int inner = sampleInnerForegroundColor(previewSrc, pw, ph, px, py,
                            smoothMask, mw, mh, mx, my, confidence);
                    int outer = sampleOuterBackgroundColor(previewSrc, pw, ph, px, py,
                            smoothMask, mw, mh, mx, my, confidence);

                    if (inner != -1) {
                        int ir = Color.red(inner);
                        int ig = Color.green(inner);
                        int ib = Color.blue(inner);
                        float edgeBand = 1f - Math.abs(a * 2f - 1f);
                        float matchStrength = 0.20f + 0.35f * edgeBand;

                        if (outer != -1) {
                            int or = Color.red(outer);
                            int og = Color.green(outer);
                            int ob = Color.blue(outer);
                            float dInner = colorDistanceSq(r, g, b, ir, ig, ib);
                            float dOuter = colorDistanceSq(r, g, b, or, og, ob);

                            float solveA = Math.max(0.34f, Math.min(0.96f, a));
                            int fr = clamp255(Math.round((r - (1f - solveA) * or) / solveA));
                            int fg = clamp255(Math.round((g - (1f - solveA) * og) / solveA));
                            int fb = clamp255(Math.round((b - (1f - solveA) * ob) / solveA));

                            float recover = 0.48f + 0.34f * edgeBand;
                            fr = clamp255(Math.round(fr * recover + ir * (1f - recover)));
                            fg = clamp255(Math.round(fg * recover + ig * (1f - recover)));
                            fb = clamp255(Math.round(fb * recover + ib * (1f - recover)));

                            boolean outerContaminated = dOuter + 120f < dInner;
                            if (outerContaminated) {
                                matchStrength = Math.max(matchStrength, 0.78f);
                                if (!hairCandidate) a *= skinEdge ? 0.78f : 0.84f;
                            }

                            r = clamp255(Math.round(r * (1f - matchStrength) + fr * matchStrength));
                            g = clamp255(Math.round(g * (1f - matchStrength) + fg * matchStrength));
                            b = clamp255(Math.round(b * (1f - matchStrength) + fb * matchStrength));
                        } else {
                            if (hairCandidate) matchStrength = Math.max(matchStrength, 0.58f * edgeBand);
                            if (skinEdge) matchStrength = Math.max(matchStrength, 0.72f * edgeBand);
                            r = clamp255(Math.round(r * (1f - matchStrength) + ir * matchStrength));
                            g = clamp255(Math.round(g * (1f - matchStrength) + ig * matchStrength));
                            b = clamp255(Math.round(b * (1f - matchStrength) + ib * matchStrength));
                        }
                    }
                }

                if (manual != null) {
                    int ex = Math.min(manualW - 1, Math.max(0,
                            Math.round(x * (manualW - 1f) / Math.max(1f, w - 1f))));
                    int ey = Math.min(manualH - 1, Math.max(0,
                            Math.round(y * (manualH - 1f) / Math.max(1f, h - 1f))));
                    float eraseA = Color.alpha(manual[ey * manualW + ex]) / 255f;
                    a *= (1f - eraseA);
                }

                if (a > 0.03f && brighten > 0f) {
                    float interior = smoothStep(0.48f, 0.93f, a);
                    float localBrighten = brighten * interior;
                    r = clamp255(Math.round(r + (255 - r) * localBrighten));
                    g = clamp255(Math.round(g + (255 - g) * localBrighten));
                    b = clamp255(Math.round(b + (255 - b) * localBrighten));
                }

                int rr = clamp255(Math.round(r * a + br * (1f - a)));
                int gg = clamp255(Math.round(g * a + bg * (1f - a)));
                int bbv = clamp255(Math.round(b * a + bb * (1f - a)));
                row[x] = Color.rgb(rr, gg, bbv);
            }

            full.setPixels(row, 0, w, 0, y, w, 1);
        }
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
        stopProcessingAnimation();
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
