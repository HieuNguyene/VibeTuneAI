package com.example.spotifyclone.util;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Tiện ích chuẩn hóa xử lý Window Insets (Edge-to-Edge) cho toàn bộ ứng dụng Spotify Clone.
 * Đảm bảo các thành phần UI (Toolbar, Header, Navigation) không bao giờ bị tràn lên Status Bar,
 * bị che khuất bởi nốt ruồi camera (Display Cutout) hay thanh cử chỉ đáy (Navigation Bar).
 */
public class WindowInsetHelper {

    /**
     * Tự động bù trừ padding đỉnh (Status Bar + Camera Cutout) và/hoặc đáy (Navigation Bar)
     * mà vẫn bảo lưu các giá trị padding ban đầu được định nghĩa trong XML.
     */
    public static void applySystemBarInsets(@NonNull View targetView, boolean applyTop, boolean applyBottom) {
        final int initialLeft = targetView.getPaddingLeft();
        final int initialTop = targetView.getPaddingTop();
        final int initialRight = targetView.getPaddingRight();
        final int initialBottom = targetView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(targetView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );

            int topPadding = applyTop ? (initialTop + insets.top) : initialTop;
            int bottomPadding = applyBottom ? (initialBottom + insets.bottom) : initialBottom;

            v.setPadding(initialLeft, topPadding, initialRight, bottomPadding);
            return windowInsets;
        });

        if (targetView.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(targetView);
        }
    }

    /**
     * Bù trừ padding đỉnh an toàn (Status Bar + Display Cutout) cho View
     */
    public static void applyStatusBarPadding(@NonNull View targetView) {
        applySystemBarInsets(targetView, true, false);
    }

    /**
     * Bù trừ margin đỉnh an toàn (Status Bar + Display Cutout) cho View
     */
    public static void applyStatusBarMargin(@NonNull View targetView) {
        ViewGroup.LayoutParams params = targetView.getLayoutParams();
        final int initialTopMargin = (params instanceof ViewGroup.MarginLayoutParams)
                ? ((ViewGroup.MarginLayoutParams) params).topMargin : 0;

        ViewCompat.setOnApplyWindowInsetsListener(targetView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );

            if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                marginParams.topMargin = initialTopMargin + insets.top;
                v.setLayoutParams(marginParams);
            }
            return windowInsets;
        });

        if (targetView.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(targetView);
        }
    }

    /**
     * Bù trừ thanh trạng thái cho Toolbar pinned trong CollapsingToolbarLayout:
     * Cập nhật padding top = statusBar và chiều cao = actionBarSize + statusBar,
     * giúp icon back và tiêu đề luôn nằm an toàn bên dưới thanh trạng thái.
     */
    public static void applyToolbarInsets(@NonNull androidx.appcompat.widget.Toolbar toolbar) {
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );

            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());

            android.util.TypedValue tv = new android.util.TypedValue();
            int actionBarHeight = 0;
            if (v.getContext().getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                actionBarHeight = android.util.TypedValue.complexToDimensionPixelSize(tv.data, v.getResources().getDisplayMetrics());
            }
            if (actionBarHeight <= 0) {
                actionBarHeight = (int) (56 * v.getResources().getDisplayMetrics().density);
            }

            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null) {
                lp.height = actionBarHeight + bars.top;
                v.setLayoutParams(lp);
            }

            return insets;
        });

        if (toolbar.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(toolbar);
        }
    }

    /**
     * Bù trừ toàn diện cho các màn hình sử dụng CoordinatorLayout + CollapsingToolbarLayout
     * (như PlaylistDetailActivity và ArtistDetailActivity).
     * Đăng ký trực tiếp trên rootView để đảm bảo 100% nhận WindowInsets, sau đó:
     * 1. Điều chỉnh Toolbar (padding top = bars.top, height = actionBarSize + bars.top)
     * 2. Mở rộng AppBarLayout thêm bars.top
     * 3. Đẩy view đỉnh header (cover art hoặc avatar) xuống dưới thanh trạng thái
     * 4. Bù trừ padding đáy cho scrollContent để không bị thanh điều hướng che khuất.
     */
    public static void applyCollapsingToolbarInsets(
            @NonNull View rootView,
            @NonNull com.google.android.material.appbar.AppBarLayout appBarLayout,
            @NonNull androidx.appcompat.widget.Toolbar toolbar,
            @androidx.annotation.Nullable View headerTopView,
            @androidx.annotation.Nullable View scrollContent
    ) {
        final int baseAppBarHeight = appBarLayout.getLayoutParams() != null ? appBarLayout.getLayoutParams().height : 0;
        final int baseHeaderTopMargin = (headerTopView != null && headerTopView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)
                ? ((ViewGroup.MarginLayoutParams) headerTopView.getLayoutParams()).topMargin : 0;

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );

            // 1. Toolbar: add bars.top to height & paddingTop
            int actionBarHeight = 0;
            android.util.TypedValue tv = new android.util.TypedValue();
            if (v.getContext().getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                actionBarHeight = android.util.TypedValue.complexToDimensionPixelSize(tv.data, v.getResources().getDisplayMetrics());
            }
            if (actionBarHeight <= 0) {
                actionBarHeight = (int) (56 * v.getResources().getDisplayMetrics().density);
            }

            ViewGroup.LayoutParams toolbarLp = toolbar.getLayoutParams();
            if (toolbarLp != null) {
                toolbarLp.height = actionBarHeight + bars.top;
                toolbar.setLayoutParams(toolbarLp);
            }
            toolbar.setPadding(toolbar.getPaddingLeft(), bars.top, toolbar.getPaddingRight(), toolbar.getPaddingBottom());

            // 2. AppBarLayout height
            if (baseAppBarHeight > 0) {
                ViewGroup.LayoutParams appBarLp = appBarLayout.getLayoutParams();
                if (appBarLp != null) {
                    appBarLp.height = baseAppBarHeight + bars.top;
                    appBarLayout.setLayoutParams(appBarLp);
                }
            }

            // 3. Header top view margin
            if (headerTopView != null && headerTopView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginLp = (ViewGroup.MarginLayoutParams) headerTopView.getLayoutParams();
                marginLp.topMargin = baseHeaderTopMargin + bars.top;
                headerTopView.setLayoutParams(marginLp);
            }

            // 4. Scroll content bottom padding for navigation bar
            if (scrollContent != null) {
                scrollContent.setPadding(
                        scrollContent.getPaddingLeft(),
                        scrollContent.getPaddingTop(),
                        scrollContent.getPaddingRight(),
                        bars.bottom
                );
                if (scrollContent instanceof ViewGroup) {
                    ((ViewGroup) scrollContent).setClipToPadding(false);
                }
            }

            return windowInsets;
        });

        if (rootView.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(rootView);
        }
    }
}
