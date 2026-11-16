const crypto = require('crypto');
const config = require('../config/environment');
const ApiError = require('../utils/ApiError');

const GOOGLE_CERTS_URL = 'https://www.googleapis.com/oauth2/v3/certs';
const VALID_ISSUERS = new Set(['https://accounts.google.com', 'accounts.google.com']);

let jwksCache = {
  keysByKid: new Map(),
  expiresAt: 0
};

function decodeBase64UrlJson(segment) {
  try {
    const raw = Buffer.from(segment, 'base64url').toString('utf8');
    return JSON.parse(raw);
  } catch (_) {
    return null;
  }
}

function parseMaxAgeMs(cacheControlHeader) {
  const match = String(cacheControlHeader || '').match(/max-age=(\d+)/i);
  if (!match) return 5 * 60 * 1000;
  return Math.max(60, parseInt(match[1], 10)) * 1000;
}

async function fetchGoogleJwks(forceRefresh = false) {
  const now = Date.now();
  if (!forceRefresh && jwksCache.keysByKid.size > 0 && now < jwksCache.expiresAt) {
    return jwksCache.keysByKid;
  }

  let res;
  try {
    res = await fetch(GOOGLE_CERTS_URL, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(8000)
    });
  } catch (err) {
    throw ApiError.unauthorized(`Không thể kết nối máy chủ xác thực Google OAuth: ${err.message}`);
  }

  if (!res.ok) {
    throw ApiError.unauthorized(`Không lấy được khóa công khai Google JWKS (HTTP ${res.status})`);
  }

  const data = await res.json();
  if (!data || !Array.isArray(data.keys) || data.keys.length === 0) {
    throw ApiError.unauthorized('Danh sách khóa công khai Google JWKS rỗng');
  }

  const nextMap = new Map();
  for (const jwk of data.keys) {
    if (jwk && jwk.kid && jwk.kty === 'RSA') {
      nextMap.set(jwk.kid, jwk);
    }
  }

  jwksCache = {
    keysByKid: nextMap,
    expiresAt: now + parseMaxAgeMs(res.headers.get('cache-control'))
  };

  return nextMap;
}

function clearJwksCache() {
  jwksCache = { keysByKid: new Map(), expiresAt: 0 };
}

/**
 * Xác thực Google ID Token (JWT RS256) bằng bộ khóa JWKS chính thức từ Google.
 * Hỗ trợ override resolver khi unit test (không bao giờ cho phép bypass ở production runtime).
 */
async function verifyGoogleIdToken(idToken, { keyResolver = null } = {}) {
  if (!idToken || typeof idToken !== 'string') {
    throw ApiError.badRequest('Google ID Token là bắt buộc');
  }

  const parts = idToken.trim().split('.');
  if (parts.length !== 3 || parts.some((p) => !p)) {
    throw ApiError.unauthorized('Google ID Token không đúng định dạng JWT');
  }

  const [headerB64, payloadB64, signatureB64] = parts;
  const header = decodeBase64UrlJson(headerB64);
  const payload = decodeBase64UrlJson(payloadB64);

  if (!header || !payload || typeof header !== 'object' || typeof payload !== 'object') {
    throw ApiError.unauthorized('Không thể giải mã nội dung Google ID Token');
  }

  if (header.alg !== 'RS256' || !header.kid) {
    throw ApiError.unauthorized('Thuật toán hoặc Key ID trong Google ID Token không được hỗ trợ');
  }

  let jwk = null;
  if (typeof keyResolver === 'function') {
    jwk = await keyResolver(header.kid);
  } else {
    let keysMap = await fetchGoogleJwks(false);
    jwk = keysMap.get(header.kid);
    if (!jwk) {
      keysMap = await fetchGoogleJwks(true);
      jwk = keysMap.get(header.kid);
    }
  }

  if (!jwk) {
    throw ApiError.unauthorized('Không tìm thấy khóa công khai Google tương ứng với Token');
  }

  let publicKey;
  let signatureValid = false;
  try {
    publicKey = crypto.createPublicKey({ key: jwk, format: 'jwk' });
    const signingInput = Buffer.from(`${headerB64}.${payloadB64}`, 'utf8');
    const signatureBuf = Buffer.from(signatureB64, 'base64url');
    signatureValid = crypto.verify('RSA-SHA256', signingInput, publicKey, signatureBuf);
  } catch (_) {
    signatureValid = false;
  }

  if (!signatureValid) {
    throw ApiError.unauthorized('Chữ ký Google ID Token không hợp lệ');
  }

  if (!VALID_ISSUERS.has(payload.iss)) {
    throw ApiError.unauthorized('Issuer của Google ID Token không hợp lệ');
  }

  const nowSec = Math.floor(Date.now() / 1000);
  if (typeof payload.exp !== 'number' || payload.exp <= nowSec - 30) {
    throw ApiError.unauthorized('Google ID Token đã hết hạn');
  }
  if (typeof payload.nbf === 'number' && payload.nbf > nowSec + 30) {
    throw ApiError.unauthorized('Google ID Token chưa có hiệu lực');
  }

  if (Array.isArray(config.googleClientIds) && config.googleClientIds.length > 0) {
    if (!payload.aud || !config.googleClientIds.includes(payload.aud)) {
      throw ApiError.unauthorized('Audience (Client ID) của Google ID Token không khớp ứng dụng');
    }
  } else if (config.nodeEnv === 'production') {
    throw ApiError.internal('GOOGLE_CLIENT_ID chưa được cấu hình trên môi trường production');
  }

  if (!payload.sub || typeof payload.sub !== 'string') {
    throw ApiError.unauthorized('Google ID Token thiếu mã định danh người dùng (sub)');
  }
  if (!payload.email || typeof payload.email !== 'string') {
    throw ApiError.unauthorized('Google ID Token thiếu địa chỉ email');
  }
  if (payload.email_verified !== true) {
    throw ApiError.unauthorized('Email Google chưa được xác minh (email_verified)');
  }

  return {
    googleId: payload.sub.trim(),
    email: payload.email.toLowerCase().trim(),
    displayName: typeof payload.name === 'string' ? payload.name.trim() : '',
    avatarUrl: typeof payload.picture === 'string' ? payload.picture.trim() : ''
  };
}

module.exports = {
  verifyGoogleIdToken,
  clearJwksCache
};
