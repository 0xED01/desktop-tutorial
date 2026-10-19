package com.sleepwell.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;

/** 渲染各页面截图到 build/screens，便于肉眼检查（不做断言）。 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w411dp-h891dp-xxhdpi")
public class ScreenshotTest {
    private void shot(View root, String name) throws Exception {
        ShadowLooper.idleMainLooper();
        View scroll = ((ViewGroup) ((ViewGroup) root.findViewById(android.R.id.content)).getChildAt(0));
        Bitmap b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        File dir = new File("build/screens");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    @Test
    public void screens() throws Exception {
        int[][] nights = {{23 * 60, 40, 50, 390, 420}, {23 * 60 + 30, 60, 30, 420, 440}, {22 * 60 + 45, 30, 70, 400, 410},
                {23 * 60, 25, 40, 380, 400}, {0, 50, 20, 450, 460}, {23 * 60 + 15, 35, 45, 410, 420}, {23 * 60, 20, 30, 395, 405}};
        for (int i = 0; i < nights.length; i++) {
            SleepEntry e = new SleepEntry();
            e.date = LocalDate.now().minusDays(i).toString();
            e.bed = nights[i][0]; e.sol = nights[i][1]; e.waso = nights[i][2]; e.wake = nights[i][3]; e.up = nights[i][4];
            e.quality = 2 + i % 3;
            Store.saveEntry(RuntimeEnvironment.getApplication(), e);
        }
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = a.getWindow().getDecorView();
        shot(root, "0_tonight");
        a.showTab(1); shot(root, "1_diary");
        a.showTab(2); shot(root, "2_relax");
        a.showTab(3); shot(root, "3_sound");
        a.showTab(4); shot(root, "4_guide");
        a.showTab(2);
        for (View v : AppTestHelper.all(root)) {
            if (v instanceof Button && ((Button) v).getText().toString().equals("开始")) { v.performClick(); break; }
        }
        ShadowLooper.idleMainLooper(5500, java.util.concurrent.TimeUnit.MILLISECONDS);
        shot(root, "5_breath");
        Store.setCustomBed(RuntimeEnvironment.getApplication(), 22 * 60 + 30);
        a.showTab(0);
        android.widget.ScrollView sv = null;
        for (View v : AppTestHelper.all(root)) if (v instanceof android.widget.ScrollView) sv = (android.widget.ScrollView) v;
        shot(root, "6_custom_top");
        sv.scrollTo(0, 100000);
        shot(root, "7_custom_settings");
    }
}
