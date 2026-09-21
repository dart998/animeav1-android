package com.ovelayos.animeav1;

import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.KeyEvent;
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
    private MediaController controls;
    private int position;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);setTitle(getIntent().getStringExtra(EXTRA_TITLE));
        String path=getIntent().getStringExtra(EXTRA_PATH);if(path==null||!new File(path).isFile()){finish();return;}
        video=new VideoView(this);video.setBackgroundColor(android.graphics.Color.BLACK);setContentView(video);
        controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);video.setVideoURI(Uri.fromFile(new File(path)));
        video.setOnPreparedListener(player->{player.setScreenOnWhilePlaying(true);video.seekTo(position);video.start();});
        video.setOnCompletionListener(player->controls.show());
        hideSystemBars();
    }
    @SuppressWarnings("deprecation") private void hideSystemBars(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());}}else video.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)hideSystemBars();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(video!=null&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){switch(event.getKeyCode()){case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:if(video.isPlaying())video.pause();else video.start();controls.show();return true;case KeyEvent.KEYCODE_MEDIA_PLAY:video.start();controls.show();return true;case KeyEvent.KEYCODE_MEDIA_PAUSE:video.pause();controls.show();return true;case KeyEvent.KEYCODE_DPAD_LEFT:case KeyEvent.KEYCODE_MEDIA_REWIND:video.seekTo(Math.max(0,video.getCurrentPosition()-10000));controls.show();return true;case KeyEvent.KEYCODE_DPAD_RIGHT:case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:int duration=video.getDuration();video.seekTo(duration>0?Math.min(duration,video.getCurrentPosition()+10000):video.getCurrentPosition()+10000);controls.show();return true;}}return super.dispatchKeyEvent(event);}
    @Override protected void onPause(){if(video!=null){position=video.getCurrentPosition();video.pause();}super.onPause();}
    @Override protected void onResume(){super.onResume();if(video!=null&&position>0){video.seekTo(position);video.start();}}
}
