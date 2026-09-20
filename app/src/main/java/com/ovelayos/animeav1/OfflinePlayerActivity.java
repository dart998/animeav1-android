package com.ovelayos.animeav1;

import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.VideoView;

import java.io.File;

public final class OfflinePlayerActivity extends Activity {
    static final String EXTRA_PATH="path";
    static final String EXTRA_TITLE="title";
    private VideoView video;
    private int position;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);setTitle(getIntent().getStringExtra(EXTRA_TITLE));
        String path=getIntent().getStringExtra(EXTRA_PATH);if(path==null||!new File(path).isFile()){finish();return;}
        video=new VideoView(this);video.setBackgroundColor(android.graphics.Color.BLACK);setContentView(video);
        MediaController controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);video.setVideoURI(Uri.fromFile(new File(path)));
        video.setOnPreparedListener(player->{player.setScreenOnWhilePlaying(true);video.seekTo(position);video.start();});
        video.setOnCompletionListener(player->controls.show());
        hideSystemBars();
    }
    @SuppressWarnings("deprecation") private void hideSystemBars(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());}}else video.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)hideSystemBars();}
    @Override protected void onPause(){if(video!=null)position=video.getCurrentPosition();super.onPause();}
    @Override protected void onResume(){super.onResume();if(video!=null&&position>0){video.seekTo(position);video.start();}}
}
