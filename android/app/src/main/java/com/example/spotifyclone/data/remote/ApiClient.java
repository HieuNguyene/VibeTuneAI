package com.example.spotifyclone.data.remote;

import android.content.Context;
import android.os.Build;

import com.example.spotifyclone.data.local.SessionManager;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static Context appContext;
    private static Retrofit retrofit = null;
    private static ApiService apiService = null;

    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    // Tu dong nhan dien May Ao (Emulator: 10.0.2.2) hay Dien thoai that (Wi-Fi may tinh: 192.168.1.9)
    public static String getBaseUrl() {
        boolean isEmulator = Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.PRODUCT.contains("sdk");

        return isEmulator ? "http://10.0.2.2:5000/" : "http://192.168.1.9:5000/";
    }

    public static synchronized ApiService getApiService() {
        if (apiService == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        Request.Builder requestBuilder = original.newBuilder();

                        if (appContext != null) {
                            String token = SessionManager.getInstance(appContext).getToken();
                            if (token != null && !token.isEmpty() && original.header("Authorization") == null) {
                                requestBuilder.header("Authorization", "Bearer " + token);
                            }
                        }
                        return chain.proceed(requestBuilder.build());
                    })
                    .addInterceptor(logging)
                    .build();

            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                    .registerTypeAdapter(com.example.spotifyclone.data.model.Album.class,
                            (com.google.gson.JsonDeserializer<com.example.spotifyclone.data.model.Album>) (json, typeOfT, context) -> {
                                if (json.isJsonObject()) {
                                    return new com.google.gson.Gson().fromJson(json, com.example.spotifyclone.data.model.Album.class);
                                } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                                    com.example.spotifyclone.data.model.Album album = new com.example.spotifyclone.data.model.Album();
                                    album.setId(json.getAsString());
                                    return album;
                                }
                                return null;
                            })
                    .registerTypeAdapter(com.example.spotifyclone.data.model.Artist.class,
                            (com.google.gson.JsonDeserializer<com.example.spotifyclone.data.model.Artist>) (json, typeOfT, context) -> {
                                if (json.isJsonObject()) {
                                    return new com.google.gson.Gson().fromJson(json, com.example.spotifyclone.data.model.Artist.class);
                                } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                                    com.example.spotifyclone.data.model.Artist artist = new com.example.spotifyclone.data.model.Artist();
                                    artist.setId(json.getAsString());
                                    return artist;
                                }
                                return null;
                            })
                    .create();

            retrofit = new Retrofit.Builder()
                    .baseUrl(getBaseUrl())
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();

            apiService = retrofit.create(ApiService.class);
        }
        return apiService;
    }

    public static String resolveMediaUrl(String path) {
        if (path == null || path.isEmpty()) {
            return "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300";
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String host = getBaseUrl();
        if (host.endsWith("/") && path.startsWith("/")) {
            return host.substring(0, host.length() - 1) + path;
        }
        return host + path;
    }
}
