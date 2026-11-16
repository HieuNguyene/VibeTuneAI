const SongService = require('../services/songService');
const ApiResponse = require('../utils/ApiResponse');

class SongController {
  static async getAllSongs(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      const result = await SongService.getAllSongs(req.query, userId);
      return ApiResponse.success(res, result, 'Songs retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async getHomeFeed(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      const feed = await SongService.getHomeFeed(userId);
      return ApiResponse.success(res, feed, 'Home feed retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async getFollowedMusic(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      const { sort } = req.query;
      const data = await SongService.getFollowedArtistsMusic(userId, { sort: sort || 'newest' });
      return ApiResponse.success(res, data, 'Followed artists music retrieved successfully');
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
      const { page, limit, sort } = req.query;
      const userId = req.user ? req.user._id : null;
      const result = await SongService.getSongsByGenre(genreId, {
        page: parseInt(page, 10) || 1,
        limit: parseInt(limit, 10) || 50,
        sort: sort || 'popular',
        userId
      });
      return ApiResponse.success(res, result, 'Genre songs retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async getSongsByArtist(req, res, next) {
    try {
      const { artistId } = req.params;
      const { sort } = req.query;
      const userId = req.user ? req.user._id : null;
      const result = await SongService.getSongsByArtist(artistId, userId, sort || 'popular');
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

  static async getSimilarSongs(req, res, next) {
    try {
      const { id } = req.params;
      const { limit, genreId, mood } = req.query;
      const CosineRecommender = require('../ai/cosineRecommender');
      const similarSongs = await CosineRecommender.findSimilarSongs(id, {
        limit: parseInt(limit, 10) || 8,
        genreId,
        mood
      });
      return ApiResponse.success(res, similarSongs, 'Similar songs retrieved via Cosine Similarity');
    } catch (error) {
      next(error);
    }
  }

  static async streamSong(req, res, next) {
    try {
      const fs = require('fs');
      const path = require('path');
      const { Song } = require('../models');
      const ApiError = require('../utils/ApiError');

      const song = await Song.findById(req.params.id);
      if (!song) {
        throw ApiError.notFound('Song not found');
      }

      let filePath = song.filePath;
      if (!filePath || !fs.existsSync(filePath)) {
        filePath = path.join(__dirname, '../../public', song.audioUrl);
      }

      if (!fs.existsSync(filePath)) {
        throw ApiError.notFound('Audio file not found on server');
      }

      const stat = fs.statSync(filePath);
      const fileSize = stat.size;
      const ext = path.extname(filePath).toLowerCase();
      const mimeMap = {
        '.mp3': 'audio/mpeg',
        '.m4a': 'audio/mp4',
        '.aac': 'audio/aac',
        '.flac': 'audio/flac',
        '.wav': 'audio/wav',
        '.ogg': 'audio/ogg'
      };
      const contentType = mimeMap[ext] || 'audio/mpeg';

      const range = req.headers.range;

      if (range) {
        let start = 0;
        let end = fileSize - 1;
        const matches = range.match(/bytes=(\d*)-(\d*)/);
        if (matches) {
          if (matches[1]) {
            start = parseInt(matches[1], 10);
            if (matches[2]) end = parseInt(matches[2], 10);
          } else if (matches[2]) {
            start = Math.max(0, fileSize - parseInt(matches[2], 10));
          }
        }

        if (isNaN(start) || isNaN(end) || start > end || start >= fileSize) {
          res.status(416).setHeader('Content-Range', `bytes */${fileSize}`);
          return res.send('Requested range not satisfiable');
        }

        end = Math.min(end, fileSize - 1);
        const chunksize = (end - start) + 1;
        const file = fs.createReadStream(filePath, { start, end });
        file.on('error', (err) => {
          if (!res.headersSent) next(err);
        });

        const head = {
          'Content-Range': `bytes ${start}-${end}/${fileSize}`,
          'Accept-Ranges': 'bytes',
          'Content-Length': chunksize,
          'Content-Type': contentType,
        };

        res.writeHead(206, head);
        file.pipe(res);
      } else {
        const head = {
          'Content-Length': fileSize,
          'Accept-Ranges': 'bytes',
          'Content-Type': contentType,
        };
        res.writeHead(200, head);
        const file = fs.createReadStream(filePath);
        file.on('error', (err) => {
          if (!res.headersSent) next(err);
        });
        file.pipe(res);
      }
    } catch (error) {
      next(error);
    }
  }
}

module.exports = SongController;
