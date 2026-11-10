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
    private String artistId;
    private String artistName;
    private boolean isFollowing = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityArtistDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
            isFollowing = !isFollowing;
            if (isFollowing) {
                binding.btnFollow.setText("Đang theo dõi");
                binding.btnFollow.setTextColor(getColor(R.color.spotify_green));
                binding.btnFollow.setStrokeColorResource(R.color.spotify_green);
                Toast.makeText(this, "Đã theo dõi " + (artistName != null ? artistName : "nghệ sĩ"), Toast.LENGTH_SHORT).show();
            } else {
                binding.btnFollow.setText("Theo dõi");
                binding.btnFollow.setTextColor(getColor(R.color.text_primary));
                binding.btnFollow.setStrokeColorResource(R.color.spotify_stroke);
            }
        });
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
                    if (songs != null && !songs.isEmpty()) {
                        songList.addAll(songs);
                        trackAdapter.setSongs(songList);
                        binding.tvSongsCount.setText(songList.size() + " bài hát");
                        binding.layoutArtistEmptyState.setVisibility(View.GONE);
                        binding.rvArtistTracks.setVisibility(View.VISIBLE);
                    } else {
                        trackAdapter.setSongs(new ArrayList<>());
                        binding.tvSongsCount.setText("0 bài hát");
                        binding.layoutArtistEmptyState.setVisibility(View.VISIBLE);
                        binding.rvArtistTracks.setVisibility(View.GONE);
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
