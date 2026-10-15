package com.cleanweather.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

/** 支持顶部下拉刷新回调的 ScrollView */
public class PullScrollView extends ScrollView {

    public interface PullCallback {
        /** 下拉超过阈值，触发一次 */
        void onPullRefresh();
    }

    private PullCallback callback;
    private float lastY = -1;
    private boolean armed = true;

    public PullScrollView(Context context) {
        super(context);
    }

    public PullScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPullCallback(PullCallback cb) {
        this.callback = cb;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (callback != null) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastY = ev.getY();
                    armed = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float y = ev.getY();
                    if (getScrollY() == 0 && armed && lastY >= 0) {
                        float dy = y - lastY;
                        if (dy > 90 * getResources().getDisplayMetrics().density) {
                            armed = false;
                            callback.onPullRefresh();
                        }
                    }
                    lastY = y;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    lastY = -1;
                    break;
            }
        }
        return super.onTouchEvent(ev);
    }
}
