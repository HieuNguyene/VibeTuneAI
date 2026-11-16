const Song = require('../models/Song');

const AUDIO_FEATURES = [
  'danceability',
  'energy',
  'valence',
  'tempoBpm',
  'acousticness',
  'instrumentalness',
  'liveness',
  'speechiness'
];

const MOOD_DEFINITIONS = {
  happy: {
    label: 'Vui vẻ / Năng lượng',
    conditions: {
      valence: { min: 0.55, max: 1.0 },
      energy: { min: 0.55, max: 1.0 }
    }
  },
  sad: {
    label: 'Buồn / Trầm lắng',
    conditions: {
      valence: { min: 0.0, max: 0.42 },
      energy: { min: 0.0, max: 0.45 }
    }
  },
  relax: {
    label: 'Thư giãn / Cafe Chill',
    conditions: {
      energy: { min: 0.0, max: 0.45 },
      acousticness: { min: 0.45, max: 1.0 }
    }
  },
  party: {
    label: 'Sôi động / Party',
    conditions: {
      danceability: { min: 0.65, max: 1.0 },
      energy: { min: 0.65, max: 1.0 }
    }
  },
  focus: {
    label: 'Tập trung / Làm việc',
    conditions: {
      energy: { min: 0.15, max: 0.55 }
    }
  }
};

const SIMILARITY_JITTER_THRESHOLD = 0.02; // 2% Jitter Shuffling

class CosineRecommender {
  /**
   * Chuyển đổi audioFeatures của bài hát thành Vector số thực chuẩn hóa [0, 1]
   */
  static extractNormalizedVector(song) {
    const f = song.audioFeatures || {};
    const tempo = f.tempoBpm || 120;
    // Chuẩn hóa tempo trong khoảng [50, 200] BPM
    const normalizedTempo = Math.max(0, Math.min(1, (tempo - 50) / 150));

    return [
      typeof f.danceability === 'number' ? f.danceability : 0.5,
      typeof f.energy === 'number' ? f.energy : 0.5,
      typeof f.valence === 'number' ? f.valence : 0.5,
      normalizedTempo,
      typeof f.acousticness === 'number' ? f.acousticness : 0.3,
      typeof f.instrumentalness === 'number' ? f.instrumentalness : 0.05,
      typeof f.liveness === 'number' ? f.liveness : 0.15,
      typeof f.speechiness === 'number' ? f.speechiness : 0.1
    ];
  }

  /**
   * Tính toán độ tương đồng Cosine giữa 2 vector đặc trưng
   * Cosine Similarity = (A . B) / (||A|| * ||B||)
   */
  static calculateCosineSimilarity(vecA, vecB) {
    if (!vecA || !vecB || vecA.length !== vecB.length) return 0;

    let dotProduct = 0;
    let normA = 0;
    let normB = 0;

    for (let i = 0; i < vecA.length; i++) {
      dotProduct += vecA[i] * vecB[i];
      normA += vecA[i] * vecA[i];
      normB += vecB[i] * vecB[i];
    }

    if (normA === 0 || normB === 0) return 0;
    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  /**
   * Thuật toán Đa dạng hóa (Jitter Shuffling) lấy cảm hứng từ engine.py:
   * Nếu các bài hát có độ tương đồng chênh lệch dưới 2%, xáo trộn nhẹ để kết quả luôn tươi mới
   */
  static diversify(recommendations, threshold = SIMILARITY_JITTER_THRESHOLD) {
    if (recommendations.length <= 1) return recommendations;

    const result = [];
    let group = [recommendations[0]];

    for (let i = 1; i < recommendations.length; i++) {
      const current = recommendations[i];
      if (Math.abs(current.similarity - group[0].similarity) < threshold) {
        group.push(current);
      } else {
        // Shuffle group ngẫu nhiên
        group.sort(() => Math.random() - 0.5);
        result.push(...group);
        group = [current];
      }
    }

    group.sort(() => Math.random() - 0.5);
    result.push(...group);
    return result;
  }

  /**
   * Kiểm tra bài hát có thỏa mãn điều kiện tâm trạng (Mood Filter)
   */
  static matchesMood(song, moodKey) {
    if (!moodKey || !MOOD_DEFINITIONS[moodKey]) return true;
    const moodDef = MOOD_DEFINITIONS[moodKey];
    const features = song.audioFeatures || {};

    for (const [feat, range] of Object.entries(moodDef.conditions)) {
      const val = features[feat];
      if (typeof val === 'number') {
        if (val < range.min || val > range.max) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Tìm danh sách bài hát tương đồng nhất với một bài hát mục tiêu
   * @param {string} targetSongId
   * @param {Object} options { limit: 8, genreId: null, mood: null, excludeIds: [] }
   */
  static async findSimilarSongs(targetSongId, options = {}) {
    const { limit = 8, genreId = null, mood = null, excludeIds = [] } = options;

    // 1. Tìm bài hát mục tiêu
    const targetSong = await Song.findById(targetSongId)
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('featuredArtistIds', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    if (!targetSong) return [];

    const targetVector = this.extractNormalizedVector(targetSong);

    // 2. Lấy toàn bộ các bài hát ứng viên trong kho nhạc (ngoại trừ chính nó)
    const filter = {
      _id: { $ne: targetSong._id, $nin: excludeIds },
      isPublic: true
    };
    if (genreId) {
      filter.genreIds = genreId;
    }

    const candidateSongs = await Song.find(filter)
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('featuredArtistIds', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    if (candidateSongs.length === 0) return [];

    // 3. Tính toán Cosine Similarity cho từng bài hát ứng viên
    let scoredList = [];
    for (const candidate of candidateSongs) {
      // Lọc theo Mood nếu có yêu cầu
      if (mood && !this.matchesMood(candidate, mood)) {
        continue;
      }

      const candidateVector = this.extractNormalizedVector(candidate);
      const similarity = this.calculateCosineSimilarity(targetVector, candidateVector);

      // Điểm cộng nhỏ nếu cùng ca sĩ hoặc cùng thể loại (0.02)
      let bonus = 0;
      const targetArtistId = targetSong.artistId?._id?.toString() || targetSong.artistId?.toString();
      const candArtistId = candidate.artistId?._id?.toString() || candidate.artistId?.toString();
      if (targetArtistId && targetArtistId === candArtistId) {
        bonus += 0.02;
      }

      const finalSim = Math.min(1.0, similarity + bonus);
      const similarityScore = Math.round(finalSim * 1000) / 10; // ví dụ: 95.8%

      scoredList.push({
        ...candidate,
        similarity: finalSim,
        similarityScore // % match
      });
    }

    // 4. Sắp xếp giảm dần theo độ tương đồng Cosine
    scoredList.sort((a, b) => b.similarity - a.similarity);

    // 5. Áp dụng thuật toán Đa dạng hóa (Jitter Diversification)
    const diversified = this.diversify(scoredList);

    // 6. Cắt theo số lượng limit yêu cầu
    return diversified.slice(0, limit);
  }
}

module.exports = CosineRecommender;
