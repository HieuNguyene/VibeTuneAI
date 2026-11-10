package com.example.spotifyclone.presentation.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.spotifyclone.R;
import com.example.spotifyclone.data.local.SessionManager;
import com.example.spotifyclone.data.model.Album;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.Artist;
import com.example.spotifyclone.data.model.HomeFeedData;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.FragmentHomeBinding;
import com.example.spotifyclone.presentation.artist.ArtistDetailActivity;
import com.example.spotifyclone.presentation.main.MainActivity;
import com.example.spotifyclone.presentation.player.PlayerActivity;
import com.example.spotifyclone.presentation.playlist.PlaylistDetailActivity;

import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment implements SongCardAdapter.OnSongClickListener, RecentSongAdapter.OnRecentSongClickListener {

    private FragmentHomeBinding binding;
    private RecentSongAdapter recentSongAdapter;
    private SongCardAdapter madeForYouAdapter;
    private SongCardAdapter aiRecommendedAdapter;
    private AlbumCardAdapter popularAlbumsAdapter;
    private PlaylistCardAdapter featuredPlaylistsAdapter;
    private ArtistCardAdapter popularArtistAdapter;
    private final java.util.List<Song> allFeedSongs = new java.util.ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupGreeting();
        setupFilterChips();
        setupProfileClick();
        setupRecyclerViews();
        fetchHomeFeed();
    }

    private void setupRecyclerViews() {
        recentSongAdapter = new RecentSongAdapter(this);
        binding.rvRecentlyPlayed.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.rvRecentlyPlayed.setAdapter(recentSongAdapter);

        // Hiển thị ngay các bài hát đã nghe gần đây từ SessionManager nếu có
        if (getContext() != null) {
            List<Song> localRecents = SessionManager.getInstance(requireContext()).getRecentlyPlayedSongs();
            if (localRecents != null && !localRecents.isEmpty()) {
                recentSongAdapter.setSongs(localRecents);
            }
        }

        madeForYouAdapter = new SongCardAdapter(this);
        binding.rvMadeForYou.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvMadeForYou.setAdapter(madeForYouAdapter);

        aiRecommendedAdapter = new SongCardAdapter(this);
        binding.rvAiRecommended.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvAiRecommended.setAdapter(aiRecommendedAdapter);

        binding.layoutAiHeader.setOnClickListener(v -> {
            try {
                androidx.navigation.NavController navController = androidx.navigation.Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
                navController.navigate(R.id.navigation_ai);
            } catch (Exception ignored) {}
        });

        popularAlbumsAdapter = new AlbumCardAdapter(this::onAlbumClick);
        binding.rvPopularAlbums.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvPopularAlbums.setAdapter(popularAlbumsAdapter);

        featuredPlaylistsAdapter = new PlaylistCardAdapter(this::onPlaylistClick);
        binding.rvFeaturedPlaylists.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvFeaturedPlaylists.setAdapter(featuredPlaylistsAdapter);

        popularArtistAdapter = new ArtistCardAdapter(this::onArtistClick);
        binding.rvPopularArtists.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvPopularArtists.setAdapter(popularArtistAdapter);
    }

    private void onAlbumClick(Album album) {
        if (album == null) return;
        Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE, PlaylistDetailActivity.TYPE_ALBUM);
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, album.getId());
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TITLE, album.getTitle());
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_COVER, album.getCoverImageUrl());
        startActivity(intent);
    }

    private void onPlaylistClick(Playlist playlist) {
        if (playlist == null) return;
        Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE, PlaylistDetailActivity.TYPE_CUSTOM_PLAYLIST);
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlist.getId());
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TITLE, playlist.getTitle());
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_COVER, playlist.getCoverImageUrl());
        startActivity(intent);
    }

    private void onArtistClick(Artist artist) {
        if (artist == null) return;
        Intent intent = new Intent(requireContext(), ArtistDetailActivity.class);
        intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_ID, artist.getId());
        intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_NAME, artist.getName());
        intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_AVATAR, artist.getAvatarUrl());
        intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_BIO, artist.getBio());
        intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_FOLLOWERS, artist.getFollowersCount());
        startActivity(intent);
    }

    private void fetchHomeFeed() {
        showLoading(true);

        ApiClient.getApiService().getHomeFeed().enqueue(new Callback<ApiResponse<HomeFeedData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<HomeFeedData>> call, @NonNull Response<ApiResponse<HomeFeedData>> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    HomeFeedData feedData = response.body().getData();

                    List<Song> trending = feedData.getTrending();
                    List<Song> newReleases = feedData.getNewReleases();
                    List<Song> recents = feedData.getRecentlyPlayed();

                    allFeedSongs.clear();

                    // Cập nhật Nghe gần đây (Ưu tiên lịch sử thực tế của người dùng)
                    List<Song> localRecents = getContext() != null ? SessionManager.getInstance(requireContext()).getRecentlyPlayedSongs() : null;
                    if (localRecents != null && !localRecents.isEmpty()) {
                        recentSongAdapter.setSongs(localRecents);
                        for (Song s : localRecents) {
                            if (!allFeedSongs.contains(s)) {
                                allFeedSongs.add(s);
                            }
                        }
                    } else if (recents != null && !recents.isEmpty()) {
                        recentSongAdapter.setSongs(recents);
                        for (Song s : recents) {
                            if (!allFeedSongs.contains(s)) {
                                allFeedSongs.add(s);
                            }
                        }
                    } else if (trending != null && !trending.isEmpty()) {
                        recentSongAdapter.setSongs(trending);
                    }

                    if (trending != null && !trending.isEmpty()) {
                        for (Song s : trending) {
                            if (!allFeedSongs.contains(s)) {
                                allFeedSongs.add(s);
                            }
                        }
                        madeForYouAdapter.setSongs(trending);
                    }
                    if (newReleases != null && !newReleases.isEmpty()) {
                        for (Song s : newReleases) {
                            if (!allFeedSongs.contains(s)) {
                                allFeedSongs.add(s);
                            }
                        }
                        aiRecommendedAdapter.setSongs(newReleases);
                    } else if (trending != null && !trending.isEmpty()) {
                        aiRecommendedAdapter.setSongs(trending);
                    }

                    popularAlbumsAdapter.setAlbums(feedData.getPopularAlbums());
                    featuredPlaylistsAdapter.setPlaylists(feedData.getFeaturedPlaylists());
                    popularArtistAdapter.setArtists(feedData.getPopularArtists());
                } else {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Chưa có bài hát trong CSDL", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<HomeFeedData>> call, @NonNull Throwable t) {
                showLoading(false);
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Lỗi kết nối Backend: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onSongClick(Song song) {
        if (song == null) return;

        int index = allFeedSongs.indexOf(song);
        if (index >= 0) {
            com.example.spotifyclone.player.MusicPlayerManager.getInstance().playQueue(allFeedSongs, index);
        } else {
            com.example.spotifyclone.player.MusicPlayerManager.getInstance().playSong(song);
        }

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateMiniPlayerVisibility(true);
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
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.slide_in_bottom, R.anim.stay);
        }
    }

    private void setupProfileClick() {
        if (getContext() != null) {
            com.example.spotifyclone.data.model.User user =
                    com.example.spotifyclone.data.local.SessionManager.getInstance(requireContext()).getUser();
            if (user != null && user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                com.bumptech.glide.Glide.with(this)
                        .load(ApiClient.resolveMediaUrl(user.getAvatarUrl()))
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(binding.ivProfileAvatar);
            }
        }

        binding.ivProfileAvatar.setOnClickListener(v -> {
            com.example.spotifyclone.presentation.auth.ProfileBottomSheetDialog dialog =
                    com.example.spotifyclone.presentation.auth.ProfileBottomSheetDialog.newInstance();
            dialog.show(getParentFragmentManager(), "ProfileBottomSheet");
        });
    }

    private void setupGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 4 && hour < 12) {
            binding.tvGreeting.setText(R.string.greeting_morning);
        } else if (hour >= 12 && hour < 18) {
            binding.tvGreeting.setText(R.string.greeting_afternoon);
        } else {
            binding.tvGreeting.setText(R.string.greeting_evening);
        }
    }

    private void setupFilterChips() {
        binding.chipAll.setOnClickListener(v -> selectChip(binding.chipAll));
        binding.chipMusic.setOnClickListener(v -> selectChip(binding.chipMusic));
        binding.chipPodcasts.setOnClickListener(v -> selectChip(binding.chipPodcasts));

        binding.btnBackToMusic.setOnClickListener(v -> selectChip(binding.chipAll));
    }

    private void selectChip(View selectedChip) {
        binding.chipAll.setBackgroundResource(R.drawable.bg_pill_inactive);
        binding.chipAll.setTextColor(getResources().getColor(R.color.text_primary, null));

        binding.chipMusic.setBackgroundResource(R.drawable.bg_pill_inactive);
        binding.chipMusic.setTextColor(getResources().getColor(R.color.text_primary, null));

        binding.chipPodcasts.setBackgroundResource(R.drawable.bg_pill_inactive);
        binding.chipPodcasts.setTextColor(getResources().getColor(R.color.text_primary, null));

        selectedChip.setBackgroundResource(R.drawable.bg_pill_active);
        ((android.widget.TextView) selectedChip).setTextColor(getResources().getColor(R.color.black, null));

        if (selectedChip == binding.chipPodcasts) {
            binding.layoutHomeContent.setVisibility(View.GONE);
            binding.layoutPodcastsEmptyState.setVisibility(View.VISIBLE);
        } else {
            binding.layoutHomeContent.setVisibility(View.VISIBLE);
            binding.layoutPodcastsEmptyState.setVisibility(View.GONE);
        }
    }

    public void showLoading(boolean isLoading) {
        if (binding == null) return;
        if (isLoading) {
            binding.shimmerHomeContainer.setVisibility(View.VISIBLE);
            binding.shimmerHomeContainer.startShimmer();
            binding.layoutHomeContent.setVisibility(View.GONE);
            binding.layoutPodcastsEmptyState.setVisibility(View.GONE);
        } else {
            binding.shimmerHomeContainer.stopShimmer();
            binding.shimmerHomeContainer.setVisibility(View.GONE);
            if (binding.chipPodcasts.getTextColors().getDefaultColor() == getResources().getColor(R.color.black, null)) {
                binding.layoutHomeContent.setVisibility(View.GONE);
                binding.layoutPodcastsEmptyState.setVisibility(View.VISIBLE);
            } else {
                binding.layoutHomeContent.setVisibility(View.VISIBLE);
                binding.layoutPodcastsEmptyState.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && getContext() != null) {
            com.example.spotifyclone.data.model.User user =
                    com.example.spotifyclone.data.local.SessionManager.getInstance(requireContext()).getUser();
            if (user != null && user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                com.bumptech.glide.Glide.with(this)
                        .load(ApiClient.resolveMediaUrl(user.getAvatarUrl()))
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(binding.ivProfileAvatar);
            }

            // Tự động làm mới danh sách nghe gần đây khi quay lại từ màn hình nghe nhạc
            List<Song> localRecents = SessionManager.getInstance(requireContext()).getRecentlyPlayedSongs();
            if (localRecents != null && !localRecents.isEmpty() && recentSongAdapter != null) {
                recentSongAdapter.setSongs(localRecents);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
