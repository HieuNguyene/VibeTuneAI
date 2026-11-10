package com.example.spotifyclone.presentation.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
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

        // Bù trừ an toàn thanh trạng thái trên đỉnh và thanh điều hướng ở đáy
        com.example.spotifyclone.util.WindowInsetHelper.applySystemBarInsets(binding.getRoot(), true, true);

        initGoogleSignIn();
        setupActions();
    }

    private void initGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

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
                        // Trình giả lập chưa đăng nhập Google Play hoặc chưa cấu hình OAuth GCP -> mở hộp thoại đăng nhập giả lập thông minh
                        showGoogleFallbackDialog();
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

        // Dang nhap Google
        binding.btnContinueGoogle.setOnClickListener(v -> {
            try {
                googleSignInClient.signOut();
                Intent signInIntent = googleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            } catch (Exception e) {
                showGoogleFallbackDialog();
            }
        });

        // Quen mat khau
        binding.tvForgotPassword.setOnClickListener(v -> {
            Toast.makeText(this, "Vui lòng liên hệ quản trị viên để đặt lại mật khẩu.", Toast.LENGTH_LONG).show();
        });

        // Xu ly Dang nhap
        binding.btnLogin.setOnClickListener(v -> performLogin());
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
        String googleId = account.getId() != null ? account.getId() : "google_" + System.currentTimeMillis();
        String email = account.getEmail();
        String displayName = account.getDisplayName() != null ? account.getDisplayName() : "Google User";
        String avatarUrl = account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : "";

        callGoogleLoginApi(googleId, email, displayName, avatarUrl);
    }

    private void showGoogleFallbackDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Đăng nhập với Google");
        builder.setMessage("Nhập email Google của bạn để đăng nhập nhanh:");

        final EditText inputEmail = new EditText(this);
        inputEmail.setHint("example@gmail.com");
        inputEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        inputEmail.setTextColor(getResources().getColor(R.color.white));
        inputEmail.setHintTextColor(getResources().getColor(R.color.text_secondary));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, padding / 2);
        container.addView(inputEmail);

        builder.setView(container);
        builder.setPositiveButton("Đăng nhập", (dialog, which) -> {
            String email = inputEmail.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                email = "user" + (System.currentTimeMillis() % 10000) + "@gmail.com";
            }
            String googleId = "gid_" + Math.abs(email.hashCode());
            String displayName = email.contains("@") ? email.split("@")[0] : "Google User";
            callGoogleLoginApi(googleId, email, displayName, "");
        });
        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void callGoogleLoginApi(String googleId, String email, String displayName, String avatarUrl) {
        setLoading(true);

        GoogleLoginRequest request = new GoogleLoginRequest(googleId, email, displayName, avatarUrl);
        ApiClient.getApiService().loginWithGoogle(request).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<AuthResponse>> call, @NonNull Response<ApiResponse<AuthResponse>> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    AuthResponse authData = response.body().getData();
                    sessionManager.saveAuth(authData.getToken(), authData.getUser());
                    Toast.makeText(LoginActivity.this, "Đăng nhập Google thành công! Chào mừng " + displayName, Toast.LENGTH_SHORT).show();
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

    private void navigateToHome() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
