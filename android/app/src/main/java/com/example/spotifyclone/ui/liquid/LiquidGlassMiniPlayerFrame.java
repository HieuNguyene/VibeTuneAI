package com.example.spotifyclone.ui.liquid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Floating translucent glass container for the MiniPlayer capsule.
 * Samples and blurs the NavHostFragment behind it so scrolled album art and typography
 * remain softly visible through the frosted surface.
 */
public class LiquidGlassMiniPlayerFrame extends FrameLayout {

    private final LiquidBackdropBlur backdropBlur = new LiquidBackdropBlur();
    private final LiquidDropletPaint dropletPaint = new LiquidDropletPaint();
    private final RectF bounds = new RectF();
    private final float density;

    public LiquidGlassMiniPlayerFrame(@NonNull Context context) {
        this(context, null);
    }

    public LiquidGlassMiniPlayerFrame(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LiquidGlassMiniPlayerFrame(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        density = getResources().getDisplayMetrics().density;
    }

    public void setBackdropSource(@Nullable View backdropSource) {
        backdropBlur.attach(this, backdropSource);
        invalidate();
    }

    public void markBackdropDirty() {
        backdropBlur.markDirty();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        backdropBlur.onAttachedToWindow(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        backdropBlur.detach();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (getWidth() > 0 && getHeight() > 0) {
            boolean isNight = com.example.spotifyclone.util.ThemeManager.isNightMode(getContext());
            dropletPaint.setNightMode(isNight);
            bounds.set(0f, 0f, getWidth(), getHeight());
            backdropBlur.prepareBackdrop(this);
            dropletPaint.drawGlassDock(
                    canvas,
                    bounds,
                    22f * density,
                    density,
                    backdropBlur,
                    isNight ? 0.36f : 0.58f
            );
        }
        super.onDraw(canvas);
    }
}
