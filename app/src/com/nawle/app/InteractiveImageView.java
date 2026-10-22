package com.nawle.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 支持双指缩放/拖动的图片视图，叠加可点击的交互点（按百分比坐标定位）。
 */
public class InteractiveImageView extends View {

    public interface Host {
        void onHotspotTap(Store.Hotspot h);

        void onHotspotLongPress(Store.Hotspot h);

        void onImageTap(float xPct, float yPct);
    }

    private Bitmap bitmap;
    private final Matrix matrix = new Matrix();
    private final Matrix inverse = new Matrix();
    private final float[] mValues = new float[9];
    private List<Store.Hotspot> hotspots = new ArrayList<>();
    private boolean placementMode = false;
    private Host host;
    private float baseScale = 1f;

    private final Paint pHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEmpty = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectTmp = new RectF();
    private final RectF haloTmp = new RectF();

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    public InteractiveImageView(Context c) {
        super(c);
        init(c);
    }

    public InteractiveImageView(Context c, android.util.AttributeSet attrs) {
        super(c, attrs);
        init(c);
    }

    public InteractiveImageView(Context c, android.util.AttributeSet attrs, int defStyleAttr) {
        super(c, attrs, defStyleAttr);
        init(c);
    }

    private void init(Context c) {
        // 交互点下的暗色光晕，保证亮色照片上也能看清
        pHalo.setColor(Color.argb(70, 0, 0, 0));
        pFill.setColor(Color.argb(215, 255, 255, 255));
        pStroke.setColor(Color.rgb(0, 105, 92));
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(Math.max(2f, Ui.dp(c, 2.5f)));
        pLabel.setColor(Color.rgb(0, 77, 64));
        pLabel.setTextAlign(Paint.Align.CENTER);
        pLabel.setTypeface(Typeface.DEFAULT_BOLD);
        pLabel.setTextSize(Ui.sp(c, 15));
        pEmpty.setColor(Color.argb(170, 255, 255, 255));
        pEmpty.setTextAlign(Paint.Align.CENTER);
        pEmpty.setTextSize(Ui.sp(c, 15));

        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float f = d.getScaleFactor();
                float cur = currentScale();
                float max = baseScale * 6f;
                float min = baseScale * 0.8f;
                if (cur * f > max) f = max / cur;
                if (cur * f < min) f = min / cur;
                matrix.postScale(f, f, d.getFocusX(), d.getFocusY());
                constrain();
                invalidate();
                return true;
            }
        });
        gestureDetector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (bitmap == null) return false;
                matrix.postTranslate(-dx, -dy);
                constrain();
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                handleTap(e.getX(), e.getY());
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Store.Hotspot hit = hitHotspot(e.getX(), e.getY());
                if (hit != null && host != null) host.onHotspotLongPress(hit);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (bitmap == null) return true;
                if (currentScale() > baseScale * 1.3f) {
                    resetToBase();
                } else {
                    matrix.postScale(2.5f, 2.5f, e.getX(), e.getY());
                    constrain();
                    invalidate();
                }
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });
    }

    public void setHost(Host h) {
        this.host = h;
    }

    public void setPlacementMode(boolean on) {
        placementMode = on;
        invalidate();
    }

    public void setImage(Bitmap b) {
        bitmap = b;
        resetToBase();
    }

    public void clearImage() {
        bitmap = null;
        invalidate();
    }

    public void setHotspots(List<Store.Hotspot> list) {
        hotspots = list == null ? new ArrayList<>() : list;
        invalidate();
    }

    private float currentScale() {
        matrix.getValues(mValues);
        return mValues[Matrix.MSCALE_X];
    }

    private void resetToBase() {
        matrix.reset();
        if (bitmap != null && getWidth() > 0 && getHeight() > 0) {
            baseScale = Math.min(getWidth() / (float) bitmap.getWidth(),
                    getHeight() / (float) bitmap.getHeight());
            matrix.setScale(baseScale, baseScale);
            matrix.postTranslate((getWidth() - bitmap.getWidth() * baseScale) / 2f,
                    (getHeight() - bitmap.getHeight() * baseScale) / 2f);
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetToBase();
    }

    /** 防止把图片拖出可视范围 */
    private void constrain() {
        if (bitmap == null) return;
        matrix.getValues(mValues);
        float scale = mValues[Matrix.MSCALE_X];
        float tx = mValues[Matrix.MTRANS_X];
        float ty = mValues[Matrix.MTRANS_Y];
        float w = bitmap.getWidth() * scale;
        float h = bitmap.getHeight() * scale;
        float vw = getWidth();
        float vh = getHeight();
        if (w <= vw) tx = (vw - w) / 2f;
        else {
            if (tx > 0) tx = 0;
            if (tx < vw - w) tx = vw - w;
        }
        if (h <= vh) ty = (vh - h) / 2f;
        else {
            if (ty > 0) ty = 0;
            if (ty < vh - h) ty = vh - h;
        }
        mValues[Matrix.MTRANS_X] = tx;
        mValues[Matrix.MTRANS_Y] = ty;
        matrix.setValues(mValues);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        if (!scaleDetector.isInProgress()) gestureDetector.onTouchEvent(e);
        return true;
    }

    private void handleTap(float sx, float sy) {
        Store.Hotspot hit = hitHotspot(sx, sy);
        if (hit != null) {
            if (host != null) host.onHotspotTap(hit);
            return;
        }
        if (placementMode && bitmap != null && host != null && matrix.invert(inverse)) {
            float[] pts = {sx, sy};
            inverse.mapPoints(pts);
            float px = pts[0] / bitmap.getWidth();
            float py = pts[1] / bitmap.getHeight();
            if (px >= 0f && px <= 1f && py >= 0f && py <= 1f) {
                host.onImageTap(px, py);
            }
        }
    }

    private float hotspotRadius() {
        return Ui.dp(getContext(), 17);
    }

    private Store.Hotspot hitHotspot(float sx, float sy) {
        if (bitmap == null) return null;
        float r = hotspotRadius() + Ui.dp(getContext(), 10);
        float touch = Ui.dp(getContext(), 10);
        for (int i = hotspots.size() - 1; i >= 0; i--) {
            Store.Hotspot h = hotspots.get(i);
            float[] pts = {h.x * bitmap.getWidth(), h.y * bitmap.getHeight()};
            matrix.mapPoints(pts);
            float dx = pts[0] - sx;
            float dy = pts[1] - sy;
            if (dx * dx + dy * dy <= r * r) return h;
            String label = labelFor(h);
            if (!label.isEmpty()) {
                pillRectInto(pts[0], pts[1], label, rectTmp);
                rectTmp.inset(-touch, -touch);
                if (rectTmp.contains(sx, sy)) return h;
            }
        }
        return null;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (bitmap == null) {
            String s = placementMode ? "请先添加照片，再放置交互点" : "暂无照片";
            c.drawText(s, getWidth() / 2f, getHeight() / 2f, pEmpty);
            return;
        }
        c.drawBitmap(bitmap, matrix, null);
        for (Store.Hotspot h : hotspots) {
            float[] pts = {h.x * bitmap.getWidth(), h.y * bitmap.getHeight()};
            matrix.mapPoints(pts);
            String label = labelFor(h);
            if (label.isEmpty()) {
                float r = hotspotRadius();
                c.drawCircle(pts[0], pts[1], r + Ui.dp(getContext(), 3.5f), pHalo);
                c.drawCircle(pts[0], pts[1], r, pFill);
                c.drawCircle(pts[0], pts[1], r, pStroke);
                float ty = pts[1] - (pLabel.ascent() + pLabel.descent()) / 2f;
                c.drawText("•", pts[0], ty, pLabel);
            } else {
                drawLabelPill(c, pts[0], pts[1], label);
            }
        }
    }

    /** 有名字的交互点画成胶囊标签，完整展示名字而不是只取首字 */
    private void drawLabelPill(Canvas c, float cx, float cy, String label) {
        pillRectInto(cx, cy, label, rectTmp);
        haloTmp.set(rectTmp);
        float grow = Ui.dp(getContext(), 3.5f);
        haloTmp.inset(-grow, -grow);
        float corner = rectTmp.height() / 2f;
        c.drawRoundRect(haloTmp, corner, corner, pHalo);
        c.drawRoundRect(rectTmp, corner, corner, pFill);
        c.drawRoundRect(rectTmp, corner, corner, pStroke);
        float ty = cy - (pLabel.ascent() + pLabel.descent()) / 2f;
        c.drawText(label, cx, ty, pLabel);
    }

    private void pillRectInto(float cx, float cy, String label, RectF out) {
        float w = pLabel.measureText(label) + Ui.dp(getContext(), 10) * 2;
        float hgt = (pLabel.descent() - pLabel.ascent()) + Ui.dp(getContext(), 7) * 2;
        out.set(cx - w / 2f, cy - hgt / 2f, cx + w / 2f, cy + hgt / 2f);
    }

    /** 名字过长时截断加省略号，避免标签超出屏幕；空串表示未命名 */
    private String labelFor(Store.Hotspot h) {
        String n = h.childName == null ? "" : h.childName.trim();
        if (n.length() <= 12) return n;
        return n.substring(0, 12) + "…";
    }
}
