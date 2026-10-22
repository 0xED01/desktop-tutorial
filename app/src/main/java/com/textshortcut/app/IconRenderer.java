package com.textshortcut.app;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

/** 把一个字绘制成图标位图。 */
public class IconRenderer {

    /** 按“用户感知的字符”拆分（支持表情等组合字符），并去掉空白。 */
    public static List<String> splitChars(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(text);
        int start = it.first();
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            String ch = text.substring(start, end);
            if (!ch.trim().isEmpty()) out.add(ch);
        }
        return out;
    }

    /**
     * @param adaptive 为 true 时生成自适应图标（108dp 画布，内容缩进到安全区，背景铺满，形状由桌面决定）
     */
    public static Bitmap render(String ch, IconStyle s, Typeface baseFace, Bitmap bgImage,
                                int size, boolean adaptive) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float w = size;

        // 背景区域路径
        Path shapePath = new Path();
        RectF full = new RectF(0, 0, w, w);
        int shape = adaptive ? IconStyle.SHAPE_SQUARE : s.shape;
        switch (shape) {
            case IconStyle.SHAPE_CIRCLE:
                shapePath.addOval(full, Path.Direction.CW);
                break;
            case IconStyle.SHAPE_ROUNDED:
                shapePath.addRoundRect(full, w * 0.22f, w * 0.22f, Path.Direction.CW);
                break;
            case IconStyle.SHAPE_SQUARE:
                shapePath.addRect(full, Path.Direction.CW);
                break;
            default:
                break;
        }

        if (shape != IconStyle.SHAPE_NONE) {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (s.useBgImage && bgImage != null) {
                BitmapShader shader = new BitmapShader(bgImage, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                // 居中裁剪
                float scale = Math.max(w / bgImage.getWidth(), w / bgImage.getHeight());
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                m.postTranslate((w - bgImage.getWidth() * scale) / 2f, (w - bgImage.getHeight() * scale) / 2f);
                shader.setLocalMatrix(m);
                bg.setShader(shader);
            } else if (s.gradient) {
                bg.setShader(new LinearGradient(0, 0, w, w, s.bgColor, s.bgColor2, Shader.TileMode.CLAMP));
            } else {
                bg.setColor(s.bgColor);
            }
            c.drawPath(shapePath, bg);

            if (s.borderWidth > 0) {
                float bw = w * s.borderWidth / 100f;
                Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
                border.setStyle(Paint.Style.STROKE);
                border.setStrokeWidth(bw);
                border.setColor(s.borderColor);
                if (adaptive) {
                    // 自适应图标会被桌面裁剪，边框画在安全区附近
                    float inset = w * 18f / 108f + bw / 2f;
                    c.drawOval(new RectF(inset, inset, w - inset, w - inset), border);
                } else {
                    c.save();
                    c.clipPath(shapePath);
                    border.setStrokeWidth(bw * 2); // 一半在外面被裁掉
                    c.drawPath(shapePath, border);
                    c.restore();
                }
            }
        }

        // 文字
        float content = adaptive ? w * 66f / 108f : w; // 自适应图标的安全区
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        Typeface face = baseFace != null ? baseFace : Typeface.DEFAULT;
        tp.setTypeface(Typeface.create(face, s.bold ? Typeface.BOLD : Typeface.NORMAL));
        // 导入的字体文件通常没有粗体字重，用模拟加粗
        if (s.bold && s.fontIndex == IconStyle.FONT_CUSTOM) tp.setFakeBoldText(true);
        tp.setColor(s.textColor);
        if (s.shadow) tp.setShadowLayer(w * 0.03f, 0, w * 0.015f, 0x80000000);

        float target = content * s.textScale / 100f;
        tp.setTextSize(100f);
        Rect b = new Rect();
        tp.getTextBounds(ch, 0, ch.length(), b);
        float gw = Math.max(1, b.width());
        float gh = Math.max(1, b.height());
        float k = target / Math.max(gw, gh);
        tp.setTextSize(100f * k);
        tp.getTextBounds(ch, 0, ch.length(), b);

        float cx = w / 2f, cy = w / 2f + content * s.textOffset / 100f;
        float x = cx - b.exactCenterX();
        float y = cy - b.exactCenterY();
        c.drawText(ch, x, y, tp);
        return bmp;
    }
}
