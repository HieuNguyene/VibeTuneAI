const mongoose = require('mongoose');

const ListeningHistorySchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
    songId: { type: mongoose.Schema.Types.ObjectId, ref: 'Song', required: true, index: true },
    listenDuration: { type: Number, required: true, min: 0 }, // Thời gian nghe thực tế (giây)
    completed: { type: Boolean, default: false }, // Nghe > 80% thời lượng
    skipped: { type: Boolean, default: false },   // Skip trước 30 giây
    playbackSource: {
      type: String,
      enum: ['home_feed', 'playlist', 'album', 'ai_recommendation', 'search'],
      default: 'home_feed'
    },
    timestamp: { type: Date, default: Date.now, index: true }
  },
  { timestamps: false }
);

// Compound Index: Phục vụ AI Recommender Engine truy xuất lịch sử nghe nhạc gần nhất
ListeningHistorySchema.index({ userId: 1, timestamp: -1 });
ListeningHistorySchema.index({ songId: 1, timestamp: -1 });
ListeningHistorySchema.index({ userId: 1, songId: 1 });

module.exports = mongoose.model('ListeningHistory', ListeningHistorySchema);
