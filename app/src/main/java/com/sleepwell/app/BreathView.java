package com.sleepwell.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** 呼吸引导圆：吸气变大、呼气变小。 */
public class BreathView extends View {
    private float scale;
    private String label = "", count = "";
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BreathView(Context c) {
        super(c);
        float d = getResources().getDisplayMetrics().density;
        fill.setColor(0x55FFB86B);
        ring.setColor(0x44FFB86B);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2 * d);
        big.setColor(0xFFE6E8F0);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTextSize(28 * d);
        small.setColor(0xFFB0B6C6);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTextSize(44 * d);
    }

    public void set(float scale, String label, String count) {
        this.scale = scale;
        this.label = label;
        this.count = count;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = MeasureSpec.getSize(w);
        setMeasuredDimension(size, (int) (size * 0.9f));
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float maxR = Math.min(cx, cy) * 0.92f, minR = maxR * 0.38f;
        float r = minR + (maxR - minR) * scale;
        c.drawCircle(cx, cy, maxR, ring);
        c.drawCircle(cx, cy, r, fill);
        c.drawText(label, cx, cy - big.getTextSize() * 0.4f, big);
        c.drawText(count, cx, cy + small.getTextSize() * 0.9f, small);
    }
}
