package com.example.spotifyclone.presentation.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.HistoryDateGroup;
import com.example.spotifyclone.data.model.HistoryItem;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

public class HistoryGroupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ITEM = 1;

    public interface OnHistoryActionListener {
        void onSongClick(Song song, List<Song> dayQueue, int index);
        void onDeleteSong(HistoryItem item);
        void onDeleteDay(HistoryDateGroup group);
    }

    public static class RowWrapper {
        public int type;
        public HistoryDateGroup group;
        public HistoryItem item;
        public List<Song> parentQueue;
        public int queueIndex;

        public RowWrapper(HistoryDateGroup group) {
            this.type = TYPE_HEADER;
            this.group = group;
        }

        public RowWrapper(HistoryItem item, List<Song> parentQueue, int queueIndex) {
            this.type = TYPE_ITEM;
            this.item = item;
            this.parentQueue = parentQueue;
            this.queueIndex = queueIndex;
        }
    }

    private final List<RowWrapper> rows = new ArrayList<>();
    private List<HistoryDateGroup> dateGroups = new ArrayList<>();
    private final OnHistoryActionListener listener;

    public HistoryGroupAdapter(OnHistoryActionListener listener) {
        this.listener = listener;
    }

    public void setGroups(List<HistoryDateGroup> groups) {
        this.dateGroups = groups != null ? new ArrayList<>(groups) : new ArrayList<>();
        rebuildRows();
    }

    public void toggleGroup(HistoryDateGroup group) {
        if (group == null) return;
        group.setExpanded(!group.isExpanded());
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        for (HistoryDateGroup group : dateGroups) {
            if (group.getItems() == null || group.getItems().isEmpty()) continue;
            rows.add(new RowWrapper(group));

            // Chỉ thêm bài hát khi ngày này đang ở trạng thái mở rộng (expanded)
            if (group.isExpanded()) {
                List<Song> dayQueue = new ArrayList<>();
                for (HistoryItem h : group.getItems()) {
                    if (h.getSong() != null) {
                        dayQueue.add(h.getSong());
                    }
                }

                int idx = 0;
                for (HistoryItem h : group.getItems()) {
                    rows.add(new RowWrapper(h, dayQueue, idx));
                    idx++;
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View v = inflater.inflate(R.layout.item_history_header, parent, false);
            return new HeaderViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_history_song, parent, false);
            return new SongViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RowWrapper row = rows.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(row.group, this, listener);
        } else if (holder instanceof SongViewHolder) {
            ((SongViewHolder) holder).bind(row.item, row.parentQueue, row.queueIndex, listener);
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        View layoutHeaderContainer;
        ImageView ivHeaderIcon;
        TextView tvDateHeader;
        TextView tvTrackCount;
        ImageView ivChevron;
        ImageButton btnDeleteDay;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutHeaderContainer = itemView.findViewById(R.id.layoutHeaderContainer);
            ivHeaderIcon = itemView.findViewById(R.id.ivHeaderIcon);
            tvDateHeader = itemView.findViewById(R.id.tvDateHeader);
            tvTrackCount = itemView.findViewById(R.id.tvTrackCount);
            ivChevron = itemView.findViewById(R.id.ivChevron);
            btnDeleteDay = itemView.findViewById(R.id.btnDeleteDay);
        }

        public void bind(HistoryDateGroup group, HistoryGroupAdapter adapter, OnHistoryActionListener listener) {
            if (group == null) return;
            tvDateHeader.setText(group.getDisplayTitle());
            int count = group.getItems() != null ? group.getItems().size() : 0;
            tvTrackCount.setText(count + " bài hát");

            // Hiệu ứng mũi tên Chevron xoay theo trạng thái đóng/mở
            if (ivChevron != null) {
                ivChevron.setRotation(group.isExpanded() ? 180f : 0f);
            }

            // Chạm vào thanh ngày để đóng hoặc mở
            View clickTarget = layoutHeaderContainer != null ? layoutHeaderContainer : itemView;
            clickTarget.setOnClickListener(v -> {
                if (adapter != null) {
                    adapter.toggleGroup(group);
                }
            });

            btnDeleteDay.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteDay(group);
            });
        }
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivSongCover;
        TextView tvSongTitle;
        TextView tvSongArtist;
        TextView tvPlayedTime;
        ImageButton btnDeleteSong;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSongCover = itemView.findViewById(R.id.ivSongCover);
            tvSongTitle = itemView.findViewById(R.id.tvSongTitle);
            tvSongArtist = itemView.findViewById(R.id.tvSongArtist);
            tvPlayedTime = itemView.findViewById(R.id.tvPlayedTime);
            btnDeleteSong = itemView.findViewById(R.id.btnDeleteSong);
        }

        public void bind(HistoryItem item, List<Song> parentQueue, int queueIndex, OnHistoryActionListener listener) {
            if (item == null || item.getSong() == null) return;
            Song song = item.getSong();

            tvSongTitle.setText(song.getTitle());
            tvSongArtist.setText(song.getArtistName());

            String timeStr = item.getFormattedTime();
            if (timeStr != null && !timeStr.isEmpty()) {
                tvPlayedTime.setText(timeStr);
                tvPlayedTime.setVisibility(View.VISIBLE);
            } else {
                tvPlayedTime.setVisibility(View.GONE);
            }

            Glide.with(itemView.getContext())
                    .load(ApiClient.resolveMediaUrl(song.getCoverImageUrl()))
                    .placeholder(R.drawable.ic_album)
                    .error(R.drawable.ic_album)
                    .into(ivSongCover);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSongClick(song, parentQueue, queueIndex);
                }
            });

            btnDeleteSong.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteSong(item);
                }
            });
        }
    }
}
