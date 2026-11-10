const mongoose = require('mongoose');

const PlaylistTrackSchema = new mongoose.Schema(
  {
    playlistId: { type: mongoose.Schema.Types.ObjectId, ref: 'Playlist', required: true, index: true },
    songId: { type: mongoose.Schema.Types.ObjectId, ref: 'Song', required: true, index: true },
    order: { type: Number, required: true, default: 0 },
    addedAt: { type: Date, default: Date.now }
  },
  { timestamps: false }
);

// Compound Unique Index: Không cho phép duplicate bài hát trong cùng 1 playlist
PlaylistTrackSchema.index({ playlistId: 1, songId: 1 }, { unique: true });
// Compound Index: Sắp xếp theo thứ tự hiển thị
PlaylistTrackSchema.index({ playlistId: 1, order: 1 });

module.exports = mongoose.model('PlaylistTrack', PlaylistTrackSchema);
