const mongoose = require('mongoose');

const UserFavoriteSchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
    songId: { type: mongoose.Schema.Types.ObjectId, ref: 'Song', required: true, index: true },
    createdAt: { type: Date, default: Date.now }
  },
  { timestamps: false }
);

// Compound Unique Index: Ngăn user bấm thích trùng lặp, tối ưu tìm kiếm theo thời gian like
UserFavoriteSchema.index({ userId: 1, songId: 1 }, { unique: true });
UserFavoriteSchema.index({ userId: 1, createdAt: -1 });

module.exports = mongoose.model('UserFavorite', UserFavoriteSchema);
