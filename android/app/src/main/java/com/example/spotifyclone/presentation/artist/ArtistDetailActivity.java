package com.example.spotifyclone.presentation.artist;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.Artist;
import com.example.spotifyclone.data.model.ArtistDetailData;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ActivityArtistDetailBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.example.spotifyclone.data.model.Album;
import com.example.spotifyclone.presentation.home.AlbumCardAdapter;
import com.example.spotifyclone.presentation.player.PlayerActivity;
import com.example.spotifyclone.presentation.playlist.PlaylistDetailActivity;
import com.example.spotifyclone.presentation.search.SearchResultAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ArtistDetailActivity extends AppCompatActivity implements SearchResultAdapter.OnSearchSongClickListener {

    public static final String EXTRA_ARTIST_ID = "ARTIST_ID";
    public static final String EXTRA_ARTIST_NAME = "ARTIST_NAME";
    public static final String EXTRA_ARTIST_AVATAR = "ARTIST_AVATAR";
    public static final String EXTRA_ARTIST_BIO = "ARTIST_BIO";
    public static final String EXTRA_ARTIST_FOLLOWERS = "ARTIST_FOLLOWERS";

    private ActivityArtistDetailBinding binding;
    private SearchResultAdapter trackAdapter;
    private AlbumCardAdapter albumAdapter;
    private final List<Song> songList = new ArrayList<>();
    private final List<Song> originalSongList = new ArrayList<>();
    private String currentSort = "popular";
    private String artistId;
    private String artistName;
    private boolean isFollowing = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityArtistDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        com.example.spotifyclone.util.ThemeManager.applySystemBarsAppearance(this);

        // Bù trừ an toàn thanh trạng thái cho Toolbar, AppBar và đáy cho danh sách cuộn
        com.example.spotifyclone.util.WindowInsetHelper.applyCollapsingToolbarInsets(
                binding.getRoot(),
                binding.appBarLayout,
                binding.toolbarArtist,
                binding.ivArtistAvatar,
                binding.scrollArtistContent
        );

        parseIntent();
        setupToolbar();
        setupRecyclerView();
        setupActions();
        setupSortChips();
        loadArtistData();
    }

    private void parseIntent() {
        artistId = getIntent().getStringExtra(EXTRA_ARTIST_ID);
        artistName = getIntent().getStringExtra(EXTRA_ARTIST_NAME);
        String avatar = getIntent().getStringExtra(EXTRA_ARTIST_AVATAR);
        String bio = getIntent().getStringExtra(EXTRA_ARTIST_BIO);
        long followers = getIntent().getLongExtra(EXTRA_ARTIST_FOLLOWERS, 0);

        if (artistName != null && !artistName.isEmpty()) {
            binding.tvArtistName.setText(artistName);
            binding.tvToolbarTitle.setText(artistName);
        } else {
            binding.tvArtistName.setText("Nghệ sĩ");
            binding.tvToolbarTitle.setText("Nghệ sĩ");
        }

        if (followers > 0) {
            binding.tvArtistFollowers.setText(String.format(Locale.getDefault(), "%,d người theo dõi", followers));
        } else {
            binding.tvArtistFollowers.setText("Nghệ sĩ nổi bật trên Vibe Music");
        }

        if (bio != null && !bio.isEmpty()) {
            binding.tvArtistBio.setText(bio);
            binding.tvArtistBio.setVisibility(View.VISIBLE);
        } else {
            binding.tvArtistBio.setVisibility(View.GONE);
        }

        if (avatar != null && !avatar.isEmpty()) {
            Glide.with(this)
                    .load(ApiClient.resolveMediaUrl(avatar))
                    .circleCrop()
                    .placeholder(R.drawable.bg_card_rounded)
                    .error(R.drawable.bg_card_rounded)
                    .into(binding.ivArtistAvatar);
        }
    }

    private void setupToolbar() {
        binding.toolbarArtist.setNavigationOnClickListener(v -> finish());

        binding.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            int totalScrollRange = appBarLayout.getTotalScrollRange();
            if (totalScrollRange == 0) return;
            float ratio = (float) Math.abs(verticalOffset) / (float) totalScrollRange;
            binding.layoutArtistHeaderParallax.setAlpha(Math.max(0f, 1f - ratio * 1.6f));
            binding.tvToolbarTitle.setAlpha(ratio >= 0.7f ? (ratio - 0.7f) / 0.3f : 0f);
        });
    }

    private void setupRecyclerView() {
        trackAdapter = new SearchResultAdapter(this);
        binding.rvArtistTracks.setLayoutManager(new LinearLayoutManager(this));
        binding.rvArtistTracks.setAdapter(trackAdapter);

        albumAdapter = new AlbumCardAdapter(album -> {
            if (album == null) return;
            Intent intent = new Intent(this, PlaylistDetailActivity.class);
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE, PlaylistDetailActivity.TYPE_ALBUM);
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, album.getId());
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_TITLE, album.getTitle());
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_COVER, album.getCoverImageUrl());
            intent.putExtra(PlaylistDetailActivity.EXTRA_ORIGIN_DESTINATION_ID, R.id.navigation_home);
            startActivity(intent);
        });
        binding.rvArtistAlbums.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvArtistAlbums.setAdapter(albumAdapter);
    }

    private void setupActions() {
        binding.btnPlayAll.setOnClickListener(v -> {
            if (!songList.isEmpty()) {
                MusicPlayerManager.getInstance().playQueue(songList, 0);
                openPlayerActivity(songList.get(0));
            } else {
                Toast.makeText(this, "Nghệ sĩ này chưa có bài hát", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnFollow.setOnClickListener(v -> {
            if (artistId == null || artistId.isEmpty()) return;
            // Optimistic update
            isFollowing = !isFollowing;
            updateFollowButtonUI(isFollowing);

            ApiClient.getApiService().toggleFollowArtist(artistId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                        Map<String, Object> data = response.body().getData();
                        Object followObj = data.get("isFollowed");
                        if (followObj instanceof Boolean) {
                            isFollowing = (Boolean) followObj;
                            updateFollowButtonUI(isFollowing);
                        }
                        Object countObj = data.get("followersCount");
                        if (countObj instanceof Number) {
                            long count = ((Number) countObj).longValue();
                            binding.tvArtistFollowers.setText(String.format(Locale.getDefault(), "%,d người theo dõi", count));
                        }
                        String msg = response.body().getMessage();
                        if (msg != null && !msg.isEmpty()) {
                            Toast.makeText(ArtistDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                    // Rollback on failure
                    isFollowing = !isFollowing;
                    updateFollowButtonUI(isFollowing);
                    Toast.makeText(ArtistDetailActivity.this, "Không thể kết nối đến máy chủ", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void updateFollowButtonUI(boolean following) {
        if (following) {
            binding.btnFollow.setText("Đang theo dõi");
            binding.btnFollow.setTextColor(getColor(R.color.spotify_green));
            binding.btnFollow.setStrokeColorResource(R.color.spotify_green);
        } else {
            binding.btnFollow.setText("Theo dõi");
            binding.btnFollow.setTextColor(getColor(R.color.text_primary));
            binding.btnFollow.setStrokeColorResource(R.color.spotify_stroke);
        }
    }

    private void setupSortChips() {
        com.example.spotifyclone.ui.liquid.LiquidDropletHelper.selectPill(binding.chipSortPopular, true, false);
        com.example.spotifyclone.ui.liquid.LiquidDropletHelper.attachDropletSpring(
                binding.btnPlayAll,
                binding.btnFollow,
                binding.chipSortPopular,
                binding.chipSortAz,
                binding.chipSortNewest,
                binding.chipSortFavorite
        );
        binding.chipSortPopular.setOnClickListener(v -> selectSortChip("popular"));
        binding.chipSortAz.setOnClickListener(v -> selectSortChip("az"));
        binding.chipSortNewest.setOnClickListener(v -> selectSortChip("newest"));
        binding.chipSortFavorite.setOnClickListener(v -> selectSortChip("favorite"));
    }

    private void selectSortChip(String sort) {
        currentSort = sort;
        android.widget.TextView target = binding.chipSortPopular;
        if ("az".equals(sort)) {
            target = binding.chipSortAz;
        } else if ("newest".equals(sort)) {
            target = binding.chipSortNewest;
        } else if ("favorite".equals(sort)) {
            target = binding.chipSortFavorite;
        }
        binding.chipSortPopular.setTypeface(null, target == binding.chipSortPopular ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        binding.chipSortAz.setTypeface(null, target == binding.chipSortAz ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        binding.chipSortNewest.setTypeface(null, target == binding.chipSortNewest ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        binding.chipSortFavorite.setTypeface(null, target == binding.chipSortFavorite ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        com.example.spotifyclone.ui.liquid.LiquidDropletHelper.selectPill(target, true, true);

        applySongSort();
    }

    private void applySongSort() {
        if (originalSongList.isEmpty()) return;
        List<Song> sorted = new ArrayList<>(originalSongList);
        if ("az".equals(currentSort)) {
            sorted.sort((a, b) -> {
                String tA = a.getTitle() != null ? a.getTitle() : "";
                String tB = b.getTitle() != null ? b.getTitle() : "";
                return tA.compareToIgnoreCase(tB);
            });
        } else if ("newest".equals(currentSort)) {
            java.util.Collections.reverse(sorted);
        } else if ("favorite".equals(currentSort)) {
            sorted.sort((a, b) -> {
                if (a.isLiked() != b.isLiked()) {
                    return a.isLiked() ? -1 : 1;
                }
                return Long.compare(b.getLikesCount(), a.getLikesCount());
            });
        } else {
            // "popular": Nghe nhiều nhất
            sorted.sort((a, b) -> Long.compare(b.getPlaysCount(), a.getPlaysCount()));
        }

        songList.clear();
        songList.addAll(sorted);
        trackAdapter.setSongs(songList);
    }

    private void loadArtistData() {
        if (artistId == null || artistId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy thông tin nghệ sĩ", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.shimmerArtistContainer.setVisibility(View.VISIBLE);
        binding.shimmerArtistContainer.startShimmer();
        binding.rvArtistTracks.setVisibility(View.GONE);
        binding.layoutArtistEmptyState.setVisibility(View.GONE);

        ApiClient.getApiService().getSongsByArtist(artistId).enqueue(new Callback<ApiResponse<ArtistDetailData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<ArtistDetailData>> call, @NonNull Response<ApiResponse<ArtistDetailData>> response) {
                binding.shimmerArtistContainer.stopShimmer();
                binding.shimmerArtistContainer.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    ArtistDetailData detailData = response.body().getData();

                    Artist artist = detailData.getArtist();
                    if (artist != null) {
                        artistName = artist.getName();
                        isFollowing = artist.isFollowed();
                        updateFollowButtonUI(isFollowing);

                        binding.tvArtistName.setText(artist.getName());
                        binding.tvToolbarTitle.setText(artist.getName());

                        if (artist.getFollowersCount() > 0) {
                            binding.tvArtistFollowers.setText(String.format(Locale.getDefault(), "%,d người theo dõi", artist.getFollowersCount()));
                        }

                        if (artist.getBio() != null && !artist.getBio().isEmpty()) {
                            binding.tvArtistBio.setText(artist.getBio());
                            binding.tvArtistBio.setVisibility(View.VISIBLE);
                        }

                        if (artist.getAvatarUrl() != null && !artist.getAvatarUrl().isEmpty()) {
                            Glide.with(ArtistDetailActivity.this)
                                    .load(ApiClient.resolveMediaUrl(artist.getAvatarUrl()))
                                    .circleCrop()
                                    .placeholder(R.drawable.bg_card_rounded)
                                    .error(R.drawable.bg_card_rounded)
                                    .into(binding.ivArtistAvatar);
                        }
                    }

                    List<Song> songs = detailData.getSongs();
                    songList.clear();
                    originalSongList.clear();
                    if (songs != null && !songs.isEmpty()) {
                        originalSongList.addAll(songs);
                        applySongSort();
                        binding.tvSongsCount.setText(songList.size() + " bài hát");
                        binding.layoutArtistEmptyState.setVisibility(View.GONE);
                        binding.rvArtistTracks.setVisibility(View.VISIBLE);
                        binding.scrollArtistSortChips.setVisibility(View.VISIBLE);
                    } else {
                        trackAdapter.setSongs(new ArrayList<>());
                        binding.tvSongsCount.setText("0 bài hát");
                        binding.layoutArtistEmptyState.setVisibility(View.VISIBLE);
                        binding.rvArtistTracks.setVisibility(View.GONE);
                        binding.scrollArtistSortChips.setVisibility(View.GONE);
                    }

                    List<Album> albums = detailData.getAlbums();
                    if (albums != null && !albums.isEmpty()) {
                        albumAdapter.setAlbums(albums);
                        binding.tvAlbumsCount.setText(albums.size() + " album");
                        binding.layoutArtistAlbums.setVisibility(View.VISIBLE);
                    } else {
                        binding.layoutArtistAlbums.setVisibility(View.GONE);
                    }
                } else {
                    binding.rvArtistTracks.setVisibility(View.GONE);
                    binding.layoutArtistEmptyState.setVisibility(View.VISIBLE);
                    Toast.makeText(ArtistDetailActivity.this, "Không thể tải danh sách bài hát của nghệ sĩ", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<ArtistDetailData>> call, @NonNull Throwable t) {
                binding.shimmerArtistContainer.stopShimmer();
                binding.shimmerArtistContainer.setVisibility(View.GONE);
                binding.rvArtistTracks.setVisibility(View.GONE);
                binding.layoutArtistEmptyState.setVisibility(View.VISIBLE);
                Toast.makeText(ArtistDetailActivity.this, "Lỗi kết nối Backend: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onSongClick(Song song) {
        if (song == null) return;
        int index = songList.indexOf(song);
        if (index == -1) index = 0;
        MusicPlayerManager.getInstance().playQueue(songList, index);
        openPlayerActivity(song);
    }

    private void openPlayerActivity(Song song) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("SONG_TITLE", song.getTitle());
        intent.putExtra("ARTIST_NAME", song.getArtistName());
        intent.putExtra("COVER_URL", ApiClient.resolveMediaUrl(song.getCoverImageUrl()));
        intent.putExtra("AUDIO_URL", ApiClient.resolveMediaUrl(song.getAudioUrl()));
        intent.putExtra("SONG_ID", song.getId());
        intent.putExtra("IS_LIKED", song.isLiked());
        intent.putExtra("DURATION", song.getDuration());
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.stay);
    }
}
