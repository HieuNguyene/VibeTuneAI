const mongoose = require('mongoose');
const config = require('./environment');
const logger = require('../utils/logger');

const connectDB = async () => {
  try {
    const conn = await mongoose.connect(config.mongodbUri, {
      serverSelectionTimeoutMS: 5000,
      autoIndex: config.nodeEnv !== 'production' // Tự động tạo index ở môi trường Dev
    });

    logger.success(`MongoDB Connected: ${conn.connection.host}/${conn.connection.name}`);

    mongoose.connection.on('error', (err) => {
      logger.error('MongoDB connection error:', err);
    });

    mongoose.connection.on('disconnected', () => {
      logger.warn('MongoDB disconnected. Attempting to reconnect...');
    });

    return conn;
  } catch (error) {
    logger.error('Failed to connect to MongoDB:', error.message);
    // Lưu ý: Không ép process.exit ngay trong dev mode để app vẫn hiển thị chi tiết lỗi
    if (config.nodeEnv === 'production') {
      process.exit(1);
    }
  }
};

module.exports = connectDB;
