package com.sleepwell.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** 每晚实际睡眠时长柱状图，颜色表示睡眠效率。 */
public class BarsView extends View {
    private float[] hours = new float[0];
    private int[] colors = new int[0];
    private String[] labels = new String[0];
    private float ref;
    private final float d;
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BarsView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        line.setColor(0x66FFB86B);
        line.setStrokeWidth(1.5f * d);
        txt.setColor(0xFF9AA3B8);
        txt.setTextSize(10 * d);
        txt.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(float[] hours, int[] colors, String[] labels, float refHours) {
        this.hours = hours;
        this.colors = colors;
        this.labels = labels;
        this.ref = refHours;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) (150 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        int n = hours.length;
        if (n == 0) return;
        float max = 9;
        for (float h : hours) max = Math.max(max, h);
        float top = 14 * d, bottom = getHeight() - 16 * d, chartH = bottom - top;
        float slot = getWidth() / (float) n, bw = Math.min(slot * 0.6f, 22 * d);
        for (int i = 0; i < n; i++) {
            float cx = slot * i + slot / 2;
            float h = chartH * hours[i] / max;
            bar.setColor(colors[i]);
            c.drawRoundRect(cx - bw / 2, bottom - h, cx + bw / 2, bottom, 4 * d, 4 * d, bar);
            c.drawText(String.format(java.util.Locale.US, "%.1f", hours[i]), cx, bottom - h - 3 * d, txt);
            c.drawText(labels[i], cx, getHeight() - 3 * d, txt);
        }
        if (ref > 0) {
            float y = bottom - chartH * ref / max;
            c.drawLine(0, y, getWidth(), y, line);
        }
    }
}
