const config = require('./config/moodFlow');
const { cosineSimilarity } = require('./candidates');

/**
 * Chuẩn hóa khoảng cách Euclidean giữa 2 profile âm học
 * @param {Object} afA { energy, valence, bpm, danceability }
 * @param {Object} afB { energy, valence, bpm, danceability }
 * @returns {number} Giá trị khoảng cách [0, 1]
 */
function normalizedAudioDistance(afA, afB) {
  if (!afA || !afB) return 0.5;

  const dEnergy = (afA.energy ?? 0.5) - (afB.energy ?? 0.5);
  const dValence = (afA.valence ?? 0.5) - (afB.valence ?? 0.5);
  const dDance = (afA.danceability ?? 0.5) - (afB.danceability ?? 0.5);

  // Chuẩn hóa BPM về thang đo [0, 1] (từ 40 bpm đến 200 bpm)
  const normBpmA = Math.max(0, Math.min(1, ((afA.bpm ?? 120) - 40) / 160));
  const normBpmB = Math.max(0, Math.min(1, ((afB.bpm ?? 120) - 40) / 160));
  const dBpm = normBpmA - normBpmB;

  // Tính Euclidean distance có trọng số
  const squaredSum = (dEnergy * dEnergy * 0.35) +
                     (dValence * dValence * 0.25) +
                     (dDance * dDance * 0.20) +
                     (dBpm * dBpm * 0.20);

  return Math.min(1.0, Math.sqrt(squaredSum));
}

/**
 * Chấm điểm toàn diện cho 1 bài hát ứng viên trong ngữ cảnh phiên phát nhạc
 * @param {Object} candidate Bài hát ứng viên
 * @param {Object} context Ngữ cảnh { session, targetArc, recentArtists, userTasteMap, userHistoryMap }
 * @returns {Object} { totalScore, breakdown: { sim, smooth, arc, taste, novelty, penalty } }
 */
function scoreCandidate(candidate, context = {}) {
  const { session, targetArc, recentArtists = [], userTasteMap = null, userHistoryMap = null } = context;
  const af = candidate.audioFeatures || {};

  // 1. Sim Score (Cosine similarity giữa embedding ứng viên và sessionVec)
  let simScore = 0.5;
  if (Array.isArray(session?.sessionVec) && session.sessionVec.length > 0 &&
      Array.isArray(candidate.embedding) && candidate.embedding.length > 0) {
    simScore = cosineSimilarity(candidate.embedding, session.sessionVec);
  }

  // 2. Smooth Score (Độ mượt mà chuyển tiếp từ audioProfile hiện tại)
  const currentAf = session?.audioProfile || { energy: 0.5, valence: 0.5, bpm: 120, danceability: 0.5 };
  const dist = normalizedAudioDistance(af, currentAf);
  const smoothScore = Math.max(0, Math.min(1, 1 - dist));

  // 3. Arc Score (Mức độ khớp với mục tiêu targetArc)
  const targetEnergy = targetArc?.targetEnergy ?? 0.5;
  const targetValence = targetArc?.targetValence ?? 0.5;
  const candEnergy = af.energy ?? 0.5;
  const candValence = af.valence ?? 0.5;

  const energyDiff = Math.abs(candEnergy - targetEnergy);
  const valenceDiff = Math.abs(candValence - targetValence);
  const arcPenalty = (energyDiff * 0.70) + (valenceDiff * 0.30);
  const arcScore = Math.max(0, Math.min(1, 1 - arcPenalty));

  // 4. Taste Score (Dựa trên lịch sử tương tác / sở thích của user)
  let tasteScore = 0.5; // Mặc định cho khách hoặc bài chưa rõ
  if (userTasteMap) {
    const artistIdStr = candidate.artist?._id ? String(candidate.artist._id) : '';
    const songIdStr = String(candidate.id || candidate._id);
    const songAffinity = userTasteMap.songs?.get(songIdStr) ?? 0;
    const artistAffinity = userTasteMap.artists?.get(artistIdStr) ?? 0;
    // Kết hợp điểm số sở thích
    const combinedAffinity = (songAffinity * 0.7) + (artistAffinity * 0.3);
    tasteScore = Math.max(0.1, Math.min(1.0, 0.5 + combinedAffinity * 0.25));
  }

  // 5. Novelty Score (Tính mới lạ: 1 nếu chưa nghe, giảm dần nếu nghe nhiều)
  let noveltyScore = 1.0;
  const userPlayCount = userHistoryMap?.get(String(candidate.id || candidate._id)) ?? 0;
  if (userPlayCount > 0) {
    noveltyScore = Math.max(0.1, 1 / (1 + 0.4 * Math.log2(1 + userPlayCount)));
  }

  // 6. Penalties (Phạt nghệ sĩ lặp lại liên tiếp)
  let penalty = 0;
  const currentArtistId = candidate.artist?._id ? String(candidate.artist._id) : candidate.artistName;

  if (recentArtists.length > 0 && currentArtistId) {
    if (recentArtists[0] === currentArtistId) {
      // Cùng nghệ sĩ với bài vừa phát ngay trước
      penalty += config.penalties.sameArtistImmediate;
    } else if (recentArtists.length >= 2 && recentArtists.slice(0, 2).includes(currentArtistId)) {
      // Xuất hiện trong 2 bài gần nhất
      penalty += config.penalties.sameArtistRecent;
    }
  }

  // Tổng điểm có trọng số
  const totalScore = (
    config.weights.sim * simScore +
    config.weights.smooth * smoothScore +
    config.weights.arc * arcScore +
    config.weights.taste * tasteScore +
    config.weights.novelty * noveltyScore +
    penalty
  );

  return {
    candidate,
    totalScore: Math.round(totalScore * 1000) / 1000,
    breakdown: {
      sim: Math.round(simScore * 100) / 100,
      smooth: Math.round(smoothScore * 100) / 100,
      arc: Math.round(arcScore * 100) / 100,
      taste: Math.round(tasteScore * 100) / 100,
      novelty: Math.round(noveltyScore * 100) / 100,
      penalty
    }
  };
}

module.exports = {
  scoreCandidate,
  normalizedAudioDistance
};
