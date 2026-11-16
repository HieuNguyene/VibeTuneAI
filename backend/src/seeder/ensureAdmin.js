const bcrypt = require('bcryptjs');
const User = require('../models/User');
const UserPreference = require('../models/UserPreference');
const config = require('../config/environment');
const logger = require('../utils/logger');

/**
 * Đảm bảo hệ thống luôn có ít nhất 1 tài khoản Quản trị viên (role: 'admin')
 * được cấu hình từ biến môi trường ADMIN_EMAIL / ADMIN_PASSWORD (an toàn idempotent).
 */
async function ensureAdminUser() {
  const email = (config.adminEmail || '').toLowerCase().trim();
  const password = config.adminPassword || '';

  if (!email) {
    const existingAdmin = await User.findOne({ role: 'admin', isActive: true }).select('_id email').lean();
    if (!existingAdmin) {
      logger.warn('⚠️ Chưa có tài khoản admin nào và ADMIN_EMAIL chưa được cấu hình.');
    }
    return existingAdmin;
  }

  let user = await User.findOne({ email }).select('+passwordHash');
  if (user) {
    let changed = false;
    if (user.role !== 'admin') {
      user.role = 'admin';
      changed = true;
    }
    if (!user.isActive) {
      user.isActive = true;
      changed = true;
    }
    if (password && config.adminSyncPassword) {
      const salt = await bcrypt.genSalt(10);
      user.passwordHash = await bcrypt.hash(password, salt);
      changed = true;
    }
    if (changed) {
      await user.save();
    }
    return user;
  }

  if (!password) {
    const existingAdmin = await User.findOne({ role: 'admin', isActive: true }).select('_id email').lean();
    if (!existingAdmin) {
      logger.warn(`⚠️ Không thể khởi tạo admin ${email} vì ADMIN_PASSWORD chưa được thiết lập.`);
    }
    return existingAdmin;
  }

  let username = (email.split('@')[0] || 'admin').toLowerCase().replace(/[^a-z0-9_]/g, '');
  if (!username) username = 'admin';
  const usernameTaken = await User.findOne({ username }).lean();
  if (usernameTaken) {
    username = `${username}_admin_${Date.now().toString().slice(-4)}`;
  }

  const salt = await bcrypt.genSalt(10);
  const passwordHash = await bcrypt.hash(password, salt);

  user = await User.create({
    username,
    email,
    passwordHash,
    displayName: 'Quản trị viên VibeTune',
    avatarUrl: `https://api.dicebear.com/7.x/initials/png?seed=${encodeURIComponent('Admin VibeTune')}`,
    role: 'admin',
    isActive: true
  });

  await UserPreference.create({
    userId: user._id,
    favoriteGenreIds: [],
    favoriteArtistIds: [],
    streamingQuality: 'high',
    themeMode: 'dark',
    allowAiRecommendations: true
  });

  logger.success(`🛡️ Đã đảm bảo tài khoản Admin hệ thống: ${email} (role: admin)`);
  return user;
}

module.exports = ensureAdminUser;
