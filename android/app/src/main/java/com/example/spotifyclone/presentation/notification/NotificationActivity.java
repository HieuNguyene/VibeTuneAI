package com.example.spotifyclone.presentation.notification;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.spotifyclone.R;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.NotificationDateGroup;
import com.example.spotifyclone.data.model.NotificationItem;
import com.example.spotifyclone.data.model.NotificationListResponse;
import com.example.spotifyclone.data.remote.ApiClient;
import com.example.spotifyclone.data.remote.NotificationWebSocketManager;
import com.example.spotifyclone.databinding.ActivityNotificationBinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationActivity extends AppCompatActivity {
    private ActivityNotificationBinding binding;
    private NotificationGroupAdapter adapter;
    private final List<NotificationItem> rawNotificationList = new ArrayList<>();
    private final List<NotificationDateGroup> dateGroups = new ArrayList<>();

    private final NotificationWebSocketManager.OnNotificationEventListener wsListener =
            new NotificationWebSocketManager.OnNotificationEventListener() {
                @Override
                public void onConnected(int unreadCount) {
                    updateUnreadBadge(unreadCount);
                }

                @Override
                public void onNewNotification(NotificationItem notification, int unreadCount) {
                    if (notification != null) {
                        rawNotificationList.add(0, notification);
                        processNotificationData(new ArrayList<>(rawNotificationList));
                    }
                    updateUnreadBadge(unreadCount);
                }

                @Override
                public void onNotificationRead(String notificationId, int unreadCount) {
                    if (notificationId != null) {
                        for (NotificationItem item : rawNotificationList) {
                            if (notificationId.equals(item.getId())) {
                                item.setRead(true);
                                break;
                            }
                        }
                        processNotificationData(new ArrayList<>(rawNotificationList));
                    }
                    updateUnreadBadge(unreadCount);
                }

                @Override
                public void onAllNotificationsRead(int unreadCount) {
                    for (NotificationItem item : rawNotificationList) {
                        item.setRead(true);
                    }
                    processNotificationData(new ArrayList<>(rawNotificationList));
                    updateUnreadBadge(0);
                }

                @Override
                public void onNotificationDeleted(String notificationId, int unreadCount) {
                    if (notificationId != null) {
                        rawNotificationList.removeIf(item -> notificationId.equals(item.getId()));
                        processNotificationData(new ArrayList<>(rawNotificationList));
                    }
                    updateUnreadBadge(unreadCount);
                }
            };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.example.spotifyclone.util.ThemeManager.applySystemBarsAppearance(this);
        ApiClient.init(this);
        binding = ActivityNotificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        setupViews();
        fetchNotifications();
    }

    private void setupViews() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnRefresh.setOnClickListener(v -> fetchNotifications());

        binding.btnMarkAllRead.setOnClickListener(v -> markAllAsRead());

        adapter = new NotificationGroupAdapter((item, position) -> {
            android.util.Log.d("NotificationActivity", "User tapped notification: " + item.getTitle());
            // Optimistically mark as read
            if (!item.isRead()) {
                item.setRead(true);
                markItemAsRead(item);
                adapter.notifyDataSetChanged();
            }

            // Show bottom sheet detail
            NotificationDetailBottomSheet sheet = NotificationDetailBottomSheet.newInstance(item);
            sheet.setActionListener(deletedItem -> {
                rawNotificationList.removeIf(it -> it.getId() != null && it.getId().equals(deletedItem.getId()));
                processNotificationData(new ArrayList<>(rawNotificationList));
                fetchUnreadCount();
            });
            sheet.show(getSupportFragmentManager(), "NotificationDetailSheet");
        });

        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotifications.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NotificationWebSocketManager.getInstance(this).registerListener(wsListener);
        NotificationWebSocketManager.getInstance(this).connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        NotificationWebSocketManager.getInstance(this).unregisterListener(wsListener);
    }

    private void fetchNotifications() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.layoutEmpty.setVisibility(View.GONE);

        ApiClient.getApiService().getNotifications(1, 100).enqueue(new Callback<ApiResponse<NotificationListResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<NotificationListResponse>> call,
                                   @NonNull Response<ApiResponse<NotificationListResponse>> response) {
                binding.progressBar.setVisibility(View.GONE);
                android.util.Log.d("NotificationActivity", "fetchNotifications onResponse: code=" + response.code());
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    NotificationListResponse listResp = response.body().getData();
                    android.util.Log.d("NotificationActivity", "totalCount=" + listResp.getTotalCount() + ", items=" + listResp.getItems().size());
                    updateUnreadBadge(listResp.getUnreadCount());
                    processNotificationData(listResp.getItems());
                } else {
                    processNotificationData(new ArrayList<>());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<NotificationListResponse>> call, @NonNull Throwable t) {
                binding.progressBar.setVisibility(View.GONE);
                android.util.Log.e("NotificationActivity", "fetchNotifications onFailure: " + t.getMessage(), t);
                Toast.makeText(NotificationActivity.this, "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                if (rawNotificationList.isEmpty()) {
                    showEmptyState();
                }
            }
        });
    }

    private void fetchUnreadCount() {
        ApiClient.getApiService().getUnreadNotificationCount().enqueue(new Callback<ApiResponse<com.example.spotifyclone.data.model.UnreadCountData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<com.example.spotifyclone.data.model.UnreadCountData>> call,
                                   @NonNull Response<ApiResponse<com.example.spotifyclone.data.model.UnreadCountData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    int unread = response.body().getData().getUnreadCount();
                    updateUnreadBadge(unread);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<com.example.spotifyclone.data.model.UnreadCountData>> call, @NonNull Throwable t) {}
        });
    }

    private void markItemAsRead(NotificationItem item) {
        if (item.getId() == null) return;
        ApiClient.getApiService().markNotificationAsRead(item.getId()).enqueue(new Callback<ApiResponse<NotificationItem>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<NotificationItem>> call, @NonNull Response<ApiResponse<NotificationItem>> response) {
                fetchUnreadCount();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<NotificationItem>> call, @NonNull Throwable t) {}
        });
    }

    private void markAllAsRead() {
        ApiClient.getApiService().markAllNotificationsAsRead().enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Object>> call, @NonNull Response<ApiResponse<Object>> response) {
                for (NotificationItem item : rawNotificationList) {
                    item.setRead(true);
                }
                processNotificationData(new ArrayList<>(rawNotificationList));
                updateUnreadBadge(0);
                Toast.makeText(NotificationActivity.this, "Đã đánh dấu tất cả đã đọc", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Object>> call, @NonNull Throwable t) {
                Toast.makeText(NotificationActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUnreadBadge(int count) {
        if (count > 0) {
            binding.tvUnreadBadge.setText(count + " mới");
            binding.tvUnreadBadge.setVisibility(View.VISIBLE);
        } else {
            binding.tvUnreadBadge.setVisibility(View.GONE);
        }
    }

    private void processNotificationData(List<NotificationItem> list) {
        rawNotificationList.clear();
        dateGroups.clear();

        if (list == null || list.isEmpty()) {
            showEmptyState();
            return;
        }

        rawNotificationList.addAll(list);

        // Group by localDateKey (e.g. YYYY-MM-DD)
        Map<String, NotificationDateGroup> map = new LinkedHashMap<>();
        for (NotificationItem item : list) {
            String dateKey = item.getLocalDateKey();
            if (dateKey == null || dateKey.isEmpty()) {
                dateKey = "Gần đây";
            }
            if (!map.containsKey(dateKey)) {
                map.put(dateKey, new NotificationDateGroup(dateKey));
            }
            map.get(dateKey).addItem(item);
        }

        dateGroups.addAll(map.values());
        adapter.setGroups(dateGroups);

        binding.layoutEmpty.setVisibility(View.GONE);
        binding.rvNotifications.setVisibility(View.VISIBLE);
    }

    private void showEmptyState() {
        rawNotificationList.clear();
        dateGroups.clear();
        adapter.setGroups(new ArrayList<>());
        binding.rvNotifications.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.VISIBLE);
        binding.tvUnreadBadge.setVisibility(View.GONE);
    }
}
