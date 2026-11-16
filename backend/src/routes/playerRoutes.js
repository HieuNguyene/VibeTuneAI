const express = require('express');
const router = express.Router();
const PlayerController = require('../controllers/playerController');
const { optionalAuth } = require('../middlewares/authMiddleware');

// POST /player/session
router.post('/session', optionalAuth, PlayerController.createSession);

// GET /player/next
router.get('/next', optionalAuth, PlayerController.getNext);

// POST /player/session/:id/arc & POST /player/:id/arc
router.post('/session/:id/arc', optionalAuth, PlayerController.updateArc);
router.post('/:id/arc', optionalAuth, PlayerController.updateArc);

module.exports = router;
