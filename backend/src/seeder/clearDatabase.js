const mongoose = require('mongoose');
const config = require('../config/environment');
const logger = require('../utils/logger');
const models = require('../models');

const clearDatabase = async () => {
  try {
    logger.info('Dang ket noi toi MongoDB de xoa sach du lieu...');
    await mongoose.connect(config.mongodbUri);
    logger.success('Ket noi thanh cong.');

    logger.info('Dang xoa toan bo bai hat, album, ca si va du lieu ao...');
    await Promise.all([
      models.Song.deleteMany({}),
      models.Album.deleteMany({}),
      models.Artist.deleteMany({}),
      models.Playlist.deleteMany({}),
      models.PlaylistTrack.deleteMany({}),
      models.ListeningHistory.deleteMany({}),
      models.UserFavorite.deleteMany({}),
      models.AiPromptLog.deleteMany({})
    ]);

    logger.success('====================================================');
    logger.success(' DA XOA SACH TOAN BO DU LIEU AO TRONG CO SO DU LIEU!');
    logger.success(' Co so du lieu hien tai hoan toan trong sach.');
    logger.success(' Ban co the bat dau tu them bai hat that cua minh.');
    logger.success('====================================================');

    process.exit(0);
  } catch (error) {
    logger.error('Loi khi xoa du lieu:', error.message);
    process.exit(1);
  }
};

clearDatabase();
