package com.ovelayos.animeav1;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class DownloadsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
    }
}
