const config = require('./config/moodFlow');

/**
 * Trình tạo số giả ngẫu nhiên có hỗ trợ seed (Mulberry32)
 * Giúp kiểm thử deterministic khi cần chạy simulation
 */
function createPrng(seed) {
  if (seed === null || seed === undefined) {
    return Math.random;
  }
  let s = typeof seed === 'number' ? Math.floor(seed) : 123456789;
  return function () {
    let t = (s += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Chọn bài hát tiếp theo bằng phương pháp Softmax Sampling có Temperature
 * Giúp cân bằng giữa Exploitation (chọn bài điểm cao nhất) và Exploration (đa dạng hóa)
 * @param {Array<Object>} scoredCandidates Danh sách đối tượng { candidate, totalScore, breakdown }
 * @param {number} [temperature=0.3] Tham số nhiệt độ điều chỉnh tính ngẫu nhiên
 * @param {number|null} [seed=null] Hạt giống ngẫu nhiên (dùng khi kiểm thử)
 * @returns {Object|null} Ứng viên được chọn kèm xác suất P(i)
 */
function selectNextTrack(scoredCandidates, temperature = 0.3, seed = null) {
  if (!Array.isArray(scoredCandidates) || scoredCandidates.length === 0) {
    return null;
  }

  // Nếu chỉ có 1 ứng viên, chọn luôn
  if (scoredCandidates.length === 1) {
    return {
      selected: scoredCandidates[0].candidate,
      score: scoredCandidates[0].totalScore,
      breakdown: scoredCandidates[0].breakdown,
      probability: 1.0
    };
  }

  const T = Math.max(0.01, Number(temperature) || config.temperature);
  const rng = createPrng(seed);

  // 1. Tìm điểm số lớn nhất để trừ đi (Numerical stability trick tránh tràn số khi exp)
  let maxScore = -Infinity;
  for (const item of scoredCandidates) {
    if (item.totalScore > maxScore) {
      maxScore = item.totalScore;
    }
  }

  // 2. Tính exp((score - maxScore) / T)
  const expValues = [];
  let sumExp = 0;

  for (const item of scoredCandidates) {
    const val = Math.exp((item.totalScore - maxScore) / T);
    expValues.push(val);
    sumExp += val;
  }

  // 3. Tính phân phối xác suất P(i)
  const probabilities = expValues.map(v => (sumExp > 0 ? v / sumExp : 1 / scoredCandidates.length));

  // 4. Lấy mẫu ngẫu nhiên theo phân phối tích lũy (Cumulative Distribution Function - CDF)
  const r = rng();
  let cumulative = 0;
  let selectedIndex = 0;

  for (let i = 0; i < probabilities.length; i++) {
    cumulative += probabilities[i];
    if (r <= cumulative) {
      selectedIndex = i;
      break;
    }
  }

  // Đảm bảo không bị out-of-bounds do sai số làm tròn float
  selectedIndex = Math.min(selectedIndex, scoredCandidates.length - 1);
  const chosen = scoredCandidates[selectedIndex];

  return {
    selected: chosen.candidate,
    score: chosen.totalScore,
    breakdown: chosen.breakdown,
    probability: Math.round(probabilities[selectedIndex] * 1000) / 1000
  };
}

module.exports = {
  selectNextTrack,
  createPrng
};
