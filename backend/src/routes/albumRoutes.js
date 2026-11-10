const express = require('express');
const router = express.Router();
const AlbumController = require('../controllers/AlbumController');
const { optionalAuth } = require('../middlewares/authMiddleware');

router.get('/popular', optionalAuth, AlbumController.getPopularAlbums);
router.get('/', optionalAuth, AlbumController.getPopularAlbums);
router.get('/:id', optionalAuth, AlbumController.getAlbumById);

module.exports = router;
