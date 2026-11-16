const mongoose = require('mongoose');
const { Song, ListeningEvent } = require('../../models');
const config = require('./config/moodFlow');
const { getTargetArc } = require('./arcs');

/**
 * Tính Cosine Similarity giữa 2 vector
 */
function cosineSimilarity(vecA, vecB) {
  if (!vecA || !vecB || vecA.length === 0 || vecB.length === 0) return 0;
  const len = Math.min(vecA.length, vecB.length);
  let dotProduct = 0;
  let normA = 0;
  let normB = 0;
  for (let i = 0; i < len; i++) {
    const a = vecA[i];
    const b = vecB[i];
    dotProduct += a * b;
    normA += a * a;
    normB += b * b;
  }
  if (normA === 0 || normB === 0) return 0;
  const rawCos = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
  return Math.max(0, Math.min(1, (rawCos + 1) / 2));
}

/**
 * Trích xuất audio features an toàn từ Song document
 */
function extractAudioFeatures(song) {
  const af = song.audioFeatures || {};
  const audio = song.audio || {};
  return {
    energy: typeof song.energy === 'number' ? song.energy : (af.energy ?? audio.energy ?? 0.5),
    valence: typeof song.valence === 'number' ? song.valence : (af.valence ?? audio.valence ?? 0.5),
    bpm: typeof song.bpm === 'number' ? song.bpm : (af.tempoBpm ?? af.bpm ?? audio.bpm ?? 120),
    danceability: typeof song.danceability === 'number' ? song.danceability : (af.danceability ?? audio.danceability ?? 0.5),
    acousticness: typeof song.acousticness === 'number' ? song.acousticness : (af.acousticness ?? audio.acousticness ?? 0.5)
  };
}

/**
 * Thu thập ứng viên (Candidate Pool) cho lượt gợi ý tiếp theo
 * @param {Object} session PlaySession document
 * @param {Object} [user] User document hoặc null (nếu là khách)
 * @param {number} [poolSize=80] Kích thước nhóm ứng viên
 * @returns {Promise<Array>} Danh sách ứng viên kèm metadata đã làm giàu
 */
async function getCandidates(session, user = null, poolSize = 80) {
  const targetPoolSize = Math.max(20, poolSize || config.pool.defaultSize);

  // 1. Xác định danh sách ID bị loại trừ (recentTracks + disliked trong 7 ngày)
  const excludeSet = new Set();

  // Loại trừ bài trong recentTrackIds (20 bài gần nhất của session)
  if (Array.isArray(session?.recentTrackIds)) {
    session.recentTrackIds.forEach(id => excludeSet.add(String(id)));
  }

  // Loại trừ bài dislike trong session hiện tại
  if (Array.isArray(session?.dislikedTrackIds)) {
    session.dislikedTrackIds.forEach(id => excludeSet.add(String(id)));
  }

  // Loại trừ bài user đã dislike trong 7 ngày qua
  const userId = session?.userId || user?._id;
  if (userId && mongoose.Types.ObjectId.isValid(userId)) {
    try {
      const sevenDaysAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
      const recentDislikes = await ListeningEvent.find({
        userId,
        event: 'dislike',
        ts: { $gte: sevenDaysAgo }
      }).distinct('trackId');

      recentDislikes.forEach(id => excludeSet.add(String(id)));
    } catch (err) {
      console.warn('[MoodFlow:Candidates] Lỗi khi truy vấn dislike gần đây:', err.message);
    }
  }

  // 2. Tính target arc cho vị trí tiếp theo
  const position = session?.position || 0;
  const targetArc = getTargetArc(session?.arc, position, 20, session?.audioProfile);

  // 3. Phân bổ số lượng ứng viên theo tỷ lệ cấu hình
  const vectorCount = Math.round(targetPoolSize * config.pool.vectorRatio);
  const metadataCount = Math.round(targetPoolSize * config.pool.metadataRatio);
  const discoveryCount = Math.max(5, targetPoolSize - vectorCount - metadataCount);

  const candidateMap = new Map(); // Dùng Map để deduplicate theo songId

  const helperAddCandidate = (song, sourceTag) => {
    const sId = String(song._id);
    if (excludeSet.has(sId)) return;
    if (!candidateMap.has(sId)) {
      const audioFeats = extractAudioFeatures(song);
      const artistName = song.artistId?.name || 'Nghệ sĩ';
      const artistObj = {
        _id: song.artistId?._id ? String(song.artistId._id) : '',
        name: artistName,
        avatarUrl: song.artistId?.avatarUrl || ''
      };

      candidateMap.set(sId, {
        _id: song._id,
        id: String(song._id),
        title: song.title,
        artist: artistObj,
        artistName,
        coverUrl: song.coverImageUrl || artistObj.avatarUrl || '',
        streamUrl: song.audioUrl,
        durationSec: song.duration || 180,
        moods: song.moods || (song.mood ? [song.mood] : []),
        genres: (song.genreIds || []).map(g => g.name || g),
        audioFeatures: audioFeats,
        embedding: Array.isArray(song.embedding) ? song.embedding : [],
        playsCount: song.playsCount || 0,
        sourceTag
      });
    }
  };

  // NGUỒN 1: Vector Search k-NN nếu sessionVec có dữ liệu
  const sessionVec = session?.sessionVec;
  const hasValidSessionVec = Array.isArray(sessionVec) && sessionVec.length > 0;

  if (hasValidSessionVec) {
    try {
      // Lấy tất cả bài hát có embedding từ MongoDB
      const vectorCandidates = await Song.find({
        isPublic: { $ne: false },
        'embedding.0': { $exists: true }
      })
        .populate('artistId', 'name avatarUrl')
        .populate('featuredArtistIds', 'name avatarUrl')
        .populate('genreIds', 'name')
        .lean();

      // Tính toán Cosine Similarity và xếp hạng
      const sortedVectorCandidates = vectorCandidates
        .map(song => ({
          song,
          sim: cosineSimilarity(sessionVec, song.embedding)
        }))
        .filter(item => !excludeSet.has(String(item.song._id)))
        .sort((a, b) => b.sim - a.sim)
        .slice(0, vectorCount);

      sortedVectorCandidates.forEach(item => helperAddCandidate(item.song, 'vector'));
    } catch (vecErr) {
      console.warn('[MoodFlow:Candidates] Lỗi truy vấn vector:', vecErr.message);
    }
  }

  // NGUỒN 2: Metadata matching (theo energy, valence, bpm gần với Target Arc)
  try {
    const energyMin = Math.max(0, targetArc.targetEnergy - 0.25);
    const energyMax = Math.min(1, targetArc.targetEnergy + 0.25);

    const metaSongs = await Song.find({
      isPublic: { $ne: false },
      $or: [
        { energy: { $gte: energyMin, $lte: energyMax } },
        { 'audioFeatures.energy': { $gte: energyMin, $lte: energyMax } },
        { 'audio.energy': { $gte: energyMin, $lte: energyMax } }
      ]
    })
      .populate('artistId', 'name avatarUrl')
      .populate('featuredArtistIds', 'name avatarUrl')
      .populate('genreIds', 'name')
      .limit(metadataCount * 2)
      .lean();

    metaSongs.forEach(song => helperAddCandidate(song, 'metadata'));
  } catch (metaErr) {
    console.warn('[MoodFlow:Candidates] Lỗi truy vấn metadata:', metaErr.message);
  }

  // NGUỒN 3: Discovery ngẫu nhiên (10-15%)
  try {
    const discoverySongs = await Song.aggregate([
      { $match: { isPublic: { $ne: false } } },
      { $sample: { size: discoveryCount * 2 } }
    ]);

    // Populate artistId cho aggregation sample
    await Song.populate(discoverySongs, [
      { path: 'artistId', select: 'name avatarUrl' },
      { path: 'featuredArtistIds', select: 'name avatarUrl' },
      { path: 'genreIds', select: 'name' }
    ]);

    discoverySongs.forEach(song => helperAddCandidate(song, 'discovery'));
  } catch (discErr) {
    console.warn('[MoodFlow:Candidates] Lỗi truy vấn discovery:', discErr.message);
  }

  // FALLBACK: Nếu pool vẫn ít hơn 10 bài (do catalog nhỏ hoặc thiếu dữ liệu), lấy tất cả bài hát public chưa nghe
  if (candidateMap.size < 10) {
    try {
      const fallbackSongs = await Song.find({ isPublic: { $ne: false } })
        .populate('artistId', 'name avatarUrl')
        .populate('featuredArtistIds', 'name avatarUrl')
        .populate('genreIds', 'name')
        .sort({ playsCount: -1 })
        .limit(targetPoolSize)
        .lean();

      fallbackSongs.forEach(song => helperAddCandidate(song, 'fallback'));
    } catch (fbErr) {
      console.error('[MoodFlow:Candidates] Fallback failed:', fbErr.message);
    }
  }

  return Array.from(candidateMap.values());
}

module.exports = {
  getCandidates,
  cosineSimilarity,
  extractAudioFeatures
};
