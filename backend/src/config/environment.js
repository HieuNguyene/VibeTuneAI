const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../../.env') });

const parseCsvList = (raw) =>
  String(raw || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);

const config = {
  port: parseInt(process.env.PORT, 10) || 5000,
  nodeEnv: process.env.NODE_ENV || 'development',
  mongodbUri: process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/music_app',
  jwtSecret: process.env.JWT_SECRET || 'dev_secret_key_spotify_clone_auth_token_2026',
  jwtExpiresIn: process.env.JWT_EXPIRES_IN || '7d',
  geminiApiKey: process.env.GEMINI_API_KEY || '',
  googleClientIds: parseCsvList(process.env.GOOGLE_CLIENT_ID),
  adminEmail: (process.env.ADMIN_EMAIL || 'admin@vibetune.local').trim().toLowerCase(),
  adminPassword: process.env.ADMIN_PASSWORD || '',
  adminSyncPassword: String(process.env.ADMIN_SYNC_PASSWORD || '').toLowerCase() === 'true'
};

module.exports = config;
