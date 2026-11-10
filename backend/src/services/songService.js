const mongoose = require('mongoose');
const Song = require('../models/Song');
const Artist = require('../models/Artist');
const Album = require('../models/Album');
const Genre = require('../models/Genre');
const UserFavorite = require('../models/UserFavorite');
const ListeningHistory = require('../models/ListeningHistory');
const Playlist = require('../models/Playlist');
const ApiError = require('../utils/ApiError');

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
   * Lấy dữ liệu tổng hợp cho Màn hình Home Feed (Spotify Home Style)
   */
  static async getHomeFeed(userId = null) {
    // 1. Lấy danh sách thể loại (Genres) kèm mã màu
    const genres = await Genre.find().lean();

    // 2. Lấy Top bài hát thịnh hành (Trending - sắp xếp theo playsCount)
    const trendingSongs = await Song.find({ isPublic: true })
      .sort({ playsCount: -1 })
      .limit(10)
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    // 3. Lấy Bài hát mới phát hành (New Releases)
    const newReleases = await Song.find({ isPublic: true })
      .sort({ createdAt: -1 })
      .limit(10)
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('genreIds', 'name colorCode')
      .lean();

    // 4. Lấy danh sách nghệ sĩ nổi bật (ưu tiên các nghệ sĩ có bài hát trong hệ thống)
    const activeArtistIds = await Song.distinct('artistId', { isPublic: true });
    let popularArtists = await Artist.find({ _id: { $in: activeArtistIds } })
      .sort({ followersCount: -1 })
      .limit(20)
      .lean();

    if (popularArtists.length < 8) {
      const moreArtists = await Artist.find({ _id: { $nin: activeArtistIds } })
        .sort({ followersCount: -1 })
        .limit(8 - popularArtists.length)
        .lean();
      popularArtists = [...popularArtists, ...moreArtists];
    }

    // 5. Lấy danh sách Album nổi tiếng
    const popularAlbums = await Album.find()
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('genreIds', 'name colorCode')
      .sort({ totalTracks: -1, createdAt: -1 })
      .limit(10)
      .lean();

    // 6. Lấy danh sách Playlist thịnh hành
    const featuredPlaylists = await Playlist.find({ isPublic: true })
      .sort({ totalTracks: -1, createdAt: -1 })
      .limit(10)
      .populate('creatorId', 'displayName avatarUrl')
      .lean();

    // 7. Lấy danh sách bài hát nghe gần đây (Recently Played)
    let recentSongs = [];
    if (userId) {
      const historyRecords = await ListeningHistory.find({ userId })
        .sort({ timestamp: -1 })
        .limit(20)
        .populate({
          path: 'songId',
          populate: [
            { path: 'artistId', select: 'name avatarUrl isVerified' },
            { path: 'albumId', select: 'title coverImageUrl' },
            { path: 'genreIds', select: 'name colorCode' }
          ]
        })
        .lean();

      const seenIds = new Set();
      for (const h of historyRecords) {
        if (h.songId && !seenIds.has(h.songId._id.toString())) {
          seenIds.add(h.songId._id.toString());
          recentSongs.push(h.songId);
        }
      }
    }

    // Nếu chưa có lịch sử (hoặc khách), lấy bài hát thịnh hành/mới làm gợi ý ban đầu
    if (recentSongs.length === 0) {
      recentSongs = trendingSongs.slice(0, 6);
    }

    // 8. Nếu user đã đăng nhập, gắn cờ isLiked vào các bài hát
    let likedSongIds = new Set();
    if (userId) {
      const favorites = await UserFavorite.find({ userId }).select('songId').lean();
      likedSongIds = new Set(favorites.map((f) => f.songId.toString()));
    }

    const attachLiked = (songs) =>
      songs.map((song) => ({
        ...song,
        isLiked: likedSongIds.has(song._id ? song._id.toString() : '')
      }));

    return {
      genres,
      trending: attachLiked(trendingSongs),
      newReleases: attachLiked(newReleases),
      popularArtists,
      recentlyPlayed: attachLiked(recentSongs),
      popularAlbums,
      featuredPlaylists
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
      ...s,
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
   */
  static async getSongsByGenre(genreId, { page = 1, limit = 20 } = {}) {
    if (!mongoose.Types.ObjectId.isValid(genreId)) {
      throw ApiError.badRequest('Invalid genre ID');
    }

    const genre = await Genre.findById(genreId);
    if (!genre) {
      throw ApiError.notFound('Genre not found');
    }

    const skip = (page - 1) * limit;
    const songs = await Song.find({ genreIds: genreId, isPublic: true })
      .sort({ playsCount: -1 })
      .skip(skip)
      .limit(limit)
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('albumId', 'title')
      .lean();

    const total = await Song.countDocuments({ genreIds: genreId, isPublic: true });

    return {
      genre,
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
   * Lấy danh sách bài hát theo Ca sĩ (Artist)
   * Bao gồm cả bài hát nghệ sĩ là ca sĩ chính HOẶC ca sĩ hợp tác (featured)
   */
  static async getSongsByArtist(artistId, userId = null) {
    if (!mongoose.Types.ObjectId.isValid(artistId)) {
      throw ApiError.badRequest('Invalid artist ID');
    }

    const artist = await Artist.findById(artistId).lean();
    if (!artist) {
      throw ApiError.notFound('Artist not found');
    }

    const songs = await Song.find({
      $or: [
        { artistId: artistId },
        { featuredArtistIds: artistId }
      ],
      isPublic: true
    })
      .sort({ playsCount: -1 })
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('featuredArtistIds', 'name avatarUrl')
      .populate('genreIds', 'name colorCode')
      .populate('albumId', 'title coverImageUrl')
      .lean();

    let likedSongIds = new Set();
    if (userId) {
      const favorites = await UserFavorite.find({ userId }).select('songId').lean();
      likedSongIds = new Set(favorites.map((f) => f.songId.toString()));
    }

    const mappedSongs = songs.map((s) => ({
      ...s,
      isLiked: likedSongIds.has(s._id.toString())
    }));

    const rawAlbums = await Album.find({ artistId })
      .populate('artistId', 'name avatarUrl')
      .sort({ releaseYear: -1 })
      .lean();

    const albums = await Promise.all(
      rawAlbums.map(async (album) => {
        let count = album.totalTracks;
        if (!count || count <= 0) {
          count = await Song.countDocuments({ albumId: album._id, isPublic: true });
        }
        return {
          ...album,
          totalTracks: count
        };
      })
    );

    return {
      artist,
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
        ...fav.songId,
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
