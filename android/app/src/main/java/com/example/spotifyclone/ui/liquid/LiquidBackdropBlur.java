package com.example.spotifyclone.ui.liquid;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

/**
 * Lightweight offscreen backdrop sampler and integer StackBlur engine.
 * Captures the pixels of a sibling/underlying content root (e.g. NavHostFragment)
 * strictly behind a floating glass dock, blurs them at reduced scale, and renders
 * frosted glass panels as well as optically magnified water-droplet lenses.
 */
public class LiquidBackdropBlur {

    private static final int DOWNSCALE = 6;
    private static final int BLUR_RADIUS = 8;
    private static final long MIN_CAPTURE_INTERVAL_MS = 16L;

    /**
     * Global re-entrancy guard prevents recursive drawing if any source hierarchy
     * happens to intersect a glass container during backdrop snapshotting.
     */
    private static boolean sIsCapturing = false;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint lensPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint edgeShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix lensMatrix = new Matrix();
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    private final int[] targetLoc = new int[2];
    private final int[] sourceLoc = new int[2];

    private WeakReference<View> sourceRef = new WeakReference<>(null);
    private WeakReference<View> targetRef = new WeakReference<>(null);

    private Bitmap captureBitmap;
    private Bitmap blurBitmap;
    private Canvas captureCanvas;

    private int[] pixBuffer;
    private int[] dvBuffer;
    private int dvLength = 0;
    private int[] rBuf;
    private int[] gBuf;
    private int[] bBuf;
    private int[] vMinBuf;

    private long lastCaptureMs = 0L;
    private boolean forceNextCapture = true;
    private boolean listenersAttached = false;
    private boolean isNightMode = true;

    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::notifyBackdropMoved;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::notifyBackdropMoved;

    private void notifyBackdropMoved() {
        View target = targetRef.get();
        if (target != null && !sIsCapturing && target.getVisibility() == View.VISIBLE) {
            forceNextCapture = true;
            target.postInvalidateOnAnimation();
        }
    }

    public void attach(@NonNull View targetView, @Nullable View backdropSource) {
        detach();
        this.targetRef = new WeakReference<>(targetView);
        this.sourceRef = new WeakReference<>(backdropSource);
        this.forceNextCapture = true;
        if (backdropSource != null && targetView.isAttachedToWindow()) {
            attachObservers(backdropSource);
        }
    }

    public void onAttachedToWindow(@NonNull View targetView) {
        this.targetRef = new WeakReference<>(targetView);
        View source = sourceRef.get();
        if (source != null) {
            attachObservers(source);
        }
    }

    public void detach() {
        View source = sourceRef.get();
        if (source != null && listenersAttached) {
            ViewTreeObserver vto = source.getViewTreeObserver();
            if (vto.isAlive()) {
                vto.removeOnScrollChangedListener(scrollListener);
                vto.removeOnGlobalLayoutListener(layoutListener);
            }
        }
        listenersAttached = false;
        forceNextCapture = true;
    }

    public void markDirty() {
        forceNextCapture = true;
    }

    private void attachObservers(@NonNull View source) {
        if (listenersAttached) return;
        ViewTreeObserver vto = source.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.addOnScrollChangedListener(scrollListener);
            vto.addOnGlobalLayoutListener(layoutListener);
            listenersAttached = true;
        }
    }

    /**
     * Captures and blurs the region of {@code source} strictly underneath {@code target}.
     */
    public boolean prepareBackdrop(@NonNull View target) {
        if (sIsCapturing) return blurBitmap != null;
        View source = sourceRef.get();
        if (source == null || !source.isLaidOut() || !target.isLaidOut()) {
            return false;
        }
        int w = target.getWidth();
        int h = target.getHeight();
        if (w <= 0 || h <= 0) return false;

        long now = SystemClock.uptimeMillis();
        if (!forceNextCapture && blurBitmap != null && (now - lastCaptureMs) < MIN_CAPTURE_INTERVAL_MS) {
            return true;
        }

        int bw = Math.max(12, Math.round((float) w / DOWNSCALE));
        int bh = Math.max(8, Math.round((float) h / DOWNSCALE));
        ensureBitmaps(bw, bh);

        target.getLocationInWindow(targetLoc);
        source.getLocationInWindow(sourceLoc);
        float dx = targetLoc[0] - sourceLoc[0];
        float dy = targetLoc[1] - sourceLoc[1];

        isNightMode = com.example.spotifyclone.util.ThemeManager.isNightMode(target.getContext());
        sIsCapturing = true;
        try {
            captureBitmap.eraseColor(isNightMode ? 0xFF050506 : 0xFFF2F7FC);
            int save = captureCanvas.save();
            captureCanvas.scale((float) bw / w, (float) bh / h);
            captureCanvas.translate(-dx, -dy);
            captureCanvas.clipRect(dx, dy, dx + w, dy + h);
            source.draw(captureCanvas);
            captureCanvas.restoreToCount(save);

            applyStackBlur(captureBitmap, blurBitmap, BLUR_RADIUS);
            lastCaptureMs = now;
            forceNextCapture = false;
            return true;
        } catch (Throwable ignored) {
            return blurBitmap != null;
        } finally {
            sIsCapturing = false;
        }
    }

    /**
     * Draws the frosted backdrop inside a pre-clipped Path (e.g. floating glass dock).
     */
    public void drawFrostedGlass(@NonNull Canvas canvas, @NonNull Path clipPath,
                                 @NonNull RectF bounds, float tintAlpha) {
        int save = canvas.save();
        canvas.clipPath(clipPath);
        if (blurBitmap != null && !blurBitmap.isRecycled()) {
            srcRect.set(0, 0, blurBitmap.getWidth(), blurBitmap.getHeight());
            dstRect.set(bounds);
            bitmapPaint.setAlpha(245);
            canvas.drawBitmap(blurBitmap, srcRect, dstRect, bitmapPaint);
        } else {
            canvas.drawColor(isNightMode ? 0xCC0E0F12 : 0xDCEAF3FC);
        }

        // Smoked obsidian-black glass glaze (night) or crisp airy frost (day).
        int a = Math.max(0, Math.min(255, Math.round(tintAlpha * 255f)));
        canvas.drawColor(isNightMode ? Color.argb(a, 6, 6, 8) : Color.argb(a, 244, 249, 255));
        canvas.restoreToCount(save);
    }

    public boolean isNightMode() {
        return isNightMode;
    }

    /**
     * Draws a curved optical water-lens sample inside a moving droplet with slight magnification
     * ({@code zoomFactor} > 1.0) so items passing behind bend slightly at droplet edges.
     */
    public void drawRefractedLens(@NonNull Canvas canvas, @NonNull Path clipPath,
                                  @NonNull RectF dropletBounds, float viewWidth, float viewHeight,
                                  float zoomFactor) {
        if (blurBitmap == null || blurBitmap.isRecycled() || viewWidth <= 0f || viewHeight <= 0f) {
            return;
        }
        int save = canvas.save();
        canvas.clipPath(clipPath);

        float scaleX = viewWidth / blurBitmap.getWidth();
        float scaleY = viewHeight / blurBitmap.getHeight();
        float cx = dropletBounds.centerX();
        float cy = dropletBounds.centerY();

        lensMatrix.reset();
        lensMatrix.postScale(scaleX, scaleY);
        lensMatrix.postScale(zoomFactor, zoomFactor * 1.03f, cx, cy);

        lensPaint.setAlpha(215);
        canvas.drawBitmap(blurBitmap, lensMatrix, lensPaint);

        edgeShadePaint.setShader(new LinearGradient(
                dropletBounds.left, dropletBounds.top,
                dropletBounds.left, dropletBounds.bottom,
                isNightMode
                        ? new int[]{0x22FFFFFF, 0x06000000, 0x26000000}
                        : new int[]{0x42FFFFFF, 0x04000000, 0x16000000},
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(dropletBounds, edgeShadePaint);
        edgeShadePaint.setShader(null);
        canvas.restoreToCount(save);
    }

    private void ensureBitmaps(int bw, int bh) {
        if (captureBitmap != null && captureBitmap.getWidth() == bw && captureBitmap.getHeight() == bh) {
            return;
        }
        if (captureBitmap != null) captureBitmap.recycle();
        if (blurBitmap != null) blurBitmap.recycle();

        captureBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        blurBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        captureCanvas = new Canvas(captureBitmap);

        int len = bw * bh;
        pixBuffer = new int[len];
        rBuf = new int[len];
        gBuf = new int[len];
        bBuf = new int[len];
        vMinBuf = new int[Math.max(bw, bh)];
    }

    private void applyStackBlur(@NonNull Bitmap src, @NonNull Bitmap dst, int radius) {
        if (radius < 1) return;
        int w = src.getWidth();
        int h = src.getHeight();
        int[] pix = pixBuffer;
        src.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int div = radius + radius + 1;

        int[] r = rBuf;
        int[] g = gBuf;
        int[] b = bBuf;
        int[] vmin = vMinBuf;

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int neededDv = 256 * divsum;
        if (dvBuffer == null || dvLength < neededDv) {
            dvBuffer = new int[neededDv];
            dvLength = neededDv;
            for (int i = 0; i < neededDv; i++) {
                dvBuffer[i] = i / divsum;
            }
        }
        int[] dv = dvBuffer;

        int[][] stack = new int[div][3];
        int r1 = radius + 1;
        int yw = 0;
        int yi = 0;

        for (int y = 0; y < h; y++) {
            int rsum = 0, gsum = 0, bsum = 0;
            int routsum = 0, goutsum = 0, boutsum = 0;
            int rinsum = 0, ginsum = 0, binsum = 0;

            for (int i = -radius; i <= radius; i++) {
                int p = pix[yi + Math.min(wm, Math.max(i, 0))];
                int[] sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                int rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            int stackpointer = radius;

            for (int x = 0; x < w; x++) {
                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                int stackstart = stackpointer - radius + div;
                int[] sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                int p = pix[yw + vmin[x]];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];
                yi++;
            }
            yw += w;
        }

        for (int x = 0; x < w; x++) {
            int rsum = 0, gsum = 0, bsum = 0;
            int routsum = 0, goutsum = 0, boutsum = 0;
            int rinsum = 0, ginsum = 0, binsum = 0;
            int yp = -radius * w;

            for (int i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;
                int[] sir = stack[i + radius];
                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                int rbs = r1 - Math.abs(i);
                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            int stackpointer = radius;

            for (int y = 0; y < h; y++) {
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                int stackstart = stackpointer - radius + div;
                int[] sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                int p = x + vmin[y];
                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }
        dst.setPixels(pix, 0, w, 0, 0, w, h);
    }
}
