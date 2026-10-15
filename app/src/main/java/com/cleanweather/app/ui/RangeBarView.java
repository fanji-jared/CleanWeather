package com.cleanweather.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** 每日预报温度区间条（MSN 同款：全周日温度范围内的高低温分段） */
public class RangeBarView extends View {

    /** 全周最低温 */
    private double globalMin = 0;
    /** 全周最高温 */
    private double globalMax = 30;
    private double lo = 10, hi = 20;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint segPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RangeBarView(Context context) {
        this(context, null);
    }

    public RangeBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        trackPaint.setColor(0x33FFFFFF);
        segPaint.setStyle(Paint.Style.FILL);
    }

    public void setRange(double globalMin, double globalMax, double lo, double hi) {
        this.globalMin = globalMin;
        this.globalMax = globalMax;
        this.lo = lo;
        this.hi = hi;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float barH = Math.min(h * 0.45f, 6f * getResources().getDisplayMetrics().density);
        float top = (h - barH) / 2f;
        float radius = barH / 2f;

        // 轨道
        canvas.drawRoundRect(new RectF(0, top, w, top + barH), radius, radius, trackPaint);

        double span = Math.max(0.5, globalMax - globalMin);
        float start = (float) ((lo - globalMin) / span) * w;
        float end = (float) ((hi - globalMin) / span) * w;
        start = Math.max(0, Math.min(start, w - radius));
        end = Math.max(start + radius, Math.min(end, w));

        int colorLo = tempColor(lo);
        int colorHi = tempColor(hi);
        segPaint.setShader(new LinearGradient(start, 0, end, 0, colorLo, colorHi, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(start, top, end, top + barH), radius, radius, segPaint);
    }

    /** 温度 -> 颜色（蓝 -> 青 -> 黄 -> 橙） */
    private static int tempColor(double t) {
        if (t <= -5) return 0xFF7EC8FF;
        if (t <= 3) return 0xFF9FDBFF;
        if (t <= 12) return 0xFF7EE0D3;
        if (t <= 20) return 0xFFFFD54F;
        if (t <= 28) return 0xFFFFA726;
        return 0xFFFF7043;
    }
}
