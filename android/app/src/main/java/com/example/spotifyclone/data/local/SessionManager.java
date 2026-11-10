package com.example.spotifyclone.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.model.User;

import java.util.ArrayList;
import java.util.List;

public class SessionManager {
    private static final String PREF_NAME = "spotify_session_prefs";
    private static final String KEY_TOKEN = "key_jwt_token";
    private static final String KEY_USER_ID = "key_user_id";
    private static final String KEY_USERNAME = "key_username";
    private static final String KEY_EMAIL = "key_email";
    private static final String KEY_DISPLAY_NAME = "key_display_name";
    private static final String KEY_AVATAR_URL = "key_avatar_url";
    private static final String KEY_ROLE = "key_role";

    private static volatile SessionManager instance;
    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
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
        prefs.edit().clear().apply();
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
}
