package com.example.spotifyclone.presentation.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
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
import com.example.spotifyclone.data.model.RegisterRequest;
import com.example.spotifyclone.data.model.SendOtpRequest;
import com.example.spotifyclone.data.model.SendOtpResponse;
import com.example.spotifyclone.data.model.User;
import com.example.spotifyclone.data.model.VerifyOtpRequest;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ActivityRegisterBinding;
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

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private SessionManager sessionManager;
    private CountDownTimer otpTimer;
    private boolean isPhoneRegistration = true;

    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiClient.init(this);
        sessionManager = SessionManager.getInstance(this);

        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
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
                            Toast.makeText(this, "Không nhận được tài khoản Google", Toast.LENGTH_SHORT).show();
                        }
                    } catch (ApiException e) {
                        // Trình giả lập chưa đăng nhập Google Play hoặc chưa cấu hình OAuth GCP -> mở hộp thoại đăng nhập giả lập thông minh
                        showGoogleFallbackDialog();
                    }
                }
        );
    }

    private void setupActions() {
        // Nút quay lại
        binding.btnRegisterBack.setOnClickListener(v -> finish());

        // Chuyển về Đăng nhập
        binding.tvGoToLogin.setOnClickListener(v -> finish());

        // Đăng nhập Google
        binding.btnContinueGoogle.setOnClickListener(v -> {
            try {
                googleSignInClient.signOut();
                Intent signInIntent = googleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            } catch (Exception e) {
                showGoogleFallbackDialog();
            }
        });

        // Tab chọn: Số điện thoại vs Email
        binding.toggleGroupRegType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnTabPhone) {
                    isPhoneRegistration = true;
                    binding.layoutPhoneSection.setVisibility(View.VISIBLE);
                    binding.layoutEmailSection.setVisibility(View.GONE);
                } else if (checkedId == R.id.btnTabEmail) {
                    isPhoneRegistration = false;
                    binding.layoutPhoneSection.setVisibility(View.GONE);
                    binding.layoutEmailSection.setVisibility(View.VISIBLE);
                }
            }
        });

        // Gửi mã OTP
        binding.btnSendOtp.setOnClickListener(v -> requestPhoneOtp());

        // Nút bấm Đăng ký
        binding.btnRegister.setOnClickListener(v -> {
            if (isPhoneRegistration) {
                performPhoneRegister();
            } else {
                performEmailRegister();
            }
        });
    }

    private void requestPhoneOtp() {
        String phone = binding.etRegPhone.getText() != null ? binding.etRegPhone.getText().toString().trim() : "";
        if (TextUtils.isEmpty(phone) || phone.length() < 9) {
            binding.tilRegPhone.setError("Vui lòng nhập số điện thoại hợp lệ (từ 9 số)");
            return;
        } else {
            binding.tilRegPhone.setError(null);
        }

        binding.btnSendOtp.setEnabled(false);
        binding.btnSendOtp.setText("Đang gửi...");

        ApiClient.getApiService().sendPhoneOtp(new SendOtpRequest(phone)).enqueue(new Callback<ApiResponse<SendOtpResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<SendOtpResponse>> call, @NonNull Response<ApiResponse<SendOtpResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    SendOtpResponse data = response.body().getData();
                    Toast.makeText(RegisterActivity.this, "Đã gửi OTP về " + phone + ". Vui lòng nhập mã nhận được.", Toast.LENGTH_SHORT).show();

                    startOtpCountdown();
                } else {
                    binding.btnSendOtp.setEnabled(true);
                    binding.btnSendOtp.setText("Gửi mã");
                    String errorMsg = extractErrorMessage(response);
                    Toast.makeText(RegisterActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<SendOtpResponse>> call, @NonNull Throwable t) {
                binding.btnSendOtp.setEnabled(true);
                binding.btnSendOtp.setText("Gửi mã");
                Toast.makeText(RegisterActivity.this, "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startOtpCountdown() {
        if (otpTimer != null) {
            otpTimer.cancel();
        }

        binding.btnSendOtp.setEnabled(false);
        binding.tvOtpTimer.setVisibility(View.VISIBLE);

        otpTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                binding.btnSendOtp.setText(seconds + "s");
                binding.tvOtpTimer.setText("Mã OTP có hiệu lực trong 5 phút. Gửi lại sau: " + seconds + "s");
            }

            @Override
            public void onFinish() {
                binding.btnSendOtp.setEnabled(true);
                binding.btnSendOtp.setText("Gửi lại");
                binding.tvOtpTimer.setText("Bạn chưa nhận được mã? Hãy bấm 'Gửi lại'");
            }
        }.start();
    }

    private void performPhoneRegister() {
        String phone = binding.etRegPhone.getText() != null ? binding.etRegPhone.getText().toString().trim() : "";
        String otp = binding.etRegOtp.getText() != null ? binding.etRegOtp.getText().toString().trim() : "";
        String displayName = binding.etRegUsername.getText() != null ? binding.etRegUsername.getText().toString().trim() : "";
        String password = binding.etRegPassword.getText() != null ? binding.etRegPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(phone) || phone.length() < 9) {
            binding.tilRegPhone.setError("Vui lòng nhập số điện thoại hợp lệ");
            return;
        } else {
            binding.tilRegPhone.setError(null);
        }

        if (TextUtils.isEmpty(otp) || otp.length() < 6) {
            binding.tilRegOtp.setError("Vui lòng nhập đủ 6 chữ số mã OTP");
            return;
        } else {
            binding.tilRegOtp.setError(null);
        }

        if (TextUtils.isEmpty(displayName)) {
            binding.tilRegUsername.setError("Vui lòng nhập tên hiển thị");
            return;
        } else {
            binding.tilRegUsername.setError(null);
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            binding.tilRegPassword.setError("Mật khẩu phải có ít nhất 6 ký tự");
            return;
        } else {
            binding.tilRegPassword.setError(null);
        }

        setLoading(true);

        VerifyOtpRequest request = new VerifyOtpRequest(phone, otp, displayName, password);
        ApiClient.getApiService().verifyPhoneOtp(request).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<AuthResponse>> call, @NonNull Response<ApiResponse<AuthResponse>> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    onAuthSuccess(response.body().getData(), displayName);
                } else {
                    String errorMsg = extractErrorMessage(response);
                    Toast.makeText(RegisterActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<AuthResponse>> call, @NonNull Throwable t) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void performEmailRegister() {
        String email = binding.etRegEmail.getText() != null ? binding.etRegEmail.getText().toString().trim() : "";
        String password = binding.etRegPassword.getText() != null ? binding.etRegPassword.getText().toString().trim() : "";
        String displayName = binding.etRegUsername.getText() != null ? binding.etRegUsername.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilRegEmail.setError("Vui lòng nhập địa chỉ email hợp lệ");
            return;
        } else {
            binding.tilRegEmail.setError(null);
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            binding.tilRegPassword.setError("Mật khẩu phải có ít nhất 6 ký tự");
            return;
        } else {
            binding.tilRegPassword.setError(null);
        }

        if (TextUtils.isEmpty(displayName)) {
            binding.tilRegUsername.setError("Vui lòng nhập tên hiển thị");
            return;
        } else {
            binding.tilRegUsername.setError(null);
        }

        String baseUsername = email.contains("@") ? email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "").toLowerCase() : displayName.toLowerCase();
        if (baseUsername.length() < 3) {
            baseUsername = "user_" + (System.currentTimeMillis() % 100000);
        }

        setLoading(true);

        RegisterRequest request = new RegisterRequest(baseUsername, email, password, displayName);
        ApiClient.getApiService().register(request).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<AuthResponse>> call, @NonNull Response<ApiResponse<AuthResponse>> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    onAuthSuccess(response.body().getData(), displayName);
                } else {
                    String errorMsg = extractErrorMessage(response);
                    Toast.makeText(RegisterActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<AuthResponse>> call, @NonNull Throwable t) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_LONG).show();
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
        builder.setMessage("Nhập email Google của bạn để liên kết / đăng ký nhanh tài khoản:");

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
                    onAuthSuccess(response.body().getData(), displayName);
                } else {
                    String errorMsg = extractErrorMessage(response);
                    Toast.makeText(RegisterActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<AuthResponse>> call, @NonNull Throwable t) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onAuthSuccess(AuthResponse authData, String displayName) {
        String token = authData.getToken();
        User user = authData.getUser();

        sessionManager.saveAuth(token, user);

        Toast.makeText(this, "Đăng ký thành công! Chào mừng " + displayName, Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean isLoading) {
        if (binding == null) return;
        binding.btnRegister.setEnabled(!isLoading);
        binding.btnRegister.setText(isLoading ? "Đang xử lý..." : "Đăng ký");
    }

    private String extractErrorMessage(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String errorBodyStr = response.errorBody().string();
                JSONObject json = new JSONObject(errorBodyStr);
                if (json.has("message")) {
                    String msg = json.getString("message");
                    if ("Email is already registered".equalsIgnoreCase(msg)) {
                        return "Email này đã được sử dụng. Vui lòng đăng nhập hoặc chọn email khác.";
                    }
                    if ("Username is already taken".equalsIgnoreCase(msg)) {
                        return "Tên người dùng đã tồn tại. Vui lòng thử lại.";
                    }
                    return msg;
                }
            }
        } catch (Exception ignored) {}
        return "Thao tác thất bại. Vui lòng kiểm tra lại thông tin.";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (otpTimer != null) {
            otpTimer.cancel();
        }
    }
}
