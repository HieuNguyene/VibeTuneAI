const fs = require('fs');
const path = require('path');
const ApiError = require('./ApiError');

const AUDIO_EXTS = ['.mp3', '.wav', '.flac', '.m4a', '.aac', '.ogg'];
const IMAGE_EXTS = ['.jpg', '.jpeg', '.png', '.webp', '.gif'];

const SAFE_IMAGE_MIMES = new Set([
  'image/jpeg',
  'image/jpg',
  'image/pjpeg',
  'image/png',
  'image/webp',
  'image/gif'
]);

const SAFE_AUDIO_MIMES = new Set([
  'audio/mpeg',
  'audio/mp3',
  'audio/wav',
  'audio/x-wav',
  'audio/wave',
  'audio/flac',
  'audio/x-flac',
  'audio/mp4',
  'audio/m4a',
  'audio/x-m4a',
  'audio/aac',
  'audio/ogg'
]);

const DEFAULT_MAX_AUDIO_BYTES = 45 * 1024 * 1024; // 45 MB
const DEFAULT_MAX_IMAGE_BYTES = 10 * 1024 * 1024; // 10 MB

function slugifyStem(text) {
  return String(text || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/[^a-z0-9_-]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

/**
 * Nhận diện định dạng ảnh thực tế từ Magic Bytes đầu tệp.
 * Trả về phần mở rộng chuẩn (.jpg, .png, .webp, .gif) hoặc null nếu không hợp lệ.
 */
function detectImageExtensionFromBuffer(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 4) return null;

  // JPEG: FF D8 FF
  if (buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return '.jpg';
  }

  // PNG: 89 50 4E 47 0D 0A 1A 0A
  if (
    buffer.length >= 8 &&
    buffer[0] === 0x89 &&
    buffer[1] === 0x50 &&
    buffer[2] === 0x4e &&
    buffer[3] === 0x47 &&
    buffer[4] === 0x0d &&
    buffer[5] === 0x0a &&
    buffer[6] === 0x1a &&
    buffer[7] === 0x0a
  ) {
    return '.png';
  }

  // GIF87a / GIF89a
  if (buffer.length >= 6) {
    const header6 = buffer.subarray(0, 6).toString('ascii');
    if (header6 === 'GIF87a' || header6 === 'GIF89a') {
      return '.gif';
    }
  }

  // WEBP: RIFF....WEBP
  if (
    buffer.length >= 12 &&
    buffer.subarray(0, 4).toString('ascii') === 'RIFF' &&
    buffer.subarray(8, 12).toString('ascii') === 'WEBP'
  ) {
    return '.webp';
  }

  return null;
}

/**
 * Nhận diện định dạng âm thanh thực tế từ Magic Bytes đầu tệp.
 */
function detectAudioExtensionFromBuffer(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 4) return null;

  // ID3 tag (MP3 phổ biến)
  if (buffer.subarray(0, 3).toString('ascii') === 'ID3') {
    return '.mp3';
  }

  // WAV: RIFF....WAVE
  if (
    buffer.length >= 12 &&
    buffer.subarray(0, 4).toString('ascii') === 'RIFF' &&
    buffer.subarray(8, 12).toString('ascii') === 'WAVE'
  ) {
    return '.wav';
  }

  // FLAC: fLaC
  if (buffer.subarray(0, 4).toString('ascii') === 'fLaC') {
    return '.flac';
  }

  // OGG: OggS
  if (buffer.subarray(0, 4).toString('ascii') === 'OggS') {
    return '.ogg';
  }

  // ISO BMFF (M4A / MP4 audio): ....ftyp
  if (buffer.length >= 8 && buffer.subarray(4, 8).toString('ascii') === 'ftyp') {
    return '.m4a';
  }

  // ADIF AAC
  if (buffer.subarray(0, 4).toString('ascii') === 'ADIF') {
    return '.aac';
  }

  // MPEG / ADTS frame sync (0xFFFx / 0xFFEx)
  if (buffer[0] === 0xff && (buffer[1] & 0xe0) === 0xe0) {
    const layerBits = (buffer[1] >> 1) & 0x03;
    if (layerBits === 0x00 && (buffer[1] & 0xf6) === 0xf0) {
      return '.aac';
    }
    if (layerBits !== 0x00) {
      return '.mp3';
    }
  }

  return null;
}

/**
 * Giải mã base64 an toàn và kiểm tra Data URI MIME (nếu có).
 */
function decodeBase64Payload(base64Input, { expectedCategory, maxBytes }) {
  if (!base64Input || typeof base64Input !== 'string') {
    throw ApiError.badRequest('Dữ liệu tệp base64 không hợp lệ');
  }

  const trimmed = base64Input.trim();
  let declaredMime = null;
  let rawBase64 = trimmed;

  if (trimmed.startsWith('data:')) {
    const commaIdx = trimmed.indexOf(',');
    if (commaIdx === -1) {
      throw ApiError.badRequest('Định dạng Data URI không hợp lệ');
    }
    const meta = trimmed.slice(5, commaIdx).toLowerCase();
    rawBase64 = trimmed.slice(commaIdx + 1);
    const semiIdx = meta.indexOf(';');
    declaredMime = (semiIdx >= 0 ? meta.slice(0, semiIdx) : meta).trim();
    if (!meta.includes(';base64')) {
      throw ApiError.badRequest('Chỉ hỗ trợ dữ liệu mã hóa base64');
    }

    if (expectedCategory === 'image' && !SAFE_IMAGE_MIMES.has(declaredMime)) {
      throw ApiError.badRequest(`MIME ảnh "${declaredMime}" không được phép tải lên`);
    }
    if (expectedCategory === 'audio' && !SAFE_AUDIO_MIMES.has(declaredMime)) {
      throw ApiError.badRequest(`MIME âm thanh "${declaredMime}" không được phép tải lên`);
    }
  }

  const compactBase64 = rawBase64.replace(/\s+/g, '');
  if (!compactBase64 || !/^[A-Za-z0-9+/]+={0,2}$/.test(compactBase64) || compactBase64.length % 4 !== 0) {
    throw ApiError.badRequest('Chuỗi base64 chứa ký tự không hợp lệ hoặc bị hỏng');
  }

  // Ước lượng kích thước trước khi cấp phát buffer để tránh payload quá lớn
  const estimatedBytes = Math.floor((compactBase64.length * 3) / 4);
  if (estimatedBytes > maxBytes) {
    const maxMb = Math.round((maxBytes / (1024 * 1024)) * 10) / 10;
    throw ApiError.badRequest(`Dung lượng tệp vượt quá giới hạn cho phép (${maxMb} MB)`);
  }

  const buffer = Buffer.from(compactBase64, 'base64');
  if (!buffer || buffer.length === 0) {
    throw ApiError.badRequest('Dữ liệu tệp không hợp lệ hoặc rỗng!');
  }
  if (buffer.length > maxBytes) {
    const maxMb = Math.round((maxBytes / (1024 * 1024)) * 10) / 10;
    throw ApiError.badRequest(`Dung lượng tệp vượt quá giới hạn cho phép (${maxMb} MB)`);
  }

  return { buffer, declaredMime };
}

/**
 * Kiểm tra đường dẫn upload không thoát khỏi thư mục đích (Path Traversal protection).
 */
function resolveSafeUploadPath(destDir, fileName) {
  const safeRoot = path.resolve(destDir);
  const resolved = path.resolve(safeRoot, fileName);
  if (!resolved.startsWith(safeRoot + path.sep)) {
    throw ApiError.badRequest('Đường dẫn lưu tệp không hợp lệ');
  }
  return resolved;
}

/**
 * Xác thực và lưu tệp base64 an toàn vào public/uploads/<subDir>.
 */
function saveValidatedBase64Upload({
  fileName,
  base64Data,
  target,
  uploadsBaseDir,
  subDirOverride,
  prefix,
  maxBytes
}) {
  const normalizedTarget = String(target || '').trim().toLowerCase();
  const isImageTarget = ['cover', 'image', 'avatar', 'playlist'].includes(normalizedTarget);
  const isAudioTarget = normalizedTarget === 'audio';

  if (normalizedTarget && !isImageTarget && !isAudioTarget) {
    throw ApiError.badRequest(`Mục tiêu tải tệp "${normalizedTarget}" không hợp lệ`);
  }

  const rawInputName = fileName ? String(fileName).trim() : '';
  if (rawInputName.includes('..') || rawInputName.includes('/') || rawInputName.includes('\\') || rawInputName.includes('\0')) {
    throw ApiError.badRequest('Tên tệp chứa ký tự điều hướng không an toàn');
  }

  const baseInputName = path.basename(rawInputName);
  const rawExt = path.extname(baseInputName).toLowerCase();

  let expectedCategory = isAudioTarget ? 'audio' : (isImageTarget ? 'image' : null);
  if (!expectedCategory) {
    if (AUDIO_EXTS.includes(rawExt)) expectedCategory = 'audio';
    else if (IMAGE_EXTS.includes(rawExt)) expectedCategory = 'image';
  }

  if (expectedCategory === 'audio') {
    if (rawExt && !AUDIO_EXTS.includes(rawExt)) {
      throw ApiError.badRequest(`Định dạng "${rawExt || 'trống'}" không hợp lệ cho tệp âm thanh (chỉ nhận ${AUDIO_EXTS.join(', ')})`);
    }
  } else if (expectedCategory === 'image') {
    if (rawExt && !IMAGE_EXTS.includes(rawExt)) {
      throw ApiError.badRequest(`Định dạng "${rawExt || 'trống'}" không hợp lệ cho tệp ảnh (chỉ nhận ${IMAGE_EXTS.join(', ')})`);
    }
  } else {
    throw ApiError.badRequest(`Định dạng tệp "${rawExt || 'không xác định'}" không được hỗ trợ!`);
  }

  const effectiveMaxBytes = maxBytes || (expectedCategory === 'audio' ? DEFAULT_MAX_AUDIO_BYTES : DEFAULT_MAX_IMAGE_BYTES);
  const { buffer } = decodeBase64Payload(base64Data, {
    expectedCategory,
    maxBytes: effectiveMaxBytes
  });

  const detectedExt = expectedCategory === 'audio'
    ? detectAudioExtensionFromBuffer(buffer)
    : detectImageExtensionFromBuffer(buffer);

  if (!detectedExt) {
    throw ApiError.badRequest(
      expectedCategory === 'audio'
        ? 'Nội dung tệp không khớp chữ ký âm thanh hợp lệ (Magic Bytes MP3/WAV/FLAC/M4A/AAC/OGG)'
        : 'Nội dung tệp không khớp chữ ký ảnh hợp lệ (Magic Bytes JPG/PNG/WEBP/GIF)'
    );
  }

  // Luôn dùng phần mở rộng khớp chữ ký nhị phân thật để tránh giả mạo Content-Type
  const finalExt = (expectedCategory === 'audio' && rawExt && AUDIO_EXTS.includes(rawExt) && rawExt === detectedExt)
    ? rawExt
    : detectedExt;

  const rawStem = baseInputName ? path.basename(baseInputName, rawExt || undefined) : '';
  const safeStem = slugifyStem(rawStem) || (prefix || 'upload');
  const uniqueSuffix = `${Date.now().toString().slice(-6)}_${Math.random().toString(36).slice(2, 6)}`;
  const finalFileName = `${safeStem}_${uniqueSuffix}${finalExt}`;

  const subDir = subDirOverride || (expectedCategory === 'audio' ? 'audios' : 'covers');
  const targetDir = path.join(uploadsBaseDir, subDir);
  if (!fs.existsSync(targetDir)) {
    fs.mkdirSync(targetDir, { recursive: true });
  }

  const filePath = resolveSafeUploadPath(targetDir, finalFileName);
  fs.writeFileSync(filePath, buffer);

  return {
    category: expectedCategory,
    subDir,
    fileName: finalFileName,
    filePath,
    url: `/uploads/${subDir}/${finalFileName}`,
    sizeBytes: buffer.length
  };
}

/**
 * Kiểm tra URL ảnh/media an toàn (chỉ cho phép /uploads/* hoặc http(s)://, chặn javascript:, data:text/html, path traversal).
 */
function sanitizeMediaUrl(url, { allowEmpty = true } = {}) {
  if (url === undefined || url === null) {
    return '';
  }
  if (typeof url !== 'string') {
    throw ApiError.badRequest('Đường dẫn media phải là chuỗi ký tự');
  }
  const trimmed = url.trim();
  if (!trimmed) {
    if (allowEmpty) return '';
    throw ApiError.badRequest('Đường dẫn media không được để trống');
  }

  const lower = trimmed.toLowerCase();
  if (lower.startsWith('javascript:') || lower.startsWith('vbscript:') || lower.startsWith('data:') || lower.startsWith('file:')) {
    throw ApiError.badRequest('Giao thức URL không được phép vì lý do bảo mật');
  }

  if (trimmed.startsWith('/uploads/')) {
    if (trimmed.includes('..') || trimmed.includes('\\') || trimmed.includes('\0')) {
      throw ApiError.badRequest('Đường dẫn nội bộ chứa ký tự không hợp lệ');
    }
    return trimmed;
  }

  let parsed;
  try {
    parsed = new URL(trimmed);
  } catch (_) {
    throw ApiError.badRequest('URL media phải bắt đầu bằng /uploads/, http:// hoặc https://');
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw ApiError.badRequest('Chỉ chấp nhận đường dẫn http://, https:// hoặc /uploads/');
  }

  return trimmed;
}

module.exports = {
  AUDIO_EXTS,
  IMAGE_EXTS,
  detectImageExtensionFromBuffer,
  detectAudioExtensionFromBuffer,
  decodeBase64Payload,
  saveValidatedBase64Upload,
  sanitizeMediaUrl
};
