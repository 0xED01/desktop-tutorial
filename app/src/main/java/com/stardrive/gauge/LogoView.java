package com.stardrive.gauge;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

/** Small static star logo for toolbars. */
public class LogoView extends View {
    private final StarLogo logo = new StarLogo();

    public LogoView(Context c) {
        this(c, null);
    }

    public LogoView(Context c, AttributeSet a) {
        super(c, a);
    }

    @Override
    protected void onDraw(Canvas c) {
        float r = Math.min(getWidth(), getHeight()) / 2f;
        logo.draw(c, getWidth() / 2f, getHeight() / 2f, r);
    }
}
