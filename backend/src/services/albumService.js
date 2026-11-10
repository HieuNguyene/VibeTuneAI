const mongoose = require('mongoose');
const Album = require('../models/Album');
const Song = require('../models/Song');
const UserFavorite = require('../models/UserFavorite');
const ApiError = require('../utils/ApiError');

class AlbumService {
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

    return albums;
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

    // Lấy danh sách bài hát thuộc album này
    let songs = await Song.find({ albumId: album._id, isPublic: true })
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('genreIds', 'name colorCode')
      .sort({ playsCount: -1 })
      .lean();

    // Nếu album chưa có bài hát được gán albumId trực tiếp, tìm bài hát của artist đó
    if (songs.length === 0 && album.artistId) {
      songs = await Song.find({ artistId: album.artistId._id, isPublic: true })
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('genreIds', 'name colorCode')
        .sort({ playsCount: -1 })
        .lean();
    }

    // Gắn cờ isLiked nếu người dùng đã đăng nhập
    let likedSet = new Set();
    if (userId) {
      const favorites = await UserFavorite.find({ userId }).select('songId').lean();
      likedSet = new Set(favorites.map((f) => f.songId.toString()));
    }

    const tracks = songs.map((s) => ({
      ...s,
      isLiked: likedSet.has(s._id.toString())
    }));

    return {
      ...album,
      tracks,
      totalTracks: tracks.length
    };
  }
}

module.exports = AlbumService;
