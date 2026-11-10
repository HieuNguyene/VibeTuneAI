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
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.FragmentAiChatBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.example.spotifyclone.presentation.main.MainActivity;
import com.example.spotifyclone.presentation.player.PlayerActivity;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
                "Xin chào! Mình là Spotify AI Music DJ 🎧✨. Mình có thể giúp bạn tìm bài hát theo tâm trạng, ca sĩ hoặc thể loại bạn yêu thích. Bạn muốn nghe gì hôm nay?",
                null,
                Arrays.asList("Gợi ý nhạc chill ☕", "Bài hát đang hot 🔥", "Nhạc Sơn Tùng M-TP 🌟")
        );
        chatAdapter.addMessage(welcome);
    }

    private void setupInputActions() {
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
                    String promptText = chip.getText().toString();
                    sendCustomMessage(promptText);
                });
            }
        }
    }

    private void setupResetAction() {
        binding.btnResetChat.setOnClickListener(v -> {
            chatAdapter.clear();
            addWelcomeMessage();
            Toast.makeText(requireContext(), "Đã làm mới cuộc trò chuyện với AI", Toast.LENGTH_SHORT).show();
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
        // 1. Thêm tin nhắn của User vào danh sách
        chatAdapter.addMessage(new AiChatMessage(AiChatMessage.TYPE_USER, userPrompt));
        scrollToBottom();

        // 2. Thêm bong bóng loading
        chatAdapter.addMessage(AiChatMessage.createLoading());
        scrollToBottom();

        // 3. Gửi request lên Backend API
        AiChatRequest request = new AiChatRequest(userPrompt);
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
                            "Xin lỗi, mình gặp chút trục trặc khi kết nối với máy chủ AI. Bạn hãy thử lại câu hỏi khác nhé! 🔄",
                            null,
                            Arrays.asList("Gợi ý nhạc chill ☕", "Bài hát đang hot 🔥")
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
                        "Không thể kết nối đến máy chủ AI. Vui lòng kiểm tra lại mạng hoặc thử lại! 📡",
                        null,
                        Arrays.asList("Thử lại 🔄", "Gợi ý nhạc chill ☕")
                );
                chatAdapter.addMessage(networkError);
                scrollToBottom();
            }
        });
    }

    private void scrollToBottom() {
        if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
            binding.rvChatMessages.post(() -> binding.rvChatMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1));
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
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
