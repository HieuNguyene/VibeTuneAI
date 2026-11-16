package com.example.spotifyclone.presentation.notification;

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
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.NotificationItem;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.DialogNotificationDetailBinding;
import com.example.spotifyclone.presentation.playlist.PlaylistDetailActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationDetailBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_NOTIFICATION = "arg_notification";

    public interface OnNotificationDetailActionListener {
        void onNotificationDeleted(NotificationItem item);
    }

    private DialogNotificationDetailBinding binding;
    private NotificationItem notification;
    private OnNotificationDetailActionListener actionListener;

    public static NotificationDetailBottomSheet newInstance(NotificationItem item) {
        NotificationDetailBottomSheet fragment = new NotificationDetailBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_NOTIFICATION, item);
        fragment.setArguments(args);
        return fragment;
    }

    public void setActionListener(OnNotificationDetailActionListener listener) {
        this.actionListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            notification = (NotificationItem) getArguments().getSerializable(ARG_NOTIFICATION);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogNotificationDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (notification == null) {
            dismiss();
            return;
        }

        bindData();
        setupListeners();
    }

    private void bindData() {
        // Type Badge
        String type = notification.getType();
        String typeLabel = "THÔNG BÁO";
        if ("ADMIN_ANNOUNCEMENT".equalsIgnoreCase(type)) {
            typeLabel = "THÔNG BÁO TỪ QUẢN TRỊ VIÊN";
        } else if ("PLAYLIST_LIKED".equalsIgnoreCase(type)) {
            typeLabel = "YÊU THÍCH DANH SÁCH PHÁT";
        } else if ("SYSTEM_UPDATE".equalsIgnoreCase(type)) {
            typeLabel = "CẬP NHẬT HỆ THỐNG";
        }
        binding.tvDetailTypeBadge.setText(typeLabel);

        // Title
        binding.tvDetailTitle.setText(notification.getTitle() != null ? notification.getTitle() : "Thông báo");

        // Content
        binding.tvDetailContent.setText(notification.getContent() != null ? notification.getContent() : "");

        // Sender Info
        binding.tvDetailSenderName.setText(notification.getSenderName());
        binding.tvDetailTimestamp.setText(notification.getFullFormattedDateTime());

        if (notification.getSenderAvatar() != null && !notification.getSenderAvatar().isEmpty()) {
            Glide.with(this)
                    .load(ApiClient.resolveMediaUrl(notification.getSenderAvatar()))
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(binding.ivDetailSenderAvatar);
        } else {
            binding.ivDetailSenderAvatar.setImageResource(R.drawable.ic_person);
        }

        // Image
        if (notification.getImageUrl() != null && !notification.getImageUrl().isEmpty()) {
            binding.ivDetailImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(ApiClient.resolveMediaUrl(notification.getImageUrl()))
                    .placeholder(R.drawable.bg_card_rounded)
                    .into(binding.ivDetailImage);
        } else {
            binding.ivDetailImage.setVisibility(View.GONE);
        }

        // Action Button (e.g. Playlist link)
        if (notification.getMetadata() != null && notification.getMetadata().getPlaylistId() != null
                && !notification.getMetadata().getPlaylistId().trim().isEmpty()) {
            final String playlistId = notification.getMetadata().getPlaylistId().trim();
            binding.btnDetailAction.setVisibility(View.VISIBLE);
            binding.btnDetailAction.setText("Xem Danh Sách Phát");
            binding.btnDetailAction.setOnClickListener(v -> {
                dismiss();
                Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
                intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE, PlaylistDetailActivity.TYPE_CUSTOM_PLAYLIST);
                intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlistId);
                intent.putExtra(PlaylistDetailActivity.EXTRA_ORIGIN_DESTINATION_ID, R.id.navigation_home);
                startActivity(intent);
            });
        } else {
            binding.btnDetailAction.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        binding.btnDetailClose.setOnClickListener(v -> dismiss());

        binding.btnDetailDelete.setOnClickListener(v -> {
            if (notification.getId() == null) {
                dismiss();
                return;
            }

            ApiClient.getApiService().deleteNotification(notification.getId()).enqueue(new Callback<ApiResponse<Object>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<Object>> call, @NonNull Response<ApiResponse<Object>> response) {
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(requireContext(), "Đã xóa thông báo", Toast.LENGTH_SHORT).show();
                    }
                    if (actionListener != null) {
                        actionListener.onNotificationDeleted(notification);
                    }
                    dismiss();
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Object>> call, @NonNull Throwable t) {
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(requireContext(), "Lỗi khi xóa: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
