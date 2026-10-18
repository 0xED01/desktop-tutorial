package com.stardrive.gauge;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SweepGradient;

/** Three-pointed star in a chrome ring, drawn with a light and a dark facet per arm. */
final class StarLogo {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint facet = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private float shaderX = Float.NaN, shaderY = Float.NaN;

    StarLogo() {
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.BUTT);
        facet.setStyle(Paint.Style.FILL);
    }

    void draw(Canvas c, float cx, float cy, float r) {
        draw(c, cx, cy, r, 1f, 1f, 0f, 255);
    }

    /**
     * @param ringP fraction of the ring drawn (0..1), starting at the top
     * @param starP star scale (0..1+, may overshoot for a bounce)
     * @param rot   extra rotation of the star in degrees
     */
    void draw(Canvas c, float cx, float cy, float r, float ringP, float starP, float rot, int alpha) {
        if (cx != shaderX || cy != shaderY) {
            ring.setShader(new SweepGradient(cx, cy,
                    new int[]{0xFF6A6E74, 0xFFF4F6F8, 0xFF7C8086, 0xFFE2E5E8,
                            0xFF5E6268, 0xFFF0F2F4, 0xFF6A6E74}, null));
            shaderX = cx;
            shaderY = cy;
        }
        if (ringP > 0) {
            float rr = r * 0.955f;
            ring.setStrokeWidth(r * 0.09f);
            ring.setAlpha(alpha);
            oval.set(cx - rr, cy - rr, cx + rr, cy + rr);
            if (ringP >= 1f) c.drawCircle(cx, cy, rr, ring);
            else c.drawArc(oval, -90f, 360f * ringP, false, ring);
        }
        if (starP <= 0) return;
        float tip = r * 0.915f * starP, inner = r * 0.155f * starP;
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(-90 + 120 * i + rot);
            double l = a - Math.toRadians(60), rgt = a + Math.toRadians(60);
            float tx = cx + tip * (float) Math.cos(a), ty = cy + tip * (float) Math.sin(a);
            // lit facet
            path.reset();
            path.moveTo(cx, cy);
            path.lineTo(tx, ty);
            path.lineTo(cx + inner * (float) Math.cos(rgt), cy + inner * (float) Math.sin(rgt));
            path.close();
            facet.setColor(0xFFF2F4F6);
            facet.setAlpha(alpha);
            c.drawPath(path, facet);
            // shaded facet
            path.reset();
            path.moveTo(cx, cy);
            path.lineTo(tx, ty);
            path.lineTo(cx + inner * (float) Math.cos(l), cy + inner * (float) Math.sin(l));
            path.close();
            facet.setColor(0xFF8B9197);
            facet.setAlpha(alpha);
            c.drawPath(path, facet);
        }
    }
}
