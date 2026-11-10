const jwt = require('jsonwebtoken');
const config = require('../config/environment');
const ApiError = require('../utils/ApiError');
const User = require('../models/User');

/**
 * Authentication Middleware - Bảo vệ các Private Routes
 */
const authenticate = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      throw ApiError.unauthorized('Authentication token is required (Bearer <token>)');
    }

    const token = authHeader.split(' ')[1];
    const decoded = jwt.verify(token, config.jwtSecret);

    const user = await User.findById(decoded.id).select('-passwordHash');
    if (!user) {
      throw ApiError.unauthorized('User associated with this token no longer exists');
    }

    if (!user.isActive) {
      throw ApiError.forbidden('User account has been deactivated');
    }

    req.user = user;
    next();
  } catch (error) {
    next(error);
  }
};

/**
 * Optional Authentication - Cho phép người dùng vãng lai (Guest) hoặc đã đăng nhập
 */
const optionalAuth = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
      const token = authHeader.split(' ')[1];
      const decoded = jwt.verify(token, config.jwtSecret);
      const user = await User.findById(decoded.id).select('-passwordHash');
      if (user && user.isActive) {
        req.user = user;
      }
    }
    next();
  } catch (error) {
    // Với optional auth, nếu token lỗi/hết hạn thì vẫn cho phép đi tiếp dưới tư cách Guest
    next();
  }
};

/**
 * Role-based Access Control Middleware
 */
const authorize = (...roles) => {
  return (req, res, next) => {
    if (!req.user || !roles.includes(req.user.role)) {
      return next(ApiError.forbidden('You do not have permission to perform this action'));
    }
    next();
  };
};

module.exports = {
  authenticate,
  optionalAuth,
  authorize
};
