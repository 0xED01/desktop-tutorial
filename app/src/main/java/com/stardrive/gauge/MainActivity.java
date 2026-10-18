package com.stardrive.gauge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.function.IntConsumer;

public class MainActivity extends Activity implements LocationListener, SensorEventListener {
    private static final int DIALOG_THEME = android.R.style.Theme_Material_Dialog_Alert;
    private static final String[] DIRS = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};

    // Trip state lives in statics so it survives rotation / activity recreation.
    private static double tripM, odoM, altitude = Double.NaN;
    private static long movingMs;
    private static float maxMs, speedMs, gNow, gPeak, best0100, heading;
    private static boolean stateLoaded;

    private static final int AT_OFF = 0, AT_WAIT_STOP = 1, AT_READY = 2, AT_RUN = 3, AT_DONE = 4;
    private int atState;
    private long atStart;
    private float atResult;

    private SharedPreferences prefs;
    private int accentIdx, style, unit, limit, scaleIdx, orient;
    private boolean demo, fullscreen, mirror, askedPerm, hasPermission, gpsHeading;

    private GaugeView gauge;
    private TextView tvClock, tvGps, tvHeading;
    private final TextView[] values = new TextView[9];
    private final TextView[] labels = new TextView[9];
    private final View[] cells = new View[9];
    private final TextView[] buttons = new TextView[6];

    private LocationManager lm;
    private SensorManager sm;
    private GnssStatus.Callback gnssCb;
    private Location lastLoc;
    private long lastFixAt, lastTick, lastBeep, prevAt;
    private float prevMs;
    private int satUsed, satTotal, tickCount;
    private ToneGenerator tone;
    private boolean wasOver;
    private final float[] rotM = new float[9], ori = new float[3];

    private float demoSpeed, demoTarget;
    private long demoNext;
    private final Random rnd = new Random();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);

    // ---- lifecycle ------------------------------------------------------

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = Cfg.prefs(this);
        if (!stateLoaded) {
            odoM = Double.longBitsToDouble(prefs.getLong("odo", 0L));
            best0100 = prefs.getFloat("best", 0f);
            stateLoaded = true;
        }
        loadPrefs();
        applyOrientation();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        setContentView(R.layout.activity_main);

        gauge = findViewById(R.id.gauge);
        tvClock = findViewById(R.id.tv_clock);
        tvGps = findViewById(R.id.tv_gps);
        tvHeading = findViewById(R.id.tv_heading);
        buildInfoGrid();
        buildButtons();
        setupGestures();

        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
        } catch (RuntimeException e) {
            tone = null;
        }
        if (saved != null) {
            fullscreen = saved.getBoolean("fs");
            mirror = saved.getBoolean("mirror");
            atState = saved.getInt("at");
        }
        applySettings();
        applyMirror();
        gauge.setSpeed(toDisplay(speedMs));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("fs", fullscreen);
        out.putBoolean("mirror", mirror);
        out.putInt("at", atState == AT_RUN ? AT_WAIT_STOP : atState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sm != null) {
            Sensor rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            Sensor la = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            if (rv != null) sm.registerListener(this, rv, SensorManager.SENSOR_DELAY_UI);
            if (la != null) sm.registerListener(this, la, SensorManager.SENSOR_DELAY_GAME);
        }
        startLocation();
        lastTick = 0;
        handler.post(tick);
        applyFullscreen();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        if (sm != null) sm.unregisterListener(this);
        stopLocation();
        saveTotals();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tone != null) tone.release();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && fullscreen) setSystemBarsHidden(true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (fullscreen) {
            fullscreen = false;
            applyFullscreen();
        } else {
            super.onBackPressed();
        }
    }

    // ---- preferences ----------------------------------------------------

    private void loadPrefs() {
        accentIdx = prefs.getInt("accent", 0);
        style = prefs.getInt("style", GaugeView.STYLE_CLASSIC);
        unit = prefs.getInt("unit", 0);
        limit = prefs.getInt("limit", 0);
        scaleIdx = prefs.getInt("scale", 1);
        demo = prefs.getBoolean("demo", false);
        orient = prefs.getInt("orient", 0);
    }

    private void saveTotals() {
        prefs.edit().putLong("odo", Double.doubleToLongBits(odoM)).putFloat("best", best0100).apply();
    }

    private void applyOrientation() {
        int o = orient == 1 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : orient == 2 ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
        if (getRequestedOrientation() != o) setRequestedOrientation(o);
    }

    private int accent() {
        return Cfg.ACCENTS[accentIdx];
    }

    private String speedUnit() {
        return unit == 0 ? "km/h" : "mph";
    }

    private float toDisplay(float ms) {
        return ms * (unit == 0 ? Cfg.MS_TO_KMH : Cfg.MS_TO_MPH);
    }

    private String sprintLabel() {
        return unit == 0 ? "0-100" : "0-60";
    }

    private void applySettings() {
        gauge.setStyle(style);
        gauge.setAccent(accent());
        gauge.setUnit(speedUnit());
        gauge.setMaxScale(Cfg.SCALES[unit][scaleIdx]);
        gauge.setLimit(limit);
        gauge.setMax(toDisplay(maxMs));
        gauge.setSpeed(toDisplay(speedMs));
        labels[8].setText(sprintLabel() + " 最佳");
        buttons[2].setText(sprintLabel());
        int acc = accent();
        for (TextView b : buttons) b.setBackground(buttonBg(acc));
        for (TextView l : labels) l.setTextColor(acc);
        updatePanels();
    }

    // ---- UI construction -----------------------------------------------

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private GradientDrawable cellBg() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF16191E, 0xFF0A0B0D});
        d.setCornerRadius(dp(10));
        d.setStroke(dp(1), 0x26FFFFFF);
        return d;
    }

    private GradientDrawable buttonBg(int acc) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1A1D22, 0xFF0C0D10});
        d.setCornerRadius(dp(20));
        d.setStroke(dp(1), (acc & 0x00FFFFFF) | 0xAA000000);
        return d;
    }

    private void buildInfoGrid() {
        LinearLayout grid = findViewById(R.id.info_grid);
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        String[] names = {"本次里程", "行驶时间", "平均速度", "最高速度", "海拔",
                "方向", "G 值 / 峰值", "总里程", "0-100 最佳"};
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(row, land
                    ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                    : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            for (int col = 0; col < 3; col++) {
                int i = r * 3 + col;
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(4), dp(8), dp(4), dp(8));
                cell.setBackground(cellBg());
                TextView v = new TextView(this);
                v.setTextColor(0xFFF2F4F6);
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                v.setTypeface(android.graphics.Typeface.create("sans-serif-light", 0));
                v.setSingleLine(true);
                v.setGravity(Gravity.CENTER);
                v.setText("--");
                TextView l = new TextView(this);
                l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                l.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", 0));
                l.setText(names[i]);
                l.setSingleLine(true);
                l.setGravity(Gravity.CENTER);
                cell.addView(v);
                cell.addView(l);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(cell, lp);
                values[i] = v;
                labels[i] = l;
                cells[i] = cell;
            }
        }
        // long-press the 0-100 cell to clear the best time
        cells[8].setOnLongClickListener(v -> {
            best0100 = 0;
            saveTotals();
            updatePanels();
            toast("已清除最佳成绩");
            return true;
        });
    }

    private void buildButtons() {
        LinearLayout bar = findViewById(R.id.button_bar);
        String[] names = {"全屏", "HUD", "0-100", "风格", "重置", "设置"};
        Runnable[] actions = {
                this::toggleFullscreen, this::toggleMirror, this::toggleSprint,
                this::cycleStyle, this::confirmReset, this::showSettings
        };
        for (int i = 0; i < names.length; i++) {
            TextView b = new TextView(this);
            b.setText(names[i]);
            b.setTextColor(0xFFE6E9EC);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            b.setGravity(Gravity.CENTER);
            b.setSingleLine(true);
            b.setPadding(0, dp(10), 0, dp(10));
            Runnable a = actions[i];
            b.setOnClickListener(v -> a.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            bar.addView(b, lp);
            buttons[i] = b;
        }
    }

    private void setupGestures() {
        GestureDetector det = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                toggleFullscreen();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                toggleMirror();
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (fullscreen) toast("双击退出全屏 · 长按切换 HUD 镜像");
                return true;
            }
        });
        gauge.setOnTouchListener((v, e) -> det.onTouchEvent(e));
    }

    // ---- actions --------------------------------------------------------

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        applyFullscreen();
        if (fullscreen) toast("全屏码表 · 双击退出");
    }

    private void applyFullscreen() {
        int vis = fullscreen ? View.GONE : View.VISIBLE;
        for (int id : new int[]{R.id.top_bar, R.id.info_grid, R.id.button_bar, R.id.side}) {
            View v = findViewById(id);
            if (v != null) v.setVisibility(vis);
        }
        gauge.setExtras(fullscreen);
        setSystemBarsHidden(fullscreen);
    }

    @SuppressWarnings("deprecation")
    private void setSystemBarsHidden(boolean on) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c == null) return;
            if (on) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(on
                    ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    : 0);
        }
    }

    private void toggleMirror() {
        mirror = !mirror;
        applyMirror();
        toast(mirror ? "HUD 镜像已开启：手机平放在挡风玻璃下方，屏幕亮度已调至最高" : "HUD 镜像已关闭");
    }

    private void applyMirror() {
        gauge.setScaleX(mirror ? -1f : 1f);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = mirror ? 1f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
    }

    private void toggleSprint() {
        if (atState == AT_OFF || atState == AT_DONE) {
            atState = speedMs < 0.5f ? AT_READY : AT_WAIT_STOP;
            toast(sprintLabel() + " 加速测试已就绪，起步即开始计时");
        } else {
            atState = AT_OFF;
            toast("加速测试已取消");
        }
        updateNote(SystemClock.elapsedRealtime());
    }

    private void cycleStyle() {
        style = (style + 1) % Cfg.STYLE_NAMES.length;
        prefs.edit().putInt("style", style).apply();
        gauge.setStyle(style);
        toast("表盘风格：" + Cfg.STYLE_NAMES[style]);
    }

    private void confirmReset() {
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("重置本次行程")
                .setMessage("将清零本次里程、行驶时间、最高速度和 G 值峰值。总里程不受影响。")
                .setPositiveButton("重置", (d, w) -> {
                    tripM = 0;
                    movingMs = 0;
                    maxMs = 0;
                    gPeak = 0;
                    gauge.setMax(0);
                    updatePanels();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSettings() {
        String[] items = {
                "单位：" + speedUnit(),
                "氛围灯颜色：" + Cfg.ACCENT_NAMES[accentIdx],
                "表盘风格：" + Cfg.STYLE_NAMES[style],
                "超速提醒：" + (limit == 0 ? "关闭" : limit + " " + speedUnit()),
                "表盘量程：0 - " + Cfg.SCALES[unit][scaleIdx],
                "演示模式：" + (demo ? "开启" : "关闭"),
                "屏幕方向：" + Cfg.ORIENT_NAMES[orient],
                "重置总里程",
                "使用说明"
        };
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("设置")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            choose("单位", new String[]{"公制 km/h", "英制 mph"}, unit, i -> {
                                unit = i;
                                prefs.edit().putInt("unit", i).apply();
                            });
                            break;
                        case 1:
                            choose("氛围灯颜色", Cfg.ACCENT_NAMES, accentIdx, i -> {
                                accentIdx = i;
                                prefs.edit().putInt("accent", i).apply();
                            });
                            break;
                        case 2:
                            choose("表盘风格", Cfg.STYLE_NAMES, style, i -> {
                                style = i;
                                prefs.edit().putInt("style", i).apply();
                            });
                            break;
                        case 3: {
                            String[] opts = new String[Cfg.LIMITS.length];
                            int sel = 0;
                            for (int k = 0; k < opts.length; k++) {
                                opts[k] = Cfg.LIMITS[k] == 0 ? "关闭" : Cfg.LIMITS[k] + " " + speedUnit();
                                if (Cfg.LIMITS[k] == limit) sel = k;
                            }
                            choose("超速提醒", opts, sel, i -> {
                                limit = Cfg.LIMITS[i];
                                prefs.edit().putInt("limit", limit).apply();
                            });
                            break;
                        }
                        case 4: {
                            int[] sc = Cfg.SCALES[unit];
                            String[] opts = new String[sc.length];
                            for (int k = 0; k < sc.length; k++) opts[k] = "0 - " + sc[k] + " " + speedUnit();
                            choose("表盘量程", opts, scaleIdx, i -> {
                                scaleIdx = i;
                                prefs.edit().putInt("scale", i).apply();
                            });
                            break;
                        }
                        case 5:
                            demo = !demo;
                            prefs.edit().putBoolean("demo", demo).apply();
                            demoSpeed = demo ? speedMs : 0;
                            if (!demo) onSpeedSample(0, 0);
                            toast(demo ? "演示模式已开启：模拟驾驶数据" : "演示模式已关闭，使用 GPS 车速");
                            break;
                        case 6:
                            choose("屏幕方向", Cfg.ORIENT_NAMES, orient, i -> {
                                orient = i;
                                prefs.edit().putInt("orient", i).apply();
                                applyOrientation();
                            });
                            break;
                        case 7:
                            new AlertDialog.Builder(this, DIALOG_THEME)
                                    .setTitle("重置总里程")
                                    .setMessage("确定将总里程清零吗？")
                                    .setPositiveButton("清零", (d2, w2) -> {
                                        odoM = 0;
                                        saveTotals();
                                        updatePanels();
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                            break;
                        default:
                            showHelp();
                    }
                })
                .show();
    }

    private void choose(String title, String[] opts, int checked, IntConsumer cb) {
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(title)
                .setSingleChoiceItems(opts, checked, (d, which) -> {
                    cb.accept(which);
                    applySettings();
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showHelp() {
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("使用说明")
                .setMessage("• 车速来自手机 GPS，请在室外开阔处使用并允许定位权限。\n\n"
                        + "• 双击码表：进入 / 退出全屏码表。\n"
                        + "• 长按码表：HUD 镜像模式（手机放在挡风玻璃下，通过反射看车速）。\n"
                        + "• 风格：经典指针 / 运动光环 / HUD 数字 三种表盘。\n"
                        + "• 0-100：点一下进入加速测试，停车后起步自动计时，到 100 km/h 自动出成绩。长按“最佳”格子可清除成绩。\n"
                        + "• 超速提醒：超过设定车速时表盘闪红并发出提示音、震动。\n"
                        + "• 氛围灯颜色：8 种氛围灯配色。\n"
                        + "• 演示模式：无 GPS 时模拟驾驶，用于体验效果。\n\n"
                        + "驾驶时请勿操作手机，注意行车安全。GPS 车速仅供参考，以车辆仪表为准。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---- location -------------------------------------------------------

    private void startLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            hasPermission = false;
            if (!askedPerm) {
                askedPerm = true;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            }
            return;
        }
        hasPermission = true;
        if (lm == null) return;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 200, 0f, this, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException e) {
            return;
        }
        if (gnssCb == null) {
            gnssCb = new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(GnssStatus s) {
                    int used = 0;
                    for (int i = 0; i < s.getSatelliteCount(); i++) if (s.usedInFix(i)) used++;
                    satUsed = used;
                    satTotal = s.getSatelliteCount();
                }
            };
        }
        try {
            lm.registerGnssStatusCallback(gnssCb, handler);
        } catch (SecurityException ignored) {
        }
    }

    private void stopLocation() {
        if (lm == null) return;
        lm.removeUpdates(this);
        if (gnssCb != null) lm.unregisterGnssStatusCallback(gnssCb);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startLocation();
        } else {
            toast("需要定位权限才能测速，可在设置中开启演示模式体验");
        }
    }

    @Override
    public void onLocationChanged(Location loc) {
        lastFixAt = SystemClock.elapsedRealtime();
        if (loc.hasAltitude()) altitude = loc.getAltitude();
        if (demo) {
            lastLoc = loc;
            return;
        }
        float ms;
        double dist = 0;
        if (lastLoc != null) {
            float dt = (loc.getElapsedRealtimeNanos() - lastLoc.getElapsedRealtimeNanos()) / 1e9f;
            float d = loc.distanceTo(lastLoc);
            ms = loc.hasSpeed() ? loc.getSpeed() : (dt > 0 ? d / dt : 0f);
            if (loc.getAccuracy() < 35f && ms > 0.6f) dist = d;
        } else {
            ms = loc.hasSpeed() ? loc.getSpeed() : 0f;
        }
        if (ms < 0.4f) ms = 0f;
        gpsHeading = loc.hasBearing() && ms > 2f;
        if (gpsHeading) heading = loc.getBearing();
        lastLoc = loc;
        onSpeedSample(ms, dist);
    }

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    @SuppressWarnings("deprecation")
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    // ---- sensors --------------------------------------------------------

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotM, e.values);
            SensorManager.getOrientation(rotM, ori);
            if (!gpsHeading && !demo) heading = ((float) Math.toDegrees(ori[0]) + 360f) % 360f;
        } else if (e.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION && !demo) {
            float x = e.values[0], y = e.values[1], z = e.values[2];
            float g = (float) Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
            gNow = gNow * 0.85f + g * 0.15f;
            if (gNow > gPeak) gPeak = gNow;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ---- core update ----------------------------------------------------

    private void onSpeedSample(float ms, double dist) {
        long now = SystemClock.elapsedRealtime();
        speedMs = ms;
        if (ms > maxMs) maxMs = ms;
        tripM += dist;
        odoM += dist;
        updateSprint(ms, now);
        prevMs = ms;
        prevAt = now;
        gauge.setSpeed(toDisplay(ms));
        gauge.setMax(toDisplay(maxMs));
        checkOverspeed(now);
    }

    /** Time at which speed crossed v, interpolated between the previous and current sample. */
    private long crossTime(float v0, long t0, float v1, long t1, float v) {
        if (t0 == 0 || v1 <= v0) return t1;
        float f = Math.max(0f, Math.min(1f, (v - v0) / (v1 - v0)));
        return t0 + (long) ((t1 - t0) * f);
    }

    private void updateSprint(float ms, long now) {
        float targetMs = unit == 0 ? 100f / Cfg.MS_TO_KMH : 60f / Cfg.MS_TO_MPH;
        switch (atState) {
            case AT_WAIT_STOP:
                if (ms < 0.5f) atState = AT_READY;
                break;
            case AT_READY:
                if (ms >= 0.8f) {
                    atStart = crossTime(prevMs, prevAt, ms, now, 0.3f);
                    atState = AT_RUN;
                }
                break;
            case AT_RUN:
                if (ms >= targetMs) {
                    long end = crossTime(prevMs, prevAt, ms, now, targetMs);
                    atResult = (end - atStart) / 1000f;
                    atState = AT_DONE;
                    if (best0100 == 0 || atResult < best0100) {
                        best0100 = atResult;
                        saveTotals();
                    }
                    if (tone != null) tone.startTone(ToneGenerator.TONE_PROP_ACK, 400);
                    toast(String.format(Locale.US, "%s 成绩：%.2f 秒", sprintLabel(), atResult));
                    updatePanels();
                } else if (ms < 0.3f) {
                    atState = AT_READY;
                }
                break;
            default:
                break;
        }
    }

    private void updateNote(long now) {
        String n = null;
        switch (atState) {
            case AT_WAIT_STOP: n = sprintLabel() + " · 请先停车"; break;
            case AT_READY: n = sprintLabel() + " · 准备出发"; break;
            case AT_RUN:
                n = String.format(Locale.US, "%s · %.1fs", sprintLabel(), (now - atStart) / 1000f);
                break;
            case AT_DONE:
                n = String.format(Locale.US, "%s · %.2fs", sprintLabel(), atResult);
                break;
            default:
                break;
        }
        gauge.setNote(n);
    }

    @SuppressWarnings("deprecation")
    private void checkOverspeed(long now) {
        boolean over = limit > 0 && toDisplay(speedMs) > limit;
        gauge.setOverspeed(over);
        if (over) {
            if (!wasOver) {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(300);
                    }
                }
            }
            if (now - lastBeep > 1500 && tone != null) {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 250);
                lastBeep = now;
            }
        }
        wasOver = over;
    }

    private void stepDemo(float dt, long now) {
        if (now > demoNext) {
            float topMs = Cfg.SCALES[unit][scaleIdx] / (unit == 0 ? Cfg.MS_TO_KMH : Cfg.MS_TO_MPH);
            demoTarget = rnd.nextFloat() < 0.2f ? 0f : Math.min(topMs * 0.85f, 8f + rnd.nextFloat() * 52f);
            demoNext = now + 4000 + rnd.nextInt(6000);
        }
        float diff = demoTarget - demoSpeed;
        float a = diff > 0 ? Math.min(3.8f, diff * 0.6f + 0.6f) : Math.max(-6f, diff * 0.6f - 0.6f);
        float nv = demoSpeed + a * dt;
        if ((diff > 0 && nv > demoTarget) || (diff < 0 && nv < demoTarget)) nv = demoTarget;
        demoSpeed = Math.max(0f, nv);
        float g = Math.abs(diff) < 0.01f ? 0.02f : Math.abs(a) / SensorManager.GRAVITY_EARTH;
        gNow = gNow * 0.8f + g * 0.2f;
        if (gNow > gPeak) gPeak = gNow;
        heading = (heading + dt * 4f) % 360f;
        gpsHeading = true;
        onSpeedSample(demoSpeed, demoSpeed * dt);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            float dt = lastTick == 0 ? 0f : (now - lastTick) / 1000f;
            lastTick = now;
            if (demo) {
                stepDemo(dt, now);
            } else if (speedMs > 0 && lastFixAt > 0 && now - lastFixAt > 4000) {
                onSpeedSample(0f, 0); // signal lost
            }
            if (speedMs > 0.5f) movingMs += (long) (dt * 1000);
            checkOverspeed(now);
            updateNote(now);
            if (tickCount++ % 5 == 0) updatePanels();
            handler.postDelayed(this, 100);
        }
    };

    private String gpsStatus() {
        if (demo) return "演示模式";
        if (!hasPermission) return "定位未授权";
        try {
            if (lm != null && !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return "GPS 已关闭";
        } catch (RuntimeException ignored) {
        }
        long age = SystemClock.elapsedRealtime() - lastFixAt;
        if (lastFixAt == 0 || age > 4000) {
            return satTotal > 0 ? "搜星中 " + satUsed + "/" + satTotal : "搜星中…";
        }
        return "GPS ● " + satUsed + "/" + satTotal;
    }

    private String direction(float deg) {
        return DIRS[Math.round(((deg % 360f) + 360f) % 360f / 45f) % 8];
    }

    private void updatePanels() {
        if (values[0] == null) return;
        boolean metric = unit == 0;
        String du = metric ? "km" : "mi";
        double distF = metric ? 1 / 1000.0 : 1 / 1609.344;
        long s = movingMs / 1000;
        float avgMs = movingMs > 0 ? (float) (tripM / (movingMs / 1000.0)) : 0f;
        values[0].setText(String.format(Locale.US, "%.2f %s", tripM * distF, du));
        values[1].setText(String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
        values[2].setText(String.format(Locale.US, "%d %s", Math.round(toDisplay(avgMs)), speedUnit()));
        values[3].setText(String.format(Locale.US, "%d %s", Math.round(toDisplay(maxMs)), speedUnit()));
        values[4].setText(Double.isNaN(altitude) ? "--"
                : metric ? String.format(Locale.US, "%d m", Math.round(altitude))
                : String.format(Locale.US, "%d ft", Math.round(altitude * 3.28084)));
        values[5].setText(String.format(Locale.US, "%s %d°", direction(heading), Math.round(heading) % 360));
        values[6].setText(String.format(Locale.US, "%.2f / %.2f", gNow, gPeak));
        values[7].setText(String.format(Locale.US, "%.1f %s", odoM * distF, du));
        values[8].setText(best0100 > 0 ? String.format(Locale.US, "%.2f s", best0100) : "--");

        String clock = clockFmt.format(new Date());
        String gps = gpsStatus();
        tvClock.setText(clock);
        tvGps.setText(gps);
        tvHeading.setText("▲ " + direction(heading) + " " + Math.round(heading) % 360 + "°");
        gauge.setCorners(clock, gps,
                String.format(Locale.US, "TRIP %.1f %s", tripM * distF, du),
                String.format(Locale.US, "AVG %d · %s %d°", Math.round(toDisplay(avgMs)),
                        direction(heading), Math.round(heading) % 360));
    }
}
