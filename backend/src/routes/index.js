const express = require('express');
const router = express.Router();

const authRoutes = require('./authRoutes');
const songRoutes = require('./songRoutes');
const playlistRoutes = require('./playlistRoutes');
const albumRoutes = require('./albumRoutes');
const aiRoutes = require('./aiRoutes');
const adminRoutes = require('./adminRoutes');

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
router.use('/songs', songRoutes);
router.use('/playlists', playlistRoutes);
router.use('/albums', albumRoutes);
router.use('/ai', aiRoutes);
router.use('/admin', adminRoutes);

module.exports = router;
