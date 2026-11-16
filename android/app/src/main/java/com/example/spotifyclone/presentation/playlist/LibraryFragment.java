package com.example.spotifyclone.presentation.playlist;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Album;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.Artist;
import com.example.spotifyclone.data.model.FavoritesData;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.FragmentLibraryBinding;
import com.example.spotifyclone.presentation.artist.ArtistDetailActivity;
import com.example.spotifyclone.presentation.player.PlayerActivity;
import com.example.spotifyclone.player.MusicPlayerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LibraryFragment extends Fragment implements LibraryAdapter.OnLibraryItemClickListener {

    private FragmentLibraryBinding binding;
    private LibraryAdapter libraryAdapter;

    private final List<LibraryItem> personalPlaylistItems = new ArrayList<>();
    private final List<LibraryItem> favoritePlaylistItems = new ArrayList<>();
    private final List<LibraryItem> allPlaylistItems = new ArrayList<>();
    private final List<LibraryItem> artistItems = new ArrayList<>();
    private final List<LibraryItem> albumItems = new ArrayList<>();
    private final List<LibraryItem> songItems = new ArrayList<>();
    private final List<Song> favoriteSongModels = new ArrayList<>();

    private String selectedFilter = "PLAYLISTS"; // "PLAYLISTS", "ARTISTS", "ALBUMS", "SONGS"
    private String selectedPlaylistSubFilter = "ALL"; // "ALL", "PERSONAL", "FAVORITE"

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
        selectFilter(selectedFilter);
        selectPlaylistSubFilter(selectedPlaylistSubFilter);
        fetchLibraryData();
    }

    private void setupRecyclerView() {
        libraryAdapter = new LibraryAdapter(this);
        binding.rvLibraryItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvLibraryItems.setAdapter(libraryAdapter);
    }

    private void setupFilterChips() {
        binding.chipPlaylists.setOnClickListener(v -> selectFilter("PLAYLISTS"));
        binding.chipArtists.setOnClickListener(v -> selectFilter("ARTISTS"));
        binding.chipAlbums.setOnClickListener(v -> selectFilter("ALBUMS"));
        binding.chipSongs.setOnClickListener(v -> selectFilter("SONGS"));

        binding.chipSubAll.setOnClickListener(v -> selectPlaylistSubFilter("ALL"));
        binding.chipSubPersonal.setOnClickListener(v -> selectPlaylistSubFilter("PERSONAL"));
        binding.chipSubFavorite.setOnClickListener(v -> selectPlaylistSubFilter("FAVORITE"));
    }

    private void selectFilter(String filter) {
        selectedFilter = filter;
        updateChipVisuals();
        if ("PLAYLISTS".equals(selectedFilter)) {
            binding.layoutPlaylistSubFilters.setVisibility(View.VISIBLE);
            updateSubFilterVisuals();
        } else {
            binding.layoutPlaylistSubFilters.setVisibility(View.GONE);
        }
        applyCurrentFilter();
    }

    private void selectPlaylistSubFilter(String subFilter) {
        selectedPlaylistSubFilter = subFilter;
        updateSubFilterVisuals();
        applyCurrentFilter();
    }

    private void updateChipVisuals() {
        if (getContext() == null || binding == null) return;
        TextView activeChip = binding.chipPlaylists;
        if ("ARTISTS".equals(selectedFilter)) {
            activeChip = binding.chipArtists;
        } else if ("ALBUMS".equals(selectedFilter)) {
            activeChip = binding.chipAlbums;
        } else if ("SONGS".equals(selectedFilter)) {
            activeChip = binding.chipSongs;
        }
        com.example.spotifyclone.ui.liquid.LiquidDropletHelper.selectPill(activeChip, true, true);
    }

    private void updateSubFilterVisuals() {
        if (getContext() == null || binding == null) return;
        boolean isAll = "ALL".equals(selectedPlaylistSubFilter);
        boolean isPersonal = "PERSONAL".equals(selectedPlaylistSubFilter);
        boolean isFavorite = "FAVORITE".equals(selectedPlaylistSubFilter);

        applySelectionButtonStyle(binding.chipSubAll, isAll);
        applySelectionButtonStyle(binding.chipSubPersonal, isPersonal);
        applySelectionButtonStyle(binding.chipSubFavorite, isFavorite);
    }

    private void applySelectionButtonStyle(TextView button, boolean isSelected) {
        if (button == null || getContext() == null) return;
        button.setBackgroundResource(isSelected ? R.drawable.bg_selection_button_active : R.drawable.bg_selection_button_inactive);
        button.setTextColor(ContextCompat.getColor(requireContext(), isSelected ? R.color.selection_active_text : R.color.selection_inactive_text));
        button.setTypeface(null, isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        if (isSelected) {
            button.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).withEndAction(() ->
                    button.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            ).start();
        }
    }

    private void applyCurrentFilter() {
        if (binding == null) return;
        List<LibraryItem> targetList;
        if ("ARTISTS".equals(selectedFilter)) {
            targetList = artistItems;
        } else if ("ALBUMS".equals(selectedFilter)) {
            targetList = albumItems;
        } else if ("SONGS".equals(selectedFilter)) {
            targetList = songItems;
        } else {
            if ("PERSONAL".equals(selectedPlaylistSubFilter)) {
                targetList = personalPlaylistItems;
            } else if ("FAVORITE".equals(selectedPlaylistSubFilter)) {
                targetList = favoritePlaylistItems;
            } else {
                targetList = allPlaylistItems;
            }
        }

        libraryAdapter.setItems(targetList);
        showEmptyState(targetList.isEmpty());
    }

    private void setupCreatePlaylistAction() {
        com.example.spotifyclone.ui.liquid.LiquidDropletHelper.attachDropletSpring(
                binding.btnCreatePlaylist,
                binding.btnExploreMusic,
                binding.chipPlaylists,
                binding.chipArtists,
                binding.chipAlbums,
                binding.chipSongs,
                binding.chipSubAll,
                binding.chipSubPersonal,
                binding.chipSubFavorite
        );
        binding.btnCreatePlaylist.setOnClickListener(v -> showCreatePlaylistDialog());
    }

    private void showCreatePlaylistDialog() {
        if (getContext() == null) return;
        CreatePlaylistBottomSheetDialog dialog = CreatePlaylistBottomSheetDialog.newInstance();
        dialog.setOnPlaylistCreatedListener(playlist -> {
            selectedFilter = "PLAYLISTS";
            updateChipVisuals();
            fetchLibraryData();
        });
        dialog.show(getChildFragmentManager(), "CreatePlaylistBottomSheetDialog");
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

        binding.shimmerLibraryContainer.setVisibility(View.VISIBLE);
        binding.shimmerLibraryContainer.startShimmer();
        binding.rvLibraryItems.setVisibility(View.GONE);
        binding.layoutLibraryEmpty.setVisibility(View.GONE);

        fetchPersonalPlaylists();
    }

    private void fetchPersonalPlaylists() {
        ApiClient.getApiService().getMyPlaylists(true).enqueue(new Callback<ApiResponse<List<Playlist>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Playlist>>> call, @NonNull Response<ApiResponse<List<Playlist>>> response) {
                personalPlaylistItems.clear();
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<Playlist> playlists = response.body().getData();
                    for (Playlist p : playlists) {
                        personalPlaylistItems.add(LibraryItem.createPlaylist(p));
                    }
                }
                fetchFavoritePlaylists();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Playlist>>> call, @NonNull Throwable t) {
                personalPlaylistItems.clear();
                fetchFavoritePlaylists();
            }
        });
    }

    private void fetchFavoritePlaylists() {
        ApiClient.getApiService().getFavoritePlaylists().enqueue(new Callback<ApiResponse<List<Playlist>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Playlist>>> call, @NonNull Response<ApiResponse<List<Playlist>>> response) {
                favoritePlaylistItems.clear();
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<Playlist> playlists = response.body().getData();
                    for (Playlist p : playlists) {
                        favoritePlaylistItems.add(LibraryItem.createPlaylist(p));
                    }
                }
                buildAllPlaylists();
                fetchFollowedArtists();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Playlist>>> call, @NonNull Throwable t) {
                favoritePlaylistItems.clear();
                buildAllPlaylists();
                fetchFollowedArtists();
            }
        });
    }

    private void buildAllPlaylists() {
        allPlaylistItems.clear();
        java.util.HashSet<String> seenIds = new java.util.HashSet<>();
        for (LibraryItem item : personalPlaylistItems) {
            allPlaylistItems.add(item);
            if (item.getId() != null) seenIds.add(item.getId());
        }
        for (LibraryItem item : favoritePlaylistItems) {
            if (item.getId() != null && !seenIds.contains(item.getId())) {
                allPlaylistItems.add(item);
                seenIds.add(item.getId());
            }
        }
    }

    private void fetchFollowedArtists() {
        ApiClient.getApiService().getLibraryArtists(true).enqueue(new Callback<ApiResponse<List<Artist>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Artist>>> call, @NonNull Response<ApiResponse<List<Artist>>> response) {
                artistItems.clear();
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<Artist> artists = response.body().getData();
                    for (Artist a : artists) {
                        artistItems.add(LibraryItem.createArtist(a));
                    }
                }
                fetchFavoriteAlbums();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Artist>>> call, @NonNull Throwable t) {
                artistItems.clear();
                fetchFavoriteAlbums();
            }
        });
    }

    private void fetchFavoriteAlbums() {
        ApiClient.getApiService().getFavoriteAlbums().enqueue(new Callback<ApiResponse<List<Album>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Album>>> call, @NonNull Response<ApiResponse<List<Album>>> response) {
                albumItems.clear();
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<Album> albums = response.body().getData();
                    for (Album a : albums) {
                        albumItems.add(LibraryItem.createAlbum(a));
                    }
                }
                fetchFavoriteSongs();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Album>>> call, @NonNull Throwable t) {
                albumItems.clear();
                fetchFavoriteSongs();
            }
        });
    }

    private void fetchFavoriteSongs() {
        ApiClient.getApiService().getFavorites().enqueue(new Callback<ApiResponse<FavoritesData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<FavoritesData>> call, @NonNull Response<ApiResponse<FavoritesData>> response) {
                songItems.clear();
                favoriteSongModels.clear();
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<Song> songs = response.body().getData().getSongs();
                    if (songs != null) {
                        for (Song s : songs) {
                            s.setLiked(true);
                            favoriteSongModels.add(s);
                            songItems.add(LibraryItem.createSong(s));
                        }
                    }
                }
                finishLoading();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<FavoritesData>> call, @NonNull Throwable t) {
                songItems.clear();
                favoriteSongModels.clear();
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
                if ("PERSONAL".equals(selectedPlaylistSubFilter)) {
                    binding.tvLibraryEmptyTitle.setText("Chưa có playlist cá nhân nào");
                    binding.tvLibraryEmptySubtitle.setText("Tạo playlist của riêng bạn để lưu các bài hát yêu thích.");
                    binding.btnExploreMusic.setText("Tạo playlist");
                    binding.btnExploreMusic.setOnClickListener(v -> showCreatePlaylistDialog());
                } else if ("FAVORITE".equals(selectedPlaylistSubFilter)) {
                    binding.tvLibraryEmptyTitle.setText("Chưa có playlist yêu thích nào");
                    binding.tvLibraryEmptySubtitle.setText("Khám phá các playlist công khai và nhấn biểu tượng trái tim để lưu lại.");
                    binding.btnExploreMusic.setText("Khám phá playlist");
                    binding.btnExploreMusic.setOnClickListener(v -> navigateToSearch());
                } else {
                    binding.tvLibraryEmptyTitle.setText("Chưa có playlist nào");
                    binding.tvLibraryEmptySubtitle.setText("Tạo playlist của riêng bạn hoặc lưu lại playlist bạn thích.");
                    binding.btnExploreMusic.setText("Tạo playlist");
                    binding.btnExploreMusic.setOnClickListener(v -> showCreatePlaylistDialog());
                }
            } else if ("ARTISTS".equals(selectedFilter)) {
                binding.tvLibraryEmptyTitle.setText("Chưa theo dõi nghệ sĩ nào");
                binding.tvLibraryEmptySubtitle.setText("Khám phá các nghệ sĩ hàng đầu và theo dõi họ để cập nhật bài hát mới.");
                binding.btnExploreMusic.setText("Khám phá nghệ sĩ");
                binding.btnExploreMusic.setOnClickListener(v -> navigateToSearch());
            } else if ("ALBUMS".equals(selectedFilter)) {
                binding.tvLibraryEmptyTitle.setText("Chưa có album yêu thích nào");
                binding.tvLibraryEmptySubtitle.setText("Khám phá các album nổi tiếng và lưu vào danh mục của bạn.");
                binding.btnExploreMusic.setText("Khám phá album");
                binding.btnExploreMusic.setOnClickListener(v -> navigateToSearch());
            } else if ("SONGS".equals(selectedFilter)) {
                binding.tvLibraryEmptyTitle.setText("Chưa có bài hát yêu thích nào");
                binding.tvLibraryEmptySubtitle.setText("Khám phá và thả tim bài hát đầu tiên để lưu lại tại đây.");
                binding.btnExploreMusic.setText("Khám phá bài hát");
                binding.btnExploreMusic.setOnClickListener(v -> navigateToSearch());
            }
        }
    }

    @Override
    public void onLibraryItemClick(LibraryItem item) {
        if (item == null) return;

        if (item.getType() == LibraryItem.Type.ARTIST && item.getArtist() != null) {
            Intent intent = new Intent(requireContext(), ArtistDetailActivity.class);
            intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_ID, item.getArtist().getId());
            intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_NAME, item.getArtist().getName());
            intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_AVATAR, item.getArtist().getAvatarUrl());
            intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_BIO, item.getArtist().getBio());
            intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_FOLLOWERS, item.getArtist().getFollowersCount());
            startActivity(intent);
            return;
        }

        if (item.getType() == LibraryItem.Type.SONG && item.getSong() != null) {
            Song targetSong = item.getSong();
            int index = favoriteSongModels.indexOf(targetSong);
            if (index == -1) index = 0;
            if (!favoriteSongModels.isEmpty()) {
                MusicPlayerManager.getInstance().playQueue(favoriteSongModels, index);
            }
            openPlayerActivity(targetSong);
            return;
        }

        if (item.getType() == LibraryItem.Type.PLAYLIST && item.isLocked()) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Danh sách phát bị khóa")
                    .setMessage("Danh sách phát \"" + item.getTitle() + "\" đã được người tạo đặt về chế độ riêng tư nên bạn không thể truy cập nội dung bài hát. Bạn có muốn bỏ thích để gỡ khỏi danh sách phát cá nhân hóa?")
                    .setPositiveButton("Bỏ thích", (dialog, which) -> {
                        ApiClient.getApiService().toggleFavoritePlaylist(item.getId()).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                            @Override
                            public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                                if (response.isSuccessful()) {
                                    Toast.makeText(requireContext(), "Đã gỡ danh sách phát khỏi thư viện", Toast.LENGTH_SHORT).show();
                                    fetchLibraryData();
                                } else {
                                    Toast.makeText(requireContext(), "Không thể cập nhật danh sách", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                                Toast.makeText(requireContext(), "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .setNegativeButton("Đóng", null)
                    .show();
            return;
        }

        Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
        intent.putExtra(PlaylistDetailActivity.EXTRA_ORIGIN_DESTINATION_ID, R.id.navigation_library);
        if (item.getType() == LibraryItem.Type.ALBUM) {
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE, PlaylistDetailActivity.TYPE_ALBUM);
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, item.getId());
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TITLE, item.getTitle());
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_COVER, item.getCoverUrl());
            if (item.getAlbum() != null) {
                intent.putExtra(PlaylistDetailActivity.EXTRA_IS_FAVORITE, item.getAlbum().isFavorite());
            }
        } else {
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE, PlaylistDetailActivity.TYPE_CUSTOM_PLAYLIST);
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, item.getId());
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TITLE, item.getTitle());
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_COVER, item.getCoverUrl());
            if (item.getPlaylist() != null) {
                intent.putExtra(PlaylistDetailActivity.EXTRA_IS_FAVORITE, item.getPlaylist().isFavorite());
            }
        }
        startActivity(intent);
    }

    private void openPlayerActivity(Song song) {
        if (getContext() == null || song == null) return;
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
