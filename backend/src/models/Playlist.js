const mongoose = require('mongoose');

const PlaylistSchema = new mongoose.Schema(
  {
    title: { type: String, required: true, trim: true, maxlength: 150 },
    description: { type: String, default: '', maxlength: 500 },
    creatorId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
    isAiGenerated: { type: Boolean, default: false, index: true },
    aiPrompt: { type: String, default: null },
    coverImageUrl: { type: String, default: '' },
    isPublic: { type: Boolean, default: true },
    totalTracks: { type: Number, default: 0, min: 0 }
  },
  { timestamps: true }
);

PlaylistSchema.index({ creatorId: 1, createdAt: -1 });
PlaylistSchema.index({ isPublic: 1, createdAt: -1 });

module.exports = mongoose.model('Playlist', PlaylistSchema);
