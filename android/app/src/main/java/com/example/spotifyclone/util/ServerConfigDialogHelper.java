package com.example.spotifyclone.util;

import android.app.Activity;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.example.spotifyclone.R;
import com.example.spotifyclone.data.local.SessionManager;
import com.example.spotifyclone.data.remote.ApiClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ServerConfigDialogHelper {

    public interface OnServerConfigChangedListener {
        void onServerConfigChanged(String newUrl);
    }

    public static void show(Activity activity, OnServerConfigChangedListener listener) {
        if (activity == null || activity.isFinishing()) return;

        SessionManager sessionManager = SessionManager.getInstance(activity);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Cấu hình kết nối Máy chủ");

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (18 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);

        TextView infoText = new TextView(activity);
        infoText.setText("Địa chỉ Server Backend Node.js hiện tại:\n(Ví dụ: http://192.168.1.9:5000/ hoặc http://10.0.2.2:5000/)");
        infoText.setTextColor(activity.getResources().getColor(R.color.text_secondary, null));
        infoText.setTextSize(13);
        layout.addView(infoText);

        final EditText etUrl = new EditText(activity);
        etUrl.setText(ApiClient.getBaseUrl());
        etUrl.setHint("http://192.168.1.9:5000/");
        etUrl.setTextColor(activity.getResources().getColor(R.color.text_primary, null));
        etUrl.setHintTextColor(activity.getResources().getColor(R.color.text_secondary, null));
        etUrl.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(etUrl);

        final TextView tvPingResult = new TextView(activity);
        tvPingResult.setPadding(0, 16, 0, 8);
        tvPingResult.setTextSize(13);
        layout.addView(tvPingResult);

        com.google.android.material.button.MaterialButton btnPing = new com.google.android.material.button.MaterialButton(activity);
        btnPing.setText("Kiểm tra kết nối tới Server");
        btnPing.setBackgroundColor(activity.getResources().getColor(R.color.spotify_surface, null));
        btnPing.setTextColor(activity.getResources().getColor(R.color.spotify_green, null));
        btnPing.setOnClickListener(v -> {
            String testUrl = etUrl.getText().toString().trim();
            if (TextUtils.isEmpty(testUrl)) {
                tvPingResult.setTextColor(activity.getResources().getColor(android.R.color.holo_red_light, null));
                tvPingResult.setText("Vui lòng nhập địa chỉ URL máy chủ");
                return;
            }
            if (!testUrl.startsWith("http://") && !testUrl.startsWith("https://")) {
                testUrl = "http://" + testUrl;
            }
            if (!testUrl.endsWith("/")) {
                testUrl = testUrl + "/";
            }
            tvPingResult.setTextColor(activity.getResources().getColor(R.color.spotify_green, null));
            tvPingResult.setText("Đang kiểm tra kết nối tới " + testUrl + "...");

            final String finalTestUrl = testUrl;
            OkHttpClient testClient = new OkHttpClient.Builder()
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(4, TimeUnit.SECONDS)
                    .build();

            Request pingRequest = new Request.Builder()
                    .url(finalTestUrl + "api/v1/health")
                    .build();

            testClient.newCall(pingRequest).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    activity.runOnUiThread(() -> {
                        tvPingResult.setTextColor(activity.getResources().getColor(android.R.color.holo_red_light, null));
                        tvPingResult.setText("❌ Không kết nối được (" + e.getMessage() + ").\nVui lòng kiểm tra điện thoại đã kết nối CÙNG mạng Wi-Fi và máy chủ đang chạy.");
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    activity.runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            tvPingResult.setTextColor(activity.getResources().getColor(R.color.spotify_green, null));
                            tvPingResult.setText("✅ Kết nối máy chủ thành công! (HTTP " + response.code() + ")");
                        } else {
                            tvPingResult.setTextColor(activity.getResources().getColor(android.R.color.holo_orange_light, null));
                            tvPingResult.setText("⚠️ Máy chủ phản hồi mã lỗi HTTP: " + response.code());
                        }
                    });
                }
            });
        });
        layout.addView(btnPing);

        builder.setView(layout);

        builder.setPositiveButton("Lưu và áp dụng", (dialog, which) -> {
            String newUrl = etUrl.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                    newUrl = "http://" + newUrl;
                }
                if (!newUrl.endsWith("/")) {
                    newUrl = newUrl + "/";
                }
                sessionManager.setCustomServerUrl(newUrl);
                ApiClient.resetApiService();
                Toast.makeText(activity, "Đã lưu Server URL: " + newUrl, Toast.LENGTH_SHORT).show();
                if (listener != null) {
                    listener.onServerConfigChanged(newUrl);
                }
            }
        });

        builder.setNeutralButton("Mặc định", (dialog, which) -> {
            sessionManager.setCustomServerUrl(null);
            ApiClient.resetApiService();
            Toast.makeText(activity, "Đã khôi phục địa chỉ Server tự động!", Toast.LENGTH_SHORT).show();
            if (listener != null) {
                listener.onServerConfigChanged(ApiClient.getBaseUrl());
            }
        });

        builder.setNegativeButton("Hủy", null);
        builder.show();
    }
}
