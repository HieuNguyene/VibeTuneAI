const express = require('express');
const router = express.Router();
const AdminController = require('../controllers/AdminController');
const { authenticate, authorize } = require('../middlewares/authMiddleware');

// Bắt buộc xác thực JWT và kiểm tra quyền Quản trị viên (role: 'admin') cho toàn bộ /admin/*
router.use(authenticate, authorize('admin'));

// Summary & Data Lookups
router.get('/summary', AdminController.getSummary);
router.get('/stats', AdminController.getSummary);
router.get('/data/:collection', AdminController.getCollectionData);
router.get('/uploads', AdminController.getUploadFiles);
router.get('/options', AdminController.getFormOptions);
router.post('/upload-file', AdminController.uploadFile);
router.post('/sync-system', AdminController.syncSystem);

// Songs CRUD
router.post('/songs', AdminController.addSong);
router.put('/songs/:id', AdminController.updateSong);
router.put('/songs/:id/lyrics', AdminController.updateSongLyrics);
router.delete('/songs/:id', AdminController.deleteSong);

// Artists CRUD
router.post('/artists', AdminController.createArtist);
router.put('/artists/:id', AdminController.updateArtist);
router.delete('/artists/:id', AdminController.deleteArtist);

// Albums CRUD
router.post('/albums', AdminController.createAlbum);
router.put('/albums/:id', AdminController.updateAlbum);
router.delete('/albums/:id', AdminController.deleteAlbum);
router.get('/albums/:id/tracks', AdminController.getAlbumTracks);
router.post('/albums/:id/tracks', AdminController.addSongToAlbum);
router.delete('/albums/:id/tracks/:songId', AdminController.removeSongFromAlbum);

// Genres CRUD
router.post('/genres', AdminController.createGenre);
router.put('/genres/:id', AdminController.updateGenre);
router.delete('/genres/:id', AdminController.deleteGenre);

// Playlists CRUD
router.post('/playlists', AdminController.createPlaylist);
router.put('/playlists/:id', AdminController.updatePlaylist);
router.delete('/playlists/:id', AdminController.deletePlaylist);
router.get('/playlists/:id/tracks', AdminController.getPlaylistTracks);
router.post('/playlists/:id/tracks', AdminController.addSongToPlaylist);
router.delete('/playlists/:id/tracks/:songId', AdminController.removeSongFromPlaylist);

// Users CRUD & Details
router.get('/users/:id/detail', AdminController.getUserDetail);
router.post('/users', AdminController.createUser);
router.put('/users/:id', AdminController.updateUser);
router.delete('/users/:id', AdminController.deleteUser);
router.delete('/users/:id/histories', AdminController.clearUserHistories);

// AI Logs
router.delete('/ailogs/:id', AdminController.deleteAiLog);
router.delete('/ailogs', AdminController.clearAllAiLogs);

// Histories & Favorites moderation
router.delete('/histories/:id', AdminController.deleteHistoryRecord);
router.delete('/favorites/:id', AdminController.deleteFavoriteRecord);

// Notifications Admin Management
router.post('/notifications', AdminController.createNotification);
router.delete('/notifications/:id', AdminController.deleteNotification);
router.delete('/notifications', AdminController.clearAllNotifications);

module.exports = router;
