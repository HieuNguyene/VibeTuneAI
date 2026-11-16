package com.example.spotifyclone.presentation.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.Artist;
import com.example.spotifyclone.data.remote.ApiClient;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders artists cleanly with pure circular portraits (no square background bleed),
 * verified badge, follower/song metadata, filtering/sorting, and strip or 2-col grid cards.
 */
public class ArtistPortraitAdapter extends RecyclerView.Adapter<ArtistPortraitAdapter.ArtistViewHolder> {

    public static final int FILTER_ALL = 0;
    public static final int FILTER_WITH_SONGS = 1;
    public static final int FILTER_FOLLOWING = 2;
    public static final int FILTER_AZ = 3;

    public interface OnArtistClickListener {
        void onArtistClick(Artist artist);
    }

    public interface OnArtistsFilteredListener {
        void onArtistsFiltered(int visibleCount, int totalCount);
    }

    private final List<Artist> masterList = new ArrayList<>();
    private final List<Artist> artists = new ArrayList<>();
    private final OnArtistClickListener listener;
    private OnArtistsFilteredListener filteredListener;
    private final boolean isGridMode;
    private boolean showMixSubtitle = false;
    private int filterMode = FILTER_ALL;

    public ArtistPortraitAdapter(OnArtistClickListener listener, boolean isGridMode) {
        this.listener = listener;
        this.isGridMode = isGridMode;
    }

    public void setShowMixSubtitle(boolean showMixSubtitle) {
        this.showMixSubtitle = showMixSubtitle;
    }

    public void setOnArtistsFilteredListener(OnArtistsFilteredListener filteredListener) {
        this.filteredListener = filteredListener;
    }

    public void setFilterMode(int filterMode) {
        this.filterMode = filterMode;
        applyFilterAndNotify();
    }

    public int getFilterMode() {
        return filterMode;
    }

    public void setArtists(List<Artist> list) {
        this.masterList.clear();
        if (list != null) {
            this.masterList.addAll(list);
        }
        applyFilterAndNotify();
    }

    private void applyFilterAndNotify() {
        this.artists.clear();
        if (!isGridMode || filterMode == FILTER_ALL) {
            this.artists.addAll(this.masterList);
        } else if (filterMode == FILTER_WITH_SONGS) {
            for (Artist a : this.masterList) {
                if (a.getSongsCount() > 0) {
                    this.artists.add(a);
                }
            }
        } else if (filterMode == FILTER_FOLLOWING) {
            for (Artist a : this.masterList) {
                if (a.isFollowed()) {
                    this.artists.add(a);
                }
            }
        } else if (filterMode == FILTER_AZ) {
            this.artists.addAll(this.masterList);
            Collator collator = Collator.getInstance(new Locale("vi", "VN"));
            collator.setStrength(Collator.PRIMARY);
            this.artists.sort((a, b) -> {
                String n1 = a.getName() != null ? a.getName().trim() : "";
                String n2 = b.getName() != null ? b.getName().trim() : "";
                return collator.compare(n1, n2);
            });
        }
        notifyDataSetChanged();
        if (filteredListener != null) {
            filteredListener.onArtistsFiltered(this.artists.size(), this.masterList.size());
        }
    }

    @NonNull
    @Override
    public ArtistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = isGridMode ? R.layout.item_artist_grid : R.layout.item_artist_card;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new ArtistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArtistViewHolder holder, int position) {
        Artist artist = artists.get(position);
        holder.bind(artist);
    }

    @Override
    public int getItemCount() {
        return artists.size();
    }

    class ArtistViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivAvatar;
        private final View badgeVerified;
        private final TextView tvName;
        private final TextView tvSubtitle;
        private final TextView tvFollowTag;

        ArtistViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivArtistAvatar);
            badgeVerified = itemView.findViewById(R.id.badgeVerified);
            tvName = itemView.findViewById(R.id.tvArtistName);
            tvSubtitle = itemView.findViewById(R.id.tvArtistSubtitle);
            tvFollowTag = itemView.findViewById(R.id.tvArtistFollowTag);
        }

        void bind(Artist artist) {
            tvName.setText(artist.getName());
            if (badgeVerified != null) {
                badgeVerified.setVisibility(artist.isVerified() ? View.VISIBLE : View.GONE);
            }

            if (isGridMode) {
                tvSubtitle.setText(buildGridSubtitle(artist));
                if (tvFollowTag != null) {
                    tvFollowTag.setVisibility(artist.isFollowed() ? View.VISIBLE : View.GONE);
                }
            } else if (showMixSubtitle) {
                tvSubtitle.setText("Tuyển tập • " + formatFollowers(artist.getFollowersCount()));
            } else if (artist.isFollowed()) {
                tvSubtitle.setText("Đang theo dõi");
            } else {
                tvSubtitle.setText(formatFollowers(artist.getFollowersCount()) + " người nghe");
            }

            String fullAvatarUrl = ApiClient.resolveMediaUrl(artist.getAvatarUrl());
            if (isGridMode) {
                Glide.with(itemView.getContext())
                        .load(fullAvatarUrl)
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .centerCrop()
                        .transition(DrawableTransitionOptions.withCrossFade(150))
                        .into(ivAvatar);
            } else {
                Glide.with(itemView.getContext())
                        .load(fullAvatarUrl)
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .circleCrop()
                        .transition(DrawableTransitionOptions.withCrossFade(150))
                        .into(ivAvatar);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onArtistClick(artist);
                }
            });
        }

        private String buildGridSubtitle(Artist artist) {
            String followersText = formatFollowers(artist.getFollowersCount());
            if (artist.getSongsCount() > 0) {
                return artist.getSongsCount() + " bài hát • " + followersText;
            }
            return "Nghệ sĩ • " + followersText;
        }

        private String formatFollowers(long count) {
            if (count >= 1_000_000L) {
                return String.format(Locale.US, "%.1fM", count / 1_000_000.0).replace(".0M", "M");
            }
            if (count >= 1_000L) {
                return String.format(Locale.US, "%.0fK", count / 1_000.0);
            }
            return count + "";
        }
    }
}
