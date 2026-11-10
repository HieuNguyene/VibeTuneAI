const mongoose = require('mongoose');

const UserPreferenceSchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, unique: true, index: true },
    favoriteGenreIds: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Genre' }],
    favoriteArtistIds: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Artist' }],
    streamingQuality: {
      type: String,
      enum: ['low', 'normal', 'high', 'lossless'],
      default: 'normal'
    },
    themeMode: {
      type: String,
      enum: ['dark', 'system'],
      default: 'dark'
    },
    allowAiRecommendations: { type: Boolean, default: true }
  },
  { timestamps: true }
);

module.exports = mongoose.model('UserPreference', UserPreferenceSchema);
