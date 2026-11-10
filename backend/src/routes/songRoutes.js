const express = require('express');
const router = express.Router();
const SongController = require('../controllers/SongController');
const { authenticate, optionalAuth } = require('../middlewares/authMiddleware');

router.get('/home', optionalAuth, SongController.getHomeFeed);
router.get('/feed', optionalAuth, SongController.getHomeFeed);
router.get('/search', optionalAuth, SongController.search);
router.get('/genre/:genreId', SongController.getSongsByGenre);
router.get('/artist/:artistId', optionalAuth, SongController.getSongsByArtist);
router.get('/favorites', authenticate, SongController.getUserFavorites);
router.get('/:id', optionalAuth, SongController.getSongById);
router.post('/:id/favorite', authenticate, SongController.toggleFavorite);
router.post('/log-playback', optionalAuth, SongController.logPlayback);

module.exports = router;
