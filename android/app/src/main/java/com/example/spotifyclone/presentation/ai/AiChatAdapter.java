package com.example.spotifyclone.presentation.ai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.AiChatMessage;
import com.example.spotifyclone.data.model.Song;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AiChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnChatInteractionListener {
        void onPlaySong(Song song, List<Song> songList, int position);
        void onQuickReplyClick(String prompt);
    }

    private final List<AiChatMessage> messages = new ArrayList<>();
    private final OnChatInteractionListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public AiChatAdapter(OnChatInteractionListener listener) {
        this.listener = listener;
    }

    public void setMessages(List<AiChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    public void addMessage(AiChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public void removeLoading() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getType() == AiChatMessage.TYPE_LOADING) {
                messages.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == AiChatMessage.TYPE_USER) {
            View view = inflater.inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        } else if (viewType == AiChatMessage.TYPE_LOADING) {
            View view = inflater.inflate(R.layout.item_chat_loading, parent, false);
            return new LoadingViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_chat_ai, parent, false);
            return new AiViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AiChatMessage message = messages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).bind(message);
        } else if (holder instanceof AiViewHolder) {
            ((AiViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMessage;
        private final TextView tvTime;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvUserMessage);
            tvTime = itemView.findViewById(R.id.tvUserTime);
        }

        public void bind(AiChatMessage message) {
            tvMessage.setText(message.getMessage());
            tvTime.setText(timeFormat.format(new Date(message.getTimestamp())));
        }
    }

    class AiViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMessage;
        private final TextView tvTime;
        private final RecyclerView rvSongs;
        private final View hsvSuggestions;
        private final ChipGroup chipGroupFollowUps;

        public AiViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvAiMessage);
            tvTime = itemView.findViewById(R.id.tvAiTime);
            rvSongs = itemView.findViewById(R.id.rvAiSongs);
            hsvSuggestions = itemView.findViewById(R.id.hsvSuggestions);
            chipGroupFollowUps = itemView.findViewById(R.id.chipGroupFollowUps);
        }

        public void bind(AiChatMessage message) {
            tvMessage.setText(message.getMessage());
            tvTime.setText(timeFormat.format(new Date(message.getTimestamp())));

            // Bind Embedded Songs
            List<Song> songs = message.getSuggestedSongs();
            if (songs != null && !songs.isEmpty()) {
                rvSongs.setVisibility(View.VISIBLE);
                rvSongs.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
                AiChatSongAdapter songAdapter = new AiChatSongAdapter(songs, (song, songList, position) -> {
                    if (listener != null) {
                        listener.onPlaySong(song, songList, position);
                    }
                });
                rvSongs.setAdapter(songAdapter);
            } else {
                rvSongs.setVisibility(View.GONE);
            }

            // Bind Follow-up Suggestion Chips
            List<String> replies = message.getSuggestedReplies();
            chipGroupFollowUps.removeAllViews();
            if (replies != null && !replies.isEmpty()) {
                hsvSuggestions.setVisibility(View.VISIBLE);
                for (String replyText : replies) {
                    Chip chip = new Chip(itemView.getContext(), null, com.google.android.material.R.style.Widget_MaterialComponents_Chip_Action);
                    chip.setText(replyText);
                    chip.setTextSize(12f);
                    chip.setTextColor(itemView.getContext().getResources().getColor(R.color.text_primary));
                    chip.setChipBackgroundColorResource(R.color.spotify_surface);
                    chip.setChipStrokeColorResource(R.color.spotify_stroke);
                    chip.setChipStrokeWidth(1f);
                    chip.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onQuickReplyClick(replyText);
                        }
                    });
                    chipGroupFollowUps.addView(chip);
                }
            } else {
                hsvSuggestions.setVisibility(View.GONE);
            }
        }
    }

    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
