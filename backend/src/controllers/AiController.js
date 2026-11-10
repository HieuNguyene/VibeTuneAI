const AiService = require('../services/aiService');
const AiChatService = require('../services/aiChatService');
const ApiResponse = require('../utils/ApiResponse');

class AiController {
  static async chat(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      const { message, conversationHistory } = req.body;
      const result = await AiChatService.handleChat(userId, message, conversationHistory);
      return ApiResponse.success(res, result, 'AI chat response generated successfully');
    } catch (error) {
      next(error);
    }
  }

  static async generatePlaylist(req, res, next) {
    try {
      const { prompt, savePlaylist } = req.body;
      const result = await AiService.generatePlaylistFromPrompt(
        req.user._id,
        prompt,
        savePlaylist !== false
      );
      return ApiResponse.success(res, result, 'AI playlist generated successfully');
    } catch (error) {
      next(error);
    }
  }

  static async getRecommendations(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      const { limit } = req.query;
      const songs = await AiService.getRecommendations(userId, parseInt(limit, 10) || 15);
      return ApiResponse.success(res, songs, 'Personalized recommendations generated');
    } catch (error) {
      next(error);
    }
  }

  static async getPromptHistory(req, res, next) {
    try {
      const logs = await AiService.getPromptHistory(req.user._id);
      return ApiResponse.success(res, logs, 'AI prompt history retrieved');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = AiController;
