package com.example.liquidprank;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * Full-screen "broken screen" prank.
 *
 * It fills the display with the liquid-leak animation and hides the system
 * bars so it looks like the whole panel has failed.
 *
 * Deliberate design choice: this is an Activity, not a system overlay / service.
 * Pressing Home, swiping away in Recents, or using either of the built-in exit
 * gestures below ends the prank immediately. It is a joke you run on your own
 * phone and can always escape - never a lock that traps whoever is holding it.
 *
 * Exit gestures:
 *   - press BACK three times, or
 *   - long-press anywhere for ~3 seconds.
 */
public class PrankActivity extends Activity {

    private static final int BACK_PRESSES_TO_EXIT = 3;
    private static final long LONG_PRESS_MS = 3000;
    private static final long BACK_WINDOW_MS = 2000;

    private int backCount = 0;
    private long lastBackTime = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable longPressExit = this::finishPrank;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep the screen awake while the gag runs.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LiquidLeakView view = new LiquidLeakView(this);
        setContentView(view);

        enterImmersive();

        // A short buzz when the "damage" appears makes it more convincing.
        buzz(120);

        Toast.makeText(this,
                getString(R.string.exit_hint),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersive();
        }
    }

    private void enterImmersive() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                handler.postDelayed(longPressExit, LONG_PRESS_MS);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressExit);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            long now = System.currentTimeMillis();
            if (now - lastBackTime > BACK_WINDOW_MS) {
                backCount = 0;
            }
            lastBackTime = now;
            backCount++;
            if (backCount >= BACK_PRESSES_TO_EXIT) {
                finishPrank();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void finishPrank() {
        handler.removeCallbacksAndMessages(null);
        buzz(40);
        finish();
    }

    @SuppressWarnings("deprecation")
    private void buzz(long ms) {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(ms);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
