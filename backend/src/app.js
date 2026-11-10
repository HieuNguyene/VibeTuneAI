const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const routes = require('./routes');
const errorHandler = require('./middlewares/errorHandler');
const { apiLimiter } = require('./middlewares/rateLimiter');
const path = require('path');
const config = require('./config/environment');

const app = express();

// Security Middlewares (Tắt CSP và bật cross-origin để stream media tới Android/Web)
app.use(helmet({ contentSecurityPolicy: false, crossOriginResourcePolicy: { policy: "cross-origin" } }));
app.use(
  cors({
    origin: '*', // Cho phép mọi kết nối từ Android Emulator (10.0.2.2), thiết bị thật và Web
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization']
  })
);

// Serve Static files for Web Admin
app.use(express.static(path.join(__dirname, '../public')));

// Logging Middleware
if (config.nodeEnv !== 'test') {
  app.use(morgan(config.nodeEnv === 'development' ? 'dev' : 'combined'));
}

// Body Parsing Middlewares
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// General Rate Limiter
app.use('/api', apiLimiter);

// Mount API Endpoints (v1)
app.use('/api/v1', routes);
app.use('/api', routes); // Alias hỗ trợ cả /api và /api/v1

// Web Admin Database Visualizer Route
app.get(['/admin', '/db', '/dashboard'], (req, res) => {
  res.sendFile(path.join(__dirname, '../public/admin.html'));
});

// Root route
app.get('/', (req, res) => {
  res.status(200).json({
    name: 'Spotify Clone Music Streaming API',
    version: '1.0.0',
    documentation: '/api/health',
    status: 'Running'
  });
});

// 404 Route Not Found Handler
app.use('*', (req, res) => {
  res.status(404).json({
    success: false,
    statusCode: 404,
    message: `Cannot ${req.method} ${req.originalUrl}`
  });
});

// Centralized Error Handler (Phải đặt ở cuối cùng sau các routes)
app.use(errorHandler);

module.exports = app;
