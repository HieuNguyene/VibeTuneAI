const { Artist, Song, UserPreference } = require('../models');
const ApiResponse = require('../utils/ApiResponse');
const SongService = require('../services/songService');

async function getArtistSongCountMap() {
  const stats = await Song.aggregate([
    { $match: { isPublic: true } },
    {
      $project: {
        allArtists: {
          $setUnion: [
            { $cond: [{ $ifNull: ['$artistId', false] }, ['$artistId'], []] },
            { $ifNull: ['$featuredArtistIds', []] },
            { $ifNull: ['$artistIds', []] },
            { $cond: [{ $ifNull: ['$composerId', false] }, ['$composerId'], []] },
            { $ifNull: ['$composerIds', []] }
          ]
        }
      }
    },
    { $unwind: '$allArtists' },
    { $group: { _id: '$allArtists', count: { $sum: 1 } } }
  ]);
  return new Map(stats.map((s) => [String(s._id), s.count]));
}

class ArtistController {
  /**
   * Lấy danh sách nghệ sĩ (hỗ trợ phân trang, tìm kiếm, lọc theo thể loại, sắp xếp theo followers)
   */
  static async getAllArtists(req, res, next) {
    try {
      const page = Math.max(1, parseInt(req.query.page, 10) || 1);
      const limit = Math.min(100, Math.max(1, parseInt(req.query.limit, 10) || 50));
      const skip = (page - 1) * limit;
      const { q, genreId, sort = 'followers' } = req.query;

      const query = {};
      if (q && q.trim()) {
        const escapedQ = q.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        query.name = { $regex: escapedQ, $options: 'i' };
      }
      if (genreId) {
        query.genreIds = genreId;
      }

      const sortOptions = sort === 'name' ? { name: 1 } : { followersCount: -1 };

      const [artists, total, songCountMap] = await Promise.all([
        Artist.find(query)
          .populate('genreIds', 'name slug colorCode')
          .sort(sortOptions)
          .skip(skip)
          .limit(limit)
          .lean(),
        Artist.countDocuments(query),
        getArtistSongCountMap()
      ]);

      // Bổ sung số lượng bài hát của từng nghệ sĩ bằng single-aggregation lookup
      const artistsWithSongCount = artists.map((art) => ({
        ...art,
        songsCount: songCountMap.get(String(art._id)) || 0
      }));

      return ApiResponse.success(
        res,
        {
          artists: artistsWithSongCount,
          pagination: {
            page,
            limit,
            total,
            totalPages: Math.ceil(total / limit)
          }
        },
        'Lấy danh sách nghệ sĩ thành công'
      );
    } catch (error) {
      next(error);
    }
  }

  /**
   * Lấy danh sách nghệ sĩ cho Thư viện (Library)
   * Hiển thị nghệ sĩ người dùng đã follow kèm top nghệ sĩ nổi bật có bài hát trong hệ thống
   */
  static async getLibraryArtists(req, res, next) {
    try {
      const userId = req.user ? req.user._id : null;
      let favoriteArtistIds = [];

      if (userId) {
        const pref = await UserPreference.findOne({ userId }).select('favoriteArtistIds').lean();
        if (pref && Array.isArray(pref.favoriteArtistIds)) {
          favoriteArtistIds = pref.favoriteArtistIds.map((id) => id.toString());
        }
      }

      const favSet = new Set(favoriteArtistIds);

      // 1. Lấy danh sách nghệ sĩ đã follow
      let followedArtists = [];
      if (favoriteArtistIds.length > 0) {
        followedArtists = await Artist.find({ _id: { $in: favoriteArtistIds } })
          .populate('genreIds', 'name slug colorCode')
          .lean();
      }

      const followedOnly = req.query.followedOnly === 'true';
      let combinedArtists = followedArtists;

      if (!followedOnly) {
        // 2. Lấy thêm top nghệ sĩ nổi bật có bài hát trong hệ thống
        const followedIdList = followedArtists.map((a) => a._id);
        const popularArtists = await Artist.find({ _id: { $nin: followedIdList } })
          .populate('genreIds', 'name slug colorCode')
          .sort({ followersCount: -1 })
          .limit(100)
          .lean();

        // Kết hợp: followed xếp trước, sau đó là popular
        combinedArtists = [...followedArtists, ...popularArtists];
      }

      const songCountMap = await getArtistSongCountMap();

      // Gắn songCount & ưu tiên nghệ sĩ có bài hát thật trong hệ thống lên trước
      const mapped = combinedArtists.map((art) => ({
        ...art,
        songsCount: songCountMap.get(String(art._id)) || 0,
        isFollowed: favSet.has(art._id.toString())
      }));

      const result = followedOnly
        ? mapped
        : [
            ...mapped.filter((a) => a.isFollowed),
            ...mapped
              .filter((a) => !a.isFollowed)
              .sort((a, b) => {
                const hasA = a.songsCount > 0 ? 1 : 0;
                const hasB = b.songsCount > 0 ? 1 : 0;
                if (hasB !== hasA) return hasB - hasA;
                return (b.followersCount || 0) - (a.followersCount || 0);
              })
          ];

      return ApiResponse.success(res, result, 'Lấy danh sách nghệ sĩ thư viện thành công');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Toggle theo dõi (Follow/Unfollow) nghệ sĩ
   */
  static async toggleFollowArtist(req, res, next) {
    try {
      const { id } = req.params;
      const userId = req.user._id;

      const artist = await Artist.findById(id);
      if (!artist) {
        return ApiResponse.notFound(res, 'Không tìm thấy nghệ sĩ');
      }

      let pref = await UserPreference.findOne({ userId });
      if (!pref) {
        pref = new UserPreference({ userId, favoriteArtistIds: [] });
      }

      const strId = artist._id.toString();
      const existingIndex = pref.favoriteArtistIds.findIndex((aId) => aId.toString() === strId);
      let isFollowed = false;

      if (existingIndex > -1) {
        pref.favoriteArtistIds.splice(existingIndex, 1);
        artist.followersCount = Math.max(0, (artist.followersCount || 1) - 1);
        isFollowed = false;
      } else {
        pref.favoriteArtistIds.push(artist._id);
        artist.followersCount = (artist.followersCount || 0) + 1;
        isFollowed = true;
      }

      await Promise.all([pref.save(), artist.save()]);

      return ApiResponse.success(
        res,
        {
          isFollowed,
          followersCount: artist.followersCount
        },
        isFollowed ? `Đã theo dõi ${artist.name}` : `Đã hủy theo dõi ${artist.name}`
      );
    } catch (error) {
      next(error);
    }
  }

  /**
   * Lấy chi tiết nghệ sĩ và toàn bộ bài hát của nghệ sĩ đó
   */
  static async getArtistById(req, res, next) {
    try {
      const { id } = req.params;
      const userId = req.user ? req.user._id : null;
      const result = await SongService.getSongsByArtist(id, userId);
      return ApiResponse.success(res, result, 'Lấy chi tiết nghệ sĩ thành công');
    } catch (error) {
      next(error);
    }
  }

  /**
   * Lấy danh sách bài hát theo ID nghệ sĩ (RESTful endpoint: GET /artists/:id/songs)
   */
  static async getArtistSongs(req, res, next) {
    try {
      const { id } = req.params;
      const userId = req.user ? req.user._id : null;
      const result = await SongService.getSongsByArtist(id, userId);
      return ApiResponse.success(res, result, 'Lấy danh sách bài hát của nghệ sĩ thành công');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = ArtistController;
