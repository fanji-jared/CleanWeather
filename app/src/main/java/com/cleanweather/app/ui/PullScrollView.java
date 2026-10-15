package com.cleanweather.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

/**
 * 支持顶部下拉刷新的 ScrollView。
 * 下拉时显示刷新头部（图标 + 上次更新时间提示），松手超过阈值即触发刷新；
 * 刷新过程中头部保持显示，由调用方在刷新结束后调用 {@link #collapseHeader()} 收起。
 */
public class PullScrollView extends ScrollView {

    public interface PullCallback {
        /** 下拉进度（1.0 ≈ 达到触发阈值），用于更新头部文案 / 视觉 */
        void onPullProgress(float progress);

        /** 松手且超过阈值，触发一次刷新 */
        void onPullRefresh();
    }

    private PullCallback callback;
    private View header;
    private View icon;

    private int thresholdPx;
    private int maxHeaderPx;

    private float startY = -1;
    private boolean dragging;

    public PullScrollView(Context context) {
        super(context);
        init();
    }

    public PullScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        thresholdPx = dp(60);
        maxHeaderPx = dp(80);
    }

    public void setPullCallback(PullCallback cb) {
        this.callback = cb;
    }

    /** 绑定下拉刷新头部与其中的图标（用于旋转反馈） */
    public void setRefreshHeader(View header, View icon) {
        this.header = header;
        this.icon = icon;
        applyHeaderHeight(0);
    }

    /** 刷新结束：动画收起头部 */
    public void collapseHeader() {
        if (header == null) return;
        int from = header.getLayoutParams() == null ? 0 : header.getLayoutParams().height;
        if (from <= 0) {
            applyHeaderHeight(0);
            return;
        }
        ValueAnimator anim = ValueAnimator.ofInt(from, 0);
        anim.setDuration(180);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                applyHeaderHeight((int) animation.getAnimatedValue());
            }
        });
        anim.start();
    }

    private void applyHeaderHeight(int h) {
        if (header == null) return;
        ViewGroup.LayoutParams lp = header.getLayoutParams();
        if (lp == null) return;
        if (lp.height != h) {
            lp.height = h;
            header.setLayoutParams(lp);
        }
        float progress = thresholdPx <= 0 ? 0f : Math.min(1f, h / (float) thresholdPx);
        header.setAlpha(h <= 0 ? 0f : Math.min(1f, 0.35f + 0.65f * progress));
        if (icon != null) {
            icon.setRotation(Math.min(1f, h / (float) thresholdPx) * 180f);
        }
    }

    private int currentHeight() {
        return header == null || header.getLayoutParams() == null ? 0 : header.getLayoutParams().height;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (callback == null || header == null) {
            return super.onTouchEvent(ev);
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startY = ev.getY();
                dragging = false;
                break;

            case MotionEvent.ACTION_MOVE: {
                float dy = startY < 0 ? 0 : ev.getY() - startY;
                if (getScrollY() <= 0 && dy > 0) {
                    dragging = true;
                    int h = (int) Math.min(dy * 0.5f, maxHeaderPx + dp(24));
                    applyHeaderHeight(h);
                    callback.onPullProgress(thresholdPx <= 0 ? 0f : h / (float) thresholdPx);
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                startY = -1;
                if (dragging) {
                    dragging = false;
                    int h = currentHeight();
                    if (h >= thresholdPx) {
                        callback.onPullRefresh();
                        // 头部保持显示，等调用方刷新结束后 collapseHeader()
                    } else {
                        collapseHeader();
                    }
                    return true;
                }
                break;
            }
        }
        return super.onTouchEvent(ev);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
