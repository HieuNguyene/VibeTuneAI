const mongoose = require('mongoose');
const Song = require('../models/Song');
const Artist = require('../models/Artist');
const Album = require('../models/Album');
const Genre = require('../models/Genre');
const Playlist = require('../models/Playlist');
const PlaylistTrack = require('../models/PlaylistTrack');
const AiPromptLog = require('../models/AiPromptLog');

class PlaylistGenerator {
  /**
   * Truy vấn và xếp hạng bài hát thực tế từ MongoDB dựa trên tiêu chí trích xuất
   */
  static async retrieveAndRankSongs(criteria, limit = 15) {
    const { mood = [], genres = [], tempo, keywords = [] } = criteria;

    const escapeRegex = (str) => String(str).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

    const normalizedRegex = (val) => new RegExp(escapeRegex(val), 'i');

    // 1. Tìm ObjectIds của các thể loại tương ứng
    const genreDocs = await Genre.find({
      name: { $in: genres.map((g) => normalizedRegex(g)) }
    }).select('_id name');
    const targetGenreIds = genreDocs.map((g) => g._id);

    // 2. Query ứng viên từ MongoDB
    // Lấy bài hát có ít nhất 1 đặc tính khớp (mood hoặc genre)
    let queryConditions = [{ isPublic: true }];
    const orConditions = [];

    if (mood.length > 0) {
      orConditions.push({
        $or: [
          { moods: { $in: mood } },
          { mood: { $in: mood } }
        ]
      });
    }
    if (targetGenreIds.length > 0) {
      orConditions.push({ genreIds: { $in: targetGenreIds } });
    }
    if (tempo) {
      orConditions.push({ tempo });
    }

    if (orConditions.length > 0) {
      queryConditions.push({ $or: orConditions });
    }

    let candidateSongs = await Song.find({ $and: queryConditions })
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('featuredArtistIds', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    // Fallback: Nếu không đủ bài hát khớp chính xác, lấy thêm bài hát phổ biến nhất
    if (candidateSongs.length < limit) {
      const extraSongs = await Song.find({
        isPublic: true,
        _id: { $nin: candidateSongs.map((s) => s._id) }
      })
        .sort({ playsCount: -1 })
        .limit(limit - candidateSongs.length)
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('featuredArtistIds', 'name avatarUrl isVerified')
        .populate('albumId', 'title coverImageUrl')
        .populate('genreIds', 'name colorCode')
        .lean();

      candidateSongs = [...candidateSongs, ...extraSongs];
    }

    // 3. Tính điểm phù hợp (Match Scoring Algorithm)
    const scoredSongs = candidateSongs.map((song) => {
      let score = 0;

      // Khớp Mood: +3 điểm mỗi mood trùng
      if (Array.isArray(song.mood) && Array.isArray(mood)) {
        const moodMatches = song.mood.filter((m) => mood.includes(m)).length;
        score += moodMatches * 3;
      }

      // Khớp Genre: +3 điểm
      if (Array.isArray(song.genreIds)) {
        const songGenreIdStrs = song.genreIds.map((g) => g._id.toString());
        const hasGenreMatch = targetGenreIds.some((tg) => songGenreIdStrs.includes(tg.toString()));
        if (hasGenreMatch) score += 3;
      }

      // Khớp Tempo: +1.5 điểm
      if (tempo && song.tempo === tempo) {
        score += 1.5;
      }

      // Khớp Keyword trong Title hoặc Lyrics: +2 điểm
      if (Array.isArray(keywords)) {
        keywords.forEach((kw) => {
          const safeKw = escapeRegex(String(kw || '').trim());
          if (!safeKw) return;
          const kwRegex = new RegExp(safeKw, 'i');
          if (kwRegex.test(song.title)) score += 2;
          if (song.lyrics && kwRegex.test(song.lyrics)) score += 1;
        });
      }

      // Khớp Đặc tính âm thanh thực tế (Audio Features Constraints)
      const features = song.audioFeatures || {};
      const isCalmOrSad = mood.some((m) => ['sad', 'chill', 'relax', 'acoustic', 'ballad', 'rain', 'heartbroken'].includes(m.toLowerCase()));
      const isHighEnergy = mood.some((m) => ['energetic', 'party', 'trap', 'hype', 'dance', 'gym'].includes(m.toLowerCase()));

      if (isCalmOrSad) {
        if (typeof features.energy === 'number' && features.energy > 0.65) score -= 6.0; // Phạt nặng nếu nhạc quá sôi động
        if (typeof features.acousticness === 'number' && features.acousticness > 0.5) score += 3.0; // Ưu tiên nhạc mộc acoustic
        if (typeof features.valence === 'number' && features.valence < 0.45) score += 2.0; // Ưu tiên giai điệu trầm buồn
      } else if (isHighEnergy) {
        if (typeof features.energy === 'number' && features.energy < 0.50) score -= 6.0; // Phạt nặng nếu nhạc quá êm dịu/buồn
        if (typeof features.energy === 'number' && features.energy > 0.70) score += 3.0;
        if (typeof features.danceability === 'number' && features.danceability > 0.70) score += 2.5;
      }

      // Độ phổ biến (Popularity bonus): chuẩn hoá 0 - 0.5 điểm (giảm ảnh hưởng để tránh chiếm ưu thế tuyệt đối)
      score += Math.min(song.playsCount / 20000, 0.5);

      // Yếu tố Đa dạng hóa ngẫu nhiên (Random Exploration Jitter: 0 - 2.5 điểm)
      // Giúp mỗi lần tạo playlist cùng một prompt sẽ mang lại danh sách bài phong phú, không bị rập khuôn
      score += Math.random() * 2.5;

      return {
        ...song,
        matchScore: parseFloat(score.toFixed(2))
      };
    });

    // 4. Sắp xếp điểm số từ cao xuống thấp và cắt đúng số lượng
    scoredSongs.sort((a, b) => b.matchScore - a.matchScore);
    return scoredSongs.slice(0, limit);
  }

  /**
   * Tạo Playlist thực tế và lưu vào MongoDB
   */
  static async persistAiPlaylist(userId, criteria, songs, aiLogData) {
    // 1. Tạo Playlist document
    const playlist = await Playlist.create({
      title: criteria.title || 'AI Mix',
      description: criteria.description || 'Generated by AI',
      creatorId: userId,
      isAiGenerated: true,
      aiPrompt: aiLogData.userPrompt,
      coverImageUrl: songs.length > 0 ? songs[0].coverImageUrl : '/uploads/playlists/default_music_cover.jpg',
      isPublic: true,
      totalTracks: songs.length
    });

    // 2. Tạo PlaylistTrack documents theo thứ tự
    if (songs.length > 0) {
      const trackDocs = songs.map((song, index) => ({
        playlistId: playlist._id,
        songId: song._id,
        order: index,
        addedAt: new Date()
      }));
      await PlaylistTrack.insertMany(trackDocs);
    }

    // 3. Ghi log kiểm toán AiPromptLog
    await AiPromptLog.create({
      userId,
      userPrompt: aiLogData.userPrompt,
      aiExtractedCriteria: criteria,
      suggestedSongIds: songs.map((s) => s._id),
      generatedPlaylistId: playlist._id,
      provider: aiLogData.provider,
      latencyMs: aiLogData.latencyMs,
      tokensUsed: aiLogData.tokensUsed
    });

    return playlist;
  }
}

module.exports = PlaylistGenerator;
