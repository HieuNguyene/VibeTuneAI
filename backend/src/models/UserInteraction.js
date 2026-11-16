const mongoose = require('mongoose');

const INTERACTION_WEIGHTS = {
  LIKE: 1.0,
  LISTEN_COMPLETE: 0.7,
  PLAY: 0.2,
  SKIP: -0.5
};

const UserInteractionSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true
    },
    songId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'Song',
      required: true,
      index: true
    },
    interactionType: {
      type: String,
      enum: ['LIKE', 'LISTEN_COMPLETE', 'SKIP', 'PLAY'],
      required: true
    },
    weight: {
      type: Number,
      required: true,
      default: function () {
        return INTERACTION_WEIGHTS[this.interactionType] ?? 0.2;
      }
    },
    createdAt: {
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

// Compound indexes phục vụ truy vấn lịch sử và cá nhân hóa nhanh
UserInteractionSchema.index({ userId: 1, createdAt: -1 });
UserInteractionSchema.index({ userId: 1, songId: 1 });
UserInteractionSchema.index({ songId: 1, interactionType: 1 });

UserInteractionSchema.statics.WEIGHTS = INTERACTION_WEIGHTS;

module.exports = mongoose.model('UserInteraction', UserInteractionSchema, 'user_interactions');
