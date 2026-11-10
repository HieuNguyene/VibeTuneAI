const mongoose = require('mongoose');

const ArtistSchema = new mongoose.Schema(
  {
    name: { type: String, required: true, trim: true, maxlength: 150 },
    slug: { type: String, required: true, unique: true, lowercase: true, trim: true },
    avatarUrl: { type: String, default: '' },
    bio: { type: String, default: '', maxlength: 2000 },
    followersCount: { type: Number, default: 0, min: 0 },
    isVerified: { type: Boolean, default: false },
    genreIds: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Genre' }]
  },
  { timestamps: true }
);

ArtistSchema.index({ name: 'text' });
ArtistSchema.index({ followersCount: -1 });

module.exports = mongoose.model('Artist', ArtistSchema);
