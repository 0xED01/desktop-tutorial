package com.aapin.game;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.aapin.game.core.Level;

public final class MainActivity extends BaseActivity {

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackground(Ui.background(th));
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(this, 28);
        col.setPadding(pad, Ui.dp(this, 24), pad, Ui.dp(this, 24));
        sv.addView(col, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(new MenuDiscView(this, th), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 190)));
        TextView title = Ui.text(this, "见缝插针", 42, th.text, true);
        title.setGravity(Gravity.CENTER);
        col.addView(title);
        TextView stars = Ui.text(this, "★ " + Prefs.totalStars(this) + " / " + Level.COUNT * 3
                + "     无尽最佳 " + Prefs.best(this, "endless"), 15, Ui.sub(th), false);
        stars.setGravity(Gravity.CENTER);
        col.addView(stars, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 6)));

        int m = Ui.dp(this, 6);
        int full = ViewGroup.LayoutParams.MATCH_PARENT, wrap = ViewGroup.LayoutParams.WRAP_CONTENT;
        col.addView(Ui.button(this, "闯关模式", th.accent, 0xFFFFFFFF,
                v -> startActivity(new Intent(this, LevelSelectActivity.class))), Ui.lp(full, wrap, m));
        LinearLayout row = new LinearLayout(this);
        row.addView(Ui.button(this, "无尽模式", th.p1, 0xFFFFFFFF, v -> GameActivity.start(this, "endless", 0)),
                weight(m));
        row.addView(Ui.button(this, "限时 60 秒", th.p1, 0xFFFFFFFF, v -> GameActivity.start(this, "time", 0)),
                weight(m));
        col.addView(row, Ui.lp(full, wrap, 0));
        LinearLayout row2 = new LinearLayout(this);
        row2.addView(Ui.button(this, "本机双人", th.p2, 0xFFFFFFFF, v -> GameActivity.start(this, "local", 0)),
                weight(m));
        row2.addView(Ui.button(this, "局域网联机", th.p2, 0xFFFFFFFF,
                v -> startActivity(new Intent(this, LanActivity.class))), weight(m));
        col.addView(row2, Ui.lp(full, wrap, 0));
        int grey = Painter.blend(th.text, th.bgTop, 0.55f);
        LinearLayout row3 = new LinearLayout(this);
        row3.addView(Ui.button(this, "皮肤与设置", grey, 0xFFFFFFFF,
                v -> startActivity(new Intent(this, SettingsActivity.class))), weight(m));
        row3.addView(Ui.button(this, "玩法说明", grey, 0xFFFFFFFF, v -> help()), weight(m));
        col.addView(row3, Ui.lp(full, wrap, 0));
        setContentView(sv);
    }

    private LinearLayout.LayoutParams weight(int m) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(m, m, m, m);
        return p;
    }

    private void help() {
        new AlertDialog.Builder(this)
                .setTitle("玩法说明")
                .setMessage("点击屏幕把针射向旋转的圆盘，针不能碰到盘上已有的针。把所有针都插上即可过关。\n\n"
                        + "【闯关模式】6 章共 60 关：匀速、反转、变速、急停、逆转、幽灵、摇摆、加速……"
                        + "一次通过得 3 星，星星可解锁新皮肤。\n\n"
                        + "【无尽模式】一盘接一盘，速度越来越快，撞针即结束。快速连续插针可触发连击加分。\n\n"
                        + "【限时 60 秒】在 60 秒内尽量多插针；清盘 +5 秒，撞针 -5 秒。\n\n"
                        + "【本机双人】两人分坐手机两端，点击自己半场发射。撞针判负，先插完自己的针获胜，先赢 3 局者胜。\n\n"
                        + "【局域网联机】两台手机连接同一 WiFi（或一台开热点、另一台连接），一方创建房间，另一方加入即可对战。")
                .setPositiveButton("知道了", null)
                .show();
    }
}
