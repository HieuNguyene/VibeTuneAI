const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { exec } = require('child_process');
const User = require('../models/User');
const UserPreference = require('../models/UserPreference');
const OtpVerification = require('../models/OtpVerification');
const SmsService = require('./smsService');
const ApiError = require('../utils/ApiError');
const config = require('../config/environment');

class AuthService {
  /**
   * Tạo JWT Token từ thông tin User
   */
  static generateToken(user) {
    return jwt.sign(
      {
        id: user._id,
        email: user.email,
        role: user.role
      },
      config.jwtSecret,
      { expiresIn: config.jwtExpiresIn }
    );
  }

  /**
   * Đăng ký người dùng mới
   */
  static async register({ username, email, password, displayName }) {
    // 1. Kiểm tra username hoặc email đã tồn tại chưa
    const existingUser = await User.findOne({
      $or: [{ email: email.toLowerCase() }, { username: username.toLowerCase() }]
    });

    if (existingUser) {
      if (existingUser.email === email.toLowerCase()) {
        throw ApiError.conflict('Email is already registered');
      }
      throw ApiError.conflict('Username is already taken');
    }

    // 2. Hash mật khẩu bằng bcrypt
    const salt = await bcrypt.genSalt(10);
    const passwordHash = await bcrypt.hash(password, salt);

    // 3. Tạo User document
    const user = await User.create({
      username: username.toLowerCase().trim(),
      email: email.toLowerCase().trim(),
      passwordHash,
      displayName: displayName.trim(),
      avatarUrl: `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(displayName)}`
    });

    // 4. Khởi tạo 1-1 UserPreference mặc định
    await UserPreference.create({
      userId: user._id,
      favoriteGenreIds: [],
      favoriteArtistIds: [],
      streamingQuality: 'normal',
      themeMode: 'dark',
      allowAiRecommendations: true
    });

    // 5. Sinh JWT Token
    const token = this.generateToken(user);

    return {
      user: {
        _id: user._id,
        username: user.username,
        email: user.email,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        role: user.role,
        createdAt: user.createdAt
      },
      token
    };
  }

  /**
   * Đăng nhập bằng Email hoặc Username
   */
  static async login({ identifier, password }) {
    if (!identifier || !password) {
      throw ApiError.badRequest('Email/Username and password are required');
    }

    // 1. Tìm user theo email, username hoặc số điện thoại và lấy kèm passwordHash
    const normalizedIdentifier = identifier.toLowerCase().trim();
    const rawIdentifier = identifier.trim();
    const user = await User.findOne({
      $or: [
        { email: normalizedIdentifier },
        { username: normalizedIdentifier },
        { phoneNumber: rawIdentifier }
      ]
    }).select('+passwordHash');

    if (!user) {
      throw ApiError.unauthorized('Invalid login credentials');
    }

    if (!user.isActive) {
      throw ApiError.forbidden('Your account has been deactivated. Please contact support.');
    }

    // 2. So khớp mật khẩu bcrypt
    const isMatch = await bcrypt.compare(password, user.passwordHash);
    if (!isMatch) {
      throw ApiError.unauthorized('Invalid login credentials');
    }

    // 3. Sinh token
    const token = this.generateToken(user);

    // 4. Lấy kèm preferences
    const preferences = await UserPreference.findOne({ userId: user._id });

    return {
      user: {
        _id: user._id,
        username: user.username,
        email: user.email,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        role: user.role,
        preferences
      },
      token
    };
  }

  /**
   * Lấy Profile người dùng hiện tại
   */
  static async getProfile(userId) {
    const user = await User.findById(userId).select('-passwordHash');
    if (!user) {
      throw ApiError.notFound('User not found');
    }

    const preferences = await UserPreference.findOne({ userId })
      .populate('favoriteGenreIds', 'name colorCode')
      .populate('favoriteArtistIds', 'name avatarUrl');

    return {
      user,
      preferences
    };
  }

  /**
   * Cập nhật gu âm nhạc & tuỳ chọn người dùng
   */
  static async updatePreferences(userId, updateData) {
    const preferences = await UserPreference.findOneAndUpdate(
      { userId },
      { $set: updateData },
      { new: true, upsert: true, runValidators: true }
    )
      .populate('favoriteGenreIds', 'name colorCode')
      .populate('favoriteArtistIds', 'name avatarUrl');

    return preferences;
  }

  /**
   * Cập nhật thông tin hồ sơ (displayName, avatarUrl)
   */
  static async updateProfile(userId, { displayName, avatarUrl }) {
    const updateData = {};
    if (displayName && displayName.trim()) {
      updateData.displayName = displayName.trim();
    }
    if (avatarUrl !== undefined && avatarUrl !== null) {
      updateData.avatarUrl = avatarUrl.trim();
    }

    if (Object.keys(updateData).length === 0) {
      throw ApiError.badRequest('No profile data provided to update');
    }

    const updatedUser = await User.findByIdAndUpdate(
      userId,
      { $set: updateData },
      { new: true, runValidators: true }
    ).select('-passwordHash');

    if (!updatedUser) {
      throw ApiError.notFound('User not found');
    }

    return updatedUser;
  }

  /**
   * Đổi mật khẩu tài khoản
   */
  static async changePassword(userId, { currentPassword, newPassword }) {
    if (!currentPassword || !newPassword) {
      throw ApiError.badRequest('Current password and new password are required');
    }

    if (newPassword.length < 8) {
      throw ApiError.badRequest('Mật khẩu mới phải có ít nhất 8 ký tự');
    }

    const user = await User.findById(userId).select('+passwordHash');
    if (!user) {
      throw ApiError.notFound('User not found');
    }

    // So khớp mật khẩu cũ
    const isMatch = await bcrypt.compare(currentPassword, user.passwordHash);
    if (!isMatch) {
      throw ApiError.badRequest('Mật khẩu hiện tại không chính xác');
    }

    // Băm mật khẩu mới
    const salt = await bcrypt.genSalt(10);
    user.passwordHash = await bcrypt.hash(newPassword, salt);
    await user.save();

    return { message: 'Đổi mật khẩu thành công' };
  }

  /**
   * Gửi mã OTP xác thực qua số điện thoại
   */
  static async sendPhoneOtp({ phoneNumber }) {
    if (!phoneNumber || phoneNumber.trim().length < 8) {
      throw ApiError.badRequest('Số điện thoại không hợp lệ');
    }

    const cleanPhone = phoneNumber.trim();

    // Kiểm tra số điện thoại đã tồn tại chưa
    const existing = await User.findOne({ phoneNumber: cleanPhone });
    if (existing) {
      throw ApiError.conflict('Số điện thoại này đã được đăng ký tài khoản');
    }

    // Sinh mã OTP 6 số
    const otp = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 phút

    // Lưu vào database
    await OtpVerification.deleteMany({ phoneNumber: cleanPhone });
    await OtpVerification.create({ phoneNumber: cleanPhone, otp, expiresAt });

    // Gửi tin nhắn SMS OTP (Hỗ trợ Twilio, eSMS.vn và máy ảo ADB)
    await SmsService.sendOtp(cleanPhone, otp);

    console.log(`\n======================================================`);
    console.log(`[XÁC THỰC OTP SỐ ĐIỆN THOẠI: ${cleanPhone}]`);
    console.log(`>> MÃ OTP: [ ${otp} ] (Hiệu lực 5 phút) <<`);
    console.log(`======================================================\n`);

    return {
      message: 'Mã xác thực OTP đã được gửi đến số điện thoại của bạn',
      expiresIn: 300
    };
  }

  /**
   * Xác thực mã OTP và hoàn tất đăng ký tài khoản bằng số điện thoại
   */
  static async verifyPhoneOtp({ phoneNumber, otp, displayName, password }) {
    if (!phoneNumber || !otp || !displayName || !password) {
      throw ApiError.badRequest('Vui lòng điền đầy đủ số điện thoại, mã OTP, họ tên và mật khẩu');
    }

    if (password.length < 6) {
      throw ApiError.badRequest('Mật khẩu phải có ít nhất 6 ký tự');
    }

    const cleanPhone = phoneNumber.trim();
    const cleanOtp = otp.trim();

    // 1. Kiểm tra OTP
    const record = await OtpVerification.findOne({ phoneNumber: cleanPhone, otp: cleanOtp });
    if (!record || record.expiresAt < new Date()) {
      throw ApiError.badRequest('Mã OTP không chính xác hoặc đã hết hạn');
    }

    // 2. Kiểm tra số điện thoại tồn tại
    const existingUser = await User.findOne({ phoneNumber: cleanPhone });
    if (existingUser) {
      throw ApiError.conflict('Số điện thoại này đã được đăng ký tài khoản');
    }

    // 3. Hash mật khẩu
    const salt = await bcrypt.genSalt(10);
    const passwordHash = await bcrypt.hash(password, salt);

    // 4. Tạo User document
    const numericPhone = cleanPhone.replace(/[^0-9]/g, '');
    let username = `user_${numericPhone}`;
    const usernameTaken = await User.findOne({ username });
    if (usernameTaken) {
      username = `${username}_${Math.floor(1000 + Math.random() * 9000)}`;
    }
    const email = `${numericPhone}@phone.vibe.vn`;

    const user = await User.create({
      username,
      email,
      phoneNumber: cleanPhone,
      passwordHash,
      displayName: displayName.trim(),
      avatarUrl: `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(displayName.trim())}`
    });

    // 5. Khởi tạo UserPreference mặc định
    await UserPreference.create({
      userId: user._id,
      favoriteGenreIds: [],
      favoriteArtistIds: [],
      streamingQuality: 'normal',
      themeMode: 'dark',
      allowAiRecommendations: true
    });

    // 6. Xóa OTP đã sử dụng
    await OtpVerification.deleteMany({ phoneNumber: cleanPhone });

    // 7. Tạo JWT Token
    const token = this.generateToken(user);

    return {
      user: {
        _id: user._id,
        username: user.username,
        email: user.email,
        phoneNumber: user.phoneNumber,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        role: user.role,
        createdAt: user.createdAt
      },
      token
    };
  }

  /**
   * Đăng nhập hoặc liên kết bằng Google Account
   */
  static async loginWithGoogle({ googleId, email, displayName, avatarUrl }) {
    if (!googleId && !email) {
      throw ApiError.badRequest('Thông tin tài khoản Google không hợp lệ');
    }

    let user = null;
    if (googleId) {
      user = await User.findOne({ googleId });
    }

    if (!user && email) {
      user = await User.findOne({ email: email.toLowerCase().trim() });
      if (user) {
        if (googleId && !user.googleId) {
          user.googleId = googleId;
        }
        if (avatarUrl && !user.avatarUrl) {
          user.avatarUrl = avatarUrl;
        }
        await user.save();
      }
    }

    if (!user) {
      const basePrefix = email ? email.split('@')[0].replace(/[^a-zA-Z0-9_]/g, '') : `google_${(googleId || '').slice(-6)}`;
      let username = basePrefix || `user_${Date.now()}`;
      const usernameTaken = await User.findOne({ username });
      if (usernameTaken) {
        username = `${username}_${Math.floor(1000 + Math.random() * 9000)}`;
      }

      user = await User.create({
        username,
        email: email ? email.toLowerCase().trim() : `${username}@google.vibe.vn`,
        googleId,
        displayName: displayName?.trim() || (email ? email.split('@')[0] : 'Google User'),
        avatarUrl: avatarUrl || `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(displayName || 'Google')}`
      });

      await UserPreference.create({
        userId: user._id,
        favoriteGenreIds: [],
        favoriteArtistIds: [],
        streamingQuality: 'normal',
        themeMode: 'dark',
        allowAiRecommendations: true
      });
    }

    if (!user.isActive) {
      throw ApiError.forbidden('Tài khoản của bạn đã bị khóa. Vui lòng liên hệ hỗ trợ.');
    }

    const token = this.generateToken(user);
    const preferences = await UserPreference.findOne({ userId: user._id });

    return {
      user: {
        _id: user._id,
        username: user.username,
        email: user.email,
        phoneNumber: user.phoneNumber,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        role: user.role,
        preferences
      },
      token
    };
  }
}

module.exports = AuthService;
