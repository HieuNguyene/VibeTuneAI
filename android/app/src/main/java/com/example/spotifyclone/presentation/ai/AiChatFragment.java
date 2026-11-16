package com.example.spotifyclone.presentation.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.AiChatMessage;
import com.example.spotifyclone.data.model.AiChatRequest;
import com.example.spotifyclone.data.model.AiChatResponseData;
import com.example.spotifyclone.data.model.AiGeneratePlaylistResponse;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.CreatePlaylistRequest;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.FragmentAiChatBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.example.spotifyclone.presentation.main.MainActivity;
import com.example.spotifyclone.presentation.player.PlayerActivity;
import com.example.spotifyclone.presentation.playlist.PlaylistDetailActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiChatFragment extends Fragment implements AiChatAdapter.OnChatInteractionListener {

    private FragmentAiChatBinding binding;
    private AiChatAdapter chatAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAiChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupInputActions();
        setupQuickPrompts();
        setupResetAction();

        if (chatAdapter.isEmpty()) {
            addWelcomeMessage();
        }
    }

    private void setupRecyclerView() {
        chatAdapter = new AiChatAdapter(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(false);
        binding.rvChatMessages.setLayoutManager(layoutManager);
        binding.rvChatMessages.setAdapter(chatAdapter);
    }

    private void addWelcomeMessage() {
        AiChatMessage welcome = new AiChatMessage(
                AiChatMessage.TYPE_AI,
                "Xin chào! Mình là Trợ lý Âm nhạc. Mình có thể giúp bạn tìm bài hát theo tâm trạng, ca sĩ hoặc thể loại bạn yêu thích. Bạn muốn nghe gì hôm nay?",
                null,
                Arrays.asList("Gợi ý nhạc chill", "Bài hát đang hot", "Nhạc Sơn Tùng M-TP")
        );
        chatAdapter.addMessage(welcome);
    }

    private void setupInputActions() {
        com.example.spotifyclone.ui.liquid.LiquidDropletHelper.attachDropletSpring(binding.btnSendMessage);
        binding.btnSendMessage.setOnClickListener(v -> sendMessage());

        binding.etChatMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    private void setupQuickPrompts() {
        for (int i = 0; i < binding.chipGroupQuickPrompts.getChildCount(); i++) {
            View child = binding.chipGroupQuickPrompts.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                chip.setOnClickListener(v -> {
                    if (chip.getId() == R.id.chipPromptCreateAiPlaylist) {
                        showAiPlaylistPromptDialog();
                    } else {
                        String promptText = chip.getText().toString();
                        sendCustomMessage(promptText);
                    }
                });
            }
        }
    }

    private void showAiPlaylistPromptDialog() {
        if (getContext() == null) return;

        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Ví dụ: Nhạc buồn mưa rơi, Acoustic thư giãn...");
        input.setHintTextColor(requireContext().getColor(R.color.text_secondary));
        input.setTextColor(requireContext().getColor(R.color.text_primary));
        input.setTextSize(15f);

        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        int padH = (int) (20 * getResources().getDisplayMetrics().density);
        int padV = (int) (12 * getResources().getDisplayMetrics().density);
        container.setPadding(padH, padV, padH, padV);
        container.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("✨ Tạo playlist bằng AI")
                .setMessage("Nhập chủ đề hoặc tâm trạng bạn muốn AI tạo:")
                .setView(container)
                .setPositiveButton("Gợi ý trong Chat", (dialog, which) -> {
                    String p = input.getText().toString().trim();
                    if (!p.isEmpty()) {
                        sendCustomMessage("Tạo 1 playlist: " + p);
                    }
                })
                .setNeutralButton("Tạo 15 bài vào Thư viện", (dialog, which) -> {
                    String p = input.getText().toString().trim();
                    if (p.isEmpty()) {
                        Toast.makeText(requireContext(), "Vui lòng nhập chủ đề trước", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    generateAiPlaylistDirectly(p);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void generateAiPlaylistDirectly(String prompt) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "Đang tạo playlist AI: " + prompt + "...", Toast.LENGTH_LONG).show();

        Map<String, Object> body = new HashMap<>();
        body.put("prompt", prompt);
        body.put("savePlaylist", true);

        ApiClient.getApiService().generateAiPlaylist(body).enqueue(new Callback<ApiResponse<AiGeneratePlaylistResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<AiGeneratePlaylistResponse>> call, @NonNull Response<ApiResponse<AiGeneratePlaylistResponse>> response) {
                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    AiGeneratePlaylistResponse data = response.body().getData();
                    Playlist playlist = data.getPlaylist();
                    if (playlist != null) {
                        Toast.makeText(requireContext(), "✨ Đã tạo playlist AI: " + playlist.getTitle(), Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
                        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE, PlaylistDetailActivity.TYPE_CUSTOM_PLAYLIST);
                        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlist.getId());
                        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TITLE, playlist.getTitle());
                        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_COVER, ApiClient.resolveMediaUrl(playlist.getCoverImageUrl()));
                        startActivity(intent);
                        return;
                    }
                }
                Toast.makeText(requireContext(), "Không thể tạo playlist AI lúc này. Vui lòng thử lại!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<AiGeneratePlaylistResponse>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupResetAction() {
        binding.btnResetChat.setOnClickListener(v -> {
            chatAdapter.clear();
            addWelcomeMessage();
            Toast.makeText(requireContext(), "Đã làm mới cuộc trò chuyện", Toast.LENGTH_SHORT).show();
        });
    }

    private void sendMessage() {
        String text = binding.etChatMessage.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        binding.etChatMessage.setText("");
        sendCustomMessage(text);
    }

    private void sendCustomMessage(String userPrompt) {
        // 1. Thu thập lịch sử hội thoại gần nhất để AI hiểu ngữ cảnh (ví dụ: "không còn bài khác à")
        List<Object> history = new ArrayList<>();
        if (chatAdapter != null) {
            List<AiChatMessage> currentMessages = chatAdapter.getMessages();
            int start = Math.max(0, currentMessages.size() - 6);
            for (int i = start; i < currentMessages.size(); i++) {
                AiChatMessage msg = currentMessages.get(i);
                if (msg.getType() == AiChatMessage.TYPE_USER || msg.getType() == AiChatMessage.TYPE_AI) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("role", msg.getType() == AiChatMessage.TYPE_USER ? "user" : "model");
                    item.put("parts", msg.getMessage());
                    if (msg.getSuggestedSongs() != null && !msg.getSuggestedSongs().isEmpty()) {
                        List<String> songIds = new ArrayList<>();
                        List<String> songTitles = new ArrayList<>();
                        for (Song s : msg.getSuggestedSongs()) {
                            if (s.getId() != null) songIds.add(s.getId());
                            if (s.getTitle() != null) songTitles.add(s.getTitle());
                        }
                        item.put("songIds", songIds);
                        item.put("songTitles", songTitles);
                    }
                    history.add(item);
                }
            }
        }

        // 2. Thêm tin nhắn của User vào danh sách
        chatAdapter.addMessage(new AiChatMessage(AiChatMessage.TYPE_USER, userPrompt));
        scrollToBottom();

        // 3. Thêm bong bóng loading
        chatAdapter.addMessage(AiChatMessage.createLoading());
        scrollToBottom();

        // 4. Gửi request lên Backend API kèm theo history
        AiChatRequest request = new AiChatRequest(userPrompt, history);
        ApiClient.getApiService().sendAiChatMessage(request).enqueue(new Callback<ApiResponse<AiChatResponseData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<AiChatResponseData>> call, @NonNull Response<ApiResponse<AiChatResponseData>> response) {
                if (!isAdded()) return;

                chatAdapter.removeLoading();

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    AiChatResponseData data = response.body().getData();
                    AiChatMessage aiMessage = new AiChatMessage(
                            AiChatMessage.TYPE_AI,
                            data.getReply(),
                            data.getSuggestedSongs(),
                            data.getSuggestedReplies()
                    );
                    chatAdapter.addMessage(aiMessage);
                } else {
                    AiChatMessage errorMessage = new AiChatMessage(
                            AiChatMessage.TYPE_AI,
                            "Xin lỗi, mình gặp chút trục trặc khi xử lý yêu cầu. Bạn hãy thử lại câu hỏi khác nhé.",
                            null,
                            Arrays.asList("Gợi ý nhạc chill", "Bài hát đang hot")
                    );
                    chatAdapter.addMessage(errorMessage);
                }
                scrollToBottom();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<AiChatResponseData>> call, @NonNull Throwable t) {
                if (!isAdded()) return;

                chatAdapter.removeLoading();
                AiChatMessage networkError = new AiChatMessage(
                        AiChatMessage.TYPE_AI,
                        "Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại mạng và thử lại.",
                        null,
                        Arrays.asList("Thử lại", "Gợi ý nhạc chill")
                );
                chatAdapter.addMessage(networkError);
                scrollToBottom();
            }
        });
    }

    private void scrollToBottom() {
        if (binding != null && chatAdapter != null && chatAdapter.getItemCount() > 0) {
            binding.rvChatMessages.post(() -> {
                if (binding != null && chatAdapter != null && chatAdapter.getItemCount() > 0) {
                    binding.rvChatMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                }
            });
        }
    }

    @Override
    public void onPlaySong(Song song, List<Song> songList, int position) {
        if (song == null) return;

        int index = songList != null ? songList.indexOf(song) : 0;
        if (index == -1) index = 0;

        List<Song> queue = songList != null && !songList.isEmpty() ? songList : Arrays.asList(song);
        MusicPlayerManager.getInstance().playQueue(queue, index);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateMiniPlayerTrack(song);
        }

        Toast.makeText(requireContext(), "Đang phát: " + song.getTitle(), Toast.LENGTH_SHORT).show();

        // Gửi sự kiện nghe 'play' với source: 'chat' tới POST /events theo Giai đoạn 0 & 5
        Map<String, Object> eventBody = new HashMap<>();
        eventBody.put("trackId", song.getId());
        eventBody.put("event", "play");
        eventBody.put("positionSec", 0);
        eventBody.put("source", "chat");
        ApiClient.getApiService().logEvent(eventBody).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Object>> call, @NonNull Response<ApiResponse<Object>> response) {}
            @Override
            public void onFailure(@NonNull Call<ApiResponse<Object>> call, @NonNull Throwable t) {}
        });

        Intent intent = new Intent(requireContext(), PlayerActivity.class);
        intent.putExtra("SONG_TITLE", song.getTitle());
        intent.putExtra("ARTIST_NAME", song.getArtistName());
        intent.putExtra("COVER_URL", ApiClient.resolveMediaUrl(song.getCoverImageUrl()));
        intent.putExtra("AUDIO_URL", ApiClient.resolveMediaUrl(song.getAudioUrl()));
        intent.putExtra("SONG_ID", song.getId());
        intent.putExtra("IS_LIKED", song.isLiked());
        intent.putExtra("DURATION", song.getDuration());
        startActivity(intent);
        requireActivity().overridePendingTransition(R.anim.slide_in_bottom, R.anim.stay);
    }

    @Override
    public void onQuickReplyClick(String prompt) {
        sendCustomMessage(prompt);
    }

    @Override
    public void onCreatePlaylistClick(AiChatMessage message) {
        if (message == null || message.getSuggestedSongs() == null || message.getSuggestedSongs().isEmpty()) {
            Toast.makeText(requireContext(), "Không có bài hát nào để tạo playlist", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Song> songs = message.getSuggestedSongs();
        List<String> trackIds = new ArrayList<>();
        for (Song s : songs) {
            if (s != null && s.getId() != null) {
                trackIds.add(s.getId());
            }
        }

        if (trackIds.isEmpty()) {
            Toast.makeText(requireContext(), "Không tìm thấy mã bài hát hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        String firstTitle = songs.get(0).getTitle() != null ? songs.get(0).getTitle() : "Gợi ý hay";
        String defaultTitle = "Playlist AI: " + firstTitle;

        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setText(defaultTitle);
        input.setSelection(defaultTitle.length());
        input.setTextColor(requireContext().getColor(R.color.text_primary));
        input.setHint("Nhập tên danh sách phát...");
        input.setHintTextColor(requireContext().getColor(R.color.text_secondary));

        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        int padH = (int) (20 * getResources().getDisplayMetrics().density);
        int padV = (int) (12 * getResources().getDisplayMetrics().density);
        container.setPadding(padH, padV, padH, padV);
        container.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("✨ Tạo danh sách phát từ gợi ý")
                .setMessage("Lưu " + trackIds.size() + " bài hát gợi ý của AI vào thư viện của bạn:")
                .setView(container)
                .setPositiveButton("Tạo ngay", (dialog, which) -> {
                    String chosenTitle = input.getText().toString().trim();
                    if (chosenTitle.isEmpty()) chosenTitle = defaultTitle;
                    createPlaylistFromAiTracks(chosenTitle, trackIds);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void createPlaylistFromAiTracks(String title, List<String> trackIds) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), "Đang tạo playlist...", Toast.LENGTH_SHORT).show();

        CreatePlaylistRequest request = new CreatePlaylistRequest(
                title,
                "Danh sách phát được tạo bởi Trợ lý Âm nhạc AI VibeTune",
                true,
                null,
                trackIds
        );

        ApiClient.getApiService().createPlaylist(request).enqueue(new Callback<ApiResponse<Playlist>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Playlist>> call, @NonNull Response<ApiResponse<Playlist>> response) {
                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    Playlist created = response.body().getData();
                    Toast.makeText(requireContext(), "✨ Đã tạo playlist: " + created.getTitle(), Toast.LENGTH_LONG).show();

                    Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
                    intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE, PlaylistDetailActivity.TYPE_CUSTOM_PLAYLIST);
                    intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, created.getId());
                    intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TITLE, created.getTitle());
                    intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_COVER, ApiClient.resolveMediaUrl(created.getCoverImageUrl()));
                    startActivity(intent);
                } else {
                    Toast.makeText(requireContext(), "Không thể tạo playlist lúc này", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Playlist>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
