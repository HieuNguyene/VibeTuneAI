const fs = require('fs');
const path = require('path');
const mongoose = require('mongoose');
const models = require('../models');
const ApiResponse = require('../utils/ApiResponse');
const AlbumService = require('../services/albumService');
const PlaylistService = require('../services/playlistService');
const bcrypt = require('bcryptjs');
const { saveValidatedBase64Upload, sanitizeMediaUrl } = require('../utils/fileValidator');
const LastFmService = require('../services/lastFmService');
const EmbeddingService = require('../services/embeddingService');

/**
 * Tự động chuẩn hóa moods, tags, semanticText và vector embedding 768 chiều cho bài hát mới/cập nhật
 */
async function syncSongAiMetadata(songId, { regenerateEmbedding = false } = {}) {
  try {
    const song = await models.Song.findById(songId)
      .populate('artistId', 'name')
      .populate('genreIds', 'name');
    if (!song) return;

    const baseMoods = Array.isArray(song.moods) && song.moods.length > 0
      ? song.moods
      : (Array.isArray(song.mood) && song.mood.length > 0 ? song.mood : ['chill']);
    song.mood = baseMoods;
    song.moods = baseMoods;

    const artistName = song.artistId?.name || 'Nghệ sĩ';
    const genreNames = (song.genreIds || []).map((g) => g?.name || String(g)).filter(Boolean);
    const enrichedTags = await LastFmService.enrichTags({
      title: song.title,
      artist: artistName,
      genres: genreNames,
      lyrics: song.lyrics || ''
    });

    song.tags = Array.from(new Set([...(song.tags || []), ...enrichedTags]))
      .filter((t) => t && !['podcast', 'talkshow'].includes(String(t).toLowerCase()));
    song.semanticText = EmbeddingService.generateSongSemanticText(
      song,
      artistName,
      genreNames,
      song.tags
    );

    const needsEmbedding = regenerateEmbedding || !Array.isArray(song.embedding) || song.embedding.length === 0;
    if (needsEmbedding && process.env.GEMINI_API_KEY) {
      try {
        const embedding = await Promise.race([
          EmbeddingService.generateEmbedding(song.semanticText, 1),
          new Promise((_, reject) => setTimeout(() => reject(new Error('Embedding timeout')), 4500))
        ]);
        if (Array.isArray(embedding) && embedding.length > 0) {
          song.embedding = embedding;
        }
      } catch (embErr) {
        console.warn(`[AdminController] Bỏ qua sinh embedding tức thời cho "${song.title}":`, embErr.message);
      }
    }

    await song.save();
  } catch (err) {
    console.warn('[AdminController] syncSongAiMetadata warning:', err.message);
  }
}

const UPLOADS_AUDIOS_ROOT = path.resolve(__dirname, '../../public/uploads/audios');

function resolveLocalAudioPath(audioUrl) {
  if (!audioUrl || typeof audioUrl !== 'string') return null;
  if (!audioUrl.startsWith('/uploads/audios/')) return null;
  const relPath = audioUrl.replace(/^\/+/, '');
  const fullPath = path.resolve(__dirname, '../../public', relPath);
  if (!fullPath.startsWith(UPLOADS_AUDIOS_ROOT)) return null;
  return fs.existsSync(fullPath) ? fullPath : null;
}

async function extractAudioInfo(filePath) {
  const result = { duration: null, metadata: {} };
  if (!filePath || !fs.existsSync(filePath)) return result;
  try {
    const mm = await import('music-metadata');
    const meta = await mm.parseFile(filePath, { duration: true, skipCovers: true });
    if (meta.format && Number.isFinite(meta.format.duration) && meta.format.duration > 0) {
      result.duration = Math.round(meta.format.duration);
    }
    if (meta.common) {
      result.metadata = {
        title: meta.common.title || '',
        artist: meta.common.artist || '',
        album: meta.common.album || '',
        year: meta.common.year || null,
        bitrate: meta.format?.bitrate || null,
        sampleRate: meta.format?.sampleRate || null
      };
    }
  } catch (metaErr) {
    console.warn('[AdminController] Không thể trích xuất audio metadata:', metaErr.message);
  }
  return result;
}

async function readAudioDurationSeconds(audioUrl, fallbackSeconds = null) {
  const filePath = resolveLocalAudioPath(audioUrl);
  if (!filePath) return fallbackSeconds;
  const info = await extractAudioInfo(filePath);
  return info.duration && info.duration > 0 ? info.duration : fallbackSeconds;
}

function slugify(text) {
  if (!text) return '';
  return text
    .toString()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[đĐ]/g, 'd')
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9 -]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-');
}

async function resolveArtistsData({ artistId, featuredArtistIds, artistIds, artistName, coverUrl }) {
  const mongoose = require('mongoose');
  let allIds = [];

  if (Array.isArray(artistIds) && artistIds.length > 0) {
    allIds = [...artistIds];
  } else {
    if (artistId) allIds.push(artistId);
    if (Array.isArray(featuredArtistIds)) {
      allIds.push(...featuredArtistIds);
    } else if (featuredArtistIds) {
      allIds.push(featuredArtistIds);
    }
  }

  // Parse text if provided (e.g. "Sơn Tùng M-TP, B Ray" or "AMEE ft. MCK")
  if (artistName && typeof artistName === 'string' && artistName.trim()) {
    const ComposerService = require('../services/composerService');
    const names = ComposerService.splitAuthors(artistName);
    for (const name of names) {
      let a = await models.Artist.findOne({
        name: { $regex: new RegExp(`^${name.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`, 'i') }
      });
      if (!a) {
        a = await models.Artist.create({
          name: name.trim(),
          slug: `${slugify(name)}-${Math.floor(Math.random() * 899 + 100)}`,
          avatarUrl: coverUrl || '/uploads/playlists/default_music_cover.jpg',
          bio: 'Nghệ sĩ biểu diễn trên Vibe Music.',
          followersCount: 1000,
          isVerified: true
        });
      }
      if (!allIds.some(id => String(id) === String(a._id))) {
        allIds.push(a._id);
      }
    }
  }

  const uniqueIds = [];
  for (const id of allIds) {
    if (id && !uniqueIds.some(u => String(u) === String(id))) {
      uniqueIds.push(new mongoose.Types.ObjectId(id));
    }
  }

  if (uniqueIds.length === 0) {
    const defaultArtist = await models.Artist.findOne();
    if (defaultArtist) uniqueIds.push(defaultArtist._id);
  }

  return {
    artistId: uniqueIds[0],
    featuredArtistIds: uniqueIds.slice(1),
    artistIds: uniqueIds
  };
}

async function resolveComposersData({ composer, composerId, composerIds, genreIds }) {
  const mongoose = require('mongoose');
  const ComposerService = require('../services/composerService');
  let allIds = [];

  if (Array.isArray(composerIds) && composerIds.length > 0) {
    allIds = [...composerIds];
  } else if (composerId) {
    allIds.push(composerId);
  }

  if (composer && typeof composer === 'string' && composer.trim()) {
    const ids = await ComposerService.findOrCreateMultipleComposers(composer, genreIds);
    for (const id of ids) {
      if (!allIds.some(existing => String(existing) === String(id))) {
        allIds.push(id);
      }
    }
  }

  const uniqueIds = [];
  for (const id of allIds) {
    if (id && !uniqueIds.some(u => String(u) === String(id))) {
      uniqueIds.push(new mongoose.Types.ObjectId(id));
    }
  }

  return {
    composerId: uniqueIds[0] || null,
    composerIds: uniqueIds,
    composerText: composer ? composer.trim() : ''
  };
}

async function syncAlbumTotalTracks(albumIds) {
  const uniqueIds = [...new Set((albumIds || []).filter((id) => id && mongoose.Types.ObjectId.isValid(String(id))).map(String))];
  await Promise.all(
    uniqueIds.map(async (id) => {
      const count = await models.Song.countDocuments({ albumId: id });
      await models.Album.findByIdAndUpdate(id, { $set: { totalTracks: count } });
    })
  );
}

async function syncPlaylistTotalTracks(playlistIds) {
  const uniqueIds = [...new Set((playlistIds || []).filter((id) => id && mongoose.Types.ObjectId.isValid(String(id))).map(String))];
  await Promise.all(
    uniqueIds.map(async (id) => {
      const count = await models.PlaylistTrack.countDocuments({ playlistId: id });
      await models.Playlist.findByIdAndUpdate(id, { $set: { totalTracks: count } });
    })
  );
}

async function safelyDeleteLocalUploadIfUnused(url) {
  if (!url || typeof url !== 'string') return;
  const rel = url.replace(/^\/+/, '');
  if (!rel.startsWith('uploads/')) return;
  if (rel.includes('default_') || rel.includes('placeholder')) return;
  const full = path.join(__dirname, '../../public', rel);
  if (!fs.existsSync(full)) return;

  const urlPattern = new RegExp(rel.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  const [songRef, artistRef, albumRef, playlistRef] = await Promise.all([
    models.Song.exists({ $or: [{ audioUrl: urlPattern }, { coverImageUrl: urlPattern }] }),
    models.Artist.exists({ avatarUrl: urlPattern }),
    models.Album.exists({ coverImageUrl: urlPattern }),
    models.Playlist.exists({ coverImageUrl: urlPattern })
  ]);
  if (!songRef && !artistRef && !albumRef && !playlistRef) {
    try {
      fs.unlinkSync(full);
    } catch (_) {}
  }
}

function refIdStr(val) {
  if (!val) return null;
  return val._id ? String(val._id) : String(val);
}

function cleanStr(val, fallback = '') {
  return typeof val === 'string' ? val.trim() : fallback;
}

function buildArtistSongMap(allSongs) {
  const map = new Map();
  for (const s of allSongs) {
    const primaryId = refIdStr(s.artistId);
    const featuredIds = new Set(
      [...(s.featuredArtistIds || []), ...(s.artistIds || [])]
        .map(refIdStr)
        .filter((id) => id && id !== primaryId)
    );
    const composerIds = new Set(
      [s.composerId, ...(s.composerIds || [])]
        .map(refIdStr)
        .filter(Boolean)
    );
    const involvedIds = new Set(
      [primaryId, ...featuredIds, ...composerIds].filter(Boolean)
    );

    for (const artIdStr of involvedIds) {
      const isPrimary = artIdStr === primaryId;
      const isFeatured = featuredIds.has(artIdStr);
      const isComposer = composerIds.has(artIdStr);

      let roleLabel = 'Ca sĩ chính';
      if (isPrimary && isComposer) roleLabel = 'Hát chính & Sáng tác';
      else if (isPrimary) roleLabel = 'Ca sĩ chính';
      else if (isFeatured && isComposer) roleLabel = 'Feat & Sáng tác';
      else if (isFeatured) roleLabel = 'Ca sĩ hợp tác (Feat)';
      else if (isComposer) roleLabel = 'Nhạc sĩ sáng tác';

      if (!map.has(artIdStr)) map.set(artIdStr, []);
      map.get(artIdStr).push({ ...s, roleLabel });
    }
  }
  return map;
}

/**
 * Chuẩn hóa danh sách thống kê gom nhóm theo người dùng (dùng chung cho histories & favorites)
 */
async function buildUserSummaryList(aggRows, mapper) {
  const validIds = aggRows
    .map((r) => r._id)
    .filter((id) => id && mongoose.Types.ObjectId.isValid(String(id)));

  const users = validIds.length
    ? await models.User.find({ _id: { $in: validIds } }).select('-passwordHash').lean()
    : [];
  const userMap = new Map(users.map((u) => [String(u._id), u]));

  return aggRows.map((row) => {
    const idStr = row._id ? String(row._id) : 'unknown';
    const user = userMap.get(idStr) || {
      _id: row._id || null,
      username: 'unknown',
      displayName: 'Người dùng đã xóa / Ẩn danh',
      email: '',
      avatarUrl: '',
      role: 'user',
      isActive: false
    };
    const displayName = user.displayName || user.username || user.email || 'Ẩn danh';
    return mapper(row, {
      ...user,
      displayName
    }, idStr);
  });
}

function toStatsMap(aggRows) {
  return new Map((aggRows || []).filter((r) => r._id).map((r) => [String(r._id), r]));
}

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
        favoritesCount,
        aiLogsCount,
        notificationsCount
      ] = await Promise.all([
        models.Song.countDocuments(),
        models.Artist.countDocuments(),
        models.Album.countDocuments(),
        models.Genre.countDocuments(),
        models.Playlist.countDocuments(),
        models.User.countDocuments(),
        models.ListeningHistory.countDocuments(),
        models.UserFavorite.countDocuments(),
        models.AiPromptLog.countDocuments(),
        models.Notification.countDocuments()
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
          favorites: favoritesCount,
          aiLogs: aiLogsCount,
          notifications: notificationsCount
        }
      }, 'Thống kê cơ sở dữ liệu thành công');
    } catch (error) {
      next(error);
    }
  }

  async getCollectionData(req, res, next) {
    try {
      const { collection } = req.params;
      const limit = parseInt(req.query.limit) || 500;
      let data = [];

      switch (collection.toLowerCase()) {
        case 'songs': {
          const rawSongs = await models.Song.find()
            .populate('artistId', 'name avatarUrl isVerified')
            .populate('composerId', 'name avatarUrl isVerified')
            .populate('composerIds', 'name avatarUrl isVerified')
            .populate('featuredArtistIds', 'name avatarUrl isVerified')
            .populate('artistIds', 'name avatarUrl isVerified')
            .populate('albumId', 'title coverImageUrl')
            .populate('genreIds', 'name colorCode')
            .sort({ createdAt: -1 })
            .limit(limit)
            .lean();

          data = rawSongs.map(s => require('../utils/songFormatter').formatSongArtistFields(s));
          break;
        }
        case 'artists': {
          const [rawArtists, allSongs] = await Promise.all([
            models.Artist.find()
              .populate('genreIds', 'name colorCode')
              .sort({ followersCount: -1 })
              .limit(limit)
              .lean(),
            models.Song.find()
              .select('title artistId featuredArtistIds artistIds composerId composerIds composer audioUrl coverImageUrl duration genreIds isPublic')
              .populate('genreIds', 'name')
              .populate('artistId', 'name')
              .populate('featuredArtistIds', 'name')
              .populate('composerIds', 'name')
              .lean()
          ]);

          const artistSongMap = buildArtistSongMap(allSongs);
          data = rawArtists.map((art) => {
            const artistSongs = artistSongMap.get(String(art._id)) || [];
            return {
              ...art,
              songsCount: artistSongs.length,
              songs: artistSongs
            };
          });
          break;
        }
        case 'albums': {
          const rawAlbums = await models.Album.find()
            .populate('artistId', 'name avatarUrl')
            .populate('genreIds', 'name colorCode')
            .sort({ createdAt: -1 })
            .limit(limit)
            .lean();
          data = await AlbumService.attachLiveAlbumTrackCounts(rawAlbums, { includePrivate: true });
          break;
        }
        case 'genres':
          data = await models.Genre.find().limit(limit).lean();
          break;
        case 'playlists': {
          const rawPlaylists = await models.Playlist.find()
            .populate('creatorId', 'displayName username email avatarUrl')
            .sort({ createdAt: -1 })
            .limit(limit)
            .lean();
          data = await PlaylistService.attachLivePlaylistTrackCounts(rawPlaylists);
          break;
        }
        case 'users': {
          const [rawUsers, historyCounts, favoriteCounts, playlistCounts, aiLogCounts] = await Promise.all([
            models.User.find().select('-passwordHash').sort({ createdAt: -1 }).limit(limit).lean(),
            models.ListeningHistory.aggregate([
              { $group: { _id: '$userId', count: { $sum: 1 }, lastAt: { $max: '$timestamp' } } }
            ]),
            models.UserFavorite.aggregate([
              { $group: { _id: '$userId', count: { $sum: 1 }, lastAt: { $max: '$createdAt' } } }
            ]),
            models.Playlist.aggregate([
              { $group: { _id: '$creatorId', count: { $sum: 1 } } }
            ]),
            models.AiPromptLog.aggregate([
              { $group: { _id: '$userId', count: { $sum: 1 } } }
            ])
          ]);

          const hMap = toStatsMap(historyCounts);
          const fMap = toStatsMap(favoriteCounts);
          const pMap = toStatsMap(playlistCounts);
          const aMap = toStatsMap(aiLogCounts);

          data = rawUsers.map((u) => {
            const key = String(u._id);
            const h = hMap.get(key);
            const f = fMap.get(key);
            const p = pMap.get(key);
            const a = aMap.get(key);
            return {
              ...u,
              historiesCount: h ? h.count : 0,
              lastPlayedAt: h ? h.lastAt : null,
              favoritesCount: f ? f.count : 0,
              lastFavoritedAt: f ? f.lastAt : null,
              playlistsCount: p ? p.count : 0,
              aiLogsCount: a ? a.count : 0
            };
          });
          break;
        }
        case 'histories':
        case 'listeninghistories': {
          const agg = await models.ListeningHistory.aggregate([
            {
              $group: {
                _id: '$userId',
                totalPlays: { $sum: 1 },
                totalDurationSec: { $sum: { $ifNull: ['$listenDuration', 0] } },
                completedPlays: { $sum: { $cond: ['$completed', 1, 0] } },
                skippedPlays: { $sum: { $cond: ['$skipped', 1, 0] } },
                songIds: { $addToSet: '$songId' },
                lastPlayedAt: { $max: '$timestamp' }
              }
            },
            { $sort: { lastPlayedAt: -1 } },
            { $limit: limit }
          ]);

          data = await buildUserSummaryList(agg, (row, user, idStr) => ({
            _id: idStr,
            user,
            displayName: user.displayName,
            username: user.username || '',
            email: user.email || '',
            avatarUrl: user.avatarUrl || '',
            totalPlays: row.totalPlays || 0,
            uniqueSongsCount: Array.isArray(row.songIds) ? row.songIds.length : 0,
            totalDurationSec: row.totalDurationSec || 0,
            completedPlays: row.completedPlays || 0,
            skippedPlays: row.skippedPlays || 0,
            lastPlayedAt: row.lastPlayedAt
          }));
          break;
        }
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
        case 'userfavorites': {
          const agg = await models.UserFavorite.aggregate([
            {
              $group: {
                _id: '$userId',
                totalFavorites: { $sum: 1 },
                lastFavoritedAt: { $max: '$createdAt' }
              }
            },
            { $sort: { lastFavoritedAt: -1 } },
            { $limit: limit }
          ]);

          data = await buildUserSummaryList(agg, (row, user, idStr) => ({
            _id: idStr,
            user,
            displayName: user.displayName,
            username: user.username || '',
            email: user.email || '',
            avatarUrl: user.avatarUrl || '',
            totalFavorites: row.totalFavorites || 0,
            lastFavoritedAt: row.lastFavoritedAt
          }));
          break;
        }
        case 'notifications': {
          data = await models.Notification.find()
            .populate('recipientId', 'displayName username email avatarUrl role')
            .populate('senderId', 'displayName username email avatarUrl role')
            .sort({ createdAt: -1 })
            .limit(limit)
            .lean();
          break;
        }
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

  async getFormOptions(req, res, next) {
    try {
      const [artists, genres, albums, users, songs] = await Promise.all([
        models.Artist.find().select('name avatarUrl isVerified').sort({ name: 1 }).lean(),
        models.Genre.find().select('name slug colorCode').sort({ name: 1 }).lean(),
        models.Album.find().select('title coverImageUrl artistId').sort({ title: 1 }).lean(),
        models.User.find().select('username displayName email avatarUrl role').sort({ displayName: 1 }).lean(),
        models.Song.find({ isPublic: true })
          .select('title artistId coverImageUrl duration audioUrl')
          .populate('artistId', 'name')
          .sort({ title: 1 })
          .lean()
      ]);
      return ApiResponse.success(res, { artists, genres, albums, users, songs });
    } catch (error) {
      next(error);
    }
  }

  // ==========================================
  // TẢI TỆP TRỰC TIẾP (DIRECT FILE UPLOAD)
  // ==========================================
  async uploadFile(req, res, next) {
    try {
      const { fileName, base64Data, target, type } = req.body;
      if (!fileName || !base64Data) {
        return res.status(400).json({ success: false, message: 'Vui lòng cung cấp tên tệp và dữ liệu base64!' });
      }

      const normTarget = target ? String(target).trim().toLowerCase() : '';
      const normType = type ? String(type).trim().toLowerCase() : '';
      if (normTarget && normType && normTarget !== normType) {
        return res.status(400).json({
          success: false,
          message: `Tham số target (${normTarget}) và type (${normType}) không khớp nhau!`
        });
      }
      const effectiveTarget = normTarget || normType || '';

      const path = require('path');
      const saved = saveValidatedBase64Upload({
        fileName,
        base64Data,
        target: effectiveTarget,
        uploadsBaseDir: path.join(__dirname, '../../public/uploads')
      });

      let duration = null;
      let metadata = {};

      if (saved.category === 'audio') {
        const audioInfo = await extractAudioInfo(saved.filePath);
        duration = audioInfo.duration;
        metadata = audioInfo.metadata;
      }

      return ApiResponse.success(res, {
        url: saved.url,
        fileName: saved.fileName,
        sizeBytes: saved.sizeBytes,
        duration,
        metadata
      }, `Tải tệp ${saved.fileName} lên máy chủ thành công!`);
    } catch (error) {
      next(error);
    }
  }

  // ==========================================
  // ĐỒNG BỘ TOÀN BỘ HỆ THỐNG (SYSTEM SYNC)
  // ==========================================
  async syncSystem(req, res, next) {
    try {
      const fs = require('fs');
      const path = require('path');

      // 1. Đồng bộ số bài hát cho tất cả Album
      const allAlbums = await models.Album.find().select('_id title').lean();
      let albumsSynced = 0;
      await Promise.all(
        allAlbums.map(async (alb) => {
          const count = await models.Song.countDocuments({ albumId: alb._id });
          await models.Album.findByIdAndUpdate(alb._id, { $set: { totalTracks: count } });
          albumsSynced++;
        })
      );

      // 2. Dọn rác PlaylistTrack mồ côi (bài hát hoặc playlist không tồn tại)
      const allSongIds = await models.Song.distinct('_id');
      const allPlaylistIds = await models.Playlist.distinct('_id');
      const orphanedTracksResult = await models.PlaylistTrack.deleteMany({
        $or: [
          { songId: { $nin: allSongIds } },
          { playlistId: { $nin: allPlaylistIds } }
        ]
      });

      // 3. Đồng bộ số bài hát cho tất cả Playlist
      let playlistsSynced = 0;
      await Promise.all(
        allPlaylistIds.map(async (plId) => {
          const count = await models.PlaylistTrack.countDocuments({ playlistId: plId });
          await models.Playlist.findByIdAndUpdate(plId, { $set: { totalTracks: count } });
          playlistsSynced++;
        })
      );

      // 4. Đồng bộ likesCount cho Song từ UserFavorite
      const favoriteCounts = await models.UserFavorite.aggregate([
        { $group: { _id: '$songId', count: { $sum: 1 } } }
      ]);
      const favMap = new Map(favoriteCounts.map(f => [String(f._id), f.count]));
      let songsLikesSynced = 0;
      await Promise.all(
        allSongIds.map(async (songId) => {
          const likes = favMap.get(String(songId)) || 0;
          await models.Song.findByIdAndUpdate(songId, { $set: { likesCount: likes } });
          songsLikesSynced++;
        })
      );

      // 5. Đồng bộ playsCount cho Song từ ListeningHistory
      const historyCounts = await models.ListeningHistory.aggregate([
        { $group: { _id: '$songId', count: { $sum: 1 } } }
      ]);
      const histMap = new Map(historyCounts.map(h => [String(h._id), h.count]));
      let songsPlaysSynced = 0;
      await Promise.all(
        allSongIds.map(async (songId) => {
          const plays = histMap.get(String(songId)) || 0;
          await models.Song.findByIdAndUpdate(songId, { $set: { playsCount: plays } });
          songsPlaysSynced++;
        })
      );

      // 5b. Đồng bộ thời lượng (duration) bài hát theo file âm thanh thực tế trên máy chủ
      const recheckDurations = req.query.recheckDurations === 'true' || req.body?.recheckDurations === true;
      const allSongsForDuration = await models.Song.find().select('_id title duration audioUrl').lean();
      let durationsSynced = 0;
      const missingAudioFiles = [];
      for (const s of allSongsForDuration) {
        if (!s.audioUrl || !s.audioUrl.startsWith('/uploads/audios/')) continue;
        const localPath = resolveLocalAudioPath(s.audioUrl);
        if (!localPath) {
          missingAudioFiles.push({ id: String(s._id), title: s.title, audioUrl: s.audioUrl });
          continue;
        }
        if (recheckDurations || !s.duration || s.duration <= 0) {
          const realDuration = await readAudioDurationSeconds(s.audioUrl, null);
          if (realDuration && realDuration > 0 && realDuration !== s.duration) {
            await models.Song.findByIdAndUpdate(s._id, { $set: { duration: realDuration } });
            durationsSynced++;
          }
        }
      }

      // 6. Kiểm tra dung lượng thư mục uploads
      const audiosDir = path.join(__dirname, '../../public/uploads/audios');
      const coversDir = path.join(__dirname, '../../public/uploads/covers');
      const audioFiles = fs.existsSync(audiosDir) ? fs.readdirSync(audiosDir) : [];
      const coverFiles = fs.existsSync(coversDir) ? fs.readdirSync(coversDir) : [];

      const report = {
        albumsSynced,
        playlistsSynced,
        orphanedTracksCleaned: orphanedTracksResult.deletedCount || 0,
        songsLikesSynced,
        songsPlaysSynced,
        durationsSynced,
        missingAudioFilesCount: missingAudioFiles.length,
        missingAudioFiles: missingAudioFiles.slice(0, 20),
        totalSongsInDb: allSongIds.length,
        storage: {
          audioFilesCount: audioFiles.length,
          coverFilesCount: coverFiles.length
        },
        timestamp: new Date().toISOString()
      };

      return ApiResponse.success(res, report, 'Đồng bộ toàn bộ hệ thống cơ sở dữ liệu và liên kết tệp tin thành công!');
    } catch (error) {
      next(error);
    }
  }

  // ==========================================
  // CRUD BÀI HÁT (SONGS)
  // ==========================================
  async addSong(req, res, next) {
    try {
      const {
        title,
        artistName,
        artistId,
        featuredArtistIds,
        artistIds,
        composer,
        composerId,
        composerIds,
        albumId,
        genreIds,
        genreSlug,
        audioFileName,
        audioUrl,
        coverFileName,
        coverImageUrl,
        lyrics,
        duration,
        mood,
        tempo,
        isPublic
      } = req.body;

      if (!title || (!audioFileName && !audioUrl)) {
        return res.status(400).json({ success: false, message: 'Vui lòng nhập tên bài hát và chọn file nhạc hoặc URL!' });
      }

      // 1. Thể loại
      let finalGenreIds = [];
      if (genreIds && Array.isArray(genreIds) && genreIds.length > 0) {
        finalGenreIds = genreIds;
      } else if (genreSlug) {
        const g = await models.Genre.findOne({ slug: genreSlug });
        if (g) finalGenreIds = [g._id];
      }
      if (finalGenreIds.length === 0) {
        const defaultG = await models.Genre.findOne();
        if (defaultG) finalGenreIds = [defaultG._id];
      }

      // 2. Audio URL
      let finalAudioUrl = audioUrl ? sanitizeMediaUrl(audioUrl, { allowEmpty: false }) : '';
      if (!finalAudioUrl && audioFileName) {
        let safeAudioName = path.basename(String(audioFileName));
        if (!String(audioFileName).startsWith('http') && (safeAudioName.includes('#') || safeAudioName.includes('[') || safeAudioName.includes(' '))) {
          const oldPath = path.join(__dirname, '../../public/uploads/audios', safeAudioName);
          const cleanName = safeAudioName.replace(/[#\[\]()]/g, '').trim().replace(/\s+/g, '_');
          const newPath = path.join(__dirname, '../../public/uploads/audios', cleanName);
          if (fs.existsSync(oldPath)) {
            fs.renameSync(oldPath, newPath);
            safeAudioName = cleanName;
          }
        }
        finalAudioUrl = String(audioFileName).startsWith('http')
          ? sanitizeMediaUrl(audioFileName, { allowEmpty: false })
          : `/uploads/audios/${safeAudioName}`;
      }

      // 3. Cover URL
      let finalCoverUrl = coverImageUrl ? sanitizeMediaUrl(coverImageUrl) : '';
      if (!finalCoverUrl) {
        finalCoverUrl = coverFileName
          ? (String(coverFileName).startsWith('http') ? sanitizeMediaUrl(coverFileName) : `/uploads/covers/${path.basename(String(coverFileName))}`)
          : '/uploads/playlists/default_music_cover.jpg';
      }

      // 4. Nghệ sĩ biểu diễn (hỗ trợ nhiều nghệ sĩ)
      const { artistId: finalArtistId, featuredArtistIds: finalFeaturedArtistIds, artistIds: finalArtistIds } =
        await resolveArtistsData({ artistId, featuredArtistIds, artistIds, artistName, coverUrl: finalCoverUrl });

      // 5. Nhạc sĩ sáng tác (hỗ trợ nhiều nhạc sĩ)
      const { composerId: finalComposerId, composerIds: finalComposerIds, composerText } =
        await resolveComposersData({ composer, composerId, composerIds, genreIds: finalGenreIds });

      // 6. Đồng bộ duration chuẩn từ file âm thanh trên máy chủ (fallback về input nếu là URL ngoài)
      const parsedDuration = parseInt(duration, 10);
      const serverDuration = await readAudioDurationSeconds(finalAudioUrl, null);
      const finalDuration = serverDuration || (Number.isFinite(parsedDuration) && parsedDuration > 0 ? parsedDuration : 210);

      // 7. Tạo bài hát mới
      const normalizedMood = mood ? (Array.isArray(mood) ? mood : [mood]) : ['chill'];
      const newSong = await models.Song.create({
        title: title.trim(),
        artistId: finalArtistId,
        featuredArtistIds: finalFeaturedArtistIds,
        artistIds: finalArtistIds,
        albumId: albumId || null,
        genreIds: finalGenreIds,
        composer: composerText,
        composerId: finalComposerId,
        composerIds: finalComposerIds,
        duration: finalDuration,
        audioUrl: finalAudioUrl,
        coverImageUrl: finalCoverUrl,
        lyrics: lyrics || '',
        mood: normalizedMood,
        moods: normalizedMood,
        tempo: tempo || 'medium',
        isPublic: isPublic !== undefined ? Boolean(isPublic) : true
      });

      await syncSongAiMetadata(newSong._id, { regenerateEmbedding: true });

      if (newSong.albumId) {
        await syncAlbumTotalTracks([newSong.albumId]);
      }

      const populatedSong = await models.Song.findById(newSong._id)
        .select('-embedding')
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('featuredArtistIds', 'name avatarUrl isVerified')
        .populate('artistIds', 'name avatarUrl isVerified')
        .populate('composerId', 'name avatarUrl isVerified')
        .populate('composerIds', 'name avatarUrl isVerified')
        .populate('albumId', 'title coverImageUrl')
        .populate('genreIds', 'name colorCode')
        .lean();

      if (populatedSong) {
        let compArtistName = populatedSong.artistId?.name || '';
        if (populatedSong.featuredArtistIds && populatedSong.featuredArtistIds.length > 0) {
          const featNames = populatedSong.featuredArtistIds.map(f => (f && typeof f === 'object' && typeof f.name === 'string' ? f.name.trim() : '')).filter(Boolean);
          if (featNames.length > 0) compArtistName = `${compArtistName} ft. ${featNames.join(', ')}`;
        }
        populatedSong.artistName = compArtistName;
      }

      return ApiResponse.success(res, populatedSong, 'Thêm bài hát mới thành công!');
    } catch (error) {
      next(error);
    }
  }

  async updateSong(req, res, next) {
    try {
      const { id } = req.params;
      const {
        title,
        artistId,
        featuredArtistIds,
        artistIds,
        artistName,
        composer,
        composerId,
        composerIds,
        albumId,
        genreIds,
        genreSlug,
        audioFileName,
        audioUrl,
        coverFileName,
        coverImageUrl,
        lyrics,
        duration,
        mood,
        tempo,
        isPublic
      } = req.body;

      const song = await models.Song.findById(id);
      if (!song) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy bài hát' });
      }
      const previousAlbumId = song.albumId ? song.albumId.toString() : null;

      if (title) song.title = title.trim();

      // Cập nhật nghệ sĩ biểu diễn (Đa nghệ sĩ)
      if (artistId || featuredArtistIds !== undefined || artistIds !== undefined || artistName) {
        const { artistId: updatedArtistId, featuredArtistIds: updatedFeatured, artistIds: updatedArtistIds } =
          await resolveArtistsData({
            artistId: artistId || song.artistId,
            featuredArtistIds: featuredArtistIds !== undefined ? featuredArtistIds : song.featuredArtistIds,
            artistIds: artistIds !== undefined ? artistIds : song.artistIds,
            artistName,
            coverUrl: coverImageUrl || song.coverImageUrl
          });
        song.artistId = updatedArtistId;
        song.featuredArtistIds = updatedFeatured;
        song.artistIds = updatedArtistIds;
      }

      // Cập nhật nhạc sĩ sáng tác (Đa nhạc sĩ)
      if (composer !== undefined || composerIds !== undefined || composerId) {
        const { composerId: updatedComposerId, composerIds: updatedComposerIds, composerText } =
          await resolveComposersData({
            composer: composer !== undefined ? composer : song.composer,
            composerId: composerId || song.composerId,
            composerIds: composerIds !== undefined ? composerIds : song.composerIds,
            genreIds: song.genreIds
          });
        song.composer = composerText;
        song.composerId = updatedComposerId;
        song.composerIds = updatedComposerIds;
      }

      if (albumId !== undefined) song.albumId = albumId || null;

      if (genreIds && Array.isArray(genreIds) && genreIds.length > 0) {
        song.genreIds = genreIds;
      } else if (genreSlug) {
        const g = await models.Genre.findOne({ slug: genreSlug });
        if (g) song.genreIds = [g._id];
      }

      if (audioUrl) {
        song.audioUrl = sanitizeMediaUrl(audioUrl, { allowEmpty: false });
      } else if (audioFileName) {
        song.audioUrl = String(audioFileName).startsWith('http')
          ? sanitizeMediaUrl(audioFileName, { allowEmpty: false })
          : `/uploads/audios/${path.basename(String(audioFileName))}`;
      }

      const serverDuration = await readAudioDurationSeconds(song.audioUrl, null);
      if (serverDuration) {
        song.duration = serverDuration;
      } else if (duration !== undefined && duration !== null) {
        const durNum = parseInt(duration, 10);
        if (!isNaN(durNum) && durNum > 0) song.duration = durNum;
      }

      if (coverImageUrl !== undefined) {
        song.coverImageUrl = sanitizeMediaUrl(coverImageUrl) || '/uploads/playlists/default_music_cover.jpg';
      } else if (coverFileName) {
        song.coverImageUrl = String(coverFileName).startsWith('http')
          ? sanitizeMediaUrl(coverFileName)
          : `/uploads/covers/${path.basename(String(coverFileName))}`;
      }

      if (lyrics !== undefined) song.lyrics = lyrics || '';
      if (mood) {
        const normalizedMood = Array.isArray(mood) ? mood : [mood];
        song.mood = normalizedMood;
        song.moods = normalizedMood;
      }
      if (tempo) song.tempo = tempo;
      if (isPublic !== undefined) song.isPublic = Boolean(isPublic);

      await song.save();
      const semanticChanged = Boolean(title || mood || lyrics !== undefined || genreIds || genreSlug || artistId || artistName);
      await syncSongAiMetadata(song._id, { regenerateEmbedding: semanticChanged });
      await syncAlbumTotalTracks([previousAlbumId, song.albumId]);

      const populated = await models.Song.findById(song._id)
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('featuredArtistIds', 'name avatarUrl isVerified')
        .populate('artistIds', 'name avatarUrl isVerified')
        .populate('composerId', 'name avatarUrl isVerified')
        .populate('composerIds', 'name avatarUrl isVerified')
        .populate('albumId', 'title coverImageUrl')
        .populate('genreIds', 'name colorCode')
        .lean();

      const formattedPopulated = populated ? require('../utils/songFormatter').formatSongArtistFields(populated) : populated;

      return ApiResponse.success(res, formattedPopulated, 'Cập nhật bài hát thành công!');
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
      await syncSongAiMetadata(song._id, { regenerateEmbedding: true });
      return ApiResponse.success(res, song, 'Đã cập nhật lời bài hát thành công!');
    } catch (error) {
      next(error);
    }
  }

  async deleteSong(req, res, next) {
    try {
      const { id } = req.params;
      const song = await models.Song.findById(id).lean();
      if (!song) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy bài hát!' });
      }

      const affectedPlaylistIds = await models.PlaylistTrack.distinct('playlistId', { songId: id });

      await Promise.all([
        models.Song.findByIdAndDelete(id),
        models.PlaylistTrack.deleteMany({ songId: id }),
        models.UserFavorite.deleteMany({ songId: id }),
        models.ListeningHistory.deleteMany({ songId: id }),
        models.ListeningEvent.deleteMany({ songId: id }),
        models.UserInteraction.deleteMany({ songId: id }),
        models.PlaySession.updateMany(
          { $or: [{ recentTrackIds: id }, { dislikedTrackIds: id }] },
          { $pull: { recentTrackIds: id, dislikedTrackIds: id } }
        )
      ]);

      await Promise.all([
        syncAlbumTotalTracks([song.albumId]),
        syncPlaylistTotalTracks(affectedPlaylistIds)
      ]);

      if (song.audioUrl) await safelyDeleteLocalUploadIfUnused(song.audioUrl);
      if (song.coverImageUrl) await safelyDeleteLocalUploadIfUnused(song.coverImageUrl);

      return ApiResponse.success(res, null, 'Đã xóa bài hát và đồng bộ liên kết thành công!');
    } catch (error) {
      next(error);
    }
  }

  // ==========================================
  // CRUD NGHỆ SĨ (ARTISTS)
  // ==========================================
  async createArtist(req, res, next) {
    try {
      const { name, slug, avatarUrl, bio, followersCount, isVerified, genreIds } = req.body;
      if (!name || !name.trim()) {
        return res.status(400).json({ success: false, message: 'Vui lòng nhập tên nghệ sĩ!' });
      }

      let finalSlug = (slug && slug.trim()) ? slug.trim().toLowerCase() : slugify(name);
      if (!finalSlug) finalSlug = 'art-' + Date.now();

      const existing = await models.Artist.findOne({ slug: finalSlug });
      if (existing) {
        finalSlug = `${finalSlug}-${Date.now().toString().slice(-4)}`;
      }

      const artist = await models.Artist.create({
        name: name.trim(),
        slug: finalSlug,
        avatarUrl: sanitizeMediaUrl(avatarUrl) || '/uploads/playlists/default_music_cover.jpg',
        bio: bio ? bio.trim() : '',
        followersCount: parseInt(followersCount) || 0,
        isVerified: Boolean(isVerified),
        genreIds: Array.isArray(genreIds) ? genreIds : []
      });

      return ApiResponse.success(res, artist, 'Thêm nghệ sĩ mới thành công!');
    } catch (error) {
      next(error);
    }
  }

  async updateArtist(req, res, next) {
    try {
      const { id } = req.params;
      const { name, slug, avatarUrl, bio, followersCount, isVerified, genreIds } = req.body;

      const artist = await models.Artist.findById(id);
      if (!artist) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy nghệ sĩ!' });
      }

      if (typeof name === 'string' && name.trim()) artist.name = name.trim();
      if (typeof slug === 'string' && slug.trim()) artist.slug = slug.trim().toLowerCase();
      if (avatarUrl !== undefined) artist.avatarUrl = cleanStr(sanitizeMediaUrl(avatarUrl));
      if (bio !== undefined) artist.bio = cleanStr(bio);
      if (followersCount !== undefined) artist.followersCount = parseInt(followersCount) || 0;
      if (isVerified !== undefined) artist.isVerified = Boolean(isVerified);
      if (genreIds !== undefined) artist.genreIds = Array.isArray(genreIds) ? genreIds : [];

      await artist.save();
      return ApiResponse.success(res, artist, 'Cập nhật thông tin nghệ sĩ thành công!');
    } catch (error) {
      next(error);
    }
  }

  async deleteArtist(req, res, next) {
    try {
      const { id } = req.params;
      const [songsCount, albumsCount] = await Promise.all([
        models.Song.countDocuments({
          $or: [
            { artistId: id },
            { featuredArtistIds: id },
            { artistIds: id },
            { composerId: id },
            { composerIds: id }
          ]
        }),
        models.Album.countDocuments({ artistId: id })
      ]);
      if ((songsCount > 0 || albumsCount > 0) && req.query.force !== 'true') {
        return res.status(400).json({
          success: false,
          message: `Nghệ sĩ này đang liên kết ${songsCount} bài hát và ${albumsCount} album. Vui lòng gỡ liên kết trước khi xóa!`
        });
      }

      const deleted = await models.Artist.findByIdAndDelete(id);
      if (!deleted) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy nghệ sĩ!' });
      }
      await Promise.all([
        models.UserPreference.updateMany(
          { favoriteArtistIds: id },
          { $pull: { favoriteArtistIds: id } }
        ),
        models.Song.updateMany(
          { $or: [{ featuredArtistIds: id }, { artistIds: id }, { composerIds: id }] },
          { $pull: { featuredArtistIds: id, artistIds: id, composerIds: id } }
        ),
        models.Song.updateMany(
          { composerId: id },
          { $set: { composerId: null } }
        ),
        models.Album.updateMany(
          { artistId: id },
          { $set: { artistId: null } }
        )
      ]);
      return ApiResponse.success(res, null, 'Đã xóa nghệ sĩ thành công!');
    } catch (error) {
      next(error);
    }
  }

  // ==========================================
  // CRUD ALBUM
  // ==========================================
  async createAlbum(req, res, next) {
    try {
      const { title, artistId, coverImageUrl, releaseYear, genreIds, totalTracks } = req.body;
      if (!title || !title.trim()) {
        return res.status(400).json({ success: false, message: 'Vui lòng nhập tên Album!' });
      }

      let finalArtistId = artistId;
      if (!finalArtistId) {
        const firstArtist = await models.Artist.findOne();
        if (firstArtist) finalArtistId = firstArtist._id;
      }

      const album = await models.Album.create({
        title: title.trim(),
        artistId: finalArtistId,
        coverImageUrl: sanitizeMediaUrl(coverImageUrl) || '/uploads/playlists/default_music_cover.jpg',
        releaseYear: parseInt(releaseYear) || new Date().getFullYear(),
        genreIds: Array.isArray(genreIds) ? genreIds : [],
        totalTracks: parseInt(totalTracks) || 0
      });

      const populated = await models.Album.findById(album._id)
        .populate('artistId', 'name avatarUrl')
        .populate('genreIds', 'name colorCode')
        .lean();

      return ApiResponse.success(res, populated, 'Thêm Album mới thành công!');
    } catch (error) {
      next(error);
    }
  }

  async updateAlbum(req, res, next) {
    try {
      const { id } = req.params;
      const { title, artistId, coverImageUrl, releaseYear, genreIds } = req.body;

      const album = await models.Album.findById(id);
      if (!album) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy Album!' });
      }

      if (typeof title === 'string' && title.trim()) album.title = title.trim();
      if (artistId) album.artistId = artistId;
      if (coverImageUrl !== undefined) album.coverImageUrl = cleanStr(sanitizeMediaUrl(coverImageUrl));
      if (releaseYear) album.releaseYear = parseInt(releaseYear) || album.releaseYear;
      if (genreIds !== undefined) album.genreIds = Array.isArray(genreIds) ? genreIds : [];

      await album.save();
      await syncAlbumTotalTracks([album._id]);

      const populated = await models.Album.findById(album._id)
        .populate('artistId', 'name avatarUrl')
        .populate('genreIds', 'name colorCode')
        .lean();

      return ApiResponse.success(res, populated, 'Cập nhật Album thành công!');
    } catch (error) {
      next(error);
    }
  }

  async deleteAlbum(req, res, next) {
    try {
      const { id } = req.params;
      const deleted = await models.Album.findByIdAndDelete(id);
      if (!deleted) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy Album!' });
      }
      await Promise.all([
        models.Song.updateMany({ albumId: id }, { $set: { albumId: null } }),
        models.UserPreference.updateMany({ favoriteAlbumIds: id }, { $pull: { favoriteAlbumIds: id } })
      ]);
      return ApiResponse.success(res, null, 'Đã xóa Album thành công!');
    } catch (error) {
      next(error);
    }
  }

  async getAlbumTracks(req, res, next) {
    try {
      const { id } = req.params;
      const album = await models.Album.findById(id).populate('artistId', 'name avatarUrl').lean();
      if (!album) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy Album!' });
      }

      const songs = await models.Song.find({ albumId: id })
        .select('-embedding')
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('featuredArtistIds', 'name avatarUrl isVerified')
        .populate('artistIds', 'name avatarUrl isVerified')
        .populate('composerId', 'name avatarUrl isVerified')
        .populate('composerIds', 'name avatarUrl isVerified')
        .populate('genreIds', 'name colorCode')
        .sort({ playsCount: -1, createdAt: -1 })
        .lean();

      return ApiResponse.success(res, {
        album,
        tracks: songs,
        totalTracks: songs.length
      }, 'Lấy danh sách bài hát trong Album thành công!');
    } catch (error) {
      next(error);
    }
  }

  async addSongToAlbum(req, res, next) {
    try {
      const { id } = req.params;
      const { songId } = req.body;
      if (!songId) {
        return res.status(400).json({ success: false, message: 'Vui lòng chọn bài hát cần thêm!' });
      }

      const album = await models.Album.findById(id);
      if (!album) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy Album!' });
      }

      const song = await models.Song.findById(songId);
      if (!song) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy bài hát!' });
      }

      const previousAlbumId = song.albumId ? song.albumId.toString() : null;
      song.albumId = album._id;
      await song.save();
      await syncAlbumTotalTracks([previousAlbumId, album._id]);

      const count = await models.Song.countDocuments({ albumId: id });
      album.totalTracks = count;

      return ApiResponse.success(res, { album, song, totalTracks: count }, 'Đã thêm bài hát vào Album thành công!');
    } catch (error) {
      next(error);
    }
  }

  async removeSongFromAlbum(req, res, next) {
    try {
      const { id, songId } = req.params;
      const album = await models.Album.findById(id);
      if (!album) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy Album!' });
      }

      await models.Song.findByIdAndUpdate(songId, { $set: { albumId: null } });

      const count = await models.Song.countDocuments({ albumId: id });
      album.totalTracks = count;
      await album.save();

      return ApiResponse.success(res, { totalTracks: count }, 'Đã gỡ bài hát khỏi Album thành công!');
    } catch (error) {
      next(error);
    }
  }

  // ==========================================
  // CRUD THỂ LOẠI (GENRES)
  // ==========================================
  async createGenre(req, res, next) {
    try {
      const { name, slug, colorCode, iconUrl, description } = req.body;
      if (!name || !name.trim()) {
        return res.status(400).json({ success: false, message: 'Vui lòng nhập tên thể loại!' });
      }

      let finalSlug = (slug && slug.trim()) ? slug.trim().toLowerCase() : slugify(name);
      const existing = await models.Genre.findOne({ slug: finalSlug });
      if (existing) {
        return res.status(400).json({ success: false, message: 'Slug thể loại đã tồn tại!' });
      }

      const genre = await models.Genre.create({
        name: name.trim(),
        slug: finalSlug,
        colorCode: colorCode || '#1DB954',
        iconUrl: sanitizeMediaUrl(iconUrl),
        description: description ? description.trim() : ''
      });

      return ApiResponse.success(res, genre, 'Thêm thể loại mới thành công!');
    } catch (error) {
      next(error);
    }
  }

  async updateGenre(req, res, next) {
    try {
      const { id } = req.params;
      const { name, slug, colorCode, iconUrl, description } = req.body;

      const genre = await models.Genre.findById(id);
      if (!genre) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy thể loại!' });
      }

      if (typeof name === 'string' && name.trim()) genre.name = name.trim();
      if (typeof slug === 'string' && slug.trim()) genre.slug = slug.trim().toLowerCase();
      if (typeof colorCode === 'string' && colorCode.trim()) genre.colorCode = colorCode.trim();
      if (iconUrl !== undefined) genre.iconUrl = cleanStr(sanitizeMediaUrl(iconUrl));
      if (description !== undefined) genre.description = cleanStr(description);

      await genre.save();
      return ApiResponse.success(res, genre, 'Cập nhật thể loại thành công!');
    } catch (error) {
      next(error);
    }
  }

  async deleteGenre(req, res, next) {
    try {
      const { id } = req.params;
      const deleted = await models.Genre.findByIdAndDelete(id);
      if (!deleted) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy thể loại!' });
      }
      await Promise.all([
        models.Song.updateMany({ genreIds: id }, { $pull: { genreIds: id } }),
        models.Album.updateMany({ genreIds: id }, { $pull: { genreIds: id } }),
        models.Artist.updateMany({ genreIds: id }, { $pull: { genreIds: id } }),
        models.UserPreference.updateMany({ favoriteGenreIds: id }, { $pull: { favoriteGenreIds: id } })
      ]);
      return ApiResponse.success(res, null, 'Đã xóa thể loại thành công!');
    } catch (error) {
      next(error);
    }
  }

  // ==========================================
  // CRUD PLAYLISTS
  // ==========================================
  async createPlaylist(req, res, next) {
    try {
      const { title, description, creatorId, coverImageUrl, isPublic, isAiGenerated } = req.body;
      if (!title || !title.trim()) {
        return res.status(400).json({ success: false, message: 'Vui lòng nhập tên danh sách phát!' });
      }

      let finalCreatorId = creatorId;
      if (!finalCreatorId) {
        const adminUser = (await models.User.findOne({ role: 'admin' })) || (await models.User.findOne());
        if (adminUser) finalCreatorId = adminUser._id;
      }

      const playlist = await models.Playlist.create({
        title: title.trim(),
        description: description ? description.trim() : '',
        creatorId: finalCreatorId,
        coverImageUrl: sanitizeMediaUrl(coverImageUrl) || '/uploads/playlists/default_music_cover.jpg',
        isPublic: isPublic !== undefined ? Boolean(isPublic) : true,
        isAiGenerated: Boolean(isAiGenerated),
        totalTracks: 0
      });

      const populated = await models.Playlist.findById(playlist._id)
        .populate('creatorId', 'displayName email username')
        .lean();

      return ApiResponse.success(res, populated, 'Thêm Playlist mới thành công!');
    } catch (error) {
      next(error);
    }
  }

  async updatePlaylist(req, res, next) {
    try {
      const { id } = req.params;
      const { title, description, creatorId, coverImageUrl, isPublic } = req.body;

      const playlist = await models.Playlist.findById(id);
      if (!playlist) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy Playlist!' });
      }

      if (typeof title === 'string' && title.trim()) playlist.title = title.trim();
      if (description !== undefined) playlist.description = cleanStr(description);
      if (creatorId) playlist.creatorId = creatorId;
      if (coverImageUrl !== undefined) {
        playlist.coverImageUrl = cleanStr(sanitizeMediaUrl(coverImageUrl)) || '/uploads/playlists/default_music_cover.jpg';
      }
      if (isPublic !== undefined) playlist.isPublic = Boolean(isPublic);

      await playlist.save();
      await syncPlaylistTotalTracks([playlist._id]);

      const populated = await models.Playlist.findById(playlist._id)
        .populate('creatorId', 'displayName email username')
        .lean();

      return ApiResponse.success(res, populated, 'Cập nhật Playlist thành công!');
    } catch (error) {
      next(error);
    }
  }

  async deletePlaylist(req, res, next) {
    try {
      const { id } = req.params;
      const playlist = await models.Playlist.findById(id).lean();
      if (!playlist) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy Playlist!' });
      }
      await models.Playlist.findByIdAndDelete(id);
      await Promise.all([
        models.PlaylistTrack.deleteMany({ playlistId: id }),
        models.UserPreference.updateMany(
          { favoritePlaylistIds: id },
          { $pull: { favoritePlaylistIds: id } }
        )
      ]);
      if (playlist.coverImageUrl) await safelyDeleteLocalUploadIfUnused(playlist.coverImageUrl);
      return ApiResponse.success(res, null, 'Đã xóa Playlist thành công!');
    } catch (error) {
      next(error);
    }
  }

  async getPlaylistTracks(req, res, next) {
    try {
      const { id } = req.params;
      const playlist = await models.Playlist.findById(id)
        .populate('creatorId', 'displayName username email avatarUrl')
        .lean();
      if (!playlist) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy Playlist!' });
      }

      const tracks = await models.PlaylistTrack.find({ playlistId: id })
        .sort({ order: 1 })
        .populate({
          path: 'songId',
          populate: [
            { path: 'artistId', select: 'name avatarUrl isVerified' },
            { path: 'featuredArtistIds', select: 'name avatarUrl isVerified' },
            { path: 'artistIds', select: 'name avatarUrl isVerified' },
            { path: 'composerId', select: 'name avatarUrl isVerified' },
            { path: 'composerIds', select: 'name avatarUrl isVerified' },
            { path: 'albumId', select: 'title coverImageUrl' },
            { path: 'genreIds', select: 'name colorCode' }
          ]
        })
        .lean();

      const formattedTracks = tracks
        .filter((t) => t.songId != null)
        .map((t) => ({
          ...t.songId,
          order: t.order,
          addedAt: t.addedAt
        }));

      return ApiResponse.success(res, {
        playlist,
        tracks: formattedTracks,
        totalTracks: formattedTracks.length
      }, 'Lấy danh sách bài hát trong Playlist thành công!');
    } catch (error) {
      next(error);
    }
  }

  async addSongToPlaylist(req, res, next) {
    try {
      const { id } = req.params;
      const { songId } = req.body;
      if (!songId) {
        return res.status(400).json({ success: false, message: 'Vui lòng chọn bài hát cần thêm!' });
      }

      const playlist = await models.Playlist.findById(id);
      if (!playlist) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy Playlist!' });
      }

      const song = await models.Song.findById(songId);
      if (!song) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy bài hát!' });
      }

      const existingTrack = await models.PlaylistTrack.findOne({ playlistId: id, songId });
      if (existingTrack) {
        return res.status(400).json({ success: false, message: 'Bài hát đã có trong Playlist này rồi!' });
      }

      const lastTrack = await models.PlaylistTrack.findOne({ playlistId: id }).sort({ order: -1 });
      const nextOrder = lastTrack ? lastTrack.order + 1 : 0;

      await models.PlaylistTrack.create({
        playlistId: id,
        songId,
        order: nextOrder,
        addedAt: new Date()
      });

      const count = await models.PlaylistTrack.countDocuments({ playlistId: id });
      playlist.totalTracks = count;
      await playlist.save();

      return ApiResponse.success(res, { playlist, totalTracks: count }, 'Đã thêm bài hát vào Playlist thành công!');
    } catch (error) {
      next(error);
    }
  }

  async removeSongFromPlaylist(req, res, next) {
    try {
      const { id, songId } = req.params;
      const playlist = await models.Playlist.findById(id);
      if (!playlist) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy Playlist!' });
      }

      await models.PlaylistTrack.findOneAndDelete({ playlistId: id, songId });

      const count = await models.PlaylistTrack.countDocuments({ playlistId: id });
      playlist.totalTracks = count;
      await playlist.save();

      return ApiResponse.success(res, { playlist, totalTracks: count }, 'Đã gỡ bài hát khỏi Playlist thành công!');
    } catch (error) {
      next(error);
    }
  }

  // ==========================================
  // CRUD NGƯỜI DÙNG (USERS)
  // ==========================================
  async createUser(req, res, next) {
    try {
      const { username, email, password, displayName, avatarUrl, role, isActive, phoneNumber } = req.body;
      if (!username || !password || !displayName) {
        return res.status(400).json({ success: false, message: 'Vui lòng nhập tên đăng nhập, mật khẩu và tên hiển thị!' });
      }

      const validRole = role ? String(role).trim().toLowerCase() : 'user';
      if (!['user', 'admin', 'artist'].includes(validRole)) {
        return res.status(400).json({ success: false, message: 'Vai trò người dùng không hợp lệ (chấp nhận user, artist hoặc admin)!' });
      }

      const safeAvatarUrl = avatarUrl ? sanitizeMediaUrl(avatarUrl) : '';

      const cleanUsername = username.toLowerCase().trim();
      const cleanEmail = email ? email.toLowerCase().trim() : `${cleanUsername}@phone.vibe.vn`;

      const existingUser = await models.User.findOne({
        $or: [{ username: cleanUsername }, { email: cleanEmail }]
      });
      if (existingUser) {
        return res.status(400).json({ success: false, message: 'Tên đăng nhập hoặc email đã được sử dụng!' });
      }

      const salt = await bcrypt.genSalt(10);
      const passwordHash = await bcrypt.hash(password, salt);

      const user = await models.User.create({
        username: cleanUsername,
        email: cleanEmail,
        phoneNumber: phoneNumber ? phoneNumber.trim() : undefined,
        passwordHash,
        displayName: displayName.trim(),
        avatarUrl: safeAvatarUrl || `https://api.dicebear.com/7.x/initials/png?seed=${encodeURIComponent(displayName)}`,
        role: validRole,
        isActive: isActive !== undefined ? Boolean(isActive) : true
      });

      await models.UserPreference.create({
        userId: user._id,
        favoriteGenreIds: [],
        favoriteArtistIds: []
      });

      const userObj = user.toObject();
      delete userObj.passwordHash;

      return ApiResponse.success(res, userObj, 'Thêm người dùng mới thành công!');
    } catch (error) {
      next(error);
    }
  }

  async updateUser(req, res, next) {
    try {
      const { id } = req.params;
      const { displayName, email, phoneNumber, role, isActive, avatarUrl, newPassword } = req.body;

      const user = await models.User.findById(id);
      if (!user) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy người dùng!' });
      }

      if (role !== undefined && role !== null && role !== '') {
        const validRole = String(role).trim().toLowerCase();
        if (!['user', 'admin', 'artist'].includes(validRole)) {
          return res.status(400).json({ success: false, message: 'Vai trò người dùng không hợp lệ!' });
        }
        if (user.role === 'admin' && validRole !== 'admin') {
          if (req.user && String(req.user._id) === String(user._id)) {
            return res.status(400).json({ success: false, message: 'Không thể tự hạ quyền Quản trị viên của chính bạn!' });
          }
          const activeAdmins = await models.User.countDocuments({ role: 'admin', isActive: true });
          if (activeAdmins <= 1 && user.isActive) {
            return res.status(400).json({ success: false, message: 'Không thể hạ quyền Quản trị viên cuối cùng đang hoạt động!' });
          }
        }
        user.role = validRole;
      }

      if (isActive !== undefined) {
        const nextActive = Boolean(isActive);
        if (!nextActive && user.role === 'admin') {
          if (req.user && String(req.user._id) === String(user._id)) {
            return res.status(400).json({ success: false, message: 'Không thể tự vô hiệu hóa tài khoản Quản trị đang đăng nhập!' });
          }
          const activeAdmins = await models.User.countDocuments({ role: 'admin', isActive: true });
          if (activeAdmins <= 1 && user.isActive) {
            return res.status(400).json({ success: false, message: 'Không thể khóa Quản trị viên cuối cùng đang hoạt động!' });
          }
        }
        user.isActive = nextActive;
      }

      if (typeof displayName === 'string' && displayName.trim()) user.displayName = displayName.trim();
      if (typeof email === 'string' && email.trim()) user.email = email.toLowerCase().trim();
      if (phoneNumber !== undefined) {
        const cleanPhone = cleanStr(phoneNumber);
        if (cleanPhone) {
          user.phoneNumber = cleanPhone;
        } else {
          user.set('phoneNumber', undefined);
        }
      }
      if (avatarUrl !== undefined) user.avatarUrl = cleanStr(sanitizeMediaUrl(avatarUrl));

      if (typeof newPassword === 'string' && newPassword.trim()) {
        const salt = await bcrypt.genSalt(10);
        user.passwordHash = await bcrypt.hash(newPassword.trim(), salt);
      }

      await user.save();
      const userObj = user.toObject();
      delete userObj.passwordHash;

      return ApiResponse.success(res, userObj, 'Cập nhật người dùng thành công!');
    } catch (error) {
      next(error);
    }
  }

  async deleteUser(req, res, next) {
    try {
      const { id } = req.params;
      if (req.user && String(req.user._id) === String(id)) {
        return res.status(400).json({ success: false, message: 'Không thể tự xóa tài khoản Quản trị đang đăng nhập!' });
      }
      const targetUser = await models.User.findById(id).select('role isActive').lean();
      if (!targetUser) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy người dùng!' });
      }
      if (targetUser.role === 'admin' && targetUser.isActive) {
        const activeAdmins = await models.User.countDocuments({ role: 'admin', isActive: true });
        if (activeAdmins <= 1) {
          return res.status(400).json({ success: false, message: 'Không thể xóa Quản trị viên cuối cùng đang hoạt động!' });
        }
      }

      const deleted = await models.User.findByIdAndDelete(id);
      if (!deleted) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy người dùng!' });
      }
      const [userPlaylistIds, favoritedSongIds] = await Promise.all([
        models.Playlist.distinct('_id', { creatorId: id }),
        models.UserFavorite.distinct('songId', { userId: id })
      ]);
      await Promise.all([
        models.UserPreference.deleteMany({ userId: id }),
        models.UserFavorite.deleteMany({ userId: id }),
        models.ListeningHistory.deleteMany({ userId: id }),
        models.ListeningEvent.deleteMany({ userId: id }),
        models.UserInteraction.deleteMany({ userId: id }),
        models.PlaySession.deleteMany({ userId: id }),
        models.AiPromptLog.deleteMany({ userId: id }),
        models.Notification.deleteMany({ $or: [{ recipientId: id }, { senderId: id }] }),
        userPlaylistIds.length > 0
          ? models.PlaylistTrack.deleteMany({ playlistId: { $in: userPlaylistIds } })
          : Promise.resolve(),
        userPlaylistIds.length > 0
          ? models.UserPreference.updateMany(
              { favoritePlaylistIds: { $in: userPlaylistIds } },
              { $pull: { favoritePlaylistIds: { $in: userPlaylistIds } } }
            )
          : Promise.resolve(),
        models.Playlist.deleteMany({ creatorId: id })
      ]);
      if (favoritedSongIds.length > 0) {
        await Promise.all(
          favoritedSongIds.map(async (songId) => {
            const count = await models.UserFavorite.countDocuments({ songId });
            await models.Song.findByIdAndUpdate(songId, { $set: { likesCount: count } });
          })
        );
      }
      return ApiResponse.success(res, null, 'Đã xóa người dùng và toàn bộ dữ liệu liên quan thành công!');
    } catch (error) {
      next(error);
    }
  }

  async getUserDetail(req, res, next) {
    try {
      const { id } = req.params;
      if (!mongoose.Types.ObjectId.isValid(id)) {
        return res.status(400).json({ success: false, message: 'ID người dùng không hợp lệ!' });
      }
      const objectId = new mongoose.Types.ObjectId(id);

      const [
        user,
        preference,
        histories,
        favorites,
        rawPlaylists,
        aiLogs,
        historyAgg,
        totalFavoritesCount,
        totalAiLogsCount
      ] = await Promise.all([
        models.User.findById(objectId).select('-passwordHash').lean(),
        models.UserPreference.findOne({ userId: objectId })
          .populate('favoriteGenreIds', 'name colorCode')
          .populate('favoriteArtistIds', 'name avatarUrl isVerified')
          .lean(),
        models.ListeningHistory.find({ userId: objectId })
          .populate({
            path: 'songId',
            select: 'title coverImageUrl duration audioUrl artistId composer',
            populate: { path: 'artistId', select: 'name' }
          })
          .sort({ timestamp: -1 })
          .limit(300)
          .lean(),
        models.UserFavorite.find({ userId: objectId })
          .populate({
            path: 'songId',
            select: 'title coverImageUrl duration audioUrl artistId composer',
            populate: { path: 'artistId', select: 'name' }
          })
          .sort({ createdAt: -1 })
          .limit(300)
          .lean(),
        models.Playlist.find({ creatorId: objectId }).sort({ createdAt: -1 }).lean(),
        models.AiPromptLog.find({ userId: objectId })
          .populate('generatedPlaylistId', 'title totalTracks')
          .sort({ createdAt: -1 })
          .limit(50)
          .lean(),
        models.ListeningHistory.aggregate([
          { $match: { userId: objectId } },
          {
            $group: {
              _id: '$userId',
              totalPlays: { $sum: 1 },
              totalDurationSec: { $sum: { $ifNull: ['$listenDuration', 0] } },
              completedPlays: { $sum: { $cond: ['$completed', 1, 0] } },
              skippedPlays: { $sum: { $cond: ['$skipped', 1, 0] } },
              songIds: { $addToSet: '$songId' },
              lastPlayedAt: { $max: '$timestamp' }
            }
          }
        ]),
        models.UserFavorite.countDocuments({ userId: objectId }),
        models.AiPromptLog.countDocuments({ userId: objectId })
      ]);

      if (!user && histories.length === 0 && favorites.length === 0) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy thông tin người dùng!' });
      }

      const playlists = await PlaylistService.attachLivePlaylistTrackCounts(rawPlaylists);
      const hStat = historyAgg[0] || {};
      const resolvedUser = user || {
        _id: objectId,
        username: 'deleted_user',
        displayName: 'Người dùng đã xóa',
        email: '',
        avatarUrl: '',
        role: 'user',
        isActive: false,
        isDeletedPlaceholder: true
      };

      const stats = {
        totalPlays: hStat.totalPlays || 0,
        uniqueSongsCount: Array.isArray(hStat.songIds) ? hStat.songIds.length : 0,
        totalDurationSec: hStat.totalDurationSec || 0,
        completedPlays: hStat.completedPlays || 0,
        skippedPlays: hStat.skippedPlays || 0,
        lastPlayedAt: hStat.lastPlayedAt || null,
        totalFavorites: totalFavoritesCount || 0,
        totalPlaylists: playlists.length || 0,
        totalAiLogs: totalAiLogsCount || 0
      };

      return ApiResponse.success(
        res,
        {
          user: resolvedUser,
          preference: preference || null,
          stats,
          histories,
          favorites,
          playlists,
          aiLogs
        },
        'Lấy chi tiết người dùng thành công'
      );
    } catch (error) {
      next(error);
    }
  }

  async clearUserHistories(req, res, next) {
    try {
      const { id } = req.params;
      if (!mongoose.Types.ObjectId.isValid(id)) {
        return res.status(400).json({ success: false, message: 'ID người dùng không hợp lệ!' });
      }
      const result = await models.ListeningHistory.deleteMany({ userId: id });
      return ApiResponse.success(
        res,
        { deletedCount: result.deletedCount || 0 },
        `Đã xóa ${result.deletedCount || 0} bản ghi lịch sử nghe của người dùng!`
      );
    } catch (error) {
      next(error);
    }
  }

  async deleteHistoryRecord(req, res, next) {
    try {
      const { id } = req.params;
      const deleted = await models.ListeningHistory.findByIdAndDelete(id);
      if (!deleted) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy bản ghi lịch sử!' });
      }
      return ApiResponse.success(res, null, 'Đã xóa bản ghi lịch sử nghe!');
    } catch (error) {
      next(error);
    }
  }

  async deleteFavoriteRecord(req, res, next) {
    try {
      const { id } = req.params;
      const deleted = await models.UserFavorite.findByIdAndDelete(id);
      if (!deleted) {
        return res.status(404).json({ success: false, message: 'Không tìm thấy bản ghi yêu thích!' });
      }
      if (deleted.songId) {
        const likesCount = await models.UserFavorite.countDocuments({ songId: deleted.songId });
        await models.Song.findByIdAndUpdate(deleted.songId, { $set: { likesCount } });
      }
      return ApiResponse.success(res, null, 'Đã gỡ bản ghi yêu thích!');
    } catch (error) {
      next(error);
    }
  }

  // ==========================================
  // CRUD AI PROMPT LOGS
  // ==========================================
  async deleteAiLog(req, res, next) {
    try {
      const { id } = req.params;
      await models.AiPromptLog.findByIdAndDelete(id);
      return ApiResponse.success(res, null, 'Đã xóa bản ghi log AI thành công!');
    } catch (error) {
      next(error);
    }
  }

  async clearAllAiLogs(req, res, next) {
    try {
      await models.AiPromptLog.deleteMany({});
      return ApiResponse.success(res, null, 'Đã xóa toàn bộ lịch sử AI logs thành công!');
    } catch (error) {
      next(error);
    }
  }

  // ==========================================
  // CRUD NOTIFICATIONS (ADMIN)
  // ==========================================
  async createNotification(req, res, next) {
    try {
      const NotificationService = require('../services/notificationService');
      const { targetType, targetUserIds, title, content, imageUrl, type, metadata } = req.body;
      const result = await NotificationService.sendAdminNotification({
        targetType,
        targetUserIds,
        title,
        content,
        imageUrl,
        type,
        metadata,
        adminUser: req.user
      });
      return ApiResponse.success(res, result, `Đã gửi thông báo thành công tới ${result.sentCount} người dùng!`);
    } catch (error) {
      next(error);
    }
  }

  async deleteNotification(req, res, next) {
    try {
      const { id } = req.params;
      await models.Notification.findByIdAndDelete(id);
      const socketManager = require('../websocket/socketServer');
      socketManager.broadcastAll('ADMIN_SUMMARY_UPDATE', { type: 'notifications' });
      return ApiResponse.success(res, null, 'Đã xóa thông báo thành công!');
    } catch (error) {
      next(error);
    }
  }

  async clearAllNotifications(req, res, next) {
    try {
      await models.Notification.deleteMany({});
      const socketManager = require('../websocket/socketServer');
      socketManager.broadcastAll('ADMIN_SUMMARY_UPDATE', { type: 'notifications' });
      return ApiResponse.success(res, null, 'Đã xóa toàn bộ thông báo thành công!');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = new AdminController();
