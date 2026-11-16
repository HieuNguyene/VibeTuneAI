const mongoose = require('mongoose');
const { Notification, User } = require('../models');
const socketManager = require('../websocket/socketServer');
const ApiError = require('../utils/ApiError');
const { sanitizeMediaUrl } = require('../utils/fileValidator');

class NotificationService {
  /**
   * Lay danh sach thong bao cua nguoi dung (kem thong tin phan trang)
   */
  static async getUserNotifications(userId, { page = 1, limit = 50 } = {}) {
    if (!userId || !mongoose.Types.ObjectId.isValid(userId)) {
      throw ApiError.badRequest('Invalid user ID');
    }

    const pageNum = Math.max(1, parseInt(page, 10) || 1);
    const limitNum = Math.min(100, Math.max(1, parseInt(limit, 10) || 50));
    const skip = (pageNum - 1) * limitNum;

    const [items, totalCount, unreadCount] = await Promise.all([
      Notification.find({ recipientId: userId })
        .populate('senderId', 'displayName username avatarUrl role')
        .sort({ createdAt: -1 })
        .skip(skip)
        .limit(limitNum)
        .lean(),
      Notification.countDocuments({ recipientId: userId }),
      Notification.countDocuments({ recipientId: userId, isRead: false })
    ]);

    const formatted = items.map((item) => {
      const d = item.createdAt ? new Date(item.createdAt) : new Date();
      const yyyy = d.getFullYear();
      const mm = String(d.getMonth() + 1).padStart(2, '0');
      const dd = String(d.getDate()).padStart(2, '0');
      const localDateKey = `${yyyy}-${mm}-${dd}`;

      return {
        ...item,
        id: item._id.toString(),
        localDateKey,
        senderName: item.senderName || item.senderId?.displayName || 'Ban Quản trị VibeTune',
        senderAvatar: item.senderAvatar || item.senderId?.avatarUrl || ''
      };
    });

    return {
      items: formatted,
      totalCount,
      unreadCount,
      page: pageNum,
      totalPages: Math.ceil(totalCount / limitNum)
    };
  }

  /**
   * Lay so luong thong bao chua doc
   */
  static async getUnreadCount(userId) {
    if (!userId || !mongoose.Types.ObjectId.isValid(userId)) return { unreadCount: 0 };
    const count = await Notification.countDocuments({ recipientId: userId, isRead: false });
    return { unreadCount: count };
  }

  /**
   * Danh dau 1 thong bao da doc
   */
  static async markAsRead(userId, notificationId) {
    if (!mongoose.Types.ObjectId.isValid(notificationId)) {
      throw ApiError.badRequest('Invalid notification ID');
    }

    const notif = await Notification.findOneAndUpdate(
      { _id: notificationId, recipientId: userId },
      { $set: { isRead: true, readAt: new Date() } },
      { new: true }
    ).lean();

    if (!notif) {
      throw ApiError.notFound('Notification not found or access denied');
    }

    const { unreadCount } = await this.getUnreadCount(userId);

    socketManager.broadcastToUser(userId, 'NOTIFICATION_READ', {
      notificationId: notif._id.toString(),
      unreadCount
    });

    return {
      ...notif,
      id: notif._id.toString(),
      unreadCount
    };
  }

  /**
   * Danh dau tat ca thong bao da doc
   */
  static async markAllAsRead(userId) {
    if (!userId || !mongoose.Types.ObjectId.isValid(userId)) {
      throw ApiError.badRequest('Invalid user ID');
    }

    const result = await Notification.updateMany(
      { recipientId: userId, isRead: false },
      { $set: { isRead: true, readAt: new Date() } }
    );

    socketManager.broadcastToUser(userId, 'NOTIFICATION_READ_ALL', {
      modifiedCount: result.modifiedCount,
      unreadCount: 0
    });

    return { success: true, modifiedCount: result.modifiedCount, unreadCount: 0 };
  }

  /**
   * Xoa 1 thong bao
   */
  static async deleteNotification(userId, notificationId) {
    if (!mongoose.Types.ObjectId.isValid(notificationId)) {
      throw ApiError.badRequest('Invalid notification ID');
    }

    const result = await Notification.findOneAndDelete({ _id: notificationId, recipientId: userId });
    if (!result) {
      throw ApiError.notFound('Notification not found');
    }

    const { unreadCount } = await this.getUnreadCount(userId);
    socketManager.broadcastToUser(userId, 'NOTIFICATION_DELETED', {
      notificationId,
      unreadCount
    });

    return { success: true, message: 'Notification deleted successfully', unreadCount };
  }

  /**
   * Tao va gui thong bao he thong / su kien den 1 nguoi dung
   */
  static async createNotification({
    recipientId,
    senderId = null,
    senderName = 'Hệ thống VibeTune',
    senderAvatar = '',
    title,
    content,
    imageUrl = '',
    type = 'GENERAL',
    metadata = {}
  }) {
    if (!recipientId || !title || !content) {
      throw ApiError.badRequest('Recipient, title, and content are required');
    }

    const doc = await Notification.create({
      recipientId,
      senderId,
      senderName,
      senderAvatar,
      title,
      content,
      imageUrl,
      type,
      metadata,
      isRead: false
    });

    const populated = await Notification.findById(doc._id)
      .populate('senderId', 'displayName username avatarUrl')
      .lean();

    const { unreadCount } = await this.getUnreadCount(recipientId);

    const payload = {
      ...populated,
      id: populated._id.toString(),
      unreadCount
    };

    socketManager.broadcastToUser(recipientId, 'NEW_NOTIFICATION', payload);

    return payload;
  }

  /**
   * Admin phat thong bao den 1 nguoi, nhieu nguoi hoac toan bo nguoi dung
   */
  static async sendAdminNotification({
    targetType = 'all',
    targetUserIds = [],
    title,
    content,
    imageUrl = '',
    type = 'ADMIN_ANNOUNCEMENT',
    metadata = {},
    adminUser = null
  }) {
    if (!title || !title.trim() || !content || !content.trim()) {
      throw ApiError.badRequest('Tiêu đề và nội dung thông báo là bắt buộc');
    }

    const senderName = adminUser?.displayName || 'Ban Quản trị VibeTune';
    const senderAvatar = adminUser?.avatarUrl || '';
    const senderId = adminUser?._id || null;
    const safeImageUrl = sanitizeMediaUrl(imageUrl || '');

    let recipients = [];

    if (targetType === 'all') {
      const allUsers = await User.find({ isActive: true }).select('_id').lean();
      recipients = allUsers.map((u) => u._id);
    } else if (targetType === 'single') {
      if (!targetUserIds || (Array.isArray(targetUserIds) && targetUserIds.length === 0)) {
        throw ApiError.badRequest('Vui lòng chọn 1 người dùng nhận thông báo');
      }
      const singleId = Array.isArray(targetUserIds) ? targetUserIds[0] : targetUserIds;
      if (!mongoose.Types.ObjectId.isValid(singleId)) {
        throw ApiError.badRequest('User ID không hợp lệ');
      }
      recipients = [singleId];
    } else if (targetType === 'multiple') {
      if (!Array.isArray(targetUserIds) || targetUserIds.length === 0) {
        throw ApiError.badRequest('Vui lòng chọn ít nhất 1 người dùng');
      }
      recipients = targetUserIds.filter((id) => mongoose.Types.ObjectId.isValid(id));
    }

    if (recipients.length === 0) {
      throw ApiError.badRequest('Không tìm thấy người dùng hợp lệ để gửi thông báo');
    }

    const docs = recipients.map((rId) => ({
      recipientId: rId,
      senderId,
      senderName,
      senderAvatar,
      title: title.trim(),
      content: content.trim(),
      imageUrl: safeImageUrl,
      type: type || 'ADMIN_ANNOUNCEMENT',
      metadata: {
        ...metadata,
        targetScope: targetType,
        recipientCount: recipients.length
      },
      isRead: false
    }));

    const created = await Notification.insertMany(docs);

    if (targetType === 'all') {
      socketManager.broadcastAll('NEW_NOTIFICATION', {
        title: title.trim(),
        content: content.trim(),
        imageUrl: safeImageUrl,
        senderName,
        senderAvatar,
        type: type || 'ADMIN_ANNOUNCEMENT',
        createdAt: new Date().toISOString()
      });
    } else {
      for (const notif of created) {
        socketManager.broadcastToUser(notif.recipientId, 'NEW_NOTIFICATION', {
          id: notif._id.toString(),
          title: notif.title,
          content: notif.content,
          imageUrl: notif.imageUrl,
          senderName: notif.senderName,
          senderAvatar: notif.senderAvatar,
          type: notif.type,
          createdAt: notif.createdAt
        });
      }
    }

    socketManager.broadcastAll('ADMIN_SUMMARY_UPDATE', {
      type: 'notifications',
      newCount: created.length
    });

    return {
      success: true,
      sentCount: created.length,
      targetType,
      sample: created[0]
    };
  }
}

module.exports = NotificationService;
