package com.example.spotifyclone.presentation.playlist;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.CreatePlaylistRequest;
import com.example.spotifyclone.data.model.FavoritesData;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.FragmentLibraryBinding;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LibraryFragment extends Fragment implements LibraryAdapter.OnLibraryItemClickListener {

    private FragmentLibraryBinding binding;
    private LibraryAdapter libraryAdapter;
    private final List<LibraryItem> allItems = new ArrayList<>();
    private String selectedFilter = "ALL"; // "ALL", "PLAYLISTS", "ARTISTS", "ALBUMS"

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupFilterChips();
        setupCreatePlaylistAction();
        setupEmptyStateExploreAction();
        fetchLibraryData();
    }

    private void setupRecyclerView() {
        libraryAdapter = new LibraryAdapter(this);
        binding.rvLibraryItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvLibraryItems.setAdapter(libraryAdapter);
    }

    private void setupFilterChips() {
        binding.chipPlaylists.setOnClickListener(v -> toggleChip("PLAYLISTS", binding.chipPlaylists));
        binding.chipArtists.setOnClickListener(v -> toggleChip("ARTISTS", binding.chipArtists));
        binding.chipAlbums.setOnClickListener(v -> toggleChip("ALBUMS", binding.chipAlbums));
    }

    private void toggleChip(String filter, TextView chipView) {
        if (filter.equals(selectedFilter)) {
            // Unselect -> reset to ALL
            selectedFilter = "ALL";
            resetAllChips();
        } else {
            selectedFilter = filter;
            resetAllChips();
            chipView.setBackgroundResource(R.drawable.bg_pill_active);
            chipView.setTextColor(getResources().getColor(R.color.black, null));
        }
        applyCurrentFilter();
    }

    private void resetAllChips() {
        binding.chipPlaylists.setBackgroundResource(R.drawable.bg_pill_inactive);
        binding.chipPlaylists.setTextColor(getResources().getColor(R.color.text_primary, null));

        binding.chipArtists.setBackgroundResource(R.drawable.bg_pill_inactive);
        binding.chipArtists.setTextColor(getResources().getColor(R.color.text_primary, null));

        binding.chipAlbums.setBackgroundResource(R.drawable.bg_pill_inactive);
        binding.chipAlbums.setTextColor(getResources().getColor(R.color.text_primary, null));
    }

    private void applyCurrentFilter() {
        List<LibraryItem> filtered = new ArrayList<>();
        for (LibraryItem item : allItems) {
            if ("ALL".equals(selectedFilter)) {
                filtered.add(item);
            } else if ("PLAYLISTS".equals(selectedFilter)) {
                if (item.getType() == LibraryItem.Type.LIKED_SONGS || item.getType() == LibraryItem.Type.PLAYLIST) {
                    filtered.add(item);
                }
            } else if ("ARTISTS".equals(selectedFilter)) {
                if (item.getType() == LibraryItem.Type.ARTIST) {
                    filtered.add(item);
                }
            } else if ("ALBUMS".equals(selectedFilter)) {
                if (item.getType() == LibraryItem.Type.ALBUM) {
                    filtered.add(item);
                }
            }
        }
        libraryAdapter.setItems(filtered);
        showEmptyState(filtered.isEmpty());
    }

    private void setupCreatePlaylistAction() {
        binding.btnCreatePlaylist.setOnClickListener(v -> showCreatePlaylistDialog());
    }

    private void showCreatePlaylistDialog() {
        if (getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.Theme_SpotifyClone);
        builder.setTitle("Tạo danh sách phát mới");

        final EditText input = new EditText(requireContext());
        input.setHint("Đặt tên cho danh sách phát");
        input.setHintTextColor(getResources().getColor(R.color.icon_inactive, null));
        input.setTextColor(getResources().getColor(R.color.white, null));
        input.setPadding(48, 32, 48, 32);
        builder.setView(input);

        builder.setPositiveButton("Tạo", (dialog, which) -> {
            String playlistName = input.getText().toString().trim();
            if (playlistName.isEmpty()) {
                Toast.makeText(requireContext(), "Tên danh sách phát không được để trống", Toast.LENGTH_SHORT).show();
                return;
            }
            createPlaylist(playlistName);
        });

        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void createPlaylist(String title) {
        CreatePlaylistRequest request = new CreatePlaylistRequest(title, "Danh sách phát của tôi", true);
        ApiClient.getApiService().createPlaylist(request).enqueue(new Callback<ApiResponse<Playlist>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Playlist>> call, @NonNull Response<ApiResponse<Playlist>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    Toast.makeText(requireContext(), "Đã tạo playlist: " + title, Toast.LENGTH_SHORT).show();
                    fetchLibraryData();
                } else {
                    Toast.makeText(requireContext(), "Không thể tạo playlist", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Playlist>> call, @NonNull Throwable t) {
                Toast.makeText(requireContext(), "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupEmptyStateExploreAction() {
        binding.btnExploreMusic.setOnClickListener(v -> navigateToSearch());
    }

    private void navigateToSearch() {
        try {
            NavHostFragment.findNavController(LibraryFragment.this).navigate(R.id.navigation_search);
        } catch (Exception ignored) {}
    }

    private void fetchLibraryData() {
        if (binding == null) return;
        allItems.clear();

        binding.shimmerLibraryContainer.setVisibility(View.VISIBLE);
        binding.shimmerLibraryContainer.startShimmer();
        binding.rvLibraryItems.setVisibility(View.GONE);
        binding.layoutLibraryEmpty.setVisibility(View.GONE);

        // 1. Fetch Liked Songs (Favorites)
        ApiClient.getApiService().getFavorites().enqueue(new Callback<ApiResponse<FavoritesData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<FavoritesData>> call, @NonNull Response<ApiResponse<FavoritesData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<?> songs = response.body().getData().getSongs();
                    int count = (songs != null) ? songs.size() : 0;
                    if (count > 0) {
                        allItems.add(0, LibraryItem.createLikedSongs(count));
                    }
                }
                fetchPlaylists();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<FavoritesData>> call, @NonNull Throwable t) {
                fetchPlaylists();
            }
        });
    }

    private void fetchPlaylists() {
        ApiClient.getApiService().getMyPlaylists().enqueue(new Callback<ApiResponse<List<Playlist>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Playlist>>> call, @NonNull Response<ApiResponse<List<Playlist>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<Playlist> playlists = response.body().getData();
                    for (Playlist p : playlists) {
                        allItems.add(LibraryItem.createPlaylist(p));
                    }
                }
                fetchAlbums();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Playlist>>> call, @NonNull Throwable t) {
                fetchAlbums();
            }
        });
    }

    private void fetchAlbums() {
        ApiClient.getApiService().getPopularAlbums().enqueue(new Callback<ApiResponse<List<com.example.spotifyclone.data.model.Album>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<com.example.spotifyclone.data.model.Album>>> call, @NonNull Response<ApiResponse<List<com.example.spotifyclone.data.model.Album>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<com.example.spotifyclone.data.model.Album> albums = response.body().getData();
                    for (com.example.spotifyclone.data.model.Album a : albums) {
                        allItems.add(LibraryItem.createAlbum(a));
                    }
                }
                finishLoading();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<com.example.spotifyclone.data.model.Album>>> call, @NonNull Throwable t) {
                finishLoading();
            }
        });
    }

    private void finishLoading() {
        if (binding == null) return;
        binding.shimmerLibraryContainer.stopShimmer();
        binding.shimmerLibraryContainer.setVisibility(View.GONE);
        applyCurrentFilter();
    }

    public void showEmptyState(boolean isEmpty) {
        if (binding == null) return;
        binding.layoutLibraryEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvLibraryItems.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        if (isEmpty) {
            if ("PLAYLISTS".equals(selectedFilter)) {
                binding.tvLibraryEmptyTitle.setText("Chưa có danh sách phát");
                binding.tvLibraryEmptySubtitle.setText("Tạo danh sách phát đầu tiên của bạn để lưu các bài hát yêu thích.");
                binding.btnExploreMusic.setText("Tạo danh sách phát");
                binding.btnExploreMusic.setOnClickListener(v -> showCreatePlaylistDialog());
            } else if ("ARTISTS".equals(selectedFilter)) {
                binding.tvLibraryEmptyTitle.setText("Chưa theo dõi nghệ sĩ nào");
                binding.tvLibraryEmptySubtitle.setText("Khám phá các nghệ sĩ hàng đầu và theo dõi họ để cập nhật bài hát mới.");
                binding.btnExploreMusic.setText("Khám phá nghệ sĩ");
                binding.btnExploreMusic.setOnClickListener(v -> navigateToSearch());
            } else if ("ALBUMS".equals(selectedFilter)) {
                binding.tvLibraryEmptyTitle.setText("Chưa lưu album nào");
                binding.tvLibraryEmptySubtitle.setText("Khám phá các album nổi tiếng từ những nghệ sĩ hàng đầu.");
                binding.btnExploreMusic.setText("Khám phá album");
                binding.btnExploreMusic.setOnClickListener(v -> navigateToSearch());
            } else {
                binding.tvLibraryEmptyTitle.setText(R.string.empty_library_title);
                binding.tvLibraryEmptySubtitle.setText(R.string.empty_library_subtitle);
                binding.btnExploreMusic.setText("Khám phá ngay");
                binding.btnExploreMusic.setOnClickListener(v -> navigateToSearch());
            }
        }
    }

    @Override
    public void onLibraryItemClick(LibraryItem item) {
        if (item == null) return;

        if (item.getType() == LibraryItem.Type.ARTIST && item.getArtist() != null) {
            Intent intent = new Intent(requireContext(), com.example.spotifyclone.presentation.artist.ArtistDetailActivity.class);
            intent.putExtra(com.example.spotifyclone.presentation.artist.ArtistDetailActivity.EXTRA_ARTIST_ID, item.getArtist().getId());
            intent.putExtra(com.example.spotifyclone.presentation.artist.ArtistDetailActivity.EXTRA_ARTIST_NAME, item.getArtist().getName());
            intent.putExtra(com.example.spotifyclone.presentation.artist.ArtistDetailActivity.EXTRA_ARTIST_AVATAR, item.getArtist().getAvatarUrl());
            intent.putExtra(com.example.spotifyclone.presentation.artist.ArtistDetailActivity.EXTRA_ARTIST_BIO, item.getArtist().getBio());
            intent.putExtra(com.example.spotifyclone.presentation.artist.ArtistDetailActivity.EXTRA_ARTIST_FOLLOWERS, item.getArtist().getFollowersCount());
            startActivity(intent);
            return;
        }

        Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
        if (item.getType() == LibraryItem.Type.LIKED_SONGS) {
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE, PlaylistDetailActivity.TYPE_LIKED_SONGS);
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TITLE, "Bài hát đã thích");
        } else if (item.getType() == LibraryItem.Type.ALBUM) {
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE, PlaylistDetailActivity.TYPE_ALBUM);
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, item.getId());
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TITLE, item.getTitle());
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_COVER, item.getCoverUrl());
        } else {
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE, PlaylistDetailActivity.TYPE_CUSTOM_PLAYLIST);
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, item.getId());
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TITLE, item.getTitle());
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_COVER, item.getCoverUrl());
        }
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchLibraryData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
