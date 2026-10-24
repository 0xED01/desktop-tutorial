package com.aapin.game;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import com.aapin.game.core.SoloGame;
import com.aapin.game.net.NetLink;

public final class GameActivity extends BaseActivity {
    private SoloView solo;
    private VersusView versus;
    private NetLink link;

    public static void start(Context c, String mode, int level) {
        Intent i = new Intent(c, GameActivity.class);
        i.putExtra("mode", mode);
        i.putExtra("level", level);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String mode = getIntent().getStringExtra("mode");
        int level = getIntent().getIntExtra("level", 1);
        View v;
        if ("level".equals(mode)) {
            v = solo = new SoloView(this, SoloGame.LEVEL, level);
            Prefs.addStat(this, "games", 1);
        } else if ("endless".equals(mode)) {
            v = solo = new SoloView(this, SoloGame.ENDLESS, 0);
        } else if ("time".equals(mode)) {
            v = solo = new SoloView(this, SoloGame.TIME, 0);
        } else if ("lan".equals(mode)) {
            link = NetLink.current;
            if (link == null || link.isClosed()) {
                finish();
                return;
            }
            v = versus = new VersusView(this, link.host ? VersusView.HOST : VersusView.CLIENT, link);
        } else {
            v = versus = new VersusView(this, VersusView.LOCAL, null);
        }
        setContentView(v);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (solo != null) solo.pause();
    }

    @Override
    public void onBackPressed() {
        if (solo != null && solo.onBack()) return;
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (solo != null) solo.flushStats();
        if (versus != null) versus.leave();
        if (link != null && NetLink.current == link) NetLink.current = null;
    }
}
