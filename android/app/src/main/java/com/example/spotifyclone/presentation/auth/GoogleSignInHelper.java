package com.example.spotifyclone.presentation.auth;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.spotifyclone.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

/**
 * Helper dùng chung cho xác thực Google ID Token chuẩn OAuth 2.0 (không cho phép giả lập không xác thực).
 */
public final class GoogleSignInHelper {
    private GoogleSignInHelper() {}

    public static String getWebClientId(@NonNull Context context) {
        String raw = context.getString(R.string.google_web_client_id);
        return raw != null ? raw.trim() : "";
    }

    public static boolean isConfigured(@NonNull Context context) {
        return !TextUtils.isEmpty(getWebClientId(context));
    }

    public static GoogleSignInClient createClient(@NonNull Context context) {
        GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile();
        String webClientId = getWebClientId(context);
        if (!TextUtils.isEmpty(webClientId)) {
            builder.requestIdToken(webClientId);
        }
        return GoogleSignIn.getClient(context, builder.build());
    }

    @Nullable
    public static String extractVerifiedIdToken(@Nullable GoogleSignInAccount account) {
        if (account == null) return null;
        String token = account.getIdToken();
        return token != null && !token.trim().isEmpty() ? token.trim() : null;
    }

    public static void showUnavailableDialog(@NonNull Context context, @Nullable String detailReason) {
        String baseMsg = "Đăng nhập Google yêu cầu Google ID Token được Google OAuth 2.0 ký xác thực (RS256).\n\n"
                + "Thiết bị hiện chưa lấy được Google ID Token hợp lệ. Vui lòng kiểm tra kết nối Play Services / cấu hình OAuth hoặc sử dụng Email / Số điện thoại OTP để đăng nhập.";
        String fullMsg = TextUtils.isEmpty(detailReason) ? baseMsg : baseMsg + "\n\nChi tiết: " + detailReason;

        new AlertDialog.Builder(context)
                .setTitle("Không thể xác thực Google")
                .setMessage(fullMsg)
                .setPositiveButton("Đã hiểu", (d, which) -> d.dismiss())
                .show();
    }
}
