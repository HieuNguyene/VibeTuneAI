const express = require('express');
const router = express.Router();
const PlaylistController = require('../controllers/PlaylistController');
const { authenticate, optionalAuth } = require('../middlewares/authMiddleware');

router.get('/', optionalAuth, PlaylistController.getUserPlaylists);
router.get('/my-playlists', optionalAuth, PlaylistController.getUserPlaylists);
router.get('/public', PlaylistController.getPublicPlaylists);
router.get('/favorites', authenticate, PlaylistController.getFavoritePlaylists);
router.post('/:id/favorite', authenticate, PlaylistController.toggleFavoritePlaylist);
router.get('/:id', optionalAuth, PlaylistController.getPlaylistById);
router.post('/', authenticate, PlaylistController.createPlaylist);
router.put('/:id', authenticate, PlaylistController.updatePlaylist);
router.delete('/:id', authenticate, PlaylistController.deletePlaylist);
router.post('/:id/tracks', authenticate, PlaylistController.addTrack);
router.delete('/:id/tracks/:songId', authenticate, PlaylistController.removeTrack);
router.put('/:id/reorder', authenticate, PlaylistController.reorderTracks);

module.exports = router;
