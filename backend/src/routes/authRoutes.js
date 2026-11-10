const express = require('express');
const router = express.Router();
const AuthController = require('../controllers/AuthController');
const { authenticate } = require('../middlewares/authMiddleware');
const { authLimiter } = require('../middlewares/rateLimiter');

router.post('/register', authLimiter, AuthController.register);
router.post('/login', authLimiter, AuthController.login);
router.post('/send-otp', authLimiter, AuthController.sendPhoneOtp);
router.post('/verify-otp', authLimiter, AuthController.verifyPhoneOtp);
router.post('/google', authLimiter, AuthController.loginWithGoogle);
router.get('/me', authenticate, AuthController.getMe);
router.put('/profile', authenticate, AuthController.updateProfile);
router.put('/password', authenticate, AuthController.changePassword);
router.put('/preferences', authenticate, AuthController.updatePreferences);

module.exports = router;
