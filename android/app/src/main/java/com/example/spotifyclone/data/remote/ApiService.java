package com.example.spotifyclone.data.remote;

import com.example.spotifyclone.data.model.Album;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.ArtistDetailData;
import com.example.spotifyclone.data.model.AuthResponse;
import com.example.spotifyclone.data.model.ChangePasswordRequest;
import com.example.spotifyclone.data.model.GoogleLoginRequest;
import com.example.spotifyclone.data.model.HomeFeedData;
import com.example.spotifyclone.data.model.LoginRequest;
import com.example.spotifyclone.data.model.RegisterRequest;
import com.example.spotifyclone.data.model.SearchResultData;
import com.example.spotifyclone.data.model.SendOtpRequest;
import com.example.spotifyclone.data.model.SendOtpResponse;
import com.example.spotifyclone.data.model.Song;
import com.example.spotifyclone.data.model.VerifyOtpRequest;
import com.example.spotifyclone.data.model.AiChatRequest;
import com.example.spotifyclone.data.model.AiChatResponseData;
import com.example.spotifyclone.data.model.AiGenerateRequest;
import com.example.spotifyclone.data.model.AiGenerateResponseData;
import com.example.spotifyclone.data.model.CreatePlaylistRequest;
import com.example.spotifyclone.data.model.FavoritesData;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.model.ToggleFavoriteData;
import com.example.spotifyclone.data.model.UpdateProfileRequest;
import com.example.spotifyclone.data.model.User;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @POST("api/v1/auth/login")
    Call<ApiResponse<AuthResponse>> login(@Body LoginRequest request);

    @POST("api/v1/auth/register")
    Call<ApiResponse<AuthResponse>> register(@Body RegisterRequest request);

    @POST("api/v1/auth/send-otp")
    Call<ApiResponse<SendOtpResponse>> sendPhoneOtp(@Body SendOtpRequest request);

    @POST("api/v1/auth/verify-otp")
    Call<ApiResponse<AuthResponse>> verifyPhoneOtp(@Body VerifyOtpRequest request);

    @POST("api/v1/auth/google")
    Call<ApiResponse<AuthResponse>> loginWithGoogle(@Body GoogleLoginRequest request);

    @GET("api/v1/auth/me")
    Call<ApiResponse<User>> getMe(@Header("Authorization") String token);

    @PUT("api/v1/auth/profile")
    Call<ApiResponse<User>> updateProfile(@Body UpdateProfileRequest request);

    @PUT("api/v1/auth/password")
    Call<ApiResponse<Object>> changePassword(@Body ChangePasswordRequest request);

    @GET("api/v1/songs/feed")
    Call<ApiResponse<HomeFeedData>> getHomeFeed();

    @GET("api/v1/songs/search")
    Call<ApiResponse<SearchResultData>> searchSongs(@Query("q") String query);

    @GET("api/v1/songs/artist/{id}")
    Call<ApiResponse<ArtistDetailData>> getSongsByArtist(@Path("id") String artistId);

    @GET("api/v1/songs/favorites")
    Call<ApiResponse<FavoritesData>> getFavorites();

    @POST("api/v1/songs/{id}/favorite")
    Call<ApiResponse<ToggleFavoriteData>> toggleFavorite(@Path("id") String songId);

    @GET("api/v1/playlists/my-playlists")
    Call<ApiResponse<List<Playlist>>> getMyPlaylists();

    @POST("api/v1/playlists")
    Call<ApiResponse<Playlist>> createPlaylist(@Body CreatePlaylistRequest request);

    @GET("api/v1/playlists/{id}")
    Call<ApiResponse<Playlist>> getPlaylistDetails(@Path("id") String playlistId);

    @POST("api/v1/playlists/{id}/tracks")
    Call<ApiResponse<Object>> addTrackToPlaylist(@Path("id") String playlistId, @Body Map<String, String> body);

    @POST("api/v1/ai/chat")
    Call<ApiResponse<AiChatResponseData>> sendAiChatMessage(@Body AiChatRequest request);

    @POST("api/v1/ai/generate-playlist")
    Call<ApiResponse<AiGenerateResponseData>> generateAiPlaylist(@Body AiGenerateRequest request);

    @GET("api/v1/albums/{id}")
    Call<ApiResponse<Album>> getAlbumDetails(@Path("id") String albumId);

    @GET("api/v1/albums")
    Call<ApiResponse<List<Album>>> getPopularAlbums();

    @POST("api/v1/songs/log-playback")
    Call<ApiResponse<Object>> logPlayback(@Body Map<String, Object> body);
}
