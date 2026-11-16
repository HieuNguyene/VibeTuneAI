package com.example.spotifyclone.data.model;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class NotificationDateGroup {
    private final String dateKey; // YYYY-MM-DD
    private final List<NotificationItem> items = new ArrayList<>();

    public NotificationDateGroup(String dateKey) {
        this.dateKey = dateKey != null ? dateKey : "Gần đây";
    }

    public String getDateKey() {
        return dateKey;
    }

    public List<NotificationItem> getItems() {
        return items;
    }

    public void addItem(NotificationItem item) {
        if (item != null) items.add(item);
    }

    public int getUnreadCount() {
        int count = 0;
        for (NotificationItem item : items) {
            if (!item.isRead()) count++;
        }
        return count;
    }

    public String getDisplayTitle() {
        if (dateKey == null || dateKey.isEmpty() || dateKey.equals("Gần đây")) {
            return "Gần đây";
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getDefault());
            Date date = sdf.parse(dateKey);
            if (date == null) return dateKey;

            Calendar itemCal = Calendar.getInstance();
            itemCal.setTime(date);

            Calendar todayCal = Calendar.getInstance();
            if (isSameDay(itemCal, todayCal)) {
                return "Hôm nay";
            }

            Calendar yesterdayCal = Calendar.getInstance();
            yesterdayCal.add(Calendar.DAY_OF_YEAR, -1);
            if (isSameDay(itemCal, yesterdayCal)) {
                return "Hôm qua";
            }

            SimpleDateFormat outFormat = new SimpleDateFormat("dd 'tháng' MM, yyyy", new Locale("vi", "VN"));
            return outFormat.format(date);
        } catch (Exception e) {
            return dateKey;
        }
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
}
