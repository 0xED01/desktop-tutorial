package com.balancesheet.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.List;

/** Bar chart of monthly net worth (last 12 months), negative months drawn below the baseline. */
public class TrendChart extends View {

    private static final int MAX_BARS = 12;
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint();
    private final float density;
    private final int positive, negative, highlight;
    private List<Store.Snapshot> data;

    public TrendChart(Context ctx, int positive, int negative, int highlight, int textColor) {
        super(ctx);
        this.positive = positive;
        this.negative = negative;
        this.highlight = highlight;
        density = ctx.getResources().getDisplayMetrics().density;
        label.setColor(textColor);
        label.setTextSize(11 * density);
        label.setTextAlign(Paint.Align.CENTER);
        axis.setColor(0xFFE0E0E0);
        axis.setStrokeWidth(density);
    }

    public void setData(List<Store.Snapshot> all) {
        data = all.size() > MAX_BARS ? all.subList(all.size() - MAX_BARS, all.size()) : all;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (data == null || data.isEmpty()) return;
        float w = getWidth(), h = getHeight();
        float top = 22 * density, bottom = h - 20 * density;

        long max = 0, min = 0;
        for (Store.Snapshot s : data) {
            max = Math.max(max, s.net());
            min = Math.min(min, s.net());
        }
        if (max == min) max = min + 1;
        float zeroY = top + (bottom - top) * max / (float) (max - min);
        c.drawLine(0, zeroY, w, zeroY, axis);

        float slot = w / Math.max(data.size(), 6);
        float barW = Math.min(slot * 0.55f, 28 * density);
        float offset = (w - slot * data.size()) / 2;
        for (int i = 0; i < data.size(); i++) {
            Store.Snapshot s = data.get(i);
            float cx = offset + slot * (i + 0.5f);
            float y = top + (bottom - top) * (max - s.net()) / (float) (max - min);
            boolean last = i == data.size() - 1;
            bar.setColor(s.net() < 0 ? negative : (last ? highlight : positive));
            RectF r = new RectF(cx - barW / 2, Math.min(y, zeroY), cx + barW / 2, Math.max(y, zeroY));
            if (r.height() < density * 2) r.top = r.bottom - density * 2;
            c.drawRoundRect(r, 4 * density, 4 * density, bar);
            c.drawText((s.yearMonth % 100) + "月", cx, h - 4 * density, label);
            if (last) c.drawText(MainActivity.compact(s.net()), cx, Math.min(y, zeroY) - 6 * density, label);
        }
    }
}
