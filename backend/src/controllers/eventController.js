const mongoose = require('mongoose');
const { ListeningEvent, Song, PlaySession } = require('../models');
const ApiResponse = require('../utils/ApiResponse');
const ApiError = require('../utils/ApiError');

class EventController {
  /**
   * POST /events
   * Ghi nhận sự kiện phát nhạc từ app (play, skip, complete, like, dislike)
   */
  static async logEvent(req, res, next) {
    try {
      const {
        sessionId,
        trackId,
        songId,
        event,
        positionSec = 0,
        source = 'browse'
      } = req.body;

      const targetTrackId = trackId || songId;
      if (!targetTrackId || !mongoose.Types.ObjectId.isValid(String(targetTrackId))) {
        throw ApiError.badRequest('trackId or songId is required and must be a valid ObjectId');
      }

      const song = await Song.findById(targetTrackId).lean();
      if (!song) {
        throw ApiError.notFound('Không tìm thấy bài hát trong hệ thống');
      }

      const validEvents = ['play', 'skip', 'complete', 'like', 'dislike'];
      if (!event || !validEvents.includes(event)) {
        throw ApiError.badRequest(`Invalid event type. Allowed: ${validEvents.join(', ')}`);
      }

      const userId = (req.user && req.user._id) ? req.user._id : null;

      // Ngăn IDOR: nếu truyền sessionId thuộc về user khác -> chặn 401/403
      let validSessionObjId = null;
      if (sessionId) {
        if (!mongoose.Types.ObjectId.isValid(sessionId)) {
          throw ApiError.badRequest('Invalid sessionId');
        }
        const sessionDoc = await PlaySession.findById(sessionId).select('userId').lean();
        if (sessionDoc && sessionDoc.userId) {
          if (!userId) {
            throw ApiError.unauthorized('Vui lòng đăng nhập để ghi sự kiện cho phiên phát cá nhân');
          }
          if (String(sessionDoc.userId) !== String(userId)) {
            throw ApiError.forbidden('Không có quyền cập nhật phiên phát nhạc của người dùng khác');
          }
        }
        validSessionObjId = sessionDoc ? sessionDoc._id : null;
      }

      // Tạo event document trong listening_events (kèm sessionId nếu có)
      const eventDoc = await ListeningEvent.create({
        userId,
        sessionId: validSessionObjId,
        trackId: targetTrackId,
        event,
        positionSec: Math.max(0, Number(positionSec) || 0),
        source,
        ts: new Date()
      });

      // Tự động cập nhật playsCount trên Song khi có sự kiện 'play'
      if (event === 'play') {
        Song.findByIdAndUpdate(targetTrackId, { $inc: { playsCount: 1 } }).exec().catch(() => {});
      }

      // Cập nhật Mood Flow session nếu sự kiện phát ra từ một session hợp lệ
      if (validSessionObjId && song) {
        try {
          const { updateSessionOnEvent } = require('../services/moodFlow');
          await updateSessionOnEvent(validSessionObjId, event, song, Math.max(0, Number(positionSec) || 0));
        } catch (err) {
          console.warn('[EventController] Lỗi cập nhật session moodFlow:', err.message);
        }
      }

      // Ghi nhận vào UserInteraction để phục vụ Vector Re-ranking & Personalized AI Chatbot
      if (userId) {
        const typeMap = {
          like: 'LIKE',
          complete: 'LISTEN_COMPLETE',
          skip: 'SKIP',
          play: 'PLAY'
        };
        const interactionType = typeMap[event];
        if (interactionType) {
          const { UserInteraction } = require('../models');
          UserInteraction.create({
            userId,
            songId: targetTrackId,
            interactionType,
            weight: UserInteraction.WEIGHTS[interactionType] ?? 0.2,
            createdAt: new Date()
          }).catch(() => {});
        }
      }

      return ApiResponse.success(res, eventDoc, 'Event recorded successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /events/interactions hoặc POST /interactions
   */
  static async logInteraction(req, res, next) {
    try {
      const { songId, trackId, interactionType, weight } = req.body;
      const targetId = songId || trackId;
      if (!targetId) {
        throw ApiError.badRequest('songId is required');
      }
      const userId = (req.user && req.user._id) ? req.user._id : null;
      if (!userId) {
        return ApiResponse.success(res, null, 'Ignored anonymous interaction');
      }

      const { UserInteraction } = require('../models');
      const validTypes = ['LIKE', 'LISTEN_COMPLETE', 'SKIP', 'PLAY'];
      const type = (interactionType || 'PLAY').toUpperCase();
      if (!validTypes.includes(type)) {
        throw ApiError.badRequest(`Invalid interactionType. Allowed: ${validTypes.join(', ')}`);
      }

      const interaction = await UserInteraction.create({
        userId,
        songId: targetId,
        interactionType: type,
        weight: typeof weight === 'number' ? weight : (UserInteraction.WEIGHTS[type] ?? 0.2),
        createdAt: new Date()
      });

      return ApiResponse.success(res, interaction, 'User interaction saved');
    } catch (error) {
      next(error);
    }
  }

  /**
   * GET /events/my-summary
   * Tóm tắt hành vi nghe nhạc của người dùng hiện tại
   */
  static async getUserSummary(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      if (!userId) {
        return ApiResponse.success(res, { totalEvents: 0, topTracks: [] });
      }

      const stats = await ListeningEvent.aggregate([
        { $match: { userId } },
        { $group: { _id: '$event', count: { $sum: 1 } } }
      ]);

      return ApiResponse.success(res, { stats }, 'User listening summary retrieved');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = EventController;
