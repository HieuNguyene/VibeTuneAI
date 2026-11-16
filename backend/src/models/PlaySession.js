const mongoose = require('mongoose');

const PlaySessionSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      default: null,
      index: true
    },
    arc: {
      type: String,
      enum: ['wind_down', 'workout', 'focus', 'steady'],
      default: 'steady',
      required: true
    },
    startedAt: {
      type: Date,
      default: Date.now,
      index: true
    },
    sessionVec: {
      type: [Number],
      default: []
    },
    audioProfile: {
      energy: { type: Number, default: 0.5, min: 0, max: 1 },
      valence: { type: Number, default: 0.5, min: 0, max: 1 },
      bpm: { type: Number, default: 120, min: 40, max: 240 },
      danceability: { type: Number, default: 0.5, min: 0, max: 1 }
    },
    recentTrackIds: [
      {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Song'
      }
    ],
    dislikedTrackIds: [
      {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Song'
      }
    ],
    position: {
      type: Number,
      default: 0,
      min: 0
    },
    lastEventAt: {
      type: Date,
      default: Date.now
    },
    totalTracksPlayed: {
      type: Number,
      default: 0
    }
  },
  {
    timestamps: true,
    versionKey: false
  }
);

// Compound indexes
PlaySessionSchema.index({ userId: 1, startedAt: -1 });

module.exports = mongoose.model('PlaySession', PlaySessionSchema, 'play_sessions');
