/**
 * Cấu hình trọng số và tham số cho thuật toán Mood Flow
 */
module.exports = {
  // Trọng số các thành phần điểm số (tổng = 1.0)
  weights: {
    sim: 0.35,      // Độ tương đồng ngữ nghĩa qua vector embedding
    smooth: 0.25,   // Độ mượt chuyển tiếp âm học (energy, valence, bpm, danceability)
    arc: 0.20,      // Mức độ bám theo đường cong hành trình tâm trạng (arc curve)
    taste: 0.15,    // Sở thích người dùng dựa trên tương tác lịch sử
    novelty: 0.05   // Tính mới mẻ / khám phá (novelty)
  },

  // Điểm phạt (penalties)
  penalties: {
    sameArtistImmediate: -0.15, // Cùng nghệ sĩ với bài vừa phát ngay trước
    sameArtistRecent: -0.40,    // Đã phát nghệ sĩ này trong 2 bài gần nhất
    recentTrack: -0.50,         // Nằm trong danh sách bài vừa nghe
    disliked: -1.0              // Bị người dùng dislike
  },

  // Softmax sampling temperature (T càng nhỏ càng chọn bài điểm cao nhất, T lớn tăng tính ngẫu nhiên)
  temperature: 0.3,

  // Tham số cập nhật Session Vector và Audio Profile
  learningRate: {
    alphaComplete: 0.3, // Vector dịch chuyển về bài nghe trọn vẹn
    betaSkip: 0.15,     // Vector né tránh bài bị skip sớm
    alphaAudio: 0.2     // Trọng số bài mới trong trung bình động âm học (0.8 cũ + 0.2 mới)
  },

  // Cấu hình ứng viên (Candidate Pool)
  pool: {
    defaultSize: 80,
    vectorRatio: 0.60,      // 60% từ Vector Search
    metadataRatio: 0.25,    // 25% từ Audio Features & Moods matching
    discoveryRatio: 0.15,   // 15% ngẫu nhiên mở rộng khám phá
    maxRecentTracks: 20     // Số bài tối đa giữ trong recentTrackIds
  },

  // Danh sách các Arc hợp lệ
  validArcs: ['wind_down', 'workout', 'focus', 'steady']
};
