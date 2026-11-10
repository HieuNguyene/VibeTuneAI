const app = require('./src/app');
const connectDB = require('./src/config/db');
const config = require('./src/config/environment');
const logger = require('./src/utils/logger');

const startServer = async () => {
  try {
    // 1. Kết nối cơ sở dữ liệu MongoDB
    await connectDB();

    // 2. Lắng nghe trên 0.0.0.0 để Android Emulator (10.0.2.2) và thiết bị thật kết nối được
    const server = app.listen(config.port, '0.0.0.0', () => {
      logger.success(`🚀 Server running in [${config.nodeEnv}] mode on http://localhost:${config.port}`);
      logger.info(`📱 Android Emulator URL: http://10.0.2.2:${config.port}/api/v1`);
      logger.info(`🔍 Health check: http://localhost:${config.port}/api/v1/health`);
    });

    // Graceful Shutdown
    const shutdown = () => {
      logger.info('Shutting down server gracefully...');
      server.close(() => {
        logger.info('HTTP server closed.');
        process.exit(0);
      });
    };

    process.on('SIGTERM', shutdown);
    process.on('SIGINT', shutdown);
  } catch (error) {
    logger.error('Failed to start server:', error);
    process.exit(1);
  }
};

startServer();
