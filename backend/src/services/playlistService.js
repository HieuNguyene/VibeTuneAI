const fs = require('fs');
const path = require('path');
const mongoose = require('mongoose');
const Playlist = require('../models/Playlist');
const PlaylistTrack = require('../models/PlaylistTrack');
const Song = require('../models/Song');
const User = require('../models/User');
const UserFavorite = require('../models/UserFavorite');
const UserPreference = require('../models/UserPreference');
const ApiError = require('../utils/ApiError');
const { formatSongArtistFields } = require('../utils/songFormatter');
const { saveValidatedBase64Upload, sanitizeMediaUrl } = require('../utils/fileValidator');

const UPLOADS_BASE_DIR = path.join(__dirname, '../../public/uploads');

function saveValidatedPlaylistCover(coverImageBase64) {
  const saved = saveValidatedBase64Upload({
    fileName: 'playlist_cover.jpg',
    base64Data: coverImageBase64,
    target: 'cover',
    uploadsBaseDir: UPLOADS_BASE_DIR,
    subDirOverride: 'covers',
    prefix: 'playlist',
    maxBytes: 5 * 1024 * 1024
  });
  return saved.url;
}

async function attachLivePlaylistTrackCounts(playlists) {
  if (!Array.isArray(playlists) || playlists.length === 0) return [];
  const ids = playlists.map((p) => p._id);
  const stats = await PlaylistTrack.aggregate([
    { $match: { playlistId: { $in: ids } } },
    { $group: { _id: '$playlistId', count: { $sum: 1 } } }
  ]);
  const countMap = new Map(stats.map((s) => [String(s._id), s.count]));
  return playlists.map((pl) => ({
    ...pl,
    totalTracks: countMap.has(String(pl._id)) ? countMap.get(String(pl._id)) : 0
  }));
}

class PlaylistService {
  static async attachLivePlaylistTrackCounts(playlists) {
    return attachLivePlaylistTrackCounts(playlists);
  }

  /**
   * Lấy danh sách Playlist của người dùng (hỗ trợ filter: 'all', 'personal', 'favorite' hoặc personalOnly)
   */
  static async getUserPlaylists(userId = null, filter = 'all') {
    let mode = 'all';
    if (typeof filter === 'boolean') {
      mode = filter ? 'personal' : 'all';
    } else if (typeof filter === 'string') {
      mode = filter.toLowerCase();
    }

    let favoritePlaylistIds = [];
    if (userId && mongoose.Types.ObjectId.isValid(userId)) {
      const pref = await UserPreference.findOne({ userId }).select('favoritePlaylistIds').lean();
      if (pref && Array.isArray(pref.favoritePlaylistIds)) {
        favoritePlaylistIds = pref.favoritePlaylistIds.map((id) => id.toString());
      }
    }

    const favSet = new Set(favoritePlaylistIds);

    if (mode === 'favorite') {
      if (favoritePlaylistIds.length === 0) return [];
      const favPlaylists = await Playlist.find({ _id: { $in: favoritePlaylistIds } })
        .populate('creatorId', 'displayName username avatarUrl')
        .sort({ createdAt: -1 })
        .lean();
      const withCounts = await attachLivePlaylistTrackCounts(favPlaylists);
      return withCounts.map((p) => {
        const creatorIdStr = p.creatorId ? (p.creatorId._id ? p.creatorId._id.toString() : p.creatorId.toString()) : '';
        const isOwner = Boolean(userId && creatorIdStr === userId.toString());
        return {
          ...p,
          isFavorite: true,
          isLocked: !p.isPublic && !isOwner
        };
      });
    }

    let userPlaylists = [];
    if (userId && mongoose.Types.ObjectId.isValid(userId)) {
      userPlaylists = await Playlist.find({ creatorId: userId })
        .populate('creatorId', 'displayName username avatarUrl')
        .sort({ createdAt: -1 })
        .lean();
    }

    if (mode === 'personal') {
      const withCounts = await attachLivePlaylistTrackCounts(userPlaylists);
      return withCounts.map((p) => ({
        ...p,
        isFavorite: favSet.has(p._id.toString()),
        isLocked: false
      }));
    }

    // mode === 'all'
    const userPlaylistIds = userPlaylists.map((p) => p._id.toString());
    const otherFavIds = favoritePlaylistIds.filter((id) => !userPlaylistIds.includes(id));
    let favoritePlaylists = [];
    if (otherFavIds.length > 0) {
      favoritePlaylists = await Playlist.find({ _id: { $in: otherFavIds } })
        .populate('creatorId', 'displayName username avatarUrl')
        .sort({ createdAt: -1 })
        .lean();
    }

    const allKnownIds = [...userPlaylistIds, ...otherFavIds];
    const publicPlaylists = await Playlist.find({
      isPublic: true,
      _id: { $nin: allKnownIds }
    })
      .populate('creatorId', 'displayName username avatarUrl')
      .sort({ createdAt: -1 })
      .lean();

    const merged = [...userPlaylists, ...favoritePlaylists, ...publicPlaylists];
    const withCounts = await attachLivePlaylistTrackCounts(merged);
    return withCounts.map((p) => {
      const creatorIdStr = p.creatorId ? (p.creatorId._id ? p.creatorId._id.toString() : p.creatorId.toString()) : '';
      const isOwner = Boolean(userId && creatorIdStr === userId.toString());
      return {
        ...p,
        isFavorite: favSet.has(p._id.toString()),
        isLocked: !p.isPublic && !isOwner
      };
    });
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

    const [enrichedPlaylists, total] = await Promise.all([
      attachLivePlaylistTrackCounts(playlists),
      Playlist.countDocuments({ isPublic: true })
    ]);

    return {
      playlists: enrichedPlaylists,
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
      .populate('creatorId', 'displayName username avatarUrl')
      .lean();

    if (!playlist) {
      throw ApiError.notFound('Playlist not found');
    }

    // Nếu playlist là private và không phải của creator thì chặn
    const creatorIdStr = playlist.creatorId ? (playlist.creatorId._id ? playlist.creatorId._id.toString() : playlist.creatorId.toString()) : '';
    const isOwner = Boolean(userId && creatorIdStr === userId.toString());
    if (!playlist.isPublic && !isOwner) {
      throw ApiError.forbidden('Danh sách phát này là riêng tư và đã bị khóa bởi người tạo');
    }

    // Lấy các track theo thứ tự order
    const tracks = await PlaylistTrack.find({ playlistId })
      .sort({ order: 1 })
      .populate({
        path: 'songId',
        populate: [
          { path: 'artistId', select: 'name avatarUrl isVerified' },
          { path: 'featuredArtistIds', select: 'name avatarUrl isVerified' },
          { path: 'artistIds', select: 'name avatarUrl isVerified' },
          { path: 'composerId', select: 'name avatarUrl isVerified' },
          { path: 'composerIds', select: 'name avatarUrl isVerified' },
          { path: 'albumId', select: 'title coverImageUrl' },
          { path: 'genreIds', select: 'name colorCode' }
        ]
      })
      .lean();
    let likedSet = new Set();
    let isPlaylistFavorite = false;
    if (userId && mongoose.Types.ObjectId.isValid(userId)) {
      const [favorites, pref] = await Promise.all([
        UserFavorite.find({ userId }).select('songId').lean(),
        UserPreference.findOne({ userId }).select('favoritePlaylistIds').lean()
      ]);
      likedSet = new Set(favorites.map((f) => f.songId.toString()));
      if (pref && Array.isArray(pref.favoritePlaylistIds)) {
        isPlaylistFavorite = pref.favoritePlaylistIds.some((id) => id.toString() === playlist._id.toString());
      }
    }

    const formattedTracks = tracks
      .filter((t) => t.songId != null)
      .map((t) => {
        const formatted = formatSongArtistFields(t.songId);
        return {
          ...formatted,
          order: t.order,
          addedAt: t.addedAt,
          isLiked: likedSet.has((formatted._id || formatted.id).toString())
        };
      });

    return {
      ...playlist,
      tracks: formattedTracks,
      totalTracks: formattedTracks.length,
      isFavorite: isPlaylistFavorite
    };
  }

  /**
   * Tạo Playlist mới
   */
  static async createPlaylist(userId, { title, description = '', isPublic = true, coverImageUrl = '', coverImageBase64 = '', trackIds = [] }) {
    if (!title || title.trim() === '') {
      throw ApiError.badRequest('Playlist title is required');
    }

    let finalCoverUrl = sanitizeMediaUrl(coverImageUrl);

    // Nếu người dùng tải ảnh từ máy (base64)
    if (coverImageBase64 && typeof coverImageBase64 === 'string' && coverImageBase64.trim().length > 0) {
      finalCoverUrl = saveValidatedPlaylistCover(coverImageBase64);
    }

    if (!finalCoverUrl) {
      finalCoverUrl = '/uploads/playlists/default_music_cover.jpg';
    }

    const isPub = (typeof isPublic === 'boolean') ? isPublic : (String(isPublic).toLowerCase() === 'true');

    const playlist = await Playlist.create({
      title: title.trim(),
      description: (description || '').trim(),
      creatorId: userId,
      isPublic: isPub,
      coverImageUrl: finalCoverUrl,
      totalTracks: 0
    });

    if (Array.isArray(trackIds) && trackIds.length > 0) {
      const validSongIds = trackIds.filter(id => mongoose.Types.ObjectId.isValid(id));
      if (validSongIds.length > 0) {
        const existingSongs = await Song.find({ _id: { $in: validSongIds } }).select('_id coverImageUrl').lean();
        const existingMap = new Map(existingSongs.map(s => [String(s._id), s]));
        const trackDocs = [];
        let order = 0;
        for (const sid of validSongIds) {
          if (existingMap.has(String(sid))) {
            trackDocs.push({
              playlistId: playlist._id,
              songId: sid,
              order: order++,
              addedAt: new Date()
            });
          }
        }
        if (trackDocs.length > 0) {
          await PlaylistTrack.insertMany(trackDocs);
          playlist.totalTracks = trackDocs.length;
          if ((!coverImageUrl && !coverImageBase64) && existingSongs[0]?.coverImageUrl) {
            playlist.coverImageUrl = existingSongs[0].coverImageUrl;
          }
          await playlist.save();
        }
      }
    }

    const populated = await Playlist.findById(playlist._id)
      .populate('creatorId', 'displayName username avatarUrl')
      .lean();

    return populated;
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

    const remainingCount = await PlaylistTrack.countDocuments({ playlistId });
    await Playlist.findByIdAndUpdate(playlistId, { $set: { totalTracks: remainingCount } });

    return { success: true, totalTracks: remainingCount, message: 'Track removed from playlist' };
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

  /**
   * Cập nhật thông tin Playlist (chỉ chủ sở hữu mới có quyền)
   */
  static async updatePlaylist(userId, playlistId, { title, description, isPublic, coverImageUrl, coverImageBase64 }) {
    if (!mongoose.Types.ObjectId.isValid(playlistId)) {
      throw ApiError.badRequest('Invalid playlist ID');
    }

    const playlist = await Playlist.findById(playlistId);
    if (!playlist) {
      throw ApiError.notFound('Playlist not found');
    }

    if (playlist.creatorId.toString() !== userId.toString()) {
      throw ApiError.forbidden('You do not have permission to modify this playlist');
    }

    if (title !== undefined && title.trim()) {
      playlist.title = title.trim();
    }

    if (description !== undefined) {
      playlist.description = description.trim();
    }

    if (isPublic !== undefined) {
      playlist.isPublic = (typeof isPublic === 'boolean') ? isPublic : (String(isPublic).toLowerCase() === 'true');
    }

    if (coverImageBase64 && typeof coverImageBase64 === 'string' && coverImageBase64.trim().length > 0) {
      playlist.coverImageUrl = saveValidatedPlaylistCover(coverImageBase64);
    } else if (coverImageUrl !== undefined && typeof coverImageUrl === 'string') {
      playlist.coverImageUrl = sanitizeMediaUrl(coverImageUrl);
    }

    await playlist.save();

    return Playlist.findById(playlistId)
      .populate('creatorId', 'displayName username avatarUrl')
      .lean();
  }

  /**
   * Xóa Playlist (chỉ chủ sở hữu mới có quyền) kèm dọn dẹp các bản ghi liên kết
   */
  static async deletePlaylist(userId, playlistId) {
    if (!mongoose.Types.ObjectId.isValid(playlistId)) {
      throw ApiError.badRequest('Invalid playlist ID');
    }

    const playlist = await Playlist.findById(playlistId);
    if (!playlist) {
      throw ApiError.notFound('Playlist not found');
    }

    if (playlist.creatorId.toString() !== userId.toString()) {
      throw ApiError.forbidden('You do not have permission to delete this playlist');
    }

    await Promise.all([
      PlaylistTrack.deleteMany({ playlistId }),
      Playlist.findByIdAndDelete(playlistId),
      UserPreference.updateMany(
        { favoritePlaylistIds: playlistId },
        { $pull: { favoritePlaylistIds: playlistId } }
      )
    ]);

    return { success: true, message: 'Playlist deleted successfully' };
  }

  /**
   * Toggle yêu thích Playlist
   */
  static async toggleFavoritePlaylist(userId, playlistId) {
    if (!mongoose.Types.ObjectId.isValid(playlistId)) {
      throw ApiError.badRequest('Invalid playlist ID');
    }

    const playlist = await Playlist.findById(playlistId).lean();
    if (!playlist) {
      throw ApiError.notFound('Playlist not found');
    }

    let pref = await UserPreference.findOne({ userId });
    if (!pref) {
      pref = await UserPreference.create({ userId, favoritePlaylistIds: [] });
    }

    const playlistObjId = new mongoose.Types.ObjectId(playlistId);
    const exists = pref.favoritePlaylistIds && pref.favoritePlaylistIds.some((id) => id.toString() === playlistId.toString());

    const creatorIdStr = playlist.creatorId ? (playlist.creatorId._id ? playlist.creatorId._id.toString() : playlist.creatorId.toString()) : '';
    const isOwner = Boolean(userId && creatorIdStr === userId.toString());

    if (!exists && !playlist.isPublic && !isOwner) {
      throw ApiError.forbidden('Danh sách phát này là riêng tư và không thể thêm vào yêu thích');
    }

    let isFavorite = false;
    if (exists) {
      await UserPreference.updateOne(
        { userId },
        { $pull: { favoritePlaylistIds: playlistObjId } }
      );
      isFavorite = false;
    } else {
      await UserPreference.updateOne(
        { userId },
        { $addToSet: { favoritePlaylistIds: playlistObjId } }
      );
      isFavorite = true;

      // Tu dong tao va gui thong bao realtime toi chu so huu playlist neu nguoi khac yeu thich playlist
      if (!isOwner && playlist.creatorId) {
        (async () => {
          try {
            const liker = await User.findById(userId).select('displayName avatarUrl').lean();
            const likerName = liker?.displayName || 'Một người dùng';
            const NotificationService = require('./notificationService');
            await NotificationService.createNotification({
              recipientId: playlist.creatorId,
              senderId: userId,
              senderName: likerName,
              senderAvatar: liker?.avatarUrl || '',
              title: 'Playlist của bạn được yêu thích!',
              content: `${likerName} đã thả tim và thêm playlist "${playlist.title}" vào danh sách yêu thích.`,
              imageUrl: playlist.coverImageUrl || liker?.avatarUrl || '',
              type: 'PLAYLIST_LIKED',
              metadata: { playlistId: playlist._id }
            });
          } catch (e) {
            console.error('Lỗi gửi thông báo yêu thích playlist:', e);
          }
        })();
      }
    }

    return { isFavorite, playlistId };
  }

  /**
   * Lấy danh sách Playlist yêu thích của người dùng
   */
  static async getFavoritePlaylists(userId) {
    if (!userId || !mongoose.Types.ObjectId.isValid(userId)) return [];

    const pref = await UserPreference.findOne({ userId }).select('favoritePlaylistIds').lean();
    if (!pref || !Array.isArray(pref.favoritePlaylistIds) || pref.favoritePlaylistIds.length === 0) {
      return [];
    }

    const playlists = await Playlist.find({ _id: { $in: pref.favoritePlaylistIds } })
      .populate('creatorId', 'displayName username avatarUrl')
      .sort({ createdAt: -1 })
      .lean();

    const withCounts = await attachLivePlaylistTrackCounts(playlists);
    return withCounts.map((p) => {
      const creatorIdStr = p.creatorId ? (p.creatorId._id ? p.creatorId._id.toString() : p.creatorId.toString()) : '';
      const isOwner = Boolean(userId && creatorIdStr === userId.toString());
      return {
        ...p,
        isFavorite: true,
        isLocked: !p.isPublic && !isOwner
      };
    });
  }
}

module.exports = PlaylistService;
