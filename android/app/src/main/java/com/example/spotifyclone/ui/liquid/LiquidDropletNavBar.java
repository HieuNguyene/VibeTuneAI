package com.example.spotifyclone.ui.liquid;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.ui.NavigationUI;

import com.example.spotifyclone.R;
import com.example.spotifyclone.util.ThemeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating 3D liquid-glass Bottom Navigation dock with live frosted see-through backdrop,
 * optical curved lens magnification inside the moving emerald water droplet, elastic metaball
 * horizontal stretch during transitions, and damped water wobble upon arrival.
 */
public class LiquidDropletNavBar extends FrameLayout {

    private static final long MORPH_DURATION_MS = 480L;

    private final LiquidBackdropBlur backdropBlur = new LiquidBackdropBlur();
    private final LiquidDropletPaint dropletPaint = new LiquidDropletPaint();
    private final LinearLayout rowContainer;
    private final List<TabEntry> tabs = new ArrayList<>();

    private final RectF dockBounds = new RectF();
    private final RectF dropletRect = new RectF();
    private final float density;

    private Menu backingMenu;
    private NavController navController;

    private int selectedIndex = 0;
    private int animFromIndex = 0;
    private int animToIndex = 0;
    private float animProgress = 1f;
    private float touchScale = 1f;
    private float rippleCx = 0f;
    private float rippleCy = 0f;
    private float rippleProgress = 1f;

    public interface OnTabClickListener {
        boolean onTabClick(MenuItem item, int index);
    }
    private OnTabClickListener onTabClickListener;

    public void setOnTabClickListener(@Nullable OnTabClickListener listener) {
        this.onTabClickListener = listener;
    }

    private ValueAnimator morphAnimator;
    private ValueAnimator touchAnimator;

    private static final class TabEntry {
        final MenuItem menuItem;
        final LinearLayout container;
        final ImageView icon;
        final TextView label;

        TabEntry(MenuItem menuItem, LinearLayout container, ImageView icon, TextView label) {
            this.menuItem = menuItem;
            this.container = container;
            this.icon = icon;
            this.label = label;
        }
    }

    public LiquidDropletNavBar(@NonNull Context context) {
        this(context, null);
    }

    public LiquidDropletNavBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LiquidDropletNavBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        density = getResources().getDisplayMetrics().density;

        rowContainer = new LinearLayout(context);
        rowContainer.setOrientation(LinearLayout.HORIZONTAL);
        rowContainer.setGravity(Gravity.CENTER_VERTICAL);
        rowContainer.setClipChildren(false);
        rowContainer.setClipToPadding(false);
        int padH = Math.round(6 * density);
        int padV = Math.round(5 * density);
        rowContainer.setPadding(padH, padV, padH, padV);
        addView(rowContainer, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        inflateMenu(R.menu.bottom_nav_menu);
    }

    /**
     * Links the NavHostFragment or scrollable hierarchy behind this dock for live frosted blur.
     */
    public void setBackdropSource(@Nullable View backdropSource) {
        backdropBlur.attach(this, backdropSource);
        invalidate();
    }

    public void markBackdropDirty() {
        backdropBlur.markDirty();
        invalidate();
    }

    /**
     * Populates navigation tabs cleanly from a standard Android menu resource.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    public void inflateMenu(@MenuRes int menuResId) {
        tabs.clear();
        rowContainer.removeAllViews();

        PopupMenu temp = new PopupMenu(getContext(), this);
        backingMenu = temp.getMenu();
        new MenuInflater(getContext()).inflate(menuResId, backingMenu);

        for (int i = 0; i < backingMenu.size(); i++) {
            final int index = i;
            final MenuItem item = backingMenu.getItem(i);

            LinearLayout tabLayout = new LinearLayout(getContext());
            tabLayout.setId(item.getItemId());
            tabLayout.setOrientation(LinearLayout.VERTICAL);
            tabLayout.setGravity(Gravity.CENTER);
            int tabPadH = Math.round(2 * density);
            tabLayout.setPadding(tabPadH, 0, tabPadH, 0);
            tabLayout.setClipChildren(false);
            tabLayout.setClickable(true);
            tabLayout.setFocusable(true);
            tabLayout.setContentDescription(item.getTitle());

            ImageView icon = new ImageView(getContext());
            int iconSize = Math.round(22 * density);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconLp.bottomMargin = Math.round(3 * density);
            icon.setLayoutParams(iconLp);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageDrawable(item.getIcon());

            TextView label = new TextView(getContext());
            label.setText(item.getTitle());
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            label.setSingleLine(true);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);

            tabLayout.addView(icon);
            tabLayout.addView(label);

            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            );
            rowContainer.addView(tabLayout, tabLp);

            final TabEntry entry = new TabEntry(item, tabLayout, icon, label);
            tabs.add(entry);

            tabLayout.setOnClickListener(v -> onTabTapped(index, entry));
            tabLayout.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    animateTouchScale(0.92f, 95L);
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    animateTouchScale(1.0f, 240L);
                }
                return false;
            });
        }
        updateTabVisualStates(selectedIndex);
    }

    /**
     * Connects NavigationUI tab selection & destination sync identically to BottomNavigationView.
     */
    public void bindNavController(@NonNull NavController controller) {
        this.navController = controller;
        NavDestination current = controller.getCurrentDestination();
        if (current != null) {
            syncDestination(current.getId(), false);
        }
        controller.addOnDestinationChangedListener((ctrl, destination, arguments) ->
                syncDestination(destination.getId(), true)
        );
    }

    public void setSelectedItemId(@IdRes int itemId, boolean animate) {
        syncDestination(itemId, animate);
    }

    private void onTabTapped(int index, @NonNull TabEntry entry) {
        triggerWaterRipple(entry.container);
        if (onTabClickListener != null && onTabClickListener.onTabClick(entry.menuItem, index)) {
            animateToIndex(index, true);
            return;
        }
        if (navController != null) {
            try {
                androidx.navigation.NavOptions options = new androidx.navigation.NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setRestoreState(true)
                        .setPopUpTo(navController.getGraph().getStartDestinationId(), false, true)
                        .build();
                navController.navigate(entry.menuItem.getItemId(), null, options);
            } catch (Exception e) {
                NavigationUI.onNavDestinationSelected(entry.menuItem, navController);
            }
        } else {
            animateToIndex(index, true);
        }
    }

    private void syncDestination(@IdRes int destId, boolean animate) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).menuItem.getItemId() == destId) {
                animateToIndex(i, animate);
                return;
            }
        }
    }

    private void animateToIndex(int targetIndex, boolean animate) {
        if (targetIndex < 0 || targetIndex >= tabs.size()) return;
        if (selectedIndex == targetIndex && animProgress >= 1f) {
            updateTabVisualStates(targetIndex);
            return;
        }

        if (morphAnimator != null) {
            morphAnimator.cancel();
        }
        animFromIndex = selectedIndex;
        animToIndex = targetIndex;
        selectedIndex = targetIndex;

        updateTabVisualStates(targetIndex);

        if (!animate || getWidth() <= 0) {
            animFromIndex = targetIndex;
            animToIndex = targetIndex;
            animProgress = 1f;
            invalidate();
            return;
        }

        animProgress = 0f;
        morphAnimator = ValueAnimator.ofFloat(0f, 1f);
        morphAnimator.setDuration(MORPH_DURATION_MS);
        morphAnimator.setInterpolator(new LinearInterpolator());
        morphAnimator.addUpdateListener(anim -> {
            animProgress = (float) anim.getAnimatedValue();
            rippleProgress = Math.min(1f, animProgress * 1.15f);
            invalidate();
        });
        morphAnimator.start();
    }

    private void triggerWaterRipple(@NonNull View tabView) {
        rippleCx = rowContainer.getLeft() + (tabView.getLeft() + tabView.getRight()) * 0.5f;
        rippleCy = rowContainer.getTop() + (tabView.getTop() + tabView.getBottom()) * 0.5f;
        rippleProgress = 0.01f;
        invalidate();
    }

    private void animateTouchScale(float targetScale, long duration) {
        if (touchAnimator != null) touchAnimator.cancel();
        touchAnimator = ValueAnimator.ofFloat(touchScale, targetScale);
        touchAnimator.setDuration(duration);
        touchAnimator.setInterpolator(new DecelerateInterpolator(1.7f));
        touchAnimator.addUpdateListener(anim -> {
            touchScale = (float) anim.getAnimatedValue();
            invalidate();
        });
        touchAnimator.start();
    }

    private void updateTabVisualStates(int activeIdx) {
        boolean isNight = com.example.spotifyclone.util.ThemeManager.isNightMode(getContext());
        int inactiveColor = isNight ? 0xD9CBD5E1 : 0xDE1E293B;
        int activeColor = androidx.core.content.ContextCompat.getColor(getContext(), R.color.on_primary_accent);
        for (int i = 0; i < tabs.size(); i++) {
            TabEntry tab = tabs.get(i);
            boolean active = (i == activeIdx);
            int color = active ? activeColor : inactiveColor;
            tab.icon.setImageTintList(ColorStateList.valueOf(color));
            tab.label.setTextColor(color);
            tab.label.setTypeface(null, active
                    ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
            tab.container.animate()
                    .translationY(0f)
                    .scaleX(active ? 1.03f : 1f)
                    .scaleY(active ? 1.03f : 1f)
                    .setDuration(220L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            tab.container.setSelected(active);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        backdropBlur.onAttachedToWindow(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        backdropBlur.detach();
        if (morphAnimator != null) morphAnimator.cancel();
        if (touchAnimator != null) touchAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (getWidth() > 0 && getHeight() > 0) {
            float dockCorner = getHeight() * 0.5f;
            dockBounds.set(0f, 0f, getWidth(), getHeight());

            // 1. Live frosted glass dock letting underlying NavHostFragment views blur through
            boolean isNight = ThemeManager.isNightMode(getContext());
            dropletPaint.setNightMode(isNight);
            backdropBlur.prepareBackdrop(this);
            dropletPaint.drawGlassDock(canvas, dockBounds, dockCorner, density, backdropBlur, isNight ? 0.32f : 0.54f);

            // 2. Gliding 3D water-droplet active tab capsule with metaball stretch & damped wobble
            computeDropletRect();
            if (dropletRect.width() > 2f && dropletRect.height() > 2f) {
                dropletPaint.drawWaterDroplet(
                        canvas,
                        dropletRect,
                        dropletRect.height() * 0.5f,
                        density,
                        LiquidDropletPaint.VARIANT_EMERALD,
                        backdropBlur,
                        getWidth(),
                        getHeight(),
                        true
                );
            }

            // 3. Expanding surface water ripple centered on the tapped destination
            if (rippleProgress > 0f && rippleProgress < 1f) {
                dropletPaint.drawWaterRipple(
                        canvas,
                        rippleCx,
                        rippleCy,
                        getHeight() * 0.85f,
                        rippleProgress,
                        density,
                        true
                );
            }
        }
        // 4. Crisp icons and Vietnamese labels rendered cleanly above the translucent water droplet
        super.dispatchDraw(canvas);
    }

    private void computeDropletRect() {
        if (tabs.isEmpty()) {
            dropletRect.setEmpty();
            return;
        }
        View fromTab = tabs.get(Math.max(0, Math.min(animFromIndex, tabs.size() - 1))).container;
        View toTab = tabs.get(Math.max(0, Math.min(animToIndex, tabs.size() - 1))).container;
        if (fromTab.getWidth() <= 0 || toTab.getWidth() <= 0) {
            dropletRect.setEmpty();
            return;
        }

        float offX = rowContainer.getLeft();
        float offY = rowContainer.getTop();
        float fromLeft = offX + fromTab.getLeft();
        float fromRight = offX + fromTab.getRight();
        float toLeft = offX + toTab.getLeft();
        float toRight = offX + toTab.getRight();

        float t = Math.max(0f, Math.min(1f, animProgress));
        // Asymmetric surface-tension edges: leading edge surges smoothly, trailing edge lags behind
        float leadT = smoothOutCubic(t);
        float trailT = smoothInOutDelayed(t);

        boolean movingRight = toLeft >= fromLeft;
        float left;
        float right;
        if (movingRight) {
            right = lerp(fromRight, toRight, leadT);
            left = lerp(fromLeft, toLeft, trailT);
        } else {
            left = lerp(fromLeft, toLeft, leadT);
            right = lerp(fromRight, toRight, trailT);
        }

        // Damped harmonic water-droplet jelly oscillation on landing
        float wobble = 0f;
        if (t < 1f) {
            float settlePhase = Math.max(0f, (t - 0.58f) / 0.42f);
            float flightStretch = (float) Math.sin(t * Math.PI) * 3.0f * density;
            float arrivalBounce = settlePhase > 0f
                    ? (float) (Math.sin(settlePhase * Math.PI * 2.4) * Math.exp(-settlePhase * 3.8) * 3.2f * density)
                    : 0f;
            wobble = flightStretch - arrivalBounce;
        }

        float padY = 0.5f * density;
        float top = offY + toTab.getTop() + padY + wobble * 0.45f;
        float bottom = offY + toTab.getBottom() - padY - wobble * 0.45f;
        float horizontalSquish = (1f - touchScale) * 14f * density - wobble * 0.35f;

        dropletRect.set(
                left + 1.5f * density + horizontalSquish,
                Math.max(offY, top),
                right - 1.5f * density - horizontalSquish,
                Math.min(offY + rowContainer.getHeight(), bottom)
        );
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
