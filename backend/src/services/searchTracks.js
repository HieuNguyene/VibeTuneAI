const mongoose = require('mongoose');
const { Song, Artist, Genre } = require('../models');
const { MOOD_LIST, ACTIVITY_LIST, ENERGY_LEVELS } = require('../config/labels');

/**
 * Tìm kiếm bài hát trong Catalog thật theo tâm trạng, năng lượng, thể loại
 * Áp dụng $sample và nới lỏng điều kiện dần (progressive relaxation)
 */
async function searchTracks({
  moods = [],
  energy = null,
  activities = [],
  genres = [],
  language = null,
  excludeIds = [],
  userId = null,
  limit = 8
} = {}) {
  // 1. Validate & sanitize inputs
  const safeLimit = Math.min(Math.max(1, parseInt(limit, 10) || 8), 20);

  const safeMoods = Array.isArray(moods)
    ? moods.map(m => String(m).toLowerCase().trim()).filter(m => MOOD_LIST.includes(m))
    : [];

  const safeActivities = Array.isArray(activities)
    ? activities.map(a => String(a).toLowerCase().trim()).filter(a => ACTIVITY_LIST.includes(a))
    : [];

  const safeEnergy = ENERGY_LEVELS.includes(String(energy).toLowerCase())
    ? String(energy).toLowerCase()
    : null;

  const validExcludeObjectIds = (Array.isArray(excludeIds) ? excludeIds : [])
    .filter(id => mongoose.Types.ObjectId.isValid(id))
    .map(id => new mongoose.Types.ObjectId(id));

  // Hàm xây dựng query theo mức độ nới lỏng (relaxation level)
  function buildQuery(level) {
    const q = { isPublic: { $ne: false } };

    if (validExcludeObjectIds.length > 0) {
      q._id = { $nin: validExcludeObjectIds };
    }

    if (language && level === 1) {
      q.language = language;
    }

    // Mức 1 & 2: lọc theo mood hoặc activity
    if (safeMoods.length > 0 || safeActivities.length > 0) {
      const orConditions = [];
      if (safeMoods.length > 0) {
        orConditions.push({ moods: { $in: safeMoods } });
        orConditions.push({ mood: { $in: safeMoods } });
      }
      if (safeActivities.length > 0) {
        orConditions.push({ activities: { $in: safeActivities } });
      }
      if (orConditions.length > 0) {
        q.$or = orConditions;
      }
    }

    // Mức 1: lọc chặt chẽ cả energy
    if (level === 1 && safeEnergy) {
      if (safeEnergy === 'low') {
        q.$and = [{ $or: [{ 'audio.energy': { $lt: 0.55 } }, { 'audioFeatures.energy': { $lt: 0.55 } }] }];
      } else if (safeEnergy === 'high') {
        q.$and = [{ $or: [{ 'audio.energy': { $gt: 0.65 } }, { 'audioFeatures.energy': { $gt: 0.65 } }] }];
      } else if (safeEnergy === 'medium') {
        q.$and = [{ $or: [{ 'audio.energy': { $gte: 0.4, $lte: 0.75 } }, { 'audioFeatures.energy': { $gte: 0.4, $lte: 0.75 } }] }];
      }
    }

    return q;
  }

  // Thử lần lượt các mức độ nới lỏng
  let results = [];
  const levels = [1, 2, 3]; // 1: Chặt chẽ, 2: Bỏ energy, 3: Chỉ cần còn bài chưa nghe

  for (const lvl of levels) {
    const query = buildQuery(lvl);
    results = await Song.aggregate([
      { $match: query },
      { $sample: { size: safeLimit } },
      {
        $lookup: {
          from: 'artists',
          localField: 'artistId',
          foreignField: '_id',
          as: 'artist'
        }
      },
      { $unwind: { path: '$artist', preserveNullAndEmptyArrays: true } }
    ]);

    if (results.length >= Math.min(3, safeLimit)) {
      break;
    }
  }

  // Fallback an toàn tuyệt đối: Nếu catalog vẫn chưa đủ, lấy bài bất kỳ từ songs
  if (results.length === 0) {
    results = await Song.aggregate([
      { $match: { isPublic: { $ne: false } } },
      { $sample: { size: safeLimit } },
      {
        $lookup: {
          from: 'artists',
          localField: 'artistId',
          foreignField: '_id',
          as: 'artist'
        }
      },
      { $unwind: { path: '$artist', preserveNullAndEmptyArrays: true } }
    ]);
  }

  // Chuẩn hóa cấu trúc kết quả trả về
  let formattedTracks = results.map(track => {
    const artistName = track.artist ? track.artist.name : 'Nghệ sĩ';
    const artistObj = {
      _id: track.artist ? String(track.artist._id) : (track.artistId ? String(track.artistId) : ''),
      name: artistName,
      avatarUrl: track.artist ? (track.artist.avatarUrl || '') : ''
    };
    return {
      _id: track._id,
      id: track._id,
      title: track.title,
      artist: artistObj,
      artistId: artistObj,
      artistName: artistName,
      albumId: track.albumId,
      duration: track.duration,
      durationSec: track.duration,
      audioUrl: track.audioUrl,
      coverImageUrl: track.coverImageUrl || (track.artist ? track.artist.avatarUrl : ''),
      coverUrl: track.coverImageUrl || '',
      moods: track.moods || track.mood || [],
      activities: track.activities || [],
      description: track.description || `Bài hát ${track.title} của ${artistName}`,
      audio: track.audio || {
        bpm: track.audioFeatures ? track.audioFeatures.tempoBpm : 120,
        energy: track.audioFeatures ? track.audioFeatures.energy : 0.5,
        valence: track.audioFeatures ? track.audioFeatures.valence : 0.5
      }
    };
  });

  // Re-ranking cá nhân hóa nếu có userId (Giai đoạn 6)
  if (userId && mongoose.Types.ObjectId.isValid(userId)) {
    try {
      const { ListeningEvent } = require('../models');
      const recentEvents = await ListeningEvent.find({ userId })
        .sort({ ts: -1 })
        .limit(50)
        .lean();

      const skippedTrackIds = new Set(
        recentEvents.filter(e => e.event === 'skip').map(e => String(e.trackId))
      );
      const likedTrackIds = new Set(
        recentEvents.filter(e => e.event === 'like' || e.event === 'complete').map(e => String(e.trackId))
      );

      formattedTracks.sort((a, b) => {
        let scoreA = 0;
        let scoreB = 0;
        const idA = String(a.id);
        const idB = String(b.id);

        if (likedTrackIds.has(idA)) scoreA += 2;
        if (likedTrackIds.has(idB)) scoreB += 2;
        if (skippedTrackIds.has(idA)) scoreA -= 2;
        if (skippedTrackIds.has(idB)) scoreB -= 2;

        return scoreB - scoreA;
      });
    } catch (reRankErr) {
      // Bỏ qua lỗi re-rank, giữ nguyên kết quả
    }
  }

  return formattedTracks;
}

module.exports = searchTracks;
