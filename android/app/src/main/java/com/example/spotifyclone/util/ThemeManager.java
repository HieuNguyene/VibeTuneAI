package com.example.spotifyclone.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.spotifyclone.R;

/**
 * Manages Light / Dark / System theme mode independently of user login sessions
 * so theme preferences persist across logouts and restarts.
 */
public final class ThemeManager {

    public static final String MODE_DARK = "dark";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_SYSTEM = "system";

    private static final String PREF_NAME = "vibetune_theme_prefs";
    private static final String LEGACY_PREF_NAME = "spotify_theme_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";

    private ThemeManager() {
    }

    private static SharedPreferences getPrefs(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_THEME_MODE)) {
            SharedPreferences legacy = appContext.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE);
            if (legacy.contains(KEY_THEME_MODE)) {
                String val = legacy.getString(KEY_THEME_MODE, MODE_DARK);
                prefs.edit().putString(KEY_THEME_MODE, val).apply();
            }
        }
        return prefs;
    }

    @NonNull
    public static String getSavedMode(@NonNull Context context) {
        SharedPreferences prefs = getPrefs(context);
        String mode = prefs.getString(KEY_THEME_MODE, MODE_DARK);
        if (MODE_LIGHT.equals(mode) || MODE_SYSTEM.equals(mode)) {
            return mode;
        }
        return MODE_DARK;
    }

    public static void applySavedTheme(@NonNull Context context) {
        AppCompatDelegate.setDefaultNightMode(toNightModeInt(getSavedMode(context)));
    }

    public static void setThemeMode(@NonNull Context context, @NonNull String mode) {
        String normalized = normalizeMode(mode);
        getPrefs(context)
                .edit()
                .putString(KEY_THEME_MODE, normalized)
                .apply();
        int targetNightMode = toNightModeInt(normalized);
        if (AppCompatDelegate.getDefaultNightMode() != targetNightMode) {
            AppCompatDelegate.setDefaultNightMode(targetNightMode);
        }
    }

    public static int toNightModeInt(@NonNull String mode) {
        switch (normalizeMode(mode)) {
            case MODE_LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case MODE_SYSTEM:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            case MODE_DARK:
            default:
                return AppCompatDelegate.MODE_NIGHT_YES;
        }
    }

    @NonNull
    public static String getModeLabel(@NonNull String mode) {
        switch (normalizeMode(mode)) {
            case MODE_LIGHT:
                return "Sáng";
            case MODE_SYSTEM:
                return "Theo hệ thống";
            case MODE_DARK:
            default:
                return "Tối";
        }
    }

    public static boolean isNightMode(@NonNull Context context) {
        int nightFlags = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (nightFlags == Configuration.UI_MODE_NIGHT_YES) {
            return true;
        }
        if (nightFlags == Configuration.UI_MODE_NIGHT_NO) {
            return false;
        }
        return AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES;
    }

    public static void applySystemBarsAppearance(@NonNull Activity activity) {
        Window window = activity.getWindow();
        if (window == null) return;
        boolean lightIcons = !isNightMode(activity);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(lightIcons);
            controller.setAppearanceLightNavigationBars(lightIcons);
        }
    }

    /**
     * Binds a shared 3-pill Light/Dark/System selector so ProfileBottomSheetDialog and
     * ProfileActivity share identical state, styling, and persistence logic.
     */
    public static void bindModeSelector(@NonNull Context context,
                                        @NonNull TextView btnLight,
                                        @NonNull TextView btnDark,
                                        @NonNull TextView btnSystem,
                                        @Nullable TextView tvStatusLabel,
                                        @Nullable Runnable beforeApply) {
        updateModeSelectorVisuals(context, btnLight, btnDark, btnSystem, tvStatusLabel, getSavedMode(context));

        View.OnClickListener clickListener = v -> {
            String targetMode = MODE_DARK;
            if (v == btnLight) {
                targetMode = MODE_LIGHT;
            } else if (v == btnSystem) {
                targetMode = MODE_SYSTEM;
            }
            if (targetMode.equals(getSavedMode(context))) {
                return;
            }
            updateModeSelectorVisuals(context, btnLight, btnDark, btnSystem, tvStatusLabel, targetMode);
            if (beforeApply != null) {
                beforeApply.run();
            }
            setThemeMode(context, targetMode);
        };
        btnLight.setOnClickListener(clickListener);
        btnDark.setOnClickListener(clickListener);
        btnSystem.setOnClickListener(clickListener);
    }

    private static void updateModeSelectorVisuals(@NonNull Context context,
                                                  @NonNull TextView btnLight,
                                                  @NonNull TextView btnDark,
                                                  @NonNull TextView btnSystem,
                                                  @Nullable TextView tvStatusLabel,
                                                  @NonNull String activeMode) {
        styleModePill(context, btnLight, MODE_LIGHT.equals(activeMode));
        styleModePill(context, btnDark, MODE_DARK.equals(activeMode));
        styleModePill(context, btnSystem, MODE_SYSTEM.equals(activeMode));
        if (tvStatusLabel != null) {
            tvStatusLabel.setText(getModeLabel(activeMode));
        }
    }

    private static void styleModePill(@NonNull Context context, @NonNull TextView pill, boolean active) {
        pill.setBackgroundResource(active ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
        pill.setTextColor(ContextCompat.getColor(
                context, active ? R.color.on_primary_accent : R.color.text_primary));
        pill.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    @NonNull
    private static String normalizeMode(String mode) {
        if (MODE_LIGHT.equals(mode)) return MODE_LIGHT;
        if (MODE_SYSTEM.equals(mode)) return MODE_SYSTEM;
        return MODE_DARK;
    }
}
