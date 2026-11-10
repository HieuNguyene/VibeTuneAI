const mongoose = require('mongoose');

const AiPromptLogSchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
    userPrompt: { type: String, required: true, trim: true, maxlength: 500 },
    aiExtractedCriteria: {
      mood: [{ type: String }],
      genres: [{ type: String }],
      tempo: { type: String },
      keywords: [{ type: String }],
      targetDurationSeconds: { type: Number }
    },
    suggestedSongIds: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Song' }],
    generatedPlaylistId: { type: mongoose.Schema.Types.ObjectId, ref: 'Playlist', default: null },
    provider: { type: String, default: 'gemini-1.5-flash' },
    latencyMs: { type: Number, default: 0 },
    tokensUsed: {
      promptTokens: { type: Number, default: 0 },
      completionTokens: { type: Number, default: 0 },
      totalTokens: { type: Number, default: 0 }
    },
    createdAt: { type: Date, default: Date.now, index: true }
  },
  { timestamps: false }
);

AiPromptLogSchema.index({ userId: 1, createdAt: -1 });

module.exports = mongoose.model('AiPromptLog', AiPromptLogSchema);
