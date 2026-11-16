const express = require('express');
const router = express.Router();
const EventController = require('../controllers/eventController');
const { optionalAuth, authenticate } = require('../middlewares/authMiddleware');

router.post('/', optionalAuth, (req, res, next) => {
  if ((req.baseUrl && req.baseUrl.endsWith('/interactions')) || (!req.body?.event && req.body?.interactionType)) {
    return EventController.logInteraction(req, res, next);
  }
  return EventController.logEvent(req, res, next);
});
router.post('/interactions', optionalAuth, EventController.logInteraction);
router.get('/my-summary', authenticate, EventController.getUserSummary);

module.exports = router;
