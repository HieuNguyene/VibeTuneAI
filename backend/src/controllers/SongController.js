const SongService = require('../services/songService');
const ApiResponse = require('../utils/ApiResponse');

class SongController {
  static async getHomeFeed(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      const feed = await SongService.getHomeFeed(userId);
      return ApiResponse.success(res, feed, 'Home feed retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async search(req, res, next) {
    try {
      const { q } = req.query;
      const userId = req.user ? req.user._id : null;
      const results = await SongService.search(q, userId);
      return ApiResponse.success(res, results, 'Search results retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async getSongById(req, res, next) {
    try {
      const { id } = req.params;
      const userId = req.user ? req.user._id : null;
      const song = await SongService.getSongById(id, userId);
      return ApiResponse.success(res, song, 'Song retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async getSongsByGenre(req, res, next) {
    try {
      const { genreId } = req.params;
      const { page, limit } = req.query;
      const result = await SongService.getSongsByGenre(genreId, {
        page: parseInt(page, 10) || 1,
        limit: parseInt(limit, 10) || 20
      });
      return ApiResponse.success(res, result, 'Genre songs retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async getSongsByArtist(req, res, next) {
    try {
      const { artistId } = req.params;
      const userId = req.user ? req.user._id : null;
      const result = await SongService.getSongsByArtist(artistId, userId);
      return ApiResponse.success(res, result, 'Artist songs retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async toggleFavorite(req, res, next) {
    try {
      const { id } = req.params;
      const result = await SongService.toggleFavorite(req.user._id, id);
      return ApiResponse.success(res, result, result.isLiked ? 'Added to favorites' : 'Removed from favorites');
    } catch (error) {
      next(error);
    }
  }

  static async getUserFavorites(req, res, next) {
    try {
      const { page, limit } = req.query;
      const result = await SongService.getUserFavorites(req.user._id, {
        page: parseInt(page, 10) || 1,
        limit: parseInt(limit, 10) || 50
      });
      return ApiResponse.success(res, result, 'Favorites retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async logPlayback(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      const result = await SongService.logPlayback(userId, req.body);
      return ApiResponse.success(res, result, 'Playback logged successfully');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = SongController;
