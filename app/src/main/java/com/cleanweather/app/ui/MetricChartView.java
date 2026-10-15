package com.cleanweather.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用指标曲线图：平滑贝塞尔曲线 + 渐变填充 + 触摸选择。
 * 用于每小时预报页的温度/降水/风速/湿度等指标展示。
 */
public class MetricChartView extends View {

    /** 数据点 */
    public static class Point {
        public final long time;
        public final double value;

        public Point(long time, double value) {
            this.time = time;
            this.value = value;
        }
    }

    public interface SelectListener {
        void onSelected(int index);
    }

    private List<Point> points = new ArrayList<>();
    private List<String> xLabels = new ArrayList<>();
    private List<String> valueLabels = new ArrayList<>();
    private double minVal = 0, maxVal = 1;
    private int curveColor = 0xFFFFC94D;
    private int selected = -1;
    private SelectListener listener;

    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curvePath = new Path();
    private final Path fillPath = new Path();

    private final float density;
    private static final float TOP_PAD_DP = 26;
    private static final float BOTTOM_PAD_DP = 22;

    public MetricChartView(Context context) {
        this(context, null);
    }

    public MetricChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(2.2f * density);
        curvePaint.setStrokeCap(Paint.Cap.ROUND);
        curvePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFFFFFFFF);

        dotStrokePaint.setStyle(Paint.Style.STROKE);
        dotStrokePaint.setStrokeWidth(2f * density);
        dotStrokePaint.setColor(curveColor);

        labelPaint.setColor(0xFFFFFFFF);
        labelPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(density);
        gridPaint.setColor(0x26FFFFFF);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{4 * density, 4 * density}, 0));

        selLinePaint.setStyle(Paint.Style.STROKE);
        selLinePaint.setStrokeWidth(density);
        selLinePaint.setColor(0x66FFFFFF);

        setLayerType(LAYER_TYPE_SOFTWARE, null); // DashPathEffect + gradient alpha 支持
    }

    public void setData(List<Point> pts, List<String> xLabels, List<String> valueLabels,
                         double min, double max, int color) {
        this.points = pts != null ? pts : new ArrayList<Point>();
        this.xLabels = xLabels != null ? xLabels : new ArrayList<String>();
        this.valueLabels = valueLabels != null ? valueLabels : new ArrayList<String>();
        this.minVal = min;
        this.maxVal = max;
        if (max - min < 1e-6) {
            max = min + 1;
            this.maxVal = max;
        }
        setCurveColor(color);
        selected = -1;
        invalidate();
    }

    public void setCurveColor(int color) {
        this.curveColor = color;
        curvePaint.setColor(color);
        dotStrokePaint.setColor(color);
        // 渐变：曲线色 40% -> 透明
        fillPaint.setShader(null);
        invalidate();
    }

    public void setSelected(int index) {
        this.selected = index;
        invalidate();
    }

    public void setSelectListener(SelectListener l) {
        this.listener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (points.isEmpty()) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN
                || event.getAction() == MotionEvent.ACTION_MOVE) {
            float usableW = getUsableWidth();
            float usableH = getUsableHeight();
            float step = usableW / Math.max(1, points.size() - 1);
            int idx = Math.round((event.getX() - getPaddingLeft()) / step);
            if (idx >= 0 && idx < points.size() && idx != selected) {
                selected = idx;
                invalidate();
                if (listener != null) listener.onSelected(idx);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private float getUsableWidth() {
        return Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight());
    }

    private float getUsableHeight() {
        return Math.max(1, getHeight() - getPaddingTop() - getPaddingBottom()
                - TOP_PAD_DP * density - BOTTOM_PAD_DP * density);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = points.size();
        if (n == 0) return;

        float topPad = TOP_PAD_DP * density;
        float bottomPad = BOTTOM_PAD_DP * density;
        float left = getPaddingLeft();
        float top = getPaddingTop() + topPad;
        float usableW = getUsableWidth();
        float usableH = getUsableHeight();
        float bottom = top + usableH;

        // 网格（3 条水平虚线）
        for (int i = 1; i <= 3; i++) {
            float y = top + usableH * i / 4f;
            canvas.drawLine(left, y, left + usableW, y, gridPaint);
        }

        // 计算点坐标
        float step = usableW / Math.max(1, n - 1);
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = left + step * i;
            double v = points.get(i).value;
            if (Double.isNaN(v)) v = minVal;
            float ratio = (float) ((v - minVal) / (maxVal - minVal));
            ys[i] = bottom - ratio * usableH;
        }

        // 填充路径
        fillPath.reset();
        fillPath.moveTo(xs[0], bottom);
        for (int i = 0; i < n; i++) fillPath.lineTo(xs[i], ys[i]);
        fillPath.lineTo(xs[n - 1], bottom);
        fillPath.close();
        fillPaint.setShader(new android.graphics.LinearGradient(0, top, 0, bottom,
                (curveColor & 0x00FFFFFF) | 0x59000000, (curveColor & 0x00FFFFFF) | 0x00000000,
                android.graphics.Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);

        // 平滑曲线（Catmull-Rom 转贝塞尔）
        curvePath.reset();
        curvePath.moveTo(xs[0], ys[0]);
        for (int i = 0; i < n - 1; i++) {
            float p0x = i == 0 ? xs[0] : xs[i - 1];
            float p0y = i == 0 ? ys[0] : ys[i - 1];
            float p1x = xs[i], p1y = ys[i];
            float p2x = xs[i + 1], p2y = ys[i + 1];
            float p3x = (i + 2 < n) ? xs[i + 2] : xs[n - 1];
            float p3y = (i + 2 < n) ? ys[i + 2] : ys[n - 1];
            float c1x = p1x + (p2x - p0x) / 6f;
            float c1y = p1y + (p2y - p0y) / 6f;
            float c2x = p2x - (p3x - p1x) / 6f;
            float c2y = p2y - (p3y - p1y) / 6f;
            curvePath.cubicTo(c1x, c1y, c2x, c2y, p2x, p2y);
        }
        canvas.drawPath(curvePath, curvePaint);

        // 数据点 + 数值标签（隔点绘制避免重叠）+ x 轴时间
        int labelStep = Math.max(1, (int) Math.ceil(n / 6.0));
        for (int i = 0; i < n; i++) {
            boolean isSel = (i == selected);
            float r = isSel ? 4.2f * density : 3f * density;
            dotStrokePaint.setStrokeWidth(isSel ? 2.6f * density : 2f * density);
            dotPaint.setColor(isSel ? curveColor : 0xFFFFFFFF);
            dotStrokePaint.setColor(isSel ? 0xFFFFFFFF : curveColor);
            canvas.drawCircle(xs[i], ys[i], r, dotPaint);
            canvas.drawCircle(xs[i], ys[i], r, dotStrokePaint);

            // 数值标注：选中点 或 隔点
            boolean show = isSel || (i % labelStep == 0);
            if (show && i < valueLabels.size() && !valueLabels.get(i).isEmpty()) {
                canvas.drawText(valueLabels.get(i), xs[i], ys[i] - 8 * density, labelPaint);
            }
            // x 轴时间标注：隔点
            if (i % labelStep == 0 && i < xLabels.size()) {
                labelPaint.setAlpha(0xB3);
                canvas.drawText(xLabels.get(i), xs[i], bottom + 15 * density, labelPaint);
                labelPaint.setAlpha(0xFF);
            }
        }

        // 选中竖线
        if (selected >= 0 && selected < n) {
            canvas.drawLine(xs[selected], top - 6 * density, xs[selected], bottom, selLinePaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredH = (int) (220 * density);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int hSize = MeasureSpec.getSize(heightMeasureSpec);
        int h = hMode == MeasureSpec.EXACTLY ? hSize
                : hMode == MeasureSpec.AT_MOST ? Math.min(desiredH, hSize) : desiredH;
        setMeasuredDimension(getDefaultSize(0, widthMeasureSpec), h);
    }
}
