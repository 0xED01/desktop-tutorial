package com.aapin.game;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** 全屏竖屏的基础 Activity。 */
public abstract class BaseActivity extends Activity {
    protected Theme th;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            // 让内容延伸到刘海/挖孔区域，避免顶部出现黑条
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        th = Theme.current(this);
        Sfx.init(this);
    }

    /** 菜单页面（ScrollView）在顶部留出刘海安全区；游戏画面自己处理。 */
    @Override
    public void setContentView(View v) {
        if (v instanceof android.widget.ScrollView && android.os.Build.VERSION.SDK_INT >= 28) {
            final int baseTop = v.getPaddingTop();
            v.setOnApplyWindowInsetsListener((view, in) -> {
                int cut = in.getDisplayCutout() != null ? in.getDisplayCutout().getSafeInsetTop() : 0;
                view.setPadding(view.getPaddingLeft(), baseTop + cut, view.getPaddingRight(), view.getPaddingBottom());
                return in;
            });
        }
        super.setContentView(v);
    }

    @Override
    protected void onResume() {
        super.onResume();
        th = Theme.current(this);
        immersive();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @SuppressWarnings("deprecation")
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
