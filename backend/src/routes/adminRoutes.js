const express = require('express');
const router = express.Router();
const AdminController = require('../controllers/AdminController');

router.get('/summary', AdminController.getSummary);
router.get('/data/:collection', AdminController.getCollectionData);
router.get('/uploads', AdminController.getUploadFiles);
router.post('/songs', AdminController.addSong);
router.put('/songs/:id/lyrics', AdminController.updateSongLyrics);
router.delete('/songs/:id', AdminController.deleteSong);

module.exports = router;
