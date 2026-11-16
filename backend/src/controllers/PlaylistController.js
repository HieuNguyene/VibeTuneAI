const PlaylistService = require('../services/playlistService');
const ApiResponse = require('../utils/ApiResponse');

class PlaylistController {
  static async getUserPlaylists(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      const filter = req.query.filter || (req.query.personalOnly === 'true' ? 'personal' : 'all');
      const playlists = await PlaylistService.getUserPlaylists(userId, filter);
      return ApiResponse.success(res, playlists, 'Playlists retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async getPublicPlaylists(req, res, next) {
    try {
      const { page, limit } = req.query;
      const result = await PlaylistService.getPublicPlaylists({
        page: parseInt(page, 10) || 1,
        limit: parseInt(limit, 10) || 20
      });
      return ApiResponse.success(res, result, 'Public playlists retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async getPlaylistById(req, res, next) {
    try {
      const { id } = req.params;
      const userId = req.user ? req.user._id : null;
      const playlist = await PlaylistService.getPlaylistById(id, userId);
      return ApiResponse.success(res, playlist, 'Playlist details retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async createPlaylist(req, res, next) {
    try {
      const playlist = await PlaylistService.createPlaylist(req.user._id, req.body);
      return ApiResponse.created(res, playlist, 'Playlist created successfully');
    } catch (error) {
      next(error);
    }
  }

  static async addTrack(req, res, next) {
    try {
      const { id } = req.params;
      const { songId } = req.body;
      const track = await PlaylistService.addTrackToPlaylist(req.user._id, id, songId);
      return ApiResponse.created(res, track, 'Track added to playlist');
    } catch (error) {
      next(error);
    }
  }

  static async removeTrack(req, res, next) {
    try {
      const { id, songId } = req.params;
      const result = await PlaylistService.removeTrackFromPlaylist(req.user._id, id, songId);
      return ApiResponse.success(res, result, 'Track removed from playlist');
    } catch (error) {
      next(error);
    }
  }

  static async reorderTracks(req, res, next) {
    try {
      const { id } = req.params;
      const { trackOrders } = req.body;
      const result = await PlaylistService.reorderTracks(req.user._id, id, trackOrders);
      return ApiResponse.success(res, result, 'Tracks reordered successfully');
    } catch (error) {
      next(error);
    }
  }

  static async updatePlaylist(req, res, next) {
    try {
      const { id } = req.params;
      const playlist = await PlaylistService.updatePlaylist(req.user._id, id, req.body);
      return ApiResponse.success(res, playlist, 'Playlist updated successfully');
    } catch (error) {
      next(error);
    }
  }

  static async deletePlaylist(req, res, next) {
    try {
      const { id } = req.params;
      const result = await PlaylistService.deletePlaylist(req.user._id, id);
      return ApiResponse.success(res, result, 'Playlist deleted successfully');
    } catch (error) {
      next(error);
    }
  }

  static async toggleFavoritePlaylist(req, res, next) {
    try {
      const { id } = req.params;
      const result = await PlaylistService.toggleFavoritePlaylist(req.user._id, id);
      return ApiResponse.success(
        res,
        result,
        result.isFavorite ? 'Playlist added to favorites' : 'Playlist removed from favorites'
      );
    } catch (error) {
      next(error);
    }
  }

  static async getFavoritePlaylists(req, res, next) {
    try {
      const playlists = await PlaylistService.getFavoritePlaylists(req.user._id);
      return ApiResponse.success(res, playlists, 'Favorite playlists retrieved successfully');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = PlaylistController;
