package com.example.spotifyclone.presentation.playlist;

import com.example.spotifyclone.data.model.Album;
import com.example.spotifyclone.data.model.Artist;
import com.example.spotifyclone.data.model.Playlist;
import com.example.spotifyclone.data.model.Song;

public class LibraryItem {
    public enum Type {
        LIKED_SONGS,
        PLAYLIST,
        ARTIST,
        ALBUM,
        SONG
    }

    private final Type type;
    private final String id;
    private final String title;
    private final String subtitle;
    private final String coverUrl;
    private final int trackCount;
    private final boolean isPinned;
    private final boolean isLocked;
    private final Playlist playlist;
    private final Artist artist;
    private final Album album;
    private final Song song;

    public static LibraryItem createLikedSongs(int count) {
        return new LibraryItem(
                Type.LIKED_SONGS,
                "LIKED_SONGS_ID",
                "Bài hát đã thích",
                "Danh sách phát • " + count + " bài hát",
                null,
                count,
                true,
                false,
                null,
                null,
                null,
                null
        );
    }

    public static LibraryItem createPlaylist(Playlist playlist) {
        int count = playlist.getTotalTracks();
        boolean locked = playlist.isLocked();
        String typeLabel;
        if (locked) {
            typeLabel = "Riêng tư (Đã khóa)";
        } else if (playlist.isAiGenerated()) {
            typeLabel = "Danh sách gợi ý";
        } else if (playlist.isFavorite()) {
            typeLabel = "Playlist yêu thích";
        } else if (playlist.isPublic()) {
            typeLabel = "Playlist";
        } else {
            typeLabel = "Riêng tư";
        }
        String creator = playlist.getCreatorName() != null ? " • " + playlist.getCreatorName() : "";
        String subtitle = locked
                ? "🔒 Riêng tư • Đã bị khóa bởi người tạo"
                : (typeLabel + " • " + count + " bài hát" + creator);
        return new LibraryItem(
                Type.PLAYLIST,
                playlist.getId(),
                playlist.getTitle(),
                subtitle,
                playlist.getCoverImageUrl(),
                count,
                playlist.isAiGenerated(),
                locked,
                playlist,
                null,
                null,
                null
        );
    }

    public static LibraryItem createArtist(Artist artist) {
        String subtitle = artist.getSongsCount() > 0 ? "Nghệ sĩ • " + artist.getSongsCount() + " bài hát" : "Nghệ sĩ";
        return new LibraryItem(
                Type.ARTIST,
                artist.getId(),
                artist.getName(),
                subtitle,
                artist.getAvatarUrl(),
                artist.getSongsCount(),
                false,
                false,
                null,
                artist,
                null,
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
                false,
                null,
                null,
                album,
                null
        );
    }

    public static LibraryItem createSong(Song song) {
        String artistName = song.getArtistName() != null ? song.getArtistName() : "Nghệ sĩ";
        return new LibraryItem(
                Type.SONG,
                song.getId(),
                song.getTitle(),
                "Bài hát • " + artistName,
                song.getCoverImageUrl(),
                1,
                false,
                false,
                null,
                null,
                null,
                song
        );
    }

    private LibraryItem(Type type, String id, String title, String subtitle, String coverUrl, int trackCount, boolean isPinned, boolean isLocked, Playlist playlist, Artist artist, Album album, Song song) {
        this.type = type;
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.coverUrl = coverUrl;
        this.trackCount = trackCount;
        this.isPinned = isPinned;
        this.isLocked = isLocked;
        this.playlist = playlist;
        this.artist = artist;
        this.album = album;
        this.song = song;
    }

    public Type getType() { return type; }
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getCoverUrl() { return coverUrl; }
    public int getTrackCount() { return trackCount; }
    public boolean isPinned() { return isPinned; }
    public boolean isLocked() { return isLocked; }
    public Playlist getPlaylist() { return playlist; }
    public Artist getArtist() { return artist; }
    public Album getAlbum() { return album; }
    public Song getSong() { return song; }
}
