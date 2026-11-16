package com.example.spotifyclone.presentation.history;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.spotifyclone.R;
import com.example.spotifyclone.data.local.SessionManager;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.HistoryDateGroup;
import com.example.spotifyclone.data.model.HistoryItem;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.databinding.ActivityListeningHistoryBinding;
import com.example.spotifyclone.player.MusicPlayerManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ListeningHistoryActivity extends AppCompatActivity implements HistoryGroupAdapter.OnHistoryActionListener {

    private ActivityListeningHistoryBinding binding;
    private HistoryGroupAdapter adapter;
    private final List<HistoryItem> rawHistoryList = new ArrayList<>();
    private final List<HistoryDateGroup> dateGroups = new ArrayList<>();
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListeningHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        com.example.spotifyclone.util.ThemeManager.applySystemBarsAppearance(this);

        sessionManager = SessionManager.getInstance(this);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        ApiClient.init(this);
        initViews();
        fetchHistory();
    }

    private void initViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnClearAll.setOnClickListener(v -> {
            if (rawHistoryList.isEmpty()) {
                Toast.makeText(this, "Lịch sử nghe nhạc đang trống", Toast.LENGTH_SHORT).show();
                return;
            }
            showConfirmDeleteAllDialog();
        });

        adapter = new HistoryGroupAdapter(this);
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvHistory.setAdapter(adapter);
    }

    private void fetchHistory() {
        binding.pbLoading.setVisibility(View.VISIBLE);
        binding.layoutEmpty.setVisibility(View.GONE);

        ApiClient.getApiService().getListeningHistory().enqueue(new Callback<ApiResponse<List<HistoryItem>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<HistoryItem>>> call, @NonNull Response<ApiResponse<List<HistoryItem>>> response) {
                binding.pbLoading.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<HistoryItem> apiList = response.body().getData();
                    processHistoryData(apiList);
                } else {
                    fallbackToLocalHistory();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<HistoryItem>>> call, @NonNull Throwable t) {
                binding.pbLoading.setVisibility(View.GONE);
                fallbackToLocalHistory();
            }
        });
    }

    private void fallbackToLocalHistory() {
        List<Song> localSongs = sessionManager.getRecentlyPlayedSongs();
        if (localSongs != null && !localSongs.isEmpty()) {
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            df.setTimeZone(java.util.TimeZone.getDefault());
            java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
            isoFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date now = new java.util.Date();
            String todayKey = df.format(now);
            String nowIso = isoFormat.format(now);

            List<HistoryItem> list = new ArrayList<>();
            for (Song s : localSongs) {
                HistoryItem item = new HistoryItem();
                item.setId("");
                item.setSong(s);
                item.setDate(todayKey);
                item.setTimestamp(nowIso);
                list.add(item);
            }
            processHistoryData(list);
        } else {
            showEmptyState();
        }
    }

    private void processHistoryData(List<HistoryItem> list) {
        rawHistoryList.clear();
        dateGroups.clear();

        if (list == null || list.isEmpty()) {
            showEmptyState();
            return;
        }

        rawHistoryList.addAll(list);

        // Nhóm theo ngày thực tế của máy (YYYY-MM-DD)
        Map<String, HistoryDateGroup> map = new LinkedHashMap<>();
        for (HistoryItem item : list) {
            String dateKey = item.getLocalDateKey();
            if (dateKey == null || dateKey.isEmpty()) {
                dateKey = item.getDate();
            }
            if (dateKey == null || dateKey.isEmpty()) {
                dateKey = "Gần đây";
            }
            if (!map.containsKey(dateKey)) {
                HistoryDateGroup group = new HistoryDateGroup(dateKey);
                // Ngày hiện tại theo máy: mở đầy đủ (expanded), các ngày khác: thu gọn (collapsed)
                group.setExpanded(group.isToday());
                map.put(dateKey, group);
            }
            map.get(dateKey).addItem(item);
        }

        dateGroups.addAll(map.values());
        adapter.setGroups(dateGroups);

        int totalCount = rawHistoryList.size();
        binding.tvTotalHistoryCount.setText(totalCount + " bài hát đã nghe");

        binding.layoutEmpty.setVisibility(View.GONE);
        binding.rvHistory.setVisibility(View.VISIBLE);
        binding.btnClearAll.setVisibility(View.VISIBLE);
    }

    private void showEmptyState() {
        rawHistoryList.clear();
        dateGroups.clear();
        adapter.setGroups(Collections.emptyList());

        binding.tvTotalHistoryCount.setText("0 bài hát");
        binding.rvHistory.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.VISIBLE);
        binding.btnClearAll.setVisibility(View.GONE);
    }

    @Override
    public void onSongClick(Song song, List<Song> dayQueue, int index) {
        if (song == null) return;
        if (dayQueue != null && !dayQueue.isEmpty()) {
            MusicPlayerManager.getInstance().playQueue(dayQueue, index);
        } else {
            MusicPlayerManager.getInstance().playSong(song);
        }
        Toast.makeText(this, "Đang phát: " + song.getTitle(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDeleteSong(HistoryItem item) {
        if (item == null) return;

        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Xóa bài hát khỏi lịch sử?")
                .setMessage("Bạn có chắc muốn xóa bài hát \"" + (item.getSong() != null ? item.getSong().getTitle() : "") + "\" khỏi lịch sử nghe nhạc?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> executeDeleteSong(item))
                .show();
    }

    private void removeHistoryItemFromUi(HistoryItem item) {
        if (item.getSong() != null && item.getSong().getId() != null) {
            sessionManager.removeRecentlyPlayedSong(item.getSong().getId());
        }
        rawHistoryList.remove(item);
        for (HistoryDateGroup group : dateGroups) {
            group.getItems().remove(item);
        }

        List<HistoryDateGroup> toRemove = new ArrayList<>();
        for (HistoryDateGroup group : dateGroups) {
            if (group.getItems().isEmpty()) {
                toRemove.add(group);
            }
        }
        dateGroups.removeAll(toRemove);

        if (rawHistoryList.isEmpty()) {
            showEmptyState();
        } else {
            adapter.setGroups(dateGroups);
            binding.tvTotalHistoryCount.setText(rawHistoryList.size() + " bài hát đã nghe");
        }
    }

    private void executeDeleteSong(HistoryItem item) {
        String historyId = item.getId();
        if (historyId == null || historyId.isEmpty()) {
            removeHistoryItemFromUi(item);
            Toast.makeText(this, "Đã xóa bài hát khỏi lịch sử", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiClient.getApiService().deleteHistoryItem(historyId).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Object>> call, @NonNull Response<ApiResponse<Object>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful()) {
                    removeHistoryItemFromUi(item);
                    Toast.makeText(ListeningHistoryActivity.this, "Đã xóa bài hát khỏi lịch sử", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ListeningHistoryActivity.this, "Không thể xóa bài hát khỏi lịch sử", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Object>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(ListeningHistoryActivity.this, "Lỗi kết nối khi xóa lịch sử", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDeleteDay(HistoryDateGroup group) {
        if (group == null) return;

        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Xóa lịch sử ngày này?")
                .setMessage("Tất cả các bài hát bạn đã nghe trong ngày \"" + group.getDisplayTitle() + "\" sẽ bị xóa khỏi lịch sử.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa ngày này", (dialog, which) -> executeDeleteDay(group))
                .show();
    }

    private void removeDayGroupFromUi(HistoryDateGroup group) {
        if (group.getItems() != null) {
            List<String> songIds = new ArrayList<>();
            for (HistoryItem hi : group.getItems()) {
                if (hi.getSong() != null && hi.getSong().getId() != null) {
                    songIds.add(hi.getSong().getId());
                }
            }
            sessionManager.removeRecentlyPlayedSongs(songIds);
            rawHistoryList.removeAll(group.getItems());
        }
        dateGroups.remove(group);

        if (rawHistoryList.isEmpty()) {
            showEmptyState();
        } else {
            adapter.setGroups(dateGroups);
            binding.tvTotalHistoryCount.setText(rawHistoryList.size() + " bài hát đã nghe");
        }
    }

    private void executeDeleteDay(HistoryDateGroup group) {
        String dateKey = group.getDateKey();
        if (dateKey == null || dateKey.isEmpty()) {
            removeDayGroupFromUi(group);
            Toast.makeText(this, "Đã xóa lịch sử ngày " + group.getDisplayTitle(), Toast.LENGTH_SHORT).show();
            return;
        }

        ApiClient.getApiService().deleteHistoryByDate(dateKey).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Object>> call, @NonNull Response<ApiResponse<Object>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful()) {
                    removeDayGroupFromUi(group);
                    Toast.makeText(ListeningHistoryActivity.this, "Đã xóa lịch sử ngày " + group.getDisplayTitle(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ListeningHistoryActivity.this, "Không thể xóa lịch sử ngày này", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Object>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(ListeningHistoryActivity.this, "Lỗi kết nối khi xóa lịch sử", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showConfirmDeleteAllDialog() {
        new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Xóa toàn bộ lịch sử nghe nhạc?")
                .setMessage("Toàn bộ các bài hát bạn đã từng nghe sẽ bị xóa vĩnh viễn khỏi lịch sử.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa tất cả", (dialog, which) -> executeDeleteAll())
                .show();
    }

    private void executeDeleteAll() {
        ApiClient.getApiService().deleteAllHistory(true).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Object>> call, @NonNull Response<ApiResponse<Object>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful()) {
                    sessionManager.clearRecentlyPlayedSongs();
                    showEmptyState();
                    Toast.makeText(ListeningHistoryActivity.this, "Đã xóa toàn bộ lịch sử nghe nhạc", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ListeningHistoryActivity.this, "Không thể xóa toàn bộ lịch sử", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Object>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(ListeningHistoryActivity.this, "Lỗi kết nối khi xóa lịch sử", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
