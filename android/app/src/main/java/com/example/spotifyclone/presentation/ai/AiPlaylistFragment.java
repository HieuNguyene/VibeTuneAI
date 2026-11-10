package com.example.spotifyclone.presentation.ai;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.AiCriteria;
import com.example.spotifyclone.data.model.AiGenerateRequest;
import com.example.spotifyclone.data.model.AiGenerateResponseData;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.FragmentAiPlaylistBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.example.spotifyclone.presentation.main.MainActivity;
import com.example.spotifyclone.presentation.player.PlayerActivity;
import com.example.spotifyclone.presentation.search.SearchResultAdapter;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiPlaylistFragment extends Fragment implements SearchResultAdapter.OnSearchSongClickListener {

    private FragmentAiPlaylistBinding binding;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private SearchResultAdapter generatedSongAdapter;
    private final List<Song> generatedSongs = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAiPlaylistBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupMoodChips();
        setupGenerateAction();
    }

    private void setupRecyclerView() {
        generatedSongAdapter = new SearchResultAdapter(this);
        binding.rvAiGeneratedSongs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAiGeneratedSongs.setAdapter(generatedSongAdapter);
    }

    private void setupMoodChips() {
        for (int i = 0; i < binding.chipGroupMoods.getChildCount(); i++) {
            View child = binding.chipGroupMoods.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                chip.setOnClickListener(v -> {
                    String currentText = binding.etAiPrompt.getText().toString().trim();
                    String chipText = chip.getText().toString();
                    if (currentText.isEmpty()) {
                        binding.etAiPrompt.setText(chipText);
                    } else {
                        binding.etAiPrompt.setText(currentText + ", " + chipText);
                    }
                    binding.etAiPrompt.setSelection(binding.etAiPrompt.getText().length());
                });
            }
        }
    }

    private void setupGenerateAction() {
        binding.btnGenerateAiPlaylist.setOnClickListener(v -> {
            String prompt = binding.etAiPrompt.getText().toString().trim();
            if (prompt.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập tâm trạng hoặc ý tưởng của bạn!", Toast.LENGTH_SHORT).show();
                return;
            }

            executeAiPlaylistGeneration(prompt);
        });
    }

    private void executeAiPlaylistGeneration(String prompt) {
        binding.btnGenerateAiPlaylist.setEnabled(false);
        binding.cardAiLoading.setVisibility(View.VISIBLE);
        binding.layoutAiResult.setVisibility(View.GONE);
        binding.tvAiStatusStep.setText(R.string.ai_generating_step_1);

        // Step 2 transition for Explainable AI experience
        progressHandler.postDelayed(() -> {
            if (binding != null) {
                binding.tvAiStatusStep.setText(R.string.ai_generating_step_2);
            }
        }, 1200);

        AiGenerateRequest request = new AiGenerateRequest(prompt, true);

        ApiClient.getApiService().generateAiPlaylist(request).enqueue(new Callback<ApiResponse<AiGenerateResponseData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<AiGenerateResponseData>> call, @NonNull Response<ApiResponse<AiGenerateResponseData>> response) {
                if (binding == null) return;

                binding.btnGenerateAiPlaylist.setEnabled(true);
                binding.cardAiLoading.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    AiGenerateResponseData data = response.body().getData();
                    List<Song> songs = data.getSongs();

                    if (songs != null && !songs.isEmpty()) {
                        generatedSongs.clear();
                        generatedSongs.addAll(songs);
                        generatedSongAdapter.setSongs(generatedSongs);

                        binding.layoutAiResult.setVisibility(View.VISIBLE);

                        // Set playlist title
                        String title = "Playlist Tạo Bởi AI";
                        if (data.getPlaylist() != null && data.getPlaylist().getTitle() != null) {
                            title = data.getPlaylist().getTitle();
                        } else if (data.getCriteria() != null && data.getCriteria().getTitle() != null) {
                            title = data.getCriteria().getTitle();
                        }
                        binding.tvGeneratedPlaylistTitle.setText(title);

                        // Explainable AI Reasoning summary
                        AiCriteria criteria = data.getCriteria();
                        if (criteria != null) {
                            StringBuilder explanation = new StringBuilder("✨ ");
                            if (criteria.getDescription() != null && !criteria.getDescription().isEmpty()) {
                                explanation.append(criteria.getDescription());
                            } else {
                                explanation.append("Gợi ý các ca khúc phù hợp cảm xúc và không gian âm nhạc.");
                            }
                            binding.tvAiReasoningExplanation.setText(explanation.toString());
                        }

                        Toast.makeText(requireContext(), "Đã tạo thành công " + songs.size() + " bài hát!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Không tìm thấy bài hát phù hợp. Vui lòng thử prompt khác!", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(requireContext(), "Máy chủ không thể tạo playlist. Vui lòng thử lại!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<AiGenerateResponseData>> call, @NonNull Throwable t) {
                if (binding == null) return;
                binding.btnGenerateAiPlaylist.setEnabled(true);
                binding.cardAiLoading.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Lỗi kết nối Backend: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onSongClick(Song song) {
        if (song == null) return;

        int index = generatedSongs.indexOf(song);
        if (index == -1) index = 0;

        MusicPlayerManager.getInstance().playQueue(generatedSongs, index);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateMiniPlayerTrack(song);
        }

        Intent intent = new Intent(requireContext(), PlayerActivity.class);
        intent.putExtra("SONG_TITLE", song.getTitle());
        intent.putExtra("ARTIST_NAME", song.getArtistName());
        intent.putExtra("COVER_URL", ApiClient.resolveMediaUrl(song.getCoverImageUrl()));
        intent.putExtra("AUDIO_URL", ApiClient.resolveMediaUrl(song.getAudioUrl()));
        intent.putExtra("SONG_ID", song.getId());
        intent.putExtra("IS_LIKED", song.isLiked());
        intent.putExtra("DURATION", song.getDuration());
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        progressHandler.removeCallbacksAndMessages(null);
        binding = null;
    }
}
