package com.cleanweather.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** 空气质量色带 + 当前值标记 */
public class AqiBarView extends View {

    private double aqi = 50;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int[] GRADIENT = {
            0xFF4CAF50, 0xFFFFC107, 0xFFFF9800, 0xFFF44336, 0xFF9C27B0, 0xFF7D26CD
    };

    public AqiBarView(Context context) {
        this(context, null);
    }

    public AqiBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        barPaint.setStyle(Paint.Style.FILL);
        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(0xFFFFFFFF);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
        strokePaint.setColor(0xFF3A3A3A);
    }

    public void setAqi(double v) {
        this.aqi = v;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float w = getWidth();
        float barH = Math.min(h, 10f * getResources().getDisplayMetrics().density);
        float top = (h - barH) / 2f;

        barPaint.setShader(new LinearGradient(0, 0, w, 0, GRADIENT, null, Shader.TileMode.CLAMP));
        Path p = new Path();
        RectF r = new RectF(0, top, w, top + barH);
        p.addRoundRect(r, barH / 2f, barH / 2f, Path.Direction.CW);
        canvas.drawPath(p, barPaint);

        // 标记位置：AQI 0-400 映射到 0-100%
        double ratio = Math.max(0, Math.min(1, aqi / 400.0));
        float x = (float) (w * ratio);
        canvas.drawCircle(Math.min(x, w - barH / 2f), top + barH / 2f, barH / 1.7f, markerPaint);
        canvas.drawCircle(Math.min(x, w - barH / 2f), top + barH / 2f, barH / 1.7f, strokePaint);
    }
}
