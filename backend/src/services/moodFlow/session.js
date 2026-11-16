const mongoose = require('mongoose');
const { PlaySession, Song } = require('../../models');
const config = require('./config/moodFlow');
const { extractAudioFeatures } = require('./candidates');

/**
 * Chuẩn hóa vector về độ dài L2 = 1
 * @param {number[]} vec
 * @returns {number[]}
 */
function normalizeVector(vec) {
  if (!Array.isArray(vec) || vec.length === 0) return [];
  let sumSq = 0;
  for (let i = 0; i < vec.length; i++) {
    sumSq += vec[i] * vec[i];
  }
  const norm = Math.sqrt(sumSq);
  if (norm === 0) return vec;
  return vec.map(x => x / norm);
}

/**
 * Khởi tạo một PlaySession mới
 * @param {Object} firstTrack Bài hát bắt đầu phiên
 * @param {string|null} userId ID người dùng hoặc null
 * @param {string} [arcType='steady'] Loại arc tâm trạng
 * @returns {Promise<Object>} PlaySession document đã lưu
 */
async function initSession(firstTrack, userId = null, arcType = 'steady') {
  const arc = config.validArcs.includes(arcType) ? arcType : 'steady';
  const trackId = firstTrack._id || firstTrack.id;

  const audioProfile = extractAudioFeatures(firstTrack);
  const sessionVec = Array.isArray(firstTrack.embedding) && firstTrack.embedding.length > 0
    ? normalizeVector(firstTrack.embedding)
    : [];

  const session = await PlaySession.create({
    userId: userId && mongoose.Types.ObjectId.isValid(userId) ? userId : null,
    arc,
    startedAt: new Date(),
    sessionVec,
    audioProfile,
    recentTrackIds: trackId ? [trackId] : [],
    dislikedTrackIds: [],
    position: 0,
    lastEventAt: new Date(),
    totalTracksPlayed: 1
  });

  return session;
}

/**
 * Cập nhật PlaySession dựa trên hành vi nghe nhạc thực tế của người dùng
 * @param {string|Object} sessionOrId PlaySession object hoặc ID
 * @param {string} event 'play' | 'skip' | 'complete' | 'like' | 'dislike'
 * @param {Object} track Bài hát liên quan
 * @param {number} [positionSec=0] Giây hiện tại tại thời điểm trigger sự kiện
 * @returns {Promise<Object>} PlaySession đã được cập nhật
 */
async function updateSessionOnEvent(sessionOrId, event, track, positionSec = 0) {
  let session = sessionOrId;
  if (typeof sessionOrId === 'string' || sessionOrId instanceof mongoose.Types.ObjectId) {
    session = await PlaySession.findById(sessionOrId);
  }

  if (!session) {
    return null;
  }

  const trackId = track?._id || track?.id;
  const trackEmbedding = Array.isArray(track?.embedding) && track.embedding.length > 0
    ? track.embedding
    : null;
  const trackAudio = track ? extractAudioFeatures(track) : null;
  const trackDuration = track?.duration || 180;

  session.lastEventAt = new Date();

  // 1. Xử lý sự kiện COMPLETE hoặc nghe trọn vẹn (> 70% thời lượng)
  const isListenedLong = trackDuration > 0 && (positionSec / trackDuration) >= 0.7;
  if (event === 'complete' || (event === 'play' && isListenedLong)) {
    // Cập nhật sessionVec: S_t+1 = (1 - alpha) * S_t + alpha * v_k
    if (trackEmbedding && session.sessionVec && session.sessionVec.length === trackEmbedding.length) {
      const alpha = config.learningRate.alphaComplete;
      const updatedVec = session.sessionVec.map((val, idx) => (1 - alpha) * val + alpha * trackEmbedding[idx]);
      session.sessionVec = normalizeVector(updatedVec);
    } else if (trackEmbedding && (!session.sessionVec || session.sessionVec.length === 0)) {
      session.sessionVec = normalizeVector(trackEmbedding);
    }

    // Cập nhật audioProfile theo trung bình động hàm mũ (EMA): A_t+1 = 0.8 * A_t + 0.2 * A_k
    if (trackAudio && session.audioProfile) {
      const aAlpha = config.learningRate.alphaAudio;
      session.audioProfile.energy = (1 - aAlpha) * session.audioProfile.energy + aAlpha * trackAudio.energy;
      session.audioProfile.valence = (1 - aAlpha) * session.audioProfile.valence + aAlpha * trackAudio.valence;
      session.audioProfile.bpm = Math.round((1 - aAlpha) * session.audioProfile.bpm + aAlpha * trackAudio.bpm);
      session.audioProfile.danceability = (1 - aAlpha) * session.audioProfile.danceability + aAlpha * trackAudio.danceability;
    }

    session.totalTracksPlayed = (session.totalTracksPlayed || 0) + 1;
  }

  // 2. Xử lý sự kiện SKIP sớm (< 30s)
  if (event === 'skip' && positionSec < 30) {
    // Phạt vector: S_t+1 = S_t - beta * v_k (né tránh thể loại/tâm trạng vừa bị bỏ qua)
    if (trackEmbedding && session.sessionVec && session.sessionVec.length === trackEmbedding.length) {
      const beta = config.learningRate.betaSkip;
      const updatedVec = session.sessionVec.map((val, idx) => val - beta * trackEmbedding[idx]);
      session.sessionVec = normalizeVector(updatedVec);
    }
  }

  // 3. Xử lý sự kiện DISLIKE
  if (event === 'dislike' && trackId) {
    if (!session.dislikedTrackIds) session.dislikedTrackIds = [];
    const idStr = String(trackId);
    if (!session.dislikedTrackIds.some(id => String(id) === idStr)) {
      session.dislikedTrackIds.push(trackId);
    }

    // Phạt mạnh vector theo hướng ngược lại bài bị dislike
    if (trackEmbedding && session.sessionVec && session.sessionVec.length === trackEmbedding.length) {
      const beta = config.learningRate.betaSkip * 1.5;
      const updatedVec = session.sessionVec.map((val, idx) => val - beta * trackEmbedding[idx]);
      session.sessionVec = normalizeVector(updatedVec);
    }
  }

  // 4. Cập nhật recentTrackIds (giữ tối đa 20 bài gần nhất)
  if (trackId && (event === 'play' || event === 'complete')) {
    if (!session.recentTrackIds) session.recentTrackIds = [];
    const idStr = String(trackId);
    // Xóa vị trí cũ nếu đã có để đẩy lên đầu
    session.recentTrackIds = session.recentTrackIds.filter(id => String(id) !== idStr);
    session.recentTrackIds.unshift(trackId);
    if (session.recentTrackIds.length > config.pool.maxRecentTracks) {
      session.recentTrackIds = session.recentTrackIds.slice(0, config.pool.maxRecentTracks);
    }
  }

  await session.save();
  return session;
}

module.exports = {
  initSession,
  updateSessionOnEvent,
  normalizeVector
};
