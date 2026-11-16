const mongoose = require('mongoose');
const { ListeningHistory, Song, UserFavorite } = require('../models');
const ApiResponse = require('../utils/ApiResponse');
const ApiError = require('../utils/ApiError');
const { formatSongArtistFields } = require('../utils/songFormatter');

function getLocalDateKey(ts, tzOffsetHeader) {
  const parsedOffset = tzOffsetHeader !== undefined ? parseInt(tzOffsetHeader, 10) : -420;
  const offsetMinutes = Number.isNaN(parsedOffset) ? -420 : parsedOffset;
  const localTimeMs = new Date(ts).getTime() - offsetMinutes * 60 * 1000;
  return new Date(localTimeMs).toISOString().split('T')[0];
}

class HistoryController {
  /**
   * GET /api/v1/history
   * Lấy lịch sử nghe nhạc của người dùng hiện tại
   */
  static async getHistory(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      if (!userId) {
        throw ApiError.unauthorized('Vui lòng đăng nhập để xem lịch sử nghe nhạc');
      }

      const limit = Math.min(200, Math.max(1, parseInt(req.query.limit, 10) || 100));

      const records = await ListeningHistory.find({ userId })
        .sort({ timestamp: -1 })
        .limit(limit)
        .populate({
          path: 'songId',
          select: 'title artistId featuredArtistIds artistIds albumId duration audioUrl coverImageUrl lyrics playsCount',
          populate: [
            { path: 'artistId', select: 'name avatarUrl isVerified' },
            { path: 'featuredArtistIds', select: 'name avatarUrl isVerified' },
            { path: 'artistIds', select: 'name avatarUrl isVerified' },
            { path: 'albumId', select: 'title coverImageUrl' }
          ]
        })
        .lean();

      // Lấy danh sách yêu thích để gắn cờ isLiked
      const songIds = records.map(r => r.songId?._id).filter(Boolean);
      let likedSet = new Set();
      if (songIds.length > 0) {
        const favorites = await UserFavorite.find({ userId, songId: { $in: songIds } }).lean();
        likedSet = new Set(favorites.map(f => f.songId.toString()));
      }

      const formatted = [];
      for (const item of records) {
        if (!item.songId) continue; // Bỏ qua nếu bài hát đã bị xóa khỏi hệ thống

        const song = item.songId;
        const formattedArtist = formatSongArtistFields(song);
        const artistName = formattedArtist.artistName || 'Nghệ sĩ chưa rõ';

        const ts = item.timestamp ? new Date(item.timestamp) : new Date();
        // Tính ngày theo múi giờ client qua header x-timezone-offset (phút), mặc định UTC+7 (-420 phút)
        const dateStr = getLocalDateKey(ts, req.headers['x-timezone-offset']);

        formatted.push({
          _id: item._id,
          id: item._id,
          timestamp: ts.toISOString(),
          date: dateStr,
          listenDuration: item.listenDuration || 0,
          completed: Boolean(item.completed),
          playbackSource: item.playbackSource || 'home_feed',
          song: {
            _id: song._id,
            id: song._id,
            title: song.title,
            artistId: song.artistId?._id,
            artistName: artistName,
            albumId: song.albumId?._id,
            albumTitle: song.albumId?.title || '',
            duration: song.duration,
            audioUrl: song.audioUrl,
            coverImageUrl: song.coverImageUrl || song.albumId?.coverImageUrl || '',
            lyrics: song.lyrics || '',
            playsCount: song.playsCount || 0,
            isLiked: likedSet.has(song._id.toString())
          }
        });
      }

      return ApiResponse.success(res, formatted, 'Lấy lịch sử nghe nhạc thành công');
    } catch (error) {
      next(error);
    }
  }

  /**
   * DELETE /api/v1/history/:id
   * Xóa 1 bản ghi lịch sử cụ thể
   */
  static async deleteHistoryItem(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      if (!userId) {
        throw ApiError.unauthorized('Vui lòng đăng nhập để xóa bản ghi lịch sử');
      }
      const { id } = req.params;

      if (!mongoose.Types.ObjectId.isValid(id)) {
        throw ApiError.badRequest('ID lịch sử không hợp lệ');
      }

      const deleted = await ListeningHistory.findOneAndDelete({ _id: id, userId });
      if (!deleted) {
        return ApiResponse.success(res, { deletedCount: 0 }, 'Không tìm thấy bản ghi hoặc đã bị xóa trước đó');
      }

      return ApiResponse.success(res, { deletedCount: 1, id }, 'Đã xóa bản ghi khỏi lịch sử nghe nhạc');
    } catch (error) {
      next(error);
    }
  }

  /**
   * DELETE /api/v1/history
   * Xóa tất cả hoặc xóa theo ngày cụ thể (query: ?all=true hoặc ?date=YYYY-MM-DD)
   */
  static async deleteHistory(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      if (!userId) {
        throw ApiError.unauthorized('Vui lòng đăng nhập để thực hiện thao tác xóa lịch sử');
      }

      const { all, date } = req.query;

      if (all === 'true' || all === true) {
        const result = await ListeningHistory.deleteMany({ userId });
        return ApiResponse.success(res, { deletedCount: result.deletedCount }, 'Đã xóa toàn bộ lịch sử nghe nhạc thành công');
      }

      if (date) {
        // Hỗ trợ định dạng YYYY-MM-DD
        const dateRegex = /^\d{4}-\d{2}-\d{2}$/;
        if (!dateRegex.test(date)) {
          throw ApiError.badRequest('Định dạng ngày không hợp lệ. Vui lòng dùng YYYY-MM-DD');
        }

        const startOfDay = new Date(`${date}T00:00:00.000Z`);
        const endOfDay = new Date(`${date}T23:59:59.999Z`);

        // Để tính đến sai lệch múi giờ (UTC+7 của VN), mở rộng biên thêm +-14 tiếng
        const utcStart = new Date(startOfDay.getTime() - 14 * 60 * 60 * 1000);
        const utcEnd = new Date(endOfDay.getTime() + 14 * 60 * 60 * 1000);

        const records = await ListeningHistory.find({
          userId,
          timestamp: { $gte: utcStart, $lte: utcEnd }
        });

        const targetIds = records
          .filter(r => getLocalDateKey(r.timestamp, req.headers['x-timezone-offset']) === date)
          .map(r => r._id);

        let deletedCount = 0;
        if (targetIds.length > 0) {
          const deleteRes = await ListeningHistory.deleteMany({ userId, _id: { $in: targetIds } });
          deletedCount = deleteRes.deletedCount;
        }

        return ApiResponse.success(res, { deletedCount, date }, `Đã xóa ${deletedCount} bài hát trong ngày ${date}`);
      }

      throw ApiError.badRequest('Vui lòng truyền tham số ?all=true hoặc ?date=YYYY-MM-DD');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /api/v1/history/delete-batch
   * Xóa nhiều bản ghi theo danh sách IDs hoặc nhiều ngày
   */
  static async deleteBatch(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      if (!userId) {
        throw ApiError.unauthorized('Vui lòng đăng nhập để thực hiện');
      }

      const { ids, dates } = req.body;
      let totalDeleted = 0;

      // Xóa theo danh sách IDs
      if (Array.isArray(ids) && ids.length > 0) {
        const validIds = ids.filter(id => mongoose.Types.ObjectId.isValid(id));
        if (validIds.length > 0) {
          const resIds = await ListeningHistory.deleteMany({ userId, _id: { $in: validIds } });
          totalDeleted += resIds.deletedCount;
        }
      }

      // Xóa theo danh sách các ngày
      if (Array.isArray(dates) && dates.length > 0) {
        for (const date of dates) {
          if (typeof date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(date)) {
            const startOfDay = new Date(`${date}T00:00:00.000Z`);
            const endOfDay = new Date(`${date}T23:59:59.999Z`);
            const utcStart = new Date(startOfDay.getTime() - 14 * 60 * 60 * 1000);
            const utcEnd = new Date(endOfDay.getTime() + 14 * 60 * 60 * 1000);

            const records = await ListeningHistory.find({
              userId,
              timestamp: { $gte: utcStart, $lte: utcEnd }
            });

            const targetIds = records
              .filter(r => getLocalDateKey(r.timestamp, req.headers['x-timezone-offset']) === date)
              .map(r => r._id);

            if (targetIds.length > 0) {
              const delRes = await ListeningHistory.deleteMany({ userId, _id: { $in: targetIds } });
              totalDeleted += delRes.deletedCount;
            }
          }
        }
      }

      return ApiResponse.success(res, { deletedCount: totalDeleted }, `Đã xóa thành công ${totalDeleted} bản ghi lịch sử`);
    } catch (error) {
      next(error);
    }
  }
}

module.exports = HistoryController;
