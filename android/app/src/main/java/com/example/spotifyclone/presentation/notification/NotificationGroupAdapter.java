package com.example.spotifyclone.presentation.notification;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.NotificationDateGroup;
import com.example.spotifyclone.data.model.NotificationItem;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ItemNotificationBinding;
import com.example.spotifyclone.databinding.ItemNotificationDateHeaderBinding;

import java.util.ArrayList;
import java.util.List;

public class NotificationGroupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    public interface OnNotificationClickListener {
        void onNotificationClick(NotificationItem item, int position);
    }

    public interface DisplayableItem {}

    public static class HeaderItem implements DisplayableItem {
        public final String title;
        public final int count;

        public HeaderItem(String title, int count) {
            this.title = title;
            this.count = count;
        }
    }

    public static class ContentItem implements DisplayableItem {
        public final NotificationItem notification;

        public ContentItem(NotificationItem notification) {
            this.notification = notification;
        }
    }

    private final List<DisplayableItem> displayItems = new ArrayList<>();
    private final OnNotificationClickListener clickListener;

    public NotificationGroupAdapter(OnNotificationClickListener listener) {
        this.clickListener = listener;
    }

    public void setGroups(List<NotificationDateGroup> groups) {
        displayItems.clear();
        if (groups != null) {
            for (NotificationDateGroup group : groups) {
                if (group.getItems() != null && !group.getItems().isEmpty()) {
                    displayItems.add(new HeaderItem(group.getDisplayTitle(), group.getItems().size()));
                    for (NotificationItem item : group.getItems()) {
                        displayItems.add(new ContentItem(item));
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (displayItems.get(position) instanceof HeaderItem) {
            return VIEW_TYPE_HEADER;
        }
        return VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            ItemNotificationDateHeaderBinding binding = ItemNotificationDateHeaderBinding.inflate(inflater, parent, false);
            return new HeaderViewHolder(binding);
        } else {
            ItemNotificationBinding binding = ItemNotificationBinding.inflate(inflater, parent, false);
            return new NotificationViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DisplayableItem item = displayItems.get(position);
        if (holder instanceof HeaderViewHolder && item instanceof HeaderItem) {
            ((HeaderViewHolder) holder).bind((HeaderItem) item);
        } else if (holder instanceof NotificationViewHolder && item instanceof ContentItem) {
            ((NotificationViewHolder) holder).bind(((ContentItem) item).notification, position);
        }
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final ItemNotificationDateHeaderBinding binding;

        public HeaderViewHolder(ItemNotificationDateHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(HeaderItem item) {
            binding.tvDateTitle.setText(item.title.toUpperCase(java.util.Locale.getDefault()));
            binding.tvGroupCount.setText(item.count + " thông báo");
        }
    }

    class NotificationViewHolder extends RecyclerView.ViewHolder {
        private final ItemNotificationBinding binding;

        public NotificationViewHolder(ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(NotificationItem item, int position) {
            Context context = itemView.getContext();

            binding.tvItemTitle.setText(item.getTitle() != null ? item.getTitle() : "Thông báo");
            binding.tvItemContent.setText(item.getContent() != null ? item.getContent() : "");
            binding.tvItemSender.setText(item.getSenderName());
            binding.tvItemTime.setText(item.getFormattedRelativeTime());

            // Type badge
            String type = item.getType();
            if ("ADMIN_ANNOUNCEMENT".equalsIgnoreCase(type)) {
                binding.tvItemTypeBadge.setText("Quản trị");
                binding.tvItemTypeBadge.setVisibility(View.VISIBLE);
            } else if ("PLAYLIST_LIKED".equalsIgnoreCase(type)) {
                binding.tvItemTypeBadge.setText("Playlist");
                binding.tvItemTypeBadge.setVisibility(View.VISIBLE);
            } else if ("SYSTEM_UPDATE".equalsIgnoreCase(type)) {
                binding.tvItemTypeBadge.setText("Hệ thống");
                binding.tvItemTypeBadge.setVisibility(View.VISIBLE);
            } else {
                binding.tvItemTypeBadge.setVisibility(View.GONE);
            }

            // Image
            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                Glide.with(context)
                        .load(ApiClient.resolveMediaUrl(item.getImageUrl()))
                        .placeholder(R.drawable.bg_card_rounded)
                        .error(R.drawable.ic_notification_bell)
                        .centerCrop()
                        .into(binding.ivItemImage);
            } else if ("PLAYLIST_LIKED".equalsIgnoreCase(type)) {
                binding.ivItemImage.setImageResource(R.drawable.ic_playlist_add);
            } else {
                binding.ivItemImage.setImageResource(R.drawable.ic_notification_bell);
            }

            // Unread indicator dot & stroke thickness
            boolean isUnread = !item.isRead();
            if (isUnread) {
                binding.viewItemUnreadDot.setVisibility(View.VISIBLE);
                binding.cardNotification.setStrokeWidth(dpToPx(context, 2));
                binding.cardNotification.setStrokeColor(ContextCompat.getColor(context, R.color.spotify_green));
            } else {
                binding.viewItemUnreadDot.setVisibility(View.GONE);
                binding.cardNotification.setStrokeWidth(dpToPx(context, 1));
                binding.cardNotification.setStrokeColor(ContextCompat.getColor(context, R.color.spotify_stroke));
            }

            View.OnClickListener clickHandler = v -> {
                if (clickListener != null) {
                    clickListener.onNotificationClick(item, getAdapterPosition());
                }
            };
            binding.cardNotification.setOnClickListener(clickHandler);
            itemView.setOnClickListener(clickHandler);
        }

        private int dpToPx(Context context, int dp) {
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics()
            );
        }
    }
}
