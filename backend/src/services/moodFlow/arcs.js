/**
 * Quản lý đường cong tâm trạng (Mood Arcs)
 * Định nghĩa hành trình năng lượng (targetEnergy) và sắc thái cảm xúc (targetValence)
 * theo tiến trình vị trí bài hát (position) trong phiên phát nhạc.
 */

/**
 * Tính toán mục tiêu { targetEnergy, targetValence } theo Arc và Position
 * @param {string} arcType 'wind_down' | 'workout' | 'focus' | 'steady'
 * @param {number} position Vị trí bài hát hiện tại (bắt đầu từ 0)
 * @param {number} totalEstimatedTracks Tổng số bài ước tính của một phiên (mặc định 20)
 * @param {Object} [baselineProfile] Profile âm học gốc (dùng cho 'steady')
 * @returns {{ targetEnergy: number, targetValence: number }}
 */
function getTargetArc(arcType = 'steady', position = 0, totalEstimatedTracks = 20, baselineProfile = null) {
  const safeTracks = Math.max(5, totalEstimatedTracks || 20);
  const p = Math.max(0, Math.min(1, position / safeTracks)); // Tiến độ từ 0.0 đến 1.0

  let targetEnergy = 0.5;
  let targetValence = 0.5;

  switch (arcType) {
    case 'wind_down':
      // Hạ nhiệt thư giãn: Năng lượng giảm dần từ 0.75 xuống 0.25; Cảm xúc từ 0.60 về êm đềm 0.40
      targetEnergy = 0.75 - 0.50 * p;
      targetValence = 0.60 - 0.20 * p;
      break;

    case 'workout':
      // Tập luyện: Khởi động (p < 0.2) -> Đỉnh cao bùng nổ (0.2 <= p < 0.8) -> Thả lỏng giãn cơ (p >= 0.8)
      if (p < 0.2) {
        // Khởi động tăng dần từ 0.60 lên 0.85
        const localP = p / 0.2;
        targetEnergy = 0.60 + 0.25 * localP;
        targetValence = 0.65 + 0.15 * localP;
      } else if (p < 0.8) {
        // Đỉnh cao duy trì năng lượng cao 0.85 - 0.95
        const midP = (p - 0.2) / 0.6;
        // Đạt đỉnh ở giữa giai đoạn (hình sin hoặc parabolic)
        const peakBonus = Math.sin(midP * Math.PI) * 0.10;
        targetEnergy = 0.85 + peakBonus;
        targetValence = 0.80 + peakBonus * 0.5;
      } else {
        // Hạ nhiệt từ 0.85 xuống 0.45
        const coolP = (p - 0.8) / 0.2;
        targetEnergy = 0.85 - 0.40 * coolP;
        targetValence = 0.75 - 0.25 * coolP;
      }
      break;

    case 'focus':
      // Tập trung: Giữ mức năng lượng vừa phải (0.42) để không buồn ngủ cũng không gây phân tâm
      targetEnergy = 0.42 + 0.04 * Math.sin(p * 2 * Math.PI);
      targetValence = 0.48 + 0.03 * Math.cos(p * 2 * Math.PI);
      break;

    case 'steady':
    default:
      // Giữ ổn định quanh mức baseline của bài hát mở đầu hoặc mức cân bằng 0.55
      const baseEnergy = baselineProfile?.energy ?? 0.55;
      const baseValence = baselineProfile?.valence ?? 0.55;
      // Dao động nhẹ tự nhiên không quá 0.05
      targetEnergy = baseEnergy + 0.03 * Math.sin(p * Math.PI);
      targetValence = baseValence + 0.02 * Math.cos(p * Math.PI);
      break;
  }

  return {
    targetEnergy: Math.max(0.05, Math.min(0.98, Math.round(targetEnergy * 100) / 100)),
    targetValence: Math.max(0.05, Math.min(0.98, Math.round(targetValence * 100) / 100))
  };
}

module.exports = {
  getTargetArc
};
