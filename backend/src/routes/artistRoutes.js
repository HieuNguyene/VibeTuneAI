const express = require('express');
const router = express.Router();
const ArtistController = require('../controllers/ArtistController');
const { authenticate, optionalAuth } = require('../middlewares/authMiddleware');

router.get('/', optionalAuth, ArtistController.getAllArtists);
router.get('/library', optionalAuth, ArtistController.getLibraryArtists);
router.get('/:id', optionalAuth, ArtistController.getArtistById);
router.get('/:id/songs', optionalAuth, ArtistController.getArtistSongs);
router.post('/:id/follow', authenticate, ArtistController.toggleFollowArtist);

module.exports = router;

