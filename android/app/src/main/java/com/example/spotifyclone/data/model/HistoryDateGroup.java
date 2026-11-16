package com.example.spotifyclone.data.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class HistoryDateGroup implements Serializable {
    private String dateKey; // YYYY-MM-DD theo giờ máy
    private String displayTitle;
    private List<HistoryItem> items = new ArrayList<>();
    private boolean isExpanded = false;
    private boolean isToday = false;

    public HistoryDateGroup(String dateKey) {
        this.dateKey = dateKey;
        this.displayTitle = computeDisplayTitle(dateKey);
        this.isToday = checkIfToday(dateKey);
        // Ngày hiện tại theo máy: mặc định mở đầy đủ. Các ngày khác: mặc định gập lại để ngắn gọn.
        this.isExpanded = this.isToday;
    }

    public String getDateKey() { return dateKey != null ? dateKey : ""; }
    public String getDisplayTitle() { return displayTitle != null ? displayTitle : dateKey; }
    public List<HistoryItem> getItems() { return items; }
    public void setItems(List<HistoryItem> items) { this.items = items; }
    public void addItem(HistoryItem item) { this.items.add(item); }

    public boolean isExpanded() { return isExpanded; }
    public void setExpanded(boolean expanded) { this.isExpanded = expanded; }
    public boolean isToday() { return isToday; }

    public static boolean checkIfToday(String dateKey) {
        if (dateKey == null || dateKey.isEmpty()) return false;
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            df.setTimeZone(TimeZone.getDefault());
            Date date = df.parse(dateKey);
            if (date == null) return false;

            Calendar calItem = Calendar.getInstance();
            calItem.setTime(date);

            Calendar calToday = Calendar.getInstance();
            return calToday.get(Calendar.YEAR) == calItem.get(Calendar.YEAR) &&
                   calToday.get(Calendar.DAY_OF_YEAR) == calItem.get(Calendar.DAY_OF_YEAR);
        } catch (Exception e) {
            return false;
        }
    }

    public static String computeDisplayTitle(String dateKey) {
        if (dateKey == null || dateKey.isEmpty()) return "Lịch sử";
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            df.setTimeZone(TimeZone.getDefault());
            Date date = df.parse(dateKey);
            if (date == null) return dateKey;

            Calendar calItem = Calendar.getInstance();
            calItem.setTime(date);

            SimpleDateFormat dayMonthYearFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            dayMonthYearFormat.setTimeZone(TimeZone.getDefault());

            Calendar calToday = Calendar.getInstance();
            if (calToday.get(Calendar.YEAR) == calItem.get(Calendar.YEAR) &&
                calToday.get(Calendar.DAY_OF_YEAR) == calItem.get(Calendar.DAY_OF_YEAR)) {
                return "Hôm nay | " + dayMonthYearFormat.format(date);
            }

            Calendar calYesterday = Calendar.getInstance();
            calYesterday.add(Calendar.DAY_OF_YEAR, -1);
            if (calYesterday.get(Calendar.YEAR) == calItem.get(Calendar.YEAR) &&
                calYesterday.get(Calendar.DAY_OF_YEAR) == calItem.get(Calendar.DAY_OF_YEAR)) {
                return "Hôm qua | " + dayMonthYearFormat.format(date);
            }

            return dayMonthYearFormat.format(date);
        } catch (Exception e) {
            return dateKey;
        }
    }
}
