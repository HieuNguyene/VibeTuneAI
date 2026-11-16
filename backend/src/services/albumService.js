const mongoose = require('mongoose');
const Album = require('../models/Album');
const Song = require('../models/Song');
const Artist = require('../models/Artist');
const Genre = require('../models/Genre');
const UserFavorite = require('../models/UserFavorite');
const UserPreference = require('../models/UserPreference');
const ApiError = require('../utils/ApiError');
const { formatSongArtistFields } = require('../utils/songFormatter');

/**
 * Đếm trực tiếp số bài hát hiện có của mỗi album từ collection Song (1 query aggregation)
 */
async function attachLiveAlbumTrackCounts(albums, { includePrivate = false } = {}) {
  if (!Array.isArray(albums) || albums.length === 0) return [];
  const albumIds = albums.map((a) => a._id).filter(Boolean);
  if (albumIds.length === 0) return albums;

  const matchStage = { albumId: { $in: albumIds } };
  if (!includePrivate) {
    matchStage.isPublic = true;
  }

  const counts = await Song.aggregate([
    { $match: matchStage },
    { $group: { _id: '$albumId', count: { $sum: 1 } } }
  ]);
  const countMap = new Map(counts.map((c) => [c._id.toString(), c.count]));
  return albums.map((album) => ({
    ...album,
    totalTracks: countMap.get(album._id.toString()) || 0
  }));
}

class AlbumService {
  static async attachLiveAlbumTrackCounts(albums, options = {}) {
    return attachLiveAlbumTrackCounts(albums, options);
  }

  /**
   * Lấy danh sách album nổi tiếng
   */
  static async getPopularAlbums(limit = 20) {
    const albums = await Album.find()
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('genreIds', 'name colorCode')
      .sort({ totalTracks: -1, createdAt: -1 })
      .limit(limit)
      .lean();

    return attachLiveAlbumTrackCounts(albums);
  }

  /**
   * Lấy chi tiết album kèm danh sách bài hát
   */
  static async getAlbumById(albumId, userId = null) {
    if (!mongoose.Types.ObjectId.isValid(albumId)) {
      throw ApiError.badRequest('Invalid album ID');
    }

    const album = await Album.findById(albumId)
      .populate('artistId', 'name avatarUrl isVerified bio')
      .populate('genreIds', 'name colorCode')
      .lean();

    if (!album) {
      throw ApiError.notFound('Album not found');
    }

    // Lấy danh sách bài hát thuộc đúng album này
    const songs = await Song.find({ albumId: album._id, isPublic: true })
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('featuredArtistIds', 'name avatarUrl isVerified')
      .populate('artistIds', 'name avatarUrl isVerified')
      .populate('composerId', 'name avatarUrl isVerified')
      .populate('composerIds', 'name avatarUrl isVerified')
      .populate('genreIds', 'name colorCode')
      .populate('albumId', 'title coverImageUrl releaseYear')
      .sort({ playsCount: -1 })
      .lean();

    // Gắn cờ isLiked nếu người dùng đã đăng nhập và kiểm tra isFavorite album
    let likedSet = new Set();
    let isAlbumFavorite = false;
    if (userId) {
      const [favorites, pref] = await Promise.all([
        UserFavorite.find({ userId }).select('songId').lean(),
        UserPreference.findOne({ userId }).select('favoriteAlbumIds').lean()
      ]);
      likedSet = new Set(favorites.map((f) => f.songId.toString()));
      if (pref && Array.isArray(pref.favoriteAlbumIds)) {
        isAlbumFavorite = pref.favoriteAlbumIds.some((id) => id.toString() === album._id.toString());
      }
    }

    const albumInfo = {
      _id: album._id,
      title: album.title,
      coverImageUrl: album.coverImageUrl,
      releaseYear: album.releaseYear
    };

    const tracks = songs.map((s) => {
      const formatted = formatSongArtistFields(s);
      return {
        ...formatted,
        albumId: (formatted.albumId && typeof formatted.albumId === 'object' && formatted.albumId.title) ? formatted.albumId : albumInfo,
        isLiked: likedSet.has(formatted._id.toString())
      };
    });

    return {
      ...album,
      tracks,
      totalTracks: tracks.length,
      isFavorite: isAlbumFavorite
    };
  }

  /**
   * Toggle yêu thích Album
   */
  static async toggleFavoriteAlbum(userId, albumId) {
    if (!mongoose.Types.ObjectId.isValid(albumId)) {
      throw ApiError.badRequest('Invalid album ID');
    }

    const album = await Album.findById(albumId).lean();
    if (!album) {
      throw ApiError.notFound('Album not found');
    }

    let pref = await UserPreference.findOne({ userId });
    if (!pref) {
      pref = await UserPreference.create({ userId, favoriteAlbumIds: [] });
    }

    const albumObjId = new mongoose.Types.ObjectId(albumId);
    const exists = pref.favoriteAlbumIds && pref.favoriteAlbumIds.some((id) => id.toString() === albumId);

    let isFavorite = false;
    if (exists) {
      await UserPreference.updateOne(
        { userId },
        { $pull: { favoriteAlbumIds: albumObjId } }
      );
      isFavorite = false;
    } else {
      await UserPreference.updateOne(
        { userId },
        { $addToSet: { favoriteAlbumIds: albumObjId } }
      );
      isFavorite = true;
    }

    return { isFavorite, albumId };
  }

  /**
   * Lấy danh sách Album yêu thích của người dùng
   */
  static async getFavoriteAlbums(userId) {
    if (!userId) return [];

    const pref = await UserPreference.findOne({ userId }).select('favoriteAlbumIds').lean();
    if (!pref || !Array.isArray(pref.favoriteAlbumIds) || pref.favoriteAlbumIds.length === 0) {
      return [];
    }

    const albums = await Album.find({ _id: { $in: pref.favoriteAlbumIds } })
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('genreIds', 'name colorCode')
      .lean();

    const withCounts = await attachLiveAlbumTrackCounts(albums);
    return withCounts.map((a) => ({
      ...a,
      isFavorite: true
    }));
  }
}

module.exports = AlbumService;
