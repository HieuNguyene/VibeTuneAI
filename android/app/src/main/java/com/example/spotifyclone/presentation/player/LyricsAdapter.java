package com.example.spotifyclone.presentation.player;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
    private final OnLyricClickListener listener;

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

    /**
     * Kích hoạt sự kiện đổi màu chữ nổi bật (Highlighting)
     * Chỉ cập nhật đúng dòng cũ và dòng mới để tối ưu hiệu năng tối đa (tránh notifyDataSetChanged()).
     *
     * @param newPosition Vị trí dòng hát hiện tại
     * @return true nếu vị trí có thay đổi, false nếu giữ nguyên
     */
    public boolean setActivePosition(int newPosition) {
        if (this.activePosition == newPosition) {
            return false;
        }

        int oldPosition = this.activePosition;
        this.activePosition = newPosition;

        if (oldPosition >= 0 && oldPosition < lyricLines.size()) {
            notifyItemChanged(oldPosition);
        }
        if (newPosition >= 0 && newPosition < lyricLines.size()) {
            notifyItemChanged(newPosition);
        }
        return true;
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
        boolean isActive = (position == activePosition);
        holder.bind(line, isActive);
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

            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null && pos < lyricLines.size()) {
                    listener.onLyricClick(lyricLines.get(pos), pos);
                }
            });
        }

        public void bind(LyricLine line, boolean isActive) {
            tvLyricText.setText(line.getText());
            tvLyricText.animate().cancel();
            tvLyricText.setPivotX(0f);
            tvLyricText.setPivotY(tvLyricText.getHeight() > 0 ? (tvLyricText.getHeight() / 2f) : 30f);

            if (isActive) {
                // Đổi màu chữ nổi bật (Highlighting) với hiệu ứng phóng to mượt mà chuẩn GPU không gây nhảy layout
                tvLyricText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.spotify_green));
                tvLyricText.setTypeface(Typeface.DEFAULT_BOLD);
                tvLyricText.animate()
                        .scaleX(1.08f)
                        .scaleY(1.08f)
                        .alpha(1.0f)
                        .setDuration(240)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                        .start();
            } else {
                // Dòng chưa đến hoặc đã qua hát: Làm mờ êm dịu, thu về kích thước chuẩn
                tvLyricText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
                tvLyricText.setTypeface(Typeface.DEFAULT);
                tvLyricText.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(0.40f)
                        .setDuration(240)
                        .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                        .start();
            }
        }
    }
}
