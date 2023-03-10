package cn.lienoo.exoplayerextend;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;
import java.util.UUID;

/** Activity that demonstrates use of {@link SurfaceControl} with ExoPlayer. */
public final class MainActivity extends Activity {

    private static final String DEFAULT_MEDIA_URI = "smb://smb:smb123@192.168.1.232/Videos/record.h264";
    private static final String SURFACE_CONTROL_NAME = "surfacedemo";

    private static final String ACTION_VIEW = "com.google.android.exoplayer.surfacedemo.action.VIEW";
    private static final String EXTENSION_EXTRA = "extension";
    private static final String DRM_SCHEME_EXTRA = "drm_scheme";
    private static final String DRM_LICENSE_URL_EXTRA = "drm_license_url";
    private static final String OWNER_EXTRA = "owner";

    private boolean isOwner;
    @Nullable private PlayerControlView playerControlView;
    @Nullable private SurfaceView fullScreenView;
    @Nullable private SurfaceView nonFullScreenView;
    @Nullable private SurfaceView currentOutputView;

    @Nullable private static ExoPlayer player;
    @Nullable private static SurfaceControl surfaceControl;
    @Nullable private static Surface videoSurface;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        playerControlView = findViewById(R.id.player_control_view);
        fullScreenView = findViewById(R.id.full_screen_view);
        fullScreenView.setOnClickListener(
                v -> {
                    setCurrentOutputView(nonFullScreenView);
                    Assertions.checkNotNull(fullScreenView).setVisibility(View.GONE);
                });
        attachSurfaceListener(fullScreenView);
        isOwner = getIntent().getBooleanExtra(OWNER_EXTRA, /* defaultValue= */ true);
        GridLayout gridLayout = findViewById(R.id.grid_layout);
        for (int i = 0; i < 9; i++) {
            View view;
            if (i == 0) {
                Button button = new Button(/* context= */ this);
                view = button;
                button.setText(getString(R.string.no_output_label));
                button.setOnClickListener(v -> reparent(/* surfaceView= */ null));
            } else if (i == 1) {
                Button button = new Button(/* context= */ this);
                view = button;
                button.setText(getString(R.string.full_screen_label));
                button.setOnClickListener(
                        v -> {
                            setCurrentOutputView(fullScreenView);
                            Assertions.checkNotNull(fullScreenView).setVisibility(View.VISIBLE);
                        });
            } else if (i == 2) {
                Button button = new Button(/* context= */ this);
                view = button;
                button.setText(getString(R.string.new_activity_label));
                button.setOnClickListener(
                        v ->
                                startActivity(
                                        new Intent(MainActivity.this, MainActivity.class)
                                                .putExtra(OWNER_EXTRA, /* value= */ false)));
            } else {
                SurfaceView surfaceView = new SurfaceView(this);
                view = surfaceView;
                attachSurfaceListener(surfaceView);
                surfaceView.setOnClickListener(
                        v -> {
                            setCurrentOutputView(surfaceView);
                            nonFullScreenView = surfaceView;
                        });
                if (nonFullScreenView == null) {
                    nonFullScreenView = surfaceView;
                }
            }
            gridLayout.addView(view);
            GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams();
            layoutParams.width = 0;
            layoutParams.height = 0;
            layoutParams.columnSpec = GridLayout.spec(i % 3, 1f);
            layoutParams.rowSpec = GridLayout.spec(i / 3, 1f);
            layoutParams.bottomMargin = 10;
            layoutParams.leftMargin = 10;
            layoutParams.topMargin = 10;
            layoutParams.rightMargin = 10;
            view.setLayoutParams(layoutParams);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (isOwner && player == null) {
            initializePlayer();
        }

        setCurrentOutputView(nonFullScreenView);

        PlayerControlView playerControlView = Assertions.checkNotNull(this.playerControlView);
        playerControlView.setPlayer(player);
        playerControlView.show();
    }

    @Override
    public void onPause() {
        super.onPause();

        Assertions.checkNotNull(playerControlView).setPlayer(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isOwner && isFinishing()) {
            if (surfaceControl != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    surfaceControl.release();
                }
                surfaceControl = null;
            }
            if (videoSurface != null) {
                videoSurface.release();
                videoSurface = null;
            }
            if (player != null) {
                player.release();
                player = null;
            }
        }
    }

    private void initializePlayer() {
        Intent intent = getIntent();
        String action = intent.getAction();
        AssetManager assetManager = getAssets();
        String assetUri = null;
        try {
            for (String asset : assetManager.list("")) {
                if (asset.endsWith(".h264"))
                    assetUri = "asset:///" + asset;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Uri uri =
                ACTION_VIEW.equals(action)
                        ? Assertions.checkNotNull(intent.getData())
                        : Uri.parse(assetUri);
        DrmSessionManager drmSessionManager;
        if (intent.hasExtra(DRM_SCHEME_EXTRA)) {
            String drmScheme = Assertions.checkNotNull(intent.getStringExtra(DRM_SCHEME_EXTRA));
            String drmLicenseUrl = Assertions.checkNotNull(intent.getStringExtra(DRM_LICENSE_URL_EXTRA));
            UUID drmSchemeUuid = Assertions.checkNotNull(Util.getDrmUuid(drmScheme));
            DataSource.Factory licenseDataSourceFactory = new DefaultHttpDataSource.Factory();
            HttpMediaDrmCallback drmCallback =
                    new HttpMediaDrmCallback(drmLicenseUrl, licenseDataSourceFactory);
            drmSessionManager =
                    new DefaultDrmSessionManager.Builder()
                            .setUuidAndExoMediaDrmProvider(drmSchemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                            .build(drmCallback);
        } else {
            drmSessionManager = DrmSessionManager.DRM_UNSUPPORTED;
        }

//        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
//        MediaSource mediaSource;
//        @Nullable String fileExtension = intent.getStringExtra(EXTENSION_EXTRA);
//        @C.ContentType
//        int type =
//                TextUtils.isEmpty(fileExtension)
//                        ? Util.inferContentType(uri)
//                        : Util.inferContentTypeForExtension(fileExtension);
//        if (type == C.CONTENT_TYPE_DASH) {
//            mediaSource =
//                    new DashMediaSource.Factory(dataSourceFactory)
//                            .setDrmSessionManagerProvider(unusedMediaItem -> drmSessionManager)
//                            .createMediaSource(MediaItem.fromUri(uri));
//        } else if (type == C.CONTENT_TYPE_OTHER) {
//            mediaSource =
//                    new ProgressiveMediaSource.Factory(dataSourceFactory)
//                            .setDrmSessionManagerProvider(unusedMediaItem -> drmSessionManager)
//                            .createMediaSource(MediaItem.fromUri(uri));
//        } else {
//            throw new IllegalStateException();
//        }

//        DataSource.Factory dataSourceFactory = new SmbDataSourceFactory(DEFAULT_MEDIA_URI);
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
        MediaSource mediaSource =
                new ProgressiveMediaSource.Factory(dataSourceFactory, new ExtendExtractorsFactory())
                        .setDrmSessionManagerProvider(unusedMediaItem -> drmSessionManager)
                        .createMediaSource(MediaItem.fromUri(uri));



        ExoPlayer player = new ExoPlayer.Builder(getApplicationContext()).build();
        player.setMediaSource(mediaSource);
        player.prepare();
        player.play();
        player.setRepeatMode(Player.REPEAT_MODE_ALL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            surfaceControl =
                    new SurfaceControl.Builder()
                            .setName(SURFACE_CONTROL_NAME)
                            .setBufferSize(/* width= */ 0, /* height= */ 0)
                            .build();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            videoSurface = new Surface(surfaceControl);
        }
        player.setVideoSurface(videoSurface);
        MainActivity.player = player;
    }

    private void setCurrentOutputView(@Nullable SurfaceView surfaceView) {
        currentOutputView = surfaceView;
        if (surfaceView != null && surfaceView.getHolder().getSurface() != null) {
            reparent(surfaceView);
        }
    }

    private void attachSurfaceListener(SurfaceView surfaceView) {
        surfaceView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                                if (surfaceView == currentOutputView) {
                                    reparent(surfaceView);
                                }
                            }

                            @Override
                            public void surfaceChanged(
                                    SurfaceHolder surfaceHolder, int format, int width, int height) {}

                            @Override
                            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {}
                        });
    }

    private static void reparent(@Nullable SurfaceView surfaceView) {
        SurfaceControl surfaceControl = Assertions.checkNotNull(MainActivity.surfaceControl);
        if (surfaceView == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                new SurfaceControl.Transaction()
                        .reparent(surfaceControl, /* newParent= */ null)
                        .setBufferSize(surfaceControl, /* w= */ 0, /* h= */ 0)
                        .setVisibility(surfaceControl, /* visible= */ false)
                        .apply();
            }
        } else {
            SurfaceControl newParentSurfaceControl = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                newParentSurfaceControl = surfaceView.getSurfaceControl();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                new SurfaceControl.Transaction()
                        .reparent(surfaceControl, newParentSurfaceControl)
                        .setBufferSize(surfaceControl, surfaceView.getWidth(), surfaceView.getHeight())
                        .setVisibility(surfaceControl, /* visible= */ true)
                        .apply();
            }
        }
    }
}
