const express = require('express');
const router = express.Router();
const HistoryController = require('../controllers/historyController');
const { authenticate } = require('../middlewares/authMiddleware');

// Bắt buộc xác thực JWT cho toàn bộ lịch sử nghe cá nhân (chống IDOR)
router.use(authenticate);

// Lấy danh sách lịch sử
router.get('/', HistoryController.getHistory);

// Xóa 1 bản ghi
router.delete('/:id', HistoryController.deleteHistoryItem);

// Xóa toàn bộ hoặc xóa theo ngày (?all=true hoặc ?date=YYYY-MM-DD)
router.delete('/', HistoryController.deleteHistory);

// Xóa theo lô (nhiều ID hoặc nhiều ngày)
router.post('/delete-batch', HistoryController.deleteBatch);

module.exports = router;
