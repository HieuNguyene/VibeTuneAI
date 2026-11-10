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
        List<LyricLine> list = new ArrayList<>();
        if (lrcContent == null || lrcContent.trim().isEmpty()) {
            return list;
        }

        String[] rawLines = lrcContent.split("\\r?\\n");

        for (String raw : rawLines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Bỏ qua các tag thông tin metadata như [ar:Ca sĩ], [ti:Tên bài]
            if (METADATA_PATTERN.matcher(line).matches()) {
                continue;
            }

            Matcher matcher = TIME_TAG_PATTERN.matcher(line);
            int lastMatchEnd = 0;
            boolean foundTag = false;

            while (matcher.find()) {
                foundTag = true;
                lastMatchEnd = matcher.end();
            }

            if (foundTag) {
                String text = line.substring(lastMatchEnd).trim();
                if (text.isEmpty()) {
                    text = "♪";
                }
                list.add(new LyricLine(-1, text));
            } else {
                // Dòng plain text không có mốc thời gian
                list.add(new LyricLine(-1, line));
            }
        }

        return list;
    }
}
