package com.example.spotifyclone.presentation.playlist;

import com.example.spotifyclone.data.model.Album;
import com.example.spotifyclone.data.model.Artist;
import com.example.spotifyclone.data.model.Playlist;

public class LibraryItem {
    public enum Type {
        LIKED_SONGS,
        PLAYLIST,
        ARTIST,
        ALBUM
    }

    private final Type type;
    private final String id;
    private final String title;
    private final String subtitle;
    private final String coverUrl;
    private final int trackCount;
    private final boolean isPinned;
    private final Playlist playlist;
    private final Artist artist;
    private final Album album;

    public static LibraryItem createLikedSongs(int count) {
        return new LibraryItem(
                Type.LIKED_SONGS,
                "LIKED_SONGS_ID",
                "Bài hát đã thích",
                "Danh sách phát • " + count + " bài hát",
                null,
                count,
                true,
                null,
                null,
                null
        );
    }

    public static LibraryItem createPlaylist(Playlist playlist) {
        int count = playlist.getTotalTracks();
        String subtitle = (playlist.isAiGenerated() ? "AI Playlist • " : "Danh sách phát • ") + count + " bài hát";
        return new LibraryItem(
                Type.PLAYLIST,
                playlist.getId(),
                playlist.getTitle(),
                subtitle,
                playlist.getCoverImageUrl(),
                count,
                playlist.isAiGenerated(),
                playlist,
                null,
                null
        );
    }

    public static LibraryItem createArtist(Artist artist) {
        return new LibraryItem(
                Type.ARTIST,
                artist.getId(),
                artist.getName(),
                "Nghệ sĩ",
                artist.getAvatarUrl(),
                0,
                false,
                null,
                artist,
                null
        );
    }

    public static LibraryItem createAlbum(Album album) {
        return new LibraryItem(
                Type.ALBUM,
                album.getId(),
                album.getTitle(),
                "Album • " + album.getArtistName(),
                album.getCoverImageUrl(),
                album.getTracks() != null ? album.getTracks().size() : 0,
                false,
                null,
                null,
                album
        );
    }

    private LibraryItem(Type type, String id, String title, String subtitle, String coverUrl, int trackCount, boolean isPinned, Playlist playlist, Artist artist, Album album) {
        this.type = type;
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.coverUrl = coverUrl;
        this.trackCount = trackCount;
        this.isPinned = isPinned;
        this.playlist = playlist;
        this.artist = artist;
        this.album = album;
    }

    public Type getType() { return type; }
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getCoverUrl() { return coverUrl; }
    public int getTrackCount() { return trackCount; }
    public boolean isPinned() { return isPinned; }
    public Playlist getPlaylist() { return playlist; }
    public Artist getArtist() { return artist; }
    public Album getAlbum() { return album; }
}
