const express = require('express');
const router = express.Router();
const AiController = require('../controllers/AiController');
const { authenticate, optionalAuth } = require('../middlewares/authMiddleware');
const { aiLimiter } = require('../middlewares/rateLimiter');

router.post('/chat', optionalAuth, aiLimiter, AiController.chat);
router.post('/generate-playlist', authenticate, aiLimiter, AiController.generatePlaylist);
router.get('/recommendations', optionalAuth, AiController.getRecommendations);
router.get('/history', authenticate, AiController.getPromptHistory);

module.exports = router;
