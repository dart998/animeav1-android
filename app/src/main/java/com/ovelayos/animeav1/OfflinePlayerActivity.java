package com.ovelayos.animeav1;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;

public class OfflinePlayerActivity extends Activity {
    public static final String EXTRA_PATH="path",EXTRA_TITLE="title",EXTRA_EPISODE="episode";

    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String path=getIntent().getStringExtra(EXTRA_PATH);String title=getIntent().getStringExtra(EXTRA_TITLE);int ep=getIntent().getIntExtra(EXTRA_EPISODE,0);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(16,15,20));
        TextView head=new TextView(this);head.setText((title==null||title.isEmpty()?"AnimeAV1":title)+(ep>0?" · Episodio "+ep:""));head.setTextColor(Color.WHITE);head.setTextSize(18);head.setPadding(dp(16),dp(14),dp(16),dp(12));root.addView(head,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout box=new FrameLayout(this);box.setBackgroundColor(Color.BLACK);VideoView video=new VideoView(this);MediaController controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);box.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));root.addView(box,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        if(path==null||!new File(path).isFile()){head.setText("El archivo offline ya no existe");return;}video.setVideoURI(Uri.fromFile(new File(path)));video.setOnPreparedListener(mp->{mp.setLooping(false);video.start();});}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
}
