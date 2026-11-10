const ApiError = require('../utils/ApiError');
const logger = require('../utils/logger');
const config = require('../config/environment');

/**
 * Global Error Handling Middleware
 */
const errorHandler = (err, req, res, next) => {
  let error = err;

  // Nếu không phải ApiError chuẩn, chuyển đổi sang ApiError
  if (!(error instanceof ApiError)) {
    const statusCode = error.statusCode || error.status || 500;
    const message = error.message || 'Internal Server Error';
    error = new ApiError(statusCode, message, error.errors || [], err.stack);
  }

  // Mongoose CastError (Sai định dạng ObjectId)
  if (err.name === 'CastError') {
    error = ApiError.badRequest(`Invalid resource identifier: ${err.value}`);
  }

  // Mongoose Duplicate Key Error (Unique Constraint)
  if (err.code === 11000) {
    const field = Object.keys(err.keyValue)[0];
    error = ApiError.conflict(`Duplicate value for '${field}'. Please use another value.`);
  }

  // Mongoose Validation Error
  if (err.name === 'ValidationError') {
    const messages = Object.values(err.errors).map((e) => e.message);
    error = ApiError.badRequest('Validation failed', messages);
  }

  // JsonWebTokenError
  if (err.name === 'JsonWebTokenError') {
    error = ApiError.unauthorized('Invalid authentication token');
  }

  // TokenExpiredError
  if (err.name === 'TokenExpiredError') {
    error = ApiError.unauthorized('Authentication token has expired');
  }

  logger.error(`[${req.method}] ${req.originalUrl} - Status: ${error.statusCode} - ${error.message}`);

  const response = {
    success: false,
    statusCode: error.statusCode,
    message: error.message,
    errors: error.errors,
    timestamp: new Date().toISOString(),
    ...(config.nodeEnv === 'development' && { stack: error.stack })
  };

  return res.status(error.statusCode).json(response);
};

module.exports = errorHandler;
