const models = require('../models');
const ApiResponse = require('../utils/ApiResponse');

class AdminController {
  async getSummary(req, res, next) {
    try {
      const [
        songsCount,
        artistsCount,
        albumsCount,
        genresCount,
        playlistsCount,
        usersCount,
        historiesCount,
        aiLogsCount
      ] = await Promise.all([
        models.Song.countDocuments(),
        models.Artist.countDocuments(),
        models.Album.countDocuments(),
        models.Genre.countDocuments(),
        models.Playlist.countDocuments(),
        models.User.countDocuments(),
        models.ListeningHistory.countDocuments(),
        models.AiPromptLog.countDocuments()
      ]);

      return ApiResponse.success(res, {
        summary: {
          songs: songsCount,
          artists: artistsCount,
          albums: albumsCount,
          genres: genresCount,
          playlists: playlistsCount,
          users: usersCount,
          histories: historiesCount,
          aiLogs: aiLogsCount
        }
      }, 'Thống kê cơ sở dữ liệu thành công');
    } catch (error) {
      next(error);
    }
  }

  async getCollectionData(req, res, next) {
    try {
      const { collection } = req.params;
      const limit = parseInt(req.query.limit) || 100;
      let data = [];

      switch (collection.toLowerCase()) {
        case 'songs':
          data = await models.Song.find()
            .populate('artistId', 'name avatarUrl isVerified')
            .populate('featuredArtistIds', 'name avatarUrl')
            .populate('albumId', 'title coverImageUrl')
            .populate('genreIds', 'name colorCode')
            .limit(limit)
            .lean();
          break;
        case 'artists': {
          const rawArtists = await models.Artist.find()
            .populate('genreIds', 'name colorCode')
            .limit(limit)
            .lean();

          const allSongs = await models.Song.find({ isPublic: true })
            .select('title artistId featuredArtistIds audioUrl coverImageUrl duration genreIds')
            .populate('genreIds', 'name')
            .lean();

          data = rawArtists.map(art => {
            const artIdStr = art._id.toString();
            const artistSongs = allSongs.filter(s => 
              (s.artistId && s.artistId.toString() === artIdStr) ||
              (s.featuredArtistIds && s.featuredArtistIds.some(f => f.toString() === artIdStr))
            );
            return {
              ...art,
              songsCount: artistSongs.length,
              songs: artistSongs
            };
          });
          break;
        }
        case 'albums':
          data = await models.Album.find()
            .populate('artistId', 'name avatarUrl')
            .populate('genreIds', 'name colorCode')
            .limit(limit)
            .lean();
          break;
        case 'genres':
          data = await models.Genre.find().limit(limit).lean();
          break;
        case 'playlists':
          data = await models.Playlist.find()
            .populate('creatorId', 'displayName email')
            .limit(limit)
            .lean();
          break;
        case 'users':
          data = await models.User.find().select('-passwordHash').limit(limit).lean();
          break;
        case 'histories':
        case 'listeninghistories':
          data = await models.ListeningHistory.find()
            .populate('userId', 'displayName email')
            .populate({
              path: 'songId',
              select: 'title coverImageUrl duration artistId',
              populate: { path: 'artistId', select: 'name' }
            })
            .limit(limit)
            .sort({ timestamp: -1 })
            .lean();
          break;
        case 'ailogs':
        case 'aipromptlogs':
          data = await models.AiPromptLog.find()
            .populate('userId', 'displayName email')
            .populate('generatedPlaylistId', 'title totalTracks')
            .limit(limit)
            .sort({ createdAt: -1 })
            .lean();
          break;
        case 'favorites':
        case 'userfavorites':
          data = await models.UserFavorite.find()
            .populate('userId', 'displayName email')
            .populate('songId', 'title coverImageUrl')
            .limit(limit)
            .sort({ createdAt: -1 })
            .lean();
          break;
        default:
          return res.status(400).json({ success: false, message: 'Collection không hợp lệ' });
      }

      return ApiResponse.success(res, { collection, count: data.length, items: data });
    } catch (error) {
      next(error);
    }
  }

  async getUploadFiles(req, res, next) {
    try {
      const fs = require('fs');
      const path = require('path');
      const audiosDir = path.join(__dirname, '../../public/uploads/audios');
      const coversDir = path.join(__dirname, '../../public/uploads/covers');

      const audios = fs.existsSync(audiosDir) ? fs.readdirSync(audiosDir) : [];
      const covers = fs.existsSync(coversDir) ? fs.readdirSync(coversDir) : [];

      return ApiResponse.success(res, { audios, covers });
    } catch (error) {
      next(error);
    }
  }

  async addSong(req, res, next) {
    try {
      const { title, artistName, genreSlug, audioFileName, coverFileName, lyrics, duration, mood, tempo } = req.body;

      if (!title || !audioFileName) {
        return res.status(400).json({ success: false, message: 'Vui lòng nhập tên bài hát và chọn file nhạc!' });
      }

      // 1. Tìm hoặc tạo nghệ sĩ
      let artist = await models.Artist.findOne({ name: artistName || 'Nghệ Sĩ Mới' });
      if (!artist) {
        artist = await models.Artist.create({
          name: artistName || 'Nghệ Sĩ Mới',
          slug: (artistName || 'nghe-si-moi').toLowerCase().replace(/\s+/g, '-'),
          avatarUrl: coverFileName ? `/uploads/covers/${coverFileName}` : 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&q=80',
          bio: 'Nghệ sĩ do người dùng tự thêm.'
        });
      }

      // 2. Tìm thể loại
      let genre = await models.Genre.findOne({ slug: genreSlug });
      if (!genre) {
        genre = await models.Genre.findOne();
      }

      // 3. Chuẩn hóa tên file an toàn cho HTTP URL (loại bỏ dấu cách, dấu ngoặc vuông và ký tự #)
      let safeAudioName = audioFileName;
      if (!audioFileName.startsWith('http') && (audioFileName.includes('#') || audioFileName.includes('[') || audioFileName.includes(' '))) {
        const fs = require('fs');
        const path = require('path');
        const oldPath = path.join(__dirname, '../../public/uploads/audios', audioFileName);
        const cleanName = audioFileName.replace(/[#\[\]()]/g, '').trim().replace(/\s+/g, '_');
        const newPath = path.join(__dirname, '../../public/uploads/audios', cleanName);
        if (fs.existsSync(oldPath)) {
          fs.renameSync(oldPath, newPath);
          safeAudioName = cleanName;
        }
      }

      // 4. Tạo bài hát mới
      const newSong = await models.Song.create({
        title,
        artistId: artist._id,
        genreIds: genre ? [genre._id] : [],
        duration: parseInt(duration) || 210,
        audioUrl: safeAudioName.startsWith('http') ? safeAudioName : `/uploads/audios/${safeAudioName}`,
        coverImageUrl: coverFileName ? (coverFileName.startsWith('http') ? coverFileName : `/uploads/covers/${coverFileName}`) : 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80',
        lyrics: lyrics || '',
        mood: mood ? (Array.isArray(mood) ? mood : [mood]) : ['chill'],
        tempo: tempo || 'medium',
        isPublic: true
      });

      return ApiResponse.success(res, newSong, 'Thêm bài hát thành công!');
    } catch (error) {
      next(error);
    }
  }

  async deleteSong(req, res, next) {
    try {
      const { id } = req.params;
      await models.Song.findByIdAndDelete(id);
      return ApiResponse.success(res, null, 'Đã xóa bài hát thành công!');
    } catch (error) {
      next(error);
    }
  }

  async updateSongLyrics(req, res, next) {
    try {
      const { id } = req.params;
      const { lyrics } = req.body;
      const song = await models.Song.findByIdAndUpdate(
        id,
        { lyrics: lyrics || '' },
        { new: true }
      );
      if (!song) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy bài hát' });
      }
      return ApiResponse.success(res, song, 'Đã cập nhật lời bài hát thành công!');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new AdminController();
