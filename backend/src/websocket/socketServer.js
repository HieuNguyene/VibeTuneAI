const { WebSocketServer, WebSocket } = require('ws');
const jwt = require('jsonwebtoken');
const config = require('../config/environment');
const logger = require('../utils/logger');
const User = require('../models/User');

class SocketManager {
  constructor() {
    this.wss = null;
    this.userSockets = new Map(); // userId -> Set<WebSocket>
    this.allSockets = new Set();  // Set<WebSocket> (chỉ chứa socket đã xác thực)
  }

  init(server) {
    this.wss = new WebSocketServer({ server, path: '/ws' });
    logger.info('📡 WebSocket Server initialized on path /ws (JWT Auth Required)');

    this.wss.on('connection', async (ws, req) => {
      ws.isAlive = true;

      let closedEarly = false;
      ws.on('close', () => {
        closedEarly = true;
        this.unregisterSocket(ws);
      });
      ws.on('error', () => {
        closedEarly = true;
        this.unregisterSocket(ws);
      });

      let token = null;
      try {
        const fullUrl = `http://localhost${req.url || ''}`;
        const parsedUrl = new URL(fullUrl);
        token = parsedUrl.searchParams.get('token');
      } catch (_) {
        token = null;
      }

      if (!token && typeof req.headers?.authorization === 'string' && req.headers.authorization.startsWith('Bearer ')) {
        token = req.headers.authorization.slice(7).trim();
      }

      let decodedId = null;
      if (token) {
        try {
          const decoded = jwt.verify(token, config.jwtSecret);
          decodedId = decoded.userId || decoded.id || decoded._id || null;
        } catch (_) {
          decodedId = null;
        }
      }

      if (!decodedId) {
        try {
          ws.close(1008, 'Authentication required');
        } catch (_) {}
        return;
      }

      let user = null;
      try {
        user = await User.findById(decodedId).select('role isActive').lean();
      } catch (_) {
        user = null;
      }

      if (closedEarly || ws.readyState !== WebSocket.OPEN) {
        return;
      }

      if (!user || !user.isActive) {
        try {
          ws.close(1008, 'Unauthorized or deactivated account');
        } catch (_) {}
        return;
      }

      this.allSockets.add(ws);
      this.registerUserSocket(String(user._id), ws, user.role || 'user');

      ws.on('pong', () => {
        ws.isAlive = true;
      });

      ws.on('message', (message) => {
        try {
          const parsed = JSON.parse(message.toString());
          if (parsed.type === 'PING') {
            ws.send(JSON.stringify({ event: 'PONG', timestamp: Date.now() }));
          }
        } catch (_) {
          // Bỏ qua frame không phải JSON
        }
      });

      try {
        ws.send(JSON.stringify({
          event: 'CONNECTED',
          message: 'Connected to VibeTune Realtime Stream',
          role: ws.userRole,
          serverTime: new Date().toISOString()
        }));
      } catch (_) {}
    });

    this.heartbeatInterval = setInterval(() => {
      if (!this.wss) return;
      this.wss.clients.forEach((ws) => {
        if (!ws.isAlive) {
          this.unregisterSocket(ws);
          return ws.terminate();
        }
        ws.isAlive = false;
        ws.ping();
      });
    }, 30000);
  }

  registerUserSocket(userId, ws, role = 'user') {
    ws.userId = userId;
    ws.userRole = role;
    if (!this.userSockets.has(userId)) {
      this.userSockets.set(userId, new Set());
    }
    this.userSockets.get(userId).add(ws);
  }

  unregisterSocket(ws) {
    this.allSockets.delete(ws);
    if (ws.userId && this.userSockets.has(ws.userId)) {
      const set = this.userSockets.get(ws.userId);
      set.delete(ws);
      if (set.size === 0) {
        this.userSockets.delete(ws.userId);
      }
    }
  }

  safeSend(ws, payload) {
    try {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(payload);
        return true;
      }
    } catch (_) {}
    return false;
  }

  broadcastToUser(userId, event, data) {
    const strUserId = String(userId);
    const payload = JSON.stringify({ event, data, timestamp: new Date().toISOString() });
    let count = 0;
    if (this.userSockets.has(strUserId)) {
      this.userSockets.get(strUserId).forEach((ws) => {
        if (this.safeSend(ws, payload)) {
          count++;
        }
      });
    }
    return count;
  }

  broadcastToUsers(userIds, event, data) {
    if (!Array.isArray(userIds) || userIds.length === 0) return 0;
    let count = 0;
    for (const uid of userIds) {
      count += this.broadcastToUser(uid, event, data);
    }
    return count;
  }

  broadcastAll(event, data, { roles = null } = {}) {
    const allowedRoles = Array.isArray(roles) && roles.length > 0
      ? new Set(roles)
      : (event === 'ADMIN_SUMMARY_UPDATE' ? new Set(['admin']) : null);

    const payload = JSON.stringify({ event, data, timestamp: new Date().toISOString() });
    let count = 0;
    this.allSockets.forEach((ws) => {
      if (ws.readyState === WebSocket.OPEN) {
        if (allowedRoles && !allowedRoles.has(ws.userRole)) {
          return;
        }
        if (this.safeSend(ws, payload)) {
          count++;
        }
      }
    });
    return count;
  }

  getOnlineUserCount() {
    return this.userSockets.size;
  }

  getTotalConnections() {
    return this.allSockets.size;
  }

  close() {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
    if (this.wss) {
      this.allSockets.forEach((ws) => {
        try { ws.close(); } catch (_) {}
      });
      this.allSockets.clear();
      this.userSockets.clear();
      this.wss.close();
      this.wss = null;
    }
  }
}

const socketManager = new SocketManager();
module.exports = socketManager;
