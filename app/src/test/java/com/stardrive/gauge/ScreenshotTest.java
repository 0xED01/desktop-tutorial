package com.stardrive.gauge;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;

/** Renders key screens to PNG files under build/screens for visual review. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w860dp-h400dp-land-xhdpi")
public class ScreenshotTest {
    private static final File OUT = new File("build/screens");

    private static void save(View v, int w, int h, String name) throws Exception {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        OUT.mkdirs();
        try (FileOutputStream f = new FileOutputStream(new File(OUT, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, f);
        }
    }

    @Test
    public void splash() throws Exception {
        android.app.Activity a = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        SplashView v = new SplashView(a, Cfg.ACCENTS[0], new SplashView.Listener() {
            public void onSplashDone() {}
            public void onLogoLanded() {}
        });
        save(v, 1720, 800, "splash_0");
        long prev = 0;
        for (long ms : new long[]{700, 1500, 1900, 2600, 3300, 4100}) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(ms - prev));
            prev = ms;
            save(v, 1720, 800, "splash_" + ms);
        }
    }

    @Test
    public void gauges() throws Exception {
        android.app.Activity a = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        for (int style = 0; style < 3; style++) {
            GaugeView g = new GaugeView(a);
            g.setStyle(style);
            g.setLimit(120);
            g.setMax(156);
            g.setNote(style == 1 ? "0-100 · 6.84s" : null);
            g.setSpeed(98);
            g.setAccent(Cfg.ACCENTS[style == 1 ? 1 : 0]);
            save(g, 800, 800, "gauge_" + style); // first frame computes size
            for (int i = 0; i < 40; i++) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(100));
                save(g, 800, 800, "gauge_" + style);
            }
            g.setExtras(true);
            g.setCorners("21:08:45", "GPS ● 14/22", "TRIP 23.4 km", "AVG 64 · 东北 45°");
            g.setSpeed(132);
            for (int i = 0; i < 40; i++) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(100));
                g.setOverspeed(true);
            }
            save(g, 1720, 800, "full_" + style);
        }
    }

    @Test
    public void mainLandscape() throws Exception {
        MainActivity m = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = m.findViewById(R.id.root);
        save(root, 1720, 800, "main_land");
    }
}
