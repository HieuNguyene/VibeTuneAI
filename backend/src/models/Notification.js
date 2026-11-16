const mongoose = require('mongoose');

const NotificationSchema = new mongoose.Schema(
  {
    recipientId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true
    },
    senderId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      default: null
    },
    senderName: {
      type: String,
      default: 'Ban Quản trị VibeTune'
    },
    senderAvatar: {
      type: String,
      default: ''
    },
    title: {
      type: String,
      required: true,
      trim: true,
      maxlength: 200
    },
    content: {
      type: String,
      required: true,
      trim: true,
      maxlength: 2000
    },
    imageUrl: {
      type: String,
      default: ''
    },
    type: {
      type: String,
      enum: ['ADMIN_ANNOUNCEMENT', 'PLAYLIST_LIKED', 'SYSTEM_UPDATE', 'GENERAL'],
      default: 'GENERAL',
      index: true
    },
    metadata: {
      playlistId: { type: mongoose.Schema.Types.ObjectId, ref: 'Playlist', default: null },
      songId: { type: mongoose.Schema.Types.ObjectId, ref: 'Song', default: null },
      targetScope: { type: String, enum: ['all', 'single', 'multiple', 'auto'], default: 'auto' },
      extra: { type: mongoose.Schema.Types.Mixed, default: {} }
    },
    isRead: {
      type: Boolean,
      default: false,
      index: true
    },
    readAt: {
      type: Date,
      default: null
    }
  },
  { timestamps: true }
);

// Indexes toi uu truy van danh sach thong bao
NotificationSchema.index({ recipientId: 1, createdAt: -1 });
NotificationSchema.index({ recipientId: 1, isRead: 1, createdAt: -1 });

module.exports = mongoose.model('Notification', NotificationSchema);
