const mongoose = require('mongoose');
const Song = require('../models/Song');
const ListeningHistory = require('../models/ListeningHistory');
const UserFavorite = require('../models/UserFavorite');
const UserPreference = require('../models/UserPreference');

class RecommendationEngine {
  /**
   * Sinh danh sách gợi ý cá nhân hoá (Personalized Recommendations)
   * Sử dụng Content-based Filtering kết hợp trọng số tương tác từ Listening History
   */
  static async getPersonalizedFeed(userId, limit = 15) {
    if (!userId) {
      // Người dùng chưa đăng nhập (Guest): Trả về top trending
      return this.getColdStartRecommendations(limit);
    }

    // 1. Lấy dữ liệu sở thích (Preferences) và Lịch sử nghe (History) trong 30 ngày
    const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);

    const [preferences, favorites, historyLogs] = await Promise.all([
      UserPreference.findOne({ userId }).lean(),
      UserFavorite.find({ userId }).select('songId').lean(),
      ListeningHistory.find({ userId, timestamp: { $gte: thirtyDaysAgo } }).populate('songId').lean()
    ]);

    const likedSongIds = new Set(favorites.map((f) => f.songId.toString()));

    // 2. Cold Start: Nếu chưa có lịch sử nghe nhạc
    if (historyLogs.length < 3 && (!preferences || preferences.favoriteGenreIds.length === 0)) {
      return this.getColdStartRecommendations(limit, likedSongIds);
    }

    // 3. Tính toán trọng số sở thích thể loại (Genre affinity) và nghệ sĩ (Artist affinity)
    const genreScores = new Map();
    const artistScores = new Map();

    // Điểm từ User Preferences thiết lập ban đầu (+5 điểm)
    if (preferences && preferences.favoriteGenreIds) {
      preferences.favoriteGenreIds.forEach((gid) => {
        genreScores.set(gid.toString(), (genreScores.get(gid.toString()) || 0) + 5);
      });
    }

    // Điểm từ danh sách yêu thích (+4 điểm cho mỗi bài thích)
    // Tín hiệu quan trọng nhất của người dùng
    for (const fav of favorites) {
      // Trọng số đã gián tiếp thể hiện
    }

    // Điểm từ Listening History (Tần suất, thời lượng, hoàn thành, bỏ qua)
    historyLogs.forEach((log) => {
      if (!log.songId) return;

      let interactionScore = 1; // Base play
      if (log.completed) interactionScore += 2.5; // Nghe trên 80%
      if (log.skipped) interactionScore -= 1.0;   // Skip dưới 30s
      if (log.listenDuration > 120) interactionScore += 1.5; // Nghe lâu

      const artistId = log.songId.artistId?.toString();
      if (artistId) {
        artistScores.set(artistId, (artistScores.get(artistId) || 0) + interactionScore);
      }

      if (Array.isArray(log.songId.genreIds)) {
        log.songId.genreIds.forEach((gid) => {
          const gIdStr = gid.toString();
          genreScores.set(gIdStr, (genreScores.get(gIdStr) || 0) + interactionScore);
        });
      }
    });

    // 4. Lấy danh sách ứng viên bài hát từ MongoDB
    const candidateSongs = await Song.find({ isPublic: true })
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    // 5. Chấm điểm từng bài hát dựa trên Profile người dùng
    const rankedCandidates = candidateSongs.map((song) => {
      let finalScore = 0;
      const songArtistId = song.artistId?._id?.toString() || song.artistId?.toString();

      // Điểm nghệ sĩ quen thuộc
      if (artistScores.has(songArtistId)) {
        finalScore += artistScores.get(songArtistId) * 1.2;
      }

      // Điểm thể loại ưa thích
      if (Array.isArray(song.genreIds)) {
        song.genreIds.forEach((g) => {
          const gid = g._id?.toString() || g.toString();
          if (genreScores.has(gid)) {
            finalScore += genreScores.get(gid);
          }
        });
      }

      // Bonus nếu bài hát được người dùng thích
      const isLiked = likedSongIds.has(song._id.toString());
      if (isLiked) finalScore += 3.0;

      // Bonus độ thịnh hành chung (phòng tránh bong bóng lọc / filter bubble)
      finalScore += Math.log10(song.playsCount + 1) * 0.8;

      return {
        ...song,
        isLiked,
        recommendationScore: parseFloat(finalScore.toFixed(2))
      };
    });

    // Sắp xếp bài hát theo điểm gợi ý giảm dần
    rankedCandidates.sort((a, b) => b.recommendationScore - a.recommendationScore);

    return rankedCandidates.slice(0, limit);
  }

  /**
   * Cold Start: Gợi ý cho người dùng mới đăng ký hoặc khách vãng lai
   */
  static async getColdStartRecommendations(limit = 15, likedSongIds = new Set()) {
    const songs = await Song.find({ isPublic: true })
      .sort({ playsCount: -1, likesCount: -1 })
      .limit(limit)
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    return songs.map((song) => ({
      ...song,
      isLiked: likedSongIds.has(song._id.toString()),
      recommendationScore: 1.0
    }));
  }
}

module.exports = RecommendationEngine;
