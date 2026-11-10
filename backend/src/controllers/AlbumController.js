const AlbumService = require('../services/albumService');
const ApiResponse = require('../utils/ApiResponse');

class AlbumController {
  static async getPopularAlbums(req, res, next) {
    try {
      const limit = parseInt(req.query.limit, 10) || 20;
      const albums = await AlbumService.getPopularAlbums(limit);
      return ApiResponse.success(res, albums, 'Popular albums retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async getAlbumById(req, res, next) {
    try {
      const { id } = req.params;
      const userId = req.user ? req.user._id : null;
      const album = await AlbumService.getAlbumById(id, userId);
      return ApiResponse.success(res, album, 'Album details retrieved successfully');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = AlbumController;
