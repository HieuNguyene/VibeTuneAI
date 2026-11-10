package com.example.spotifyclone.presentation.auth;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.local.SessionManager;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.ChangePasswordRequest;
import com.example.spotifyclone.data.model.UpdateProfileRequest;
import com.example.spotifyclone.data.model.User;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ActivityProfileBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private SessionManager sessionManager;
    private String currentAvatarUrl = "";

    private static final String[] PRESET_AVATARS = {
            "https://api.dicebear.com/7.x/bottts/svg?seed=CyberDJ",
            "https://api.dicebear.com/7.x/avataaars/svg?seed=AcousticSoul",
            "https://api.dicebear.com/7.x/bottts/svg?seed=NeonBeat",
            "https://api.dicebear.com/7.x/avataaars/svg?seed=PopStar",
            "https://api.dicebear.com/7.x/bottts/svg?seed=RockLegend",
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&q=80",
            "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500&q=80"
    };

    private static final String[] AUDIO_QUALITIES = {
            "Tự động (Khuyên dùng)",
            "Bình thường (160 kbps)",
            "Cao (320 kbps)",
            "Rất cao / Lossless (FLAC)"
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiClient.init(this);
        sessionManager = SessionManager.getInstance(this);

        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Bù trừ an toàn thanh trạng thái trên đỉnh và thanh cử chỉ điều hướng ở đáy
        com.example.spotifyclone.util.WindowInsetHelper.applySystemBarInsets(binding.getRoot(), true, true);

        loadUserData();
        setupActions();
    }

    private void loadUserData() {
        User user = sessionManager.getUser();
        if (user != null) {
            binding.etDisplayName.setText(user.getDisplayName());
            binding.etUsername.setText(user.getUsername());
            binding.etEmail.setText(user.getEmail());

            currentAvatarUrl = user.getAvatarUrl();
            loadAvatarPreview(currentAvatarUrl);

            if ("admin".equalsIgnoreCase(user.getRole())) {
                binding.tvProfileBadge.setText("QUẢN TRỊ VIÊN • ADMIN");
            } else {
                binding.tvProfileBadge.setText("SPOTIFY PREMIUM • HOẠT ĐỘNG");
            }
        }
    }

    private void setupActions() {
        binding.btnProfileBack.setOnClickListener(v -> finish());

        // Chạm vào ảnh đại diện hoặc nút badge camera để đổi avatar
        View.OnClickListener changeAvatarListener = v -> showAvatarOptionsDialog();
        binding.ivProfileAvatar.setOnClickListener(changeAvatarListener);
        binding.btnChangeAvatarBadge.setOnClickListener(changeAvatarListener);

        // Lưu thay đổi thông tin cá nhân
        binding.btnSaveProfile.setOnClickListener(v -> performSaveProfile());

        // Chọn chất lượng âm thanh
        binding.btnAudioQualitySelector.setOnClickListener(v -> showAudioQualityDialog());

        // Đổi mật khẩu
        binding.btnChangePassword.setOnClickListener(v -> performChangePassword());
    }

    private void loadAvatarPreview(String url) {
        if (url != null && !url.isEmpty()) {
            Glide.with(this)
                    .load(ApiClient.resolveMediaUrl(url))
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(binding.ivProfileAvatar);
        } else {
            binding.ivProfileAvatar.setImageResource(R.drawable.ic_person);
        }
    }

    private void showAvatarOptionsDialog() {
        String[] options = {
                "Chọn từ bộ sưu tập Avatar có sẵn",
                "Tạo ngẫu nhiên Avatar mới (DiceBear)",
                "Nhập đường dẫn ảnh tùy thích (URL)"
        };

        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(R.string.profile_change_avatar)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showPresetAvatarsDialog();
                    } else if (which == 1) {
                        String randomSeed = UUID.randomUUID().toString().substring(0, 8);
                        currentAvatarUrl = "https://api.dicebear.com/7.x/bottts/svg?seed=" + randomSeed;
                        loadAvatarPreview(currentAvatarUrl);
                        Toast.makeText(this, "Đã tạo Avatar mới! Hãy bấm 'Lưu thay đổi' để áp dụng.", Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        showCustomUrlDialog();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showPresetAvatarsDialog() {
        String[] presetNames = {
                "Cyber DJ (Robot Neon)",
                "Acoustic Soul (Chill)",
                "Neon Beat (Tím mộng mơ)",
                "Pop Star (Năng động)",
                "Rock Legend (Cá tính)",
                "Nghệ sĩ trẻ năng động",
                "Nhiếp ảnh gia sành điệu"
        };

        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Bộ sưu tập Avatar")
                .setItems(presetNames, (dialog, which) -> {
                    if (which >= 0 && which < PRESET_AVATARS.length) {
                        currentAvatarUrl = PRESET_AVATARS[which];
                        loadAvatarPreview(currentAvatarUrl);
                        Toast.makeText(this, "Đã chọn avatar! Hãy bấm 'Lưu thay đổi' để hoàn tất.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showCustomUrlDialog() {
        EditText input = new EditText(this);
        input.setHint("https://example.com/my-photo.jpg");
        input.setTextColor(getColor(R.color.white));
        input.setPadding(48, 32, 48, 32);

        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(R.string.profile_custom_url)
                .setView(input)
                .setPositiveButton("Áp dụng", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(url)) {
                        currentAvatarUrl = url;
                        loadAvatarPreview(currentAvatarUrl);
                        Toast.makeText(this, "Đã cập nhật ảnh tạm thời. Hãy bấm 'Lưu thay đổi'!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showAudioQualityDialog() {
        int currentIndex = 2; // Default High (320 kbps)
        String currentValue = binding.tvAudioQualityValue.getText().toString();
        for (int i = 0; i < AUDIO_QUALITIES.length; i++) {
            if (AUDIO_QUALITIES[i].equalsIgnoreCase(currentValue)) {
                currentIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(R.string.audio_quality)
                .setSingleChoiceItems(AUDIO_QUALITIES, currentIndex, (dialog, which) -> {
                    binding.tvAudioQualityValue.setText(AUDIO_QUALITIES[which]);
                    Toast.makeText(this, "Đã chọn chất lượng: " + AUDIO_QUALITIES[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void performSaveProfile() {
        String newDisplayName = binding.etDisplayName.getText() != null ?
                binding.etDisplayName.getText().toString().trim() : "";

        if (TextUtils.isEmpty(newDisplayName)) {
            binding.tilDisplayName.setError("Tên hiển thị không được để trống");
            return;
        } else {
            binding.tilDisplayName.setError(null);
        }

        binding.btnSaveProfile.setEnabled(false);
        binding.btnSaveProfile.setText("Đang lưu thay đổi...");

        UpdateProfileRequest request = new UpdateProfileRequest(newDisplayName, currentAvatarUrl);
        ApiClient.getApiService().updateProfile(request).enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<User>> call, @NonNull Response<ApiResponse<User>> response) {
                binding.btnSaveProfile.setEnabled(true);
                binding.btnSaveProfile.setText(R.string.profile_save_changes);

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    User updatedUser = response.body().getData();
                    sessionManager.updateUser(updatedUser);

                    Toast.makeText(ProfileActivity.this, R.string.profile_saved_success, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    String errorMsg = extractErrorMessage(response, "Không thể cập nhật hồ sơ");
                    Toast.makeText(ProfileActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<User>> call, @NonNull Throwable t) {
                binding.btnSaveProfile.setEnabled(true);
                binding.btnSaveProfile.setText(R.string.profile_save_changes);
                Toast.makeText(ProfileActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void performChangePassword() {
        String currentPassword = binding.etCurrentPassword.getText() != null ?
                binding.etCurrentPassword.getText().toString().trim() : "";
        String newPassword = binding.etNewPassword.getText() != null ?
                binding.etNewPassword.getText().toString().trim() : "";
        String confirmPassword = binding.etConfirmPassword.getText() != null ?
                binding.etConfirmPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(currentPassword)) {
            binding.tilCurrentPassword.setError("Vui lòng nhập mật khẩu hiện tại");
            return;
        } else {
            binding.tilCurrentPassword.setError(null);
        }

        if (TextUtils.isEmpty(newPassword) || newPassword.length() < 8) {
            binding.tilNewPassword.setError("Mật khẩu mới phải có ít nhất 8 ký tự");
            return;
        } else {
            binding.tilNewPassword.setError(null);
        }

        if (!newPassword.equals(confirmPassword)) {
            binding.tilConfirmPassword.setError("Xác nhận mật khẩu mới không khớp");
            return;
        } else {
            binding.tilConfirmPassword.setError(null);
        }

        binding.btnChangePassword.setEnabled(false);
        binding.btnChangePassword.setText("Đang xử lý...");

        ChangePasswordRequest request = new ChangePasswordRequest(currentPassword, newPassword);
        ApiClient.getApiService().changePassword(request).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Object>> call, @NonNull Response<ApiResponse<Object>> response) {
                binding.btnChangePassword.setEnabled(true);
                binding.btnChangePassword.setText(R.string.profile_change_password);

                if (response.isSuccessful()) {
                    binding.etCurrentPassword.setText("");
                    binding.etNewPassword.setText("");
                    binding.etConfirmPassword.setText("");
                    Toast.makeText(ProfileActivity.this, R.string.profile_password_success, Toast.LENGTH_LONG).show();
                } else {
                    String errorMsg = extractErrorMessage(response, "Đổi mật khẩu thất bại");
                    Toast.makeText(ProfileActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Object>> call, @NonNull Throwable t) {
                binding.btnChangePassword.setEnabled(true);
                binding.btnChangePassword.setText(R.string.profile_change_password);
                Toast.makeText(ProfileActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private String extractErrorMessage(Response<?> response, String fallback) {
        try {
            if (response.errorBody() != null) {
                String errorBodyStr = response.errorBody().string();
                JSONObject json = new JSONObject(errorBodyStr);
                if (json.has("message")) {
                    return json.getString("message");
                }
            }
        } catch (Exception ignored) {}
        return fallback + " (" + response.code() + ")";
    }
}
