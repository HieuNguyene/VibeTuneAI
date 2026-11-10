const mongoose = require('mongoose');

const SongSchema = new mongoose.Schema(
  {
    title: { type: String, required: true, trim: true, maxlength: 255 },
    artistId: { type: mongoose.Schema.Types.ObjectId, ref: 'Artist', required: true, index: true },
    featuredArtistIds: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Artist' }],
    albumId: { type: mongoose.Schema.Types.ObjectId, ref: 'Album', default: null, index: true },
    genreIds: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Genre', required: true, index: true }],
    duration: { type: Number, required: true, min: 1 }, // thời lượng tính bằng giây (seconds)
    audioUrl: { type: String, required: true },
    coverImageUrl: { type: String, default: '' },
    lyrics: { type: String, default: '' },
    mood: [{
      type: String,
      trim: true,
      lowercase: true
    }],
    tempo: {
      type: String,
      enum: ['slow', 'medium', 'fast'],
      default: 'medium'
    },
    playsCount: { type: Number, default: 0, min: 0 },
    likesCount: { type: Number, default: 0, min: 0 },
    isPublic: { type: Boolean, default: true }
  },
  { timestamps: true }
);

SongSchema.index({ title: 'text', lyrics: 'text' });
SongSchema.index({ playsCount: -1 });
SongSchema.index({ createdAt: -1 });

module.exports = mongoose.model('Song', SongSchema);
