package com.sts.fastbrowser;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.util.Locale;

@UnstableApi
public class MediaPlayerActivity extends Activity {
    private Uri sourceUri;
    private String fileName;
    private String mimeType;
    private boolean videoMode;

    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout mediaStage;
    private TextView playPauseButton;
    private TextView elapsedView;
    private TextView durationView;
    private SeekBar seekBar;
    private View videoTopBar;
    private View videoControlPanel;
    private boolean videoControlsVisible = true;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideVideoControlsRunnable = this::hideVideoControls;
    private boolean userSeeking = false;
    private boolean firstStart = true;
    private long resumePosition = 0L;
    private boolean resumePlaying = true;

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (player != null && !userSeeking) {
                try {
                    long pos = Math.max(0L, player.getCurrentPosition());
                    long dur = player.getDuration();
                    if (dur == C.TIME_UNSET || dur < 0L) dur = 0L;
                    int max = durationToSeekMax(dur);
                    seekBar.setMax(Math.max(1, max));
                    seekBar.setProgress(positionToSeek(pos, dur, max));
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
                (!TextUtils.isEmpty(mimeType) &&
                        mimeType.toLowerCase(Locale.ROOT).startsWith("video/")) ||
                looksLikeVideo(fileName);

        if (savedInstanceState != null) {
            resumePosition = savedInstanceState.getLong("position", 0L);
            resumePlaying = savedInstanceState.getBoolean("playing", true);
            firstStart = false;
        }

        setTitle(fileName);
        if (videoMode) enterVideoImmersive();
        setContentView(buildUi());
        initializePlayer();
        if (videoMode) scheduleVideoControlsHide();
        handler.post(progressUpdater);
    }

    private View buildUi() {
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(4), dp(3), dp(4), dp(3));
        GradientDrawable topBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#D9F0EE"), Color.parseColor("#E3EAF4"), Color.parseColor("#EEE8F4")}
        );
        topBar.setBackground(topBg);

        TextView back = makeTopButton("‹", 30);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        topBar.addView(back, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText(fileName);
        title.setTextColor(Color.parseColor("#162326"));
        title.setTextSize(14);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(6), 0, dp(8), 0);
        topBar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView menu = makeTopButton("⋮", 26);
        menu.setContentDescription("Menu");
        menu.setOnClickListener(v -> {
            showVideoControls();
            showMenu(v);
        });
        topBar.addView(menu, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.MATCH_PARENT));

        mediaStage = new FrameLayout(this);
        mediaStage.setBackgroundColor(Color.BLACK);

        if (videoMode) {
            playerView = new PlayerView(this);
            playerView.setUseController(false);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setBackgroundColor(Color.BLACK);
            playerView.setClickable(true);
            playerView.setOnClickListener(v -> toggleVideoControls());
            mediaStage.addView(playerView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER));
        } else {
            buildAudioStage();
        }

        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setPadding(dp(12), dp(8), dp(12), dp(12));
        controlPanel.setBackgroundColor(videoMode
                ? Color.parseColor("#CC101010")
                : Color.parseColor("#101010"));

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
        timeRow.addView(elapsedView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        timeRow.addView(durationView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, dp(4), 0, 0);
        controlPanel.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        TextView rewind = makeControlButton("↶ 10", 16);
        playPauseButton = makeControlButton("▶", 28);
        TextView forward = makeControlButton("10 ↷", 16);

        rewind.setOnClickListener(v -> {
            seekRelative(-10000L);
            showVideoControls();
        });
        playPauseButton.setOnClickListener(v -> {
            togglePlayback();
            showVideoControls();
        });
        forward.setOnClickListener(v -> {
            seekRelative(10000L);
            showVideoControls();
        });

        buttons.addView(rewind, new LinearLayout.LayoutParams(dp(90), dp(52)));
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(dp(72), dp(56));
        centerLp.setMargins(dp(12), 0, dp(12), 0);
        buttons.addView(playPauseButton, centerLp);
        buttons.addView(forward, new LinearLayout.LayoutParams(dp(90), dp(52)));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || player == null) return;
                long dur = player.getDuration();
                if (dur == C.TIME_UNSET || dur <= 0L) return;
                long pos = seekToPosition(progress, bar.getMax(), dur);
                elapsedView.setText(formatTime(pos));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
                showVideoControls();
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                if (player != null) {
                    long dur = player.getDuration();
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        player.seekTo(seekToPosition(bar.getProgress(), bar.getMax(), dur));
                    }
                }
                scheduleVideoControlsHide();
            }
        });

        if (videoMode) {
            FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(Color.BLACK);
            root.addView(mediaStage, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44), Gravity.TOP);
            root.addView(topBar, topLp);

            FrameLayout.LayoutParams controlsLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            root.addView(controlPanel, controlsLp);

            videoTopBar = topBar;
            videoControlPanel = controlPanel;
            return root;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        root.addView(mediaStage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(controlPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private void buildAudioStage() {
        LinearLayout audioPanel = new LinearLayout(this);
        audioPanel.setOrientation(LinearLayout.VERTICAL);
        audioPanel.setGravity(Gravity.CENTER);
        audioPanel.setPadding(dp(28), dp(24), dp(28), dp(24));

        TextView artwork = new TextView(this);
        artwork.setText("♫");
        artwork.setTextColor(Color.WHITE);
        artwork.setTextSize(84);
        artwork.setGravity(Gravity.CENTER);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor("#252525"));
        circle.setStroke(dp(2), Color.parseColor("#5B7FA3"));
        artwork.setBackground(circle);
        audioPanel.addView(artwork, new LinearLayout.LayoutParams(dp(190), dp(190)));

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

    private void enterVideoImmersive() {
        if (!videoMode) return;
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void showVideoControls() {
        if (!videoMode || videoTopBar == null || videoControlPanel == null) return;
        handler.removeCallbacks(hideVideoControlsRunnable);
        videoControlsVisible = true;
        videoTopBar.setVisibility(View.VISIBLE);
        videoControlPanel.setVisibility(View.VISIBLE);
        videoTopBar.animate().alpha(1f).setDuration(140).start();
        videoControlPanel.animate().alpha(1f).setDuration(140).start();
        scheduleVideoControlsHide();
    }

    private void hideVideoControls() {
        if (!videoMode || videoTopBar == null || videoControlPanel == null || userSeeking) return;
        videoControlsVisible = false;
        videoTopBar.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            if (!videoControlsVisible) videoTopBar.setVisibility(View.GONE);
        }).start();
        videoControlPanel.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            if (!videoControlsVisible) videoControlPanel.setVisibility(View.GONE);
        }).start();
        enterVideoImmersive();
    }

    private void toggleVideoControls() {
        if (!videoMode) return;
        if (videoControlsVisible) hideVideoControls();
        else showVideoControls();
    }

    private void scheduleVideoControlsHide() {
        if (!videoMode) return;
        handler.removeCallbacks(hideVideoControlsRunnable);
        handler.postDelayed(hideVideoControlsRunnable, 2600L);
    }

    private void initializePlayer() {
        if (sourceUri == null) {
            Toast.makeText(this, "Media file नहीं मिली", Toast.LENGTH_SHORT).show();
            return;
        }

        releasePlayer();

        player = new ExoPlayer.Builder(this).build();
        if (playerView != null) playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    long dur = player.getDuration();
                    if (dur == C.TIME_UNSET || dur < 0L) dur = 0L;
                    seekBar.setMax(Math.max(1, durationToSeekMax(dur)));
                    durationView.setText(formatTime(dur));

                    if (resumePosition > 0L) {
                        player.seekTo(resumePosition);
                        resumePosition = 0L;
                    }

                    boolean shouldPlay = firstStart || resumePlaying;
                    firstStart = false;
                    player.setPlayWhenReady(shouldPlay);
                    updatePlayButton();
                } else if (playbackState == Player.STATE_ENDED) {
                    updatePlayButton();
                    setKeepScreenOn(false);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayButton();
                setKeepScreenOn(videoMode && isPlaying);
                if (videoMode) {
                    if (isPlaying) scheduleVideoControlsHide();
                    else showVideoControls();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(MediaPlayerActivity.this,
                        "यह video/audio format इस device पर play नहीं हो पाया",
                        Toast.LENGTH_SHORT).show();
                updatePlayButton();
                setKeepScreenOn(false);
            }
        });

        MediaItem item = new MediaItem.Builder()
                .setUri(sourceUri)
                .setMimeType(TextUtils.isEmpty(mimeType) ? null : mimeType)
                .build();
        player.setMediaItem(item);
        player.prepare();
    }

    private void togglePlayback() {
        if (player == null) return;
        int state = player.getPlaybackState();
        if (state == Player.STATE_ENDED) player.seekTo(0L);
        if (player.isPlaying()) player.pause();
        else player.play();
        updatePlayButton();
    }

    private void updatePlayButton() {
        if (playPauseButton == null) return;
        boolean playing = player != null && player.isPlaying();
        playPauseButton.setText(playing ? "❚❚" : "▶");
    }

    private void seekRelative(long deltaMs) {
        if (player == null) return;
        long duration = player.getDuration();
        long current = player.getCurrentPosition();
        long max = (duration == C.TIME_UNSET || duration < 0L)
                ? Math.max(current, current + Math.max(0L, deltaMs))
                : duration;
        long target = Math.max(0L, Math.min(max, current + deltaMs));
        player.seekTo(target);
        elapsedView.setText(formatTime(target));
    }

    private int durationToSeekMax(long duration) {
        if (duration <= 0L) return 1;
        return duration > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) duration;
    }

    private int positionToSeek(long position, long duration, int seekMax) {
        if (duration <= 0L || seekMax <= 0) return 0;
        if (duration <= Integer.MAX_VALUE) {
            return (int) Math.max(0L, Math.min(position, duration));
        }
        return (int) Math.max(0L,
                Math.min(seekMax, Math.round((position / (double) duration) * seekMax)));
    }

    private long seekToPosition(int progress, int seekMax, long duration) {
        if (duration <= 0L || seekMax <= 0) return 0L;
        if (duration <= Integer.MAX_VALUE) {
            return Math.max(0L, Math.min((long) progress, duration));
        }
        return Math.max(0L,
                Math.min(duration, Math.round((progress / (double) seekMax) * duration)));
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

    private String formatTime(long ms) {
        long total = Math.max(0L, ms / 1000L);
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        if (hours > 0L) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private void setKeepScreenOn(boolean keep) {
        if (keep) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && videoMode) enterVideoImmersive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            resumePlaying = player.isPlaying();
            resumePosition = player.getCurrentPosition();
            player.pause();
        }
        setKeepScreenOn(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && resumePlaying && !firstStart &&
                player.getPlaybackState() == Player.STATE_READY) {
            player.play();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (player != null) {
            outState.putLong("position", player.getCurrentPosition());
            outState.putBoolean("playing", player.isPlaying());
        } else {
            outState.putLong("position", resumePosition);
            outState.putBoolean("playing", resumePlaying);
        }
        super.onSaveInstanceState(outState);
    }

    private void releasePlayer() {
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(hideVideoControlsRunnable);
        handler.removeCallbacksAndMessages(null);
        setKeepScreenOn(false);
        releasePlayer();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
