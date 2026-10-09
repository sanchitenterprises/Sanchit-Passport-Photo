package com.sanchit.passportfresh;

import android.Manifest;
import android.util.Base64;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
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
import android.widget.PopupMenu;
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
    private static final int[] PHOTO_BG_COLORS = new int[]{
            BLUE,                   // Standard blue
            Color.rgb(176, 218, 242), // Sky blue
            Color.WHITE,            // White
            Color.rgb(226, 230, 234), // Light grey
            Color.rgb(196, 64, 64),   // Passport/photo red
            Color.rgb(245, 235, 213), // Cream
            Color.rgb(64, 160, 87)    // Green
    };
    private static final String[] PHOTO_BG_NAMES = new String[]{
            "Blue", "Sky Blue", "White", "Light Grey", "Red", "Cream", "Green"
    };
    private static final int MAX_SIDE = 1440;
    private static final int MAX_HISTORY = 8;

    private static final String[] BRUSH_SHAPE_NAMES = new String[]{
            "Soft Round", "Soft Wide Round", "Soft Half-Circle", "Soft Vertical",
            "Soft Horizontal", "Soft Wedge", "Soft Point"
    };
    private static final String[] BRUSH_SHAPE_PNG = new String[]{
            "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"
    };

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayDeque<Bitmap> undoMasks = new ArrayDeque<>();
    private final ArrayDeque<Bitmap> redoMasks = new ArrayDeque<>();

    private ImageView imageView;
    private ProgressBar progress;
    private TextView status;
    private ImageView processingLogo;
    private View processingShade;
    private LensView magnifierLens;
    private BrushCursorView brushCursor;
    private AnimatorSet processingAnimator;
    private SeekBar brightnessSeek;
    private SeekBar fairnessSeek;
    private SeekBar smoothSeek;
    private SeekBar brushSeek;
    private Button compareButton;
    private Button brushButton;
    private Button objectButton;
    private Button undoButton;
    private Button redoButton;

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
    private boolean brushFinalizing = false;
    private float lastBrushViewX;
    private float lastBrushViewY;
    private boolean dualBrushRotateActive = false;
    private float dualBrushRotateStartAngle = 0f;
    private float dualBrushRotateStartDegrees = 0f;
    private int brushShapeIndex = -1; // -1 = normal live brush; 0..6 = old shapes
    private float brushRotationDegrees = 0f;
    private TextView toolSeekLabel;
    private int colorToleranceValue = 22;
    private int brushSizeValue = 18;
    private int selectedBackgroundColor = BLUE;
    private LinearLayout backgroundColorRow;
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
        root.setBackgroundColor(0xFFF0F6F6);
        root.setPadding(dp(8), dp(6), dp(8), dp(7));

        // Compact professional header: fixed, no scrolling.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(3), dp(8), dp(3));
        header.setBackground(rounded(0xFFF9FCFC, 16));

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
        subtitle.setText("Created by Sanchit Kumar • v" + getAppVersion());
        subtitle.setTextColor(0xFF6B7280);
        subtitle.setTextSize(10);
        subtitle.setSingleLine(true);
        headerText.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        header.addView(headerText, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView menuButton = new TextView(this);
        menuButton.setText("⋮");
        menuButton.setTextSize(28);
        menuButton.setTextColor(0xFF1F2933);
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setClickable(true);
        menuButton.setFocusable(true);
        menuButton.setBackground(rippleRounded(0xFFF0F6F6, 14, 0x33000000));
        attachTouchAnimation(menuButton);
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(dp(42), dp(48));
        header.addView(menuButton, menuLp);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, dp(60));
        headerLp.bottomMargin = dp(6);
        root.addView(header, headerLp);

        // Preview area gets most free space and never scrolls.
        FrameLayout preview = new FrameLayout(this);
        preview.setBackground(rounded(0xFFE4ECEF, 18));

        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        imageView.setBackgroundColor(0xFFE4ECEF);
        preview.addView(imageView, new FrameLayout.LayoutParams(-1, -1));

        brushCursor = new BrushCursorView(this);
        brushCursor.setVisibility(View.GONE);
        preview.addView(brushCursor, new FrameLayout.LayoutParams(-1, -1));

        magnifierLens = new LensView(this);
        magnifierLens.setVisibility(View.GONE);
        FrameLayout.LayoutParams lensLp = new FrameLayout.LayoutParams(dp(132), dp(132));
        lensLp.gravity = Gravity.TOP | Gravity.LEFT;
        preview.addView(magnifierLens, lensLp);

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
        controls.setBackground(rounded(0xFFF8FBFA, 18));
        root.addView(controls, new LinearLayout.LayoutParams(-1, 0, 0.42f));

        backgroundColorRow = new LinearLayout(this);
        backgroundColorRow.setOrientation(LinearLayout.HORIZONTAL);
        backgroundColorRow.setGravity(Gravity.CENTER);
        backgroundColorRow.setPadding(dp(6), dp(2), dp(6), dp(2));
        controls.addView(backgroundColorRow, weightedControlRow(0.82f));
        renderToolChoiceRow();

        LinearLayout row1 = buttonRow();
        undoButton = button("UNDO", false);
        redoButton = button("REDO", false);
        row1.addView(undoButton, weightedButton());
        row1.addView(redoButton, weightedButton());
        controls.addView(row1, weightedControlRow(1.0f));

        brightnessSeek = compactSlider(controls, "Brightness", 22);
        fairnessSeek = compactSlider(controls, "Fairness", 28);
        smoothSeek = compactSlider(controls, "Smooth BG", 35);
        brushSeek = compactSlider(controls, "Color Tolerance", 22);

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
        Button camera = button("📷 कैमरा", false);
        Button gallery = button("🖼 फोटो चुनें", true);
        row3.addView(camera, weightedButton());
        row3.addView(gallery, weightedButton());
        controls.addView(row3, weightedControlRow(1.0f));

        setContentView(root);

        camera.setOnClickListener(v -> openCamera());
        gallery.setOnClickListener(v -> openGallery());
        compareButton.setOnClickListener(v -> toggleCompare());
        brushButton.setOnClickListener(v -> toggleBrushMode());
        objectButton.setOnClickListener(v -> toggleColorClean());
        undoButton.setOnClickListener(v -> undo());
        redoButton.setOnClickListener(v -> redo());
        menuButton.setOnClickListener(v -> showTopMenu(menuButton));

        SeekBar.OnSeekBarChangeListener redraw = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                if (fromUser && seekBar == smoothSeek && status != null) {
                    status.setText("Smooth BG " + p + "%");
                } else if (fromUser && seekBar == fairnessSeek && status != null) {
                    status.setText("Fairness " + p + "%");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (personMask != null && originalBitmap != null) renderResult();
            }
        };
        brightnessSeek.setOnSeekBarChangeListener(redraw);
        fairnessSeek.setOnSeekBarChangeListener(redraw);
        smoothSeek.setOnSeekBarChangeListener(redraw);
        brushSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                if (brushModeOn) {
                    brushSizeValue = p;
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
                hideLens();
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
        b.setPadding(dp(6), 0, dp(6), 0);

        int bgColor;
        int textColor = Color.WHITE;
        if (text.contains("UNDO")) bgColor = 0xFF7C7399;
        else if (text.contains("REDO")) bgColor = 0xFF7E9B76;
        else if (text.contains("COMPARE")) { bgColor = 0xFFB39B7A; textColor = 0xFF1F2933; }
        else if (text.contains("BRUSH")) bgColor = 0xFF4F8F8B;
        else if (text.contains("LOCAL")) bgColor = 0xFF5B7FA3;
        else if (text.contains("कैमरा")) bgColor = 0xFF7C7399;
        else if (text.contains("फोटो")) bgColor = 0xFF5B7FA3;
        else if (text.contains("SHARE")) { bgColor = 0xFFB39B7A; textColor = 0xFF1F2933; }
        else if (primary) bgColor = 0xFF5B7FA3;
        else { bgColor = 0xFFE8F0EF; textColor = 0xFF1F2933; }

        b.setTextColor(textColor);
        b.setBackground(rippleRounded(bgColor, 13,
                textColor == Color.WHITE ? 0x66FFFFFF : 0x33000000));
        attachTouchAnimation(b);
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
        if (Build.VERSION.SDK_INT >= 21) {
            int accent = "Brightness".equals(name) ? 0xFFB39B7A
                    : ("Fairness".equals(name) ? 0xFF7E9B76
                    : ("Smooth BG".equals(name) ? 0xFF4F8F8B : 0xFF7C7399));
            seek.setProgressTintList(ColorStateList.valueOf(accent));
            seek.setThumbTintList(ColorStateList.valueOf(accent));
        }
        row.addView(seek, new LinearLayout.LayoutParams(0, -1, 1f));

        parent.addView(row, weightedControlRow(0.82f));
        return seek;
    }

    private void renderToolChoiceRow() {
        if (backgroundColorRow == null) return;
        backgroundColorRow.removeAllViews();

        if (brushModeOn) {
            // First slot is the default NORMAL live brush.
            FrameLayout normalSlot = new FrameLayout(this);
            normalSlot.setTag(-1);
            boolean normalSelected = brushShapeIndex < 0;
            normalSlot.setBackground(rounded(normalSelected ? 0xFFD7EAE8 : 0xFFF8FBFA, 12));
            normalSlot.setElevation(normalSelected ? dp(3) : dp(1));

            TextView normalIcon = new TextView(this);
            normalIcon.setText("✎");
            normalIcon.setTextSize(24);
            normalIcon.setTextColor(0xFF111827);
            normalIcon.setGravity(Gravity.CENTER);
            normalIcon.setContentDescription("Normal Brush");
            normalSlot.addView(normalIcon, new FrameLayout.LayoutParams(-1, -1));
            normalSlot.setOnClickListener(v -> {
                brushShapeIndex = -1;
                brushRotationDegrees = 0f;
                dualBrushRotateActive = false;
                if (brushCursor != null) {
                    brushCursor.clearTrail();
                    brushCursor.clearShapePreview();
                }
                renderToolChoiceRow();
                status.setText("Normal Brush • live stroke");
            });
            attachTouchAnimation(normalSlot);
            LinearLayout.LayoutParams normalLp = new LinearLayout.LayoutParams(0, -1, 1f);
            normalLp.setMargins(dp(1), dp(1), dp(1), dp(1));
            backgroundColorRow.addView(normalSlot, normalLp);

            // The seven original brush shapes.
            for (int i = 0; i < BRUSH_SHAPE_NAMES.length; i++) {
                final int shapeIndex = i;
                FrameLayout slot = new FrameLayout(this);
                slot.setTag(shapeIndex);
                boolean selected = shapeIndex == brushShapeIndex;
                slot.setBackground(rounded(selected ? 0xFFD7EAE8 : 0xFFF8FBFA, 12));
                slot.setElevation(selected ? dp(3) : dp(1));

                ImageView icon = createBrushShapePngIcon(shapeIndex);
                FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(30), dp(30));
                iconLp.gravity = Gravity.CENTER;
                slot.addView(icon, iconLp);

                slot.setOnClickListener(v -> {
                    brushShapeIndex = shapeIndex;
                    dualBrushRotateActive = false;
                    if (brushCursor != null) brushCursor.clearTrail();
                    renderToolChoiceRow();
                    status.setText("Shape • " + BRUSH_SHAPE_NAMES[shapeIndex]
                            + " • 2-finger rotate");
                });
                attachTouchAnimation(slot);

                LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(0, -1, 1f);
                slotLp.setMargins(dp(1), dp(1), dp(1), dp(1));
                backgroundColorRow.addView(slot, slotLp);
            }
            return;
        }

        for (int i = 0; i < PHOTO_BG_COLORS.length; i++) {
            final int color = PHOTO_BG_COLORS[i];
            final String colorName = PHOTO_BG_NAMES[i];

            FrameLayout slot = new FrameLayout(this);
            slot.setTag(color);

            View circle = createBackgroundColorCircle(color, colorName);
            FrameLayout.LayoutParams circleLp = new FrameLayout.LayoutParams(dp(34), dp(34));
            circleLp.gravity = Gravity.CENTER;
            slot.addView(circle, circleLp);

            LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(0, -1, 1f);
            backgroundColorRow.addView(slot, slotLp);
        }
        updateBackgroundColorSelection();
    }

    private ImageView createBrushShapePngIcon(int index) {
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(2), dp(2), dp(2), dp(2));
        icon.setContentDescription(BRUSH_SHAPE_NAMES[index]);
        try {
            byte[] bytes = Base64.decode(BRUSH_SHAPE_PNG[index], Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            icon.setImageBitmap(bitmap);
        } catch (Throwable ignored) {
            icon.setImageResource(com.sanchit.passportfresh.R.drawable.ic_passport_logo);
        }
        return icon;
    }

    private View createBackgroundColorCircle(int color, String name) {
        TextView circle = new TextView(this);
        circle.setGravity(Gravity.CENTER);
        circle.setContentDescription("Background " + name);
        circle.setTag(color);
        circle.setClickable(true);
        circle.setFocusable(true);
        attachTouchAnimation(circle);
        circle.setOnClickListener(v -> {
            Object tag = v.getTag();
            if (!(tag instanceof Integer)) return;
            selectedBackgroundColor = (Integer)tag;
            updateBackgroundColorSelection();
            if (originalBitmap != null && personMask != null) {
                status.setText(name + " background");
                renderResult();
            } else {
                status.setText(name + " background selected");
            }
        });
        return circle;
    }

    private void updateBackgroundColorSelection() {
        if (backgroundColorRow == null) return;
        for (int i = 0; i < backgroundColorRow.getChildCount(); i++) {
            View slotView = backgroundColorRow.getChildAt(i);
            if (!(slotView instanceof FrameLayout)) continue;
            FrameLayout slot = (FrameLayout)slotView;
            Object tag = slot.getTag();
            if (!(tag instanceof Integer) || slot.getChildCount() == 0) continue;

            int color = (Integer)tag;
            boolean selected = color == selectedBackgroundColor;
            View circle = slot.getChildAt(0);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            int strokeColor = selected ? 0xFF111827 : 0xFFB7C0C8;
            bg.setStroke(dp(selected ? 4 : 1), strokeColor);
            circle.setBackground(bg);
            circle.setElevation(selected ? dp(3) : dp(1));
            circle.setScaleX(selected ? 1.08f : 1f);
            circle.setScaleY(selected ? 1.08f : 1f);
        }
    }

    private RippleDrawable rippleRounded(int color, int radiusDp, int rippleColor) {
        GradientDrawable content = rounded(color, radiusDp);
        GradientDrawable mask = rounded(Color.WHITE, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    private void attachTouchAnimation(View v) {
        v.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.90f).setDuration(90).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(130).start();
            }
            return false;
        });
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
        fairnessSeek.setEnabled(enabled);
        smoothSeek.setEnabled(enabled);
        brushSeek.setEnabled(enabled);
        compareButton.setEnabled(enabled);
        brushButton.setEnabled(enabled);
        objectButton.setEnabled(enabled);
        if (backgroundColorRow != null) {
            for (int i = 0; i < backgroundColorRow.getChildCount(); i++) {
                View slot = backgroundColorRow.getChildAt(i);
                slot.setEnabled(enabled);
                if (slot instanceof FrameLayout && ((FrameLayout)slot).getChildCount() > 0) {
                    ((FrameLayout)slot).getChildAt(0).setEnabled(enabled);
                }
            }
        }
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

    private String getAppVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0.20";
        }
    }

    private void showTopMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Share");
        popup.getMenu().add("Save");
        popup.getMenu().add("About");
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Share".equals(title)) {
                sharePhoto();
                return true;
            }
            if ("Save".equals(title)) {
                savePhoto();
                return true;
            }
            if ("About".equals(title)) {
                showAbout();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("STS Photo Background Remover")
                .setMessage("Created by Sanchit Kumar\nVersion " + getAppVersion())
                .setPositiveButton("OK", null)
                .show();
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
        final int fairness = fairnessSeek.getProgress();
        final int smooth = smoothSeek.getProgress();
        final int backgroundColor = selectedBackgroundColor;
        final Bitmap localErase = eraseMask == null ? null : eraseMask.copy(Bitmap.Config.ALPHA_8, false);
        final int token = ++renderToken;
        final boolean quietBrushRender = brushModeOn && (brushStrokeStarted || brushFinalizing);

        if (!quietBrushRender) setBusy(true, "Background तैयार हो रहा है…");

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

                float smoothAmount = Math.max(0f, Math.min(1f, smooth / 100f));
                float smoothStrength = smoothAmount * (0.78f + 0.52f * smoothAmount);
                int blurRadius = smooth == 0 ? 0 : Math.round(2f + smoothStrength * 20f);
                float[] smoothMask = blurMask(mask, mw, mh, blurRadius);
                if (smoothAmount > 0.10f) {
                    smoothMask = blurMask(smoothMask, mw, mh,
                            Math.max(1, Math.round(1f + smoothStrength * 9f)));
                }
                if (smoothAmount > 0.72f) {
                    smoothMask = blurMask(smoothMask, mw, mh,
                            Math.max(1, Math.round((smoothAmount - 0.70f) * 12f)));
                }

                // Hair-safe refinement:
                // use a small max/dilation support mask to close tiny holes inside hair,
                // but only inside the detected upper/head region.
                int hairRadius = 2 + Math.round(smoothStrength * 2f);
                float[] hairSupportMask = maxFilterMask(smoothMask, mw, mh, hairRadius);
                int[] personBounds = findMaskBounds(mask, mw, mh, 0.55f);

                // Normal body edge cleanup.
                float threshold = 0.50f + (0.115f * smoothStrength);
                float feather = 0.17f + (0.17f * smoothStrength);
                float low = threshold - feather * 0.5f;
                float high = threshold + feather * 0.5f;
                float brightnessAmount = Math.max(0f, Math.min(1f, brightness / 100f));
                float brighten = brightnessAmount * (0.52f + 0.23f * brightnessAmount);

                int br = Color.red(backgroundColor);
                int bg = Color.green(backgroundColor);
                int bb = Color.blue(backgroundColor);

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
                            float hairThreshold = 0.31f + 0.035f * smoothStrength;
                            float hairFeather = 0.30f + 0.055f * smoothStrength;
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
                                float softMix = Math.min(0.98f, 0.40f + 0.46f * smoothStrength);
                                a = a * (1f - softMix) + softened * softMix;
                                float edgeCut = Math.min(0.20f, 0.145f * smoothStrength);
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
                            float keep = 0.24f + 0.35f * (1f - Math.min(1f, smoothStrength));
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
                                float matchStrength = 0.24f + (0.34f + 0.24f * smoothStrength) * edgeBand;

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
                                        matchStrength = Math.max(matchStrength, Math.min(0.98f, 0.78f + 0.18f * smoothStrength));
                                        if (!hairCandidate) {
                                            // Stronger Smooth BG removes old matte/halo while hair stays protected.
                                            float trim = skinEdge
                                                    ? (0.80f - 0.20f * smoothStrength)
                                                    : (0.86f - 0.18f * smoothStrength);
                                            a *= Math.max(0.60f, trim);
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

                        // Fairness covers face + jaw + visible neck skin with a soft spatial fade.
                        // This removes the old hard horizontal cutoff below the chin.
                        float fairnessRegion = fairnessRegionWeight(mx, my, personBounds, mw, mh);
                        if (fairness > 0 && skinLike && fairnessRegion > 0.001f && a > 0.52f) {
                            float interior = smoothStep(0.52f, 0.96f, a);
                            float fairAmount = Math.max(0f, Math.min(1f, fairness / 100f));
                            float fair = fairAmount * (0.32f + 0.18f * fairAmount)
                                    * interior * fairnessRegion;
                            float y0 = Math.max(1f, 0.299f * r + 0.587f * g + 0.114f * b);
                            float targetY = y0 + (244f - y0) * fair;
                            float scale = Math.min(1.45f, targetY / y0);
                            r = clamp255(Math.round(r * scale));
                            g = clamp255(Math.round(g * scale));
                            b = clamp255(Math.round(b * scale));
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
                    if (brushFinalizing) {
                        brushFinalizing = false;
                        if (brushCursor != null) {
                            brushCursor.clearTrail();
                            brushCursor.setVisibility(View.GONE);
                        }
                        if (status != null) status.setText("Brush stroke applied");
                    } else if (!quietBrushRender) {
                        setBusy(false, "Ready • Background applied");
                    }
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

    private float fairnessRegionWeight(int mx, int my, int[] bounds, int mw, int mh) {
        if (bounds == null || bounds.length < 4) return 1f;

        float left = bounds[0];
        float top = bounds[1];
        float right = bounds[2];
        float bottom = bounds[3];
        float bw = Math.max(1f, right - left);
        float bh = Math.max(1f, bottom - top);
        float relY = (my - top) / bh;

        // Full face and jaw coverage, then a gradual neck fade.
        float vertical;
        if (relY <= 0.54f) vertical = 1f;
        else if (relY >= 0.82f) vertical = 0f;
        else vertical = 1f - smoothStep(0.54f, 0.82f, relY);

        // Below the jaw, prefer the central neck area so exposed arms/hands are not brightened.
        float horizontal = 1f;
        if (relY > 0.48f) {
            float cx = (left + right) * 0.5f;
            float dx = Math.abs(mx - cx) / Math.max(1f, bw * 0.5f);
            horizontal = 1f - smoothStep(0.56f, 0.92f, dx);
        }

        return Math.max(0f, Math.min(1f, vertical * horizontal));
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
            brushShapeIndex = -1;
            brushRotationDegrees = 0f;
            dualBrushRotateActive = false;
            objectButton.setText("LOCAL COLOR CLEAN");
            brushButton.setText("BRUSH ✓");
            if (toolSeekLabel != null) toolSeekLabel.setText("Brush Size");
            brushSeek.setProgress(brushSizeValue);
            status.setText("Brush ON • Normal live stroke • shape चुनने पर old shape mode");
        } else {
            brushButton.setText("BRUSH");
            if (brushCursor != null) {
                brushCursor.clearTrail();
                brushCursor.clearShapePreview();
                brushCursor.setVisibility(View.GONE);
            }
            if (toolSeekLabel != null) toolSeekLabel.setText("Color Tolerance");
            brushSeek.setProgress(colorToleranceValue);
            status.setText("Brush OFF");
        }
        renderToolChoiceRow();
        hideLens();
    }

    private void toggleColorClean() {
        if (resultBitmap == null) return;
        colorCleanOn = !colorCleanOn;
        if (colorCleanOn) {
            brushModeOn = false;
            brushShapeIndex = -1;
            brushRotationDegrees = 0f;
            dualBrushRotateActive = false;
            brushButton.setText("BRUSH");
            if (brushCursor != null) brushCursor.setVisibility(View.GONE);
            if (toolSeekLabel != null) toolSeekLabel.setText("Color Tolerance");
            brushSeek.setProgress(colorToleranceValue);
        }
        if (compareOriginal && colorCleanOn) {
            compareOriginal = false;
            imageView.setImageBitmap(resultBitmap);
            compareButton.setText("COMPARE");
        }
        objectButton.setText(colorCleanOn ? "LOCAL COLOR CLEAN ✓" : "LOCAL COLOR CLEAN");
        renderToolChoiceRow();
        status.setText(colorCleanOn
                ? "Finger रखें • lens में exact colour देखकर clean करें"
                : "Local Color Clean OFF");
        hideLens();
    }

    private boolean handlePhotoTouch(View v, MotionEvent e) {
        boolean shapeBrush = brushModeOn && brushShapeIndex >= 0;

        // Normal brush keeps pinch zoom. Old shape mode owns two fingers for rotation.
        if (!shapeBrush && scaleGestureDetector != null) {
            scaleGestureDetector.onTouchEvent(e);
        }

        int action = e.getActionMasked();

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (shapeBrush && !compareOriginal && resultBitmap != null
                    && e.getPointerCount() >= 2) {
                dualBrushRotateActive = true;
                dualBrushRotateStartAngle = twoFingerAngle(e);
                dualBrushRotateStartDegrees = brushRotationDegrees;
                gestureWasScaling = true;
                brushStrokeStarted = false;
                brushStrokeChanged = false;
                if (brushCursor != null) brushCursor.clearTrail();
                showLens(lastBrushViewX, lastBrushViewY);
                status.setText("2-finger rotate • " + Math.round(brushRotationDegrees) + "°");
                return true;
            }

            // Normal live brush: finish current stroke before pinch zoom.
            gestureWasScaling = true;
            if (brushStrokeStarted && brushStrokeChanged) {
                brushStrokeStarted = false;
                brushFinalizing = true;
                if (brushCursor != null) brushCursor.finishStroke();
                renderResult();
            } else {
                brushStrokeStarted = false;
                if (brushCursor != null) brushCursor.clearTrail();
            }
            hideLens();
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_UP) {
            if (shapeBrush && dualBrushRotateActive) {
                dualBrushRotateActive = false;
                showLens(lastBrushViewX, lastBrushViewY);
                status.setText("Rotation set • " + Math.round(brushRotationDegrees) + "°");
            } else {
                gestureWasScaling = true;
                hideLens();
            }
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN) {
            lastPanX = e.getX();
            lastPanY = e.getY();
            lastBrushViewX = e.getX();
            lastBrushViewY = e.getY();
            panMoved = false;
            gestureWasScaling = false;
            brushStrokeStarted = false;
            brushStrokeChanged = false;

            if (brushModeOn && !compareOriginal && resultBitmap != null) {
                if (shapeBrush) {
                    if (brushCursor != null) brushCursor.clearTrail();
                    showLens(e.getX(), e.getY());
                    status.setText(BRUSH_SHAPE_NAMES[brushShapeIndex]
                            + " • position करें • release पर erase");
                    return true;
                }

                pushUndo();
                clearDeque(redoMasks);
                brushStrokeStarted = true;
                brushStrokeChanged = false;
                if (brushCursor != null) {
                    brushCursor.startStroke(e.getX(), e.getY(),
                            currentBrushScreenRadius() * 2f, selectedBackgroundColor);
                }
                showLens(e.getX(), e.getY());
                status.setText("Brush • live erase");
                return true;
            }

            if (colorCleanOn && !compareOriginal && resultBitmap != null) {
                showLens(e.getX(), e.getY());
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (shapeBrush && dualBrushRotateActive && e.getPointerCount() >= 2
                    && !compareOriginal && resultBitmap != null) {
                float nowAngle = twoFingerAngle(e);
                brushRotationDegrees = normalizeDegrees(
                        dualBrushRotateStartDegrees
                                + angleDeltaDegrees(dualBrushRotateStartAngle, nowAngle));
                gestureWasScaling = true;
                showLens(lastBrushViewX, lastBrushViewY);
                status.setText("2-finger rotate • " + Math.round(brushRotationDegrees) + "°");
                return true;
            }

            if (shapeBrush && e.getPointerCount() == 1
                    && !compareOriginal && resultBitmap != null) {
                // Old shape behavior: one finger only positions the selected shape.
                lastBrushViewX = e.getX();
                lastBrushViewY = e.getY();
                showLens(lastBrushViewX, lastBrushViewY);
                return true;
            }

            if (e.getPointerCount() > 1
                    || (!shapeBrush && scaleGestureDetector != null
                    && scaleGestureDetector.isInProgress())) {
                gestureWasScaling = true;
                hideLens();
                return true;
            }

            if (brushModeOn && brushStrokeStarted && !compareOriginal && resultBitmap != null) {
                float x = e.getX();
                float y = e.getY();
                float move = (float)Math.hypot(x - lastBrushViewX, y - lastBrushViewY);

                if (move >= dp(1)) {
                    boolean changed = applyBrushStrokeSegment(
                            lastBrushViewX, lastBrushViewY, x, y);
                    if (changed) {
                        brushStrokeChanged = true;
                        if (brushCursor != null) {
                            brushCursor.addStroke(x, y,
                                    currentBrushScreenRadius() * 2f, selectedBackgroundColor);
                        }
                    }
                    lastBrushViewX = x;
                    lastBrushViewY = y;
                }

                showLens(x, y);
                return true;
            }

            if (colorCleanOn && !compareOriginal && resultBitmap != null) {
                showLens(e.getX(), e.getY());
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
            if (shapeBrush && !compareOriginal && resultBitmap != null) {
                lastBrushViewX = e.getX();
                lastBrushViewY = e.getY();

                if (!gestureWasScaling) {
                    showLens(lastBrushViewX, lastBrushViewY);
                    pushUndo();
                    clearDeque(redoMasks);
                    boolean changed = applyBrushShapeStampAt(
                            lastBrushViewX, lastBrushViewY);

                    if (changed) {
                        updateHistoryButtons();
                        status.setText(BRUSH_SHAPE_NAMES[brushShapeIndex]
                                + " • " + Math.round(brushRotationDegrees) + "° • applied");
                        renderResult();
                    } else if (!undoMasks.isEmpty()) {
                        undoMasks.pop();
                        updateHistoryButtons();
                    }
                }

                dualBrushRotateActive = false;
                gestureWasScaling = false;
                hideLens();
                if (brushCursor != null) {
                    brushCursor.clearShapePreview();
                    brushCursor.setVisibility(View.GONE);
                }
                return true;
            }

            if (brushModeOn && brushStrokeStarted && !compareOriginal && resultBitmap != null) {
                float x = e.getX();
                float y = e.getY();
                float move = (float)Math.hypot(x - lastBrushViewX, y - lastBrushViewY);
                if (move >= dp(1)) {
                    boolean changed = applyBrushStrokeSegment(
                            lastBrushViewX, lastBrushViewY, x, y);
                    if (changed) {
                        brushStrokeChanged = true;
                        if (brushCursor != null) {
                            brushCursor.addStroke(x, y,
                                    currentBrushScreenRadius() * 2f, selectedBackgroundColor);
                        }
                    }
                }

                brushStrokeStarted = false;
                hideLens();

                if (brushStrokeChanged) {
                    updateHistoryButtons();
                    brushFinalizing = true;
                    if (brushCursor != null) brushCursor.finishStroke();
                    status.setText("Brush • finalizing");
                    renderResult();
                } else if (!undoMasks.isEmpty()) {
                    undoMasks.pop();
                    updateHistoryButtons();
                    if (brushCursor != null) {
                        brushCursor.clearTrail();
                        brushCursor.setVisibility(View.GONE);
                    }
                    status.setText("Brush • stroke चलाएँ");
                }

                brushStrokeChanged = false;
                gestureWasScaling = false;
                return true;
            }

            if (colorCleanOn && !compareOriginal && resultBitmap != null
                    && !panMoved && !gestureWasScaling
                    && (scaleGestureDetector == null || !scaleGestureDetector.isInProgress())) {
                showLens(e.getX(), e.getY());
                applyLocalColorCleanAt(e.getX(), e.getY());
                hideLens();
            } else {
                hideLens();
            }

            gestureWasScaling = false;
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            dualBrushRotateActive = false;
            if (brushStrokeStarted && brushStrokeChanged) {
                brushStrokeStarted = false;
                brushFinalizing = true;
                if (brushCursor != null) brushCursor.finishStroke();
                renderResult();
            } else {
                brushStrokeStarted = false;
                if (brushCursor != null) {
                    brushCursor.clearTrail();
                    brushCursor.clearShapePreview();
                    brushCursor.setVisibility(View.GONE);
                }
            }
            brushStrokeChanged = false;
            gestureWasScaling = false;
            hideLens();
            return true;
        }

        return true;
    }

    private float twoFingerAngle(MotionEvent e) {
        if (e == null || e.getPointerCount() < 2) return 0f;
        float dx = e.getX(1) - e.getX(0);
        float dy = e.getY(1) - e.getY(0);
        return (float)Math.toDegrees(Math.atan2(dy, dx));
    }

    private float angleDeltaDegrees(float from, float to) {
        float delta = to - from;
        while (delta > 180f) delta -= 360f;
        while (delta < -180f) delta += 360f;
        return delta;
    }

    private float normalizeDegrees(float degrees) {
        while (degrees >= 180f) degrees -= 360f;
        while (degrees < -180f) degrees += 360f;
        return degrees;
    }

    private float currentBrushScreenRadius() {
        float density = getResources().getDisplayMetrics().density;
        float t = Math.max(0f, Math.min(1f, brushSizeValue / 100f));
        // Precise release-point brush radius for hair/ear edge cleanup.
        return density * (2.5f + 15.5f * t);
    }

    private float currentImageScale() {
        float[] values = new float[9];
        photoMatrix.getValues(values);
        float sx = values[Matrix.MSCALE_X];
        float sy = values[Matrix.MSKEW_Y];
        return Math.max(0.0001f, (float)Math.sqrt(sx * sx + sy * sy));
    }

    private void showLens(float viewX, float viewY) {
        if (magnifierLens == null || imageView == null) return;
        Bitmap shown = compareOriginal ? originalBitmap : (resultBitmap != null ? resultBitmap : originalBitmap);
        if (shown == null) return;

        Matrix inv = new Matrix();
        if (!photoMatrix.invert(inv)) return;
        float[] pt = new float[]{viewX, viewY};
        inv.mapPoints(pt);

        float magnification = 3.4f;
        float lensRadiusPx = dp(66);
        float sourceRadius = lensRadiusPx / Math.max(0.0001f, currentImageScale() * magnification);
        float brushWidthInLens = brushModeOn
                ? Math.min(dp(52), currentBrushScreenRadius() * magnification)
                : 0f;

        magnifierLens.setLens(shown, pt[0], pt[1], sourceRadius,
                brushModeOn ? LensView.MODE_BRUSH : LensView.MODE_COLOR_CLEAN,
                brushWidthInLens, brushShapeIndex, brushRotationDegrees);

        if (brushModeOn && brushCursor != null) {
            if (brushShapeIndex >= 0) {
                brushCursor.setShapePreview(viewX, viewY,
                        currentBrushScreenRadius() * 2f,
                        brushShapeIndex, brushRotationDegrees);
            } else {
                brushCursor.setBrush(viewX, viewY, currentBrushScreenRadius() * 2f);
            }
            brushCursor.setVisibility(View.VISIBLE);
            brushCursor.bringToFront();
        } else if (brushCursor != null) {
            brushCursor.setVisibility(View.GONE);
        }

        int lensSize = dp(132);
        int margin = dp(8);
        int gap = dp(28);
        int left = Math.round(viewX - lensSize * 0.5f);
        int top = Math.round(viewY - lensSize - gap);
        if (top < margin) top = Math.round(viewY + gap);

        left = Math.max(margin, Math.min(imageView.getWidth() - lensSize - margin, left));
        top = Math.max(margin, Math.min(imageView.getHeight() - lensSize - margin, top));

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)magnifierLens.getLayoutParams();
        lp.width = lensSize;
        lp.height = lensSize;
        lp.leftMargin = left;
        lp.topMargin = top;
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        magnifierLens.setLayoutParams(lp);
        magnifierLens.setVisibility(View.VISIBLE);
        magnifierLens.bringToFront();
        if (status != null) status.bringToFront();
    }

    private void hideLens() {
        if (magnifierLens != null) magnifierLens.setVisibility(View.GONE);
    }

    private boolean applyBrushStrokeSegment(float viewX1, float viewY1,
                                            float viewX2, float viewY2) {
        try {
            if (eraseMask == null) createEmptyEraseMask();
            if (eraseMask == null) return false;

            Matrix inv = new Matrix();
            if (!photoMatrix.invert(inv)) return false;

            float[] pts = new float[]{viewX1, viewY1, viewX2, viewY2};
            inv.mapPoints(pts);

            float halfWidth = Math.max(1.5f,
                    currentBrushScreenRadius() / currentImageScale());
            return paintSoftBrushSegment(pts[0], pts[1], pts[2], pts[3], halfWidth);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean paintSoftBrushSegment(float x1, float y1, float x2, float y2,
                                          float halfWidth) {
        int w = eraseMask.getWidth();
        int h = eraseMask.getHeight();

        float vx = x2 - x1;
        float vy = y2 - y1;
        float lenSq = vx * vx + vy * vy;
        if (lenSq < 0.25f) return false;

        float margin = halfWidth + 2f;
        int left = Math.max(0, (int)Math.floor(Math.min(x1, x2) - margin));
        int top = Math.max(0, (int)Math.floor(Math.min(y1, y2) - margin));
        int right = Math.min(w - 1, (int)Math.ceil(Math.max(x1, x2) + margin));
        int bottom = Math.min(h - 1, (int)Math.ceil(Math.max(y1, y2) + margin));
        if (right < left || bottom < top) return false;

        int rw = right - left + 1;
        int rh = bottom - top + 1;
        int[] px = new int[rw * rh];
        eraseMask.getPixels(px, 0, rw, left, top, rw, rh);

        // Real brush strip: full-strength center band with soft side feather.
        // Finite projection t=0..1 gives line/brush behavior instead of circular stamps.
        float inner = halfWidth * 0.48f;
        float feather = Math.max(0.75f, halfWidth - inner);
        boolean changed = false;

        for (int yy = 0; yy < rh; yy++) {
            float py = top + yy + 0.5f;
            for (int xx = 0; xx < rw; xx++) {
                float pxX = left + xx + 0.5f;

                float wx = pxX - x1;
                float wy = py - y1;
                float t = (wx * vx + wy * vy) / lenSq;

                // Square/brush-like ends: nothing outside the actual moved segment.
                if (t < 0f || t > 1f) continue;

                float cx = x1 + t * vx;
                float cy = y1 + t * vy;
                float side = (float)Math.hypot(pxX - cx, py - cy);
                if (side > halfWidth) continue;

                float strength;
                if (side <= inner) {
                    strength = 1f;
                } else {
                    float u = 1f - (side - inner) / feather;
                    u = Math.max(0f, Math.min(1f, u));
                    strength = u * u * (3f - 2f * u);
                }

                int index = yy * rw + xx;
                int oldA = Color.alpha(px[index]);
                int newA = Math.max(oldA, Math.round(255f * strength));
                if (newA != oldA) {
                    px[index] = Color.argb(newA, 255, 255, 255);
                    changed = true;
                }
            }
        }

        if (changed) eraseMask.setPixels(px, 0, rw, left, top, rw, rh);
        return changed;
    }

    private boolean applyBrushShapeStampAt(float viewX, float viewY) {
        try {
            if (eraseMask == null) createEmptyEraseMask();
            if (eraseMask == null || brushShapeIndex < 0) return false;

            Matrix inv = new Matrix();
            if (!photoMatrix.invert(inv)) return false;
            float[] pt = new float[]{viewX, viewY};
            inv.mapPoints(pt);

            float radius = Math.max(2f, currentBrushScreenRadius() / currentImageScale());
            return paintEraseShape(pt[0], pt[1], radius,
                    brushShapeIndex, brushRotationDegrees);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean paintEraseShape(float cx, float cy, float radius, int shape,
                                    float rotationDegrees) {
        int w = eraseMask.getWidth();
        int h = eraseMask.getHeight();

        float boundsRadius = shape == 6 ? radius * 0.36f : radius;
        int left = Math.max(0, (int)Math.floor(cx - boundsRadius - 3));
        int top = Math.max(0, (int)Math.floor(cy - boundsRadius - 3));
        int right = Math.min(w - 1, (int)Math.ceil(cx + boundsRadius + 3));
        int bottom = Math.min(h - 1, (int)Math.ceil(cy + boundsRadius + 3));
        if (right < left || bottom < top) return false;

        int rw = right - left + 1;
        int rh = bottom - top + 1;
        int[] pixels = new int[rw * rh];
        eraseMask.getPixels(pixels, 0, rw, left, top, rw, rh);

        boolean changed = false;
        for (int yy = 0; yy < rh; yy++) {
            float py = top + yy + 0.5f;
            for (int xx = 0; xx < rw; xx++) {
                float px = left + xx + 0.5f;
                float dx = px - cx;
                float dy = py - cy;
                float strength = brushShapeStrength(dx, dy, radius, shape, rotationDegrees);
                if (strength <= 0f) continue;

                int index = yy * rw + xx;
                int oldA = Color.alpha(pixels[index]);
                int newA = Math.max(oldA, Math.round(255f * strength));
                if (newA != oldA) changed = true;
                pixels[index] = Color.argb(newA, 255, 255, 255);
            }
        }
        if (changed) eraseMask.setPixels(pixels, 0, rw, left, top, rw, rh);
        return changed;
    }

    private float brushShapeStrength(float dx, float dy, float radius, int shape,
                                     float rotationDegrees) {
        if (radius <= 0f) return 0f;

        double radians = Math.toRadians(-rotationDegrees);
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        float localX = dx * cos - dy * sin;
        float localY = dx * sin + dy * cos;
        dx = localX;
        dy = localY;

        float ax = Math.abs(dx);
        float ay = Math.abs(dy);

        if (shape == 3) {
            float nx = ax / Math.max(1f, radius * 0.30f);
            float ny = ay / radius;
            return softShapeFalloff(Math.max(nx, ny), 0.50f);
        }

        if (shape == 4) {
            float nx = ax / radius;
            float ny = ay / Math.max(1f, radius * 0.30f);
            return softShapeFalloff(Math.max(nx, ny), 0.50f);
        }

        if (shape == 5) {
            float nx = (dx + radius) / Math.max(1f, radius * 2f);
            float ny = (dy + radius) / Math.max(1f, radius * 2f);
            float edgeRoom = Math.min(Math.min(nx, ny), 1f - nx - ny);
            if (edgeRoom <= -0.10f) return 0f;
            if (edgeRoom >= 0.14f) return 1f;
            return smoothStep(-0.10f, 0.14f, edgeRoom);
        }

        float useRadius = shape == 6 ? radius * 0.30f : radius;
        float norm = (float)Math.sqrt((dx * dx + dy * dy)
                / Math.max(1f, useRadius * useRadius));

        if (shape == 2) {
            float radial = softShapeFalloff(norm, 0.50f);
            float straight = smoothStep(-radius * 0.18f, radius * 0.18f, dx);
            return radial * straight;
        }

        if (shape == 1) return softShapeFalloff(norm, 0.62f);
        if (shape == 0) return softShapeFalloff(norm, 0.30f);
        return softShapeFalloff(norm, 0.42f);
    }

    private float softShapeFalloff(float normalizedDistance, float fullStrengthUntil) {
        if (normalizedDistance >= 1f) return 0f;
        if (normalizedDistance <= fullStrengthUntil) return 1f;
        float u = 1f - (normalizedDistance - fullStrengthUntil)
                / Math.max(0.001f, 1f - fullStrengthUntil);
        u = Math.max(0f, Math.min(1f, u));
        return u * u * (3f - 2f * u);
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

            int br = Color.red(selectedBackgroundColor);
            int bg = Color.green(selectedBackgroundColor);
            int bb = Color.blue(selectedBackgroundColor);
            if (colorDistanceSq(tr, tg, tb, br, bg, bb) < 900f) {
                status.setText("यह पहले से background है • बचा हुआ colour select करें");
                return;
            }

            pushUndo();
            clearDeque(redoMasks);

            float toleranceProgress = Math.max(0f, Math.min(1f, brushSeek.getProgress() / 100f));
            int localRadius = Math.min(220, Math.max(30,
                    Math.round(Math.min(w, h) * (0.040f + 0.090f * toleranceProgress))));
            int minX = Math.max(0, sx - localRadius);
            int maxX = Math.min(w - 1, sx + localRadius);
            int minY = Math.max(0, sy - localRadius);
            int maxY = Math.min(h - 1, sy + localRadius);
            int boxW = maxX - minX + 1;
            int boxH = maxY - minY + 1;

            boolean[] visited = new boolean[boxW * boxH];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add((sy - minY) * boxW + (sx - minX));

            float tolerance = 4f + 76f * toleranceProgress;
            float toleranceSq = tolerance * tolerance;
            float targetLum = 0.299f * tr + 0.587f * tg + 0.114f * tb;
            boolean targetSkin = isSkinLikeColor(tr, tg, tb);
            int[] resultPixels = new int[w * h];
            int[] maskPixels = new int[w * h];
            resultBitmap.getPixels(resultPixels, 0, w, 0, 0, w, h);
            eraseMask.getPixels(maskPixels, 0, w, 0, 0, w, h);

            int changed = 0;
            final int maxChanged = 90000;

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
        final int saveFairness = fairnessSeek.getProgress();
        final int saveSmooth = smoothSeek.getProgress();
        final int saveBackgroundColor = selectedBackgroundColor;

        setBusy(true, "Original size में फोटो सेव हो रही है…");

        worker.execute(() -> {
            Uri uri = null;
            Bitmap full = null;
            try {
                if (saveSourceUri == null) throw new IllegalStateException("source uri missing");

                full = decodeOriginalFull(saveSourceUri);
                if (full == null) throw new IllegalStateException("full decode failed");

                renderFullResolutionForSave(full, previewSource, saveMask, saveMaskW, saveMaskH,
                        saveErase, saveBrightness, saveFairness, saveSmooth, saveBackgroundColor);

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
            int brightness, int fairness, int smooth, int backgroundColor) {

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

        float smoothAmount = Math.max(0f, Math.min(1f, smooth / 100f));
                float smoothStrength = smoothAmount * (0.78f + 0.52f * smoothAmount);
        int blurRadius = smooth == 0 ? 0 : Math.round(2f + smoothStrength * 20f);
        float[] smoothMask = blurMask(mask, mw, mh, blurRadius);
        if (smoothAmount > 0.12f) {
            smoothMask = blurMask(smoothMask, mw, mh,
                    Math.max(1, Math.round(1f + smoothAmount * 8f)));
        }
        int hairRadius = 2 + Math.round(smoothStrength * 2f);
        float[] hairSupportMask = maxFilterMask(smoothMask, mw, mh, hairRadius);
        int[] personBounds = findMaskBounds(mask, mw, mh, 0.55f);

        float threshold = 0.50f + (0.10f * smoothAmount);
        float feather = 0.17f + (0.15f * smoothAmount);
        float low = threshold - feather * 0.5f;
        float high = threshold + feather * 0.5f;
        float brightnessAmount = Math.max(0f, Math.min(1f, brightness / 100f));
                float brighten = brightnessAmount * (0.52f + 0.23f * brightnessAmount);

        int br = Color.red(backgroundColor);
        int bg = Color.green(backgroundColor);
        int bb = Color.blue(backgroundColor);

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
                    float hairThreshold = 0.31f + 0.035f * smoothStrength;
                    float hairFeather = 0.30f + 0.055f * smoothStrength;
                    a = smoothStep(hairThreshold - hairFeather * 0.5f,
                            hairThreshold + hairFeather * 0.5f, confidence);
                    if (hairSupport > 0.62f && a < 0.58f) a = 0.58f;
                } else {
                    a = smoothStep(low, high, confidence);
                    if (smoothAmount > 0f && a > 0f && a < 1f) {
                        float softened = smoothStep(0f, 1f, a);
                        float softMix = Math.min(0.98f, 0.40f + 0.46f * smoothStrength);
                        a = a * (1f - softMix) + softened * softMix;
                        float edgeCut = Math.min(0.20f, 0.145f * smoothStrength);
                        a = Math.max(0f, Math.min(1f,
                                (a - edgeCut) / Math.max(0.01f, 1f - edgeCut)));
                    }
                }

                boolean skinEdge = skinLike && headZone && a > 0.03f && a < 0.985f;
                if (skinEdge) {
                    float skinA = smoothStep(0.44f, 0.62f, confidence);
                    float keep = 0.24f + 0.35f * (1f - Math.min(1f, smoothStrength));
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
                        float matchStrength = 0.24f + (0.34f + 0.24f * smoothStrength) * edgeBand;

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
                                matchStrength = Math.max(matchStrength, Math.min(0.98f, 0.78f + 0.18f * smoothStrength));
                                if (!hairCandidate) {
                                    float trim = skinEdge
                                            ? (0.80f - 0.20f * smoothStrength)
                                            : (0.86f - 0.18f * smoothStrength);
                                    a *= Math.max(0.60f, trim);
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

                if (manual != null) {
                    int ex = Math.min(manualW - 1, Math.max(0,
                            Math.round(x * (manualW - 1f) / Math.max(1f, w - 1f))));
                    int ey = Math.min(manualH - 1, Math.max(0,
                            Math.round(y * (manualH - 1f) / Math.max(1f, h - 1f))));
                    float eraseA = Color.alpha(manual[ey * manualW + ex]) / 255f;
                    a *= (1f - eraseA);
                }

                float fairnessRegion = fairnessRegionWeight(mx, my, personBounds, mw, mh);
                if (fairness > 0 && skinLike && fairnessRegion > 0.001f && a > 0.52f) {
                    float interior = smoothStep(0.52f, 0.96f, a);
                    float fairAmount = Math.max(0f, Math.min(1f, fairness / 100f));
                    float fair = fairAmount * (0.32f + 0.18f * fairAmount)
                            * interior * fairnessRegion;
                    float y0 = Math.max(1f, 0.299f * r + 0.587f * g + 0.114f * b);
                    float targetY = y0 + (244f - y0) * fair;
                    float scale = Math.min(1.45f, targetY / y0);
                    r = clamp255(Math.round(r * scale));
                    g = clamp255(Math.round(g * scale));
                    b = clamp255(Math.round(b * scale));
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

    private static class BrushCursorView extends View {
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint metalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bristlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trailFeatherPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path bristlePath = new Path();
        private final Path trailPath = new Path();
        private boolean trailStarted = false;
        private boolean showBrushTip = true;
        private boolean shapePreview = false;
        private int previewShape = -1;
        private float previewRotation = 0f;
        private float tipX;
        private float tipY;
        private float brushWidth = 16f;

        BrushCursorView(android.content.Context context) {
            super(context);
            setClickable(false);
            setFocusable(false);
            setWillNotDraw(false);
            handlePaint.setStyle(Paint.Style.STROKE);
            handlePaint.setStrokeCap(Paint.Cap.ROUND);
            metalPaint.setStyle(Paint.Style.STROKE);
            metalPaint.setStrokeCap(Paint.Cap.SQUARE);
            bristlePaint.setStyle(Paint.Style.FILL);
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(1.8f);
            outlinePaint.setColor(0xEE111111);

            trailPaint.setStyle(Paint.Style.STROKE);
            trailPaint.setStrokeCap(Paint.Cap.BUTT);
            trailPaint.setStrokeJoin(Paint.Join.ROUND);
            trailFeatherPaint.setStyle(Paint.Style.STROKE);
            trailFeatherPaint.setStrokeCap(Paint.Cap.BUTT);
            trailFeatherPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        void startStroke(float x, float y, float width, int backgroundColor) {
            shapePreview = false;
            previewShape = -1;
            trailPath.reset();
            trailPath.moveTo(x, y);
            trailStarted = true;
            showBrushTip = true;
            tipX = x;
            tipY = y;
            brushWidth = Math.max(8f, Math.min(52f, width));
            setTrailStyle(backgroundColor);
            invalidate();
        }

        void addStroke(float x, float y, float width, int backgroundColor) {
            if (!trailStarted) startStroke(x, y, width, backgroundColor);
            brushWidth = Math.max(8f, Math.min(52f, width));
            setTrailStyle(backgroundColor);
            trailPath.lineTo(x, y);
            tipX = x;
            tipY = y;
            invalidate();
        }

        void finishStroke() {
            showBrushTip = false;
            invalidate();
        }

        void clearTrail() {
            trailPath.reset();
            trailStarted = false;
            showBrushTip = true;
            invalidate();
        }

        void setShapePreview(float x, float y, float width, int shape, float rotation) {
            trailPath.reset();
            trailStarted = false;
            shapePreview = true;
            previewShape = Math.max(0, Math.min(6, shape));
            previewRotation = rotation;
            tipX = x;
            tipY = y;
            brushWidth = Math.max(10f, Math.min(72f, width));
            showBrushTip = false;
            invalidate();
        }

        void clearShapePreview() {
            shapePreview = false;
            previewShape = -1;
            invalidate();
        }

        private void setTrailStyle(int backgroundColor) {
            trailPaint.setColor(backgroundColor);
            trailPaint.setStrokeWidth(Math.max(3f, brushWidth * 0.72f));
            trailFeatherPaint.setColor((backgroundColor & 0x00FFFFFF) | 0x66000000);
            trailFeatherPaint.setStrokeWidth(Math.max(4f, brushWidth));
        }

        void setBrush(float x, float y, float width) {
            shapePreview = false;
            previewShape = -1;
            tipX = x;
            tipY = y;
            brushWidth = Math.max(8f, Math.min(52f, width));
            showBrushTip = true;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (shapePreview && previewShape >= 0) {
                float radius = Math.max(8f, brushWidth * 0.5f);
                canvas.save();
                canvas.rotate(previewRotation, tipX, tipY);

                Paint fill = bristlePaint;
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(0x225B7FA3);
                drawShapeOutline(canvas, tipX, tipY, radius, previewShape, fill);

                outlinePaint.setStyle(Paint.Style.STROKE);
                outlinePaint.setStrokeWidth(2.3f);
                outlinePaint.setColor(0xEE111111);
                drawShapeOutline(canvas, tipX, tipY, radius, previewShape, outlinePaint);
                canvas.restore();
                return;
            }

            if (trailStarted) {
                canvas.drawPath(trailPath, trailFeatherPaint);
                canvas.drawPath(trailPath, trailPaint);
            }
            if (!showBrushTip) return;

            float contactW = brushWidth;
            float w = Math.max(22f, contactW * 1.55f);
            float angle = -48f;
            double rad = Math.toRadians(angle);
            float ux = (float)Math.cos(rad);
            float uy = (float)Math.sin(rad);
            float nx = -uy;
            float ny = ux;

            // Bristle tip is the exact erase point. Brush extends away from the finger.
            float bristleLen = Math.max(16f, w * 1.15f);
            float ferruleLen = Math.max(13f, w * 0.82f);
            float handleLen = Math.max(34f, w * 2.0f);

            float b0x = tipX;
            float b0y = tipY;
            float b1x = tipX - ux * bristleLen;
            float b1y = tipY - uy * bristleLen;

            float halfTip = Math.max(2.5f, w * 0.22f);
            float halfBase = Math.max(4f, w * 0.42f);

            bristlePath.reset();
            bristlePath.moveTo(b0x + nx * halfTip, b0y + ny * halfTip);
            bristlePath.lineTo(b0x - nx * halfTip, b0y - ny * halfTip);
            bristlePath.lineTo(b1x - nx * halfBase, b1y - ny * halfBase);
            bristlePath.lineTo(b1x + nx * halfBase, b1y + ny * halfBase);
            bristlePath.close();

            bristlePaint.setColor(0xCC6D4C35);
            canvas.drawPath(bristlePath, bristlePaint);
            canvas.drawPath(bristlePath, outlinePaint);

            float f1x = b1x - ux * ferruleLen;
            float f1y = b1y - uy * ferruleLen;
            metalPaint.setStrokeWidth(Math.max(6f, w * 0.62f));
            metalPaint.setColor(0xFFD5D9DE);
            canvas.drawLine(b1x, b1y, f1x, f1y, metalPaint);
            outlinePaint.setStrokeWidth(1.5f);
            canvas.drawLine(b1x + nx * w * 0.34f, b1y + ny * w * 0.34f,
                    f1x + nx * w * 0.34f, f1y + ny * w * 0.34f, outlinePaint);
            canvas.drawLine(b1x - nx * w * 0.34f, b1y - ny * w * 0.34f,
                    f1x - nx * w * 0.34f, f1y - ny * w * 0.34f, outlinePaint);

            float h1x = f1x - ux * handleLen;
            float h1y = f1y - uy * handleLen;
            handlePaint.setStrokeWidth(Math.max(7f, w * 0.56f));
            handlePaint.setColor(0xFF7C4F2D);
            canvas.drawLine(f1x, f1y, h1x, h1y, handlePaint);

            // Tiny contact mark shows actual stroke width without a round cursor.
            outlinePaint.setStrokeWidth(1.8f);
            outlinePaint.setColor(0xEE111111);
            canvas.drawLine(tipX - nx * contactW * 0.45f, tipY - ny * contactW * 0.45f,
                    tipX + nx * contactW * 0.45f, tipY + ny * contactW * 0.45f, outlinePaint);
        }

        private void drawShapeOutline(Canvas canvas, float cx, float cy,
                                      float radius, int shape, Paint paint) {
            if (radius <= 0f) return;
            if (shape == 0 || shape == 1) {
                canvas.drawCircle(cx, cy, radius, paint);
                return;
            }
            if (shape == 2) {
                RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
                Path p = new Path();
                p.moveTo(cx, cy - radius);
                p.arcTo(oval, -90f, 180f);
                p.lineTo(cx, cy - radius);
                p.close();
                canvas.drawPath(p, paint);
                return;
            }
            if (shape == 3) {
                RectF rect = new RectF(cx - radius * 0.30f, cy - radius,
                        cx + radius * 0.30f, cy + radius);
                canvas.drawRoundRect(rect, radius * 0.12f, radius * 0.12f, paint);
                return;
            }
            if (shape == 4) {
                RectF rect = new RectF(cx - radius, cy - radius * 0.30f,
                        cx + radius, cy + radius * 0.30f);
                canvas.drawRoundRect(rect, radius * 0.12f, radius * 0.12f, paint);
                return;
            }
            if (shape == 5) {
                Path p = new Path();
                p.moveTo(cx - radius, cy - radius);
                p.lineTo(cx + radius, cy - radius);
                p.lineTo(cx - radius, cy + radius);
                p.close();
                canvas.drawPath(p, paint);
                return;
            }
            canvas.drawCircle(cx, cy, radius * 0.30f, paint);
        }
    }

    private static class LensView extends View {
        static final int MODE_BRUSH = 1;
        static final int MODE_COLOR_CLEAN = 2;

        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();

        private Bitmap source;
        private float sourceX;
        private float sourceY;
        private float sourceRadius = 20f;
        private int mode = MODE_BRUSH;
        private float brushWidthPx;
        private int brushShape = -1;
        private float brushRotationDegrees = 0f;

        LensView(android.content.Context context) {
            super(context);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(4f);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setLens(Bitmap bitmap, float x, float y, float radius, int lensMode,
                     float brushWidth, int selectedBrushShape, float selectedBrushRotation) {
            source = bitmap;
            sourceX = x;
            sourceY = y;
            sourceRadius = Math.max(2f, radius);
            mode = lensMode;
            brushWidthPx = brushWidth;
            brushShape = selectedBrushShape;
            brushRotationDegrees = selectedBrushRotation;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float radius = Math.min(getWidth(), getHeight()) * 0.5f - 5f;

            clip.reset();
            clip.addCircle(cx, cy, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            canvas.drawColor(0xFFF7FBFC);

            if (source != null && !source.isRecycled()) {
                // Anchor the magnified image to the exact brush-tip/touch pixel.
                // This keeps the FRONT bristle tip at lens center even near photo edges.
                float scale = radius / Math.max(1f, sourceRadius);
                Matrix lensMatrix = new Matrix();
                lensMatrix.postTranslate(-sourceX, -sourceY);
                lensMatrix.postScale(scale, scale);
                lensMatrix.postTranslate(cx, cy);
                canvas.drawBitmap(source, lensMatrix, bitmapPaint);
            }
            canvas.restore();

            int accent = mode == MODE_BRUSH ? 0xFF4F8F8B : 0xFF5B7FA3;
            borderPaint.setColor(accent);
            canvas.drawCircle(cx, cy, radius, borderPaint);

            if (mode == MODE_BRUSH && brushWidthPx > 0f) {
                if (brushShape >= 0) {
                    float rr = Math.min(radius * 0.72f, Math.max(10f, brushWidthPx));
                    canvas.save();
                    canvas.rotate(brushRotationDegrees, cx, cy);

                    guidePaint.setStyle(Paint.Style.STROKE);
                    guidePaint.setStrokeWidth(5f);
                    guidePaint.setColor(0xEEFFFFFF);
                    drawBrushShapeOutline(canvas, cx, cy, rr, brushShape, guidePaint);

                    guidePaint.setStrokeWidth(2.2f);
                    guidePaint.setColor(0xEE111111);
                    drawBrushShapeOutline(canvas, cx, cy, rr, brushShape, guidePaint);
                    canvas.restore();
                } else {
                    // Normal mode: larger pen visual, FRONT bristle tip remains exact center.
                    float contactW = Math.min(radius * 0.54f, Math.max(10f, brushWidthPx));
                    float w = Math.max(24f, contactW * 1.42f);
                    float angle = -48f;
                    double rad = Math.toRadians(angle);
                    float ux = (float)Math.cos(rad);
                    float uy = (float)Math.sin(rad);
                    float nx = -uy;
                    float ny = ux;

                    float tipX = cx;
                    float tipY = cy;
                    float bristleLen = Math.max(20f, w * 1.15f);
                    float b1x = tipX - ux * bristleLen;
                    float b1y = tipY - uy * bristleLen;

                    Path bp = new Path();
                    float halfTip = Math.max(3f, w * 0.20f);
                    float halfBase = Math.max(5f, w * 0.38f);
                    bp.moveTo(tipX + nx * halfTip, tipY + ny * halfTip);
                    bp.lineTo(tipX - nx * halfTip, tipY - ny * halfTip);
                    bp.lineTo(b1x - nx * halfBase, b1y - ny * halfBase);
                    bp.lineTo(b1x + nx * halfBase, b1y + ny * halfBase);
                    bp.close();

                    guidePaint.setStyle(Paint.Style.FILL);
                    guidePaint.setColor(0xCC6D4C35);
                    canvas.drawPath(bp, guidePaint);

                    guidePaint.setStyle(Paint.Style.STROKE);
                    guidePaint.setStrokeWidth(Math.max(7f, w * 0.58f));
                    guidePaint.setColor(0xFFD5D9DE);
                    float fx = b1x - ux * Math.max(15f, w * 0.72f);
                    float fy = b1y - uy * Math.max(15f, w * 0.72f);
                    canvas.drawLine(b1x, b1y, fx, fy, guidePaint);

                    guidePaint.setStrokeCap(Paint.Cap.ROUND);
                    guidePaint.setStrokeWidth(Math.max(8f, w * 0.52f));
                    guidePaint.setColor(0xFF7C4F2D);
                    canvas.drawLine(fx, fy,
                            fx - ux * Math.max(36f, w * 1.75f),
                            fy - uy * Math.max(36f, w * 1.75f), guidePaint);
                    guidePaint.setStrokeCap(Paint.Cap.BUTT);

                    guidePaint.setStrokeWidth(2.2f);
                    guidePaint.setColor(0xEE111111);
                    canvas.drawLine(tipX - nx * contactW * 0.42f, tipY - ny * contactW * 0.42f,
                            tipX + nx * contactW * 0.42f, tipY + ny * contactW * 0.42f, guidePaint);
                }
            } else {
                guidePaint.setStyle(Paint.Style.STROKE);
                guidePaint.setStrokeWidth(2.5f);
                guidePaint.setColor(0xEE111111);
                canvas.drawLine(cx - 13f, cy, cx + 13f, cy, guidePaint);
                canvas.drawLine(cx, cy - 13f, cx, cy + 13f, guidePaint);

                guidePaint.setStyle(Paint.Style.FILL);
                guidePaint.setColor(accent);
                canvas.drawCircle(cx, cy, 5f, guidePaint);
            }
        }
        private void drawBrushShapeOutline(Canvas canvas, float cx, float cy,
                                           float radius, int shape, Paint paint) {
            if (radius <= 0f) return;

            if (shape == 0) {
                canvas.drawCircle(cx, cy, radius, paint);
                float oldAlpha = paint.getAlpha();
                paint.setAlpha(Math.min(190, oldAlpha));
                canvas.drawCircle(cx, cy, radius * 0.36f, paint);
                paint.setAlpha(oldAlpha);
                return;
            }
            if (shape == 1) {
                canvas.drawCircle(cx, cy, radius, paint);
                return;
            }
            if (shape == 2) {
                RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
                Path p = new Path();
                p.moveTo(cx, cy - radius);
                p.arcTo(oval, -90f, 180f);
                p.lineTo(cx, cy - radius);
                p.close();
                canvas.drawPath(p, paint);
                return;
            }
            if (shape == 3) {
                RectF rect = new RectF(cx - radius * 0.30f, cy - radius,
                        cx + radius * 0.30f, cy + radius);
                canvas.drawRoundRect(rect, radius * 0.12f, radius * 0.12f, paint);
                return;
            }
            if (shape == 4) {
                RectF rect = new RectF(cx - radius, cy - radius * 0.30f,
                        cx + radius, cy + radius * 0.30f);
                canvas.drawRoundRect(rect, radius * 0.12f, radius * 0.12f, paint);
                return;
            }
            if (shape == 5) {
                Path p = new Path();
                p.moveTo(cx - radius, cy - radius);
                p.lineTo(cx + radius, cy - radius);
                p.lineTo(cx - radius, cy + radius);
                p.close();
                canvas.drawPath(p, paint);
                return;
            }
            canvas.drawCircle(cx, cy, radius * 0.30f, paint);
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
        if (brushCursor != null) brushCursor.setVisibility(View.GONE);
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
