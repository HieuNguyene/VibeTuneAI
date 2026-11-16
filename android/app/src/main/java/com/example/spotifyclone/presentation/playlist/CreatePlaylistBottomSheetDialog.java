package com.example.spotifyclone.presentation.playlist;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.local.SessionManager;
import com.example.spotifyclone.data.model.AiGeneratePlaylistResponse;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.CreatePlaylistRequest;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.model.User;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.BottomSheetCreatePlaylistBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreatePlaylistBottomSheetDialog extends BottomSheetDialogFragment {

    public interface OnPlaylistCreatedListener {
        void onPlaylistCreated(Playlist playlist);
    }

    public interface OnPlaylistUpdatedListener {
        void onPlaylistUpdated(Playlist playlist);
    }

    private BottomSheetCreatePlaylistBinding binding;
    private OnPlaylistCreatedListener listener;
    private OnPlaylistUpdatedListener updateListener;
    private ActivityResultLauncher<String> pickImageLauncher;
    private Uri selectedImageUri = null;
    private SessionManager sessionManager;
    private boolean isEditMode = false;
    private boolean isAiMode = false;
    private String editPlaylistId = null;

    public static CreatePlaylistBottomSheetDialog newInstance() {
        return new CreatePlaylistBottomSheetDialog();
    }

    public static CreatePlaylistBottomSheetDialog newEditInstance(Playlist playlist) {
        CreatePlaylistBottomSheetDialog fragment = new CreatePlaylistBottomSheetDialog();
        Bundle args = new Bundle();
        if (playlist != null) {
            args.putString("ARG_PLAYLIST_ID", playlist.getId());
            args.putString("ARG_PLAYLIST_TITLE", playlist.getTitle());
            args.putString("ARG_PLAYLIST_DESCRIPTION", playlist.getDescription());
            args.putString("ARG_PLAYLIST_COVER", playlist.getCoverImageUrl());
            args.putBoolean("ARG_PLAYLIST_IS_PUBLIC", playlist.isPublic());
        }
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnPlaylistCreatedListener(OnPlaylistCreatedListener listener) {
        this.listener = listener;
    }

    public void setOnPlaylistUpdatedListener(OnPlaylistUpdatedListener updateListener) {
        this.updateListener = updateListener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_Spotify_BottomSheet);

        // Đăng ký bộ chọn ảnh từ bộ nhớ thiết bị
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        if (binding != null) {
                            binding.layoutUploadPlaceholder.setVisibility(View.GONE);
                            binding.layoutChangeBadge.setVisibility(View.VISIBLE);
                            binding.tvCoverHelper.setText("Ảnh bìa đã chọn từ máy");

                            Glide.with(this)
                                    .load(uri)
                                    .transform(new CenterCrop(), new RoundedCorners(24))
                                    .into(binding.ivPlaylistCover);
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetCreatePlaylistBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sessionManager = SessionManager.getInstance(requireContext());

        bindCreatorInfo();
        setupListeners();

        if (getArguments() != null && getArguments().containsKey("ARG_PLAYLIST_ID")) {
            isEditMode = true;
            editPlaylistId = getArguments().getString("ARG_PLAYLIST_ID");
            String title = getArguments().getString("ARG_PLAYLIST_TITLE", "");
            String desc = getArguments().getString("ARG_PLAYLIST_DESCRIPTION", "");
            String cover = getArguments().getString("ARG_PLAYLIST_COVER", "");
            boolean isPub = getArguments().getBoolean("ARG_PLAYLIST_IS_PUBLIC", true);

            binding.layoutModeSelector.setVisibility(View.GONE);
            binding.layoutManualSection.setVisibility(View.VISIBLE);
            binding.layoutAiSection.setVisibility(View.GONE);

            binding.tvSheetTitle.setText("Chỉnh sửa danh sách phát");
            binding.btnCreatePlaylistSubmit.setText("Lưu thay đổi");
            binding.etPlaylistTitle.setText(title);
            binding.etPlaylistDescription.setText(desc);
            binding.switchPublic.setChecked(isPub);

            if (cover != null && !cover.isEmpty()) {
                binding.layoutUploadPlaceholder.setVisibility(View.GONE);
                binding.layoutChangeBadge.setVisibility(View.VISIBLE);
                binding.tvCoverHelper.setText("Ảnh bìa hiện tại");
                Glide.with(this)
                        .load(ApiClient.resolveMediaUrl(cover))
                        .transform(new CenterCrop(), new RoundedCorners(24))
                        .placeholder(R.drawable.bg_card_rounded)
                        .error(R.drawable.bg_card_rounded)
                        .into(binding.ivPlaylistCover);
            }
        } else {
            isEditMode = false;
            binding.layoutModeSelector.setVisibility(View.VISIBLE);
            switchMode(false);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialog;
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        }
    }

    private void bindCreatorInfo() {
        User user = sessionManager.getUser();
        String creatorName = "Người dùng";
        if (user != null) {
            if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                creatorName = user.getDisplayName().trim();
            } else if (user.getUsername() != null && !user.getUsername().trim().isEmpty()) {
                creatorName = user.getUsername().trim();
            }

            if (user.getAvatarUrl() != null && !user.getAvatarUrl().trim().isEmpty()) {
                Glide.with(this)
                        .load(ApiClient.resolveMediaUrl(user.getAvatarUrl()))
                        .circleCrop()
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(binding.ivCreatorAvatar);
            }
        }
        binding.tvCreatorName.setText(creatorName);
    }

    private void switchMode(boolean ai) {
        this.isAiMode = ai;
        if (ai) {
            binding.btnModeAi.setBackgroundTintList(android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.selection_active_bg)));
            binding.btnModeAi.setTextColor(requireContext().getColor(R.color.selection_active_text));
            binding.btnModeAi.setIconTint(android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.selection_active_text)));

            binding.btnModeManual.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            binding.btnModeManual.setTextColor(requireContext().getColor(R.color.selection_inactive_text));
            binding.btnModeManual.setIconTint(android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.selection_inactive_text)));

            binding.layoutManualSection.setVisibility(View.GONE);
            binding.layoutAiSection.setVisibility(View.VISIBLE);
            binding.btnCreatePlaylistSubmit.setText("✨ Tạo playlist bằng AI");
        } else {
            binding.btnModeManual.setBackgroundTintList(android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.selection_active_bg)));
            binding.btnModeManual.setTextColor(requireContext().getColor(R.color.selection_active_text));
            binding.btnModeManual.setIconTint(android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.selection_active_text)));

            binding.btnModeAi.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            binding.btnModeAi.setTextColor(requireContext().getColor(R.color.selection_inactive_text));
            binding.btnModeAi.setIconTint(android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.selection_inactive_text)));

            binding.layoutManualSection.setVisibility(View.VISIBLE);
            binding.layoutAiSection.setVisibility(View.GONE);
            binding.btnCreatePlaylistSubmit.setText(isEditMode ? "Lưu thay đổi" : "Tạo playlist");
        }
    }

    private void setupListeners() {
        // Mode Selector: Manual vs AI
        binding.btnModeManual.setOnClickListener(v -> switchMode(false));
        binding.btnModeAi.setOnClickListener(v -> switchMode(true));

        // Quick AI suggestion chips
        binding.chipAiSad.setOnClickListener(v -> binding.etAiPrompt.setText("Nhạc buồn suy ban đêm, mưa rơi nhẹ nhàng"));
        binding.chipAiChill.setOnClickListener(v -> binding.etAiPrompt.setText("Acoustic quán cafe, giai điệu mộc mạc thư giãn"));
        binding.chipAiWorkout.setOnClickListener(v -> binding.etAiPrompt.setText("Tập gym, chạy bộ, EDM và Pop sôi động hưng phấn"));
        binding.chipAiVpop.setOnClickListener(v -> binding.etAiPrompt.setText("Hit V-Pop thịnh hành, nhạc trẻ Việt Nam cuốn hút"));

        // 1. Chọn ảnh từ máy
        binding.btnPickCoverImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // 2. Nút đóng và Hủy
        binding.btnCloseSheet.setOnClickListener(v -> dismiss());
        binding.btnCancel.setOnClickListener(v -> dismiss());

        // 3. Công tắc chuyển đổi Công khai / Riêng tư
        binding.switchPublic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                binding.ivPublicIcon.setImageResource(R.drawable.ic_globe);
                binding.ivPublicIcon.setColorFilter(requireContext().getColor(R.color.spotify_green));
                binding.tvPublicTitle.setText("Công khai danh sách phát");
                binding.tvPublicDescription.setText("Mọi người dùng khác đều có thể tìm thấy và nghe playlist này.");
            } else {
                binding.ivPublicIcon.setImageResource(R.drawable.ic_lock);
                binding.ivPublicIcon.setColorFilter(requireContext().getColor(R.color.text_secondary));
                binding.tvPublicTitle.setText("Riêng tư (Chỉ mình bạn xem)");
                binding.tvPublicDescription.setText("Chỉ duy nhất bạn mới có thể xem và quản lý playlist này.");
            }
        });

        // 4. Bấm vào layout công khai cũng có thể toggle switch
        binding.layoutPublicToggle.setOnClickListener(v -> {
            binding.switchPublic.toggle();
        });

        // 5. Gửi form tạo playlist (Manual hoặc AI)
        binding.btnCreatePlaylistSubmit.setOnClickListener(v -> {
            if (isAiMode) {
                performCreateAiPlaylist();
            } else {
                performCreatePlaylist();
            }
        });
    }

    private void performCreatePlaylist() {
        String title = "";
        if (binding.etPlaylistTitle.getText() != null) {
            title = binding.etPlaylistTitle.getText().toString().trim();
        }

        if (title.isEmpty()) {
            binding.tilPlaylistTitle.setError("Vui lòng nhập tên danh sách phát");
            binding.etPlaylistTitle.requestFocus();
            return;
        } else {
            binding.tilPlaylistTitle.setError(null);
        }

        String description = "";
        if (binding.etPlaylistDescription.getText() != null) {
            description = binding.etPlaylistDescription.getText().toString().trim();
        }

        boolean isPublic = binding.switchPublic.isChecked();

        // Hiển thị trạng thái đang xử lý
        setLoadingState(true);

        final String finalTitle = title;
        final String finalDesc = description;

        // Xử lý nén ảnh trên luồng phụ để không gây giật giao diện
        Executors.newSingleThreadExecutor().execute(() -> {
            String base64Image = null;
            if (selectedImageUri != null && getContext() != null) {
                base64Image = processImageToBase64(selectedImageUri);
            }

            final String finalBase64 = base64Image;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isEditMode) {
                        sendUpdatePlaylistRequest(finalTitle, finalDesc, isPublic, finalBase64);
                    } else {
                        sendCreatePlaylistRequest(finalTitle, finalDesc, isPublic, finalBase64);
                    }
                });
            }
        });
    }

    private String processImageToBase64(Uri uri) {
        try {
            if (getContext() == null) return null;
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            if (originalBitmap == null) return null;

            // 1. Cắt vuông (Center Crop 1:1)
            int width = originalBitmap.getWidth();
            int height = originalBitmap.getHeight();
            int newDimension = Math.min(width, height);
            int cropX = (width - newDimension) / 2;
            int cropY = (height - newDimension) / 2;
            Bitmap croppedBitmap = Bitmap.createBitmap(originalBitmap, cropX, cropY, newDimension, newDimension);

            // 2. Thu nhỏ kích thước vuông tối đa 600x600 chuẩn ảnh bìa nhạc
            int targetSize = 600;
            Bitmap finalBitmap;
            if (newDimension > targetSize) {
                finalBitmap = Bitmap.createScaledBitmap(croppedBitmap, targetSize, targetSize, true);
                if (croppedBitmap != originalBitmap) croppedBitmap.recycle();
                originalBitmap.recycle();
            } else {
                finalBitmap = croppedBitmap;
                if (croppedBitmap != originalBitmap) originalBitmap.recycle();
            }

            // 3. Nén sang định dạng JPEG chất lượng cao 85%
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
            byte[] imageBytes = outputStream.toByteArray();
            finalBitmap.recycle();

            // 4. Mã hóa Base64 chuỗi chuẩn data:image/jpeg;base64,...
            return "data:image/jpeg;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private void sendCreatePlaylistRequest(String title, String description, boolean isPublic, String base64Image) {
        CreatePlaylistRequest request = new CreatePlaylistRequest(title, description, isPublic, base64Image);

        ApiClient.getApiService().createPlaylist(request).enqueue(new Callback<ApiResponse<Playlist>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Playlist>> call, @NonNull Response<ApiResponse<Playlist>> response) {
                if (getContext() == null) return;
                setLoadingState(false);

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    Playlist createdPlaylist = response.body().getData();
                    Toast.makeText(requireContext(), "Đã tạo playlist: " + title, Toast.LENGTH_SHORT).show();

                    if (listener != null) {
                        listener.onPlaylistCreated(createdPlaylist);
                    }
                    dismiss();
                } else {
                    String msg = "Không thể tạo playlist";
                    if (response.body() != null && response.body().getMessage() != null) {
                        msg = response.body().getMessage();
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Playlist>> call, @NonNull Throwable t) {
                if (getContext() == null) return;
                setLoadingState(false);
                Toast.makeText(requireContext(), "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendUpdatePlaylistRequest(String title, String description, boolean isPublic, String base64Image) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("isPublic", isPublic);
        if (base64Image != null && !base64Image.isEmpty()) {
            body.put("coverImageBase64", base64Image);
        }

        ApiClient.getApiService().updatePlaylist(editPlaylistId, body).enqueue(new Callback<ApiResponse<Playlist>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Playlist>> call, @NonNull Response<ApiResponse<Playlist>> response) {
                if (getContext() == null) return;
                setLoadingState(false);

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    Playlist updatedPlaylist = response.body().getData();
                    Toast.makeText(requireContext(), "Đã cập nhật playlist thành công", Toast.LENGTH_SHORT).show();

                    if (updateListener != null) {
                        updateListener.onPlaylistUpdated(updatedPlaylist);
                    }
                    dismiss();
                } else {
                    String msg = "Không thể cập nhật playlist";
                    if (response.body() != null && response.body().getMessage() != null) {
                        msg = response.body().getMessage();
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Playlist>> call, @NonNull Throwable t) {
                if (getContext() == null) return;
                setLoadingState(false);
                Toast.makeText(requireContext(), "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performCreateAiPlaylist() {
        String prompt = "";
        if (binding.etAiPrompt.getText() != null) {
            prompt = binding.etAiPrompt.getText().toString().trim();
        }

        if (prompt.isEmpty()) {
            binding.tilAiPrompt.setError("Vui lòng nhập chủ đề bạn muốn tạo");
            binding.etAiPrompt.requestFocus();
            return;
        } else {
            binding.tilAiPrompt.setError(null);
        }

        setLoadingState(true);

        Map<String, Object> body = new HashMap<>();
        body.put("prompt", prompt);
        body.put("savePlaylist", true);

        ApiClient.getApiService().generateAiPlaylist(body).enqueue(new Callback<ApiResponse<AiGeneratePlaylistResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<AiGeneratePlaylistResponse>> call, @NonNull Response<ApiResponse<AiGeneratePlaylistResponse>> response) {
                if (getContext() == null) return;
                setLoadingState(false);

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    AiGeneratePlaylistResponse data = response.body().getData();
                    Playlist playlist = data.getPlaylist();
                    if (playlist != null) {
                        Toast.makeText(requireContext(), "✨ Đã tạo playlist AI: " + playlist.getTitle(), Toast.LENGTH_LONG).show();
                        if (listener != null) {
                            listener.onPlaylistCreated(playlist);
                        }
                        dismiss();
                        return;
                    }
                }

                String msg = "Không thể tạo playlist bằng AI lúc này";
                if (response.body() != null && response.body().getMessage() != null) {
                    msg = response.body().getMessage();
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<AiGeneratePlaylistResponse>> call, @NonNull Throwable t) {
                if (getContext() == null) return;
                setLoadingState(false);
                Toast.makeText(requireContext(), "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoadingState(boolean isLoading) {
        if (binding == null) return;
        binding.btnCreatePlaylistSubmit.setEnabled(!isLoading);
        binding.btnCreatePlaylistSubmit.setText(isLoading ? "" : (isAiMode ? "✨ Tạo playlist bằng AI" : (isEditMode ? "Lưu thay đổi" : "Tạo playlist")));
        binding.pbLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnModeManual.setEnabled(!isLoading);
        binding.btnModeAi.setEnabled(!isLoading);
        binding.btnPickCoverImage.setEnabled(!isLoading);
        binding.etPlaylistTitle.setEnabled(!isLoading);
        binding.etPlaylistDescription.setEnabled(!isLoading);
        binding.etAiPrompt.setEnabled(!isLoading);
        binding.switchPublic.setEnabled(!isLoading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
