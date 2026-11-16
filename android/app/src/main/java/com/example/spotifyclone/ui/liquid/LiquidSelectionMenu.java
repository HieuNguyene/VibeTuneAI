package com.example.spotifyclone.ui.liquid;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.spotifyclone.R;

import java.util.List;

/**
 * 3D Liquid-Glass selection dropdown menu.
 * Replaces wide horizontal chip rows with a single sleek selection button
 * that displays the currently active option and opens an anchored popup menu.
 */
public final class LiquidSelectionMenu {

    public static class Item {
        @NonNull
        public final String id;
        @NonNull
        public final String title;

        public Item(@NonNull String id, @NonNull String title) {
            this.id = id;
            this.title = title;
        }
    }

    public interface OnItemSelectedListener {
        void onItemSelected(@NonNull Item item);
    }

    private LiquidSelectionMenu() {}

    /**
     * Wires an interactive selection button with 3D spring, label updating, and dropdown popup.
     */
    public static void setupSelectionButton(
            @NonNull View button,
            @NonNull TextView labelView,
            @Nullable ImageView chevronView,
            @NonNull String prefix,
            @NonNull List<Item> items,
            @NonNull String initialSelectedId,
            @NonNull OnItemSelectedListener listener
    ) {
        final String[] selectedIdHolder = new String[]{initialSelectedId};

        boolean isNight = com.example.spotifyclone.util.ThemeManager.isNightMode(button.getContext());
        int btnTextColor = isNight ? 0xFFFFFFFF : 0xFF0F172A;
        labelView.setTextColor(btnTextColor);
        if (chevronView != null) {
            chevronView.setColorFilter(btnTextColor);
        }

        // Set initial label
        for (Item item : items) {
            if (item.id.equals(initialSelectedId)) {
                labelView.setText(prefix + item.title);
                break;
            }
        }

        LiquidDropletHelper.attachDropletSpring(button);

        button.setOnClickListener(v -> {
            boolean isNightCur = com.example.spotifyclone.util.ThemeManager.isNightMode(button.getContext());
            int curTextColor = isNightCur ? 0xFFFFFFFF : 0xFF0F172A;
            labelView.setTextColor(curTextColor);
            if (chevronView != null) {
                chevronView.setColorFilter(curTextColor);
            }
            show(
                    button.getContext(),
                    button,
                    chevronView,
                    items,
                    selectedIdHolder[0],
                    item -> {
                        selectedIdHolder[0] = item.id;
                        labelView.setText(prefix + item.title);
                        listener.onItemSelected(item);
                    }
            );
        });
    }

    /**
     * Displays a floating 3D liquid-glass dropdown menu anchored to anchorView.
     */
    public static void show(
            @NonNull Context context,
            @NonNull View anchorView,
            @Nullable ImageView chevronView,
            @NonNull List<Item> items,
            @NonNull String selectedId,
            @NonNull OnItemSelectedListener listener
    ) {
        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_dropdown_panel_3d);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        int padH = Math.round(8 * density);
        int padV = Math.round(10 * density);
        root.setPadding(padH, padV, padH, padV);

        final PopupWindow popup = new PopupWindow(
                root,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(16f * density);
        popup.setAnimationStyle(android.R.style.Animation_Dialog);

        boolean isNight = com.example.spotifyclone.util.ThemeManager.isNightMode(context);
        int textColorSelected = isNight ? 0xFF38BDF8 : 0xFF0369A1;
        int textColorNormal = isNight ? 0xDFE6EAE4 : 0xFF0F172A;
        int activeBgColor = isNight ? 0x2E38BDF8 : 0x240EA5E9;
        int activeStrokeColor = isNight ? 0x8038BDF8 : 0x990284C7;
        int checkTintColor = isNight ? 0xFF38BDF8 : 0xFF0284C7;
        int dotInactiveColor = isNight ? 0x66FFFFFF : 0xFF64748B;

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            boolean isSelected = item.id.equals(selectedId);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClickable(true);
            row.setFocusable(true);

            int rowPadH = Math.round(14 * density);
            int rowPadV = Math.round(10 * density);
            row.setPadding(rowPadH, rowPadV, rowPadH, rowPadV);

            // Subtle rounded highlight for active row
            if (isSelected) {
                GradientDrawable activeBg = new GradientDrawable();
                activeBg.setColor(activeBgColor);
                activeBg.setCornerRadius(14f * density);
                activeBg.setStroke(Math.round(1f * density), activeStrokeColor);
                row.setBackground(activeBg);
            } else {
                GradientDrawable normalBg = new GradientDrawable();
                normalBg.setColor(Color.TRANSPARENT);
                normalBg.setCornerRadius(14f * density);
                row.setBackground(normalBg);
            }

            // Dot indicator
            View dot = new View(context);
            int dotSize = Math.round(6 * density);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotLp.setMarginEnd(Math.round(10 * density));
            dot.setLayoutParams(dotLp);
            GradientDrawable dotDrawable = new GradientDrawable();
            dotDrawable.setShape(GradientDrawable.OVAL);
            dotDrawable.setColor(isSelected ? (isNight ? 0xFF38BDF8 : 0xFF0284C7) : dotInactiveColor);
            dot.setBackground(dotDrawable);

            TextView tv = new TextView(context);
            tv.setText(item.title);
            tv.setTextSize(13f);
            tv.setTextColor(isSelected ? textColorSelected : textColorNormal);
            tv.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(tvLp);

            row.addView(dot);
            row.addView(tv);

            if (isSelected) {
                ImageView check = new ImageView(context);
                int checkSize = Math.round(16 * density);
                LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(checkSize, checkSize);
                checkLp.setMarginStart(Math.round(12 * density));
                check.setLayoutParams(checkLp);
                check.setImageResource(R.drawable.ic_check);
                check.setColorFilter(checkTintColor);
                row.addView(check);
            }

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                rowLp.topMargin = Math.round(4 * density);
            }
            row.setLayoutParams(rowLp);

            LiquidDropletHelper.attachDropletSpring(row);

            row.setOnClickListener(v -> {
                popup.dismiss();
                listener.onItemSelected(item);
            });

            root.addView(row);
        }

        // Min width to ensure text never truncates or feels cramped
        int minWidth = Math.max(anchorView.getWidth(), Math.round(210 * density));
        root.setMinimumWidth(minWidth);

        if (chevronView != null) {
            chevronView.animate().rotation(180f).setDuration(180L).start();
            popup.setOnDismissListener(() -> {
                chevronView.animate().rotation(0f).setDuration(180L).start();
            });
        }

        popup.showAsDropDown(anchorView, 0, Math.round(4 * density));
    }
}
