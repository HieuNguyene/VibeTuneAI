package com.example.spotifyclone;

import android.app.Application;
import android.content.Context;

public class SpotifyApp extends Application {
    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        com.example.spotifyclone.util.ThemeManager.applySavedTheme(this);
    }

    public static Context getAppContext() {
        return appContext;
    }
}
