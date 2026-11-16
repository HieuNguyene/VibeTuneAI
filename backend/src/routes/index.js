const express = require('express');
const router = express.Router();

const authRoutes = require('./authRoutes');
const songRoutes = require('./songRoutes');
const playlistRoutes = require('./playlistRoutes');
const albumRoutes = require('./albumRoutes');
const aiRoutes = require('./aiRoutes');
const adminRoutes = require('./adminRoutes');
const artistRoutes = require('./artistRoutes');
const eventRoutes = require('./eventRoutes');
const playerRoutes = require('./playerRoutes');
const historyRoutes = require('./historyRoutes');
const genreRoutes = require('./genreRoutes');
const notificationRoutes = require('./notificationRoutes');

// API Health Check
router.get('/health', (req, res) => {
  res.status(200).json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  });
});

// Main Route Modules
router.use('/auth', authRoutes);
router.use('/artists', artistRoutes);
router.use('/songs', songRoutes);
router.use('/playlists', playlistRoutes);
router.use('/albums', albumRoutes);
router.use('/genres', genreRoutes);
router.use('/ai', aiRoutes);
router.use('/admin', adminRoutes);
router.use('/events', eventRoutes);
router.use('/interactions', eventRoutes);
router.use('/player', playerRoutes);
router.use('/history', historyRoutes);
router.use('/notifications', notificationRoutes);

module.exports = router;
