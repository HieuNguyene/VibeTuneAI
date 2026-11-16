package com.example.spotifyclone.ui.liquid;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Shared 3D liquid-glass and translucent water-droplet canvas painter.
 * Renders realistic surface-tension geometry: ambient shadow halo, refracted lens interior,
 * upper meniscus specular glare, lower internal caustic bounce, and crisp beveled rims.
 */
public final class LiquidDropletPaint {

    public static final int VARIANT_EMERALD = 0;
    public static final int VARIANT_CLEAR = 1;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint causticPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path dockPath = new Path();
    private final Path dropletPath = new Path();
    private final Path specularPath = new Path();
    private final RectF workRect = new RectF();
    private final RectF specularRect = new RectF();
    private final RectF causticRect = new RectF();
    private boolean isNightMode = true;

    public LiquidDropletPaint() {
        strokePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStyle(Paint.Style.STROKE);
    }

    public void setNightMode(boolean nightMode) {
        this.isNightMode = nightMode;
    }

    /**
     * Draws a floating frosted-glass capsule dock that transmits and blurs underlying views.
     */
    public void drawGlassDock(@NonNull Canvas canvas, @NonNull RectF bounds, float cornerRadius,
                              float density, @Nullable LiquidBackdropBlur backdrop,
                              float tintAlpha) {
        dockPath.reset();
        dockPath.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW);

        // Soft 3D elevation shadow floating above scrollable feeds
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x00000000);
        shadowPaint.setShadowLayer(
                (isNightMode ? 12f : 10f) * density,
                0f,
                (isNightMode ? 5f : 4f) * density,
                isNightMode ? 0x80000000 : 0x260C243D
        );
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, shadowPaint);
        shadowPaint.clearShadowLayer();

        if (backdrop != null) {
            backdrop.drawFrostedGlass(canvas, dockPath, bounds, tintAlpha);
        } else {
            fillPaint.setShader(new LinearGradient(
                    bounds.left, bounds.top, bounds.left, bounds.bottom,
                    isNightMode
                            ? new int[]{0xE6141519, 0xF00E0F12, 0xF5050506}
                            : new int[]{0xF0FFFFFF, 0xE6F2F7FC, 0xDEE6F0FA},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawPath(dockPath, fillPaint);
            fillPaint.setShader(null);
        }

        // Subtle top border highlight
        int clipSave = canvas.save();
        canvas.clipPath(dockPath);
        specularRect.set(
                bounds.left + 3f * density,
                bounds.top + 0.5f * density,
                bounds.right - 3f * density,
                bounds.top + bounds.height() * 0.25f
        );
        specularPaint.setShader(new LinearGradient(
                specularRect.left, specularRect.top,
                specularRect.left, specularRect.bottom,
                isNightMode
                        ? new int[]{0x14FFFFFF, 0x00FFFFFF}
                        : new int[]{0x38FFFFFF, 0x00FFFFFF},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(specularRect, cornerRadius, cornerRadius, specularPaint);
        canvas.restoreToCount(clipSave);

        // Clean hairline dock border
        strokePaint.setShader(null);
        strokePaint.setStrokeWidth(1f * density);
        strokePaint.setColor(isNightMode ? 0x2EE2E8F0 : 0x3D94B3D1);
        workRect.set(bounds);
        workRect.inset(0.5f * density, 0.5f * density);
        canvas.drawRoundRect(workRect, cornerRadius, cornerRadius, strokePaint);
    }

    /**
     * Draws a clean capsule indicator with subtle elevation shadow and balanced contrast.
     */
    public void drawWaterDroplet(@NonNull Canvas canvas, @NonNull RectF bounds, float cornerRadius,
                                 float density, int variant, @Nullable LiquidBackdropBlur backdrop,
                                 float hostWidth, float hostHeight, boolean activeGlow) {
        if (bounds.width() <= 2f || bounds.height() <= 2f) return;

        dropletPath.reset();
        dropletPath.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW);

        // Subtle elevation shadow
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x00000000);
        shadowPaint.setShadowLayer(
                3f * density, 0f, 1.5f * density,
                isNightMode ? 0x38000000 : 0x1A0C243D
        );
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, shadowPaint);
        shadowPaint.clearShadowLayer();

        // Clean active vs inactive capsule fill (zero neon glow / glare blobs)
        fillPaint.setShader(null);
        if (variant == VARIANT_EMERALD) {
            fillPaint.setColor(isNightMode ? 0xFF38BDF8 : 0xFF0EA5E9);
        } else {
            fillPaint.setColor(isNightMode ? 0x26FFFFFF : 0xEBEEF6FC);
        }
        canvas.drawPath(dropletPath, fillPaint);

        // Crisp hairline border
        strokePaint.setShader(null);
        strokePaint.setStrokeWidth(1f * density);
        if (variant == VARIANT_EMERALD) {
            strokePaint.setColor(isNightMode ? 0x4D7DD3FC : 0x260C4A6E);
        } else {
            strokePaint.setColor(isNightMode ? 0x28E2E8F0 : 0x66CBDCEB);
        }
        workRect.set(bounds);
        workRect.inset(0.5f * density, 0.5f * density);
        canvas.drawRoundRect(workRect, cornerRadius, cornerRadius, strokePaint);
    }

    /**
     * Draws an expanding translucent water surface ripple ring centered at (cx, cy).
     */
    public void drawWaterRipple(@NonNull Canvas canvas, float cx, float cy,
                                float maxRadius, float progress, float density, boolean emeraldTint) {
        if (progress <= 0f || progress >= 1f || maxRadius <= 0f) return;
        float radius = (0.18f + 0.82f * progress) * maxRadius;
        float fade = (1f - progress) * (1f - progress);
        int baseColor = emeraldTint
                ? (isNightMode ? Color.rgb(226, 232, 240) : Color.rgb(2, 132, 199))
                : (isNightMode ? Color.WHITE : Color.rgb(51, 65, 85));
        int alpha = Math.round(fade * 145f);
        if (alpha <= 1) return;

        ripplePaint.setStrokeWidth((2.4f - 1.5f * progress) * density);
        ripplePaint.setColor(Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)));
        canvas.drawCircle(cx, cy, radius, ripplePaint);
    }
}
