package com.sts.deepcleaner;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.StatFs;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.webkit.MimeTypeMap;
import androidx.core.content.FileProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class MainActivity extends Activity {
    private static final int PURPLE = Color.rgb(90,74,227);
    private static final int PURPLE2 = Color.rgb(117,92,255);
    private static final int TEAL = Color.rgb(41,214,194);
    private static final int AMBER = Color.rgb(255,179,71);
    private static final int ROSE = Color.rgb(255,103,137);
    private static final int BLUE = Color.rgb(73,144,226);
    private static final int BG = Color.rgb(247,248,255);
    private static final int INK = Color.rgb(22,27,40);
    private static final int MUTED = Color.rgb(102,108,128);

    private ScanSummary lastSummary;
    private boolean pendingScanAfterAccess = false;
    private boolean pendingDeepAfterAccess = false;
    private String pendingToolAfterAccess = null;
    private ToolResult activeToolResult = null;
    private ExoPlayer activePlayer = null;
    private PdfRenderer activePdfRenderer = null;
    private ParcelFileDescriptor activePdfFd = null;
    private StorageAnalytics cachedAnalytics = null;
    private long cachedAnalyticsAt = 0L;
    private boolean pendingUsageAnalyzer = false;

    private final Map<String,Integer> scrollPositions = new HashMap<>();
    private final Map<String,Integer> sortModes = new HashMap<>();
    private final Map<String,Set<String>> selections = new HashMap<>();
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(3);
    private ScrollView currentScrollView = null;
    private String currentScrollKey = null;
    private SystemStorageResult activeSystemResult = null;
    private AppDetailResult activeAppDetailResult = null;
    private AppVisibleCategory activeVisibleCategory = null;
    private ScanSummary activeScanSummary = null;
    private int activeScanGalleryMode = -1;
    private String previewReturnTag = null;
    private StorageCategory activeStorageCategory = null;
    private List<File> activeStorageCategoryFiles = null;
    private StorageCategory activeSystemCategory = null;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(PURPLE);
        w.setNavigationBarColor(BG);
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingUsageAnalyzer && hasUsageAccess()) {
            pendingUsageAnalyzer = false;
            cachedAnalytics = null;
            cachedAnalyticsAt = 0L;
            showSystemAnalyzer();
            return;
        }
        if (pendingToolAfterAccess != null && hasAllFilesAccess()) {
            String tool = pendingToolAfterAccess;
            pendingToolAfterAccess = null;
            openTool(tool);
            return;
        }
        if (pendingScanAfterAccess && hasAllFilesAccess()) {
            boolean deep = pendingDeepAfterAccess;
            pendingScanAfterAccess = false;
            pendingDeepAfterAccess = false;
            startScan(deep);
            return;
        }
        if (getWindow().getDecorView().getTag() != null &&
                "home".equals(getWindow().getDecorView().getTag())) {
            showHome();
        }
    }

    private void showHome() {
        rememberCurrentScroll();
        releasePreviewResources();
        purgeExpiredTrashAsync();
        getWindow().getDecorView().setTag("home");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(BG);

        LinearLayout root = column();
        root.setPadding(0, 0, 0, dp(24));

        LinearLayout header = column();
        header.setPadding(dp(20), dp(22), dp(20), dp(26));
        header.setBackground(gradient(PURPLE, PURPLE2, 0, 0, 0, 0));

        LinearLayout brandRow = row();
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.sts.deepcleaner.R.drawable.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        brandRow.addView(logo, logoLp);

        LinearLayout brandText = column();
        brandText.setPadding(dp(12), 0, 0, 0);
        TextView title = text("STS Smart Cleaner", 27, Color.WHITE, true);
        TextView sub = text("Safe • Smart • Deep", 14, Color.argb(220,255,255,255), false);
        brandText.addView(title);
        brandText.addView(space(3));
        brandText.addView(sub);
        brandRow.addView(brandText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(brandRow, matchWrap());
        header.addView(space(18));

        long[] st = storage();
        StorageRing ring = new StorageRing(this);
        ring.setStorage(st[0], st[1]);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(dp(210), dp(210));
        ringLp.gravity = Gravity.CENTER_HORIZONTAL;
        header.addView(ring, ringLp);

        header.addView(space(10));
        TextView mode = pill(hasAllFilesAccess() ? "Full storage access ON" : "Full scan access OFF",
                hasAllFilesAccess() ? Color.rgb(18,145,123) : PURPLE,
                hasAllFilesAccess() ? Color.rgb(228,252,248) : Color.rgb(244,240,255));
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modeLp.gravity = Gravity.CENTER_HORIZONTAL;
        header.addView(mode, modeLp);

        root.addView(header, matchWrap());

        LinearLayout content = column();
        content.setPadding(dp(16), dp(10), dp(16), 0);

        content.addView(section("Storage by File Type"));
        LinearLayout analyticsCard = card();
        analyticsCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView analyticsStatus = text("Analyzing visible files…", 12, MUTED, false);
        analyticsCard.addView(analyticsStatus);

        analyticsCard.addView(space(12));
        StorageBreakdownView breakdown = new StorageBreakdownView(this);
        analyticsCard.addView(breakdown, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        analyticsCard.addView(space(12));
        LinearLayout analyticsRows = column();
        analyticsCard.addView(analyticsRows, matchWrap());
        content.addView(analyticsCard, matchWrap());

        loadStorageAnalytics(st[0], st[1], ring, breakdown, analyticsRows, analyticsStatus);

        content.addView(section("Storage Tools"));
        TextView homeView = viewControl(this::showHome);
        content.addView(homeView, matchWrap());
        content.addView(space(10));
        addHomeToolGrid(content);

        content.addView(space(22));
        TextView scan = actionButton("SMART SCAN", PURPLE);
        touch(scan);
        scan.setOnClickListener(v -> beginScan(false));
        content.addView(scan, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

        content.addView(space(10));
        TextView deep = actionButton(hasAllFilesAccess() ? "DEEP SCAN • ACCESS ON" : "DEEP SCAN • ENABLE ACCESS",
                Color.rgb(236,233,255));
        deep.setTextColor(PURPLE);
        touch(deep);
        deep.setOnClickListener(v -> beginScan(true));
        content.addView(deep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        content.addView(space(16));
        TextView safety = text("✓ Personal photos, videos और documents auto-delete नहीं होंगे\n✓ System-critical paths हमेशा protected रहेंगे", 13, MUTED, false);
        safety.setLineSpacing(dp(3), 1f);
        content.addView(safety);

        root.addView(content, matchWrap());
        scroll.addView(root);
        setContentView(scroll);
        bindScrollPosition("home", scroll);
        fadeIn(root);
        popIn(logo);
    }

    private boolean galleryListMode() {
        return getSharedPreferences("sts_view", MODE_PRIVATE).getBoolean("gallery_list_mode", false);
    }

    private void setGalleryListMode(boolean listMode) {
        getSharedPreferences("sts_view", MODE_PRIVATE).edit()
                .putBoolean("gallery_list_mode", listMode).apply();
    }

    private int galleryColumns() {
        int n = getSharedPreferences("sts_view", MODE_PRIVATE).getInt("gallery_columns", 3);
        return Math.max(2, Math.min(5, n));
    }

    private void setGalleryColumns(int n) {
        getSharedPreferences("sts_view", MODE_PRIVATE)
                .edit().putInt("gallery_columns", Math.max(2, Math.min(5, n))).apply();
    }

    private TextView viewControl(Runnable refresh) {
        String label = galleryListMode() ? "VIEW  •  LIST" : "VIEW  •  GRID " + galleryColumns();
        TextView v = pill(label, Color.rgb(61,92,150), Color.rgb(235,242,255));
        touch(v);
        v.setOnClickListener(x -> {
            String[] options = {"List", "Grid • 2 Columns", "Grid • 3 Columns", "Grid • 4 Columns", "Grid • 5 Columns"};
            int current = galleryListMode() ? 0 : galleryColumns() - 1;
            new AlertDialog.Builder(this)
                    .setTitle("View Layout")
                    .setSingleChoiceItems(options, current, (d, which) -> {
                        if (which == 0) {
                            setGalleryListMode(true);
                        } else {
                            setGalleryListMode(false);
                            setGalleryColumns(which + 1);
                        }
                        d.dismiss();
                        refresh.run();
                    })
                    .setNegativeButton("CANCEL", null)
                    .show();
        });
        return v;
    }


    private Set<String> selectionSet(String key) {
        Set<String> set = selections.get(key);
        if (set == null) {
            set = new HashSet<>();
            selections.put(key, set);
        }
        return set;
    }

    private void toggleSelected(String key, File file) {
        if (file == null) return;
        Set<String> set = selectionSet(key);
        String p = file.getAbsolutePath();
        if (set.contains(p)) set.remove(p);
        else set.add(p);
    }

    private boolean selected(String key, File file) {
        return file != null && selectionSet(key).contains(file.getAbsolutePath());
    }

    private void selectAllFiles(String key, List<File> files) {
        Set<String> set = selectionSet(key);
        set.clear();
        for (File f : files) if (f != null && f.exists()) set.add(f.getAbsolutePath());
    }

    private void clearSelection(String key) {
        selectionSet(key).clear();
    }

    private long selectedBytes(String key, List<File> files) {
        Set<String> set = selectionSet(key);
        long total = 0L;
        for (File f : files) if (f != null && set.contains(f.getAbsolutePath())) total += Math.max(0L, f.length());
        return total;
    }

    private List<File> selectedFiles(String key, List<File> files) {
        Set<String> set = selectionSet(key);
        List<File> out = new ArrayList<>();
        for (File f : files) if (f != null && set.contains(f.getAbsolutePath())) out.add(f);
        return out;
    }

    private LinearLayout galleryControls(String selectionKey, List<File> selectableFiles,
                                         Runnable refresh, String selectedActionLabel,
                                         Runnable selectedAction) {
        LinearLayout box = column();

        LinearLayout top = row();
        TextView view = viewControl(refresh);
        TextView all = pill("SELECT ALL", Color.rgb(18,145,123), Color.rgb(228,252,248));
        TextView clear = pill("CLEAR ALL", Color.rgb(92,98,116), Color.rgb(239,241,246));
        touch(all); touch(clear);
        all.setOnClickListener(v -> { selectAllFiles(selectionKey, selectableFiles); refresh.run(); });
        clear.setOnClickListener(v -> { clearSelection(selectionKey); refresh.run(); });
        top.addView(view, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        top.addView(spaceH(6));
        top.addView(all, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .9f));
        top.addView(spaceH(6));
        top.addView(clear, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .7f));
        box.addView(top, matchWrap());

        box.addView(space(8));
        Set<String> set = selectionSet(selectionKey);
        int count = 0;
        long bytes = 0L;
        for (File file : selectableFiles) {
            if (file != null && set.contains(file.getAbsolutePath())) {
                count++;
                bytes += Math.max(0L, file.length());
            }
        }
        TextView selectedInfo = centerText(count + " selected  •  " + format(bytes), 12,
                count > 0 ? PURPLE : MUTED, true);
        box.addView(selectedInfo, matchWrap());

        if (count > 0 && selectedActionLabel != null && selectedAction != null) {
            box.addView(space(8));
            int actionColor = selectedActionLabel.contains("RESTORE") ? TEAL
                    : (selectedActionLabel.contains("DELETE") || selectedActionLabel.contains("TRASH") ||
                       selectedActionLabel.contains("CLEAN")) ? ROSE : PURPLE;
            TextView action = actionButton(selectedActionLabel, actionColor);
            touch(action);
            action.setOnClickListener(v -> selectedAction.run());
            box.addView(action, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        }
        return box;
    }

    private int previewHeightDp() {
        if (galleryListMode()) return 86;
        switch (galleryColumns()) {
            case 2: return 150;
            case 4: return 82;
            case 5: return 66;
            case 3:
            default: return 108;
        }
    }

    private void showFileActions(File file, AppFileInfo info) {
        List<String> actions = new ArrayList<>();
        actions.add("Open / Preview");
        actions.add("Share");
        actions.add("Details");
        String[] options = actions.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(options, (d, which) -> {
                    if (which == 0) openFoundFile(file);
                    else if (which == 1) shareFoundFile(file);
                    else showFileDetails(file, info != null ? info : describeAppFile(file));
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void shareFoundFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(mime(file));
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share file"));
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Share unavailable")
                    .setMessage("इस file को share नहीं किया जा सका।")
                    .setPositiveButton("OK", null).show();
        }
    }

    private LinearLayout galleryFileCard(File file, long bytes, String selectionKey,
                                         boolean selectable, AppFileInfo info, int accent,
                                         Runnable refresh) {
        boolean isSelected = selectable && selected(selectionKey, file);
        boolean listMode = galleryListMode();

        LinearLayout card = listMode ? row() : column();
        card.setGravity(listMode ? Gravity.CENTER_VERTICAL : Gravity.NO_GRAVITY);
        card.setPadding(dp(6), dp(6), dp(6), dp(7));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isSelected ? Color.rgb(239,236,255) : Color.WHITE);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(isSelected ? 2 : 1), isSelected ? PURPLE : Color.rgb(224,227,236));
        card.setBackground(bg);
        card.setElevation(dp(2));

        FrameLayout preview = new FrameLayout(this);
        preview.setBackgroundColor(Color.rgb(238,240,247));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(238,240,247));
        preview.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int fallbackSize = listMode ? 22 : (galleryColumns() >= 4 ? 20 : 28);
        TextView fallback = centerText(fileIcon(file), fallbackSize, MUTED, true);
        preview.addView(fallback, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (isVideoFile(file)) {
            TextView play = centerText("▶", listMode ? 18 : (galleryColumns() >= 4 ? 20 : 30), Color.WHITE, true);
            GradientDrawable pd = new GradientDrawable();
            pd.setColor(Color.argb(135, 0, 0, 0));
            pd.setShape(GradientDrawable.OVAL);
            play.setBackground(pd);
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(listMode ? 34 : 42), dp(listMode ? 34 : 42), Gravity.CENTER);
            preview.addView(play, pp);
        }

        TextView menu = centerText("⋮", 18, Color.rgb(45,50,65), true);
        GradientDrawable md = new GradientDrawable();
        md.setShape(GradientDrawable.OVAL);
        md.setColor(Color.argb(230,255,255,255));
        menu.setBackground(md);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.TOP|Gravity.START);
        mp.setMargins(dp(5),dp(5),0,0);
        preview.addView(menu,mp);
        touch(menu);
        menu.setOnClickListener(v -> showFileActions(file, info));

        if (selectable) {
            TextView check = centerText(isSelected ? "✓" : "○", 16,
                    isSelected ? Color.WHITE : PURPLE, true);
            GradientDrawable cd = new GradientDrawable();
            cd.setShape(GradientDrawable.OVAL);
            cd.setColor(isSelected ? PURPLE : Color.argb(235,255,255,255));
            check.setBackground(cd);
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(30), dp(30),
                    Gravity.TOP | Gravity.END);
            cp.setMargins(0, dp(5), dp(5), 0);
            preview.addView(check, cp);
            touch(check);
            check.setOnClickListener(v -> {
                toggleSelected(selectionKey, file);
                refresh.run();
            });
        }

        touch(preview);
        preview.setOnClickListener(v -> openFoundFile(file));
        preview.setOnLongClickListener(v -> {
            if (!selectable) return false;
            toggleSelected(selectionKey, file);
            refresh.run();
            return true;
        });

        if (listMode) {
            card.addView(preview, new LinearLayout.LayoutParams(dp(108), dp(previewHeightDp())));
            card.addView(spaceH(9));
            LinearLayout details = column();
            TextView name = text(file.getName(), 12, INK, true);
            name.setMaxLines(2);
            details.addView(name, matchWrap());
            details.addView(space(3));
            details.addView(text(format(bytes), 10, accent, true));
            if (info != null) {
                details.addView(space(3));
                TextView status = text(info.status, 9, info.statusColor, true);
                status.setMaxLines(1);
                details.addView(status, matchWrap());
            }
            details.addView(space(5));
            TextView actions = text("OPEN  •  SHARE  •  DETAILS", 8, Color.rgb(61,92,150), true);
            actions.setOnClickListener(v -> showFileActions(file, info));
            touch(actions);
            details.addView(actions, matchWrap());
            card.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            card.addView(preview, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(previewHeightDp())));
            card.addView(space(6));
            TextView name = text(file.getName(), galleryColumns() >= 4 ? 9 : 11, INK, true);
            name.setMaxLines(2);
            card.addView(name, matchWrap());
            card.addView(space(2));
            card.addView(text(format(bytes), galleryColumns() >= 4 ? 8 : 10, accent, true));
            if (info != null && galleryColumns() <= 3) {
                card.addView(space(3));
                TextView status = text(info.status, 8, info.statusColor, true);
                status.setMaxLines(1);
                card.addView(status, matchWrap());
            }
        }

        card.setOnLongClickListener(v -> {
            if (!selectable) return false;
            toggleSelected(selectionKey, file);
            refresh.run();
            return true;
        });

        loadThumbnail(image, fallback, file);
        return card;
    }


    private String fileIcon(File file) {
        String n = file.getName().toLowerCase(Locale.ROOT);
        if (isImageFile(file)) return "▧";
        if (isVideoFile(file)) return "▶";
        if (isAudioFile(n)) return "♪";
        if (isPdfFile(file)) return "PDF";
        if (n.endsWith(".apk")) return "APK";
        if (isArchiveFile(n)) return "ZIP";
        if (isTextFile(file)) return "TXT";
        return "FILE";
    }

    private void loadThumbnail(ImageView image, TextView fallback, File file) {
        String path = file.getAbsolutePath();
        image.setTag(path);
        thumbnailExecutor.execute(() -> {
            Bitmap bm = createThumbnail(file, Math.max(dp(140), dp(previewHeightDp())));
            if (bm == null) return;
            runOnUiThread(() -> {
                Object tag = image.getTag();
                if (tag != null && path.equals(tag.toString())) {
                    image.setImageBitmap(bm);
                    fallback.setVisibility(View.GONE);
                }
            });
        });
    }

    private Bitmap createThumbnail(File file, int targetPx) {
        try {
            if (isImageFile(file)) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                int sample = 1;
                while (bounds.outWidth / sample > targetPx * 2 ||
                        bounds.outHeight / sample > targetPx * 2) sample *= 2;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = Math.max(1, sample);
                Bitmap src = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                if (src == null) return null;
                return ThumbnailUtils.extractThumbnail(src, targetPx, targetPx,
                        ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
            }
            if (isVideoFile(file)) {
                Bitmap src = ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(),
                        MediaStore.Video.Thumbnails.MINI_KIND);
                if (src == null) return null;
                return ThumbnailUtils.extractThumbnail(src, targetPx, targetPx,
                        ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
            }
            if (isPdfFile(file)) {
                try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                     PdfRenderer renderer = new PdfRenderer(fd)) {
                    if (renderer.getPageCount() <= 0) return null;
                    PdfRenderer.Page page = renderer.openPage(0);
                    int w = targetPx;
                    int h = Math.max(1, Math.round(targetPx * (float) page.getHeight() /
                            Math.max(1, page.getWidth())));
                    Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();
                    return bitmap;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void addGalleryCells(LinearLayout gallery, List<LinearLayout> cells) {
        int cols = galleryListMode() ? 1 : galleryColumns();
        LinearLayout row = null;
        int inRow = 0;
        for (LinearLayout cell : cells) {
            if (row == null || inRow == cols) {
                if (gallery.getChildCount() > 0) gallery.addView(space(7));
                row = row();
                gallery.addView(row, matchWrap());
                inRow = 0;
            }
            if (inRow > 0) row.addView(spaceH(7));
            row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            inRow++;
        }
        if (!galleryListMode() && row != null && inRow < cols) {
            while (inRow < cols) {
                if (inRow > 0) row.addView(spaceH(7));
                Space filler = new Space(this);
                row.addView(filler, new LinearLayout.LayoutParams(0, 1, 1f));
                inRow++;
            }
        }
    }


    private static final String[] FILE_SORT_OPTIONS = new String[]{
            "Largest",
            "Smallest",
            "Newest",
            "Oldest",
            "A–Z",
            "Z–A"
    };

    private static final String[] APP_SORT_OPTIONS = new String[]{
            "Total size • Large → Small",
            "Total size • Small → Large",
            "Name • A → Z",
            "Name • Z → A",
            "Private data • Large → Small",
            "Cache • Large → Small",
            "App code • Large → Small",
            "User apps / System apps"
    };

    private static final String[] TRASH_SORT_OPTIONS = new String[]{
            "Deleted • Newest first",
            "Deleted • Oldest first",
            "Size • Large → Small",
            "Size • Small → Large",
            "Name • A → Z",
            "Name • Z → A"
    };

    private interface SortRefresh {
        void refresh();
    }

    private int sortMode(String key, int defaultMode) {
        if (sortModes.containsKey(key)) return sortModes.get(key);
        int mode = getSharedPreferences("sts_sort_modes", MODE_PRIVATE)
                .getInt(key, defaultMode);
        sortModes.put(key, mode);
        return mode;
    }

    private void setSortMode(String key, int mode) {
        sortModes.put(key, mode);
        getSharedPreferences("sts_sort_modes", MODE_PRIVATE)
                .edit().putInt(key, mode).apply();
    }

    private void resetSavedScroll(String key) {
        scrollPositions.put(key, 0);
        getSharedPreferences("sts_scroll_positions", MODE_PRIVATE)
                .edit().putInt(key, 0).apply();
    }

    private TextView sortControl(String key, String[] options, int defaultMode, SortRefresh refresh) {
        int current = Math.max(0, Math.min(options.length - 1, sortMode(key, defaultMode)));
        TextView v = pill("SORT BY  •  " + options[current], PURPLE, Color.rgb(239,236,255));
        touch(v);
        v.setOnClickListener(x -> showSortChooser(key, options, defaultMode, refresh));
        return v;
    }

    private void showSortChooser(String key, String[] options, int defaultMode, SortRefresh refresh) {
        int current = Math.max(0, Math.min(options.length - 1, sortMode(key, defaultMode)));
        new AlertDialog.Builder(this)
                .setTitle("Sort By")
                .setSingleChoiceItems(options, current, (dialog, which) -> {
                    setSortMode(key, which);
                    dialog.dismiss();
                    refresh.refresh();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void sortToolItems(ToolResult result, int mode) {
        Comparator<ToolItem> cmp;
        switch (mode) {
            case 1: cmp = Comparator.comparingLong(a -> a.bytes); break;
            case 2: cmp = (a,b) -> Long.compare(b.file.lastModified(), a.file.lastModified()); break;
            case 3: cmp = Comparator.comparingLong(a -> a.file.lastModified()); break;
            case 4: cmp = Comparator.comparing(a -> a.file.getName().toLowerCase(Locale.ROOT)); break;
            case 5: cmp = (a,b) -> b.file.getName().compareToIgnoreCase(a.file.getName()); break;
            case 0:
            default: cmp = (a,b) -> Long.compare(b.bytes, a.bytes); break;
        }
        Collections.sort(result.items, cmp);
    }


    private void sortScanItems(List<ScanItem> items, int mode) {
        Comparator<ScanItem> cmp;
        switch (mode) {
            case 1: cmp = Comparator.comparingLong(a -> a.bytes); break;
            case 2: cmp = (a,b) -> Long.compare(b.file.lastModified(), a.file.lastModified()); break;
            case 3: cmp = Comparator.comparingLong(a -> a.file.lastModified()); break;
            case 4: cmp = Comparator.comparing(a -> a.file.getName().toLowerCase(Locale.ROOT)); break;
            case 5: cmp = (a,b) -> b.file.getName().compareToIgnoreCase(a.file.getName()); break;
            case 0:
            default: cmp = (a,b) -> Long.compare(b.bytes, a.bytes); break;
        }
        Collections.sort(items, cmp);
    }


    private void sortAppVisibleFiles(AppDetailResult result, int mode) {
        Comparator<AppVisibleFile> cmp;
        switch (mode) {
            case 1: cmp = Comparator.comparingLong(a -> a.bytes); break;
            case 2: cmp = (a,b) -> Long.compare(b.file.lastModified(), a.file.lastModified()); break;
            case 3: cmp = Comparator.comparingLong(a -> a.file.lastModified()); break;
            case 4: cmp = Comparator.comparing(a -> a.file.getName().toLowerCase(Locale.ROOT)); break;
            case 5: cmp = (a,b) -> b.file.getName().compareToIgnoreCase(a.file.getName()); break;
            case 0:
            default: cmp = (a,b) -> Long.compare(b.bytes, a.bytes); break;
        }
        Collections.sort(result.files, cmp);
    }


    private void sortApps(SystemStorageResult result, int mode) {
        Comparator<AppStorageEntry> cmp;
        switch (mode) {
            case 1: cmp = Comparator.comparingLong(a -> a.totalBytes); break;
            case 2: cmp = Comparator.comparing(a -> a.appName.toLowerCase(Locale.ROOT)); break;
            case 3: cmp = (a,b) -> b.appName.compareToIgnoreCase(a.appName); break;
            case 4: cmp = (a,b) -> Long.compare(b.dataBytes, a.dataBytes); break;
            case 5: cmp = (a,b) -> Long.compare(b.cacheBytes, a.cacheBytes); break;
            case 6: cmp = (a,b) -> Long.compare(b.codeBytes, a.codeBytes); break;
            case 7:
                cmp = Comparator.comparing((AppStorageEntry a) -> a.systemApp)
                        .thenComparing((AppStorageEntry a) -> -a.totalBytes);
                break;
            case 0:
            default: cmp = (a,b) -> Long.compare(b.totalBytes, a.totalBytes); break;
        }
        Collections.sort(result.apps, cmp);
    }

    private File trashDataFile(File meta) {
        String baseName = meta.getName().substring(0, meta.getName().length() - 8);
        return new File(trashDir(), baseName);
    }

    private String trashDisplayName(File meta) {
        File data = trashDataFile(meta);
        return data.getName().replaceFirst("^\\d+_", "");
    }

    private long trashDeletedTime(File meta) {
        String[] md = readTrashMeta(meta);
        try { return Long.parseLong(md[1]); } catch (Exception e) { return meta.lastModified(); }
    }

    private void sortTrashMeta(File[] metas, int mode) {
        Comparator<File> cmp;
        switch (mode) {
            case 1: cmp = Comparator.comparingLong(this::trashDeletedTime); break;
            case 2: cmp = (a,b) -> Long.compare(trashDataFile(b).length(), trashDataFile(a).length()); break;
            case 3: cmp = Comparator.comparingLong(a -> trashDataFile(a).length()); break;
            case 4: cmp = Comparator.comparing(this::trashDisplayName, String.CASE_INSENSITIVE_ORDER); break;
            case 5: cmp = (a,b) -> trashDisplayName(b).compareToIgnoreCase(trashDisplayName(a)); break;
            case 0:
            default: cmp = (a,b) -> Long.compare(trashDeletedTime(b), trashDeletedTime(a)); break;
        }
        java.util.Arrays.sort(metas, cmp);
    }

    private void rememberCurrentScroll() {
        if (currentScrollView == null || currentScrollKey == null) return;
        int y = Math.max(0, currentScrollView.getScrollY());
        scrollPositions.put(currentScrollKey, y);
        getSharedPreferences("sts_scroll_positions", MODE_PRIVATE)
                .edit().putInt(currentScrollKey, y).apply();
    }

    private void bindScrollPosition(String key, ScrollView scroll) {
        currentScrollView = scroll;
        currentScrollKey = key;
        int y = scrollPositions.containsKey(key)
                ? scrollPositions.get(key)
                : getSharedPreferences("sts_scroll_positions", MODE_PRIVATE).getInt(key, 0);
        scroll.post(() -> scroll.scrollTo(0, Math.max(0, y)));
    }

    private void clearCurrentScrollBinding() {
        currentScrollView = null;
        currentScrollKey = null;
    }

    private void beginNonScrollScreen() {
        rememberCurrentScroll();
        clearCurrentScrollBinding();
    }

    private void loadStorageAnalytics(long totalStorage, long usedStorage, StorageRing ring,
                                      StorageBreakdownView chart,
                                      LinearLayout rows, TextView status) {
        if (cachedAnalytics != null && System.currentTimeMillis() - cachedAnalyticsAt < 5L*60*1000) {
            ring.setAnalytics(cachedAnalytics);
            renderStorageAnalytics(cachedAnalytics, usedStorage, chart, rows, status);
            return;
        }

        new Thread(() -> {
            StorageAnalytics a = new StorageAnalytics();
            File root = Environment.getExternalStorageDirectory();
            Set<String> seen = new HashSet<>();
            scanAnalytics(root, seen, 0, a);

            if (hasUsageAccess()) {
                fillPrivateStorageStats(a, usedStorage);
            } else {
                long hiddenBytes = Math.max(0L, usedStorage - a.visibleBytes);
                a.add(StorageCategory.HIDDEN_UNCLASSIFIED, hiddenBytes, hiddenBytes > 0 ? 1 : 0);
            }
            a.usedStorage = usedStorage;

            cachedAnalytics = a;
            cachedAnalyticsAt = System.currentTimeMillis();

            runOnUiThread(() -> {
                Object tag = getWindow().getDecorView().getTag();
                if ("home".equals(tag)) {
                    ring.setAnalytics(a);
                    renderStorageAnalytics(a, usedStorage, chart, rows, status);
                }
            });
        }, "sts-storage-analytics").start();
    }

    private void scanAnalytics(File f, Set<String> seen, int depth, StorageAnalytics a) {
        if (f == null || depth > 24) return;
        String path;
        try { path = f.getCanonicalPath(); } catch (Exception e) { path = f.getAbsolutePath(); }
        if (!seen.add(path)) return;

        String lowPath = path.toLowerCase(Locale.ROOT);
        if (lowPath.contains("/android/data/") || lowPath.contains("/android/obb/") ||
                lowPath.contains("/sts_trash/")) {
            return;
        }

        if (f.isDirectory()) {
            File[] children;
            try { children = f.listFiles(); } catch (Exception e) { children = null; }
            if (children == null) return;
            for (File x : children) scanAnalytics(x, seen, depth+1, a);
            return;
        }

        long len = Math.max(0L, f.length());
        a.visibleBytes += len;
        a.visibleFiles++;

        StorageCategory cat = categoryForAnalytics(f, lowPath);
        a.add(cat, len, 1);
    }

    private StorageCategory categoryForAnalytics(File f, String lowPath) {
        String n = f.getName().toLowerCase(Locale.ROOT);

        if (isImageFile(f)) return StorageCategory.PHOTOS;
        if (isVideoFile(f)) return StorageCategory.VIDEOS;
        if (isAudioFile(n)) return StorageCategory.AUDIO;

        if (n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx") ||
                n.endsWith(".xls") || n.endsWith(".xlsx") || n.endsWith(".ppt") ||
                n.endsWith(".pptx") || n.endsWith(".txt") || n.endsWith(".rtf") ||
                n.endsWith(".csv") || n.endsWith(".odt") || n.endsWith(".ods") ||
                n.endsWith(".odp")) {
            return StorageCategory.DOCUMENTS;
        }

        if (n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk") ||
                n.endsWith(".aab")) {
            return StorageCategory.APK;
        }

        if (isWhatsAppChatBackup(n, lowPath) || isDatabaseLike(n, lowPath) ||
                lowPath.contains("/backup/") || lowPath.contains("/backups/") ||
                n.contains("backup") || n.endsWith(".bak")) {
            return StorageCategory.BACKUPS;
        }

        if (lowPath.contains("/cache/") || lowPath.contains("/.cache/") ||
                lowPath.contains("/.thumbnails/") || lowPath.contains("/temp/") ||
                lowPath.contains("/tmp/") || n.endsWith(".tmp") || n.endsWith(".temp") ||
                n.endsWith(".log") || n.endsWith(".dmp") || n.endsWith(".crash")) {
            return StorageCategory.JUNK;
        }

        return StorageCategory.OTHERS;
    }

    private void renderStorageAnalytics(StorageAnalytics a, long usedStorage,
                                        StorageBreakdownView chart,
                                        LinearLayout rows, TextView status) {
        rows.removeAllViews();
        chart.setAnalytics(a, Math.max(1L, usedStorage));

        status.setText(format(a.visibleBytes) + " visible files scanned • " +
                a.visibleFiles + " files • % of used storage");

        for (StorageCategory cat : StorageCategory.values()) {
            long bytes = a.bytes(cat);
            int count = a.count(cat);
            if (bytes <= 0) continue;

            float pct = usedStorage <= 0 ? 0f : (bytes * 100f / usedStorage);
            LinearLayout r = row();
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(6), 0, dp(6));

            TextView dot = text("●", 18, cat.color, true);
            r.addView(dot, new LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout labels = column();
            labels.addView(text(cat.label, 13, INK, true));
            String countText;
            if (cat == StorageCategory.SYSTEM_RESERVED) countText = "Android / reserved estimate";
            else if (cat == StorageCategory.APP_CODE) countText = "installed app code";
            else if (cat == StorageCategory.APP_DATA) countText = "private app data";
            else if (cat == StorageCategory.APP_CACHE) countText = "reclaimable app cache";
            else if (cat == StorageCategory.HIDDEN_UNCLASSIFIED) countText = "tap Apps & System to split";
            else countText = count + (count == 1 ? " file" : " files");
            labels.addView(text(countText, 10, MUTED, false));
            r.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView value = text(format(bytes) + "  •  " + percentText(pct), 12, cat.color, true);
            value.setGravity(Gravity.END);
            r.addView(value);

            rows.addView(r, matchWrap());
        }

        if ("home".equals(currentScrollKey) && currentScrollView != null) {
            int y = scrollPositions.containsKey("home")
                    ? scrollPositions.get("home")
                    : getSharedPreferences("sts_scroll_positions", MODE_PRIVATE).getInt("home", 0);
            currentScrollView.postDelayed(() -> currentScrollView.scrollTo(0, Math.max(0, y)), 80);
        }
    }

    private String percentText(float pct) {
        if (pct > 0f && pct < 0.1f) return "<0.1%";
        if (pct >= 10f) return String.format(Locale.US, "%.0f%%", pct);
        return String.format(Locale.US, "%.1f%%", pct);
    }

    private boolean hasUsageAccess() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private void fillPrivateStorageStats(StorageAnalytics a, long usedStorage) {
        long appBytes = 0L;
        long dataBytes = 0L;
        long cacheBytes = 0L;
        try {
            StorageStatsManager mgr = (StorageStatsManager) getSystemService(STORAGE_STATS_SERVICE);
            StorageStats stats = mgr.queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle());
            appBytes = Math.max(0L, stats.getAppBytes());
            dataBytes = Math.max(0L, stats.getDataBytes());
            cacheBytes = Math.max(0L, stats.getCacheBytes());
        } catch (Exception ignored) {}

        a.add(StorageCategory.APP_CODE, appBytes, appBytes > 0 ? 1 : 0);
        a.add(StorageCategory.APP_DATA, dataBytes, dataBytes > 0 ? 1 : 0);
        a.add(StorageCategory.APP_CACHE, cacheBytes, cacheBytes > 0 ? 1 : 0);

        long accounted = a.visibleBytes + appBytes + dataBytes + cacheBytes;
        long systemReserved = Math.max(0L, usedStorage - accounted);
        a.add(StorageCategory.SYSTEM_RESERVED, systemReserved, systemReserved > 0 ? 1 : 0);
        a.privateStatsAvailable = true;
    }

    private void openSystemAnalyzer() {
        haptic();
        if (!hasUsageAccess()) {
            pendingUsageAnalyzer = true;
            new AlertDialog.Builder(this)
                    .setTitle("Apps & System Analyzer")
                    .setMessage("78 GB जैसे hidden/private हिस्से को Apps, App Data, Cache और System में अलग करने के लिए Android का Usage Access चाहिए। यह permission files delete नहीं करती; केवल storage statistics पढ़ने देती है।")
                    .setNegativeButton("Cancel", (d,w) -> pendingUsageAnalyzer = false)
                    .setPositiveButton("GRANT USAGE ACCESS", (d,w) -> {
                        try {
                            Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                            startActivity(i);
                        } catch (Exception e) {
                            pendingUsageAnalyzer = false;
                        }
                    }).show();
            return;
        }
        showSystemAnalyzer();
    }

    private void showSystemAnalyzer() {
        beginNonScrollScreen();
        getWindow().getDecorView().setTag("systemAnalyzer");
        LinearLayout root = column();
        root.setPadding(dp(18), dp(24), dp(18), dp(28));
        root.setBackgroundColor(BG);

        root.addView(text("Apps & System Analyzer", 27, INK, true));
        root.addView(space(5));
        TextView status = text("Installed apps और private storage analyze हो रहा है…", 13, MUTED, false);
        root.addView(status);
        root.addView(space(20));

        ScanRing ring = new ScanRing(this);
        ring.setLiveCount(0, "apps");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(190), dp(190));
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(ring, rlp);

        root.addView(space(18));
        LinearLayout live = card();
        live.setPadding(dp(18),dp(16),dp(18),dp(16));
        TextView processed = text("Apps scanned: 0", 14, INK, true);
        TextView current = text("Preparing…", 12, MUTED, false);
        TextView appCode = text("App code: …", 14, Color.rgb(82,88,110), true);
        TextView appData = text("App data: …", 14, PURPLE, true);
        TextView appCache = text("App cache: …", 14, TEAL, true);
        TextView sys = text("Android/System: calculating…", 14, MUTED, true);
        live.addView(processed);
        live.addView(space(5));
        live.addView(current);
        live.addView(space(10));
        live.addView(appCode);
        live.addView(space(6));
        live.addView(appData);
        live.addView(space(6));
        live.addView(appCache);
        live.addView(space(6));
        live.addView(sys);
        root.addView(live, matchWrap());

        Space flex = new Space(this);
        root.addView(flex,new LinearLayout.LayoutParams(1,0,1f));
        TextView back = actionButton("BACK",Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> showHome());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));

        setContentView(root);
        fadeIn(root);

        new Thread(() -> {
            SystemStorageResult result = collectSystemStorageResult(new SystemAnalyzeCallback() {
                @Override public void live(int done, int total, String appName,
                                           long code, long data, long cache) {
                    runOnUiThread(() -> {
                        ring.setLiveCount(done, "apps");
                        processed.setText("Apps scanned: " + done + " / " + total);
                        current.setText(appName == null ? "Reading Android storage statistics…" :
                                "Scanning: " + appName);
                        appCode.setText("App code: " + format(code));
                        appData.setText("App data: " + format(data));
                        appCache.setText("App cache: " + format(cache));
                    });
                }
            });
            runOnUiThread(() -> {
                ring.setDone();
                processed.setText("Apps scanned: " + result.apps.size());
                current.setText("Analysis complete");
                appCode.setText("App code: " + format(result.appCodeBytes));
                appData.setText("App data: " + format(result.appDataBytes));
                appCache.setText("App cache: " + format(result.appCacheBytes));
                sys.setText("Android/System/Reserved: " + format(result.systemReservedBytes));
                showSystemAnalyzerResult(result);
            });
        }, "sts-system-analyzer").start();
    }

    private SystemStorageResult collectSystemStorageResult(SystemAnalyzeCallback cb) {
        SystemStorageResult result = new SystemStorageResult();
        long[] st = storage();
        result.usedBytes = st[1];

        try {
            StorageStatsManager mgr = (StorageStatsManager) getSystemService(STORAGE_STATS_SERVICE);
            StorageStats userStats = mgr.queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle());
            result.appCodeBytes = Math.max(0L,userStats.getAppBytes());
            result.appDataBytes = Math.max(0L,userStats.getDataBytes());
            result.appCacheBytes = Math.max(0L,userStats.getCacheBytes());
        } catch (Exception ignored) {}

        try {
            PackageManager pm = getPackageManager();
            StorageStatsManager mgr = (StorageStatsManager) getSystemService(STORAGE_STATS_SERVICE);
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            int total = apps.size();
            int done = 0;
            cb.live(0,total,"Starting app scan",result.appCodeBytes,result.appDataBytes,result.appCacheBytes);

            for (ApplicationInfo ai : apps) {
                done++;
                String labelText = ai.packageName;
                try {
                    CharSequence label = pm.getApplicationLabel(ai);
                    if (label != null && label.length() > 0) labelText = label.toString();

                    StorageStats ss = mgr.queryStatsForPackage(StorageManager.UUID_DEFAULT,
                            ai.packageName, Process.myUserHandle());
                    long code = Math.max(0L,ss.getAppBytes());
                    long data = Math.max(0L,ss.getDataBytes());
                    long cache = Math.max(0L,ss.getCacheBytes());
                    long totalBytes = code + data + cache;
                    if (totalBytes > 0) {
                        boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        result.apps.add(new AppStorageEntry(
                                labelText, ai.packageName, code, data, cache, totalBytes, system));
                    }
                } catch (Exception ignored) {}

                if (done == total || done % 2 == 0) {
                    cb.live(done,total,labelText,result.appCodeBytes,result.appDataBytes,result.appCacheBytes);
                }
            }
        } catch (Exception ignored) {}

        long visible = cachedAnalytics != null ? cachedAnalytics.visibleBytes : 0L;
        result.visibleBytes = visible;
        result.systemReservedBytes = Math.max(0L, result.usedBytes - visible -
                result.appCodeBytes - result.appDataBytes - result.appCacheBytes);

        Collections.sort(result.apps, (a,b) -> Long.compare(b.totalBytes,a.totalBytes));
        return result;
    }

    private void showSystemAnalyzerResult(SystemStorageResult result) {
        rememberCurrentScroll();
        activeSystemResult = result;
        activeToolResult = null;
        activeAppDetailResult = null;
        activeVisibleCategory = null;
        getWindow().getDecorView().setTag("systemAnalyzerResult");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(16),dp(22),dp(16),dp(28));

        root.addView(text("Apps & System",28,INK,true));
        root.addView(space(5));
        root.addView(text("Hidden/private storage breakdown • Android-reported statistics",12,MUTED,false));
        root.addView(space(16));

        LinearLayout totals = card();
        totals.setPadding(dp(18),dp(16),dp(18),dp(16));
        totals.addView(systemRow("Installed app code", result.appCodeBytes, Color.rgb(82,88,110)));
        totals.addView(space(7));
        totals.addView(systemRow("Private app data", result.appDataBytes, PURPLE));
        totals.addView(space(7));
        totals.addView(systemRow("App cache", result.appCacheBytes, TEAL));
        totals.addView(space(7));
        totals.addView(systemRow("Android / System / Reserved", result.systemReservedBytes, ROSE));
        totals.addView(space(9));
        totals.addView(text("System/Reserved एक estimate है। Android कुछ OEM/reserved/snapshot storage को third-party apps से पूरी तरह अलग नहीं बताता।",11,MUTED,false));
        root.addView(totals,matchWrap());

        root.addView(space(14));
        TextView systemSettings = actionButton("OPEN ANDROID STORAGE SETTINGS",PURPLE);
        touch(systemSettings);
        systemSettings.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
        root.addView(systemSettings,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)));

        root.addView(section("Apps using most storage"));
        String appSortKey = "sort:systemApps";
        sortApps(result, sortMode(appSortKey, 0));
        TextView appSort = sortControl(appSortKey, APP_SORT_OPTIONS, 0, () -> {
            resetSavedScroll("systemResult");
            showSystemAnalyzerResult(result);
        });
        root.addView(appSort, matchWrap());
        root.addView(space(10));

        int limit=Math.min(60,result.apps.size());
        for(int i=0;i<limit;i++){
            AppStorageEntry e=result.apps.get(i);
            LinearLayout c=card();
            c.setPadding(dp(15),dp(13),dp(15),dp(13));

            LinearLayout top=row();
            top.setGravity(Gravity.CENTER_VERTICAL);
            try {
                Drawable d=getPackageManager().getApplicationIcon(e.packageName);
                ImageView iv=new ImageView(this);
                iv.setImageDrawable(d);
                top.addView(iv,new LinearLayout.LayoutParams(dp(38),dp(38)));
                top.addView(spaceH(10));
            } catch(Exception ignored){}
            LinearLayout labels=column();
            labels.addView(text(e.appName,14,INK,true));
            labels.addView(text(e.systemApp ? "System app" : "User app",10,e.systemApp?MUTED:PURPLE,false));
            top.addView(labels,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            top.addView(text(format(e.totalBytes),14,PURPLE,true));
            c.addView(top,matchWrap());

            c.addView(space(9));
            c.addView(text("App " + format(e.codeBytes) + "  •  Data " + format(e.dataBytes) +
                    "  •  Cache " + format(e.cacheBytes),11,MUTED,false));

            c.addView(space(9));
            LinearLayout actions=row();
            TextView details=pill("DETAILS",Color.rgb(18,145,123),Color.rgb(228,252,248));
            TextView manage=pill("APP STORAGE",PURPLE,Color.rgb(239,236,255));
            touch(details);
            touch(manage);
            details.setOnClickListener(v -> showAppDetailLoading(e));
            manage.setOnClickListener(v -> openAppStorageSettings(e.packageName));
            actions.addView(details,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            actions.addView(spaceH(8));
            actions.addView(manage,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            c.addView(actions,matchWrap());
            c.setOnClickListener(v -> showAppDetailLoading(e));
            touch(c);
            root.addView(c,matchWrap());
            root.addView(space(9));
        }

        root.addView(space(10));
        TextView back=actionButton("BACK",Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> showHome());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));

        scroll.addView(root);
        setContentView(scroll);
        bindScrollPosition("systemResult", scroll);
        fadeIn(root);
    }

    private void showAppDetailLoading(AppStorageEntry entry){
        beginNonScrollScreen();
        getWindow().getDecorView().setTag("appDetailLoading");
        LinearLayout root=column();
        root.setPadding(dp(18),dp(24),dp(18),dp(28));
        root.setBackgroundColor(BG);

        LinearLayout header=row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        try{
            Drawable d=getPackageManager().getApplicationIcon(entry.packageName);
            ImageView iv=new ImageView(this);
            iv.setImageDrawable(d);
            header.addView(iv,new LinearLayout.LayoutParams(dp(50),dp(50)));
            header.addView(spaceH(12));
        }catch(Exception ignored){}
        LinearLayout labels=column();
        labels.addView(text(entry.appName,24,INK,true));
        labels.addView(text(entry.packageName,10,MUTED,false));
        header.addView(labels,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        root.addView(header,matchWrap());
        root.addView(space(24));

        ScanRing ring=new ScanRing(this);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(190),dp(190));
        rp.gravity=Gravity.CENTER_HORIZONTAL;
        root.addView(ring,rp);
        root.addView(space(14));

        TextView status=centerText("Visible/shared files map कर रहे हैं…",13,MUTED,false);
        root.addView(status,matchWrap());

        root.addView(space(18));
        LinearLayout note=card();
        note.setPadding(dp(16),dp(15),dp(16),dp(15));
        note.addView(text("Private app data",14,INK,true));
        note.addView(space(5));
        note.addView(text("Normal Android mode में private folder की individual files पढ़ना blocked है। लेकिन exact private-data size और safe/unsafe action नीचे दिखेगा।",12,MUTED,false));
        root.addView(note,matchWrap());

        Space flex=new Space(this);
        root.addView(flex,new LinearLayout.LayoutParams(1,0,1f));
        TextView back=actionButton("BACK",Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> {
            if(activeSystemResult!=null) showSystemAnalyzerResult(activeSystemResult);
            else showHome();
        });
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));

        setContentView(root);
        fadeIn(root);

        new Thread(() -> {
            AppDetailResult result=collectAppDetail(entry);
            runOnUiThread(() -> {
                ring.setDone();
                showAppDetailResult(result);
            });
        },"sts-app-detail").start();
    }

    private AppDetailResult collectAppDetail(AppStorageEntry entry){
        AppDetailResult result=new AppDetailResult(entry);
        File root=Environment.getExternalStorageDirectory();
        Set<String> seen=new HashSet<>();
        scanAppVisibleFiles(root,seen,0,result);
        return result;
    }

    private void scanAppVisibleFiles(File f,Set<String> seen,int depth,AppDetailResult result){
        if(f==null||depth>24) return;
        String path;
        try{ path=f.getCanonicalPath(); }catch(Exception e){ path=f.getAbsolutePath(); }
        if(!seen.add(path)) return;
        String low=path.toLowerCase(Locale.ROOT);
        if(low.contains("/sts_trash/")) return;

        if(f.isDirectory()){
            File[] children;
            try{ children=f.listFiles(); }catch(Exception e){ children=null; }
            if(children==null) return;
            for(File x:children) scanAppVisibleFiles(x,seen,depth+1,result);
            return;
        }

        if(!belongsToAppVisible(path,result.entry.packageName,result.entry.appName)) return;

        long len=Math.max(0L,f.length());
        AppVisibleCategory cat=visibleCategoryForFile(f,low);
        AppFileInfo info=describeAppFile(f);
        result.add(cat,new AppVisibleFile(f,len,info));
    }

    private boolean belongsToAppVisible(String path,String pkg,String appName){
        String p=path.replace('\\','/').toLowerCase(Locale.ROOT);
        String pl=pkg==null?"":pkg.toLowerCase(Locale.ROOT);
        if(pl.length()>0){
            if(p.contains("/android/media/"+pl+"/")) return true;
            if(p.contains("/android/data/"+pl+"/")) return true;
            if(p.contains("/android/obb/"+pl+"/")) return true;
        }

        if("com.whatsapp".equalsIgnoreCase(pkg)){
            return p.contains("/whatsapp/") && !p.contains("whatsapp business");
        }
        if("com.whatsapp.w4b".equalsIgnoreCase(pkg)){
            return p.contains("/whatsapp business/") || p.contains("/android/media/com.whatsapp.w4b/");
        }
        if(pkg!=null && pkg.toLowerCase(Locale.ROOT).contains("telegram")){
            return p.contains("/telegram/");
        }
        if("com.facebook.katana".equalsIgnoreCase(pkg)){
            return p.contains("/facebook/");
        }
        if("com.instagram.android".equalsIgnoreCase(pkg)){
            return p.contains("/instagram/");
        }
        if("com.google.android.youtube".equalsIgnoreCase(pkg)){
            return p.contains("/youtube/");
        }
        if("com.spotify.music".equalsIgnoreCase(pkg)){
            return p.contains("/spotify/");
        }

        return false;
    }

    private AppVisibleCategory visibleCategoryForFile(File f,String lowPath){
        String n=f.getName().toLowerCase(Locale.ROOT);
        if(isImageFile(f)) return AppVisibleCategory.PHOTOS;
        if(isVideoFile(f)) return AppVisibleCategory.VIDEOS;
        if(isAudioFile(n)) return AppVisibleCategory.AUDIO;
        if(n.endsWith(".pdf")||n.endsWith(".doc")||n.endsWith(".docx")||
                n.endsWith(".xls")||n.endsWith(".xlsx")||n.endsWith(".ppt")||
                n.endsWith(".pptx")||n.endsWith(".txt")||n.endsWith(".csv"))
            return AppVisibleCategory.DOCUMENTS;
        if(isWhatsAppChatBackup(n,lowPath)||isDatabaseLike(n,lowPath)||
                lowPath.contains("/backup/")||lowPath.contains("/backups/")||
                n.contains("backup")||n.endsWith(".bak"))
            return AppVisibleCategory.BACKUPS;
        if(lowPath.contains("/cache/")||lowPath.contains("/.cache/")||
                lowPath.contains("/temp/")||lowPath.contains("/tmp/")||
                n.endsWith(".tmp")||n.endsWith(".temp")||n.endsWith(".log"))
            return AppVisibleCategory.JUNK;
        if(lowPath.contains("/download/")||lowPath.contains("/downloads/")||
                lowPath.contains("/offline/"))
            return AppVisibleCategory.DOWNLOADS;
        return AppVisibleCategory.OTHER;
    }

    private void showAppDetailResult(AppDetailResult result){
        rememberCurrentScroll();
        activeAppDetailResult=result;
        activeVisibleCategory=null;
        activeToolResult=null;
        getWindow().getDecorView().setTag("appDetail");

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root=column();
        root.setPadding(dp(16),dp(22),dp(16),dp(28));

        LinearLayout header=row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        try{
            Drawable d=getPackageManager().getApplicationIcon(result.entry.packageName);
            ImageView iv=new ImageView(this);
            iv.setImageDrawable(d);
            header.addView(iv,new LinearLayout.LayoutParams(dp(48),dp(48)));
            header.addView(spaceH(11));
        }catch(Exception ignored){}
        LinearLayout labels=column();
        labels.addView(text(result.entry.appName,23,INK,true));
        labels.addView(text(format(result.entry.totalBytes)+" total reported",12,PURPLE,true));
        labels.addView(text(result.entry.packageName,9,MUTED,false));
        header.addView(labels,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        root.addView(header,matchWrap());

        root.addView(section("Private storage"));
        root.addView(appStoragePart("App code",result.entry.codeBytes,
                "DO NOT DELETE",ROSE,Color.rgb(255,238,243),
                "यह installed app/program files हैं। इन्हें manually delete नहीं करना चाहिए। Space चाहिए तो app uninstall करें।",
                false,result.entry.packageName));
        root.addView(space(9));
        root.addView(appStoragePart("Private app data (total)",result.entry.dataBytes,
                "IMPORTANT • REVIEW",AMBER,Color.rgb(255,247,230),
                "यह कुल private data है। नीचे Login/Session अलग समझाया गया है; Android normal mode में उसके exact bytes अलग नहीं देता। CLEAR STORAGE से पूरा app reset हो सकता है।",
                true,result.entry.packageName));
        root.addView(space(9));

        LinearLayout loginPart = card();
        loginPart.setPadding(dp(16),dp(14),dp(16),dp(14));
        LinearLayout loginTop = row();
        loginTop.setGravity(Gravity.CENTER_VERTICAL);
        loginTop.addView(text("Login / Session",14,INK,true),
                new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        loginTop.addView(text("Included in Data",12,ROSE,true));
        loginPart.addView(loginTop,matchWrap());
        loginPart.addView(space(7));
        loginPart.addView(pill("PROTECTED • DO NOT CLEAR ALONE",ROSE,Color.rgb(255,238,243)));
        loginPart.addView(space(7));
        loginPart.addView(text("Account token, login session, account identity और authentication state private data में शामिल हो सकते हैं। Exact size Android अलग expose नहीं करता। Clear Storage करने पर login/logout और saved account state हट सकती है।",11,MUTED,false));
        root.addView(loginPart,matchWrap());

        root.addView(space(9));
        root.addView(appStoragePart("App cache",result.entry.cacheBytes,
                "SAFE TO CLEAN",Color.rgb(18,145,123),Color.rgb(228,252,248),
                "Temporary cache है। सामान्यतः safely clear किया जा सकता है और app जरूरत पर इसे फिर बनाएगा।",
                true,result.entry.packageName));

        LinearLayout locked=card();
        locked.setPadding(dp(16),dp(14),dp(16),dp(14));
        locked.addView(text("Private file-level detail",14,INK,true));
        locked.addView(space(6));
        locked.addView(pill("LOCKED IN NORMAL MODE",PURPLE,Color.rgb(239,236,255)));
        locked.addView(space(6));
        locked.addView(text("Android दूसरे app के private folder की individual files normal mode में नहीं दिखाता। Shizuku/Root Deep Access आने पर यहाँ database, offline files, internal cache आदि file-level में दिखेंगे।",11,MUTED,false));
        root.addView(space(9));
        root.addView(locked,matchWrap());

        root.addView(section("Visible / shared files"));
        if(result.visibleBytes<=0){
            LinearLayout empty=card();
            empty.setPadding(dp(16),dp(18),dp(16),dp(18));
            empty.addView(text("No app-linked shared files found",14,INK,true));
            empty.addView(space(5));
            empty.addView(text("इस app की files या तो private storage में हैं या shared folders से reliably map नहीं हुईं।",11,MUTED,false));
            root.addView(empty,matchWrap());
        }else{
            LinearLayout sum=card();
            sum.setPadding(dp(16),dp(15),dp(16),dp(15));
            sum.addView(text(format(result.visibleBytes)+" visible/shared",22,PURPLE,true));
            sum.addView(text(result.visibleFiles+" files mapped to this app",11,MUTED,false));
            root.addView(sum,matchWrap());
            root.addView(space(10));

            for(AppVisibleCategory cat:AppVisibleCategory.values()){
                long b=result.bytes(cat);
                int c=result.count(cat);
                if(b<=0) continue;
                LinearLayout item=card();
                item.setPadding(dp(15),dp(13),dp(15),dp(13));
                LinearLayout rr=row();
                rr.setGravity(Gravity.CENTER_VERTICAL);
                rr.addView(text("●",18,cat.color,true),new LinearLayout.LayoutParams(dp(24),ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout ll=column();
                ll.addView(text(cat.label,13,INK,true));
                ll.addView(text(c+" files",10,MUTED,false));
                rr.addView(ll,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
                rr.addView(text(format(b),13,cat.color,true));
                item.addView(rr,matchWrap());
                item.addView(space(7));
                item.addView(text(appCategoryGuidance(cat),11,MUTED,false));
                item.addView(space(8));
                TextView view=pill("VIEW FILES",PURPLE,Color.rgb(239,236,255));
                touch(view);
                view.setOnClickListener(v -> showAppVisibleFiles(result,cat));
                item.addView(view);
                item.setOnClickListener(v -> showAppVisibleFiles(result,cat));
                touch(item);
                root.addView(item,matchWrap());
                root.addView(space(9));
            }
        }

        root.addView(space(8));
        TextView settings=actionButton("OPEN APP STORAGE SETTINGS",PURPLE);
        touch(settings);
        settings.setOnClickListener(v -> openAppStorageSettings(result.entry.packageName));
        root.addView(settings,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)));
        root.addView(space(9));

        TextView back=actionButton("BACK",Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> {
            if(activeSystemResult!=null) showSystemAnalyzerResult(activeSystemResult);
            else showHome();
        });
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));

        scroll.addView(root);
        setContentView(scroll);
        bindScrollPosition("appDetail:" + result.entry.packageName, scroll);
        fadeIn(root);
    }

    private LinearLayout appStoragePart(String title,long bytes,String status,int color,int bg,
                                        String explanation,boolean settingsAction,String pkg){
        LinearLayout c=card();
        c.setPadding(dp(16),dp(14),dp(16),dp(14));
        LinearLayout top=row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(title,14,INK,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        top.addView(text(format(bytes),14,color,true));
        c.addView(top,matchWrap());
        c.addView(space(7));
        c.addView(pill(status,color,bg));
        c.addView(space(7));
        c.addView(text(explanation,11,MUTED,false));
        if(settingsAction && bytes>0){
            c.addView(space(9));
            TextView manage=pill("MANAGE IN ANDROID",PURPLE,Color.rgb(239,236,255));
            touch(manage);
            manage.setOnClickListener(v -> openAppStorageSettings(pkg));
            c.addView(manage);
        }
        return c;
    }

    private String appCategoryGuidance(AppVisibleCategory cat){
        if(cat==AppVisibleCategory.JUNK) return "Temporary/cache-like files • usually safe, but review list first.";
        if(cat==AppVisibleCategory.BACKUPS) return "Backup/database files • current backup may be protected; old backup review before delete.";
        if(cat==AppVisibleCategory.PHOTOS||cat==AppVisibleCategory.VIDEOS||
                cat==AppVisibleCategory.AUDIO||cat==AppVisibleCategory.DOCUMENTS)
            return "User content • preview/open करके ही delete करें.";
        if(cat==AppVisibleCategory.DOWNLOADS) return "Offline/downloaded content • delete करने पर app में offline access खत्म हो सकता है.";
        return "App-linked shared files • type/status देखकर review करें.";
    }

    private void showAppVisibleFiles(AppDetailResult result,AppVisibleCategory cat){
        rememberCurrentScroll();
        activeScanSummary = null;
        activeScanGalleryMode = -1;
        activeAppDetailResult=result;
        activeVisibleCategory=cat;
        activeToolResult=null;
        getWindow().getDecorView().setTag("appVisibleFiles");

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root=column();
        root.setPadding(dp(16),dp(22),dp(16),dp(28));

        String scrollKey="appFiles:" + result.entry.packageName + ":" + cat.name();
        String sortKey="sort:" + scrollKey;
        String selectionKey="sel:" + scrollKey;

        root.addView(text(result.entry.appName+" • "+cat.label,24,INK,true));
        root.addView(space(5));
        root.addView(text(result.count(cat)+" files • "+format(result.bytes(cat)),12,cat.color,true));
        root.addView(space(10));

        sortAppVisibleFiles(result, sortMode(sortKey, 0));
        TextView fileSort = sortControl(sortKey, FILE_SORT_OPTIONS, 0, () -> {
            resetSavedScroll(scrollKey);
            showAppVisibleFiles(result, cat);
        });
        root.addView(fileSort, matchWrap());

        List<File> selectableFiles=new ArrayList<>();
        for(AppVisibleFile vf:result.files){
            if(vf.category==cat && !vf.info.protectedFile) selectableFiles.add(vf.file);
        }

        root.addView(space(8));
        root.addView(galleryControls(selectionKey, selectableFiles,
                () -> showAppVisibleFiles(result,cat),
                "MOVE SELECTED TO STS TRASH",
                () -> moveSelectedAppFilesToTrash(result,cat,selectionKey)), matchWrap());

        root.addView(section("Preview Grid"));
        LinearLayout gallery=column();
        List<LinearLayout> cells=new ArrayList<>();
        int shown=0;
        int limit=160;
        for(AppVisibleFile vf:result.files){
            if(vf.category!=cat) continue;
            if(shown>=limit) break;
            shown++;
            cells.add(galleryFileCard(vf.file,vf.bytes,selectionKey,
                    !vf.info.protectedFile,vf.info,cat.color,
                    () -> showAppVisibleFiles(result,cat)));
        }
        addGalleryCells(gallery,cells);
        root.addView(gallery,matchWrap());

        if(result.count(cat)>shown){
            root.addView(space(10));
            root.addView(centerText("Showing first "+shown+" of "+result.count(cat)+
                    " • Select All applies to all selectable files",10,MUTED,false));
        }

        root.addView(space(12));
        TextView back=actionButton("BACK",Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> showAppDetailResult(result));
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));

        scroll.addView(root);
        setContentView(scroll);
        bindScrollPosition(scrollKey, scroll);
        fadeIn(root);
    }

    private void moveSelectedAppFilesToTrash(AppDetailResult result,AppVisibleCategory cat,String selectionKey){
        Set<String> selected=new HashSet<>(selectionSet(selectionKey));
        List<AppVisibleFile> targets=new ArrayList<>();
        long bytes=0L;
        for(AppVisibleFile vf:result.files){
            if(vf.category!=cat || !selected.contains(vf.file.getAbsolutePath()) || vf.info.protectedFile) continue;
            targets.add(vf);
            bytes+=vf.bytes;
        }
        if(targets.isEmpty()) return;

        long total=bytes;
        new AlertDialog.Builder(this)
                .setTitle("Move selected to STS Trash?")
                .setMessage(targets.size()+" files • "+format(total)+
                        "\n\n7 दिन तक Restore किया जा सकता है। Protected/current backup selected नहीं होंगे।")
                .setNegativeButton("CANCEL",null)
                .setPositiveButton("MOVE TO TRASH",(d,w)->{
                    List<AppVisibleFile> done=new ArrayList<>();
                    long moved=0L;
                    for(AppVisibleFile vf:targets){
                        if(moveToTrash(vf.file)){
                            done.add(vf);
                            moved+=vf.bytes;
                        }
                    }
                    for(AppVisibleFile vf:done) result.remove(vf);
                    clearSelection(selectionKey);
                    long finalMoved=moved;
                    new AlertDialog.Builder(this)
                            .setTitle("Moved to STS Trash")
                            .setMessage(done.size()+" files • "+format(finalMoved))
                            .setPositiveButton("OK",(x,y)->showAppVisibleFiles(result,cat))
                            .show();
                }).show();
    }

    private void confirmAppVisibleTrash(AppDetailResult result,AppVisibleCategory cat,AppVisibleFile vf){
        if(vf.info.protectedFile){
            showFileDetails(vf.file,vf.info);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Move to STS Trash?")
                .setMessage(vf.file.getName()+"\n"+format(vf.bytes)+"\n\n"+vf.info.explanation+
                        "\n\n7 दिन तक Restore किया जा सकता है।")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("MOVE TO TRASH",(d,w) -> {
                    if(moveToTrash(vf.file)){
                        result.remove(vf);
                        showAppVisibleFiles(result,cat);
                    }else{
                        new AlertDialog.Builder(this).setTitle("Move failed")
                                .setMessage("File को STS Trash में move नहीं किया जा सका।")
                                .setPositiveButton("OK",null).show();
                    }
                }).show();
    }

    private LinearLayout systemRow(String label,long bytes,int color){
        LinearLayout r=row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot=text("●",18,color,true);
        r.addView(dot,new LinearLayout.LayoutParams(dp(24),ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(text(label,13,INK,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        r.addView(text(format(bytes),13,color,true));
        return r;
    }

    private void openAppStorageSettings(String packageName){
        try{
            Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:"+packageName));
            startActivity(i);
        }catch(Exception e){
            try{ startActivity(new Intent(Settings.ACTION_SETTINGS)); }catch(Exception ignored){}
        }
    }

    private void openTool(String type) {
        haptic();
        if (Build.VERSION.SDK_INT >= 30 && !hasAllFilesAccess()) {
            pendingToolAfterAccess = type;
            requestToolAccess(type);
            return;
        }
        showToolScan(type);
    }

    private String toolTitle(String type) {
        if ("junk".equals(type)) return "Junk & Cache";
        if ("large".equals(type)) return "Large Files";
        if ("duplicates".equals(type)) return "Duplicates";
        if ("residual".equals(type)) return "Residual";
        if ("media".equals(type)) return "Media";
        if ("downloads".equals(type)) return "Downloads & Offline";
        if ("backups".equals(type)) return "Backups & Databases";
        return "Storage Tool";
    }

    private int toolAccent(String type) {
        if ("junk".equals(type)) return TEAL;
        if ("large".equals(type)) return AMBER;
        if ("duplicates".equals(type)) return BLUE;
        if ("residual".equals(type)) return ROSE;
        if ("media".equals(type)) return Color.rgb(170,82,205);
        if ("downloads".equals(type)) return Color.rgb(244,139,45);
        if ("backups".equals(type)) return Color.rgb(139,93,210);
        return PURPLE;
    }

    private void requestToolAccess(String type) {
        new AlertDialog.Builder(this)
                .setTitle(toolTitle(type) + " Access")
                .setMessage("इस tool को पूरे shared storage में real scan करने के लिए Full Storage Access चाहिए। Permission ON करके वापस आते ही यह tool अपने-आप scan शुरू करेगा।")
                .setNegativeButton("Cancel", (d,w) -> pendingToolAfterAccess = null)
                .setPositiveButton("ALLOW", (d,w) -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    }
                }).show();
    }

    private void showToolScan(String type) {
        beginNonScrollScreen();
        getWindow().getDecorView().setTag("toolScan");
        LinearLayout root = column();
        root.setPadding(dp(20), dp(26), dp(20), dp(28));
        root.setBackgroundColor(BG);

        int accent = toolAccent(type);
        TextView title = text(toolTitle(type), 28, INK, true);
        String explain = "junk".equals(type) ? "Safe temporary/cache files scan हो रहे हैं"
                : "large".equals(type) ? "100 MB से बड़ी files scan हो रही हैं"
                : "duplicates".equals(type) ? "Same-size files का SHA-256 hash compare हो रहा है"
                : "residual".equals(type) ? "Empty folders और old residual/temp candidates scan हो रहे हैं"
                : "media".equals(type) ? "Photos, videos और audio files scan हो रही हैं"
                : "downloads".equals(type) ? "Downloads और offline content scan हो रहा है"
                : "Backups और database files scan हो रही हैं";
        root.addView(title);
        root.addView(space(5));
        root.addView(text(explain, 13, MUTED, false));
        root.addView(space(24));

        ScanRing ring = new ScanRing(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(205), dp(205));
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(ring, rlp);

        TextView path = text("Preparing…", 12, MUTED, false);
        path.setGravity(Gravity.CENTER);
        path.setMaxLines(2);
        root.addView(space(9));
        root.addView(path, matchWrap());

        root.addView(space(18));
        LinearLayout live = card();
        live.setPadding(dp(18), dp(15), dp(18), dp(15));
        TextView scanned = text("0 files scanned", 16, INK, true);
        TextView analyzed = text("0 B analyzed", 13, MUTED, false);
        TextView found = text("0 candidates", 14, accent, true);
        TextView reclaim = text("0 B found", 13, MUTED, false);
        live.addView(text("LIVE " + toolTitle(type).toUpperCase(Locale.ROOT), 12, accent, true));
        live.addView(space(8));
        live.addView(scanned);
        live.addView(space(4));
        live.addView(analyzed);
        live.addView(space(8));
        live.addView(found);
        live.addView(space(4));
        live.addView(reclaim);
        root.addView(live, matchWrap());

        Space flex = new Space(this);
        root.addView(flex, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView back = actionButton("BACK", Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> handleBack());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        setContentView(root);
        fadeIn(root);

        scanTool(type, new ToolCallback() {
            @Override public void live(String currentPath, int files, long bytes, int items, long itemBytes) {
                runOnUiThread(() -> {
                    ring.setLiveFiles(files);
                    path.setText(shortPath(currentPath));
                    scanned.setText(files + " files scanned");
                    analyzed.setText(format(bytes) + " analyzed");
                    found.setText(items + ("duplicates".equals(type) ? " duplicate copies" : " candidates"));
                    reclaim.setText(format(itemBytes) + ("large".equals(type) ? " listed" : " reclaimable"));
                });
            }

            @Override public void done(ToolResult result) {
                runOnUiThread(() -> {
                    ring.setDone();
                    showToolResult(result);
                });
            }
        });
    }

    private void showToolResult(ToolResult result) {
        rememberCurrentScroll();
        activeScanSummary = null;
        activeScanGalleryMode = -1;
        activeToolResult = result;
        getWindow().getDecorView().setTag("toolResult");

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(22), dp(16), dp(28));

        int accent = toolAccent(result.type);
        String sortKey = "sort:tool:" + result.type;
        String selectionKey = "sel:tool:" + result.type;
        String scrollKey = "toolResult:" + result.type;
        sortToolItems(result, sortMode(sortKey, 0));

        root.addView(text(toolTitle(result.type), 28, INK, true));
        root.addView(space(5));
        String sub = "duplicates".equals(result.type)
                ? result.groups + " duplicate groups • original/keep protected"
                : result.scannedFiles + " files checked • " + format(result.scannedBytes) + " analyzed";
        root.addView(text(sub, 13, MUTED, false));
        root.addView(space(14));

        LinearLayout summary = card();
        summary.setPadding(dp(16), dp(14), dp(16), dp(14));
        summary.addView(text(result.items.size() + " items found", 16, INK, true));
        summary.addView(space(4));
        summary.addView(text(format(result.totalBytes), 24, accent, true));
        summary.addView(space(3));
        summary.addView(text("Tap preview to open • tap ○/✓ or long-press to select", 10, MUTED, false));
        root.addView(summary, matchWrap());

        if (result.items.isEmpty()) {
            root.addView(space(18));
            LinearLayout empty = card();
            empty.setPadding(dp(18), dp(22), dp(18), dp(22));
            empty.addView(centerText("✓", 38, TEAL, true));
            empty.addView(space(8));
            empty.addView(centerText("इस category में अभी कुछ नहीं मिला", 15, INK, true));
            root.addView(empty, matchWrap());
        } else {
            root.addView(space(10));
            TextView sort = sortControl(sortKey, FILE_SORT_OPTIONS, 0, () -> {
                resetSavedScroll(scrollKey);
                showToolResult(result);
            });
            root.addView(sort, matchWrap());

            List<File> selectableFiles = new ArrayList<>();
            for (ToolItem item : result.items) {
                AppFileInfo info = item.directory ? AppFileInfo.folder() : describeAppFile(item.file);
                if (!info.protectedFile) selectableFiles.add(item.file);
            }

            root.addView(space(8));
            String actionLabel = ("junk".equals(result.type) || "residual".equals(result.type))
                    ? "CLEAN SELECTED"
                    : "MOVE SELECTED TO STS TRASH";
            root.addView(galleryControls(selectionKey, selectableFiles, () -> showToolResult(result),
                    actionLabel, () -> performSelectedToolAction(result, selectionKey)), matchWrap());

            root.addView(section("Preview Grid"));
            LinearLayout gallery = column();
            List<LinearLayout> cells = new ArrayList<>();
            int limit = Math.min(160, result.items.size());
            for (int i=0; i<limit; i++) {
                ToolItem item = result.items.get(i);
                AppFileInfo info = item.directory ? AppFileInfo.folder() : describeAppFile(item.file);
                boolean selectable = !info.protectedFile;
                cells.add(galleryFileCard(item.file, item.bytes, selectionKey,
                        selectable, info, accent, () -> showToolResult(result)));
            }
            addGalleryCells(gallery, cells);
            root.addView(gallery, matchWrap());

            if (result.items.size() > limit) {
                root.addView(space(10));
                root.addView(centerText("Showing first " + limit + " of " + result.items.size() +
                        " • Select All applies to all safe/selectable items", 10, MUTED, false));
            }
        }

        if (("junk".equals(result.type) || "residual".equals(result.type) || "duplicates".equals(result.type))
                && !result.items.isEmpty()) {
            root.addView(space(16));
            String label = "duplicates".equals(result.type) ? "MOVE ALL DUPLICATE COPIES"
                    : "residual".equals(result.type) ? "CLEAN ALL SAFE RESIDUAL"
                    : "CLEAN ALL SAFE JUNK";
            TextView clean = actionButton(label, accent);
            touch(clean);
            clean.setOnClickListener(v -> confirmCleanToolResult(result));
            root.addView(clean, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        }

        root.addView(space(10));
        TextView rescan = actionButton("RESCAN", Color.WHITE);
        rescan.setTextColor(PURPLE);
        touch(rescan);
        rescan.setOnClickListener(v -> showToolScan(result.type));
        root.addView(rescan, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        root.addView(space(8));
        TextView back = actionButton("BACK", Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> handleBack());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        scroll.addView(root);
        setContentView(scroll);
        bindScrollPosition(scrollKey, scroll);
        fadeIn(root);
    }

    private void performSelectedToolAction(ToolResult result, String selectionKey) {
        Set<String> selected = new HashSet<>(selectionSet(selectionKey));
        List<ToolItem> targets = new ArrayList<>();
        long bytes = 0L;
        for (ToolItem item : result.items) {
            if (!selected.contains(item.file.getAbsolutePath())) continue;
            AppFileInfo info = item.directory ? AppFileInfo.folder() : describeAppFile(item.file);
            if (info.protectedFile) continue;
            targets.add(item);
            bytes += Math.max(0L, item.bytes);
        }
        if (targets.isEmpty()) return;

        boolean directClean = "junk".equals(result.type) || "residual".equals(result.type);
        String message = directClean
                ? "Selected safe junk/residual items permanently clean होंगे।"
                : "Selected files STS Trash में जाएँगी और 7 दिन तक Restore की जा सकेंगी।";
        long totalBytes = bytes;
        new AlertDialog.Builder(this)
                .setTitle(directClean ? "Clean selected?" : "Move selected to STS Trash?")
                .setMessage(targets.size() + " items • " + format(totalBytes) + "\n\n" + message)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton(directClean ? "CLEAN" : "MOVE TO TRASH", (d,w) -> {
                    int ok = 0;
                    long freed = 0L;
                    List<ToolItem> done = new ArrayList<>();
                    for (ToolItem item : targets) {
                        boolean success;
                        try {
                            success = directClean ? item.file.delete() : moveToTrash(item.file);
                        } catch (Exception e) {
                            success = false;
                        }
                        if (success) {
                            ok++;
                            freed += item.bytes;
                            done.add(item);
                        }
                    }
                    result.items.removeAll(done);
                    result.totalBytes = Math.max(0L, result.totalBytes - freed);
                    clearSelection(selectionKey);
                    new AlertDialog.Builder(this)
                            .setTitle(directClean ? "Clean complete" : "Moved to STS Trash")
                            .setMessage(ok + " items • " + format(freed))
                            .setPositiveButton("OK", (x,y) -> showToolResult(result))
                            .show();
                }).show();
    }

    private AppFileInfo describeAppFile(File file) {
        String path = file.getAbsolutePath();
        String lowPath = path.toLowerCase(Locale.ROOT);
        String name = file.getName().toLowerCase(Locale.ROOT);
        String pkg = packageFromPath(path);
        String appName = resolveAppName(pkg, path);

        if (isWhatsAppChatBackup(name, lowPath)) {
            boolean dated = containsDate(name);
            boolean current = name.startsWith("msgstore.db.") || name.equals("msgstore.db");
            if (current && !dated) {
                return new AppFileInfo(appName, pkg, "Encrypted Chat Backup",
                        "CURRENT BACKUP • KEEP",
                        Color.rgb(18,145,123), Color.rgb(228,252,248),
                        "यह current/latest local chat backup है। Delete करने से restore option प्रभावित हो सकता है।",
                        true, true);
            }
            return new AppFileInfo(appName, pkg, "Encrypted Chat Backup",
                    "OLD BACKUP • REVIEW",
                    AMBER, Color.rgb(255,247,230),
                    "यह dated/older local chat backup है। Delete करने पर उस तारीख की local restore history खत्म हो सकती है।",
                    false, true);
        }

        if (isDatabaseLike(name, lowPath)) {
            boolean old = containsDate(name) || name.endsWith(".bak") || name.endsWith(".old") ||
                    lowPath.contains("/backup/") || lowPath.contains("/backups/");
            if (old) {
                return new AppFileInfo(appName, pkg, "App Database / Backup",
                        "BACKUP • REVIEW",
                        AMBER, Color.rgb(255,247,230),
                        "यह app database/backup file है। Delete करने से पुराने restore/history data पर असर पड़ सकता है।",
                        false, true);
            }
            return new AppFileInfo(appName, pkg, "Active App Database",
                    "IMPORTANT • KEEP",
                    Color.rgb(18,145,123), Color.rgb(228,252,248),
                    "यह app का active/current database लग रहा है। इसे delete करने से app data या history खराब हो सकती है।",
                    true, true);
        }

        if (isImageFile(file)) {
            return new AppFileInfo(appName, pkg, "Photo / Image",
                    "PERSONAL FILE • REVIEW", BLUE, Color.rgb(235,245,255),
                    "यह image file है। Preview करके ही delete करें।", false, false);
        }

        if (isVideoFile(file)) {
            return new AppFileInfo(appName, pkg, "Video",
                    "PERSONAL FILE • REVIEW", BLUE, Color.rgb(235,245,255),
                    "यह video file है। Play/preview करके ही delete करें।", false, false);
        }

        if (isAudioFile(name)) {
            return new AppFileInfo(appName, pkg, "Audio / Voice / Music",
                    "USER MEDIA • REVIEW", BLUE, Color.rgb(235,245,255),
                    "यह audio/media file है। Delete करने से saved audio या voice content हट सकता है।", false, false);
        }

        if (lowPath.contains("/cache/") || lowPath.contains("/.cache/") ||
                name.endsWith(".tmp") || name.endsWith(".temp")) {
            return new AppFileInfo(appName, pkg, "App Cache / Temporary File",
                    "CACHE • USUALLY SAFE", Color.rgb(18,145,123), Color.rgb(228,252,248),
                    "यह cache/temporary data है। App जरूरत पड़ने पर इसे दोबारा बना सकता है।", false, false);
        }

        if (name.endsWith(".apk")) {
            return new AppFileInfo(appName, pkg, "Android Installer (APK)",
                    "INSTALLER • REVIEW", AMBER, Color.rgb(255,247,230),
                    "यह APK installer file है। Installed app पर असर नहीं पड़ता, लेकिन future reinstall के लिए काम आ सकती है।",
                    false, false);
        }

        if (isArchiveFile(name)) {
            return new AppFileInfo(appName, pkg, "Archive / Compressed File",
                    "ARCHIVE • REVIEW", AMBER, Color.rgb(255,247,230),
                    "यह ZIP/RAR/7Z जैसी archive file है। Open करके contents पहचानने के बाद delete करें।",
                    false, false);
        }

        if (lowPath.contains("/offline/") || lowPath.contains("/downloads/") ||
                lowPath.contains("/download/")) {
            return new AppFileInfo(appName, pkg, "Downloaded / Offline Content",
                    "OFFLINE DATA • REVIEW", AMBER, Color.rgb(255,247,230),
                    "यह downloaded/offline content हो सकता है। Delete करने पर app में offline access खत्म हो सकता है।",
                    false, false);
        }

        if (lowPath.contains("/backup/") || lowPath.contains("/backups/") ||
                name.contains("backup") || name.endsWith(".bak")) {
            return new AppFileInfo(appName, pkg, "App Backup",
                    "BACKUP • REVIEW", AMBER, Color.rgb(255,247,230),
                    "यह backup file लग रही है। Delete करने पर restore history कम हो सकती है।",
                    false, true);
        }

        return new AppFileInfo(appName, pkg, fileTypeFromExtension(name),
                "UNKNOWN / APP FILE • REVIEW", PURPLE, Color.rgb(239,236,255),
                "यह app/storage file है। Type पूरी तरह सुरक्षित रूप से पहचान नहीं पाया, इसलिए delete से पहले OPEN/DETAILS से जांचें।",
                false, false);
    }

    private String packageFromPath(String path) {
        String normalized = path.replace('\\','/');
        String[] roots = {"/Android/media/", "/Android/data/", "/Android/obb/"};
        for (String root : roots) {
            int i = normalized.indexOf(root);
            if (i < 0) {
                i = normalized.toLowerCase(Locale.ROOT).indexOf(root.toLowerCase(Locale.ROOT));
            }
            if (i >= 0) {
                String tail = normalized.substring(i + root.length());
                int slash = tail.indexOf('/');
                String pkg = slash >= 0 ? tail.substring(0, slash) : tail;
                if (pkg.contains(".") && pkg.length() > 3) return pkg;
            }
        }
        return null;
    }

    private String resolveAppName(String pkg, String path) {
        if (pkg != null) {
            String known = knownPackageName(pkg);
            if (known != null) return known;
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence label = pm.getApplicationLabel(ai);
                if (label != null && label.length() > 0) return label.toString();
            } catch (Exception ignored) {}
        }

        String low = path.toLowerCase(Locale.ROOT);
        if (low.contains("whatsapp business")) return "WhatsApp Business";
        if (low.contains("/whatsapp/")) return "WhatsApp";
        if (low.contains("/telegram/")) return "Telegram";
        if (low.contains("/instagram/")) return "Instagram";
        if (low.contains("/facebook/")) return "Facebook";
        if (low.contains("/messenger/")) return "Messenger";
        if (low.contains("/snapchat/")) return "Snapchat";
        if (low.contains("/signal/")) return "Signal";
        if (low.contains("/youtube music/")) return "YouTube Music";
        if (low.contains("/youtube/")) return "YouTube";
        if (low.contains("/spotify/")) return "Spotify";
        if (pkg != null) return pkg;
        return "Device / App Storage";
    }

    private String knownPackageName(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.equals("com.whatsapp")) return "WhatsApp";
        if (p.equals("com.whatsapp.w4b")) return "WhatsApp Business";
        if (p.equals("org.telegram.messenger")) return "Telegram";
        if (p.equals("org.thunderdog.challegram")) return "Telegram X";
        if (p.equals("com.instagram.android")) return "Instagram";
        if (p.equals("com.facebook.katana")) return "Facebook";
        if (p.equals("com.facebook.orca")) return "Messenger";
        if (p.equals("com.android.chrome")) return "Google Chrome";
        if (p.equals("com.google.android.youtube")) return "YouTube";
        if (p.equals("com.google.android.apps.youtube.music")) return "YouTube Music";
        if (p.equals("com.google.android.apps.maps")) return "Google Maps";
        if (p.equals("com.snapchat.android")) return "Snapchat";
        if (p.equals("org.thoughtcrime.securesms")) return "Signal";
        if (p.equals("com.twitter.android") || p.equals("com.x.android")) return "X / Twitter";
        if (p.equals("com.zhiliaoapp.musically")) return "TikTok";
        if (p.equals("com.lemon.lvoverseas")) return "CapCut";
        if (p.equals("org.videolan.vlc")) return "VLC";
        if (p.startsWith("com.mxtech.videoplayer")) return "MX Player";
        if (p.equals("com.microsoft.teams")) return "Microsoft Teams";
        if (p.equals("us.zoom.videomeetings")) return "Zoom";
        if (p.equals("com.google.android.gm")) return "Gmail";
        if (p.equals("com.google.android.apps.docs")) return "Google Drive";
        if (p.equals("com.microsoft.skydrive")) return "OneDrive";
        if (p.equals("com.dropbox.android")) return "Dropbox";
        if (p.equals("com.spotify.music")) return "Spotify";
        if (p.equals("com.netflix.mediaclient")) return "Netflix";
        if (p.equals("com.amazon.avod.thirdpartyclient")) return "Prime Video";
        return null;
    }

    private Drawable appIcon(String pkg) {
        if (pkg == null) return null;
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isWhatsAppChatBackup(String name, String lowPath) {
        return lowPath.contains("whatsapp") && name.startsWith("msgstore") &&
                (name.contains(".crypt") || name.endsWith(".db"));
    }

    private boolean isDatabaseLike(String name, String lowPath) {
        return name.endsWith(".db") || name.endsWith(".sqlite") || name.endsWith(".sqlite3") ||
                name.matches(".*\\.crypt\\d+$") || lowPath.contains("/databases/");
    }

    private boolean containsDate(String name) {
        return name.matches(".*(19|20)\\d{2}[-_.](0[1-9]|1[0-2])[-_.]([0-2]\\d|3[01]).*");
    }

    private boolean isAudioFile(String name) {
        return name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".aac") ||
                name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".opus") ||
                name.endsWith(".flac") || name.endsWith(".amr");
    }

    private boolean isArchiveFile(String name) {
        return name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") ||
                name.endsWith(".tar") || name.endsWith(".gz") || name.endsWith(".tgz");
    }

    private String fileTypeFromExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length()-1) {
            return name.substring(dot+1).toUpperCase(Locale.ROOT) + " File";
        }
        return "App / Storage File";
    }

    private void showFileDetails(File file, AppFileInfo info) {
        StringBuilder b = new StringBuilder();
        b.append("App: ").append(info.appName).append("\n");
        if (info.packageName != null) b.append("Package: ").append(info.packageName).append("\n");
        b.append("Type: ").append(info.category).append("\n");
        b.append("Status: ").append(info.status).append("\n");
        b.append("Size: ").append(format(file.length())).append("\n\n");
        b.append(info.explanation).append("\n\n");
        b.append(file.getAbsolutePath());

        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setMessage(b.toString())
                .setNegativeButton("CLOSE", null)
                .setPositiveButton(info.protectedFile ? "KEEP" : "OPEN", (d,w) -> {
                    if (!info.protectedFile) openExternalFile(file);
                })
                .show();
    }

    private boolean isImageFile(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
                n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp") ||
                n.endsWith(".heic") || n.endsWith(".heif");
    }

    private boolean isVideoFile(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".mov") ||
                n.endsWith(".avi") || n.endsWith(".webm") || n.endsWith(".3gp") ||
                n.endsWith(".m4v");
    }

    private String mimeForFile(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length()-1) {
            String ext = name.substring(dot+1).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return "*/*";
    }

    private void openFoundFile(File file) {
        haptic();
        Object returnTag = getWindow().getDecorView().getTag();
        previewReturnTag = returnTag == null ? null : returnTag.toString();
        if (!file.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle("File not found")
                    .setMessage("यह file अब storage में मौजूद नहीं है।")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (isImageFile(file)) {
            showPhotoPreview(file);
        } else if (isVideoFile(file) || isAudioFile(file.getName().toLowerCase(Locale.ROOT))) {
            showMediaPreview(file, isVideoFile(file));
        } else if (isPdfFile(file)) {
            showPdfPreview(file);
        } else if (isTextFile(file)) {
            showTextPreview(file);
        } else {
            showInternalFilePage(file);
        }
    }

    private void showPhotoPreview(File file) {
        beginNonScrollScreen();
        getWindow().getDecorView().setTag("preview");
        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(12,14,20));
        root.setPadding(dp(12), dp(12), dp(12), dp(14));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = pill("‹ BACK", Color.WHITE, Color.rgb(40,43,54));
        touch(back);
        back.setOnClickListener(v -> returnFromPreview());
        top.addView(back);
        TextView name = text(file.getName(), 14, Color.WHITE, true);
        name.setPadding(dp(12),0,0,0);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top, matchWrap());
        root.addView(space(10));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        image.setBackgroundColor(Color.BLACK);
        try {
            image.setImageURI(Uri.fromFile(file));
        } catch (Exception ignored) {}
        root.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(space(10));
        TextView info = centerText(format(file.length()) + "  •  " + shortPath(file.getAbsolutePath()), 11, Color.LTGRAY, false);
        root.addView(info, matchWrap());

        root.addView(space(8));
        TextView external = actionButton("OPEN IN OTHER APP", PURPLE);
        touch(external);
        external.setOnClickListener(v -> openExternalFile(file));
        root.addView(external, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        setContentView(root);
        fadeIn(root);
    }

    private void showMediaPreview(File file, boolean videoMode) {
        beginNonScrollScreen();
        releasePreviewResources();
        getWindow().getDecorView().setTag("preview");

        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(12,14,20));
        root.setPadding(dp(12), dp(12), dp(12), dp(14));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = pill("‹ BACK", Color.WHITE, Color.rgb(40,43,54));
        touch(back);
        back.setOnClickListener(v -> returnFromPreview());
        top.addView(back);
        TextView name = text(file.getName(), 14, Color.WHITE, true);
        name.setPadding(dp(12),0,0,0);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top, matchWrap());
        root.addView(space(10));

        TextView status = centerText(videoMode ? "Loading video…" : "Loading audio…", 12, Color.LTGRAY, false);

        if (!videoMode) {
            LinearLayout audioHero = column();
            audioHero.setGravity(Gravity.CENTER);
            audioHero.setBackgroundColor(Color.rgb(22,25,34));
            audioHero.addView(centerText("♪", 64, TEAL, true));
            audioHero.addView(space(8));
            audioHero.addView(centerText(file.getName(), 15, Color.WHITE, true));
            root.addView(audioHero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));
            root.addView(space(8));
        }

        PlayerView playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setBackgroundColor(Color.BLACK);
        int playerHeight = videoMode ? 0 : dp(110);
        LinearLayout.LayoutParams playerLp = videoMode
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, playerHeight);
        root.addView(playerView, playerLp);

        root.addView(space(8));
        root.addView(status, matchWrap());
        root.addView(space(8));

        TextView external = actionButton("OPEN IN OTHER PLAYER", PURPLE);
        touch(external);
        external.setOnClickListener(v -> openExternalFile(file));
        root.addView(external, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        setContentView(root);
        fadeIn(root);

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            activePlayer = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(activePlayer);
            activePlayer.addListener(new Player.Listener() {
                private void syncScreenAwake() {
                    if (!videoMode || activePlayer == null) {
                        setVideoScreenAwake(false, playerView);
                        return;
                    }
                    int state = activePlayer.getPlaybackState();
                    boolean keepAwake = activePlayer.getPlayWhenReady() &&
                            (state == Player.STATE_READY || state == Player.STATE_BUFFERING);
                    setVideoScreenAwake(keepAwake, playerView);
                }

                @Override public void onPlaybackStateChanged(int state) {
                    syncScreenAwake();
                    if (state == Player.STATE_READY) {
                        status.setText((videoMode ? "VIDEO READY" : "AUDIO READY") + " • " + format(file.length()));
                    } else if (state == Player.STATE_BUFFERING) {
                        status.setText("Buffering… • " + format(file.length()));
                    } else if (state == Player.STATE_ENDED) {
                        setVideoScreenAwake(false, playerView);
                        status.setText("Playback complete • " + format(file.length()));
                    }
                }

                @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                    syncScreenAwake();
                }

                @Override public void onIsPlayingChanged(boolean isPlaying) {
                    syncScreenAwake();
                }

                @Override public void onPlayerError(PlaybackException error) {
                    setVideoScreenAwake(false, playerView);
                    status.setText("इस codec को device/player decode नहीं कर पा रहा • Other Player try करें");
                }
            });
            activePlayer.setMediaItem(MediaItem.fromUri(uri));
            activePlayer.prepare();
            activePlayer.setPlayWhenReady(true);
        } catch (Exception e) {
            status.setText("Player start नहीं हुआ • Other Player try करें");
        }
    }

    private boolean isPdfFile(File f) {
        return f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private boolean isTextFile(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".txt") || n.endsWith(".log") || n.endsWith(".json") ||
                n.endsWith(".xml") || n.endsWith(".csv") || n.endsWith(".md") ||
                n.endsWith(".html") || n.endsWith(".htm") || n.endsWith(".ini") ||
                n.endsWith(".conf") || n.endsWith(".properties");
    }

    private void showPdfPreview(File file) {
        beginNonScrollScreen();
        releasePreviewResources();
        getWindow().getDecorView().setTag("preview");

        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(20,22,30));
        root.setPadding(dp(10), dp(10), dp(10), dp(12));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = pill("‹ BACK", Color.WHITE, Color.rgb(40,43,54));
        touch(back);
        back.setOnClickListener(v -> returnFromPreview());
        top.addView(back);
        TextView name = text(file.getName(), 14, Color.WHITE, true);
        name.setPadding(dp(12),0,0,0);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top, matchWrap());
        root.addView(space(8));

        ImageView pageView = new ImageView(this);
        pageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pageView.setBackgroundColor(Color.WHITE);
        root.addView(pageView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView pageInfo = centerText("Opening PDF…", 12, Color.LTGRAY, false);
        root.addView(space(7));
        root.addView(pageInfo, matchWrap());

        LinearLayout controls = row();
        TextView prev = pill("‹ PREV", Color.WHITE, Color.rgb(55,58,72));
        TextView next = pill("NEXT ›", Color.WHITE, Color.rgb(55,58,72));
        controls.addView(prev, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        controls.addView(spaceH(8));
        controls.addView(next, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(space(7));
        root.addView(controls, matchWrap());

        setContentView(root);
        fadeIn(root);

        try {
            activePdfFd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            activePdfRenderer = new PdfRenderer(activePdfFd);
            final int[] index = {0};
            renderPdfPage(activePdfRenderer, index[0], pageView, pageInfo);

            prev.setOnClickListener(v -> {
                if (activePdfRenderer != null && index[0] > 0) {
                    index[0]--;
                    renderPdfPage(activePdfRenderer, index[0], pageView, pageInfo);
                }
            });
            next.setOnClickListener(v -> {
                if (activePdfRenderer != null && index[0] < activePdfRenderer.getPageCount()-1) {
                    index[0]++;
                    renderPdfPage(activePdfRenderer, index[0], pageView, pageInfo);
                }
            });
            touch(prev); touch(next);
        } catch (Exception e) {
            pageInfo.setText("PDF preview नहीं खुला • file damaged/encrypted हो सकती है");
        }
    }

    private void renderPdfPage(PdfRenderer renderer, int index, ImageView target, TextView info) {
        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(index);
            int width = Math.max(900, getResources().getDisplayMetrics().widthPixels - dp(20));
            float ratio = (float) page.getHeight() / Math.max(1, page.getWidth());
            int height = Math.max(1, Math.round(width * ratio));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            target.setImageBitmap(bitmap);
            info.setText("Page " + (index+1) + " / " + renderer.getPageCount());
        } catch (Exception e) {
            info.setText("Page render failed");
        } finally {
            if (page != null) page.close();
        }
    }

    private void showTextPreview(File file) {
        beginNonScrollScreen();
        releasePreviewResources();
        getWindow().getDecorView().setTag("preview");
        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(245,246,251));
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = pill("‹ BACK", PURPLE, Color.WHITE);
        touch(back);
        back.setOnClickListener(v -> returnFromPreview());
        top.addView(back);
        TextView name = text(file.getName(), 14, INK, true);
        name.setPadding(dp(12),0,0,0);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top, matchWrap());
        root.addView(space(8));

        ScrollView textScroll = new ScrollView(this);
        TextView body = text(readTextPreview(file), 12, INK, false);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setPadding(dp(10),dp(10),dp(10),dp(10));
        textScroll.addView(body);
        root.addView(textScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(space(8));
        TextView other = actionButton("OPEN IN OTHER APP", PURPLE);
        touch(other);
        other.setOnClickListener(v -> openExternalFile(file));
        root.addView(other, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        setContentView(root);
        fadeIn(root);
    }

    private String readTextPreview(File file) {
        StringBuilder out = new StringBuilder();
        int chars = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null && chars < 500000) {
                out.append(line).append('\n');
                chars += line.length()+1;
            }
            if (br.readLine() != null) out.append("\n… preview limited to first 500 KB of text");
        } catch (Exception e) {
            return "इस file को text के रूप में read नहीं किया जा सका।";
        }
        return out.toString();
    }

    private void showInternalFilePage(File file) {
        beginNonScrollScreen();
        releasePreviewResources();
        getWindow().getDecorView().setTag("preview");
        AppFileInfo info = describeAppFile(file);

        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(20), dp(18), dp(24));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = pill("‹ BACK", PURPLE, Color.WHITE);
        touch(back);
        back.setOnClickListener(v -> returnFromPreview());
        top.addView(back);
        root.addView(top, matchWrap());
        root.addView(space(20));

        root.addView(text(file.getName(), 22, INK, true));
        root.addView(space(10));
        root.addView(text("App: " + info.appName, 14, INK, true));
        root.addView(space(6));
        root.addView(text("Type: " + info.category, 13, MUTED, false));
        root.addView(space(6));
        root.addView(text("Size: " + format(file.length()), 13, MUTED, false));
        root.addView(space(10));
        root.addView(pill(info.status, info.statusColor, info.statusBg));
        root.addView(space(14));
        root.addView(text(info.explanation, 13, MUTED, false));
        root.addView(space(14));
        TextView path = text(file.getAbsolutePath(), 11, MUTED, false);
        path.setTextIsSelectable(true);
        root.addView(path);

        Space flex = new Space(this);
        root.addView(flex, new LinearLayout.LayoutParams(1,0,1f));

        TextView other = actionButton("OPEN IN COMPATIBLE APP", PURPLE);
        touch(other);
        other.setOnClickListener(v -> openExternalFile(file));
        root.addView(other, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        setContentView(root);
        fadeIn(root);
    }

    private void openExternalFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeForFile(file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("Open file")
                    .setMessage("इस file type को खोलने के लिए compatible app नहीं मिला।")
                    .setPositiveButton("OK", null).show();
        }
    }

    private void returnFromPreview() {
        haptic();
        releasePreviewResources();
        String target = previewReturnTag;
        previewReturnTag = null;

        if ("trash".equals(target)) {
            showTrashScreen();
        } else if ("scanGallery".equals(target) && activeScanSummary != null && activeScanGalleryMode >= 0) {
            showScanItemsGallery(activeScanSummary, activeScanGalleryMode);
        } else if ("appVisibleFiles".equals(target) && activeVisibleCategory != null && activeAppDetailResult != null) {
            showAppVisibleFiles(activeAppDetailResult, activeVisibleCategory);
        } else if ("toolResult".equals(target) && activeToolResult != null) {
            showToolResult(activeToolResult);
        } else if (activeVisibleCategory != null && activeAppDetailResult != null) {
            showAppVisibleFiles(activeAppDetailResult, activeVisibleCategory);
        } else if (activeToolResult != null) {
            showToolResult(activeToolResult);
        } else if (activeScanSummary != null && activeScanGalleryMode >= 0) {
            showScanItemsGallery(activeScanSummary, activeScanGalleryMode);
        } else if (activeAppDetailResult != null) {
            showAppDetailResult(activeAppDetailResult);
        } else {
            showHome();
        }
    }

    private void setVideoScreenAwake(boolean keepAwake, View playerView) {
        try {
            if (playerView != null) playerView.setKeepScreenOn(keepAwake);
            if (keepAwake) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Exception ignored) {}
    }

    private void releasePreviewResources() {
        setVideoScreenAwake(false, null);
        try {
            if (activePlayer != null) {
                activePlayer.stop();
                activePlayer.release();
                activePlayer = null;
            }
        } catch (Exception ignored) {}
        try {
            if (activePdfRenderer != null) {
                activePdfRenderer.close();
                activePdfRenderer = null;
            }
        } catch (Exception ignored) {}
        try {
            if (activePdfFd != null) {
                activePdfFd.close();
                activePdfFd = null;
            }
        } catch (Exception ignored) {}
    }

    private void confirmDeleteToolItem(ToolResult result, ToolItem item) {
        AppFileInfo info = describeAppFile(item.file);
        if (info.protectedFile) {
            showFileDetails(item.file, info);
            return;
        }
        String warning = "duplicates".equals(result.type)
                ? "यह duplicate copy STS Trash में जाएगी। Original/first copy रखी जाएगी।"
                : "यह file पहले STS Trash में जाएगी। 7 दिन तक Restore कर सकते हैं; उसके बाद auto-delete होगी।";
        new AlertDialog.Builder(this)
                .setTitle("Move to STS Trash?")
                .setMessage(warning + "\n\nApp: " + info.appName + "\nType: " + info.category +
                        "\nStatus: " + info.status + "\n\n" + item.file.getAbsolutePath() + "\n" + format(item.bytes))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("MOVE TO TRASH", (d,w) -> {
                    boolean ok = moveToTrash(item.file);
                    if (ok) {
                        result.items.remove(item);
                        result.totalBytes = Math.max(0, result.totalBytes - item.bytes);
                        showToolResult(result);
                    } else {
                        new AlertDialog.Builder(this).setTitle("Move failed")
                                .setMessage("इस file को STS Trash में move नहीं किया जा सका।")
                                .setPositiveButton("OK", null).show();
                    }
                }).show();
    }

    private void confirmCleanToolResult(ToolResult result) {
        String msg;
        if ("duplicates".equals(result.type)) {
            msg = "हर duplicate hash group की पहली/original copy रखी जाएगी। Current databases/backups जैसे protected app files auto-delete नहीं होंगे।";
        } else if ("residual".equals(result.type)) {
            msg = "केवल scan में मिले safe empty folders और non-personal old residual/temp candidates delete होंगे। Important app data protected रहेगा।";
        } else {
            msg = "केवल safe junk/cache candidates delete होंगे। Personal media और important app database/backup protected रहेंगे।";
        }

        int deletable = 0;
        long bytes = 0;
        for (ToolItem item : result.items) {
            if (item.directory || !describeAppFile(item.file).protectedFile) {
                deletable++;
                bytes += item.bytes;
            }
        }
        if (deletable == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Nothing safe to clean")
                    .setMessage("इस list में अभी सभी items protected/important हैं।")
                    .setPositiveButton("OK", null).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Clean")
                .setMessage(msg + "\n\nSelected: " + deletable + " items • " + format(bytes))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("CLEAN", (d,w) -> cleanToolResult(result))
                .show();
    }

    private void cleanToolResult(ToolResult result) {
        new Thread(() -> {
            long freed = 0;
            int count = 0;
            int failed = 0;
            List<ToolItem> copy = new ArrayList<>(result.items);
            boolean useTrash = "duplicates".equals(result.type);
            for (ToolItem item : copy) {
                try {
                    if (!item.directory && describeAppFile(item.file).protectedFile) continue;
                    long n = item.bytes;
                    boolean ok = useTrash ? moveToTrash(item.file) : item.file.delete();
                    if (ok) { freed += n; count++; }
                    else failed++;
                } catch (Exception e) { failed++; }
            }
            long f = freed;
            int c = count;
            int x = failed;
            runOnUiThread(() -> {
                if (useTrash) showTrashMovedDone(f, c, x);
                else showCleanDone(f, c, x);
            });
        }, "sts-tool-clean").start();
    }

    private File trashDir() {
        File base = getExternalFilesDir(null);
        File dir = new File(base != null ? base : getFilesDir(), "STS_Trash");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private boolean moveToTrash(File source) {
        if (source == null || !source.exists()) return false;
        File dir = trashDir();
        long now = System.currentTimeMillis();
        String safeName = source.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        File target = new File(dir, now + "_" + safeName);
        File meta = new File(dir, target.getName() + ".stsmeta");

        boolean moved = source.renameTo(target);
        if (!moved && source.isFile()) {
            moved = copyFile(source, target);
            if (moved && !source.delete()) {
                target.delete();
                moved = false;
            }
        }
        if (!moved) return false;

        try (FileWriter w = new FileWriter(meta)) {
            w.write(source.getAbsolutePath());
            w.write("\n");
            w.write(Long.toString(now));
            w.write("\n");
        } catch (Exception e) {
            // file remains safely in STS Trash even if metadata write fails
        }
        return true;
    }

    private boolean copyFile(File source, File target) {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[256*1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf,0,n);
            out.flush();
            return true;
        } catch (Exception e) {
            try { target.delete(); } catch (Exception ignored) {}
            return false;
        }
    }

    private String[] readTrashMeta(File meta) {
        String[] out = new String[]{"", "0"};
        try (BufferedReader br = new BufferedReader(new FileReader(meta))) {
            String p = br.readLine();
            String t = br.readLine();
            if (p != null) out[0] = p;
            if (t != null) out[1] = t;
        } catch (Exception ignored) {}
        return out;
    }

    private void showTrashScreen() {
        rememberCurrentScroll();
        activeScanSummary = null;
        activeScanGalleryMode = -1;
        releasePreviewResources();
        getWindow().getDecorView().setTag("trash");
        purgeExpiredTrash();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(16),dp(22),dp(16),dp(28));

        root.addView(text("STS Trash",28,INK,true));
        root.addView(space(5));
        root.addView(text("Preview • Select • Restore • Delete Forever • 7 days",13,MUTED,false));
        root.addView(space(14));

        File dir = trashDir();
        File[] metas = dir.listFiles((d,n) -> n.endsWith(".stsmeta"));
        if (metas == null) metas = new File[0];

        String sortKey = "sort:trash";
        String selectionKey = "sel:trash";
        sortTrashMeta(metas, sortMode(sortKey, 0));

        List<File> trashFiles = new ArrayList<>();
        for(File meta:metas){
            File data=trashDataFile(meta);
            if(data.exists()) trashFiles.add(data);
        }

        if (metas.length > 0) {
            TextView trashSort = sortControl(sortKey, TRASH_SORT_OPTIONS, 0, () -> {
                resetSavedScroll("trash");
                showTrashScreen();
            });
            root.addView(trashSort, matchWrap());
            root.addView(space(8));

            root.addView(galleryControls(selectionKey, trashFiles, this::showTrashScreen,
                    null, null), matchWrap());

            int selectedCount=0;
            long selectedSize=0L;
            Set<String> sel=selectionSet(selectionKey);
            for(File file:trashFiles){
                if(sel.contains(file.getAbsolutePath())){
                    selectedCount++;
                    selectedSize+=file.length();
                }
            }
            if(selectedCount>0){
                root.addView(space(8));
                LinearLayout actions=row();
                TextView restore=pill("RESTORE SELECTED",Color.rgb(18,145,123),Color.rgb(228,252,248));
                TextView forever=pill("DELETE SELECTED FOREVER",ROSE,Color.rgb(255,238,243));
                touch(restore); touch(forever);
                restore.setOnClickListener(v->restoreSelectedTrash(selectionKey));
                forever.setOnClickListener(v->deleteSelectedTrashForever(selectionKey));
                actions.addView(restore,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
                actions.addView(spaceH(7));
                actions.addView(forever,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1.15f));
                root.addView(actions,matchWrap());
            }
        }

        if (metas.length == 0) {
            LinearLayout empty = card();
            empty.setPadding(dp(18),dp(26),dp(18),dp(26));
            empty.addView(centerText("♻",44,TEAL,true));
            empty.addView(space(8));
            empty.addView(centerText("STS Trash खाली है",16,INK,true));
            root.addView(empty,matchWrap());
        } else {
            root.addView(section("Trash Preview"));
            LinearLayout gallery=column();
            List<LinearLayout> cells=new ArrayList<>();
            int shown=0;
            int limit=160;
            for(File meta:metas){
                File data=trashDataFile(meta);
                if(!data.exists()){meta.delete();continue;}
                if(shown>=limit) break;
                shown++;
                AppFileInfo info=new AppFileInfo("STS Trash",null,"Deleted File","RESTORABLE • 7 DAYS",
                        Color.rgb(72,120,210),Color.rgb(235,245,255),
                        "Tap preview to inspect. Select to restore or permanently delete.",false,false);
                cells.add(galleryFileCard(data,data.length(),selectionKey,true,info,
                        Color.rgb(72,120,210),this::showTrashScreen));
            }
            addGalleryCells(gallery,cells);
            root.addView(gallery,matchWrap());
            if(trashFiles.size()>shown){
                root.addView(space(10));
                root.addView(centerText("Showing first "+shown+" of "+trashFiles.size()+
                        " • Select All applies to all trash items",10,MUTED,false));
            }
        }

        root.addView(space(14));
        TextView back = actionButton("BACK",Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> showHome());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));

        scroll.addView(root);
        setContentView(scroll);
        bindScrollPosition("trash", scroll);
        fadeIn(root);
    }

    private void restoreSelectedTrash(String selectionKey){
        Set<String> sel=new HashSet<>(selectionSet(selectionKey));
        File[] metas=trashDir().listFiles((d,n)->n.endsWith(".stsmeta"));
        if(metas==null) return;
        int count=0;
        long bytes=0L;
        for(File meta:metas){
            File data=trashDataFile(meta);
            if(!sel.contains(data.getAbsolutePath())) continue;
            String[] md=readTrashMeta(meta);
            long n=data.length();
            if(restoreTrashItem(data,meta,md[0])){
                count++;
                bytes+=n;
            }
        }
        clearSelection(selectionKey);
        int restored=count;
        long restoredBytes=bytes;
        new AlertDialog.Builder(this)
                .setTitle("Restore complete")
                .setMessage(restored+" files • "+format(restoredBytes))
                .setPositiveButton("OK",(d,w)->showTrashScreen())
                .show();
    }

    private void deleteSelectedTrashForever(String selectionKey){
        Set<String> sel=new HashSet<>(selectionSet(selectionKey));
        if(sel.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete selected forever?")
                .setMessage(sel.size()+" selected items STS Trash से permanently delete होंगे। यह वापस restore नहीं होंगे।")
                .setNegativeButton("CANCEL",null)
                .setPositiveButton("DELETE FOREVER",(d,w)->{
                    File[] metas=trashDir().listFiles((x,n)->n.endsWith(".stsmeta"));
                    int count=0;
                    long bytes=0L;
                    if(metas!=null){
                        for(File meta:metas){
                            File data=trashDataFile(meta);
                            if(!sel.contains(data.getAbsolutePath())) continue;
                            long n=data.length();
                            boolean ok=!data.exists() || data.delete();
                            if(ok){
                                meta.delete();
                                count++;
                                bytes+=n;
                            }
                        }
                    }
                    clearSelection(selectionKey);
                    int deleted=count;
                    long deletedBytes=bytes;
                    new AlertDialog.Builder(this)
                            .setTitle("Deleted forever")
                            .setMessage(deleted+" files • "+format(deletedBytes))
                            .setPositiveButton("OK",(x,y)->showTrashScreen())
                            .show();
                }).show();
    }

    private boolean restoreTrashItem(File data, File meta, String originalPath) {
        if (originalPath == null || originalPath.length() == 0) return false;
        File target = new File(originalPath);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        if (target.exists()) {
            target = new File(parent, "restored_" + System.currentTimeMillis() + "_" + target.getName());
        }

        boolean ok = data.renameTo(target);
        if (!ok && data.isFile()) {
            ok = copyFile(data,target);
            if (ok && !data.delete()) {
                target.delete();
                ok = false;
            }
        }
        if (ok) meta.delete();
        return ok;
    }

    private void purgeExpiredTrashAsync() {
        new Thread(this::purgeExpiredTrash,"sts-trash-expiry").start();
    }

    private void purgeExpiredTrash() {
        File dir = trashDir();
        File[] metas = dir.listFiles((d,n) -> n.endsWith(".stsmeta"));
        if (metas == null) return;
        long cutoff = System.currentTimeMillis() - 7L*24*60*60*1000;
        for (File meta : metas) {
            String[] md = readTrashMeta(meta);
            long when = 0;
            try { when = Long.parseLong(md[1]); } catch (Exception ignored) {}
            if (when > 0 && when < cutoff) {
                String baseName = meta.getName().substring(0,meta.getName().length()-8);
                new File(dir,baseName).delete();
                meta.delete();
            }
        }
    }

    private void showTrashMovedDone(long bytes, int count, int failed) {
        beginNonScrollScreen();
        getWindow().getDecorView().setTag("done");
        LinearLayout root = column();
        root.setPadding(dp(24),dp(48),dp(24),dp(36));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);

        TextView icon = text("♻",62,TEAL,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(circle(Color.rgb(225,250,246)));
        root.addView(icon,new LinearLayout.LayoutParams(dp(130),dp(130)));
        root.addView(space(24));
        root.addView(centerText("Moved to STS Trash",28,INK,true));
        root.addView(space(10));
        root.addView(centerText(format(bytes),30,PURPLE,true));
        root.addView(centerText(count + " files protected for 7 days",14,MUTED,false));
        root.addView(space(8));
        root.addView(centerText(failed + " failed",12,MUTED,false));

        Space flex = new Space(this);
        root.addView(flex,new LinearLayout.LayoutParams(1,0,1f));
        TextView trash = actionButton("OPEN STS TRASH",PURPLE);
        touch(trash);
        trash.setOnClickListener(v -> showTrashScreen());
        root.addView(trash,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(58)));
        root.addView(space(8));
        TextView done = actionButton("DONE",Color.WHITE);
        done.setTextColor(PURPLE);
        touch(done);
        done.setOnClickListener(v -> showHome());
        root.addView(done,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));
        setContentView(root);
        popIn(icon);
    }

    private void scanTool(String type, ToolCallback cb) {
        new Thread(() -> {
            ToolResult out = new ToolResult(type);
            File root = Environment.getExternalStorageDirectory();
            Set<String> seen = new HashSet<>();

            if ("duplicates".equals(type)) {
                Map<Long,List<File>> bySize = new HashMap<>();
                collectDuplicateCandidates(root, seen, 0, out, bySize, cb);
                hashDuplicateCandidates(bySize, out, cb);
            } else {
                walkTool(root, seen, 0, out, cb);
            }
            if ("large".equals(type)) {
                Collections.sort(out.items, (a,b) -> Long.compare(b.bytes, a.bytes));
            }
            cb.live("Scan complete", out.scannedFiles, out.scannedBytes, out.items.size(), out.totalBytes);
            cb.done(out);
        }, "sts-tool-"+type).start();
    }

    private void walkTool(File f, Set<String> seen, int depth, ToolResult out, ToolCallback cb) {
        if (f == null || depth > 22) return;
        String path;
        try { path = f.getCanonicalPath(); } catch (Exception e) { path = f.getAbsolutePath(); }
        if (!seen.add(path)) return;
        String low = path.toLowerCase(Locale.ROOT);
        if (isCritical(low)) return;

        if (f.isDirectory()) {
            File[] children;
            try { children = f.listFiles(); } catch (Exception e) { children = null; }
            if (children == null) return;

            if ("residual".equals(out.type) && children.length == 0 && depth > 1 && safeResidualDirectory(f)) {
                addToolItem(out, new ToolItem(f, 0, "Empty leftover folder", true));
            }

            for (File x : children) walkTool(x, seen, depth+1, out, cb);
            emitToolLive(out, cb, path, false);
            return;
        }

        long len = Math.max(0, f.length());
        long age = System.currentTimeMillis() - Math.max(0, f.lastModified());
        String name = f.getName().toLowerCase(Locale.ROOT);
        String parent = f.getParent() == null ? "" : f.getParent().toLowerCase(Locale.ROOT);

        out.scannedFiles++;
        out.scannedBytes += len;

        if ("junk".equals(out.type)) {
            boolean junk = (name.endsWith(".tmp") || name.endsWith(".temp") || name.endsWith(".log") ||
                    name.endsWith(".bak") || name.endsWith(".old") || name.endsWith(".dmp") ||
                    name.endsWith(".crash") || ((parent.contains("/cache") || parent.contains("/.cache") ||
                    parent.contains("/.thumbnails")) && !personal(name)));
            if (junk && !personal(name)) addToolItem(out, new ToolItem(f, len, "Safe temporary/cache candidate", false));
        } else if ("large".equals(out.type)) {
            if (len >= 100L*1024*1024) addToolItem(out, new ToolItem(f, len, "Large file • review before delete", false));
        } else if ("residual".equals(out.type)) {
            boolean residual = !personal(name) && age > 7L*24*60*60*1000 &&
                    (name.endsWith(".old") || name.endsWith(".bak") || name.endsWith(".log") ||
                     name.endsWith(".dmp") || name.endsWith(".crash") ||
                     parent.contains("/temp") || parent.contains("/tmp") || parent.contains("/logs"));
            if (residual) addToolItem(out, new ToolItem(f, len, "Old non-personal residual candidate", false));
        } else if ("media".equals(out.type)) {
            if (isImageFile(f) || isVideoFile(f) || isAudioFile(name))
                addToolItem(out, new ToolItem(f, len, "Media file • preview/play before delete", false));
        } else if ("downloads".equals(out.type)) {
            if (parent.contains("/download") || parent.contains("/downloads") ||
                    parent.contains("/offline"))
                addToolItem(out, new ToolItem(f, len, "Downloaded/offline file • review before delete", false));
        } else if ("backups".equals(out.type)) {
            if (isWhatsAppChatBackup(name, low) || isDatabaseLike(name, low) ||
                    low.contains("/backup/") || low.contains("/backups/") ||
                    name.contains("backup") || name.endsWith(".bak"))
                addToolItem(out, new ToolItem(f, len, "Backup/database • current backups may be protected", false));
        }

        emitToolLive(out, cb, path, false);
    }

    private boolean safeResidualDirectory(File f) {
        String p = f.getAbsolutePath().toLowerCase(Locale.ROOT);
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (p.endsWith("/dcim") || p.endsWith("/pictures") || p.endsWith("/movies") ||
                p.endsWith("/music") || p.endsWith("/documents") || p.endsWith("/download") ||
                p.endsWith("/downloads") || p.endsWith("/android") || p.endsWith("/notifications") ||
                p.endsWith("/ringtones") || p.endsWith("/podcasts") || p.endsWith("/alarms")) return false;
        return n.equals("cache") || n.equals(".cache") || n.equals("temp") || n.equals("tmp") ||
                n.equals("logs") || n.equals("crash") || n.startsWith(".tmp") || n.startsWith("temp_");
    }

    private void collectDuplicateCandidates(File f, Set<String> seen, int depth, ToolResult out,
                                            Map<Long,List<File>> bySize, ToolCallback cb) {
        if (f == null || depth > 22) return;
        String path;
        try { path = f.getCanonicalPath(); } catch (Exception e) { path = f.getAbsolutePath(); }
        if (!seen.add(path)) return;
        String low = path.toLowerCase(Locale.ROOT);
        if (isCritical(low)) return;

        if (f.isDirectory()) {
            File[] children;
            try { children = f.listFiles(); } catch (Exception e) { children = null; }
            if (children == null) return;
            for (File x : children) collectDuplicateCandidates(x, seen, depth+1, out, bySize, cb);
            return;
        }

        long len = Math.max(0, f.length());
        out.scannedFiles++;
        out.scannedBytes += len;
        if (len >= 256L*1024) {
            List<File> list = bySize.get(len);
            if (list == null) {
                list = new ArrayList<>();
                bySize.put(len, list);
            }
            list.add(f);
        }
        emitToolLive(out, cb, "Indexing: " + path, false);
    }

    private void hashDuplicateCandidates(Map<Long,List<File>> bySize, ToolResult out, ToolCallback cb) {
        Map<String,List<File>> groups = new HashMap<>();
        for (Map.Entry<Long,List<File>> e : bySize.entrySet()) {
            if (e.getValue().size() < 2) continue;
            for (File f : e.getValue()) {
                String hash = sha256(f);
                if (hash == null) continue;
                String key = e.getKey() + ":" + hash;
                List<File> group = groups.get(key);
                if (group == null) {
                    group = new ArrayList<>();
                    groups.put(key, group);
                }
                group.add(f);
                cb.live("Hashing: " + f.getAbsolutePath(), out.scannedFiles, out.scannedBytes,
                        out.items.size(), out.totalBytes);
            }
        }

        for (List<File> group : groups.values()) {
            if (group.size() < 2) continue;
            Collections.sort(group, Comparator.comparing(File::getAbsolutePath));
            File keep = group.get(0);
            out.groups++;
            for (int i=1; i<group.size(); i++) {
                File dup = group.get(i);
                addToolItem(out, new ToolItem(dup, dup.length(),
                        "Duplicate • keeping: " + keep.getName(), false));
            }
        }
    }

    private String sha256(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64*1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length*2);
            for (byte b : digest) sb.append(String.format(Locale.US, "%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void addToolItem(ToolResult out, ToolItem item) {
        out.items.add(item);
        out.totalBytes += Math.max(0, item.bytes);
    }

    private void emitToolLive(ToolResult out, ToolCallback cb, String path, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - out.lastLiveUpdate < 100) return;
        out.lastLiveUpdate = now;
        cb.live(path, out.scannedFiles, out.scannedBytes, out.items.size(), out.totalBytes);
    }

    private void beginScan(boolean deep) {
        haptic();
        if (Build.VERSION.SDK_INT >= 30 && !hasAllFilesAccess()) {
            pendingScanAfterAccess = true;
            pendingDeepAfterAccess = deep;
            requestAllFiles(deep);
            return;
        }
        startScan(deep);
    }

    private void startScan(boolean deep) {
        beginNonScrollScreen();
        getWindow().getDecorView().setTag("scan");
        LinearLayout root = column();
        root.setPadding(dp(22), dp(24), dp(22), dp(28));
        root.setBackgroundColor(BG);

        TextView title = text(deep ? "Deep Scan" : "Smart Scan", 28, INK, true);
        TextView sub = text("Live storage analysis चल रहा है", 14, MUTED, false);
        root.addView(title); root.addView(space(5)); root.addView(sub); root.addView(space(22));

        ScanRing ring = new ScanRing(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(220), dp(220));
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(ring, rlp);

        TextView status = text("Preparing storage map…", 13, MUTED, false);
        status.setGravity(Gravity.CENTER);
        status.setMaxLines(2);
        root.addView(space(10)); root.addView(status, matchWrap());

        root.addView(space(18));
        LinearLayout live = card();
        live.setPadding(dp(18), dp(16), dp(18), dp(16));
        TextView filesLive = text("0 files scanned", 16, INK, true);
        TextView dataLive = text("0 B analyzed", 13, MUTED, false);
        TextView safeLive = text("Safe junk: 0 • 0 B", 13, Color.rgb(18,145,123), true);
        TextView reviewLive = text("Review: 0 items", 13, AMBER, true);
        live.addView(text("LIVE SCAN", 12, PURPLE, true));
        live.addView(space(8));
        live.addView(filesLive);
        live.addView(space(4));
        live.addView(dataLive);
        live.addView(space(8));
        live.addView(safeLive);
        live.addView(space(4));
        live.addView(reviewLive);
        root.addView(live, matchWrap());

        root.addView(space(14));
        LinearLayout note = card();
        note.setPadding(dp(18), dp(14), dp(18), dp(14));
        note.addView(text("Safety lock active", 14, INK, true));
        note.addView(space(4));
        note.addView(text("Personal media auto-delete नहीं होगा। System-critical files protected रहेंगी।", 12, MUTED, false));
        root.addView(note, matchWrap());

        Space flex = new Space(this);
        root.addView(flex, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView back = actionButton("BACK", Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> handleBack());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        setContentView(root);
        fadeIn(root);

        scanStorage(new ScanCallback() {
            @Override public void progress(int pct, String label) {
                runOnUiThread(() -> {
                    if (pct >= 100) ring.setDone();
                    status.setText(label);
                });
            }

            @Override public void live(String currentPath, int files, long bytes,
                                       int safeCount, long safeBytes, int reviewCount) {
                runOnUiThread(() -> {
                    ring.setLiveFiles(files);
                    status.setText("Scanning: " + shortPath(currentPath));
                    filesLive.setText(files + " files scanned");
                    dataLive.setText(format(bytes) + " analyzed");
                    safeLive.setText("Safe junk: " + safeCount + " • " + format(safeBytes));
                    reviewLive.setText("Review: " + reviewCount + " items");
                });
            }

            @Override public void done(ScanSummary sum) {
                lastSummary = sum;
                runOnUiThread(() -> {
                    ring.setDone();
                    showResult(sum);
                });
            }
        });
    }

    private void showResult(ScanSummary s) {
        rememberCurrentScroll();
        activeScanSummary = s;
        activeScanGalleryMode = -1;
        getWindow().getDecorView().setTag("result");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(18), dp(26), dp(18), dp(28));

        root.addView(text("Scan Result", 28, INK, true));
        root.addView(space(5));
        root.addView(text("Actual storage scan • delete से पहले review", 14, MUTED, false));
        root.addView(space(18));

        LinearLayout scanned = card();
        scanned.setPadding(dp(18), dp(17), dp(18), dp(17));
        scanned.addView(text((s.fullAccess ? "FULL STORAGE SCAN" : "LIMITED SCAN"), 12,
                s.fullAccess ? Color.rgb(18,145,123) : ROSE, true));
        scanned.addView(space(7));
        scanned.addView(text(s.scannedFiles + " files scanned", 18, INK, true));
        scanned.addView(space(4));
        scanned.addView(text(format(s.scannedBytes) + " visible storage analyzed", 14, MUTED, false));
        if (!s.fullAccess) {
            scanned.addView(space(7));
            scanned.addView(text("Full Storage Access ON करने पर ज्यादा folders दिखाई देंगे।", 12, ROSE, false));
        }
        root.addView(scanned, matchWrap());
        root.addView(space(14));

        LinearLayout safeCard = resultCard("Safe Junk", s.safeCount, s.safeBytes, TEAL, "PREVIEW / SELECT");
        safeCard.setOnClickListener(v -> showScanItemsGallery(s, 0));
        touch(safeCard);
        root.addView(safeCard, matchWrap());
        root.addView(space(12));

        LinearLayout reviewCard = resultCard("Review Before Delete", s.reviewCount, s.reviewBytes, AMBER, "PREVIEW / SELECT");
        reviewCard.setOnClickListener(v -> showScanItemsGallery(s, 1));
        touch(reviewCard);
        root.addView(reviewCard, matchWrap());
        root.addView(space(12));

        LinearLayout personalCard = resultCard("Personal Files (Protected)", s.personalCount, s.personalBytes, BLUE, "PREVIEW ONLY");
        personalCard.setOnClickListener(v -> showScanItemsGallery(s, 2));
        touch(personalCard);
        root.addView(personalCard);
        root.addView(space(12));
        root.addView(resultCard("Restricted Android Area", s.restrictedCount, 0, ROSE, "PROTECTED"));
        root.addView(space(18));

        LinearLayout reclaim = card();
        reclaim.setPadding(dp(18), dp(18), dp(18), dp(18));
        reclaim.addView(text("Selected safe reclaim", 14, MUTED, false));
        reclaim.addView(space(5));
        reclaim.addView(text(format(s.safeBytes), 30, PURPLE, true));
        reclaim.addView(space(5));
        reclaim.addView(text("Photos, videos और documents auto-selected नहीं हैं", 12, MUTED, false));
        root.addView(reclaim, matchWrap());

        root.addView(space(22));
        TextView clean = actionButton("CLEAN SAFE JUNK", PURPLE);
        touch(clean);
        clean.setOnClickListener(v -> confirmClean(s));
        root.addView(clean, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

        root.addView(space(10));
        TextView back = actionButton("BACK", Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> handleBack());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        scroll.addView(root);
        setContentView(scroll);
        bindScrollPosition("scanResult", scroll);
        fadeIn(root);
    }

    private void showScanItemsGallery(ScanSummary summary, int mode) {
        rememberCurrentScroll();
        activeScanSummary = summary;
        activeScanGalleryMode = mode;
        getWindow().getDecorView().setTag("scanGallery");

        String label = mode == 0 ? "Safe Junk" : mode == 1 ? "Review Before Delete" : "Personal Files • Protected";
        int accent = mode == 0 ? TEAL : mode == 1 ? AMBER : BLUE;
        String scrollKey = "scanGallery:" + mode;
        String sortKey = "sort:" + scrollKey;
        String selectionKey = "sel:" + scrollKey;

        List<ScanItem> list = new ArrayList<>();
        if (mode == 2) {
            list.addAll(summary.personalItems);
        } else {
            for (ScanItem item : summary.items) {
                if ((mode == 0 && item.safe) || (mode == 1 && !item.safe)) list.add(item);
            }
        }
        sortScanItems(list, sortMode(sortKey, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(22), dp(16), dp(28));

        long totalBytes = 0L;
        for (ScanItem item : list) totalBytes += item.bytes;
        root.addView(text(label, 26, INK, true));
        root.addView(space(5));
        root.addView(text(list.size() + " files • " + format(totalBytes), 12, accent, true));
        root.addView(space(10));

        if (!list.isEmpty()) {
            root.addView(sortControl(sortKey, FILE_SORT_OPTIONS, 0, () -> {
                resetSavedScroll(scrollKey);
                showScanItemsGallery(summary, mode);
            }), matchWrap());

            List<File> selectableFiles = new ArrayList<>();
            if (mode != 2) {
                for (ScanItem item : list) {
                    AppFileInfo info = describeAppFile(item.file);
                    if (!info.protectedFile) selectableFiles.add(item.file);
                }
                root.addView(space(8));
                String action = mode == 0 ? "CLEAN SELECTED" : "MOVE SELECTED TO STS TRASH";
                root.addView(galleryControls(selectionKey, selectableFiles,
                        () -> showScanItemsGallery(summary, mode),
                        action, () -> performSelectedScanAction(summary, mode, selectionKey)), matchWrap());
            } else {
                root.addView(space(8));
                LinearLayout notice = card();
                notice.setPadding(dp(14),dp(12),dp(14),dp(12));
                notice.addView(text("PROTECTED PREVIEW",12,BLUE,true));
                notice.addView(space(4));
                notice.addView(text("Personal files यहाँ preview/open हो सकती हैं, लेकिन auto-select/delete नहीं होंगी।",10,MUTED,false));
                root.addView(notice,matchWrap());
                root.addView(space(8));
                root.addView(viewControl(() -> showScanItemsGallery(summary, mode)), matchWrap());
            }

            root.addView(section("Preview Grid"));
            LinearLayout gallery = column();
            List<LinearLayout> cells = new ArrayList<>();
            int limit = Math.min(160, list.size());
            for (int i=0;i<limit;i++) {
                ScanItem item = list.get(i);
                AppFileInfo info = describeAppFile(item.file);
                boolean selectable = mode != 2 && !info.protectedFile;
                cells.add(galleryFileCard(item.file,item.bytes,selectionKey,
                        selectable,info,accent,() -> showScanItemsGallery(summary,mode)));
            }
            addGalleryCells(gallery,cells);
            root.addView(gallery,matchWrap());

            if (list.size()>limit) {
                root.addView(space(10));
                root.addView(centerText("Showing first "+limit+" of "+list.size()+
                        (mode==2 ? "" : " • Select All applies to all selectable files"),10,MUTED,false));
            }
        } else {
            LinearLayout empty=card();
            empty.setPadding(dp(18),dp(24),dp(18),dp(24));
            empty.addView(centerText("No files",15,MUTED,true));
            root.addView(empty,matchWrap());
        }

        root.addView(space(14));
        TextView back=actionButton("BACK",Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> showResult(summary));
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));

        scroll.addView(root);
        setContentView(scroll);
        bindScrollPosition(scrollKey,scroll);
        fadeIn(root);
    }

    private void performSelectedScanAction(ScanSummary summary,int mode,String selectionKey){
        if(mode==2) return;
        Set<String> selected=new HashSet<>(selectionSet(selectionKey));
        List<ScanItem> targets=new ArrayList<>();
        long bytes=0L;
        for(ScanItem item:summary.items){
            if((mode==0 && !item.safe)||(mode==1 && item.safe)) continue;
            if(!selected.contains(item.file.getAbsolutePath())) continue;
            if(describeAppFile(item.file).protectedFile) continue;
            targets.add(item);
            bytes+=item.bytes;
        }
        if(targets.isEmpty()) return;

        boolean direct=mode==0;
        long total=bytes;
        new AlertDialog.Builder(this)
                .setTitle(direct?"Clean selected safe junk?":"Move selected to STS Trash?")
                .setMessage(targets.size()+" files • "+format(total))
                .setNegativeButton("CANCEL",null)
                .setPositiveButton(direct?"CLEAN":"MOVE TO TRASH",(d,w)->{
                    List<ScanItem> done=new ArrayList<>();
                    long freed=0L;
                    for(ScanItem item:targets){
                        boolean ok;
                        try{ok=direct?item.file.delete():moveToTrash(item.file);}catch(Exception e){ok=false;}
                        if(ok){done.add(item);freed+=item.bytes;}
                    }
                    summary.items.removeAll(done);
                    if(mode==0){
                        summary.safeCount=Math.max(0,summary.safeCount-done.size());
                        summary.safeBytes=Math.max(0L,summary.safeBytes-freed);
                    }else{
                        summary.reviewCount=Math.max(0,summary.reviewCount-done.size());
                        summary.reviewBytes=Math.max(0L,summary.reviewBytes-freed);
                    }
                    clearSelection(selectionKey);
                    long finalFreed=freed;
                    new AlertDialog.Builder(this)
                            .setTitle(direct?"Clean complete":"Moved to STS Trash")
                            .setMessage(done.size()+" files • "+format(finalFreed))
                            .setPositiveButton("OK",(x,y)->showScanItemsGallery(summary,mode))
                            .show();
                }).show();
    }

    private void showItemDetails(ScanSummary s, boolean safeOnly) {
        String sortKey = "sort:scanDetails:" + (safeOnly ? "safe" : "review");
        List<ScanItem> list = new ArrayList<>();
        for (ScanItem i : s.items) {
            if (i.safe == safeOnly) list.add(i);
        }
        sortScanItems(list, sortMode(sortKey, 0));

        StringBuilder b = new StringBuilder();
        int shown = 0;
        for (ScanItem i : list) {
            if (shown >= 30) break;
            b.append("• ").append(i.file.getName()).append("  ").append(format(i.bytes)).append("\n");
            String parent = i.file.getParent();
            if (parent != null) b.append("  ").append(parent).append("\n");
            shown++;
        }
        if (shown == 0) b.append(safeOnly ? "Safe junk item नहीं मिला।" : "Review item नहीं मिला।");
        if (list.size() > shown) {
            b.append("\n+ ").append(list.size() - shown).append(" more items");
        }

        int mode = sortMode(sortKey, 0);
        new AlertDialog.Builder(this)
                .setTitle((safeOnly ? "Safe Junk Details" : "Review Before Delete") +
                        "\nSort: " + FILE_SORT_OPTIONS[mode])
                .setMessage(b.toString())
                .setNeutralButton("SORT BY", (d,w) ->
                        showSortChooser(sortKey, FILE_SORT_OPTIONS, 0,
                                () -> showItemDetails(s, safeOnly)))
                .setPositiveButton("OK", null)
                .show();
    }

    private void confirmClean(ScanSummary s) {
        haptic();
        if (s.safeBytes <= 0) {
            new AlertDialog.Builder(this).setTitle("Safe Clean")
                    .setMessage("अभी कोई safe-selected junk नहीं मिला।")
                    .setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Safe Clean")
                .setMessage("केवल SAFE junk हटेगा। Personal photos/videos/documents और review items नहीं हटेंगे।")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clean", (d,w) -> cleanAsync(s))
                .show();
    }

    private void cleanAsync(ScanSummary s) {
        new Thread(() -> {
            long bytes = 0; int count = 0; int failed = 0;
            for (ScanItem i : s.items) {
                if (!i.safe) continue;
                try {
                    long n = i.file.length();
                    if (i.file.delete()) { bytes += n; count++; }
                    else failed++;
                } catch (Exception e) { failed++; }
            }
            long finalBytes = bytes; int finalCount = count; int finalFailed = failed;
            runOnUiThread(() -> showCleanDone(finalBytes, finalCount, finalFailed));
        }, "sts-clean").start();
    }

    private void showCleanDone(long bytes, int count, int failed) {
        beginNonScrollScreen();
        getWindow().getDecorView().setTag("done");
        LinearLayout root = column();
        root.setPadding(dp(24), dp(48), dp(24), dp(36));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);

        TextView check = text("✓", 62, TEAL, true);
        check.setGravity(Gravity.CENTER);
        check.setBackground(circle(Color.rgb(225,250,246)));
        root.addView(check, new LinearLayout.LayoutParams(dp(130), dp(130)));
        root.addView(space(24));
        root.addView(centerText("Cleaning Complete", 28, INK, true));
        root.addView(space(12));
        root.addView(centerText(format(bytes), 36, PURPLE, true));
        root.addView(centerText("storage reclaimed", 14, MUTED, false));
        root.addView(space(24));

        LinearLayout card = card();
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.addView(text(count + " files cleaned", 15, INK, true));
        card.addView(space(6));
        card.addView(text(failed + " restricted/failed", 13, MUTED, false));
        root.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Space flex = new Space(this);
        root.addView(flex, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView done = actionButton("DONE", PURPLE);
        touch(done);
        done.setOnClickListener(v -> showHome());
        root.addView(done, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        setContentView(root);
        popIn(check);
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        Object tag = getWindow().getDecorView().getTag();
        if ("preview".equals(tag)) {
            returnFromPreview();
        } else if ("scanGallery".equals(tag) && activeScanSummary != null) {
            haptic();
            showResult(activeScanSummary);
        } else if ("appVisibleFiles".equals(tag) && activeAppDetailResult != null) {
            haptic();
            showAppDetailResult(activeAppDetailResult);
        } else if (("appDetail".equals(tag) || "appDetailLoading".equals(tag)) && activeSystemResult != null) {
            haptic();
            showSystemAnalyzerResult(activeSystemResult);
        } else if ("systemAnalyzerResult".equals(tag) || "systemAnalyzer".equals(tag)) {
            haptic();
            showHome();
        } else if (tag == null || "home".equals(tag)) {
            moveTaskToBack(true);
        } else {
            haptic();
            showHome();
        }
    }

    private void requestAllFiles(boolean deep) {
        if (Build.VERSION.SDK_INT < 30) {
            pendingScanAfterAccess = false;
            startScan(deep);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Full Storage Scan Access")
                .setMessage("अभी Android app को पूरे shared storage की file-list देखने नहीं दे रहा है, इसलिए result 0 या बहुत कम आ सकता है। Full Storage Access ON करें; वापस आते ही scan अपने-आप शुरू होगा।")
                .setNegativeButton("Cancel", (d,w) -> {
                    pendingScanAfterAccess = false;
                    pendingDeepAfterAccess = false;
                })
                .setPositiveButton("ALLOW & SCAN", (d,w) -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    }
                }).show();
    }

    private boolean hasAllFilesAccess() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    private void scanStorage(ScanCallback cb) {
        new Thread(() -> {
            ScanSummary out = new ScanSummary();
            out.fullAccess = hasAllFilesAccess();
            cb.progress(1, "Storage map तैयार कर रहे हैं…");

            List<File> roots = new ArrayList<>();
            if (out.fullAccess) {
                roots.add(Environment.getExternalStorageDirectory());
            } else {
                File[] appRoots = getExternalFilesDirs(null);
                if (appRoots != null) for (File f : appRoots) if (f != null) roots.add(f);
                File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (d != null) roots.add(d);
            }

            Set<String> seen = new HashSet<>();
            for (File root : roots) {
                if (root == null || !root.exists()) continue;
                emitLive(out, cb, root.getAbsolutePath(), true);
                walk(root, out, seen, 0, cb);
            }

            emitLive(out, cb, "Final verification", true);
            cb.progress(98, "Junk / large / old files verify कर रहे हैं…");
            cb.progress(100, "Scan complete • " + out.scannedFiles + " files");
            cb.done(out);
        }, "sts-scan").start();
    }

    private void walk(File f, ScanSummary out, Set<String> seen, int depth, ScanCallback cb) {
        if (f == null || depth > 22) return;
        String path;
        try { path = f.getCanonicalPath(); } catch (Exception e) { path = f.getAbsolutePath(); }
        if (!seen.add(path)) return;
        String low = path.toLowerCase(Locale.ROOT);

        if (isCritical(low)) {
            out.restrictedCount++;
            emitLive(out, cb, path, false);
            return;
        }

        if (f.isDirectory()) {
            emitLive(out, cb, path, false);
            File[] children;
            try { children = f.listFiles(); } catch (Exception e) { children = null; }
            if (children == null) {
                out.restrictedCount++;
                emitLive(out, cb, path, true);
                return;
            }
            for (File x : children) walk(x, out, seen, depth + 1, cb);
            return;
        }

        String name = f.getName().toLowerCase(Locale.ROOT);
        String parent = f.getParent() == null ? "" : f.getParent().toLowerCase(Locale.ROOT);
        long len = Math.max(0, f.length());
        long age = System.currentTimeMillis() - Math.max(0, f.lastModified());

        out.scannedFiles++;
        out.scannedBytes += len;

        boolean isPersonal = personal(name);
        if (isPersonal) {
            out.personalCount++;
            out.personalBytes += len;
            out.personalItems.add(new ScanItem(f, len, false));
        }

        boolean safe = false;
        boolean review = false;

        if (name.endsWith(".tmp") || name.endsWith(".temp") || name.endsWith(".log") ||
                name.endsWith(".bak") || name.endsWith(".old") || name.endsWith(".dmp") ||
                name.endsWith(".crash")) {
            safe = !isPersonal;
        } else if ((parent.contains("/cache") || parent.contains("/.cache") ||
                parent.contains("/.thumbnails")) && !isPersonal) {
            safe = true;
        } else if (name.endsWith(".apk") && age > 7L*24*60*60*1000) {
            review = true;
        } else if (low.contains("/.trash") || name.startsWith(".trashed-")) {
            review = true;
        } else if (len >= 100L*1024*1024) {
            review = true;
        } else if ((parent.contains("/download") || parent.contains("/downloads")) &&
                age > 90L*24*60*60*1000 && !isPersonal) {
            review = true;
        }

        if (safe) {
            out.items.add(new ScanItem(f, len, true));
            out.safeBytes += len;
            out.safeCount++;
        } else if (review) {
            out.items.add(new ScanItem(f, len, false));
            out.reviewBytes += len;
            out.reviewCount++;
        }

        emitLive(out, cb, path, false);
    }

    private void emitLive(ScanSummary out, ScanCallback cb, String path, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - out.lastLiveUpdate < 90) return;
        out.lastLiveUpdate = now;
        cb.live(path, out.scannedFiles, out.scannedBytes, out.safeCount, out.safeBytes, out.reviewCount);
    }

    private boolean isCritical(String p) {
        return p.startsWith("/system") || p.startsWith("/vendor") || p.startsWith("/product") ||
                p.startsWith("/data/system") || p.startsWith("/data/adb") ||
                p.contains("/android/data") || p.contains("/android/obb");
    }

    private boolean personal(String n) {
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") ||
                n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".mov") || n.endsWith(".avi") ||
                n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx") || n.endsWith(".xls") ||
                n.endsWith(".xlsx") || n.endsWith(".ppt") || n.endsWith(".pptx") || n.endsWith(".zip");
    }

    private void addHomeToolGrid(LinearLayout parent) {
        HomeToolSpec[] tools = new HomeToolSpec[]{
                new HomeToolSpec("✦","Junk","Cache",TEAL,"junk"),
                new HomeToolSpec("⬢","Large","Files",AMBER,"large"),
                new HomeToolSpec("⧉","Duplicate","Hash",BLUE,"duplicates"),
                new HomeToolSpec("⌁","Residual","Leftovers",ROSE,"residual"),
                new HomeToolSpec("♻","STS Trash","7 days",Color.rgb(72,120,210),"trash"),
                new HomeToolSpec("◉","Apps","& System",Color.rgb(82,88,110),"system"),
                new HomeToolSpec("▶","Media","Photo/Video",Color.rgb(170,82,205),"media"),
                new HomeToolSpec("⇩","Downloads","Offline",Color.rgb(244,139,45),"downloads"),
                new HomeToolSpec("⛃","Backups","Database",Color.rgb(139,93,210),"backups")
        };
        int cols = galleryColumns();
        LinearLayout row = null;
        int inRow = 0;
        for (HomeToolSpec spec : tools) {
            if (row == null || inRow == cols) {
                if (row != null) parent.addView(space(8));
                row = row();
                parent.addView(row, matchWrap());
                inRow = 0;
            }
            if (inRow > 0) row.addView(spaceH(8));
            LinearLayout card = toolGridCard(spec.icon, spec.title, spec.sub, spec.accent);
            card.setOnClickListener(v -> {
                if ("trash".equals(spec.id)) showTrashScreen();
                else if ("system".equals(spec.id)) openSystemAnalyzer();
                else openTool(spec.id);
            });
            row.addView(card, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            inRow++;
        }
        if (row != null && inRow < cols) {
            while (inRow < cols) {
                row.addView(spaceH(8));
                row.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1f));
                inRow++;
            }
        }
    }

    private LinearLayout toolGridCard(String icon, String title, String sub, int accent) {
        LinearLayout c = card();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(7), dp(12), dp(7), dp(11));
        TextView ic = text(icon, galleryColumns() >= 4 ? 18 : 21, accent, true);
        ic.setGravity(Gravity.CENTER);
        c.addView(ic);
        c.addView(space(7));
        TextView t = text(title, galleryColumns() >= 4 ? 9 : 12, INK, true);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        c.addView(t, matchWrap());
        c.addView(space(3));
        TextView st = text(sub, galleryColumns() >= 4 ? 7 : 9, MUTED, false);
        st.setGravity(Gravity.CENTER);
        st.setMaxLines(1);
        c.addView(st, matchWrap());
        c.setMinimumHeight(dp(galleryColumns() >= 4 ? 96 : 116));
        touch(c);
        return c;
    }

    private LinearLayout toolCard(String icon, String title, String sub, int accent) {
        LinearLayout c = card();
        c.setPadding(dp(15), dp(16), dp(15), dp(16));
        TextView ic = text(icon, 22, accent, true);
        c.addView(ic);
        c.addView(space(12));
        c.addView(text(title, 15, INK, true));
        c.addView(space(4));
        c.addView(text(sub, 12, MUTED, false));
        touch(c);
        return c;
    }

    private LinearLayout resultCard(String title, int count, long bytes, int accent, String badge) {
        LinearLayout c = card();
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.addView(text(title, 16, INK, true));
        c.addView(space(5));
        c.addView(text(count + " items", 12, MUTED, false));
        c.addView(space(8));
        c.addView(text(format(bytes), 20, accent, true));
        c.addView(space(7));
        TextView b = pill(badge, accent, Color.argb(25, Color.red(accent), Color.green(accent), Color.blue(accent)));
        c.addView(b);
        return c;
    }

    private long[] storage() {
        StatFs st = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        long total = st.getTotalBytes(), free = st.getAvailableBytes();
        return new long[]{total, total-free};
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout l = column();
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.WHITE);
        d.setCornerRadius(dp(24));
        l.setBackground(d);
        l.setElevation(dp(3));
        return l;
    }

    private TextView section(String s) {
        TextView v = text(s, 16, INK, true);
        v.setPadding(0, dp(22), 0, dp(12));
        return v;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        return v;
    }

    private TextView centerText(String s, float size, int color, boolean bold) {
        TextView v = text(s,size,color,bold);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private TextView pill(String s, int color, int bg) {
        TextView v = text(s, 12, color, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg); d.setCornerRadius(dp(18));
        v.setBackground(d);
        return v;
    }

    private TextView actionButton(String s, int color) {
        TextView v = text(s, 17, Color.WHITE, true);
        v.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(28));
        RippleDrawable r = new RippleDrawable(ColorStateList.valueOf(Color.argb(45,255,255,255)), d, null);
        v.setBackground(r);
        return v;
    }

    private GradientDrawable gradient(int a, int b, float tl, float tr, float br, float bl) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a,b});
        d.setCornerRadii(new float[]{tl,tl,tr,tr,br,br,bl,bl});
        return d;
    }

    private GradientDrawable circle(int c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL); d.setColor(c);
        return d;
    }

    private void touch(View v) {
        v.setOnTouchListener((view,e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(.97f).scaleY(.97f).setDuration(90).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            }
            return false;
        });
    }

    private void fadeIn(View v) {
        v.setAlpha(0f); v.setTranslationY(dp(12));
        v.animate().alpha(1f).translationY(0).setDuration(330).start();
    }

    private void popIn(View v) {
        v.setScaleX(.5f); v.setScaleY(.5f); v.setAlpha(0f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(420).start();
    }

    private void haptic() {
        try {
            Vibrator v = (Vibrator)getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(30,90));
        } catch (Exception ignored) {}
    }

    private View space(int h) { Space s = new Space(this); s.setMinimumHeight(dp(h)); return s; }
    private View spaceH(int w) { Space s = new Space(this); s.setMinimumWidth(dp(w)); return s; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private String shortPath(String path) {
        if (path == null || path.length() == 0) return "";
        if (path.length() <= 68) return path;
        return "…" + path.substring(path.length() - 67);
    }

    private String format(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] u={"B","KB","MB","GB","TB"}; double v=bytes; int i=0;
        while(v>=1024 && i<u.length-1){v/=1024;i++;}
        return String.format(Locale.US, v>=100?"%.0f %s":v>=10?"%.1f %s":"%.2f %s",v,u[i]);
    }

    private enum StorageCategory {
        PHOTOS("Photos", Color.rgb(231, 76, 120)),
        VIDEOS("Videos", Color.rgb(108, 76, 230)),
        AUDIO("Audio / Music", Color.rgb(35, 180, 155)),
        DOCUMENTS("Documents / PDF", Color.rgb(61, 132, 224)),
        APK("APK / Installers", Color.rgb(255, 159, 64)),
        BACKUPS("Backups / Databases", Color.rgb(160, 94, 210)),
        JUNK("Junk / Cache", Color.rgb(72, 196, 120)),
        OTHERS("Other visible files", Color.rgb(124, 132, 150)),
        APP_CODE("Installed Apps", Color.rgb(86, 92, 118)),
        APP_DATA("Private App Data", Color.rgb(122, 86, 214)),
        APP_CACHE("App Cache", Color.rgb(38, 184, 150)),
        SYSTEM_RESERVED("Android / System / Reserved", Color.rgb(217, 87, 101)),
        HIDDEN_UNCLASSIFIED("Hidden / Private (not split yet)", Color.rgb(70, 78, 100));

        final String label;
        final int color;
        StorageCategory(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private static final class StorageAnalytics {
        final long[] bytes = new long[StorageCategory.values().length];
        final int[] counts = new int[StorageCategory.values().length];
        long visibleBytes;
        int visibleFiles;
        long usedStorage;
        boolean privateStatsAvailable;

        void add(StorageCategory c, long b, int count) {
            bytes[c.ordinal()] += Math.max(0L, b);
            counts[c.ordinal()] += Math.max(0, count);
        }
        long bytes(StorageCategory c) { return bytes[c.ordinal()]; }
        int count(StorageCategory c) { return counts[c.ordinal()]; }
    }

    private static final class StorageBreakdownView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private StorageAnalytics analytics;
        private long denominator = 1L;

        StorageBreakdownView(Context c) { super(c); }

        void setAnalytics(StorageAnalytics a, long denominator) {
            this.analytics = a;
            this.denominator = Math.max(1L, denominator);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float left = 0f;
            float top = 0f;
            float right = getWidth();
            float bottom = getHeight();
            float radius = getHeight()/2f;

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(232,234,243));
            c.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, p);

            if (analytics == null) return;

            float x = 0f;
            StorageCategory[] cats = StorageCategory.values();
            for (StorageCategory cat : cats) {
                long b = analytics.bytes(cat);
                if (b <= 0) continue;
                float w = (float) getWidth() * ((float)b / (float)denominator);
                if (w < 1f && b > 0) w = 1f;
                float end = Math.min(getWidth(), x + w);
                p.setColor(cat.color);
                c.drawRect(x, top, end, bottom, p);
                x = end;
                if (x >= getWidth()) break;
            }

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f);
            p.setColor(Color.argb(45,0,0,0));
            c.drawRoundRect(new RectF(left,top,right,bottom),radius,radius,p);
        }
    }

    private enum AppVisibleCategory {
        PHOTOS("Photos", Color.rgb(231,76,120)),
        VIDEOS("Videos", Color.rgb(108,76,230)),
        AUDIO("Audio / Voice / Music", Color.rgb(35,180,155)),
        DOCUMENTS("Documents", Color.rgb(61,132,224)),
        BACKUPS("Backups / Databases", Color.rgb(160,94,210)),
        DOWNLOADS("Downloads / Offline", Color.rgb(255,159,64)),
        JUNK("Junk / Temp / Cache", Color.rgb(72,196,120)),
        OTHER("Other app files", Color.rgb(124,132,150));

        final String label;
        final int color;
        AppVisibleCategory(String label,int color){
            this.label=label;
            this.color=color;
        }
    }

    private static final class AppVisibleFile {
        final File file;
        final long bytes;
        final AppFileInfo info;
        final AppVisibleCategory category;

        AppVisibleFile(File file,long bytes,AppFileInfo info){
            this.file=file;
            this.bytes=bytes;
            this.info=info;
            this.category=null;
        }

        AppVisibleFile(File file,long bytes,AppFileInfo info,AppVisibleCategory category){
            this.file=file;
            this.bytes=bytes;
            this.info=info;
            this.category=category;
        }
    }

    private static final class AppDetailResult {
        final AppStorageEntry entry;
        final long[] bytes=new long[AppVisibleCategory.values().length];
        final int[] counts=new int[AppVisibleCategory.values().length];
        final List<AppVisibleFile> files=new ArrayList<>();
        long visibleBytes;
        int visibleFiles;

        AppDetailResult(AppStorageEntry entry){this.entry=entry;}

        void add(AppVisibleCategory cat,AppVisibleFile f){
            AppVisibleFile stored=new AppVisibleFile(f.file,f.bytes,f.info,cat);
            files.add(stored);
            bytes[cat.ordinal()]+=Math.max(0L,f.bytes);
            counts[cat.ordinal()]++;
            visibleBytes+=Math.max(0L,f.bytes);
            visibleFiles++;
        }

        void remove(AppVisibleFile f){
            if(files.remove(f)){
                AppVisibleCategory cat=f.category;
                bytes[cat.ordinal()]=Math.max(0L,bytes[cat.ordinal()]-f.bytes);
                counts[cat.ordinal()]=Math.max(0,counts[cat.ordinal()]-1);
                visibleBytes=Math.max(0L,visibleBytes-f.bytes);
                visibleFiles=Math.max(0,visibleFiles-1);
            }
        }

        long bytes(AppVisibleCategory c){return bytes[c.ordinal()];}
        int count(AppVisibleCategory c){return counts[c.ordinal()];}
    }

    private interface SystemAnalyzeCallback {
        void live(int done,int total,String appName,long code,long data,long cache);
    }

    private static final class AppStorageEntry {
        final String appName;
        final String packageName;
        final long codeBytes;
        final long dataBytes;
        final long cacheBytes;
        final long totalBytes;
        final boolean systemApp;

        AppStorageEntry(String appName,String packageName,long codeBytes,long dataBytes,
                        long cacheBytes,long totalBytes,boolean systemApp){
            this.appName=appName;
            this.packageName=packageName;
            this.codeBytes=codeBytes;
            this.dataBytes=dataBytes;
            this.cacheBytes=cacheBytes;
            this.totalBytes=totalBytes;
            this.systemApp=systemApp;
        }
    }

    private static final class SystemStorageResult {
        long usedBytes;
        long visibleBytes;
        long appCodeBytes;
        long appDataBytes;
        long appCacheBytes;
        long systemReservedBytes;
        final List<AppStorageEntry> apps=new ArrayList<>();
    }

    private static final class AppFileInfo {
        final String appName;
        final String packageName;
        final String category;
        final String status;
        final int statusColor;
        final int statusBg;
        final String explanation;
        final boolean protectedFile;
        final boolean detailsOnly;

        AppFileInfo(String appName, String packageName, String category, String status,
                    int statusColor, int statusBg, String explanation,
                    boolean protectedFile, boolean detailsOnly) {
            this.appName = appName;
            this.packageName = packageName;
            this.category = category;
            this.status = status;
            this.statusColor = statusColor;
            this.statusBg = statusBg;
            this.explanation = explanation;
            this.protectedFile = protectedFile;
            this.detailsOnly = detailsOnly;
        }

        static AppFileInfo folder() {
            return new AppFileInfo("Device Storage", null, "Folder", "FOLDER",
                    PURPLE, Color.rgb(239,236,255), "Storage folder", false, true);
        }
    }

    private static final class HomeToolSpec {
        final String icon,title,sub,id;
        final int accent;
        HomeToolSpec(String icon,String title,String sub,int accent,String id){
            this.icon=icon;this.title=title;this.sub=sub;this.accent=accent;this.id=id;
        }
    }

    private static final class ToolItem {
        final File file;
        final long bytes;
        final String note;
        final boolean directory;
        ToolItem(File file, long bytes, String note, boolean directory) {
            this.file = file;
            this.bytes = bytes;
            this.note = note;
            this.directory = directory;
        }
    }

    private static final class ToolResult {
        final String type;
        final List<ToolItem> items = new ArrayList<>();
        int scannedFiles;
        int groups;
        long scannedBytes;
        long totalBytes;
        long lastLiveUpdate;
        ToolResult(String type) { this.type = type; }
    }

    private interface ToolCallback {
        void live(String currentPath, int files, long bytes, int items, long itemBytes);
        void done(ToolResult result);
    }

    private static final class ScanItem {
        final File file; final long bytes; final boolean safe;
        ScanItem(File f,long b,boolean s){file=f;bytes=b;safe=s;}
    }
    private static final class ScanSummary {
        long safeBytes, reviewBytes, scannedBytes, personalBytes, lastLiveUpdate;
        int safeCount, reviewCount, restrictedCount, scannedFiles, personalCount;
        boolean fullAccess;
        final List<ScanItem> items = new ArrayList<>();
        final List<ScanItem> personalItems = new ArrayList<>();
    }
    private interface ScanCallback {
        void progress(int pct,String label);
        void live(String currentPath, int files, long bytes, int safeCount, long safeBytes, int reviewCount);
        void done(ScanSummary sum);
    }

    private static final class StorageRing extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long total, used;
        private StorageAnalytics analytics;

        StorageRing(Context c){super(c);}

        void setStorage(long t,long u){
            total=Math.max(0L,t);
            used=Math.max(0L,Math.min(t,u));
            invalidate();
        }

        void setAnalytics(StorageAnalytics a){
            analytics=a;
            invalidate();
        }

        @Override protected void onDraw(Canvas c){
            float cx=getWidth()/2f, cy=getHeight()/2f, r=Math.min(cx,cy)-22;
            RectF oval=new RectF(cx-r,cy-r,cx+r,cy+r);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(17);
            p.setStrokeCap(Paint.Cap.BUTT);
            p.setColor(Color.argb(72,255,255,255));
            c.drawCircle(cx,cy,r,p);

            float usedSweep=total<=0?0f:360f*Math.min(1f,(float)used/(float)total);
            if(analytics!=null){
                long categorized=0L;
                for(StorageCategory cat:StorageCategory.values()){
                    categorized+=Math.max(0L,analytics.bytes(cat));
                }
                if(categorized>0L){
                    float start=-90f;
                    for(StorageCategory cat:StorageCategory.values()){
                        long b=Math.max(0L,analytics.bytes(cat));
                        if(b<=0L) continue;
                        float sweep=usedSweep*((float)b/(float)categorized);
                        if(sweep<=0f) continue;
                        p.setColor(cat.color);
                        c.drawArc(oval,start,Math.max(0.8f,sweep-0.8f),false,p);
                        start+=sweep;
                    }
                }else{
                    p.setColor(TEAL);
                    c.drawArc(oval,-90,usedSweep,false,p);
                }
            }else{
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(TEAL);
                c.drawArc(oval,-90,usedSweep,false,p);
            }

            long free=Math.max(0L,total-used);
            float d=getResources().getDisplayMetrics().density;

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);

            // USED section
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(9*d);
            p.setColor(Color.argb(205,255,255,255));
            c.drawText("USED",cx,cy-47*d,p);

            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(27*d);
            p.setColor(Color.WHITE);
            c.drawText(formatStatic(used),cx,cy-19*d,p);

            // subtle divider keeps USED and FREE visually separate
            p.setStrokeWidth(1*d);
            p.setColor(Color.argb(90,255,255,255));
            c.drawRect(cx-34*d,cy-2*d,cx+34*d,cy-1*d,p);

            // FREE section
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(9*d);
            p.setColor(Color.argb(205,255,255,255));
            c.drawText("FREE",cx,cy+16*d,p);

            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(19*d);
            p.setColor(Color.WHITE);
            c.drawText(formatStatic(free),cx,cy+40*d,p);

            // total stays small and clearly separated at the bottom
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(9*d);
            p.setColor(Color.argb(195,255,255,255));
            c.drawText("TOTAL  "+formatStatic(total),cx,cy+61*d,p);
        }
    }

    private static final class ScanRing extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int liveCount;
        private String liveUnit = "files";
        private boolean done;
        ScanRing(Context c){super(c);}
        void setLiveFiles(int n){setLiveCount(n,"files");}
        void setLiveCount(int n,String unit){
            liveCount=Math.max(0,n);
            liveUnit=(unit==null||unit.length()==0)?"items":unit;
            invalidate();
        }
        void setDone(){done=true;invalidate();}
        @Override protected void onDraw(Canvas c){
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-26;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(18);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.rgb(229,232,246));
            c.drawCircle(cx,cy,r,p);

            if (done) {
                p.setColor(TEAL);
                c.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),-90,360,false,p);
            } else {
                long t = System.currentTimeMillis() % 1600L;
                float start = -90f + (t / 1600f) * 360f;
                p.setColor(PURPLE);
                c.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),start,105,false,p);
                p.setStrokeWidth(8);
                p.setColor(TEAL);
                c.drawArc(new RectF(cx-r+24,cy-r+24,cx+r-24,cy+r-24),start-65,55,false,p);
                postInvalidateDelayed(16);
            }

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(INK);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(31*getResources().getDisplayMetrics().scaledDensity);
            c.drawText(done ? "DONE" : "LIVE",cx,cy-2,p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(13*getResources().getDisplayMetrics().scaledDensity);
            p.setColor(MUTED);
            c.drawText(liveCount+" "+liveUnit,cx,cy+29,p);
        }
    }

    private static String formatStatic(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] u={"B","KB","MB","GB","TB"}; double v=bytes; int i=0;
        while(v>=1024 && i<u.length-1){v/=1024;i++;}
        return String.format(Locale.US, v>=100?"%.0f %s":v>=10?"%.1f %s":"%.2f %s",v,u[i]);
    }
}
