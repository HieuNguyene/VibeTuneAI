package com.example.spotifyclone.presentation.search;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.Genre;
import com.example.spotifyclone.data.model.HomeFeedData;
import com.example.spotifyclone.data.model.SearchResultData;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.FragmentSearchBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.example.spotifyclone.presentation.main.MainActivity;
import com.example.spotifyclone.presentation.player.PlayerActivity;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchFragment extends Fragment {

    private FragmentSearchBinding binding;
    private SearchResultAdapter searchResultAdapter;
    private GenreCardAdapter genreCardAdapter;

    private final Handler searchDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private static final long SEARCH_DEBOUNCE_DELAY_MS = 300;
    private Call<ApiResponse<SearchResultData>> currentSearchCall;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerViews();
        setupSearchInput();
        loadBrowseCategories();
    }

    private void setupRecyclerViews() {
        // Danh sách kết quả tìm kiếm bài hát
        searchResultAdapter = new SearchResultAdapter(this::onSongClick);
        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSearchResults.setAdapter(searchResultAdapter);

        // Danh mục thể loại khám phá (Browse Categories)
        genreCardAdapter = new GenreCardAdapter(genre -> {
            binding.etSearchQuery.setText(genre.getName());
            binding.etSearchQuery.setSelection(genre.getName().length());
            performSearch(genre.getName());
        });
        binding.rvBrowseCategories.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.rvBrowseCategories.setAdapter(genreCardAdapter);
    }

    private void setupSearchInput() {
        binding.btnClearSearch.setOnClickListener(v -> {
            binding.etSearchQuery.setText("");
            showBrowseCategories();
        });

        binding.btnClearSearchAction.setOnClickListener(v -> {
            binding.etSearchQuery.setText("");
            showBrowseCategories();
        });

        binding.etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                binding.btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);

                // Debounce search requests tránh spam mạng
                if (searchRunnable != null) {
                    searchDebounceHandler.removeCallbacks(searchRunnable);
                }

                if (query.isEmpty()) {
                    if (currentSearchCall != null) {
                        currentSearchCall.cancel();
                    }
                    showBrowseCategories();
                } else {
                    searchRunnable = () -> performSearch(query);
                    searchDebounceHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_DELAY_MS);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadBrowseCategories() {
        ApiClient.getApiService().getGenres().enqueue(new Callback<ApiResponse<List<Genre>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Genre>>> call, @NonNull Response<ApiResponse<List<Genre>>> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    genreCardAdapter.setGenres(response.body().getData());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Genre>>> call, @NonNull Throwable t) {
                // Giữ nguyên danh sách hiện tại nếu mất mạng tạm thời
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && binding.etSearchQuery.getText().toString().trim().isEmpty()) {
            loadBrowseCategories();
        }
    }

    private void performSearch(String query) {
        // Ẩn thể loại khám phá và kết quả cũ, bật Shimmer
        binding.tvBrowseAllHeader.setVisibility(View.GONE);
        binding.rvBrowseCategories.setVisibility(View.GONE);
        binding.rvSearchResults.setVisibility(View.GONE);
        binding.layoutSearchEmptyState.setVisibility(View.GONE);
        binding.shimmerSearchContainer.setVisibility(View.VISIBLE);
        binding.shimmerSearchContainer.startShimmer();

        if (currentSearchCall != null) {
            currentSearchCall.cancel();
        }

        currentSearchCall = ApiClient.getApiService().searchSongs(query);
        currentSearchCall.enqueue(new Callback<ApiResponse<SearchResultData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<SearchResultData>> call, @NonNull Response<ApiResponse<SearchResultData>> response) {
                if (call.isCanceled() || binding == null) return;
                binding.shimmerSearchContainer.stopShimmer();
                binding.shimmerSearchContainer.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<Song> songs = response.body().getData().getSongs();
                    if (songs != null && !songs.isEmpty()) {
                        binding.layoutSearchEmptyState.setVisibility(View.GONE);
                        binding.rvSearchResults.setVisibility(View.VISIBLE);
                        searchResultAdapter.setSongs(songs);
                    } else {
                        binding.rvSearchResults.setVisibility(View.GONE);
                        binding.layoutSearchEmptyState.setVisibility(View.VISIBLE);
                    }
                } else {
                    binding.rvSearchResults.setVisibility(View.GONE);
                    binding.layoutSearchEmptyState.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<SearchResultData>> call, @NonNull Throwable t) {
                if (call.isCanceled() || binding == null) return;
                binding.shimmerSearchContainer.stopShimmer();
                binding.shimmerSearchContainer.setVisibility(View.GONE);
                binding.rvSearchResults.setVisibility(View.GONE);
                binding.layoutSearchEmptyState.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showBrowseCategories() {
        if (binding == null) return;
        binding.shimmerSearchContainer.stopShimmer();
        binding.shimmerSearchContainer.setVisibility(View.GONE);
        binding.tvBrowseAllHeader.setVisibility(View.VISIBLE);
        binding.rvBrowseCategories.setVisibility(View.VISIBLE);
        binding.rvSearchResults.setVisibility(View.GONE);
        binding.layoutSearchEmptyState.setVisibility(View.GONE);
    }

    private void onSongClick(Song song) {
        if (song == null) return;

        List<Song> currentResults = searchResultAdapter.getSongs();
        int index = currentResults != null ? currentResults.indexOf(song) : -1;
        if (currentResults != null && !currentResults.isEmpty() && index >= 0) {
            MusicPlayerManager.getInstance().playQueue(currentResults, index);
        } else {
            MusicPlayerManager.getInstance().playSong(song);
        }

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateMiniPlayerVisibility(true);
        }

        // Mở Fullscreen Player
        Intent intent = new Intent(requireContext(), PlayerActivity.class);
        intent.putExtra("SONG_TITLE", song.getTitle());
        intent.putExtra("ARTIST_NAME", song.getArtistName());
        intent.putExtra("COVER_URL", ApiClient.resolveMediaUrl(song.getCoverImageUrl()));
        intent.putExtra("AUDIO_URL", ApiClient.resolveMediaUrl(song.getAudioUrl()));
        intent.putExtra("SONG_ID", song.getId());
        intent.putExtra("IS_LIKED", song.isLiked());
        intent.putExtra("DURATION", song.getDuration());
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.slide_in_bottom, R.anim.stay);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchRunnable != null) {
            searchDebounceHandler.removeCallbacks(searchRunnable);
        }
        if (currentSearchCall != null) {
            currentSearchCall.cancel();
        }
        binding = null;
    }
}
