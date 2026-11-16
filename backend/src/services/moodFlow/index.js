const mongoose = require('mongoose');
const { PlaySession, Song, UserInteraction, ListeningEvent } = require('../../models');
const config = require('./config/moodFlow');
const { getTargetArc } = require('./arcs');
const { getCandidates } = require('./candidates');
const { scoreCandidate } = require('./scoring');
const { selectNextTrack } = require('./selector');
const { initSession, updateSessionOnEvent } = require('./session');
const { generateReason } = require('./reason');

/**
 * Xây dựng bản đồ sở thích người dùng (User Taste Map) từ UserInteraction
 */
async function buildUserTasteMap(userId) {
  if (!userId || !mongoose.Types.ObjectId.isValid(userId)) return null;

  try {
    const interactions = await UserInteraction.find({ userId })
      .sort({ createdAt: -1 })
      .limit(100)
      .lean();

    const songMap = new Map();
    const artistMap = new Map();

    for (const item of interactions) {
      const sId = String(item.songId);
      const w = item.weight || 0.2;
      songMap.set(sId, (songMap.get(sId) || 0) + w);
    }

    if (songMap.size > 0) {
      const interactedSongs = await Song.find({ _id: { $in: Array.from(songMap.keys()) } })
        .select('artistId artistIds')
        .lean();

      for (const s of interactedSongs) {
        const w = songMap.get(String(s._id)) || 0;
        const aIds = new Set();
        if (s.artistId) aIds.add(String(s.artistId));
        if (Array.isArray(s.artistIds)) {
          s.artistIds.forEach((aid) => {
            if (aid) aIds.add(String(aid));
          });
        }
        for (const aid of aIds) {
          artistMap.set(aid, (artistMap.get(aid) || 0) + w);
        }
      }
    }

    return { songs: songMap, artists: artistMap };
  } catch (err) {
    return null;
  }
}

/**
 * Xây dựng bản đồ số lần nghe (Listen Count) để tính Novelty
 */
async function buildUserHistoryMap(userId) {
  if (!userId || !mongoose.Types.ObjectId.isValid(userId)) return null;

  try {
    const counts = await ListeningEvent.aggregate([
      { $match: { userId: new mongoose.Types.ObjectId(userId) } },
      { $group: { _id: '$trackId', count: { $sum: 1 } } }
    ]);

    const historyMap = new Map();
    counts.forEach(c => historyMap.set(String(c._id), c.count));
    return historyMap;
  } catch (err) {
    return null;
  }
}

/**
 * Thuật toán chính: Đề xuất bài hát tiếp theo trong phiên nghe (Mood Flow Next Track)
 * @param {Object} params
 * @param {string} [params.sessionId] ID phiên nghe hiện tại (nếu có)
 * @param {string} [params.currentTrackId] ID bài hát đang phát
 * @param {string} [params.arc] Chế độ arc mong muốn ('wind_down', 'workout', 'focus', 'steady')
 * @param {string} [params.userId] ID người dùng
 * @param {boolean} [params.debug=false] Trả về thông tin phân rã điểm số để debug
 * @param {number|null} [params.seed=null] Hạt giống ngẫu nhiên (dùng khi test)
 * @returns {Promise<Object>} { session, track, reason, debug? }
 */
async function getNextTrack({
  sessionId = null,
  currentTrackId = null,
  arc = null,
  userId = null,
  debug = false,
  seed = null
} = {}) {
  let session = null;

  // 1. Tìm hoặc khởi tạo PlaySession
  if (sessionId && mongoose.Types.ObjectId.isValid(sessionId)) {
    session = await PlaySession.findById(sessionId);
    if (session && session.userId) {
      if (!userId) {
        const ApiError = require('../../utils/ApiError');
        throw ApiError.unauthorized('Vui lòng đăng nhập để tiếp tục phiên phát nhạc cá nhân');
      }
      if (String(session.userId) !== String(userId)) {
        const ApiError = require('../../utils/ApiError');
        throw ApiError.forbidden('Không có quyền truy cập phiên phát nhạc của người dùng khác');
      }
    }
  }

  // Nếu chưa có session, khởi tạo từ currentTrackId hoặc bài hát phổ biến nhất
  if (!session) {
    let startingTrack = null;
    if (currentTrackId && mongoose.Types.ObjectId.isValid(currentTrackId)) {
      startingTrack = await Song.findById(currentTrackId)
        .populate('artistId', 'name avatarUrl')
        .populate('featuredArtistIds', 'name avatarUrl')
        .lean();
    }
    if (!startingTrack) {
      startingTrack = await Song.findOne({ isPublic: { $ne: false } })
        .populate('artistId', 'name avatarUrl')
        .populate('featuredArtistIds', 'name avatarUrl')
        .lean();
    }
    if (!startingTrack) {
      throw new Error('Không tìm thấy bài hát nào trong cơ sở dữ liệu để khởi tạo Mood Flow.');
    }

    session = await initSession(startingTrack, userId, arc || 'steady');
  }

  // Cập nhật arc nếu người dùng chủ động đổi chế độ
  if (arc && config.validArcs.includes(arc) && session.arc !== arc) {
    session.arc = arc;
  }

  // 2. Chuẩn bị ngữ cảnh
  const nextPosition = (session.position || 0) + 1;
  const targetArc = getTargetArc(session.arc, nextPosition, 20, session.audioProfile);

  // Lấy danh sách nghệ sĩ gần nhất để phạt lặp lại
  let recentArtists = [];
  if (Array.isArray(session.recentTrackIds) && session.recentTrackIds.length > 0) {
    const recentSongs = await Song.find({ _id: { $in: session.recentTrackIds.slice(0, 3) } })
      .select('artistId')
      .lean();
    recentArtists = recentSongs.map(s => String(s.artistId));
  }

  // Lấy lịch sử sở thích của user
  const effectiveUserId = session.userId || userId;
  const [userTasteMap, userHistoryMap] = await Promise.all([
    buildUserTasteMap(effectiveUserId),
    buildUserHistoryMap(effectiveUserId)
  ]);

  const context = {
    session,
    targetArc,
    recentArtists,
    userTasteMap,
    userHistoryMap
  };

  // 3. Thu thập các ứng viên
  const candidates = await getCandidates(session, { _id: effectiveUserId }, config.pool.defaultSize);

  if (!candidates || candidates.length === 0) {
    throw new Error('Không còn bài hát nào khả dụng cho phiên nghe hiện tại.');
  }

  // 4. Chấm điểm từng ứng viên
  const scoredCandidates = candidates.map(candidate => scoreCandidate(candidate, context));

  // 5. Chọn bài hát bằng Softmax Sampling
  const selection = selectNextTrack(scoredCandidates, config.temperature, seed);
  if (!selection || !selection.selected) {
    throw new Error('Lỗi khi lựa chọn bài hát qua phân phối xác suất Softmax.');
  }

  const chosen = selection.selected;

  // 6. Sinh câu giải thích lý do tiếng Việt
  const reason = generateReason(chosen, {
    session,
    targetArc,
    breakdown: selection.breakdown,
    sourceTag: chosen.sourceTag
  });

  // 7. Cập nhật trạng thái session
  session.position = nextPosition;
  if (!session.recentTrackIds) session.recentTrackIds = [];
  session.recentTrackIds.unshift(chosen._id);
  if (session.recentTrackIds.length > config.pool.maxRecentTracks) {
    session.recentTrackIds = session.recentTrackIds.slice(0, config.pool.maxRecentTracks);
  }
  await session.save();

  // 8. Định dạng kết quả trả về theo đúng chuẩn hợp đồng REST API
  const responseData = {
    sessionId: String(session._id),
    session: {
      id: String(session._id),
      arc: session.arc,
      position: session.position
    },
    track: {
      id: String(chosen._id),
      title: chosen.title,
      artist: chosen.artist,
      artistName: chosen.artistName,
      coverUrl: chosen.coverUrl,
      streamUrl: chosen.streamUrl,
      durationSec: chosen.durationSec,
      moods: chosen.moods,
      energy: chosen.audioFeatures?.energy ?? 0.5
    },
    reason
  };

  if (debug) {
    responseData.debug = {
      score: selection.score,
      probability: selection.probability,
      breakdown: selection.breakdown,
      targetArc
    };
  }

  return responseData;
}

module.exports = {
  getNextTrack,
  initSession,
  updateSessionOnEvent,
  config,
  arcs: { getTargetArc },
  candidates: { getCandidates },
  scoring: { scoreCandidate },
  selector: { selectNextTrack },
  reason: { generateReason }
};
