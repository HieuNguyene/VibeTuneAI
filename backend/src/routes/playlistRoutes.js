const express = require('express');
const router = express.Router();
const PlaylistController = require('../controllers/PlaylistController');
const { authenticate, optionalAuth } = require('../middlewares/authMiddleware');

router.get('/my-playlists', authenticate, PlaylistController.getUserPlaylists);
router.get('/public', PlaylistController.getPublicPlaylists);
router.get('/:id', optionalAuth, PlaylistController.getPlaylistById);
router.post('/', authenticate, PlaylistController.createPlaylist);
router.post('/:id/tracks', authenticate, PlaylistController.addTrack);
router.delete('/:id/tracks/:songId', authenticate, PlaylistController.removeTrack);
router.put('/:id/reorder', authenticate, PlaylistController.reorderTracks);

module.exports = router;
