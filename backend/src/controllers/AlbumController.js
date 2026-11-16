const AlbumService = require('../services/albumService');
const ApiResponse = require('../utils/ApiResponse');

class AlbumController {
  static async getPopularAlbums(req, res, next) {
    try {
      const limit = parseInt(req.query.limit, 10) || 100;
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

  static async getFavoriteAlbums(req, res, next) {
    try {
      const userId = req.user._id;
      const albums = await AlbumService.getFavoriteAlbums(userId);
      return ApiResponse.success(res, albums, 'Favorite albums retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async toggleFavoriteAlbum(req, res, next) {
    try {
      const userId = req.user._id;
      const { id } = req.params;
      const result = await AlbumService.toggleFavoriteAlbum(userId, id);
      return ApiResponse.success(res, result, result.isFavorite ? 'Album added to favorites' : 'Album removed from favorites');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = AlbumController;
