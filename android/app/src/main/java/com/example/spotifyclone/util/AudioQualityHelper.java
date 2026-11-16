package com.example.spotifyclone.util;

import android.content.Context;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.spotifyclone.R;
import com.example.spotifyclone.data.local.SessionManager;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.remote.ApiClient;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Shared helper to select, display, and persist audio streaming quality preferences
 * via SessionManager and PUT /api/v1/auth/preferences.
 */
public final class AudioQualityHelper {

    public static final String[] QUALITY_KEYS = {
            "low",
            "normal",
            "high",
            "lossless"
    };

    public static final String[] QUALITY_LABELS = {
            "Tiết kiệm dữ liệu (Thấp)",
            "Bình thường (160 kbps)",
            "Cao (320 kbps)",
            "Lossless (Nguyên bản máy chủ)"
    };

    private AudioQualityHelper() {}

    public static int indexForKey(@Nullable String key) {
        if (key != null) {
            for (int i = 0; i < QUALITY_KEYS.length; i++) {
                if (QUALITY_KEYS[i].equalsIgnoreCase(key.trim())) {
                    return i;
                }
            }
        }
        return 1; // default "normal"
    }

    @NonNull
    public static String getLabelForKey(@Nullable String key) {
        return QUALITY_LABELS[indexForKey(key)];
    }

    @NonNull
    public static String getCurrentLabel(@NonNull Context context) {
        String key = SessionManager.getInstance(context).getStreamingQuality();
        return getLabelForKey(key);
    }

    public static void showQualityPickerDialog(@NonNull Context context, @Nullable TextView targetLabelView) {
        SessionManager sessionManager = SessionManager.getInstance(context);
        int currentIndex = indexForKey(sessionManager.getStreamingQuality());

        new MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(R.string.audio_quality)
                .setSingleChoiceItems(QUALITY_LABELS, currentIndex, (dialog, which) -> {
                    String selectedKey = QUALITY_KEYS[which];
                    String selectedLabel = QUALITY_LABELS[which];
                    sessionManager.saveStreamingQuality(selectedKey);
                    if (targetLabelView != null) {
                        targetLabelView.setText(selectedLabel);
                    }
                    syncToServer(context, selectedKey, selectedLabel);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private static void syncToServer(@NonNull Context context, @NonNull String key, @NonNull String label) {
        Context appContext = context.getApplicationContext();
        Map<String, Object> body = new HashMap<>();
        body.put("streamingQuality", key);

        ApiClient.getApiService().updatePreferences(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                   @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(appContext,
                            "Đã lưu: " + label + " (nhạc phát trực tiếp bản gốc máy chủ)",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(appContext,
                            "Đã lưu tạm trên thiết bị (" + label + ")",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                Toast.makeText(appContext,
                        "Đã lưu tạm trên thiết bị (" + label + ")",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}
