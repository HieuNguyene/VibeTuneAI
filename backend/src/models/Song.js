const mongoose = require('mongoose');

const SongSchema = new mongoose.Schema(
  {
    title: { type: String, required: true, trim: true, maxlength: 255 },
    artistId: { type: mongoose.Schema.Types.ObjectId, ref: 'Artist', required: true, index: true },
    featuredArtistIds: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Artist', index: true }],
    artistIds: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Artist', index: true }],
    albumId: { type: mongoose.Schema.Types.ObjectId, ref: 'Album', default: null, index: true },
    genreIds: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Genre', required: true, index: true }],
    duration: { type: Number, required: true, min: 1 }, // thời lượng tính bằng giây (seconds)
    audioUrl: { type: String, required: true },
    coverImageUrl: { type: String, default: '' },
    lyrics: { type: String, default: '' },
    composer: { type: String, default: '', trim: true },
    composerId: { type: mongoose.Schema.Types.ObjectId, ref: 'Artist', default: null, index: true },
    composerIds: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Artist', index: true }],
    filePath: { type: String, default: '' },
    year: { type: Number, default: 2024 },
    language: { type: String, default: 'vi' },
    tags: [{
      type: String,
      trim: true,
      lowercase: true
    }],
    semanticText: { type: String, default: '' },
    mood: [{
      type: String,
      trim: true,
      lowercase: true
    }],
    moods: [{
      type: String,
      trim: true,
      lowercase: true
    }],
    activities: [{
      type: String,
      trim: true,
      lowercase: true
    }],
    description: { type: String, default: '' },
    tempo: {
      type: String,
      enum: ['slow', 'medium', 'fast'],
      default: 'medium'
    },
    audio: {
      bpm: { type: Number, default: 120 },
      energy: { type: Number, default: 0.5, min: 0, max: 1 },
      valence: { type: Number, default: 0.5, min: 0, max: 1 },
      danceability: { type: Number, default: 0.5, min: 0, max: 1 },
      acousticness: { type: Number, default: 0.3, min: 0, max: 1 }
    },
    audioFeatures: {
      danceability: { type: Number, default: 0.5, min: 0, max: 1 },
      energy: { type: Number, default: 0.5, min: 0, max: 1 },
      valence: { type: Number, default: 0.5, min: 0, max: 1 },
      tempoBpm: { type: Number, default: 120, min: 40, max: 240 },
      acousticness: { type: Number, default: 0.3, min: 0, max: 1 },
      instrumentalness: { type: Number, default: 0.1, min: 0, max: 1 },
      liveness: { type: Number, default: 0.15, min: 0, max: 1 },
      speechiness: { type: Number, default: 0.1, min: 0, max: 1 }
    },
    embedding: [{ type: Number }],
    tagSource: {
      type: String,
      enum: ['auto', 'manual'],
      default: 'auto'
    },
    analyzedAt: { type: Date, default: null },
    playsCount: { type: Number, default: 0, min: 0 },
    likesCount: { type: Number, default: 0, min: 0 },
    isPublic: { type: Boolean, default: true }
  },
  { timestamps: true }
);

// Tự động đồng bộ mảng đa nghệ sĩ & đa nhạc sĩ và nhận diện nhạc sĩ khi thêm/cập nhật
SongSchema.pre('validate', async function () {
  // 1. Đồng bộ artistIds <-> artistId & featuredArtistIds
  if (this.artistIds && this.artistIds.length > 0) {
    if (!this.artistId) this.artistId = this.artistIds[0];
    if (!this.featuredArtistIds || this.featuredArtistIds.length === 0) {
      this.featuredArtistIds = this.artistIds.slice(1);
    }
  } else if (this.artistId) {
    const list = [this.artistId];
    if (this.featuredArtistIds && this.featuredArtistIds.length > 0) {
      for (const fId of this.featuredArtistIds) {
        if (fId && !list.some(id => String(id) === String(fId))) {
          list.push(fId);
        }
      }
    }
    this.artistIds = list;
  }

  // 2. Nhận diện composer nếu chưa có
  const ComposerService = require('../services/composerService');
  if (!this.composer || this.composer.trim() === '') {
    try {
      let artistName = '';
      if (this.artistId) {
        const artistDoc = await mongoose.model('Artist').findById(this.artistId).select('name').lean();
        if (artistDoc) artistName = artistDoc.name;
      }
      const detected = await ComposerService.detectComposer({
        title: this.title,
        artistName: artistName,
        lyrics: this.lyrics,
        audioUrl: this.audioUrl
      });
      if (detected) {
        this.composer = detected;
      }
    } catch (e) {
      console.warn('[Song pre-validate] Warning detecting composer:', e.message);
    }
  }

  // 3. Đồng bộ composerIds & composerId từ chuỗi composer
  if (this.composer && this.composer.trim()) {
    try {
      if (!this.composerIds || this.composerIds.length === 0) {
        const cIds = await ComposerService.findOrCreateMultipleComposers(this.composer, this.genreIds);
        if (cIds && cIds.length > 0) {
          this.composerIds = cIds;
          this.composerId = cIds[0];
        }
      } else {
        if (!this.composerId) this.composerId = this.composerIds[0];
      }
    } catch (e) {
      console.warn('[Song pre-validate] Warning synchronizing composerIds:', e.message);
    }
  }
});

SongSchema.index({ title: 'text', lyrics: 'text', composer: 'text' }, { default_language: 'none', language_override: 'none' });
SongSchema.index({ composer: 1 });
SongSchema.index({ playsCount: -1 });
SongSchema.index({ createdAt: -1 });
SongSchema.index({ isPublic: 1, playsCount: -1 });
SongSchema.index({ isPublic: 1, createdAt: -1 });
SongSchema.index({ artistId: 1, isPublic: 1 });
SongSchema.index({ artistIds: 1, isPublic: 1 });
SongSchema.index({ featuredArtistIds: 1, isPublic: 1 });
SongSchema.index({ genreIds: 1, isPublic: 1 });
SongSchema.index({ title: 1, artistId: 1 });
SongSchema.index({ tags: 1 });
SongSchema.index({ moods: 1 });
SongSchema.index({ activities: 1 });
SongSchema.index({ language: 1 });
SongSchema.index({ 'audio.energy': 1 });
SongSchema.index({ tagSource: 1 });

module.exports = mongoose.model('Song', SongSchema);
