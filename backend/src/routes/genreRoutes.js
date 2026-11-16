const express = require('express');
const router = express.Router();
const Genre = require('../models/Genre');
const ApiResponse = require('../utils/ApiResponse');

// GET /api/v1/genres - Lấy toàn bộ thể loại (đồng bộ trực tiếp từ Database/Admin)
router.get('/', async (req, res, next) => {
  try {
    const genres = await Genre.find().sort({ createdAt: 1 }).lean();
    return ApiResponse.success(res, genres, 'Lấy danh sách thể loại thành công');
  } catch (error) {
    next(error);
  }
});

// GET /api/v1/genres/:id - Lấy chi tiết thể loại
router.get('/:id', async (req, res, next) => {
  try {
    const genre = await Genre.findById(req.params.id).lean();
    if (!genre) {
      return ApiResponse.notFound(res, 'Không tìm thấy thể loại');
    }
    return ApiResponse.success(res, genre, 'Lấy chi tiết thể loại thành công');
  } catch (error) {
    next(error);
  }
});

module.exports = router;
