package com.example.spotifyclone.ui.liquid;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.spotifyclone.R;

/**
 * Ergonomic helpers connecting existing buttons, pills, and CTAs with organic
 * water-droplet spring physics and gliding pill transitions.
 */
public final class LiquidDropletHelper {

    private static final int TAG_ACTIVE_ANIM = R.id.liquid_droplet_anim_tag;

    private LiquidDropletHelper() {
    }

    /**
     * Attaches resilient water-droplet touch physics to one or more interactive views:
     * gentle volume-conserving squish on touch-down, followed by damped jelly overshoot
     * rebound on release. Preserves all click/focus listeners cleanly.
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void attachDropletSpring(@Nullable View... views) {
        if (views == null) return;
        for (View view : views) {
            if (view == null) continue;
            view.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        cancelAnim(v);
                        ObjectAnimator down = ObjectAnimator.ofPropertyValuesHolder(
                                v,
                                PropertyValuesHolder.ofFloat(View.SCALE_X, v.getScaleX(), 0.94f),
                                PropertyValuesHolder.ofFloat(View.SCALE_Y, v.getScaleY(), 0.91f)
                        );
                        down.setDuration(110L);
                        down.setInterpolator(new DecelerateInterpolator(1.6f));
                        v.setTag(TAG_ACTIVE_ANIM, down);
                        down.start();
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        cancelAnim(v);
                        // Conserve droplet surface area: slight horizontal widen then spring back
                        ObjectAnimator rebound = ObjectAnimator.ofPropertyValuesHolder(
                                v,
                                PropertyValuesHolder.ofFloat(View.SCALE_X, v.getScaleX(), 1.055f, 0.985f, 1.0f),
                                PropertyValuesHolder.ofFloat(View.SCALE_Y, v.getScaleY(), 0.96f, 1.02f, 1.0f)
                        );
                        rebound.setDuration(320L);
                        rebound.setInterpolator(new OvershootInterpolator(1.4f));
                        rebound.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                v.setScaleX(1f);
                                v.setScaleY(1f);
                            }
                        });
                        v.setTag(TAG_ACTIVE_ANIM, rebound);
                        rebound.start();
                        break;
                }
                return false;
            });
        }
    }

    /**
     * Selects a pill inside a {@link LiquidPillRow} (animating the sliding, stretching
     * 3D water droplet across sibling pills), or falls back cleanly on standalone pills.
     */
    public static void selectPill(@Nullable TextView chip, boolean active, boolean animate) {
        if (chip == null) return;
        ViewParent parent = chip.getParent();
        if (active && parent instanceof LiquidPillRow) {
            ((LiquidPillRow) parent).selectChild(chip, animate);
            return;
        }
        applyDirectPillStyle(chip, active, !(parent instanceof LiquidPillRow));
    }

    /**
     * Styles a single TextView pill as a 3D translucent water droplet or frosted glass chip.
     */
    public static void applyDirectPillStyle(@NonNull TextView chip, boolean active, boolean applyBackground) {
        if (applyBackground) {
            chip.setBackgroundResource(active ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
        } else {
            chip.setBackgroundResource(active ? 0 : R.drawable.bg_pill_inactive);
        }
        chip.setTextColor(ContextCompat.getColor(
                chip.getContext(),
                active ? R.color.on_primary_accent : R.color.liquid_text_inactive
        ));
        if (active && !com.example.spotifyclone.util.ThemeManager.isNightMode(chip.getContext())) {
            chip.setShadowLayer(3.5f, 0f, 1.5f, 0x3A000000);
        } else {
            chip.setShadowLayer(0f, 0f, 0f, 0);
        }
    }

    private static void cancelAnim(@NonNull View v) {
        Object tag = v.getTag(TAG_ACTIVE_ANIM);
        if (tag instanceof Animator) {
            ((Animator) tag).cancel();
        }
        v.animate().cancel();
    }
}
