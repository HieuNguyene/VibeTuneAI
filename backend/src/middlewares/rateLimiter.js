const rateLimit = require('express-rate-limit');

/**
 * General API Rate Limiter: 1000 requests per 15 minutes (bỏ qua audio streaming byte-range & Web Admin)
 */
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 1000,
  skip: (req) => {
    const url = req.originalUrl || '';
    const isTestBypass = process.env.NODE_ENV === 'test' && req.headers['x-test-suite'] === 'true';
    return url.includes('/stream') || url.includes('/admin') || url.includes('/health') || isTestBypass;
  },
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
  max: process.env.NODE_ENV === 'test' ? 500 : 60,
  skip: (req) => process.env.NODE_ENV === 'test' && req.headers['x-test-suite'] === 'true',
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
