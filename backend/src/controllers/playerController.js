const mongoose = require('mongoose');
const { PlaySession, Song } = require('../models');
const moodFlow = require('../services/moodFlow');
const ApiResponse = require('../utils/ApiResponse');
const ApiError = require('../utils/ApiError');

class PlayerController {
  /**
   * POST /player/session
   * Khởi tạo một phiên phát nhạc Mood Flow
   * Body: { firstTrackId, arc: 'wind_down' | 'workout' | 'focus' | 'steady' }
   */
  static async createSession(req, res, next) {
    try {
      const { firstTrackId, songId, arc = 'steady' } = req.body;
      const targetTrackId = firstTrackId || songId;

      const validArcs = ['wind_down', 'workout', 'focus', 'steady'];
      const chosenArc = validArcs.includes(arc) ? arc : 'steady';

      let firstTrack = null;
      if (targetTrackId && mongoose.Types.ObjectId.isValid(targetTrackId)) {
        firstTrack = await Song.findById(targetTrackId)
          .populate('artistId', 'name avatarUrl')
          .lean();
      }

      if (!firstTrack) {
        firstTrack = await Song.findOne({ isPublic: { $ne: false } })
          .populate('artistId', 'name avatarUrl')
          .lean();
      }

      if (!firstTrack) {
        throw ApiError.notFound('No tracks available in database to start a session');
      }

      const userId = (req.user && req.user._id) ? req.user._id : null;
      const session = await moodFlow.initSession(firstTrack, userId, chosenArc);

      const responsePayload = {
        sessionId: String(session._id),
        arc: session.arc,
        position: session.position || 0,
        firstTrack: {
          id: String(firstTrack._id),
          title: firstTrack.title,
          artist: firstTrack.artistId?.name || 'Nghệ sĩ',
          coverUrl: firstTrack.coverImageUrl || '',
          streamUrl: firstTrack.audioUrl
        }
      };

      return res.status(201).json({
        success: true,
        statusCode: 201,
        message: 'Session created successfully',
        data: responsePayload,
        sessionId: responsePayload.sessionId,
        arc: responsePayload.arc,
        position: responsePayload.position
      });
    } catch (error) {
      next(error);
    }
  }

  /**
   * GET /player/next
   * Lấy bài hát tiếp theo được cá nhân hóa qua Mood Flow
   * Query: sessionId (bắt buộc hoặc tự tạo nếu kèm currentTrackId), debug (boolean)
   */
  static async getNext(req, res, next) {
    const startTime = Date.now();
    try {
      const { sessionId, currentTrackId, trackId, arc, debug } = req.query;
      const isDebug = debug === 'true' || debug === '1';
      const effectiveTrackId = currentTrackId || trackId;

      if (!sessionId && !effectiveTrackId) {
        throw ApiError.badRequest('sessionId hoặc currentTrackId là bắt buộc');
      }

      const userId = (req.user && req.user._id) ? req.user._id : null;

      const result = await moodFlow.getNextTrack({
        sessionId,
        currentTrackId: effectiveTrackId,
        arc,
        userId,
        debug: isDebug
      });

      const latencyMs = Date.now() - startTime;
      if (result.debug) {
        result.debug.latencyMs = latencyMs;
      }

      return res.status(200).json({
        success: true,
        statusCode: 200,
        message: 'Next track recommended successfully',
        data: result,
        track: result.track,
        reason: result.reason,
        sessionId: result.sessionId,
        debug: result.debug
      });
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /player/session/:id/arc
   * Đổi mục tiêu hành trình âm nhạc (Arc) giữa chừng cho phiên phát nhạc
   * Body: { arc: 'workout' | 'wind_down' | 'focus' | 'steady' }
   */
  static async updateArc(req, res, next) {
    try {
      const { id } = req.params;
      const { arc } = req.body;

      const validArcs = ['wind_down', 'workout', 'focus', 'steady'];
      if (!arc || !validArcs.includes(arc)) {
        throw ApiError.badRequest(`Invalid arc type. Allowed: ${validArcs.join(', ')}`);
      }

      if (!id || !mongoose.Types.ObjectId.isValid(id)) {
        throw ApiError.badRequest('Invalid session ID');
      }

      const session = await PlaySession.findById(id);
      if (!session) {
        throw ApiError.notFound('Play session not found');
      }

      if (session.userId) {
        if (!req.user) {
          throw ApiError.unauthorized('Vui lòng đăng nhập để chỉnh sửa phiên phát nhạc cá nhân');
        }
        if (String(session.userId) !== String(req.user._id)) {
          throw ApiError.forbidden('Không có quyền chỉnh sửa phiên phát nhạc của người dùng khác');
        }
      }

      session.arc = arc;
      session.lastEventAt = new Date();
      await session.save();

      const responsePayload = {
        sessionId: String(session._id),
        arc: session.arc
      };

      return ApiResponse.success(res, responsePayload, 'Arc updated successfully');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = PlayerController;
