const NotificationService = require('../services/notificationService');
const ApiResponse = require('../utils/ApiResponse');

class NotificationController {
  static async getMyNotifications(req, res, next) {
    try {
      const data = await NotificationService.getUserNotifications(req.user._id, req.query);
      return ApiResponse.success(res, data, 'Lấy danh sách thông báo thành công');
    } catch (error) {
      next(error);
    }
  }

  static async getUnreadCount(req, res, next) {
    try {
      const data = await NotificationService.getUnreadCount(req.user._id);
      return ApiResponse.success(res, data, 'Lấy số lượng chưa đọc thành công');
    } catch (error) {
      next(error);
    }
  }

  static async markAsRead(req, res, next) {
    try {
      const { id } = req.params;
      const data = await NotificationService.markAsRead(req.user._id, id);
      return ApiResponse.success(res, data, 'Đánh dấu đã đọc thành công');
    } catch (error) {
      next(error);
    }
  }

  static async markAllAsRead(req, res, next) {
    try {
      const data = await NotificationService.markAllAsRead(req.user._id);
      return ApiResponse.success(res, data, 'Đã đánh dấu tất cả là đã đọc');
    } catch (error) {
      next(error);
    }
  }

  static async deleteNotification(req, res, next) {
    try {
      const { id } = req.params;
      const data = await NotificationService.deleteNotification(req.user._id, id);
      return ApiResponse.success(res, data, 'Xóa thông báo thành công');
    } catch (error) {
      next(error);
    }
  }
}

module.exports = NotificationController;
