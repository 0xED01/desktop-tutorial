package com.customer.recorder;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w400dp-h2000dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotTest {
    @Test
    public void shot() throws Exception {
        String out = System.getProperty("shotDir");
        if (out == null) return;
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        CustomerDb db = new CustomerDb(a);
        CustomerDb.Act act = new CustomerDb.Act();
        act.name = "十月扫码送油";
        act.gift = "食用油";
        act.cost = 45;
        db.saveActivity(act, null);
        String[] msgs = {
            "您推荐的客户胡雅萍随e贷申请已审批通过，额度为280000，利率为5.9800%。该客户首次审批通过的时间为2026年09月24日。该客户未开通我行借记卡！未开通手机银行！",
            "您推荐的客户王建国快e贷申请已审批通过，额度为150000，利率为4.3500%。该客户首次审批通过的时间为2026年10月20日。该客户已开通我行借记卡！未开通手机银行！",
            "您推荐的客户陈美玲随e贷申请已审批通过，额度为100000，利率为5.2000%。该客户首次审批通过的时间为2026年10月08日。该客户已开通我行借记卡！已开通手机银行！"};
        for (int i = 0; i < msgs.length; i++) {
            Customer c = MessageParser.parse(msgs[i]);
            c.activity = act.name; c.gift = act.gift; c.giftCost = act.cost;
            c.phone = i == 0 ? "13812345678" : "";
            c.giftGiven = i != 0;
            c.createdAt = System.currentTimeMillis() - i * 86400000L;
            db.upsert(c);
        }
        a.getSharedPreferences("settings", 0).edit().putString("last_activity", act.name).commit();
        a.recreate();
        a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ScrollView sv = (ScrollView) ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        EditText input = findEdit(sv);
        input.setText(msgs[0].replace("胡雅萍", "李晓梅"));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        View content = sv.getChildAt(0);
        int w = sv.getWidth();
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        content.layout(0, 0, w, content.getMeasuredHeight());
        Bitmap bmp = Bitmap.createBitmap(w, content.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(0xFFF2F3F5);
        content.draw(canvas);
        try (FileOutputStream fos = new FileOutputStream(new File(out, "screen.png"))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }

    private static EditText findEdit(View v) {
        if (v instanceof EditText) return (EditText) v;
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                EditText e = findEdit(((ViewGroup) v).getChildAt(i));
                if (e != null) return e;
            }
        }
        return null;
    }
}
