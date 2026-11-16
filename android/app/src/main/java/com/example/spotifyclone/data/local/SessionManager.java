package com.example.spotifyclone.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.model.User;

import java.util.ArrayList;
import java.util.List;

public class SessionManager {
    private static final String PREF_NAME = "vibetune_session_prefs";
    private static final String LEGACY_PREF_NAME = "spotify_session_prefs";
    private static final String KEY_TOKEN = "key_jwt_token";
    private static final String KEY_USER_ID = "key_user_id";
    private static final String KEY_USERNAME = "key_username";
    private static final String KEY_EMAIL = "key_email";
    private static final String KEY_DISPLAY_NAME = "key_display_name";
    private static final String KEY_AVATAR_URL = "key_avatar_url";
    private static final String KEY_ROLE = "key_role";
    private static final String KEY_STREAMING_QUALITY = "key_streaming_quality";

    private static volatile SessionManager instance;
    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        Context appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences legacyPrefs = appContext.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE);
        if (legacyPrefs.contains(KEY_TOKEN) && !prefs.contains(KEY_TOKEN)) {
            SharedPreferences.Editor editor = prefs.edit();
            for (java.util.Map.Entry<String, ?> entry : legacyPrefs.getAll().entrySet()) {
                Object val = entry.getValue();
                if (val instanceof String) editor.putString(entry.getKey(), (String) val);
                else if (val instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) val);
                else if (val instanceof Integer) editor.putInt(entry.getKey(), (Integer) val);
                else if (val instanceof Long) editor.putLong(entry.getKey(), (Long) val);
                else if (val instanceof Float) editor.putFloat(entry.getKey(), (Float) val);
            }
            editor.apply();
            legacyPrefs.edit().clear().apply();
        }
    }

    public static SessionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager(context);
                }
            }
        }
        return instance;
    }

    public void saveAuth(String token, User user) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_TOKEN, token);
        if (user != null) {
            editor.putString(KEY_USER_ID, user.getId());
            editor.putString(KEY_USERNAME, user.getUsername());
            editor.putString(KEY_EMAIL, user.getEmail());
            editor.putString(KEY_DISPLAY_NAME, user.getDisplayName());
            editor.putString(KEY_AVATAR_URL, user.getAvatarUrl());
            editor.putString(KEY_ROLE, user.getRole());
            if (user.getStreamingQuality() != null && !user.getStreamingQuality().isEmpty()) {
                editor.putString(KEY_STREAMING_QUALITY, user.getStreamingQuality());
            }
        }
        editor.apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public boolean isLoggedIn() {
        String token = getToken();
        return token != null && !token.trim().isEmpty();
    }

    public User getUser() {
        if (!isLoggedIn()) {
            return null;
        }
        String id = prefs.getString(KEY_USER_ID, "");
        String username = prefs.getString(KEY_USERNAME, "");
        String email = prefs.getString(KEY_EMAIL, "");
        String displayName = prefs.getString(KEY_DISPLAY_NAME, "");
        String avatarUrl = prefs.getString(KEY_AVATAR_URL, "");
        String role = prefs.getString(KEY_ROLE, "user");

        return new User(id, username, email, displayName, avatarUrl, role);
    }

    public void updateUser(User user) {
        if (user != null) {
            SharedPreferences.Editor editor = prefs.edit();
            if (user.getId() != null) editor.putString(KEY_USER_ID, user.getId());
            if (user.getUsername() != null) editor.putString(KEY_USERNAME, user.getUsername());
            if (user.getEmail() != null) editor.putString(KEY_EMAIL, user.getEmail());
            if (user.getDisplayName() != null) editor.putString(KEY_DISPLAY_NAME, user.getDisplayName());
            if (user.getAvatarUrl() != null) editor.putString(KEY_AVATAR_URL, user.getAvatarUrl());
            if (user.getRole() != null) editor.putString(KEY_ROLE, user.getRole());
            editor.apply();
        }
    }

    public void clearSession() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_EMAIL)
                .remove(KEY_DISPLAY_NAME)
                .remove(KEY_AVATAR_URL)
                .remove(KEY_ROLE)
                .remove(KEY_RECENT_SONGS)
                .apply();
    }

    private static final String KEY_RECENT_SONGS = "key_recent_songs_json";

    public synchronized void saveRecentlyPlayedSong(com.example.spotifyclone.data.model.Song song) {
        if (song == null || song.getId() == null || song.getId().isEmpty()) return;
        List<com.example.spotifyclone.data.model.Song> list = getRecentlyPlayedSongs();
        for (int i = 0; i < list.size(); i++) {
            if (song.getId().equals(list.get(i).getId())) {
                list.remove(i);
                break;
            }
        }
        list.add(0, song);
        if (list.size() > 20) {
            list = list.subList(0, 20);
        }
        String json = new com.google.gson.Gson().toJson(list);
        prefs.edit().putString(KEY_RECENT_SONGS, json).apply();
    }

    public synchronized java.util.List<com.example.spotifyclone.data.model.Song> getRecentlyPlayedSongs() {
        String json = prefs.getString(KEY_RECENT_SONGS, null);
        if (json == null || json.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        try {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.ArrayList<com.example.spotifyclone.data.model.Song>>(){}.getType();
            java.util.List<com.example.spotifyclone.data.model.Song> list = new com.google.gson.Gson().fromJson(json, type);
            return list != null ? list : new java.util.ArrayList<>();
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    public synchronized void removeRecentlyPlayedSong(String songId) {
        if (songId == null || songId.isEmpty()) return;
        java.util.List<com.example.spotifyclone.data.model.Song> list = getRecentlyPlayedSongs();
        java.util.List<com.example.spotifyclone.data.model.Song> filtered = new java.util.ArrayList<>();
        for (com.example.spotifyclone.data.model.Song s : list) {
            if (s != null && !songId.equals(s.getId())) {
                filtered.add(s);
            }
        }
        prefs.edit().putString(KEY_RECENT_SONGS, new com.google.gson.Gson().toJson(filtered)).apply();
    }

    public synchronized void removeRecentlyPlayedSongs(java.util.Collection<String> songIds) {
        if (songIds == null || songIds.isEmpty()) return;
        java.util.Set<String> idSet = new java.util.HashSet<>(songIds);
        java.util.List<com.example.spotifyclone.data.model.Song> list = getRecentlyPlayedSongs();
        java.util.List<com.example.spotifyclone.data.model.Song> filtered = new java.util.ArrayList<>();
        for (com.example.spotifyclone.data.model.Song s : list) {
            if (s != null && !idSet.contains(s.getId())) {
                filtered.add(s);
            }
        }
        prefs.edit().putString(KEY_RECENT_SONGS, new com.google.gson.Gson().toJson(filtered)).apply();
    }

    public synchronized void clearRecentlyPlayedSongs() {
        prefs.edit().remove(KEY_RECENT_SONGS).apply();
    }

    public synchronized void syncRecentlyPlayedMetadata(java.util.Collection<Song> latestCatalogSongs) {
        if (latestCatalogSongs == null || latestCatalogSongs.isEmpty()) return;
        java.util.List<Song> list = getRecentlyPlayedSongs();
        if (list.isEmpty()) return;
        java.util.Map<String, Song> latestMap = new java.util.HashMap<>();
        for (Song fresh : latestCatalogSongs) {
            if (fresh != null && fresh.getId() != null && !fresh.getId().isEmpty()) {
                latestMap.put(fresh.getId(), fresh);
            }
        }
        if (latestMap.isEmpty()) return;
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            Song cached = list.get(i);
            if (cached == null || cached.getId() == null) continue;
            Song fresh = latestMap.get(cached.getId());
            if (fresh != null) {
                list.set(i, fresh);
                changed = true;
            }
        }
        if (changed) {
            prefs.edit().putString(KEY_RECENT_SONGS, new com.google.gson.Gson().toJson(list)).apply();
        }
    }

    private static final String KEY_SERVER_URL = "key_server_url";

    public void setCustomServerUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            prefs.edit().putString(KEY_SERVER_URL, url.trim()).apply();
        } else {
            prefs.edit().remove(KEY_SERVER_URL).apply();
        }
    }

    public String getCustomServerUrl() {
        return prefs.getString(KEY_SERVER_URL, null);
    }

    public void saveStreamingQuality(String quality) {
        if (quality != null && !quality.trim().isEmpty()) {
            prefs.edit().putString(KEY_STREAMING_QUALITY, quality.trim()).apply();
        }
    }

    public String getStreamingQuality() {
        return prefs.getString(KEY_STREAMING_QUALITY, "normal");
    }
}
