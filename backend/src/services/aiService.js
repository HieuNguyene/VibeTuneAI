const GeminiProvider = require('../ai/geminiProvider');
const PlaylistGenerator = require('../ai/playlistGenerator');
const RecommendationEngine = require('../ai/recommendationEngine');
const AiPromptLog = require('../models/AiPromptLog');
const ApiError = require('../utils/ApiError');

class AiService {
  /**
   * Tạo Playlist từ Prompt ngôn ngữ tự nhiên thông qua Gemini LLM
   */
  static async generatePlaylistFromPrompt(userId, userPrompt, savePlaylist = true) {
    if (!userPrompt || userPrompt.trim() === '') {
      throw ApiError.badRequest('Prompt text cannot be empty');
    }

    // 1. Phân tích prompt ngôn ngữ tự nhiên -> Tiêu chí có cấu trúc (Structured Criteria)
    const { criteria, provider, latencyMs, tokensUsed } = await GeminiProvider.extractMusicCriteria(userPrompt);

    // 2. Truy vấn danh sách bài hát thực tế từ MongoDB
    const songs = await PlaylistGenerator.retrieveAndRankSongs(criteria, 15);

    let createdPlaylist = null;
    if (savePlaylist && songs.length > 0) {
      // 3. Lưu playlist vào MongoDB nếu người dùng yêu cầu
      createdPlaylist = await PlaylistGenerator.persistAiPlaylist(
        userId,
        criteria,
        songs,
        { userPrompt, provider, latencyMs, tokensUsed }
      );
    } else {
      // Lưu log kiểm toán ngay cả khi không tạo playlist cố định
      await AiPromptLog.create({
        userId,
        userPrompt,
        aiExtractedCriteria: criteria,
        suggestedSongIds: songs.map((s) => s._id),
        provider,
        latencyMs,
        tokensUsed
      });
    }

    return {
      criteria,
      songs,
      playlist: createdPlaylist,
      metadata: {
        provider,
        latencyMs,
        tracksFound: songs.length
      }
    };
  }

  /**
   * Lấy danh sách bài hát gợi ý cá nhân hoá (Personalized Recommendations)
   */
  static async getRecommendations(userId, limit = 15) {
    return RecommendationEngine.getPersonalizedFeed(userId, limit);
  }

  /**
   * Lấy lịch sử prompt AI của người dùng
   */
  static async getPromptHistory(userId) {
    const logs = await AiPromptLog.find({ userId })
      .sort({ createdAt: -1 })
      .limit(20)
      .populate('generatedPlaylistId', 'title totalTracks coverImageUrl')
      .lean();

    return logs;
  }
}

module.exports = AiService;
