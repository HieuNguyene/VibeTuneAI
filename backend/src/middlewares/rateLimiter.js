const rateLimit = require('express-rate-limit');
const ApiResponse = require('../utils/ApiResponse');

/**
 * General API Rate Limiter: 200 requests per 15 minutes
 */
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 200,
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    success: false,
    statusCode: 429,
    message: 'Too many requests from this IP, please try again after 15 minutes.'
  }
});

/**
 * Auth Rate Limiter (Login/Register protection against Brute Force): 20 requests per 15 minutes
 */
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    success: false,
    statusCode: 429,
    message: 'Too many authentication attempts, please try again after 15 minutes.'
  }
});

/**
 * AI Endpoints Rate Limiter (Gemini cost and abuse prevention): 10 requests per minute
 */
const aiLimiter = rateLimit({
  windowMs: 1 * 60 * 1000,
  max: 10,
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    success: false,
    statusCode: 429,
    message: 'AI request limit reached. Please wait a moment before sending another prompt.'
  }
});

module.exports = {
  apiLimiter,
  authLimiter,
  aiLimiter
};
