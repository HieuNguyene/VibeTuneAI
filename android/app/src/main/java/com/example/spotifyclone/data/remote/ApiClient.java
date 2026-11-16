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

    public static synchronized void resetApiService() {
        apiService = null;
        retrofit = null;
    }

    // Tu dong nhan dien May Ao (Emulator: 10.0.2.2) hay Dien thoai that (Wi-Fi/Hotspot may tinh: 192.168.43.114)
    public static String getBaseUrl() {
        if (appContext != null) {
            String custom = SessionManager.getInstance(appContext).getCustomServerUrl();
            if (custom != null && !custom.trim().isEmpty()) {
                return custom.trim().endsWith("/") ? custom.trim() : custom.trim() + "/";
            }
        }
        boolean isEmulator = Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.PRODUCT.contains("sdk");

        return isEmulator ? "http://10.0.2.2:5000/" : "http://192.168.43.114:5000/";
    }

    public static synchronized ApiService getApiService() {
        if (apiService == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        Request.Builder requestBuilder = original.newBuilder();

                        if (appContext != null) {
                            String token = SessionManager.getInstance(appContext).getToken();
                            if (token != null && !token.isEmpty() && original.header("Authorization") == null) {
                                requestBuilder.header("Authorization", "Bearer " + token);
                            }
                        }
                        if (original.header("x-timezone-offset") == null) {
                            int offsetMinutes = -java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000;
                            requestBuilder.header("x-timezone-offset", String.valueOf(offsetMinutes));
                        }
                        return chain.proceed(requestBuilder.build());
                    })
                    .addInterceptor(logging)
                    .build();

            com.google.gson.TypeAdapterFactory stringOrObjectTypeAdapterFactory = new com.google.gson.TypeAdapterFactory() {
                @Override
                public <T> com.google.gson.TypeAdapter<T> create(com.google.gson.Gson gsonInstance, com.google.gson.reflect.TypeToken<T> type) {
                    Class<?> rawType = type.getRawType();
                    if (rawType != com.example.spotifyclone.data.model.Album.class
                            && rawType != com.example.spotifyclone.data.model.Artist.class) {
                        return null;
                    }
                    com.google.gson.TypeAdapter<T> delegate = gsonInstance.getDelegateAdapter(this, type);
                    return new com.google.gson.TypeAdapter<T>() {
                        @Override
                        public void write(com.google.gson.stream.JsonWriter out, T value) throws java.io.IOException {
                            delegate.write(out, value);
                        }

                        @Override
                        public T read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                            if (in.peek() == com.google.gson.stream.JsonToken.STRING) {
                                String str = in.nextString();
                                if (rawType == com.example.spotifyclone.data.model.Album.class) {
                                    com.example.spotifyclone.data.model.Album album = new com.example.spotifyclone.data.model.Album();
                                    album.setId(str);
                                    return (T) album;
                                } else if (rawType == com.example.spotifyclone.data.model.Artist.class) {
                                    com.example.spotifyclone.data.model.Artist artist = new com.example.spotifyclone.data.model.Artist();
                                    artist.setId(str);
                                    return (T) artist;
                                }
                            } else if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                                in.nextNull();
                                return null;
                            }
                            return delegate.read(in);
                        }
                    };
                }
            };

            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                    .registerTypeAdapterFactory(stringOrObjectTypeAdapterFactory)
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
