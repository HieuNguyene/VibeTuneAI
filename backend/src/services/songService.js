const mongoose = require('mongoose');
const Song = require('../models/Song');
const Artist = require('../models/Artist');
const Album = require('../models/Album');
const Genre = require('../models/Genre');
const UserFavorite = require('../models/UserFavorite');
const ListeningHistory = require('../models/ListeningHistory');
const Playlist = require('../models/Playlist');
const UserPreference = require('../models/UserPreference');
const ApiError = require('../utils/ApiError');
const AlbumService = require('./albumService');
const PlaylistService = require('./playlistService');

const SONG_POPULATE_SPEC = [
  { path: 'artistId', select: 'name avatarUrl isVerified' },
  { path: 'featuredArtistIds', select: 'name avatarUrl isVerified' },
  { path: 'artistIds', select: 'name avatarUrl isVerified' },
  { path: 'albumId', select: 'title coverImageUrl' },
  { path: 'genreIds', select: 'name colorCode' }
];

function formatSongArtistFields(song) {
  let artistName = song.artistId?.name || song.artistName || '';
  if (Array.isArray(song.featuredArtistIds) && song.featuredArtistIds.length > 0) {
    const featNames = song.featuredArtistIds.map((f) => (f && typeof f === 'object' && typeof f.name === 'string' ? f.name.trim() : '')).filter(Boolean);
    if (featNames.length > 0 && !artistName.includes(' ft. ')) {
      artistName = `${artistName} ft. ${featNames.join(', ')}`;
    }
  }
  return {
    ...song,
    artistName
  };
}

function buildVietnameseRegex(query) {
  if (!query) return null;
  const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const accentMap = {
    a: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    à: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    á: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ả: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ã: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ạ: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ă: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ằ: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ắ: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ẳ: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ẵ: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ặ: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    â: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ầ: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ấ: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ẩ: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ẫ: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    ậ: '[aàáảãạăằắẳẵặâầấẩẫậ]',
    e: '[eèéẻẽẹêềếểễệ]',
    è: '[eèéẻẽẹêềếểễệ]',
    é: '[eèéẻẽẹêềếểễệ]',
    ẻ: '[eèéẻẽẹêềếểễệ]',
    ẽ: '[eèéẻẽẹêềếểễệ]',
    ẹ: '[eèéẻẽẹêềếểễệ]',
    ê: '[eèéẻẽẹêềếểễệ]',
    ề: '[eèéẻẽẹêềếểễệ]',
    ế: '[eèéẻẽẹêềếểễệ]',
    ể: '[eèéẻẽẹêềếểễệ]',
    ễ: '[eèéẻẽẹêềếểễệ]',
    ệ: '[eèéẻẽẹêềếểễệ]',
    i: '[iìíỉĩị]',
    ì: '[iìíỉĩị]',
    í: '[iìíỉĩị]',
    ỉ: '[iìíỉĩị]',
    ĩ: '[iìíỉĩị]',
    ị: '[iìíỉĩị]',
    o: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ò: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ó: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ỏ: '[oòóỏõọôồốổỗộơờớởỡợ]',
    õ: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ọ: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ô: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ồ: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ố: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ổ: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ỗ: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ộ: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ơ: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ờ: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ớ: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ở: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ỡ: '[oòóỏõọôồốổỗộơờớởỡợ]',
    ợ: '[oòóỏõọôồốổỗộơờớởỡợ]',
    u: '[uùúủũụưừứửữự]',
    ù: '[uùúủũụưừứửữự]',
    ú: '[uùúủũụưừứửữự]',
    ủ: '[uùúủũụưừứửữự]',
    ũ: '[uùúủũụưừứửữự]',
    ụ: '[uùúủũụưừứửữự]',
    ư: '[uùúủũụưừứửữự]',
    ừ: '[uùúủũụưừứửữự]',
    ứ: '[uùúủũụưừứửữự]',
    ử: '[uùúủũụưừứửữự]',
    ữ: '[uùúủũụưừứửữự]',
    ự: '[uùúủũụưừứửữự]',
    y: '[yỳýỷỹỵ]',
    ỳ: '[yỳýỷỹỵ]',
    ý: '[yỳýỷỹỵ]',
    ỷ: '[yỳýỷỹỵ]',
    ỹ: '[yỳýỷỹỵ]',
    ỵ: '[yỳýỷỹỵ]',
    d: '[dđ]',
    đ: '[dđ]'
  };

  let pattern = '';
  for (const char of escaped) {
    const lower = char.toLowerCase();
    if (accentMap[lower]) {
      pattern += accentMap[lower];
    } else if (/\s/.test(char)) {
      pattern += '\\s+';
    } else {
      pattern += char;
    }
  }
  return new RegExp(pattern, 'i');
}

class SongService {
  /**
   * Lấy danh sách toàn bộ bài hát với bộ lọc nâng cao, phân trang và sắp xếp
   */
  static async getAllSongs({ page = 1, limit = 20, genreId, mood, artistId, search, sort = 'playsCount' } = {}, userId = null) {
    const pageNum = Math.max(1, parseInt(page, 10) || 1);
    const limitNum = Math.min(100, Math.max(1, parseInt(limit, 10) || 20));
    const skip = (pageNum - 1) * limitNum;

    const filter = { isPublic: true };
    const andConditions = [];

    if (genreId && mongoose.Types.ObjectId.isValid(genreId)) {
      filter.genreIds = genreId;
    }

    if (mood && typeof mood === 'string' && mood.trim()) {
      const normalizedMood = mood.trim().toLowerCase();
      andConditions.push({
        $or: [
          { moods: normalizedMood },
          { mood: normalizedMood }
        ]
      });
    }

    if (artistId && mongoose.Types.ObjectId.isValid(artistId)) {
      andConditions.push({
        $or: [
          { artistId },
          { artistIds: artistId },
          { featuredArtistIds: artistId }
        ]
      });
    }

    if (andConditions.length > 0) {
      filter.$and = andConditions;
    }

    if (search && typeof search === 'string' && search.trim()) {
      const reg = buildVietnameseRegex(search.trim());
      if (reg) {
        filter.title = reg;
      }
    }

    let sortOption = { playsCount: -1 };
    if (sort === 'newest' || sort === 'createdAt') {
      sortOption = { createdAt: -1 };
    } else if (sort === 'title') {
      sortOption = { title: 1 };
    } else if (sort === 'oldest') {
      sortOption = { createdAt: 1 };
    }

    const [songs, total] = await Promise.all([
      Song.find(filter)
        .select('-embedding')
        .sort(sortOption)
        .skip(skip)
        .limit(limitNum)
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('featuredArtistIds', 'name avatarUrl isVerified')
        .populate('artistIds', 'name avatarUrl isVerified')
        .populate('composerId', 'name avatarUrl isVerified')
        .populate('composerIds', 'name avatarUrl isVerified')
        .populate('albumId', 'title coverImageUrl')
        .populate('genreIds', 'name colorCode')
        .lean(),
      Song.countDocuments(filter)
    ]);

    let likedSongIds = new Set();
    if (userId) {
      const favorites = await UserFavorite.find({ userId }).select('songId').lean();
      likedSongIds = new Set(favorites.map((f) => f.songId.toString()));
    }

    const mappedSongs = songs.map((s) => ({
      ...formatSongArtistFields(s),
      isLiked: likedSongIds.has(s._id.toString())
    }));

    return {
      songs: mappedSongs,
      pagination: {
        page: pageNum,
        limit: limitNum,
        total,
        totalPages: Math.ceil(total / limitNum)
      }
    };
  }

  /**
   * Lấy dữ liệu tổng hợp cho Màn hình Home Feed (Spotify Home Style)
   * Bao gồm 9 mục cho tab "Tất cả" và giữ nguyên các trường tương thích ngược.
   */
  static async getHomeFeed(userId = null) {
    const todayStr = new Date().toISOString().slice(0, 10);
    let dailySeed = 0;
    for (let i = 0; i < todayStr.length; i++) {
      dailySeed = (dailySeed * 31 + todayStr.charCodeAt(i)) & 0xffffffff;
    }

    // 1. Lấy danh sách thể loại, bài hát thịnh hành, mới phát hành & ngữ cảnh người dùng song song
    const [genres, trendingSongs, newReleases, catalogPool, userPref, userFavDocs, historyRecords] = await Promise.all([
      Genre.find().lean(),
      Song.find({ isPublic: true })
        .select('-embedding')
        .sort({ playsCount: -1 })
        .limit(10)
        .populate(SONG_POPULATE_SPEC)
        .lean(),
      Song.find({ isPublic: true })
        .select('-embedding')
        .sort({ createdAt: -1 })
        .limit(10)
        .populate(SONG_POPULATE_SPEC)
        .lean(),
      Song.find({ isPublic: true })
        .select('-embedding')
        .sort({ likesCount: -1, playsCount: -1 })
        .limit(45)
        .populate(SONG_POPULATE_SPEC)
        .lean(),
      userId ? UserPreference.findOne({ userId }).select('favoriteAlbumIds favoriteArtistIds favoriteGenreIds').lean() : Promise.resolve(null),
      userId
        ? UserFavorite.find({ userId })
            .sort({ createdAt: -1 })
            .populate({
              path: 'songId',
              select: '-embedding',
              populate: SONG_POPULATE_SPEC
            })
            .lean()
        : Promise.resolve([]),
      userId
        ? ListeningHistory.find({ userId })
            .sort({ timestamp: -1 })
            .limit(40)
            .populate({
              path: 'songId',
              select: '-embedding',
              populate: SONG_POPULATE_SPEC
            })
            .lean()
        : Promise.resolve([])
    ]);

    const likedSongIds = new Set();
    const likedSongObjects = [];
    const albumLikeCountMap = new Map();
    const userGenreScoreMap = new Map();

    for (const fav of userFavDocs) {
      const s = fav.songId;
      if (!s || s.isPublic === false) continue;
      likedSongIds.add(s._id.toString());
      likedSongObjects.push(s);
      if (s.albumId) {
        const albId = String(s.albumId._id || s.albumId);
        albumLikeCountMap.set(albId, (albumLikeCountMap.get(albId) || 0) + 1);
      }
      if (Array.isArray(s.genreIds)) {
        for (const g of s.genreIds) {
          const gid = String(g._id || g);
          userGenreScoreMap.set(gid, (userGenreScoreMap.get(gid) || 0) + 2);
        }
      }
    }

    // 2. Nội dung nghe gần đây + thống kê nghe lại (Listen Again)
    let recentSongs = [];
    const seenRecentIds = new Set();
    const playFrequencyMap = new Map();

    for (const h of historyRecords) {
      const s = h.songId;
      if (!s || s.isPublic === false) continue;
      const sid = s._id.toString();

      if (!seenRecentIds.has(sid)) {
        seenRecentIds.add(sid);
        recentSongs.push(s);
      }
      const existingFreq = playFrequencyMap.get(sid) || { song: s, count: 0 };
      existingFreq.count += 1;
      playFrequencyMap.set(sid, existingFreq);

      if (Array.isArray(s.genreIds)) {
        for (const g of s.genreIds) {
          const gid = String(g._id || g);
          userGenreScoreMap.set(gid, (userGenreScoreMap.get(gid) || 0) + 1);
        }
      }
    }

    if (recentSongs.length === 0) {
      recentSongs = catalogPool.slice(4, 10);
      if (recentSongs.length === 0) recentSongs = trendingSongs.slice(0, 6);
    } else {
      recentSongs = recentSongs.slice(0, 8);
    }

    // 3. Album có bài hát bạn thích (ưu tiên album có nhiều bài hát user đã thả tim + favoriteAlbumIds)
    const likedAlbumIdsOrdered = [...albumLikeCountMap.entries()]
      .sort((a, b) => b[1] - a[1])
      .map(([id]) => id);
    if (userPref && Array.isArray(userPref.favoriteAlbumIds)) {
      for (const favAlbId of userPref.favoriteAlbumIds) {
        const sid = String(favAlbId);
        if (!likedAlbumIdsOrdered.includes(sid)) {
          likedAlbumIdsOrdered.push(sid);
        }
      }
    }

    let likedSongAlbums = [];
    if (likedAlbumIdsOrdered.length > 0) {
      const rawUserLikedAlbums = await Album.find({ _id: { $in: likedAlbumIdsOrdered } })
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('genreIds', 'name colorCode')
        .lean();
      const orderIdx = new Map(likedAlbumIdsOrdered.map((id, i) => [id, i]));
      rawUserLikedAlbums.sort((a, b) => (orderIdx.get(String(a._id)) ?? 999) - (orderIdx.get(String(b._id)) ?? 999));
      likedSongAlbums = (await AlbumService.attachLiveAlbumTrackCounts(rawUserLikedAlbums)).filter((a) => a.totalTracks > 0);
    }

    // Fallback khi user chưa thích bài có album: lấy các album có nhiều lượt thích từ cộng đồng nhất
    if (likedSongAlbums.length === 0) {
      const topCommunityAlbums = await Song.aggregate([
        { $match: { isPublic: true, albumId: { $ne: null } } },
        { $group: { _id: '$albumId', totalLikes: { $sum: '$likesCount' }, tracks: { $sum: 1 } } },
        { $sort: { totalLikes: -1, tracks: -1 } },
        { $limit: 10 }
      ]);
      const fallbackAlbIds = topCommunityAlbums.map((a) => a._id);
      if (fallbackAlbIds.length > 0) {
        const rawFallback = await Album.find({ _id: { $in: fallbackAlbIds } })
          .populate('artistId', 'name avatarUrl isVerified')
          .populate('genreIds', 'name colorCode')
          .lean();
        const fbOrder = new Map(fallbackAlbIds.map((id, i) => [String(id), i]));
        rawFallback.sort((a, b) => (fbOrder.get(String(a._id)) ?? 999) - (fbOrder.get(String(b._id)) ?? 999));
        likedSongAlbums = (await AlbumService.attachLiveAlbumTrackCounts(rawFallback)).filter((a) => a.totalTracks > 0);
      }
    }

    // 4. Danh sách phát gần đây (ưu tiên playlist cá nhân mới tạo/cập nhật + playlist công khai mới nhất)
    let userOwnPlaylists = [];
    if (userId) {
      const rawOwn = await Playlist.find({ creatorId: userId })
        .sort({ updatedAt: -1, createdAt: -1 })
        .limit(6)
        .populate('creatorId', 'displayName avatarUrl')
        .lean();
      userOwnPlaylists = await PlaylistService.attachLivePlaylistTrackCounts(rawOwn);
    }
    const ownPlIdSet = new Set(userOwnPlaylists.map((p) => String(p._id)));

    const rawPublicRecentPls = await Playlist.find({
      isPublic: true,
      ...(ownPlIdSet.size > 0 ? { _id: { $nin: Array.from(ownPlIdSet) } } : {})
    })
      .sort({ updatedAt: -1, createdAt: -1 })
      .limit(12)
      .populate('creatorId', 'displayName avatarUrl')
      .lean();
    const publicRecentPls = (await PlaylistService.attachLivePlaylistTrackCounts(rawPublicRecentPls)).filter(
      (p) => p.totalTracks > 0
    );
    const recentPlaylists = [...userOwnPlaylists, ...publicRecentPls].slice(0, 10);

    // 5. Album và đĩa đơn nổi tiếng (xếp hạng theo tổng lượt nghe các bài trong album)
    const albumPlayStats = await Song.aggregate([
      { $match: { isPublic: true, albumId: { $ne: null } } },
      { $group: { _id: '$albumId', totalPlays: { $sum: '$playsCount' }, trackCount: { $sum: 1 } } },
      { $sort: { totalPlays: -1, trackCount: -1 } },
      { $limit: 12 }
    ]);
    const popAlbumIds = albumPlayStats.map((a) => a._id);
    const rawPopularAlbums = await Album.find(popAlbumIds.length > 0 ? { _id: { $in: popAlbumIds } } : {})
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('genreIds', 'name colorCode')
      .limit(10)
      .lean();
    if (popAlbumIds.length > 0) {
      const popIdx = new Map(popAlbumIds.map((id, i) => [String(id), i]));
      rawPopularAlbums.sort((a, b) => (popIdx.get(String(a._id)) ?? 999) - (popIdx.get(String(b._id)) ?? 999));
    }
    const popularAlbums = (await AlbumService.attachLiveAlbumTrackCounts(rawPopularAlbums)).filter((a) => a.totalTracks > 0);

    // 6. Radio phổ biến (Playlist thịnh hành biên tập theo thể loại/xu hướng nhiều bài hát)
    const rawFeaturedPlaylists = await Playlist.find({ isPublic: true })
      .sort({ totalTracks: -1, createdAt: 1 })
      .limit(14)
      .populate('creatorId', 'displayName avatarUrl')
      .lean();
    const featuredPlaylists = (await PlaylistService.attachLivePlaylistTrackCounts(rawFeaturedPlaylists)).filter(
      (p) => p.totalTracks > 0
    );
    const topRecentIds = new Set(recentPlaylists.slice(0, 3).map((p) => String(p._id)));
    const distinctRadios = featuredPlaylists.filter((p) => !topRecentIds.has(String(p._id)));
    const popularRadios = (distinctRadios.length >= 4 ? distinctRadios : featuredPlaylists).slice(0, 10);

    // 7. Tuyển tập hàng đầu của bạn (Made For You / Personalized Recommender)
    const RecommendationEngine = require('../ai/recommendationEngine');
    const trendingIds = new Set(trendingSongs.map((s) => s._id.toString()));
    let madeForYouSongs = await RecommendationEngine.getPersonalizedFeed(userId, 10);
    if (!userId || !madeForYouSongs || madeForYouSongs.length === 0 || madeForYouSongs.filter((s) => trendingIds.has(s._id.toString())).length > 2) {
      madeForYouSongs = await RecommendationEngine.getColdStartRecommendations(10, new Set(), Array.from(trendingIds));
    }

    // 8. Đề xuất cho hôm nay (ưu tiên sở thích người dùng + biến thiên theo ngày, hạn chế trùng Made For You)
    const madeForYouIdSet = new Set(madeForYouSongs.map((s) => s._id.toString()));
    const todayCandidates = catalogPool.filter((s) => !madeForYouIdSet.has(s._id.toString()));
    const todaySource = todayCandidates.length >= 6 ? todayCandidates : catalogPool;
    const recommendedTodaySongs = [...todaySource]
      .sort((a, b) => {
        const hashA = Math.sin(dailySeed + (a._id.toString().charCodeAt(12) || 3)) * 10000;
        const hashB = Math.sin(dailySeed + (b._id.toString().charCodeAt(12) || 3)) * 10000;
        const randA = hashA - Math.floor(hashA);
        const randB = hashB - Math.floor(hashB);
        let genreBoostA = 0;
        let genreBoostB = 0;
        if (Array.isArray(a.genreIds)) {
          for (const g of a.genreIds) genreBoostA += (userGenreScoreMap.get(String(g._id || g)) || 0) * 0.05;
        }
        if (Array.isArray(b.genreIds)) {
          for (const g of b.genreIds) genreBoostB += (userGenreScoreMap.get(String(g._id || g)) || 0) * 0.05;
        }
        const scoreA = randA * 0.55 + Math.min(0.35, (a.playsCount || 0) / 2000000) + Math.min(0.25, genreBoostA);
        const scoreB = randB * 0.55 + Math.min(0.35, (b.playsCount || 0) / 2000000) + Math.min(0.25, genreBoostB);
        return scoreB - scoreA;
      })
      .slice(0, 10);

    // 9. Nghe lại (ưu tiên bài nghe lặp nhiều lần / yêu thích của người dùng)
    const listenAgainList = [];
    const seenListenAgainIds = new Set();
    const sortedRepeatEntries = [...playFrequencyMap.values()].sort((a, b) => b.count - a.count);
    for (const entry of sortedRepeatEntries) {
      const sid = entry.song._id.toString();
      if (!seenListenAgainIds.has(sid)) {
        seenListenAgainIds.add(sid);
        listenAgainList.push(entry.song);
      }
      if (listenAgainList.length >= 10) break;
    }
    if (listenAgainList.length < 8) {
      for (const s of likedSongObjects) {
        const sid = s._id.toString();
        if (!seenListenAgainIds.has(sid)) {
          seenListenAgainIds.add(sid);
          listenAgainList.push(s);
        }
        if (listenAgainList.length >= 10) break;
      }
    }
    if (listenAgainList.length < 6) {
      for (const s of catalogPool) {
        const sid = s._id.toString();
        if (!seenListenAgainIds.has(sid) && !madeForYouIdSet.has(sid)) {
          seenListenAgainIds.add(sid);
          listenAgainList.push(s);
        }
        if (listenAgainList.length >= 10) break;
      }
    }

    // 10. Tuyển tập nhạc hay nhất của các nghệ sĩ (kèm songsCount thực tế trong catalog)
    const artistSongStats = await Song.aggregate([
      { $match: { isPublic: true } },
      {
        $project: {
          playsCount: 1,
          allArtists: {
            $setUnion: [
              { $cond: [{ $ifNull: ['$artistId', false] }, ['$artistId'], []] },
              { $ifNull: ['$featuredArtistIds', []] },
              { $ifNull: ['$artistIds', []] },
              { $ifNull: ['$composerIds', []] }
            ]
          }
        }
      },
      { $unwind: '$allArtists' },
      { $group: { _id: '$allArtists', songCount: { $sum: 1 }, totalPlays: { $sum: '$playsCount' } } },
      { $sort: { songCount: -1, totalPlays: -1 } },
      { $limit: 20 }
    ]);
    const topArtistIds = artistSongStats.map((a) => a._id);
    const artistStatsMap = new Map(artistSongStats.map((a) => [String(a._id), a]));
    const popularArtistsRaw = await Artist.find({ _id: { $in: topArtistIds } })
      .select('name avatarUrl isVerified bio followersCount')
      .lean();

    const artistOrderMap = new Map();
    topArtistIds.forEach((id, idx) => artistOrderMap.set(String(id), idx));
    popularArtistsRaw.sort((a, b) => (artistOrderMap.get(String(a._id)) ?? 999) - (artistOrderMap.get(String(b._id)) ?? 999));

    const popularArtists = popularArtistsRaw.map((art) => ({
      ...art,
      songsCount: artistStatsMap.get(String(art._id))?.songCount || 0
    }));

    const attachLiked = (songs) =>
      songs.map((song) => ({
        ...formatSongArtistFields(song),
        isLiked: likedSongIds.has(song._id ? song._id.toString() : '')
      }));

    return {
      genres,
      trending: attachLiked(trendingSongs),
      newReleases: attachLiked(newReleases),
      madeForYou: attachLiked(madeForYouSongs),
      popularArtists,
      recentlyPlayed: attachLiked(recentSongs),
      likedSongAlbums,
      recentPlaylists,
      popularAlbums,
      featuredPlaylists: featuredPlaylists.slice(0, 10),
      topMixes: attachLiked(madeForYouSongs),
      popularRadios,
      recommendedToday: attachLiked(recommendedTodaySongs),
      listenAgain: attachLiked(listenAgainList),
      bestOfArtists: popularArtists
    };
  }

  /**
   * Lấy nghệ sĩ đang theo dõi và danh sách bài hát của họ cho mục "Đang theo dõi" trong tab Âm nhạc
   */
  static async getFollowedArtistsMusic(userId, { sort = 'newest' } = {}) {
    if (!userId) {
      return { artists: [], songs: [], totalArtists: 0, totalSongs: 0 };
    }

    const pref = await UserPreference.findOne({ userId }).select('favoriteArtistIds').lean();
    const followedIds = pref && Array.isArray(pref.favoriteArtistIds) ? pref.favoriteArtistIds : [];
    if (followedIds.length === 0) {
      return { artists: [], songs: [], totalArtists: 0, totalSongs: 0 };
    }

    const [artistsRaw, songsRaw, favorites] = await Promise.all([
      Artist.find({ _id: { $in: followedIds } })
        .select('name avatarUrl isVerified bio followersCount')
        .lean(),
      Song.find({
        isPublic: true,
        $or: [
          { artistId: { $in: followedIds } },
          { featuredArtistIds: { $in: followedIds } },
          { artistIds: { $in: followedIds } },
          { composerId: { $in: followedIds } },
          { composerIds: { $in: followedIds } }
        ]
      })
        .select('-embedding')
        .populate(SONG_POPULATE_SPEC)
        .lean(),
      UserFavorite.find({ userId }).select('songId').lean()
    ]);

    const followedSet = new Set(followedIds.map((id) => String(id)));
    const artistCountMap = new Map();
    const collectRoleIds = (val) => {
      if (!val) return [];
      return Array.isArray(val) ? val.map((v) => String(v._id || v)) : [String(val._id || val)];
    };

    for (const song of songsRaw) {
      const involved = new Set([
        ...collectRoleIds(song.artistId),
        ...collectRoleIds(song.featuredArtistIds),
        ...collectRoleIds(song.artistIds),
        ...collectRoleIds(song.composerId),
        ...collectRoleIds(song.composerIds)
      ]);
      for (const aid of involved) {
        if (followedSet.has(aid)) {
          artistCountMap.set(aid, (artistCountMap.get(aid) || 0) + 1);
        }
      }
    }

    const artists = artistsRaw
      .map((a) => ({
        ...a,
        isFollowed: true,
        songsCount: artistCountMap.get(String(a._id)) || 0
      }))
      .sort((a, b) => (b.songsCount - a.songsCount) || ((b.followersCount || 0) - (a.followersCount || 0)));

    const likedSongIds = new Set(favorites.map((f) => String(f.songId)));
    const mappedSongs = songsRaw.map((song) => ({
      ...formatSongArtistFields(song),
      isLiked: likedSongIds.has(String(song._id))
    }));

    if (sort === 'popular') {
      mappedSongs.sort((a, b) => (b.playsCount || 0) - (a.playsCount || 0));
    } else if (sort === 'az') {
      mappedSongs.sort((a, b) => (a.title || '').localeCompare(b.title || '', 'vi'));
    } else {
      mappedSongs.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
    }

    return {
      artists,
      songs: mappedSongs,
      totalArtists: artists.length,
      totalSongs: mappedSongs.length
    };
  }

  /**
   * Tìm kiếm toàn văn (Full-Text Search) theo tiêu đề, lời nhạc và nghệ sĩ (hỗ trợ tiếng Việt không dấu)
   */
  static async search(query, userId = null) {
    if (!query || query.trim() === '') {
      return { songs: [], artists: [], albums: [] };
    }

    const trimmed = query.trim();
    const regex = buildVietnameseRegex(trimmed);

    // 1. Tìm nghệ sĩ khớp từ khóa
    const artists = await Artist.find({
      name: { $regex: regex }
    })
      .limit(10)
      .lean();

    const matchingArtistIds = artists.map((a) => a._id);

    // 2. Tìm thể loại khớp từ khóa (hỗ trợ tên thể loại, slug và các alias viết tắt phổ biến)
    const normalizedQuery = trimmed.toLowerCase().replace(/[\s\-_/&]/g, '');
    const genreConditions = [];

    if (normalizedQuery === 'kpop' || normalizedQuery === 'k-pop') {
      genreConditions.push({ slug: 'k-pop' });
    } else if (normalizedQuery === 'usuk' || normalizedQuery === 'aumy' || normalizedQuery === 'popaumy' || normalizedQuery === 'popaumyusuk') {
      genreConditions.push({ slug: 'us-uk-pop' });
    } else if (normalizedQuery === 'vpop' || normalizedQuery === 'nhacviet') {
      genreConditions.push({ slug: 'v-pop' });
    } else if (normalizedQuery === 'lofi' || normalizedQuery === 'chill' || normalizedQuery === 'lofichill') {
      genreConditions.push({ slug: 'lo-fi-chill' });
    } else if (normalizedQuery === 'edm' || normalizedQuery === 'electronic' || normalizedQuery === 'electronicedm') {
      genreConditions.push({ slug: 'electronic-edm' });
    } else if (normalizedQuery === 'rap' || normalizedQuery === 'hiphop' || normalizedQuery === 'hiphoprap') {
      genreConditions.push({ slug: 'hip-hop-rap' });
    } else if (normalizedQuery === 'ballad' || normalizedQuery === 'indie' || normalizedQuery === 'indieballad') {
      genreConditions.push({ slug: 'indie-ballad' });
    } else if (normalizedQuery === 'cachmang' || normalizedQuery === 'truyenthong' || normalizedQuery === 'nhaccachmang') {
      genreConditions.push({ slug: 'nhac-cach-mang' });
    } else if (normalizedQuery === 'rnb' || normalizedQuery === 'soul' || normalizedQuery === 'rnbsoul') {
      genreConditions.push({ slug: 'r-and-b-soul' });
    } else {
      const escapedQuery = trimmed.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      genreConditions.push(
        { name: { $regex: new RegExp(escapedQuery, 'i') } },
        { slug: { $regex: new RegExp(escapedQuery, 'i') } }
      );
    }

    const matchingGenres = await Genre.find({ $or: genreConditions }).select('_id').lean();
    const matchingGenreIds = matchingGenres.map((g) => g._id);

    // 3. Tìm bài hát: Khớp tên bài, lời bài hát, nghệ sĩ trình bày HOẶC thể loại
    const songQueryConditions = [
      { title: { $regex: regex } },
      { lyrics: { $regex: regex } }
    ];

    if (matchingArtistIds.length > 0) {
      songQueryConditions.push(
        { artistId: { $in: matchingArtistIds } },
        { featuredArtistIds: { $in: matchingArtistIds } }
      );
    }

    if (matchingGenreIds.length > 0) {
      songQueryConditions.push(
        { genreIds: { $in: matchingGenreIds } }
      );
    }

    const songs = await Song.find({
      isPublic: true,
      $or: songQueryConditions
    })
      .select('-embedding')
      .limit(50)
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('featuredArtistIds', 'name avatarUrl')
      .populate('albumId', 'title coverImageUrl releaseYear')
      .populate('genreIds', 'name colorCode')
      .lean();

    // 3. Tìm album
    const albums = await Album.find({
      title: { $regex: regex }
    })
      .limit(10)
      .populate('artistId', 'name')
      .lean();

    // Gắn trạng thái isLiked nếu có userId
    let likedSongIds = new Set();
    if (userId) {
      const favorites = await UserFavorite.find({ userId }).select('songId').lean();
      likedSongIds = new Set(favorites.map((f) => f.songId.toString()));
    }

    const mappedSongs = songs.map((s) => ({
      ...formatSongArtistFields(s),
      isLiked: likedSongIds.has(s._id.toString())
    }));

    return {
      songs: mappedSongs,
      artists,
      albums
    };
  }

  /**
   * Lấy chi tiết bài hát theo ID
   */
  static async getSongById(songId, userId = null) {
    if (!mongoose.Types.ObjectId.isValid(songId)) {
      throw ApiError.badRequest('Invalid song ID');
    }

    const song = await Song.findById(songId)
      .populate('artistId', 'name slug avatarUrl bio followersCount isVerified')
      .populate('composerId', 'name slug avatarUrl bio followersCount isVerified')
      .populate('featuredArtistIds', 'name avatarUrl')
      .populate('albumId', 'title coverImageUrl releaseYear')
      .populate('genreIds', 'name colorCode')
      .lean();

    if (!song) {
      throw ApiError.notFound('Song not found');
    }

    let isLiked = false;
    if (userId) {
      const favorite = await UserFavorite.findOne({ userId, songId });
      isLiked = !!favorite;
    }

    return {
      ...song,
      isLiked
    };
  }

  /**
   * Lấy danh sách bài hát theo Thể loại (Genre)
   * Hỗ trợ sort:
   * - popular: Nhiều người nghe nhất đến ít người nghe (playsCount: -1)
   * - user_plays: Hay nghe nhất đến ít nghe nhất của người dùng (từ ListeningHistory)
   * - recommended: Gợi ý hôm nay (kết hợp deterministic daily seed theo ngày)
   * - az: Sắp xếp theo tên bài hát A-Z
   * - newest: Bài hát mới nhất
   */
  static async getSongsByGenre(genreId, { page = 1, limit = 50, sort = 'popular', userId = null } = {}) {
    if (!mongoose.Types.ObjectId.isValid(genreId)) {
      throw ApiError.badRequest('Invalid genre ID');
    }

    const genre = await Genre.findById(genreId);
    if (!genre) {
      throw ApiError.notFound('Genre not found');
    }

    let sortOption = { playsCount: -1, createdAt: -1 };
    if (sort === 'az') {
      sortOption = { title: 1 };
    } else if (sort === 'newest') {
      sortOption = { createdAt: -1 };
    }

    let songs = await Song.find({ genreIds: genreId, isPublic: true })
      .select('-embedding')
      .sort(sortOption)
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('featuredArtistIds', 'name avatarUrl isVerified')
      .populate('artistIds', 'name avatarUrl isVerified')
      .populate('composerId', 'name avatarUrl isVerified')
      .populate('composerIds', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    // Lấy danh sách bài hát đã like của user nếu có
    let likedSongIds = new Set();
    if (userId) {
      const favorites = await UserFavorite.find({ userId }).select('songId').lean();
      likedSongIds = new Set(favorites.map((f) => f.songId.toString()));
    }

    // Nếu sort = 'user_plays' (hay nghe nhất của người dùng)
    if (sort === 'user_plays' && userId) {
      const historyStats = await ListeningHistory.aggregate([
        { $match: { userId: new mongoose.Types.ObjectId(userId) } },
        { $group: { _id: '$songId', userPlayCount: { $sum: 1 } } }
      ]);
      const userPlayMap = new Map();
      historyStats.forEach((h) => userPlayMap.set(String(h._id), h.userPlayCount));

      songs.sort((a, b) => {
        const countA = userPlayMap.get(String(a._id)) || 0;
        const countB = userPlayMap.get(String(b._id)) || 0;
        if (countB !== countA) return countB - countA;
        return (b.playsCount || 0) - (a.playsCount || 0);
      });
    } else if (sort === 'recommended') {
      // Gợi ý hôm nay: dùng seed ngày hôm nay để xáo trộn có định hướng
      const todayStr = new Date().toISOString().slice(0, 10);
      let seed = 0;
      for (let i = 0; i < todayStr.length; i++) {
        seed = (seed * 31 + todayStr.charCodeAt(i)) & 0xffffffff;
      }
      songs.sort((a, b) => {
        const hashA = Math.sin(seed + (a._id.toString().charCodeAt(10) || 1)) * 10000;
        const hashB = Math.sin(seed + (b._id.toString().charCodeAt(10) || 1)) * 10000;
        const pseudoRandomA = hashA - Math.floor(hashA);
        const pseudoRandomB = hashB - Math.floor(hashB);
        const scoreA = pseudoRandomA * 0.6 + Math.min(1, (a.playsCount || 0) / 1000000) * 0.4;
        const scoreB = pseudoRandomB * 0.6 + Math.min(1, (b.playsCount || 0) / 1000000) * 0.4;
        return scoreB - scoreA;
      });
    }

    const mappedSongs = songs.map((s) => ({
      ...formatSongArtistFields(s),
      isLiked: likedSongIds.has(s._id.toString())
    }));

    const total = mappedSongs.length;
    const startIndex = (page - 1) * limit;
    const paginatedSongs = mappedSongs.slice(startIndex, startIndex + limit);

    return {
      genre,
      songs: paginatedSongs,
      total,
      sort
    };
  }

  /**
   * Lấy danh sách bài hát theo Ca sĩ (Artist)
   * Bao gồm cả bài hát nghệ sĩ là ca sĩ chính HOẶC ca sĩ hợp tác (featured)
   */
  static async getSongsByArtist(artistId, userId = null, sort = 'popular') {
    if (!mongoose.Types.ObjectId.isValid(artistId)) {
      throw ApiError.badRequest('Invalid artist ID');
    }

    const artist = await Artist.findById(artistId).lean();
    if (!artist) {
      throw ApiError.notFound('Artist not found');
    }

    const targetIdStr = artistId.toString();
    const songs = await Song.find({
      $or: [
        { artistId: artistId },
        { featuredArtistIds: artistId },
        { artistIds: artistId },
        { composerId: artistId },
        { composerIds: artistId }
      ],
      isPublic: true
    })
      .sort({ playsCount: -1 })
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('composerId', 'name avatarUrl isVerified')
      .populate('composerIds', 'name avatarUrl isVerified')
      .populate('featuredArtistIds', 'name avatarUrl')
      .populate('genreIds', 'name colorCode')
      .populate('albumId', 'title coverImageUrl')
      .lean();

    let likedSongIds = new Set();
    if (userId) {
      const favorites = await UserFavorite.find({ userId }).select('songId').lean();
      likedSongIds = new Set(favorites.map((f) => f.songId.toString()));
    }

    const mappedSongs = songs.map((s) => {
      const isPrimary = s.artistId && (s.artistId._id ? s.artistId._id.toString() : s.artistId.toString()) === targetIdStr;
      const isFeatured = s.featuredArtistIds && s.featuredArtistIds.some(f => (f._id ? f._id.toString() : f.toString()) === targetIdStr);
      const isComposer = (s.composerId && (s.composerId._id ? s.composerId._id.toString() : s.composerId.toString()) === targetIdStr) ||
                         (s.composerIds && s.composerIds.some(c => (c._id ? c._id.toString() : c.toString()) === targetIdStr));

      let roleInSong = 'primary_artist';
      if (isPrimary && isComposer) {
        roleInSong = 'artist_and_composer';
      } else if (isPrimary) {
        roleInSong = 'primary_artist';
      } else if (isFeatured && isComposer) {
        roleInSong = 'featured_and_composer';
      } else if (isFeatured) {
        roleInSong = 'featured_artist';
      } else if (isComposer) {
        roleInSong = 'composer';
      }

      let artistName = s.artistId?.name || '';
      if (s.featuredArtistIds && s.featuredArtistIds.length > 0) {
        const featNames = s.featuredArtistIds.map(f => (f && typeof f === 'object' && typeof f.name === 'string' ? f.name.trim() : '')).filter(Boolean);
        if (featNames.length > 0) {
          artistName = `${artistName} ft. ${featNames.join(', ')}`;
        }
      }

      return {
        ...s,
        artistName: artistName || s.artistName,
        roleInSong,
        isLiked: likedSongIds.has(s._id.toString())
      };
    });

    if (sort === 'az') {
      mappedSongs.sort((a, b) => (a.title || '').localeCompare(b.title || '', 'vi'));
    } else if (sort === 'newest') {
      mappedSongs.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
    } else if (sort === 'favorite') {
      mappedSongs.sort((a, b) => {
        if (a.isLiked !== b.isLiked) return a.isLiked ? -1 : 1;
        return (b.likesCount || 0) - (a.likesCount || 0);
      });
    } else {
      mappedSongs.sort((a, b) => (b.playsCount || 0) - (a.playsCount || 0));
    }

    const rawAlbums = await Album.find({ artistId })
      .populate('artistId', 'name avatarUrl')
      .sort({ releaseYear: -1 })
      .lean();

    const albums = await AlbumService.attachLiveAlbumTrackCounts(rawAlbums);

    let isFollowed = false;
    if (userId) {
      const userPref = await UserPreference.findOne({ userId }).select('favoriteArtistIds').lean();
      if (userPref && Array.isArray(userPref.favoriteArtistIds)) {
        isFollowed = userPref.favoriteArtistIds.some((id) => id.toString() === artistId.toString());
      }
    }

    return {
      artist: {
        ...artist,
        isFollowed
      },
      songs: mappedSongs,
      totalSongs: mappedSongs.length,
      albums
    };
  }

  /**
   * Toggle yêu thích bài hát (Favorite/Like O(1))
   */
  static async toggleFavorite(userId, songId) {
    if (!mongoose.Types.ObjectId.isValid(songId)) {
      throw ApiError.badRequest('Invalid song ID');
    }

    const song = await Song.findById(songId);
    if (!song) {
      throw ApiError.notFound('Song not found');
    }

    const existing = await UserFavorite.findOne({ userId, songId });

    if (existing) {
      // Đã like -> Huỷ like
      await UserFavorite.deleteOne({ _id: existing._id });
      await Song.findByIdAndUpdate(songId, { $inc: { likesCount: -1 } });
      return { isLiked: false, songId, likesCount: Math.max(0, song.likesCount - 1) };
    } else {
      // Chưa like -> Tạo like
      await UserFavorite.create({ userId, songId });
      await Song.findByIdAndUpdate(songId, { $inc: { likesCount: 1 } });
      return { isLiked: true, songId, likesCount: song.likesCount + 1 };
    }
  }

  /**
   * Lấy danh sách bài hát yêu thích của người dùng
   */
  static async getUserFavorites(userId, { page = 1, limit = 50 } = {}) {
    const skip = (page - 1) * limit;

    const favorites = await UserFavorite.find({ userId })
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .populate({
        path: 'songId',
        populate: [
          { path: 'artistId', select: 'name avatarUrl isVerified' },
          { path: 'featuredArtistIds', select: 'name avatarUrl isVerified' },
          { path: 'composerId', select: 'name avatarUrl isVerified' },
          { path: 'composerIds', select: 'name avatarUrl isVerified' },
          { path: 'albumId', select: 'title coverImageUrl' },
          { path: 'genreIds', select: 'name colorCode' }
        ]
      })
      .lean();

    const total = await UserFavorite.countDocuments({ userId });

    // Lọc bỏ trường hợp bài hát đã bị xoá khỏi hệ thống
    const songs = favorites
      .filter((fav) => fav.songId != null)
      .map((fav) => ({
        ...formatSongArtistFields(fav.songId),
        favoritedAt: fav.createdAt,
        isLiked: true
      }));

    return {
      songs,
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit)
      }
    };
  }

  /**
   * Ghi nhận lịch sử nghe nhạc (Listening History Log)
   */
  static async logPlayback(userId, { songId, listenDuration, completed = false, skipped = false, playbackSource = 'home_feed' }) {
    if (!mongoose.Types.ObjectId.isValid(songId)) {
      throw ApiError.badRequest('Invalid song ID');
    }

    // 1. Tăng lượt nghe trong Song collection
    await Song.findByIdAndUpdate(songId, { $inc: { playsCount: 1 } });

    // 2. Nếu có user, lưu bản ghi chi tiết phục vụ AI Recommender
    let historyRecord = null;
    if (userId) {
      historyRecord = await ListeningHistory.create({
        userId,
        songId,
        listenDuration: Number(listenDuration) || 0,
        completed: Boolean(completed),
        skipped: Boolean(skipped),
        playbackSource,
        timestamp: new Date()
      });
    }

    return { success: true, historyId: historyRecord ? historyRecord._id : null };
  }
}

module.exports = SongService;
