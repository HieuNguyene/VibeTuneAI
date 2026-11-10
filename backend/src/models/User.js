const mongoose = require('mongoose');

const UserSchema = new mongoose.Schema(
  {
    username: { type: String, required: true, unique: true, lowercase: true, trim: true },
    email: { type: String, unique: true, sparse: true, lowercase: true, trim: true },
    phoneNumber: { type: String, unique: true, sparse: true, trim: true },
    googleId: { type: String, unique: true, sparse: true },
    passwordHash: { type: String, select: false },
    firebaseUid: { type: String, unique: true, sparse: true },
    displayName: { type: String, required: true, trim: true, maxlength: 100 },
    avatarUrl: { type: String, default: '' },
    role: { type: String, enum: ['user', 'artist', 'admin'], default: 'user' },
    isActive: { type: Boolean, default: true }
  },
  { timestamps: true }
);

module.exports = mongoose.model('User', UserSchema);
