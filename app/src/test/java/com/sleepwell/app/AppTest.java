package com.sleepwell.app;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppTest {

    private static SleepEntry entry(String date, int bed, int sol, int waso, int wake, int up) {
        SleepEntry e = new SleepEntry();
        e.date = date; e.bed = bed; e.sol = sol; e.waso = waso; e.wake = wake; e.up = up; e.quality = 3;
        return e;
    }

    @Test
    public void sleepMathAcrossMidnight() {
        SleepEntry e = entry("2026-10-18", 23 * 60, 30, 30, 7 * 60, 7 * 60 + 30);
        assertEquals(510, e.tib());
        assertEquals(420, e.tst());
        assertEquals(420 / 510f, e.se(), 1e-4);
    }

    @Test
    public void windowRules() {
        assertEquals(SleepMath.FLOOR, SleepMath.initialWindow(240));
        assertEquals(375, SleepMath.initialWindow(372));
        assertEquals(390, SleepMath.adjust(375, 0.92f));
        assertEquals(360, SleepMath.adjust(375, 0.80f));
        assertEquals(375, SleepMath.adjust(375, 0.87f));
        assertEquals(SleepMath.FLOOR, SleepMath.adjust(SleepMath.FLOOR, 0.5f));
    }

    @Test
    public void customBedtime() {
        Context c = androidx_ctx();
        Store.setWakeMin(c, 7 * 60);
        assertEquals(23 * 60, Store.bedMin(c));          // 默认 8 小时
        Store.setCustomBed(c, 22 * 60 + 30);
        assertEquals(22 * 60 + 30, Store.bedMin(c));     // 自定义优先
        assertEquals(23 * 60, Store.autoBedMin(c));

        ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class).setup();
        View root = ac.get().getWindow().getDecorView();
        assertTrue(hasText(root, "上床 22:30"));
        assertTrue(hasText(root, "（自定义）"));
        Button reset = findButton(root, "恢复自动");
        assertNotNull(reset);
        reset.performClick();
        assertEquals(-1, Store.customBed(c));
        assertTrue(hasText(root, "上床 23:00"));
        assertNull(findButton(root, "恢复自动"));
        ac.pause().stop().destroy();
    }

    private static List<View> all(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) all(g.getChildAt(i), out);
        }
        return out;
    }

    private static Button findButton(View root, String prefix) {
        for (View v : all(root, new ArrayList<>())) {
            if (v instanceof Button && ((Button) v).getText().toString().startsWith(prefix)) return (Button) v;
        }
        return null;
    }

    private static boolean hasText(View root, String sub) {
        for (View v : all(root, new ArrayList<>())) {
            if (v instanceof TextView && ((TextView) v).getText().toString().contains(sub)) return true;
        }
        return false;
    }

    @Test
    public void fullFlow() {
        Context c = androidx_ctx();
        // 7 nights of data
        for (int i = 1; i <= 7; i++) {
            Store.saveEntry(c, entry(LocalDate.now().minusDays(i - 1).toString(), 23 * 60, 40, 50, 6 * 60 + 30, 7 * 60));
        }
        ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = ac.get();
        View root = a.getWindow().getDecorView();
        assertTrue(hasText(root, "建议窗口"));
        Button startPlan = findButton(root, "开始计划");
        assertNotNull(startPlan);
        Store.setCustomBed(c, 21 * 60);
        startPlan.performClick();
        assertEquals(-1, Store.customBed(c)); // 开始计划后恢复自动
        assertEquals(360, Store.windowMin(c)); // avg tst 450-90
        assertTrue(hasText(root, "睡眠窗口 6 小时："));

        // every tab builds
        for (int t = 0; t < 5; t++) {
            a.showTab(t);
            ShadowLooper.idleMainLooper();
        }

        // diary dialog: open, save
        a.showTab(1);
        findButton(root, "＋").performClick();
        AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals(7, Store.entries(c).size()); // today's entry replaced

        // relax sessions
        a.showTab(2);
        List<Button> starts = new ArrayList<>();
        for (View v : all(root, new ArrayList<>())) if (v instanceof Button && ((Button) v).getText().toString().equals("开始")) starts.add((Button) v);
        assertEquals(5, starts.size());
        for (int i = 0; i < 4; i++) {
            a.showTab(2);
            List<Button> s = new ArrayList<>();
            for (View v : all(root, new ArrayList<>())) if (v instanceof Button && ((Button) v).getText().toString().equals("开始")) s.add((Button) v);
            s.get(i).performClick();
            ShadowLooper.idleMainLooper(11, java.util.concurrent.TimeUnit.MINUTES);
            assertNotNull(findButton(root, "结束"));
        }
        a.onBackPressed();
        // worries
        a.showTab(2);
        List<Button> s = new ArrayList<>();
        for (View v : all(root, new ArrayList<>())) if (v instanceof Button && ((Button) v).getText().toString().equals("开始")) s.add((Button) v);
        s.get(4).performClick();
        findButton(root, "保存").performClick();

        // sound
        a.showTab(3);
        findButton(root, "▶").performClick();
        Intent started = Shadows.shadowOf(a.getApplication()).getNextStartedService();
        assertNotNull(started);
        assertEquals(NoiseService.START, started.getAction());

        // reminders scheduled
        android.app.AlarmManager am = c.getSystemService(android.app.AlarmManager.class);
        assertEquals(4, Shadows.shadowOf(am).getScheduledAlarms().size());

        // receiver fires and posts notification
        Shadows.shadowOf(a.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        new ReminderReceiver().onReceive(c, new Intent().putExtra("kind", Reminders.BED));
        android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
        assertEquals(1, Shadows.shadowOf(nm).size());

        ac.pause().stop().destroy();
    }

    private static Context androidx_ctx() {
        return org.robolectric.RuntimeEnvironment.getApplication();
    }
}
