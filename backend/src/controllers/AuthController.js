const AuthService = require('../services/authService');
const ApiResponse = require('../utils/ApiResponse');

class AuthController {
  static async register(req, res, next) {
    try {
      const { username, email, password, displayName } = req.body;
      const result = await AuthService.register({ username, email, password, displayName });
      return ApiResponse.created(res, result, 'User registered successfully');
    } catch (error) {
      next(error);
    }
  }

  static async login(req, res, next) {
    try {
      const { identifier, password } = req.body;
      const result = await AuthService.login({ identifier, password });
      return ApiResponse.success(res, result, 'Login successful');
    } catch (error) {
      next(error);
    }
  }

  static async getMe(req, res, next) {
    try {
      const result = await AuthService.getProfile(req.user._id);
      return ApiResponse.success(res, result, 'Profile retrieved successfully');
    } catch (error) {
      next(error);
    }
  }

  static async updatePreferences(req, res, next) {
    try {
      const result = await AuthService.updatePreferences(req.user._id, req.body);
      return ApiResponse.success(res, result, 'Preferences updated successfully');
    } catch (error) {
      next(error);
    }
  }

  static async updateProfile(req, res, next) {
    try {
      const { displayName, avatarUrl } = req.body;
      const result = await AuthService.updateProfile(req.user._id, { displayName, avatarUrl });
      return ApiResponse.success(res, result, 'Cập nhật hồ sơ thành công');
    } catch (error) {
      next(error);
    }
  }

  static async changePassword(req, res, next) {
    try {
      const { currentPassword, newPassword } = req.body;
      const result = await AuthService.changePassword(req.user._id, { currentPassword, newPassword });
      return ApiResponse.success(res, result, 'Đổi mật khẩu thành công');
    } catch (error) {
      next(error);
    }
  }

  static async sendPhoneOtp(req, res, next) {
    try {
      const { phoneNumber } = req.body;
      const result = await AuthService.sendPhoneOtp({ phoneNumber });
      return ApiResponse.success(res, result, 'Mã OTP đã được gửi thành công');
    } catch (error) {
      next(error);
    }
  }

  static async verifyPhoneOtp(req, res, next) {
    try {
      const { phoneNumber, otp, displayName, password } = req.body;
      const result = await AuthService.verifyPhoneOtp({ phoneNumber, otp, displayName, password });
      return ApiResponse.created(res, result, 'Đăng ký số điện thoại thành công');
    } catch (error) {
      next(error);
    }
  }

  static async loginWithGoogle(req, res, next) {
    try {
      const { googleId, email, displayName, avatarUrl } = req.body;
      const result = await AuthService.loginWithGoogle({ googleId, email, displayName, avatarUrl });
      return ApiResponse.success(res, result, 'Đăng nhập Google thành công');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = AuthController;
