const mongoose = require('mongoose');

const ListeningEventSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      default: null,
      index: true
    },
    sessionId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'PlaySession',
      default: null,
      index: true
    },
    trackId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'Song',
      required: true,
      index: true
    },
    event: {
      type: String,
      enum: ['play', 'skip', 'complete', 'like', 'dislike'],
      required: true,
      index: true
    },
    positionSec: {
      type: Number,
      default: 0,
      min: 0
    },
    source: {
      type: String,
      enum: ['moodflow', 'chat', 'browse', 'playlist', 'home_feed', 'search'],
      default: 'browse'
    },
    ts: {
      type: Date,
      default: Date.now,
      index: true
    }
  },
  {
    timestamps: false,
    versionKey: false
  }
);

// Compound indexes
ListeningEventSchema.index({ sessionId: 1, ts: 1 });
ListeningEventSchema.index({ trackId: 1, event: 1 });
ListeningEventSchema.index({ userId: 1, ts: -1 });
ListeningEventSchema.index({ trackId: 1, ts: -1 });
ListeningEventSchema.index({ userId: 1, event: 1 });

module.exports = mongoose.model('ListeningEvent', ListeningEventSchema, 'listening_events');
