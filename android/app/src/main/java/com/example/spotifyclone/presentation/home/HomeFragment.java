package com.example.spotifyclone.presentation.home;

import android.animation.AnimatorInflater;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.transition.TransitionManager;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.local.SessionManager;
import com.example.spotifyclone.data.model.Album;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.Artist;
import com.example.spotifyclone.data.model.FollowingMusicData;
import com.example.spotifyclone.data.model.Genre;
import com.example.spotifyclone.data.model.GenreSongResponse;
import com.example.spotifyclone.data.model.HomeFeedData;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.model.User;
import com.example.spotifyclone.data.model.NotificationItem;
import com.example.spotifyclone.data.model.UnreadCountData;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.data.remote.NotificationWebSocketManager;
import com.example.spotifyclone.databinding.FragmentHomeBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.example.spotifyclone.presentation.artist.ArtistDetailActivity;
import com.example.spotifyclone.presentation.auth.ProfileBottomSheetDialog;
import com.example.spotifyclone.presentation.main.MainActivity;
import com.example.spotifyclone.presentation.notification.NotificationActivity;
import com.example.spotifyclone.presentation.player.PlayerActivity;
import com.example.spotifyclone.presentation.playlist.PlaylistDetailActivity;
import com.example.spotifyclone.presentation.search.SearchResultAdapter;

import com.example.spotifyclone.ui.liquid.LiquidDropletHelper;
import com.example.spotifyclone.ui.liquid.LiquidSelectionMenu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment implements
        SongCardAdapter.OnSongClickListener,
        RecentSongAdapter.OnRecentSongClickListener,
        SearchResultAdapter.OnSearchSongClickListener {

    private FragmentHomeBinding binding;

    // Adapters for "Tất cả" (9 curated sections)
    private RecentSongAdapter recentSongAdapter;
    private AlbumCardAdapter likedSongAlbumsAdapter;
    private PlaylistCardAdapter recentPlaylistsAdapter;
    private AlbumCardAdapter popularAlbumsAdapter;
    private SongCardAdapter topMixesAdapter;
    private PlaylistCardAdapter popularRadiosAdapter;
    private SongCardAdapter recommendedTodayAdapter;
    private SongCardAdapter listenAgainAdapter;
    private ArtistPortraitAdapter bestOfArtistsAdapter;

    // Adapters for "Âm nhạc" (Genre dropdown + Following) and dedicated tabs
    private ArtistPortraitAdapter musicFollowingArtistsAdapter;
    private SearchResultAdapter followingSongsAdapter;
    private SearchResultAdapter genreSongsAdapter;
    private ArtistPortraitAdapter allArtistsAdapter;
    private PlaylistCardAdapter allPlaylistsAdapter;
    private AlbumCardAdapter allAlbumsAdapter;

    private final List<Song> allFeedSongs = new ArrayList<>();
    private final List<Genre> genresList = new ArrayList<>();
    private final List<Song> genreSongList = new ArrayList<>();
    private final List<Song> followingSongList = new ArrayList<>();

    private String selectedGenreId = null;
    private String selectedGenreName = "";
    private String currentGenreSort = "popular";
    private String currentFollowingSort = "newest";
    private boolean isGenreDropdownOpen = false;
    private boolean isMusicFollowingMode = false;
    private View currentSelectedMainChip = null;

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
        setupMusicTabControls();
        setupProfileClick();
        setupNotificationBell();
        setupRecyclerViews();
        setupConnectionErrorButtons();
        fetchHomeFeed(true);
    }

    private void setupConnectionErrorButtons() {
        if (binding == null) return;
        binding.btnRetryHome.setOnClickListener(v -> fetchHomeFeed(true));
        binding.btnChangeServerIp.setOnClickListener(v -> {
            com.example.spotifyclone.util.ServerConfigDialogHelper.show(requireActivity(), newUrl -> {
                fetchHomeFeed(true);
            });
        });
    }

    private void setupRecyclerViews() {
        // Tab 1: 9 sections of "Tất cả"
        recentSongAdapter = new RecentSongAdapter(this);
        binding.rvRecentlyPlayed.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.rvRecentlyPlayed.setAdapter(recentSongAdapter);

        if (getContext() != null) {
            List<Song> localRecents = SessionManager.getInstance(requireContext()).getRecentlyPlayedSongs();
            if (localRecents != null && !localRecents.isEmpty()) {
                recentSongAdapter.setSongs(localRecents);
            }
        }

        likedSongAlbumsAdapter = new AlbumCardAdapter(this::onAlbumClick);
        binding.rvLikedSongAlbums.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvLikedSongAlbums.setAdapter(likedSongAlbumsAdapter);

        recentPlaylistsAdapter = new PlaylistCardAdapter(this::onPlaylistClick);
        binding.rvRecentPlaylists.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvRecentPlaylists.setAdapter(recentPlaylistsAdapter);

        popularAlbumsAdapter = new AlbumCardAdapter(this::onAlbumClick);
        binding.rvPopularAlbums.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvPopularAlbums.setAdapter(popularAlbumsAdapter);

        topMixesAdapter = new SongCardAdapter(this);
        binding.rvTopMixes.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvTopMixes.setAdapter(topMixesAdapter);

        popularRadiosAdapter = new PlaylistCardAdapter(this::onPlaylistClick);
        binding.rvPopularRadios.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvPopularRadios.setAdapter(popularRadiosAdapter);

        recommendedTodayAdapter = new SongCardAdapter(this);
        binding.rvRecommendedToday.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvRecommendedToday.setAdapter(recommendedTodayAdapter);

        binding.layoutRecommendedTodayHeader.setOnClickListener(v -> {
            try {
                androidx.navigation.NavController navController = androidx.navigation.Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
                navController.navigate(R.id.navigation_ai);
            } catch (Exception ignored) {}
        });

        listenAgainAdapter = new SongCardAdapter(this);
        binding.rvListenAgain.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvListenAgain.setAdapter(listenAgainAdapter);

        bestOfArtistsAdapter = new ArtistPortraitAdapter(this::onArtistClick, false);
        bestOfArtistsAdapter.setShowMixSubtitle(true);
        binding.rvBestOfArtists.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvBestOfArtists.setAdapter(bestOfArtistsAdapter);

        // Tab 2: "Âm nhạc" (Following + Genre songs)
        musicFollowingArtistsAdapter = new ArtistPortraitAdapter(this::onArtistClick, false);
        binding.rvMusicFollowingArtists.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvMusicFollowingArtists.setAdapter(musicFollowingArtistsAdapter);

        followingSongsAdapter = new SearchResultAdapter(this::onSongClick);
        binding.rvFollowingSongs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvFollowingSongs.setAdapter(followingSongsAdapter);

        genreSongsAdapter = new SearchResultAdapter(this::onSongClick);
        binding.rvGenreSongs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvGenreSongs.setAdapter(genreSongsAdapter);

        // Dedicated Tabs 3, 4, 5
        allArtistsAdapter = new ArtistPortraitAdapter(this::onArtistClick, true);
        allArtistsAdapter.setOnArtistsFilteredListener((visibleCount, totalCount) -> {
            if (binding == null) return;
            binding.tvArtistsCountBadge.setText(visibleCount + " nghệ sĩ");
            binding.tvArtistsEmpty.setVisibility(visibleCount == 0 ? View.VISIBLE : View.GONE);
        });
        binding.rvAllArtists.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.rvAllArtists.setAdapter(allArtistsAdapter);

        allPlaylistsAdapter = new PlaylistCardAdapter(this::onPlaylistClick);
        binding.rvAllPlaylists.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.rvAllPlaylists.setAdapter(allPlaylistsAdapter);
        applySymmetricTwoColSpacing(binding.rvAllPlaylists, 148);

        allAlbumsAdapter = new AlbumCardAdapter(this::onAlbumClick);
        binding.rvAllAlbums.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.rvAllAlbums.setAdapter(allAlbumsAdapter);
        applySymmetricTwoColSpacing(binding.rvAllAlbums, 152);
    }

    private void onAlbumClick(Album album) {
        if (album == null || getContext() == null) return;
        Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE, PlaylistDetailActivity.TYPE_ALBUM);
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, album.getId());
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TITLE, album.getTitle());
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_COVER, album.getCoverImageUrl());
        intent.putExtra(PlaylistDetailActivity.EXTRA_ORIGIN_DESTINATION_ID, R.id.navigation_home);
        startActivity(intent);
    }

    private void onPlaylistClick(Playlist playlist) {
        if (playlist == null || getContext() == null) return;
        Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE, PlaylistDetailActivity.TYPE_CUSTOM_PLAYLIST);
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlist.getId());
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TITLE, playlist.getTitle());
        intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_COVER, playlist.getCoverImageUrl());
        intent.putExtra(PlaylistDetailActivity.EXTRA_ORIGIN_DESTINATION_ID, R.id.navigation_home);
        startActivity(intent);
    }

    private void onArtistClick(Artist artist) {
        if (artist == null || getContext() == null) return;
        Intent intent = new Intent(requireContext(), ArtistDetailActivity.class);
        intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_ID, artist.getId());
        intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_NAME, artist.getName());
        intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_AVATAR, artist.getAvatarUrl());
        intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_BIO, artist.getBio());
        intent.putExtra(ArtistDetailActivity.EXTRA_ARTIST_FOLLOWERS, artist.getFollowersCount());
        startActivity(intent);
    }

    private void fetchHomeFeed(boolean showShimmer) {
        if (showShimmer) {
            showLoading(true);
        }
        if (binding != null) {
            binding.layoutConnectionError.setVisibility(View.GONE);
        }

        ApiClient.getApiService().getHomeFeed().enqueue(new Callback<ApiResponse<HomeFeedData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<HomeFeedData>> call, @NonNull Response<ApiResponse<HomeFeedData>> response) {
                if (!isAdded() || binding == null) return;
                if (showShimmer) {
                    showLoading(false);
                }

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    binding.layoutConnectionError.setVisibility(View.GONE);
                    HomeFeedData feedData = response.body().getData();
                    bindAllTabFeed(feedData);
                } else {
                    binding.layoutConnectionError.setVisibility(View.VISIBLE);
                    binding.tvCurrentServerUrl.setText("Máy chủ: " + ApiClient.getBaseUrl());
                    binding.layoutHomeContent.setVisibility(View.GONE);
                    if (getContext() != null && showShimmer) {
                        Toast.makeText(getContext(), "Chưa có bài hát trong CSDL", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<HomeFeedData>> call, @NonNull Throwable t) {
                if (!isAdded() || binding == null) return;
                if (showShimmer) {
                    showLoading(false);
                }
                binding.layoutHomeContent.setVisibility(View.GONE);
                binding.layoutConnectionError.setVisibility(View.VISIBLE);
                binding.tvCurrentServerUrl.setText("Máy chủ: " + ApiClient.getBaseUrl());
                binding.tvErrorMessage.setText("Không thể kết nối tới máy chủ (" + t.getMessage() + ").\nVui lòng đảm bảo điện thoại và máy tính kết nối CÙNG mạng Wi-Fi và máy chủ đang chạy.");
                if (getContext() != null && showShimmer) {
                    Toast.makeText(getContext(), "Lỗi kết nối Backend: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void bindAllTabFeed(@NonNull HomeFeedData feedData) {
        if (binding == null) return;
        allFeedSongs.clear();

        List<Song> trending = feedData.getTrending();
        List<Song> serverRecents = feedData.getRecentlyPlayed();
        if (getContext() != null) {
            java.util.List<Song> freshCatalog = new java.util.ArrayList<>();
            if (trending != null) freshCatalog.addAll(trending);
            if (feedData.getNewReleases() != null) freshCatalog.addAll(feedData.getNewReleases());
            if (feedData.getMadeForYou() != null) freshCatalog.addAll(feedData.getMadeForYou());
            if (serverRecents != null) freshCatalog.addAll(serverRecents);
            if (feedData.getTopMixes() != null) freshCatalog.addAll(feedData.getTopMixes());
            if (feedData.getRecommendedToday() != null) freshCatalog.addAll(feedData.getRecommendedToday());
            if (feedData.getListenAgain() != null) freshCatalog.addAll(feedData.getListenAgain());
            SessionManager.getInstance(requireContext()).syncRecentlyPlayedMetadata(freshCatalog);
        }
        List<Song> localRecents = getContext() != null ? SessionManager.getInstance(requireContext()).getRecentlyPlayedSongs() : null;

        // 1. Nội dung nghe gần đây
        List<Song> recentToDisplay = (localRecents != null && !localRecents.isEmpty())
                ? localRecents
                : (serverRecents != null && !serverRecents.isEmpty() ? serverRecents : trending);
        recentSongAdapter.setSongs(recentToDisplay);
        appendUniqueSongs(recentToDisplay);
        toggleSectionVisibility(binding.tvRecentlyPlayedHeader, binding.rvRecentlyPlayed, recentToDisplay.size());

        // 2. Album có bài hát bạn thích
        List<Album> likedAlbums = feedData.getLikedSongAlbums();
        if (likedAlbums.isEmpty()) {
            likedAlbums = feedData.getPopularAlbums();
        }
        likedSongAlbumsAdapter.setAlbums(likedAlbums);
        toggleSectionVisibility(binding.tvLikedSongAlbumsHeader, binding.rvLikedSongAlbums, likedAlbums.size());

        // 3. Danh sách phát gần đây
        List<Playlist> recentPls = feedData.getRecentPlaylists();
        recentPlaylistsAdapter.setPlaylists(recentPls);
        toggleSectionVisibility(binding.tvRecentPlaylistsHeader, binding.rvRecentPlaylists, recentPls.size());

        // 4. Album và đĩa đơn nổi tiếng
        List<Album> popAlbums = feedData.getPopularAlbums();
        popularAlbumsAdapter.setAlbums(popAlbums);
        toggleSectionVisibility(binding.tvPopularAlbumsHeader, binding.rvPopularAlbums, popAlbums.size());

        // 5. Tuyển tập hàng đầu của bạn
        List<Song> topMixes = feedData.getTopMixes();
        if (topMixes.isEmpty()) topMixes = trending;
        topMixesAdapter.setSongs(topMixes);
        appendUniqueSongs(topMixes);
        toggleSectionVisibility(binding.tvTopMixesHeader, binding.rvTopMixes, topMixes.size());

        // 6. Radio phổ biến
        List<Playlist> radios = feedData.getPopularRadios();
        popularRadiosAdapter.setPlaylists(radios);
        toggleSectionVisibility(binding.tvPopularRadiosHeader, binding.rvPopularRadios, radios.size());

        // 7. Đề xuất cho hôm nay
        List<Song> recToday = feedData.getRecommendedToday();
        if (recToday.isEmpty()) recToday = feedData.getNewReleases();
        recommendedTodayAdapter.setSongs(recToday);
        appendUniqueSongs(recToday);
        toggleSectionVisibility(binding.layoutRecommendedTodayHeader, binding.rvRecommendedToday, recToday.size());

        // 8. Nghe lại
        List<Song> listenAgain = feedData.getListenAgain();
        listenAgainAdapter.setSongs(listenAgain);
        appendUniqueSongs(listenAgain);
        toggleSectionVisibility(binding.tvListenAgainHeader, binding.rvListenAgain, listenAgain.size());

        // 9. Tuyển tập nhạc hay nhất của các nghệ sĩ
        List<Artist> bestArtists = feedData.getBestOfArtists();
        bestOfArtistsAdapter.setArtists(bestArtists);
        toggleSectionVisibility(binding.tvBestOfArtistsHeader, binding.rvBestOfArtists, bestArtists.size());
    }

    private void appendUniqueSongs(List<Song> songs) {
        if (songs == null) return;
        for (Song s : songs) {
            if (s != null && !allFeedSongs.contains(s)) {
                allFeedSongs.add(s);
            }
        }
    }

    private void toggleSectionVisibility(View header, View list, int count) {
        int vis = count > 0 ? View.VISIBLE : View.GONE;
        header.setVisibility(vis);
        list.setVisibility(vis);
    }

    @Override
    public void onSongClick(Song song) {
        if (song == null || getContext() == null) return;

        List<Song> currentQueue;
        if (currentSelectedMainChip == binding.chipMusic && isMusicFollowingMode && followingSongList.contains(song)) {
            currentQueue = followingSongList;
        } else if (currentSelectedMainChip == binding.chipMusic && genreSongList.contains(song)) {
            currentQueue = genreSongList;
        } else {
            currentQueue = allFeedSongs;
        }

        int index = currentQueue.indexOf(song);
        if (index >= 0) {
            MusicPlayerManager.getInstance().playQueue(currentQueue, index);
        } else {
            MusicPlayerManager.getInstance().playSong(song);
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
            User user = SessionManager.getInstance(requireContext()).getUser();
            if (user != null && user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                Glide.with(this)
                        .load(ApiClient.resolveMediaUrl(user.getAvatarUrl()))
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(binding.ivProfileAvatar);
            }
        }

        binding.ivProfileAvatar.setOnClickListener(v -> {
            ProfileBottomSheetDialog dialog = ProfileBottomSheetDialog.newInstance();
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
        currentSelectedMainChip = binding.chipAll;
        com.example.spotifyclone.ui.liquid.LiquidDropletHelper.selectPill(binding.chipAll, true, false);
        com.example.spotifyclone.ui.liquid.LiquidDropletHelper.attachDropletSpring(
                binding.chipAll,
                binding.chipMusic,
                binding.chipArtists,
                binding.chipPlaylists,
                binding.chipAlbums
        );

        binding.chipAll.setOnClickListener(v -> selectMainChip(binding.chipAll));
        binding.chipMusic.setOnClickListener(v -> selectMainChip(binding.chipMusic));
        binding.chipArtists.setOnClickListener(v -> selectMainChip(binding.chipArtists));
        binding.chipPlaylists.setOnClickListener(v -> selectMainChip(binding.chipPlaylists));
        binding.chipAlbums.setOnClickListener(v -> selectMainChip(binding.chipAlbums));
    }

    private void setupMusicTabControls() {
        LiquidDropletHelper.applyDirectPillStyle(binding.chipMusicFollowing, isMusicFollowingMode, true);
        LiquidDropletHelper.attachDropletSpring(
                binding.btnGenreDropdown,
                binding.chipMusicFollowing,
                binding.btnDiscoverArtistsInMusic,
                binding.btnGenreSortSelection,
                binding.btnFollowingSortSelection,
                binding.btnArtistFilterSelection
        );
        binding.btnGenreDropdown.setOnClickListener(v -> toggleGenreDropdown(!isGenreDropdownOpen));

        binding.chipMusicFollowing.setOnClickListener(v -> setMusicFollowingMode(!isMusicFollowingMode));
        binding.tvMusicFollowingAction.setOnClickListener(v -> setMusicFollowingMode(!isMusicFollowingMode));
        binding.btnDiscoverArtistsInMusic.setOnClickListener(v -> selectMainChip(binding.chipArtists));

        LiquidSelectionMenu.setupSelectionButton(
                binding.btnGenreSortSelection,
                binding.tvGenreSortSelected,
                binding.ivGenreSortChevron,
                "Sắp xếp: ",
                Arrays.asList(
                        new LiquidSelectionMenu.Item("popular", "Nhiều người nghe"),
                        new LiquidSelectionMenu.Item("user_plays", "Lượt nghe cá nhân"),
                        new LiquidSelectionMenu.Item("recommended", "Gợi ý cho bạn"),
                        new LiquidSelectionMenu.Item("az", "Tên bài hát (A-Z)")
                ),
                currentGenreSort,
                item -> selectGenreSort(item.id)
        );

        LiquidSelectionMenu.setupSelectionButton(
                binding.btnFollowingSortSelection,
                binding.tvFollowingSortSelected,
                binding.ivFollowingSortChevron,
                "Sắp xếp: ",
                Arrays.asList(
                        new LiquidSelectionMenu.Item("newest", "Mới nhất"),
                        new LiquidSelectionMenu.Item("popular", "Phổ biến"),
                        new LiquidSelectionMenu.Item("az", "Tên bài hát (A-Z)")
                ),
                currentFollowingSort,
                item -> selectFollowingSort(item.id)
        );

        LiquidSelectionMenu.setupSelectionButton(
                binding.btnArtistFilterSelection,
                binding.tvArtistFilterSelected,
                binding.ivArtistFilterChevron,
                "Bộ lọc: ",
                Arrays.asList(
                        new LiquidSelectionMenu.Item(String.valueOf(ArtistPortraitAdapter.FILTER_ALL), "Tất cả"),
                        new LiquidSelectionMenu.Item(String.valueOf(ArtistPortraitAdapter.FILTER_WITH_SONGS), "Có bài hát"),
                        new LiquidSelectionMenu.Item(String.valueOf(ArtistPortraitAdapter.FILTER_FOLLOWING), "Đang theo dõi"),
                        new LiquidSelectionMenu.Item(String.valueOf(ArtistPortraitAdapter.FILTER_AZ), "A - Z")
                ),
                String.valueOf(ArtistPortraitAdapter.FILTER_ALL),
                item -> selectArtistFilter(Integer.parseInt(item.id))
        );
    }

    private void selectArtistFilter(int filterMode) {
        if (allArtistsAdapter != null) {
            allArtistsAdapter.setFilterMode(filterMode);
        }
    }

    private void applySymmetricTwoColSpacing(androidx.recyclerview.widget.RecyclerView rv, int cardWidthDp) {
        float density = getResources().getDisplayMetrics().density;
        int cardWidthPx = Math.round(cardWidthDp * density);
        int vertGapPx = Math.round(14 * density);

        rv.addOnChildAttachStateChangeListener(new androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                    if (mlp.getMarginEnd() != 0) {
                        mlp.setMarginEnd(0);
                        view.setLayoutParams(mlp);
                    }
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {}
        });

        rv.addItemDecoration(new androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
                                       @NonNull androidx.recyclerview.widget.RecyclerView parent,
                                       @NonNull androidx.recyclerview.widget.RecyclerView.State state) {
                int pos = parent.getChildAdapterPosition(view);
                if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return;
                int totalW = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
                if (totalW > cardWidthPx * 2) {
                    int s = (totalW - cardWidthPx * 2) / 3;
                    int col = pos % 2;
                    if (col == 0) {
                        outRect.left = s;
                    } else {
                        outRect.left = s / 2;
                    }
                }
                outRect.bottom = vertGapPx;
            }
        });
    }

    private void applyPillActiveState(TextView pill, boolean isActive) {
        if (pill == null || getContext() == null) return;
        if (pill.getParent() instanceof com.example.spotifyclone.ui.liquid.LiquidPillRow) {
            if (isActive) {
                com.example.spotifyclone.ui.liquid.LiquidDropletHelper.selectPill(pill, true, true);
            }
            pill.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
        } else {
            com.example.spotifyclone.ui.liquid.LiquidDropletHelper.applyDirectPillStyle(pill, isActive, true);
            pill.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void selectMainChip(View selectedChip) {
        currentSelectedMainChip = selectedChip;

        TextView[] chips = new TextView[]{
                binding.chipAll,
                binding.chipMusic,
                binding.chipArtists,
                binding.chipPlaylists,
                binding.chipAlbums
        };
        for (TextView chip : chips) {
            applyPillActiveState(chip, chip == selectedChip);
        }

        binding.layoutHomeContent.setVisibility(selectedChip == binding.chipAll ? View.VISIBLE : View.GONE);
        binding.layoutMusicContainer.setVisibility(selectedChip == binding.chipMusic ? View.VISIBLE : View.GONE);
        binding.layoutArtistsContainer.setVisibility(selectedChip == binding.chipArtists ? View.VISIBLE : View.GONE);
        binding.layoutPlaylistsContainer.setVisibility(selectedChip == binding.chipPlaylists ? View.VISIBLE : View.GONE);
        binding.layoutAlbumsContainer.setVisibility(selectedChip == binding.chipAlbums ? View.VISIBLE : View.GONE);

        if (selectedChip != binding.chipMusic && isGenreDropdownOpen) {
            toggleGenreDropdown(false);
        }

        if (selectedChip == binding.chipAll) {
            fetchHomeFeed(true);
        } else if (selectedChip == binding.chipMusic) {
            loadGenresAndMusic();
        } else if (selectedChip == binding.chipArtists) {
            loadAllArtists();
        } else if (selectedChip == binding.chipPlaylists) {
            loadAllPlaylists();
        } else if (selectedChip == binding.chipAlbums) {
            loadAllAlbums();
        }
    }

    private void toggleGenreDropdown(boolean open) {
        if (binding == null) return;
        isGenreDropdownOpen = open;
        try {
            TransitionManager.beginDelayedTransition(binding.layoutMusicContainer);
        } catch (Exception ignored) {}
        binding.cardGenreDropdown.setVisibility(open ? View.VISIBLE : View.GONE);
        binding.ivGenreDropdownChevron.animate()
                .rotation(open ? 180f : 0f)
                .setDuration(180)
                .start();
    }

    private void setMusicFollowingMode(boolean followingMode) {
        if (binding == null) return;
        isMusicFollowingMode = followingMode;

        if (followingMode && isGenreDropdownOpen) {
            toggleGenreDropdown(false);
        }

        applyPillActiveState(binding.chipMusicFollowing, followingMode);
        binding.chipMusicFollowing.setTypeface(null, Typeface.BOLD);

        binding.tvMusicFollowingAction.setText(followingMode ? "Về thể loại" : "Xem tất cả");
        binding.layoutGenreSongsContent.setVisibility(followingMode ? View.GONE : View.VISIBLE);
        binding.layoutFollowingSongsContent.setVisibility(followingMode ? View.VISIBLE : View.GONE);
    }

    private void loadGenresAndMusic() {
        loadFollowedArtistsMusic();

        ApiClient.getApiService().getGenres().enqueue(new Callback<ApiResponse<List<Genre>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Genre>>> call, @NonNull Response<ApiResponse<List<Genre>>> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    genresList.clear();
                    genresList.addAll(response.body().getData());

                    boolean hasSelected = false;
                    if (selectedGenreId != null) {
                        for (Genre g : genresList) {
                            if (selectedGenreId.equals(g.getId())) {
                                selectedGenreName = g.getName();
                                hasSelected = true;
                                break;
                            }
                        }
                    }

                    if (!hasSelected) {
                        if (!genresList.isEmpty()) {
                            selectedGenreId = genresList.get(0).getId();
                            selectedGenreName = genresList.get(0).getName();
                        } else {
                            selectedGenreId = null;
                            selectedGenreName = "";
                        }
                    }

                    renderGenreChips();
                    if (selectedGenreId != null) {
                        loadGenreSongs(selectedGenreId, selectedGenreName);
                    } else {
                        genreSongList.clear();
                        genreSongsAdapter.setSongs(genreSongList);
                        binding.tvGenreSongsHeader.setText("Chưa có thể loại nào");
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Genre>>> call, @NonNull Throwable t) {
                if (!isAdded() || getContext() == null) return;
                if (!genresList.isEmpty()) {
                    renderGenreChips();
                    if (selectedGenreId != null) {
                        loadGenreSongs(selectedGenreId, selectedGenreName);
                    }
                } else {
                    Toast.makeText(getContext(), "Không thể tải danh sách thể loại: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadFollowedArtistsMusic() {
        ApiClient.getApiService().getFollowedArtistsMusic(currentFollowingSort).enqueue(new Callback<ApiResponse<FollowingMusicData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<FollowingMusicData>> call, @NonNull Response<ApiResponse<FollowingMusicData>> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    FollowingMusicData data = response.body().getData();
                    List<Artist> followedArtists = data.getArtists();
                    List<Song> songs = data.getSongs();

                    musicFollowingArtistsAdapter.setArtists(followedArtists);
                    followingSongList.clear();
                    followingSongList.addAll(songs);
                    followingSongsAdapter.setSongs(followingSongList);

                    int artistCount = followedArtists.size();
                    binding.chipMusicFollowing.setText(artistCount > 0
                            ? ("Đang theo dõi (" + artistCount + ")")
                            : "Đang theo dõi");

                    if (artistCount == 0) {
                        binding.rvMusicFollowingArtists.setVisibility(View.GONE);
                        binding.layoutFollowingEmptyPrompt.setVisibility(View.VISIBLE);
                        binding.tvMusicFollowingAction.setVisibility(View.GONE);
                        binding.tvFollowingSongsHeader.setText("Chưa theo dõi nghệ sĩ nào");
                    } else {
                        binding.rvMusicFollowingArtists.setVisibility(View.VISIBLE);
                        binding.layoutFollowingEmptyPrompt.setVisibility(View.GONE);
                        binding.tvMusicFollowingAction.setVisibility(View.VISIBLE);
                        binding.tvFollowingSongsHeader.setText("Bài hát từ " + artistCount + " nghệ sĩ đang theo dõi");
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<FollowingMusicData>> call, @NonNull Throwable t) {
                // Keep UI responsive without intrusive toast on passive sync
            }
        });
    }

    private void renderGenreChips() {
        if (binding == null || getContext() == null) return;
        binding.layoutGenreChips.removeAllViews();

        binding.tvGenreDropdownCount.setText(genresList.size() + " thể loại");
        binding.tvSelectedGenreDropdown.setText(!selectedGenreName.isEmpty()
                ? ("Thể loại: " + selectedGenreName)
                : "Chọn thể loại");

        float density = getResources().getDisplayMetrics().density;
        int rowSpacing = (int) (8 * density);
        int colGap = (int) (8 * density);
        int pillHeight = (int) (40 * density);
        int hPad = (int) (12 * density);
        int dotSize = (int) (8 * density);

        for (int i = 0; i < genresList.size(); i += 2) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.bottomMargin = rowSpacing;
            row.setLayoutParams(rowParams);

            row.addView(buildDropdownGenreItem(genresList.get(i), pillHeight, hPad, dotSize, true, colGap));

            if (i + 1 < genresList.size()) {
                row.addView(buildDropdownGenreItem(genresList.get(i + 1), pillHeight, hPad, dotSize, false, colGap));
            } else {
                View spacer = new View(requireContext());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, pillHeight, 1f));
                row.addView(spacer);
            }

            binding.layoutGenreChips.addView(row);
        }
    }

    private View buildDropdownGenreItem(Genre genre, int heightPx, int hPadPx, int dotSizePx, boolean addEndMargin, int gapPx) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(hPadPx, 0, hPadPx, 0);
        container.setClickable(true);
        container.setFocusable(true);
        container.setElevation(4 * getResources().getDisplayMetrics().density);
        try {
            container.setStateListAnimator(AnimatorInflater.loadStateListAnimator(requireContext(), R.animator.btn_tactile_raise));
        } catch (Exception ignored) {}

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, heightPx, 1f);
        if (addEndMargin) {
            lp.setMarginEnd(gapPx);
        }
        container.setLayoutParams(lp);

        boolean isSelected = genre.getId() != null && genre.getId().equals(selectedGenreId);
        container.setBackgroundResource(isSelected ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);

        // Color swatch dot representing the genre
        View dot = new View(requireContext());
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSizePx, dotSizePx);
        dotLp.setMarginEnd((int) (8 * getResources().getDisplayMetrics().density));
        dot.setLayoutParams(dotLp);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        int swatchColor = getResources().getColor(R.color.spotify_green, null);
        if (isSelected) {
            swatchColor = Color.WHITE;
        } else if (genre.getColorCode() != null && !genre.getColorCode().trim().isEmpty()) {
            try {
                swatchColor = Color.parseColor(genre.getColorCode().trim());
            } catch (IllegalArgumentException ignored) {}
        }
        dotBg.setColor(swatchColor);
        dot.setBackground(dotBg);

        TextView tv = new TextView(requireContext());
        tv.setText(genre.getName());
        tv.setTextSize(12);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setTextColor(getResources().getColor(isSelected ? R.color.white : R.color.liquid_text_inactive, null));
        tv.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        container.addView(dot);
        container.addView(tv);
        com.example.spotifyclone.ui.liquid.LiquidDropletHelper.attachDropletSpring(container);

        container.setOnClickListener(v -> {
            selectedGenreId = genre.getId();
            selectedGenreName = genre.getName();
            if (isMusicFollowingMode) {
                setMusicFollowingMode(false);
            }
            renderGenreChips();
            toggleGenreDropdown(false);
            loadGenreSongs(selectedGenreId, selectedGenreName);
        });

        return container;
    }

    private void selectGenreSort(String sort) {
        if (currentGenreSort.equals(sort)) return;
        currentGenreSort = sort;
        if (selectedGenreId != null) {
            loadGenreSongs(selectedGenreId, selectedGenreName);
        }
    }

    private void selectFollowingSort(String sort) {
        if (currentFollowingSort.equals(sort)) return;
        currentFollowingSort = sort;
        loadFollowedArtistsMusic();
    }

    private void loadGenreSongs(String genreId, String genreName) {
        if (genreId == null || binding == null) return;
        binding.tvGenreSongsHeader.setText("Bài hát thể loại: " + genreName);

        ApiClient.getApiService().getSongsByGenre(genreId, currentGenreSort).enqueue(new Callback<ApiResponse<GenreSongResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<GenreSongResponse>> call, @NonNull Response<ApiResponse<GenreSongResponse>> response) {
                if (!isAdded() || binding == null) return;
                genreSongList.clear();
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<Song> songs = response.body().getData().getSongs();
                    if (songs != null) {
                        genreSongList.addAll(songs);
                    }
                }
                genreSongsAdapter.setSongs(genreSongList);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<GenreSongResponse>> call, @NonNull Throwable t) {
                if (!isAdded() || getContext() == null) return;
                Toast.makeText(getContext(), "Lỗi tải bài hát: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAllArtists() {
        ApiClient.getApiService().getLibraryArtists(false).enqueue(new Callback<ApiResponse<List<Artist>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Artist>>> call, @NonNull Response<ApiResponse<List<Artist>>> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    allArtistsAdapter.setArtists(response.body().getData());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Artist>>> call, @NonNull Throwable t) {
                if (!isAdded() || getContext() == null) return;
                Toast.makeText(getContext(), "Lỗi tải nghệ sĩ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAllPlaylists() {
        ApiClient.getApiService().getMyPlaylists(false).enqueue(new Callback<ApiResponse<List<Playlist>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Playlist>>> call, @NonNull Response<ApiResponse<List<Playlist>>> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    allPlaylistsAdapter.setPlaylists(response.body().getData());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Playlist>>> call, @NonNull Throwable t) {
                if (!isAdded() || getContext() == null) return;
                Toast.makeText(getContext(), "Lỗi tải playlist: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAllAlbums() {
        ApiClient.getApiService().getAllAlbums(100).enqueue(new Callback<ApiResponse<List<Album>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Album>>> call, @NonNull Response<ApiResponse<List<Album>>> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    allAlbumsAdapter.setAlbums(response.body().getData());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Album>>> call, @NonNull Throwable t) {
                if (!isAdded() || getContext() == null) return;
                Toast.makeText(getContext(), "Lỗi tải album: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void showLoading(boolean isLoading) {
        if (binding == null) return;
        if (isLoading) {
            binding.shimmerHomeContainer.setVisibility(View.VISIBLE);
            binding.shimmerHomeContainer.startShimmer();
            binding.layoutHomeContent.setVisibility(View.GONE);
            binding.layoutMusicContainer.setVisibility(View.GONE);
            binding.layoutArtistsContainer.setVisibility(View.GONE);
            binding.layoutPlaylistsContainer.setVisibility(View.GONE);
            binding.layoutAlbumsContainer.setVisibility(View.GONE);
        } else {
            binding.shimmerHomeContainer.stopShimmer();
            binding.shimmerHomeContainer.setVisibility(View.GONE);
            if (currentSelectedMainChip == binding.chipMusic) {
                binding.layoutMusicContainer.setVisibility(View.VISIBLE);
            } else if (currentSelectedMainChip == binding.chipArtists) {
                binding.layoutArtistsContainer.setVisibility(View.VISIBLE);
            } else if (currentSelectedMainChip == binding.chipPlaylists) {
                binding.layoutPlaylistsContainer.setVisibility(View.VISIBLE);
            } else if (currentSelectedMainChip == binding.chipAlbums) {
                binding.layoutAlbumsContainer.setVisibility(View.VISIBLE);
            } else {
                binding.layoutHomeContent.setVisibility(View.VISIBLE);
            }
        }
    }

    private final NotificationWebSocketManager.OnNotificationEventListener notificationWsListener =
            new NotificationWebSocketManager.OnNotificationEventListener() {
                @Override
                public void onConnected(int unreadCount) {
                    updateNotificationBellBadge(unreadCount);
                }

                @Override
                public void onNewNotification(NotificationItem notification, int unreadCount) {
                    updateNotificationBellBadge(unreadCount);
                }

                @Override
                public void onNotificationRead(String notificationId, int unreadCount) {
                    updateNotificationBellBadge(unreadCount);
                }

                @Override
                public void onAllNotificationsRead(int unreadCount) {
                    updateNotificationBellBadge(unreadCount);
                }

                @Override
                public void onNotificationDeleted(String notificationId, int unreadCount) {
                    updateNotificationBellBadge(unreadCount);
                }
            };

    private void setupNotificationBell() {
        if (binding == null) return;
        binding.btnNotificationBell.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), NotificationActivity.class);
            startActivity(intent);
        });
    }

    private void updateNotificationBellBadge(int unreadCount) {
        if (!isAdded() || binding == null) return;
        if (unreadCount > 0) {
            binding.viewBellUnreadDot.setVisibility(View.VISIBLE);
            binding.btnNotificationBell.setBackgroundResource(R.drawable.bg_bell_circle_unread);
        } else {
            binding.viewBellUnreadDot.setVisibility(View.GONE);
            binding.btnNotificationBell.setBackgroundResource(R.drawable.bg_bell_circle_normal);
        }
    }

    private void fetchUnreadNotificationCount() {
        ApiClient.getApiService().getUnreadNotificationCount().enqueue(new Callback<ApiResponse<UnreadCountData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<UnreadCountData>> call,
                                   @NonNull Response<ApiResponse<UnreadCountData>> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    int unread = response.body().getData().getUnreadCount();
                    updateNotificationBellBadge(unread);
                    if (getContext() != null) {
                        NotificationWebSocketManager.getInstance(requireContext()).setLastKnownUnreadCount(unread);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<UnreadCountData>> call, @NonNull Throwable t) {}
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            NotificationWebSocketManager.getInstance(requireContext()).registerListener(notificationWsListener);
            NotificationWebSocketManager.getInstance(requireContext()).connect();
            fetchUnreadNotificationCount();
        }

        if (binding != null && getContext() != null) {
            User user = SessionManager.getInstance(requireContext()).getUser();
            if (user != null && user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                Glide.with(this)
                        .load(ApiClient.resolveMediaUrl(user.getAvatarUrl()))
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(binding.ivProfileAvatar);
            }

            List<Song> localRecents = SessionManager.getInstance(requireContext()).getRecentlyPlayedSongs();
            if (recentSongAdapter != null) {
                recentSongAdapter.setSongs(localRecents != null ? localRecents : Collections.emptyList());
            }

            if (currentSelectedMainChip == binding.chipAll) {
                fetchHomeFeed(false);
            } else if (currentSelectedMainChip == binding.chipMusic) {
                loadGenresAndMusic();
            } else if (currentSelectedMainChip == binding.chipArtists) {
                loadAllArtists();
            } else if (currentSelectedMainChip == binding.chipPlaylists) {
                loadAllPlaylists();
            } else if (currentSelectedMainChip == binding.chipAlbums) {
                loadAllAlbums();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getContext() != null) {
            NotificationWebSocketManager.getInstance(requireContext()).unregisterListener(notificationWsListener);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
