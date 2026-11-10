const mongoose = require('mongoose');

const AlbumSchema = new mongoose.Schema(
  {
    title: { type: String, required: true, trim: true, maxlength: 200 },
    artistId: { type: mongoose.Schema.Types.ObjectId, ref: 'Artist', required: true, index: true },
    coverImageUrl: { type: String, default: '' },
    releaseYear: { type: Number, default: () => new Date().getFullYear() },
    genreIds: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Genre' }],
    totalTracks: { type: Number, default: 0, min: 0 }
  },
  { timestamps: true }
);

AlbumSchema.index({ title: 'text' });

module.exports = mongoose.model('Album', AlbumSchema);
