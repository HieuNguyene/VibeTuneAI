package com.example.spotifyclone.presentation.player;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.LyricLine;

import java.util.ArrayList;
import java.util.List;

public class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.LyricViewHolder> {

    public interface OnLyricClickListener {
        void onLyricClick(LyricLine line, int position);
    }

    private final List<LyricLine> lyricLines = new ArrayList<>();
    private int activePosition = -1;
    private OnLyricClickListener listener;

    public LyricsAdapter(OnLyricClickListener listener) {
        this.listener = listener;
    }

    public void setLyricLines(List<LyricLine> lines) {
        this.lyricLines.clear();
        if (lines != null) {
            this.lyricLines.addAll(lines);
        }
        this.activePosition = -1;
        notifyDataSetChanged();
    }

    public int getActivePosition() {
        return activePosition;
    }

    public boolean setActivePosition(int newPosition) {
        // Tắt đồng bộ highlight động theo yêu cầu của người dùng
        return false;
    }

    @NonNull
    @Override
    public LyricViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lyric_line, parent, false);
        return new LyricViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LyricViewHolder holder, int position) {
        LyricLine line = lyricLines.get(position);
        holder.bind(line);
    }

    @Override
    public int getItemCount() {
        return lyricLines.size();
    }

    public LyricLine getItem(int position) {
        if (position >= 0 && position < lyricLines.size()) {
            return lyricLines.get(position);
        }
        return null;
    }

    class LyricViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvLyricText;

        public LyricViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLyricText = itemView.findViewById(R.id.tvLyricText);
            itemView.setClickable(false);
            itemView.setFocusable(false);
        }

        public void bind(LyricLine line) {
            tvLyricText.setText(line.getText());
            tvLyricText.setTextColor(Color.WHITE);
            tvLyricText.setTextSize(18f);
            tvLyricText.setAlpha(0.95f);
        }
    }
}
