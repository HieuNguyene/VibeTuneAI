package com.example.spotifyclone.ui.liquid;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.spotifyclone.R;
import com.example.spotifyclone.util.ThemeManager;

/**
 * Horizontal pill/chip LinearLayout that renders a single unified 3D translucent emerald
 * water-droplet sliding smoothly between selected chips with elastic metaball stretching,
 * damped impact wobble, and a radial water ripple wave.
 */
public class LiquidPillRow extends LinearLayout {

    private static final long MORPH_DURATION_MS = 400L;

    private final LiquidDropletPaint dropletPaint = new LiquidDropletPaint();
    private final RectF dropletRect = new RectF();
    private final RectF restingRect = new RectF();
    private final float density;

    private View fromChild;
    private View toChild;
    private View selectedChild;

    private float animProgress = 1f;
    private float rippleCx = 0f;
    private float rippleCy = 0f;
    private float rippleProgress = 1f;
    private ValueAnimator morphAnimator;

    public LiquidPillRow(@NonNull Context context) {
        this(context, null);
    }

    public LiquidPillRow(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LiquidPillRow(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        density = getResources().getDisplayMetrics().density;
    }

    /**
     * Animates or snaps the floating 3D water droplet onto {@code target}.
     */
    public void selectChild(@Nullable View target, boolean animate) {
        if (target == null || target.getParent() != this || target.getVisibility() != VISIBLE) {
            return;
        }
        View previous = selectedChild;
        selectedChild = target;

        // Apply crisp luminous white text on active pill & translucent frosted clear capsules on others
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof TextView) {
                boolean isSel = (child == target);
                LiquidDropletHelper.applyDirectPillStyle((TextView) child, isSel, false);
                child.setTag(R.id.liquid_droplet_selected_tag, isSel);
            }
        }

        if (previous == null || previous == target || previous.getParent() != this
                || previous.getVisibility() != VISIBLE || !animate || getWidth() <= 0) {
            fromChild = target;
            toChild = target;
            animProgress = 1f;
            if (morphAnimator != null) morphAnimator.cancel();
            invalidate();
            return;
        }

        if (morphAnimator != null) morphAnimator.cancel();
        fromChild = previous;
        toChild = target;
        rippleCx = (target.getLeft() + target.getRight()) * 0.5f;
        rippleCy = (target.getTop() + target.getBottom()) * 0.5f;
        rippleProgress = 0.01f;
        animProgress = 0f;

        morphAnimator = ValueAnimator.ofFloat(0f, 1f);
        morphAnimator.setDuration(MORPH_DURATION_MS);
        morphAnimator.setInterpolator(new LinearInterpolator());
        morphAnimator.addUpdateListener(a -> {
            animProgress = (float) a.getAnimatedValue();
            rippleProgress = Math.min(1f, animProgress * 1.2f);
            invalidate();
        });
        morphAnimator.start();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (selectedChild == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == VISIBLE && Boolean.TRUE.equals(child.getTag(R.id.liquid_droplet_selected_tag))) {
                    selectedChild = child;
                    fromChild = child;
                    toChild = child;
                    break;
                }
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (morphAnimator != null) morphAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        dropletPaint.setNightMode(ThemeManager.isNightMode(getContext()));
        // 1. Subtle frosted resting silhouette behind every visible pill during travel
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == VISIBLE && child.getWidth() > 0) {
                restingRect.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
                if (child != selectedChild || animProgress < 1f) {
                    dropletPaint.drawWaterDroplet(
                            canvas,
                            restingRect,
                            restingRect.height() * 0.5f,
                            density,
                            LiquidDropletPaint.VARIANT_CLEAR,
                            null,
                            0f,
                            0f,
                            false
                    );
                }
            }
        }

        // 2. Sliding & morphing 3D emerald translucent water droplet
        computeDropletRect();
        if (!dropletRect.isEmpty()) {
            dropletPaint.drawWaterDroplet(
                    canvas,
                    dropletRect,
                    dropletRect.height() * 0.5f,
                    density,
                    LiquidDropletPaint.VARIANT_EMERALD,
                    null,
                    0f,
                    0f,
                    true
            );
        }

        // 3. Radial water wave ripple at destination
        if (rippleProgress > 0f && rippleProgress < 1f && selectedChild != null) {
            dropletPaint.drawWaterRipple(
                    canvas,
                    rippleCx,
                    rippleCy,
                    Math.max(selectedChild.getHeight() * 1.15f, 36f * density),
                    rippleProgress,
                    density,
                    true
            );
        }

        // 4. Foreground labels crisp above the droplet
        super.dispatchDraw(canvas);
    }

    private void computeDropletRect() {
        View target = toChild != null ? toChild : selectedChild;
        if (target == null || target.getVisibility() != VISIBLE || target.getWidth() <= 0) {
            dropletRect.setEmpty();
            return;
        }
        View origin = (fromChild != null && fromChild.getVisibility() == VISIBLE && fromChild.getWidth() > 0)
                ? fromChild
                : target;

        float t = Math.max(0f, Math.min(1f, animProgress));
        if (t >= 1f || origin == target) {
            dropletRect.set(target.getLeft(), target.getTop(), target.getRight(), target.getBottom());
            return;
        }

        float leadT = smoothOutCubic(t);
        float trailT = smoothInOutDelayed(t);
        boolean movingRight = target.getLeft() >= origin.getLeft();

        float left = movingRight
                ? lerp(origin.getLeft(), target.getLeft(), trailT)
                : lerp(origin.getLeft(), target.getLeft(), leadT);
        float right = movingRight
                ? lerp(origin.getRight(), target.getRight(), leadT)
                : lerp(origin.getRight(), target.getRight(), trailT);

        float settlePhase = Math.max(0f, (t - 0.55f) / 0.45f);
        float stretch = (float) Math.sin(t * Math.PI) * 2.2f * density;
        float bounce = settlePhase > 0f
                ? (float) (Math.sin(settlePhase * Math.PI * 2.3) * Math.exp(-settlePhase * 3.8) * 2.5f * density)
                : 0f;
        float dy = stretch - bounce;

        float top = lerp(origin.getTop(), target.getTop(), leadT) + dy * 0.5f;
        float bottom = lerp(origin.getBottom(), target.getBottom(), leadT) - dy * 0.5f;

        dropletRect.set(left, Math.max(0f, top), right, Math.min(getHeight(), bottom));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothOutCubic(float t) {
        float u = Math.min(1f, t * 1.18f);
        float inv = 1f - u;
        return 1f - inv * inv * inv;
    }

    private static float smoothInOutDelayed(float t) {
        float d = Math.max(0f, Math.min(1f, (t - 0.14f) / 0.86f));
        return d * d * (3f - 2f * d);
    }
}
