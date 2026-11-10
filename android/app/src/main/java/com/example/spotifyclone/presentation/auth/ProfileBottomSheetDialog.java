package com.example.spotifyclone.presentation.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.local.SessionManager;
import com.example.spotifyclone.data.model.User;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.BottomSheetProfileBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ProfileBottomSheetDialog extends BottomSheetDialogFragment {

    private BottomSheetProfileBinding binding;
    private SessionManager sessionManager;

    public static ProfileBottomSheetDialog newInstance() {
        return new ProfileBottomSheetDialog();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sessionManager = SessionManager.getInstance(requireContext());

        bindUserData();

        // Account Settings Click -> Mo ProfileActivity
        binding.btnAccountSettings.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ProfileActivity.class);
            startActivity(intent);
            dismiss();
        });

        binding.ivUserAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ProfileActivity.class);
            startActivity(intent);
            dismiss();
        });

        // Audio Quality Click
        binding.btnAudioQuality.setOnClickListener(v -> {
            String[] qualities = {
                    "Tự động (Khuyên dùng)",
                    "Bình thường (160 kbps)",
                    "Cao (320 kbps)",
                    "Rất cao / Lossless (FLAC)"
            };
            new MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                    .setTitle(R.string.audio_quality)
                    .setSingleChoiceItems(qualities, 2, (dialog, which) -> {
                        Toast.makeText(requireContext(), "Đã chọn chất lượng âm thanh: " + qualities[which], Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
        });

        // Logout Flow with Confirmation Dialog
        binding.btnProfileLogout.setOnClickListener(v -> showLogoutConfirmation());
    }

    private void bindUserData() {
        User user = sessionManager.getUser();
        if (user != null) {
            binding.tvProfileName.setText(user.getDisplayName());
            binding.tvProfileEmail.setText(user.getEmail());

            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                binding.ivUserAvatar.setImageTintList(null);
                binding.ivUserAvatar.setPadding(0, 0, 0, 0);
                Glide.with(this)
                        .load(ApiClient.resolveMediaUrl(user.getAvatarUrl()))
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(binding.ivUserAvatar);
            }
        }
    }

    private void showLogoutConfirmation() {
        new MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.btn_logout, (dialog, which) -> {
                    dialog.dismiss();
                    dismiss();
                    performLogout();
                })
                .show();
    }

    private void performLogout() {
        // Dung phat nhac
        MusicPlayerManager.getInstance().stop();

        // Xoa phien dang nhap
        sessionManager.clearSession();

        Toast.makeText(requireContext(), "Đã đăng xuất tài khoản thành công!", Toast.LENGTH_SHORT).show();

        // Dieu huong ve LoginActivity va xoa toan bo stack cu
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
