package com.customer.recorder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivitySmokeTest {

    private static final String SAMPLE = "您推荐的客户胡雅萍随e贷申请已审批通过，额度为280000，利率为5.9800%。"
            + "该客户首次审批通过的时间为2026年09月24日。该客户未开通我行借记卡！未开通手机银行！";

    @Test
    public void pasteAndSave() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = a.findViewById(android.R.id.content);
        EditText input = find(root, EditText.class, "长按这里粘贴");
        input.setText(SAMPLE);
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        Button save = find(root, Button.class, "保存记录");
        assertEquals(View.VISIBLE, ((View) save.getParent()).getVisibility());
        save.performClick();
        List<Customer> all = new CustomerDb(a).query("", 0, 0, Long.MAX_VALUE, "");
        assertEquals(1, all.size());
        assertEquals("胡雅萍", all.get(0).name);
        assertTrue(textExists(root, "胡雅萍"));
    }

    /** 录入时手动点选的开通状态，保存后不能被识别结果覆盖。 */
    @Test
    public void keepsManualStateChoice() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = a.findViewById(android.R.id.content);
        find(root, EditText.class, "长按这里粘贴").setText(SAMPLE);
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        ViewGroup mobileRow = rowAfterLabel(root, "手机银行");
        find(mobileRow, TextView.class, "已开通").performClick();
        find(root, Button.class, "保存记录").performClick();
        Customer c = new CustomerDb(a).query("", 0, 0, Long.MAX_VALUE, "").get(0);
        assertEquals(MessageParser.OPENED, c.mobileBank);
        assertEquals(MessageParser.NOT_OPENED, c.debitCard);
    }

    @Test
    public void giftAndCostFromActivityAreEditable() {
        android.content.Context ctx = ApplicationProvider.getApplicationContext();
        CustomerDb.Act act = new CustomerDb.Act();
        act.name = "扫码送油";
        act.gift = "食用油";
        act.cost = 45;
        new CustomerDb(ctx).saveActivity(act, null);
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = a.findViewById(android.R.id.content);
        find(root, EditText.class, "长按这里粘贴").setText(SAMPLE);
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        find(rowAfterLabel(root, "扫码活动"), TextView.class, "扫码送油").performClick();
        EditText gift = find(root, EditText.class, "无礼品可不填");
        assertEquals("食用油", gift.getText().toString());
        EditText cost = (EditText) ((ViewGroup) ((ViewGroup) gift.getParent().getParent()).getChildAt(1)).getChildAt(1);
        assertEquals("45", cost.getText().toString());
        cost.setText("50");
        find(root, Button.class, "保存记录").performClick();
        Customer c = new CustomerDb(a).query("", 0, 0, Long.MAX_VALUE, "").get(0);
        assertEquals("扫码送油", c.activity);
        assertEquals("食用油", c.gift);
        assertEquals(50, c.giftCost, 0.001);
        assertEquals("食用油", gift.getText().toString());
    }

    private static ViewGroup rowAfterLabel(View v, String label) {
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View child = g.getChildAt(i);
                if (i + 1 < g.getChildCount() && child instanceof TextView
                        && label.contentEquals(((TextView) child).getText())
                        && g.getChildAt(i + 1) instanceof FlowLayout) {
                    return (ViewGroup) g.getChildAt(i + 1);
                }
                ViewGroup r = rowAfterLabel(child, label);
                if (r != null) return r;
            }
        }
        return null;
    }

    @Test
    public void upgradesFromV1() {
        android.content.Context ctx = ApplicationProvider.getApplicationContext();
        SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath("customers.db"), null);
        old.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT, product TEXT, amount REAL, rate TEXT, approve_date TEXT,"
                + "status TEXT, debit_card TEXT, mobile_bank TEXT, note TEXT, raw TEXT, created_at INTEGER)");
        old.execSQL("INSERT INTO customers (name, product, amount, created_at) VALUES ('张三','随e贷',1000,1)");
        old.setVersion(1);
        old.close();
        List<Customer> all = new CustomerDb(ctx).query("", 0, 0, Long.MAX_VALUE, "");
        assertEquals(1, all.size());
        assertEquals("张三", all.get(0).name);
        assertEquals(MessageParser.UNKNOWN, all.get(0).creditCard);
    }

    private static <T extends TextView> T find(View v, Class<T> type, String text) {
        if (type.isInstance(v)) {
            TextView tv = (TextView) v;
            String s = tv.getText() + "|" + tv.getHint();
            if (s.contains(text)) return type.cast(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                T r = find(g.getChildAt(i), type, text);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static boolean textExists(View v, String text) {
        return find(v, TextView.class, text) != null;
    }
}
