package com.example.spotifyclone.data.remote;

import com.example.spotifyclone.data.model.Album;
import com.example.spotifyclone.data.model.ApiResponse;
import com.example.spotifyclone.data.model.Artist;
import com.example.spotifyclone.data.model.ArtistDetailData;
import com.example.spotifyclone.data.model.AuthResponse;
import com.example.spotifyclone.data.model.ChangePasswordRequest;
import com.example.spotifyclone.data.model.Genre;
import com.example.spotifyclone.data.model.GenreSongResponse;
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
import com.example.spotifyclone.data.model.CreatePlaylistRequest;
import com.example.spotifyclone.data.model.FavoritesData;
import com.example.spotifyclone.data.model.FollowingMusicData;
import com.example.spotifyclone.data.model.MoodFlowSessionRequest;
import com.example.spotifyclone.data.model.MoodFlowSessionResponse;
import com.example.spotifyclone.data.model.MoodFlowNextResponse;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.model.ToggleFavoriteData;
import com.example.spotifyclone.data.model.HistoryItem;
import com.example.spotifyclone.data.model.User;
import com.example.spotifyclone.data.model.UpdateProfileRequest;
import com.example.spotifyclone.data.model.NotificationItem;
import com.example.spotifyclone.data.model.NotificationListResponse;
import com.example.spotifyclone.data.model.UnreadCountData;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
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

    @PUT("api/v1/auth/profile")
    Call<ApiResponse<User>> updateProfile(@Body UpdateProfileRequest request);

    @PUT("api/v1/auth/password")
    Call<ApiResponse<Object>> changePassword(@Body ChangePasswordRequest request);

    @PUT("api/v1/auth/preferences")
    Call<ApiResponse<Map<String, Object>>> updatePreferences(@Body Map<String, Object> body);

    @GET("api/v1/songs/feed")
    Call<ApiResponse<HomeFeedData>> getHomeFeed();

    @GET("api/v1/songs/following")
    Call<ApiResponse<FollowingMusicData>> getFollowedArtistsMusic(@Query("sort") String sort);

    @GET("api/v1/genres")
    Call<ApiResponse<List<Genre>>> getGenres();

    @GET("api/v1/songs/genre/{id}")
    Call<ApiResponse<GenreSongResponse>> getSongsByGenre(
            @Path("id") String genreId,
            @Query("sort") String sort
    );

    @GET("api/v1/songs/search")
    Call<ApiResponse<SearchResultData>> searchSongs(@Query("q") String query);

    @GET("api/v1/songs/artist/{id}")
    Call<ApiResponse<ArtistDetailData>> getSongsByArtist(@Path("id") String artistId);

    @GET("api/v1/songs/artist/{id}")
    Call<ApiResponse<ArtistDetailData>> getSongsByArtist(
            @Path("id") String artistId,
            @Query("sort") String sort
    );

    @GET("api/v1/songs/favorites")
    Call<ApiResponse<FavoritesData>> getFavorites();

    @POST("api/v1/songs/{id}/favorite")
    Call<ApiResponse<ToggleFavoriteData>> toggleFavorite(@Path("id") String songId);

    @GET("api/v1/playlists/my-playlists")
    Call<ApiResponse<List<Playlist>>> getMyPlaylists();

    @GET("api/v1/playlists/my-playlists")
    Call<ApiResponse<List<Playlist>>> getMyPlaylists(@Query("personalOnly") Boolean personalOnly);

    @GET("api/v1/playlists/favorites")
    Call<ApiResponse<List<Playlist>>> getFavoritePlaylists();

    @POST("api/v1/playlists/{id}/favorite")
    Call<ApiResponse<Map<String, Object>>> toggleFavoritePlaylist(@Path("id") String playlistId);

    @POST("api/v1/playlists")
    Call<ApiResponse<Playlist>> createPlaylist(@Body CreatePlaylistRequest request);

    @GET("api/v1/playlists/{id}")
    Call<ApiResponse<Playlist>> getPlaylistDetails(@Path("id") String playlistId);

    @PUT("api/v1/playlists/{id}")
    Call<ApiResponse<Playlist>> updatePlaylist(@Path("id") String playlistId, @Body Map<String, Object> body);

    @DELETE("api/v1/playlists/{id}")
    Call<ApiResponse<Object>> deletePlaylist(@Path("id") String playlistId);

    @POST("api/v1/playlists/{id}/tracks")
    Call<ApiResponse<Object>> addTrackToPlaylist(@Path("id") String playlistId, @Body Map<String, String> body);

    @DELETE("api/v1/playlists/{id}/tracks/{songId}")
    Call<ApiResponse<Object>> removeTrackFromPlaylist(@Path("id") String playlistId, @Path("songId") String songId);

    @POST("api/v1/ai/chat")
    Call<ApiResponse<AiChatResponseData>> sendAiChatMessage(@Body AiChatRequest request);

    @POST("api/v1/ai/generate-playlist")
    Call<ApiResponse<com.example.spotifyclone.data.model.AiGeneratePlaylistResponse>> generateAiPlaylist(@Body Map<String, Object> body);

    @GET("api/v1/albums/{id}")
    Call<ApiResponse<Album>> getAlbumDetails(@Path("id") String albumId);

    @GET("api/v1/albums")
    Call<ApiResponse<List<Album>>> getPopularAlbums();

    @GET("api/v1/albums")
    Call<ApiResponse<List<Album>>> getAllAlbums(@Query("limit") Integer limit);

    @GET("api/v1/albums/favorites")
    Call<ApiResponse<List<Album>>> getFavoriteAlbums();

    @POST("api/v1/albums/{id}/favorite")
    Call<ApiResponse<Map<String, Object>>> toggleFavoriteAlbum(@Path("id") String albumId);

    @GET("api/v1/artists/library")
    Call<ApiResponse<List<Artist>>> getLibraryArtists();

    @GET("api/v1/artists/library")
    Call<ApiResponse<List<Artist>>> getLibraryArtists(@Query("followedOnly") Boolean followedOnly);

    @POST("api/v1/artists/{id}/follow")
    Call<ApiResponse<Map<String, Object>>> toggleFollowArtist(@Path("id") String artistId);


    @POST("api/v1/songs/log-playback")
    Call<ApiResponse<Object>> logPlayback(@Body Map<String, Object> body);

    @GET("api/v1/songs/{id}/similar")
    Call<ApiResponse<List<Song>>> getSimilarSongs(@Path("id") String songId);

    @POST("events")
    Call<ApiResponse<Object>> logEvent(@Body Map<String, Object> body);

    @POST("player/session")
    Call<ApiResponse<MoodFlowSessionResponse>> createMoodFlowSession(@Body MoodFlowSessionRequest request);

    @GET("player/next")
    Call<ApiResponse<MoodFlowNextResponse>> getMoodFlowNext(
            @Query("sessionId") String sessionId,
            @Query("debug") boolean debug
    );

    @POST("player/session/{id}/arc")
    Call<ApiResponse<Map<String, Object>>> updateMoodFlowArc(
            @Path("id") String sessionId,
            @Body Map<String, String> body
    );

    // ==========================================
    // LISTENING HISTORY (Lịch sử nghe nhạc theo ngày)
    // ==========================================
    @GET("api/v1/history")
    Call<ApiResponse<List<HistoryItem>>> getListeningHistory();

    @DELETE("api/v1/history/{id}")
    Call<ApiResponse<Object>> deleteHistoryItem(@Path("id") String id);

    @DELETE("api/v1/history")
    Call<ApiResponse<Object>> deleteHistoryByDate(@Query("date") String date);

    @DELETE("api/v1/history")
    Call<ApiResponse<Object>> deleteAllHistory(@Query("all") boolean all);

    // ==========================================
    // NOTIFICATIONS (Thông báo)
    // ==========================================
    @GET("api/v1/notifications")
    Call<ApiResponse<NotificationListResponse>> getNotifications(
            @Query("page") int page,
            @Query("limit") int limit
    );

    @GET("api/v1/notifications/unread-count")
    Call<ApiResponse<UnreadCountData>> getUnreadNotificationCount();

    @PUT("api/v1/notifications/{id}/read")
    Call<ApiResponse<NotificationItem>> markNotificationAsRead(@Path("id") String id);

    @PUT("api/v1/notifications/read-all")
    Call<ApiResponse<Object>> markAllNotificationsAsRead();

    @DELETE("api/v1/notifications/{id}")
    Call<ApiResponse<Object>> deleteNotification(@Path("id") String id);
}
