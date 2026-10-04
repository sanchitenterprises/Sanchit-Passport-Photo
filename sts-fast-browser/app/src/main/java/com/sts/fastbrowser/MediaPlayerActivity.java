package com.sts.fastbrowser;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MediaPlayerActivity extends Activity implements SurfaceHolder.Callback {
    private Uri sourceUri;
    private String fileName;
    private String mimeType;
    private boolean videoMode;

    private MediaPlayer mediaPlayer;
    private SurfaceView surfaceView;
    private FrameLayout mediaStage;
    private TextView audioArtwork;
    private TextView titleView;
    private TextView playPauseButton;
    private TextView elapsedView;
    private TextView durationView;
    private SeekBar seekBar;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean prepared = false;
    private boolean userSeeking = false;
    private boolean firstStart = true;
    private int resumePosition = 0;
    private boolean resumePlaying = true;

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (prepared && mediaPlayer != null && !userSeeking) {
                try {
                    int pos = mediaPlayer.getCurrentPosition();
                    int dur = mediaPlayer.getDuration();
                    seekBar.setMax(Math.max(1, dur));
                    seekBar.setProgress(Math.max(0, Math.min(pos, dur)));
                    elapsedView.setText(formatTime(pos));
                    durationView.setText(formatTime(dur));
                } catch (Exception ignored) {}
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#355C62"));
        getWindow().setNavigationBarColor(Color.BLACK);

        sourceUri = getIntent().getData();
        mimeType = getIntent().getType();
        if (TextUtils.isEmpty(mimeType)) mimeType = getIntent().getStringExtra("media_mime");
        fileName = getIntent().getStringExtra("media_name");
        if (TextUtils.isEmpty(fileName)) fileName = resolveDisplayName(sourceUri);
        if (TextUtils.isEmpty(fileName)) fileName = "Media";

        String requestedMode = getIntent().getStringExtra("media_mode");
        videoMode = "video".equalsIgnoreCase(requestedMode) ||
                (!TextUtils.isEmpty(mimeType) && mimeType.toLowerCase(Locale.ROOT).startsWith("video/")) ||
                looksLikeVideo(fileName);

        if (savedInstanceState != null) {
            resumePosition = savedInstanceState.getInt("position", 0);
            resumePlaying = savedInstanceState.getBoolean("playing", true);
            firstStart = false;
        }

        setTitle(fileName);
        setContentView(buildUi());
        handler.post(progressUpdater);

        if (!videoMode) {
            prepareMedia(null);
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(4), dp(3), dp(4), dp(3));
        GradientDrawable topBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#D9F0EE"), Color.parseColor("#E3EAF4"), Color.parseColor("#EEE8F4")}
        );
        topBar.setBackground(topBg);
        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        TextView back = makeTopButton("‹", 30);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        topBar.addView(back, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.MATCH_PARENT));

        titleView = new TextView(this);
        titleView.setText(fileName);
        titleView.setTextColor(Color.parseColor("#162326"));
        titleView.setTextSize(14);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setPadding(dp(6), 0, dp(8), 0);
        topBar.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView menu = makeTopButton("⋮", 26);
        menu.setContentDescription("Menu");
        menu.setOnClickListener(this::showMenu);
        topBar.addView(menu, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.MATCH_PARENT));

        mediaStage = new FrameLayout(this);
        mediaStage.setBackgroundColor(Color.BLACK);
        root.addView(mediaStage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (videoMode) {
            surfaceView = new SurfaceView(this);
            surfaceView.setBackgroundColor(Color.BLACK);
            mediaStage.addView(surfaceView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER));
            surfaceView.getHolder().addCallback(this);
        } else {
            buildAudioStage();
        }

        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setPadding(dp(12), dp(8), dp(12), dp(12));
        controlPanel.setBackgroundColor(Color.parseColor("#101010"));
        root.addView(controlPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        seekBar = new SeekBar(this);
        seekBar.setMax(1);
        seekBar.setProgress(0);
        controlPanel.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        controlPanel.addView(timeRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        elapsedView = makeTimeText("0:00", Gravity.START);
        durationView = makeTimeText("0:00", Gravity.END);
        timeRow.addView(elapsedView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        timeRow.addView(durationView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, dp(4), 0, 0);
        controlPanel.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        TextView rewind = makeControlButton("↶ 10", 16);
        playPauseButton = makeControlButton("▶", 28);
        TextView forward = makeControlButton("10 ↷", 16);

        rewind.setOnClickListener(v -> seekRelative(-10000));
        playPauseButton.setOnClickListener(v -> togglePlayback());
        forward.setOnClickListener(v -> seekRelative(10000));

        buttons.addView(rewind, new LinearLayout.LayoutParams(dp(90), dp(52)));
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(dp(72), dp(56));
        centerLp.setMargins(dp(12), 0, dp(12), 0);
        buttons.addView(playPauseButton, centerLp);
        buttons.addView(forward, new LinearLayout.LayoutParams(dp(90), dp(52)));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) elapsedView.setText(formatTime(progress));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                if (prepared && mediaPlayer != null) {
                    try { mediaPlayer.seekTo(seekBar.getProgress()); } catch (Exception ignored) {}
                }
            }
        });

        return root;
    }

    private void buildAudioStage() {
        LinearLayout audioPanel = new LinearLayout(this);
        audioPanel.setOrientation(LinearLayout.VERTICAL);
        audioPanel.setGravity(Gravity.CENTER);
        audioPanel.setPadding(dp(28), dp(24), dp(28), dp(24));

        audioArtwork = new TextView(this);
        audioArtwork.setText("♫");
        audioArtwork.setTextColor(Color.WHITE);
        audioArtwork.setTextSize(84);
        audioArtwork.setGravity(Gravity.CENTER);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor("#252525"));
        circle.setStroke(dp(2), Color.parseColor("#5B7FA3"));
        audioArtwork.setBackground(circle);

        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(dp(190), dp(190));
        audioPanel.addView(audioArtwork, artLp);

        TextView name = new TextView(this);
        name.setText(fileName);
        name.setTextColor(Color.WHITE);
        name.setTextSize(18);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.setMargins(0, dp(22), 0, 0);
        audioPanel.addView(name, nameLp);

        TextView label = new TextView(this);
        label.setText("STS Media Player");
        label.setTextColor(Color.parseColor("#A9A9A9"));
        label.setTextSize(13);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.setMargins(0, dp(8), 0, 0);
        audioPanel.addView(label, labelLp);

        mediaStage.addView(audioPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
    }

    private TextView makeTopButton(String text, int textSize) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(textSize);
        v.setTextColor(Color.parseColor("#162326"));
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private TextView makeControlButton(String text, int textSize) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(textSize);
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        v.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#292929"));
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(1), Color.parseColor("#4A4A4A"));
        v.setBackground(bg);
        return v;
    }

    private TextView makeTimeText(String text, int gravity) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.parseColor("#C8C8C8"));
        v.setTextSize(12);
        v.setGravity(gravity | Gravity.CENTER_VERTICAL);
        return v;
    }

    private void prepareMedia(SurfaceHolder holder) {
        releasePlayer();
        if (sourceUri == null) {
            Toast.makeText(this, "Media file नहीं मिली", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(videoMode
                            ? AudioAttributes.CONTENT_TYPE_MOVIE
                            : AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            if (videoMode && holder != null) mediaPlayer.setDisplay(holder);
            mediaPlayer.setDataSource(this, sourceUri);

            mediaPlayer.setOnPreparedListener(mp -> {
                prepared = true;
                int duration = 0;
                try { duration = mp.getDuration(); } catch (Exception ignored) {}
                seekBar.setMax(Math.max(1, duration));
                durationView.setText(formatTime(duration));

                if (resumePosition > 0) {
                    try { mp.seekTo(Math.min(resumePosition, Math.max(0, duration - 1))); } catch (Exception ignored) {}
                }

                if (videoMode) {
                    try { fitVideoSurface(mp.getVideoWidth(), mp.getVideoHeight()); } catch (Exception ignored) {}
                }

                boolean shouldPlay = firstStart || resumePlaying;
                firstStart = false;
                if (shouldPlay) startPlayback();
                else updatePlayButton();
            });

            mediaPlayer.setOnVideoSizeChangedListener((mp, width, height) -> {
                if (videoMode) fitVideoSurface(width, height);
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                try {
                    seekBar.setProgress(seekBar.getMax());
                    elapsedView.setText(durationView.getText());
                } catch (Exception ignored) {}
                updatePlayButton();
                setKeepScreenOn(false);
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "Media play नहीं हो पाया", Toast.LENGTH_SHORT).show();
                updatePlayButton();
                setKeepScreenOn(false);
                return true;
            });

            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            releasePlayer();
            Toast.makeText(this, "Media open नहीं हो पाया", Toast.LENGTH_SHORT).show();
        }
    }

    private void startPlayback() {
        if (!prepared || mediaPlayer == null) return;
        try {
            mediaPlayer.start();
            updatePlayButton();
            setKeepScreenOn(videoMode);
        } catch (Exception ignored) {}
    }

    private void togglePlayback() {
        if (!prepared || mediaPlayer == null) return;
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                setKeepScreenOn(false);
            } else {
                if (mediaPlayer.getCurrentPosition() >= Math.max(0, mediaPlayer.getDuration() - 500)) {
                    mediaPlayer.seekTo(0);
                }
                mediaPlayer.start();
                setKeepScreenOn(videoMode);
            }
            updatePlayButton();
        } catch (Exception ignored) {}
    }

    private void updatePlayButton() {
        if (playPauseButton == null) return;
        boolean playing = false;
        try { playing = prepared && mediaPlayer != null && mediaPlayer.isPlaying(); } catch (Exception ignored) {}
        playPauseButton.setText(playing ? "❚❚" : "▶");
    }

    private void seekRelative(int deltaMs) {
        if (!prepared || mediaPlayer == null) return;
        try {
            int duration = mediaPlayer.getDuration();
            int target = Math.max(0, Math.min(duration, mediaPlayer.getCurrentPosition() + deltaMs));
            mediaPlayer.seekTo(target);
            seekBar.setProgress(target);
            elapsedView.setText(formatTime(target));
        } catch (Exception ignored) {}
    }

    private void fitVideoSurface(int videoWidth, int videoHeight) {
        if (!videoMode || surfaceView == null || mediaStage == null || videoWidth <= 0 || videoHeight <= 0) return;
        mediaStage.post(() -> {
            int availableW = mediaStage.getWidth();
            int availableH = mediaStage.getHeight();
            if (availableW <= 0 || availableH <= 0) return;

            float videoRatio = videoWidth / (float) videoHeight;
            float stageRatio = availableW / (float) availableH;

            int targetW;
            int targetH;
            if (videoRatio > stageRatio) {
                targetW = availableW;
                targetH = Math.max(1, Math.round(availableW / videoRatio));
            } else {
                targetH = availableH;
                targetW = Math.max(1, Math.round(availableH * videoRatio));
            }

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(targetW, targetH, Gravity.CENTER);
            surfaceView.setLayoutParams(lp);
        });
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Share");
        popup.getMenu().add("Open with another app");
        popup.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if ("Share".equals(title)) {
                shareMedia();
                return true;
            }
            if ("Open with another app".equals(title)) {
                openExternally();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void shareMedia() {
        if (sourceUri == null) return;
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(TextUtils.isEmpty(mimeType) ? (videoMode ? "video/*" : "audio/*") : mimeType);
            share.putExtra(Intent.EXTRA_STREAM, sourceUri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(ClipData.newRawUri("media", sourceUri));
            startActivity(Intent.createChooser(share, "Share"));
        } catch (Exception e) {
            Toast.makeText(this, "Share नहीं हो पाया", Toast.LENGTH_SHORT).show();
        }
    }

    private void openExternally() {
        if (sourceUri == null) return;
        try {
            Intent open = new Intent(Intent.ACTION_VIEW);
            open.setDataAndType(sourceUri,
                    TextUtils.isEmpty(mimeType) ? (videoMode ? "video/*" : "audio/*") : mimeType);
            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(open, "Open with"));
        } catch (Exception e) {
            Toast.makeText(this, "दूसरा player नहीं मिला", Toast.LENGTH_SHORT).show();
        }
    }

    private String resolveDisplayName(Uri uri) {
        if (uri == null) return null;
        Cursor c = null;
        try {
            c = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        String last = uri.getLastPathSegment();
        return TextUtils.isEmpty(last) ? null : last;
    }

    private boolean looksLikeVideo(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") ||
                n.endsWith(".3gp") || n.endsWith(".mov") || n.endsWith(".avi") ||
                n.endsWith(".wmv") || n.endsWith(".m4v") || n.endsWith(".mpeg") ||
                n.endsWith(".mpg");
    }

    private String formatTime(int ms) {
        int total = Math.max(0, ms / 1000);
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int seconds = total % 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private void setKeepScreenOn(boolean keep) {
        if (keep) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (videoMode && mediaPlayer == null) prepareMedia(holder);
        else if (videoMode && mediaPlayer != null) {
            try { mediaPlayer.setDisplay(holder); } catch (Exception ignored) {}
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (videoMode && prepared && mediaPlayer != null) {
            try { fitVideoSurface(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight()); } catch (Exception ignored) {}
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (videoMode && mediaPlayer != null) {
            try { mediaPlayer.setDisplay(null); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (prepared && mediaPlayer != null) {
            try {
                resumePlaying = mediaPlayer.isPlaying();
                resumePosition = mediaPlayer.getCurrentPosition();
                if (mediaPlayer.isPlaying()) mediaPlayer.pause();
            } catch (Exception ignored) {}
            updatePlayButton();
        }
        setKeepScreenOn(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prepared && mediaPlayer != null && resumePlaying && !firstStart) {
            startPlayback();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (prepared && mediaPlayer != null) {
            try {
                outState.putInt("position", mediaPlayer.getCurrentPosition());
                outState.putBoolean("playing", mediaPlayer.isPlaying());
            } catch (Exception ignored) {}
        } else {
            outState.putInt("position", resumePosition);
            outState.putBoolean("playing", resumePlaying);
        }
        super.onSaveInstanceState(outState);
    }

    private void releasePlayer() {
        prepared = false;
        if (mediaPlayer != null) {
            try { mediaPlayer.reset(); } catch (Exception ignored) {}
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        setKeepScreenOn(false);
        releasePlayer();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
