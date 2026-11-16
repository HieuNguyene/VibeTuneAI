package com.example.spotifyclone.player;

import com.example.spotifyclone.data.model.LyricLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LrcParser {

    private static final Pattern TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[\\.:](\\d{1,3}))?\\]");
    private static final Pattern METADATA_PATTERN = Pattern.compile("^\\[(ti|ar|al|by|offset|length):.*\\]$", Pattern.CASE_INSENSITIVE);

    public static List<LyricLine> parse(String lrcContent) {
        return parse(lrcContent, 0);
    }

    public static List<LyricLine> parse(String lrcContent, long durationMs) {
        List<LyricLine> list = new ArrayList<>();
        if (lrcContent == null || lrcContent.trim().isEmpty()) {
            return list;
        }

        String[] rawLines = lrcContent.split("\\r?\\n");
        boolean hasExplicitTimestamps = false;
        List<String> plainLines = new ArrayList<>();

        for (String raw : rawLines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Bỏ qua các tag metadata [ar:...], [ti:...], [offset:...], v.v.
            if (METADATA_PATTERN.matcher(line).matches()) {
                continue;
            }

            Matcher matcher = TIME_TAG_PATTERN.matcher(line);
            List<Long> timestamps = new ArrayList<>();
            int lastMatchEnd = 0;

            while (matcher.find()) {
                try {
                    long minutes = Long.parseLong(matcher.group(1));
                    long seconds = Long.parseLong(matcher.group(2));
                    long millis = 0;
                    String msGroup = matcher.group(3);
                    if (msGroup != null && !msGroup.isEmpty()) {
                        if (msGroup.length() == 1) {
                            millis = Long.parseLong(msGroup) * 100;
                        } else if (msGroup.length() == 2) {
                            millis = Long.parseLong(msGroup) * 10;
                        } else {
                            millis = Long.parseLong(msGroup.substring(0, Math.min(3, msGroup.length())));
                        }
                    }
                    long timeMs = (minutes * 60 + seconds) * 1000 + millis;
                    timestamps.add(timeMs);
                    lastMatchEnd = matcher.end();
                } catch (Exception ignored) {
                }
            }

            if (!timestamps.isEmpty()) {
                hasExplicitTimestamps = true;
                String text = line.substring(lastMatchEnd).trim();
                if (text.isEmpty()) {
                    text = "♪";
                }
                for (Long ts : timestamps) {
                    list.add(new LyricLine(ts, text));
                }
            } else {
                plainLines.add(line);
            }
        }

        // Trường hợp 1: Có mốc thời gian LRC chuẩn
        if (hasExplicitTimestamps) {
            Collections.sort(list, (a, b) -> Long.compare(a.getTimestampMs(), b.getTimestampMs()));
            return list;
        }

        // Trường hợp 2: Lời bài hát dạng văn bản thường (Plain-text)
        // Tự động phân bổ mốc thời gian dựa theo trọng số độ dài câu (Character-length weighted interpolation)
        // kết hợp trừ hao nhạc dạo đầu (intro) giúp lời khớp với nhịp điệu bài hát
        if (!plainLines.isEmpty()) {
            int lineCount = plainLines.size();
            long totalMs = durationMs > 10000 ? durationMs : (lineCount * 4200L);
            long startDelay = Math.min(16000L, Math.max(5000L, totalMs / 15));
            long endPadding = Math.min(10000L, Math.max(4000L, totalMs / 20));
            long activeSpan = Math.max(1000L, totalMs - startDelay - endPadding);

            int totalWeight = 0;
            int[] weights = new int[lineCount];
            for (int i = 0; i < lineCount; i++) {
                int len = plainLines.get(i).trim().length();
                int w = Math.min(80, Math.max(12, len));
                weights[i] = w;
                totalWeight += w;
            }

            long accumulatedMs = startDelay;
            for (int i = 0; i < lineCount; i++) {
                list.add(new LyricLine(accumulatedMs, plainLines.get(i)));
                long lineDuration = (long) ((double) weights[i] / totalWeight * activeSpan);
                accumulatedMs += Math.max(1800L, lineDuration);
            }
            return list;
        }

        return list;
    }

    /**
     * Thuật toán tìm kiếm nhị phân (Binary Search) xác định chính xác dòng lời bài hát
     * đang được xướng lên tại thời điểm currentPositionMs (Độ phức tạp O(log N)).
     *
     * @param lines             Danh sách dòng lời bài hát đã sắp xếp theo timestampMs
     * @param currentPositionMs Vị trí phát hiện tại tính bằng mili-giây
     * @return Chỉ số (index) dòng lời đang phát, hoặc -1 nếu danh sách rỗng
     */
    public static int findActiveLineIndex(List<LyricLine> lines, long currentPositionMs) {
        if (lines == null || lines.isEmpty()) {
            return -1;
        }

        // Nếu vị trí nhạc hiện tại trước dòng đầu tiên (đoạn intro nhạc dạo đầu)
        // Chưa có ca từ được xướng lên -> Trả về -1 (chưa kích hoạt dòng nào)
        if (currentPositionMs < lines.get(0).getTimestampMs()) {
            return -1;
        }

        // Nếu vị trí nhạc hiện tại đã vượt qua hoặc bằng dòng cuối cùng
        int size = lines.size();
        if (currentPositionMs >= lines.get(size - 1).getTimestampMs()) {
            return size - 1;
        }

        // Binary Search O(log N)
        int low = 0;
        int high = size - 1;
        int result = -1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long time = lines.get(mid).getTimestampMs();

            if (time <= currentPositionMs) {
                result = mid; // Ứng viên dòng hợp lệ gần nhất
                low = mid + 1; // Tiếp tục tìm dòng muộn hơn nhưng vẫn <= currentPositionMs
            } else {
                high = mid - 1;
            }
        }

        return result;
    }
}
