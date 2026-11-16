/**
 * Trình sinh câu giải thích tiếng Việt ngắn gọn (Reason Generator)
 * Hoạt động thuần Rule-based & Feature-based, không gọi LLM bên ngoài để đảm bảo độ trễ < 1ms
 */

/**
 * Sinh câu giải thích ngắn tại sao bài hát này được đề xuất tiếp theo
 * @param {Object} selectedTrack Bài hát được chọn
 * @param {Object} context Ngữ cảnh { session, targetArc, breakdown, previousTrack, sourceTag }
 * @returns {string} Câu tiếng Việt thân thiện, tự nhiên
 */
function generateReason(selectedTrack, context = {}) {
  const { session, targetArc, breakdown = {}, previousTrack = null, sourceTag = '' } = context;
  const arcType = session?.arc || 'steady';
  const trackTitle = selectedTrack?.title || 'bài hát';
  const artistName = selectedTrack?.artist?.name || selectedTrack?.artistName || '';
  const moods = selectedTrack?.moods || [];
  const primaryMood = moods.length > 0 ? moods[0] : null;

  // 1. Nếu là nguồn Discovery hoặc Novelty rất cao
  if (sourceTag === 'discovery' || (breakdown.novelty >= 0.95 && Math.random() < 0.4)) {
    return `Một nét chấm phá mới mẻ dành riêng cho bạn khám phá.`;
  }

  // 2. Điểm Taste cao (Người dùng có cảm tình tốt với bài/nghệ sĩ)
  if (breakdown.taste >= 0.75 && artistName) {
    return `Phù hợp với gu thưởng thức và các bài hát của ${artistName} bạn yêu thích.`;
  }

  // 3. Bám theo đường cong Arc tâm trạng
  if (breakdown.arc >= 0.75) {
    switch (arcType) {
      case 'wind_down':
        return `Giai điệu dịu êm, giảm dần nhịp độ để bạn dễ dàng thư giãn và thả lỏng.`;
      case 'workout':
        if (targetArc?.targetEnergy >= 0.8) {
          return `Năng lượng bùng nổ với nhịp điệu mạnh mẽ để bạn bứt phá tập luyện.`;
        } else {
          return `Tiết tấu chuyển tiếp nhịp nhàng, sẵn sàng cho bài tập tiếp theo.`;
        }
      case 'focus':
        return `Tiết tấu hài hòa, vừa vặn để bạn duy trì sự tập trung tối đa.`;
      case 'steady':
      default:
        return `Tiếp nối mạch cảm xúc ổn định và dễ chịu của phiên nghe.`;
    }
  }

  // 4. Độ mượt chuyển tiếp âm học cao (BPM, Energy tương đồng)
  if (breakdown.smooth >= 0.80 && previousTrack) {
    return `Chuyển tiếp mượt mà về nhịp điệu và năng lượng từ "${previousTrack.title || 'bài vừa rồi'}".`;
  }

  // 5. Độ tương đồng ngữ nghĩa cao (Embedding similarity)
  if (breakdown.sim >= 0.75) {
    if (primaryMood) {
      return `Đồng điệu về sắc thái cảm xúc "${primaryMood}" với danh sách bạn đang nghe.`;
    }
    return `Mang phong cách âm nhạc rất gần gũi với giai điệu bạn vừa thưởng thức.`;
  }

  // 6. Fallback mặc định tự nhiên
  if (primaryMood) {
    return `Đề xuất mang âm hưởng "${primaryMood}" để giữ trọn vẹn mạch nghe của bạn.`;
  }
  return `Lựa chọn kế tiếp hoàn hảo cho danh sách phát hiện tại của bạn.`;
}

module.exports = {
  generateReason
};
