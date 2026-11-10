const mongoose = require('mongoose');
const Playlist = require('../models/Playlist');
const PlaylistTrack = require('../models/PlaylistTrack');
const Song = require('../models/Song');
const ApiError = require('../utils/ApiError');

class PlaylistService {
  /**
   * Lấy danh sách Playlist của người dùng
   */
  static async getUserPlaylists(userId) {
    const playlists = await Playlist.find({ creatorId: userId })
      .sort({ createdAt: -1 })
      .lean();

    return playlists;
  }

  /**
   * Lấy danh sách Playlist công khai
   */
  static async getPublicPlaylists({ page = 1, limit = 20 } = {}) {
    const skip = (page - 1) * limit;
    const playlists = await Playlist.find({ isPublic: true })
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .populate('creatorId', 'displayName avatarUrl')
      .lean();

    const total = await Playlist.countDocuments({ isPublic: true });

    return {
      playlists,
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit)
      }
    };
  }

  /**
   * Lấy chi tiết Playlist và danh sách bài hát có thứ tự
   */
  static async getPlaylistById(playlistId, userId = null) {
    if (!mongoose.Types.ObjectId.isValid(playlistId)) {
      throw ApiError.badRequest('Invalid playlist ID');
    }

    const playlist = await Playlist.findById(playlistId)
      .populate('creatorId', 'displayName avatarUrl')
      .lean();

    if (!playlist) {
      throw ApiError.notFound('Playlist not found');
    }

    // Nếu playlist là private và không phải của creator thì chặn
    if (!playlist.isPublic && (!userId || playlist.creatorId._id.toString() !== userId.toString())) {
      throw ApiError.forbidden('This playlist is private');
    }

    // Lấy các track theo thứ tự order
    const tracks = await PlaylistTrack.find({ playlistId })
      .sort({ order: 1 })
      .populate({
        path: 'songId',
        populate: [
          { path: 'artistId', select: 'name avatarUrl isVerified' },
          { path: 'albumId', select: 'title coverImageUrl' },
          { path: 'genreIds', select: 'name colorCode' }
        ]
      })
      .lean();

    const formattedTracks = tracks
      .filter((t) => t.songId != null)
      .map((t) => ({
        ...t.songId,
        order: t.order,
        addedAt: t.addedAt
      }));

    return {
      ...playlist,
      tracks: formattedTracks
    };
  }

  /**
   * Tạo Playlist mới
   */
  static async createPlaylist(userId, { title, description = '', isPublic = true, coverImageUrl = '' }) {
    if (!title || title.trim() === '') {
      throw ApiError.badRequest('Playlist title is required');
    }

    const playlist = await Playlist.create({
      title: title.trim(),
      description: description.trim(),
      creatorId: userId,
      isPublic,
      coverImageUrl: coverImageUrl || 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80',
      totalTracks: 0
    });

    return playlist;
  }

  /**
   * Thêm bài hát vào Playlist
   */
  static async addTrackToPlaylist(userId, playlistId, songId) {
    if (!mongoose.Types.ObjectId.isValid(playlistId) || !mongoose.Types.ObjectId.isValid(songId)) {
      throw ApiError.badRequest('Invalid playlist or song ID');
    }

    const playlist = await Playlist.findById(playlistId);
    if (!playlist) {
      throw ApiError.notFound('Playlist not found');
    }

    if (playlist.creatorId.toString() !== userId.toString()) {
      throw ApiError.forbidden('You do not have permission to modify this playlist');
    }

    const song = await Song.findById(songId);
    if (!song) {
      throw ApiError.notFound('Song not found');
    }

    // Kiểm tra bài hát đã có trong playlist chưa
    const existingTrack = await PlaylistTrack.findOne({ playlistId, songId });
    if (existingTrack) {
      throw ApiError.conflict('Song is already in this playlist');
    }

    // Tìm order lớn nhất hiện tại
    const lastTrack = await PlaylistTrack.findOne({ playlistId }).sort({ order: -1 });
    const nextOrder = lastTrack ? lastTrack.order + 1 : 0;

    const track = await PlaylistTrack.create({
      playlistId,
      songId,
      order: nextOrder,
      addedAt: new Date()
    });

    await Playlist.findByIdAndUpdate(playlistId, { $inc: { totalTracks: 1 } });

    return track;
  }

  /**
   * Xoá bài hát khỏi Playlist
   */
  static async removeTrackFromPlaylist(userId, playlistId, songId) {
    if (!mongoose.Types.ObjectId.isValid(playlistId) || !mongoose.Types.ObjectId.isValid(songId)) {
      throw ApiError.badRequest('Invalid playlist or song ID');
    }

    const playlist = await Playlist.findById(playlistId);
    if (!playlist) {
      throw ApiError.notFound('Playlist not found');
    }

    if (playlist.creatorId.toString() !== userId.toString()) {
      throw ApiError.forbidden('You do not have permission to modify this playlist');
    }

    const deleted = await PlaylistTrack.findOneAndDelete({ playlistId, songId });
    if (!deleted) {
      throw ApiError.notFound('Track not found in playlist');
    }

    await Playlist.findByIdAndUpdate(playlistId, { $inc: { totalTracks: -1 } });

    return { success: true, message: 'Track removed from playlist' };
  }

  /**
   * Sắp xếp lại thứ tự bài hát trong Playlist (Reorder Tracks)
   * Phục vụ thao tác drag-and-drop trên Android RecyclerView
   */
  static async reorderTracks(userId, playlistId, trackOrders) {
    // trackOrders = [{ songId, order }]
    const playlist = await Playlist.findById(playlistId);
    if (!playlist) {
      throw ApiError.notFound('Playlist not found');
    }

    if (playlist.creatorId.toString() !== userId.toString()) {
      throw ApiError.forbidden('You do not have permission to modify this playlist');
    }

    const bulkOps = trackOrders.map((item) => ({
      updateOne: {
        filter: { playlistId, songId: item.songId },
        update: { $set: { order: item.order } }
      }
    }));

    await PlaylistTrack.bulkWrite(bulkOps);

    return { success: true, message: 'Playlist tracks reordered successfully' };
  }
}

module.exports = PlaylistService;
