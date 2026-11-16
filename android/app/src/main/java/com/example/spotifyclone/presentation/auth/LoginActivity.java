package com.example.spotifyclone.presentation.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.spotifyclone.R;
import com.example.spotifyclone.data.local.SessionManager;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.AuthResponse;
import com.example.spotifyclone.data.model.GoogleLoginRequest;
import com.example.spotifyclone.data.model.LoginRequest;
import com.example.spotifyclone.data.model.User;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ActivityLoginBinding;
import com.example.spotifyclone.presentation.main.MainActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private SessionManager sessionManager;

    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiClient.init(this);

        sessionManager = SessionManager.getInstance(this);
        // Neu da co phien dang nhap hop le, tu dong vao trang chu
        if (sessionManager.isLoggedIn()) {
            navigateToHome();
            return;
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        com.example.spotifyclone.util.ThemeManager.applySystemBarsAppearance(this);

        // Bù trừ an toàn thanh trạng thái trên đỉnh và thanh điều hướng ở đáy
        com.example.spotifyclone.util.WindowInsetHelper.applySystemBarInsets(binding.getRoot(), true, true);

        initGoogleSignIn();
        setupActions();
    }

    private void initGoogleSignIn() {
        googleSignInClient = GoogleSignInHelper.createClient(this);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            handleGoogleSuccess(account);
                        } else {
                            Toast.makeText(this, "Không nhận được thông tin tài khoản Google", Toast.LENGTH_SHORT).show();
                        }
                    } catch (ApiException e) {
                        GoogleSignInHelper.showUnavailableDialog(this, "Mã lỗi Play Services: " + e.getStatusCode());
                    }
                }
        );
    }

    private void setupActions() {
        // Chuyen sang man hinh Dang ky
        binding.tvGoToRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // Dang nhap Google (yêu cầu Google ID Token RS256 hợp lệ)
        binding.btnContinueGoogle.setOnClickListener(v -> {
            if (!GoogleSignInHelper.isConfigured(this)) {
                GoogleSignInHelper.showUnavailableDialog(this, "Chưa cấu hình google_web_client_id");
                return;
            }
            try {
                googleSignInClient.signOut();
                Intent signInIntent = googleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            } catch (Exception e) {
                GoogleSignInHelper.showUnavailableDialog(this, e.getMessage());
            }
        });

        // Quen mat khau
        binding.tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());

        // Hien thi va cau hinh Server URL
        updateServerStatusText();
        binding.btnServerConfig.setOnClickListener(v -> showServerConfigDialog());
        binding.tvServerUrlStatus.setOnClickListener(v -> showServerConfigDialog());

        // Xu ly Dang nhap
        binding.btnLogin.setOnClickListener(v -> {
            hideKeyboard();
            performLogin();
        });
    }

    private void updateServerStatusText() {
        if (binding == null) return;
        String currentUrl = ApiClient.getBaseUrl();
        binding.tvServerUrlStatus.setText("Máy chủ: " + currentUrl + " (Nhấn để đổi)");
    }

    private void performLogin() {
        String identifier = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(identifier)) {
            binding.tilEmail.setError("Vui lòng nhập email, số điện thoại hoặc tên người dùng");
            return;
        } else {
            binding.tilEmail.setError(null);
        }

        if (TextUtils.isEmpty(password)) {
            binding.tilPassword.setError("Vui lòng nhập mật khẩu");
            return;
        } else {
            binding.tilPassword.setError(null);
        }

        setLoading(true);

        LoginRequest request = new LoginRequest(identifier, password);
        ApiClient.getApiService().login(request).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<AuthResponse>> call, @NonNull Response<ApiResponse<AuthResponse>> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    AuthResponse authData = response.body().getData();
                    String token = authData.getToken();
                    User user = authData.getUser();

                    sessionManager.saveAuth(token, user);

                    String welcomeName = user != null ? user.getDisplayName() : identifier;
                    Toast.makeText(LoginActivity.this, "Đăng nhập thành công! Chào mừng " + welcomeName, Toast.LENGTH_SHORT).show();

                    navigateToHome();
                } else {
                    String errorMsg = extractErrorMessage(response);
                    Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<AuthResponse>> call, @NonNull Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleGoogleSuccess(GoogleSignInAccount account) {
        String idToken = GoogleSignInHelper.extractVerifiedIdToken(account);
        if (TextUtils.isEmpty(idToken)) {
            GoogleSignInHelper.showUnavailableDialog(this, "Không nhận được Google ID Token từ Google Play Services");
            return;
        }
        String displayName = account.getDisplayName() != null ? account.getDisplayName() : "Bạn";
        callGoogleLoginApi(idToken, displayName);
    }

    private void callGoogleLoginApi(String idToken, String displayName) {
        setLoading(true);

        GoogleLoginRequest request = new GoogleLoginRequest(idToken);
        ApiClient.getApiService().loginWithGoogle(request).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<AuthResponse>> call, @NonNull Response<ApiResponse<AuthResponse>> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    AuthResponse authData = response.body().getData();
                    sessionManager.saveAuth(authData.getToken(), authData.getUser());
                    String welcomeName = (authData.getUser() != null && !TextUtils.isEmpty(authData.getUser().getDisplayName()))
                            ? authData.getUser().getDisplayName()
                            : displayName;
                    Toast.makeText(LoginActivity.this, "Đăng nhập Google thành công! Chào mừng " + welcomeName, Toast.LENGTH_SHORT).show();
                    navigateToHome();
                } else {
                    String errorMsg = extractErrorMessage(response);
                    Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<AuthResponse>> call, @NonNull Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        if (binding == null) return;
        binding.btnLogin.setEnabled(!isLoading);
        binding.btnLogin.setText(isLoading ? "Đang đăng nhập..." : "Đăng nhập");
    }

    private void showForgotPasswordDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Khôi phục mật khẩu")
                .setMessage("Hệ thống chưa hỗ trợ tự đặt lại mật khẩu qua Email. Bạn có thể đăng nhập bằng Google nếu đã liên kết tài khoản, hoặc liên hệ quản trị viên để được cấp lại mật khẩu.")
                .setPositiveButton("Đã hiểu", null)
                .show();
    }

    private String extractErrorMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String errorBodyStr = response.errorBody().string();
                JSONObject json = new JSONObject(errorBodyStr);
                if (json.has("message")) {
                    String msg = json.getString("message");
                    if ("Invalid login credentials".equalsIgnoreCase(msg)) {
                        return "Email, số điện thoại hoặc mật khẩu không chính xác";
                    }
                    if ("Your account has been deactivated. Please contact support.".equalsIgnoreCase(msg)) {
                        return "Tài khoản của bạn đã bị khóa";
                    }
                    return msg;
                }
            }
        } catch (Exception ignored) {}
        return "Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.";
    }

    private void hideKeyboard() {
        try {
            View view = getCurrentFocus();
            if (view != null) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            }
        } catch (Exception ignored) {}
    }

    private void navigateToHome() {
        hideKeyboard();
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showServerConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cấu hình kết nối Máy chủ");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);

        TextView infoText = new TextView(this);
        infoText.setText("Địa chỉ Server Backend Node.js hiện tại:\n(Ví dụ trên mạng Wi-Fi: http://192.168.1.9:5000/)");
        infoText.setTextColor(getResources().getColor(R.color.text_secondary, null));
        infoText.setTextSize(13);
        layout.addView(infoText);

        final EditText etUrl = new EditText(this);
        etUrl.setText(ApiClient.getBaseUrl());
        etUrl.setHint("http://192.168.1.9:5000/");
        etUrl.setTextColor(getResources().getColor(R.color.text_primary, null));
        etUrl.setHintTextColor(getResources().getColor(R.color.text_secondary, null));
        etUrl.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(etUrl);

        final TextView tvPingResult = new TextView(this);
        tvPingResult.setPadding(0, 16, 0, 8);
        tvPingResult.setTextSize(13);
        layout.addView(tvPingResult);

        com.google.android.material.button.MaterialButton btnPing = new com.google.android.material.button.MaterialButton(this);
        btnPing.setText("Kiểm tra kết nối tới Server");
        btnPing.setBackgroundColor(getResources().getColor(R.color.spotify_surface, null));
        btnPing.setTextColor(getResources().getColor(R.color.spotify_green, null));
        btnPing.setOnClickListener(v -> {
            String testUrl = etUrl.getText().toString().trim();
            if (!testUrl.startsWith("http://") && !testUrl.startsWith("https://")) {
                testUrl = "http://" + testUrl;
            }
            if (!testUrl.endsWith("/")) {
                testUrl = testUrl + "/";
            }
            tvPingResult.setTextColor(getResources().getColor(R.color.spotify_green, null));
            tvPingResult.setText("Đang kiểm tra kết nối tới " + testUrl + "...");

            final String finalTestUrl = testUrl;
            okhttp3.OkHttpClient testClient = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            okhttp3.Request pingRequest = new okhttp3.Request.Builder()
                    .url(finalTestUrl + "api/v1/health")
                    .build();

            testClient.newCall(pingRequest).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                    runOnUiThread(() -> {
                        tvPingResult.setTextColor(android.graphics.Color.RED);
                        tvPingResult.setText("❌ Không kết nối được: " + e.getMessage() + "\nHãy kiểm tra lại Wi-Fi và IP máy tính!");
                    });
                }

                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            tvPingResult.setTextColor(getResources().getColor(R.color.spotify_green, null));
                            tvPingResult.setText("✅ Kết nối máy chủ thành công (200 OK)!");
                        } else {
                            tvPingResult.setTextColor(android.graphics.Color.YELLOW);
                            tvPingResult.setText("⚠️ Máy chủ phản hồi mã: " + response.code());
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
                updateServerStatusText();
                Toast.makeText(this, "Đã cập nhật Server URL: " + newUrl, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNeutralButton("Mặc định", (dialog, which) -> {
            sessionManager.setCustomServerUrl(null);
            ApiClient.resetApiService();
            updateServerStatusText();
            Toast.makeText(this, "Đã khôi phục địa chỉ Server tự động!", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}
