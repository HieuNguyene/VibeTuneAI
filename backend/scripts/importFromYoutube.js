/**
 * ==============================================================================
 * CÔNG CỤ TỰ ĐỘNG CÀO NHẠC TỪ YOUTUBE VÀO HỆ THỐNG SPOTIFY CLONE
 * ==============================================================================
 * Tính năng:
 *  1. Tự động tải âm thanh chất lượng cao (.mp3) vào public/uploads/audios/
 *  2. Tự động tải ảnh bìa độ nét cao (thumbnail) vào public/uploads/covers/
 *  3. Tự động trích xuất Tên bài hát, Tên nghệ sĩ, Thời lượng
 *  4. Tự động bóc tách Lời bài hát (Lyrics) từ mô tả video hoặc phụ đề YouTube
 *  5. Tự động phân loại Thể loại (Genre), Cảm xúc (Mood) và BPM
 *  6. Tự động cập nhật CSDL MongoDB (Artist + Song), phát ngay được trên Android!
 *
 * Cách sử dụng:
 *  - Tải 1 link YouTube:
 *      node scripts/importFromYoutube.js "https://www.youtube.com/watch?v=kXpndn_0X7k"
 *  - Tìm kiếm theo từ khóa:
 *      node scripts/importFromYoutube.js "Đừng làm trái tim anh đau Sơn Tùng M-TP"
 *  - Tải hàng loạt từ file danh sách:
 *      node scripts/importFromYoutube.js --file urls.txt
 * ==============================================================================
 */

require('dotenv').config();
const fs = require('fs');
const path = require('path');
const { execSync, spawnSync } = require('child_process');
const https = require('https');
const http = require('http');
const mongoose = require('mongoose');

const Song = require('../src/models/Song');
const Artist = require('../src/models/Artist');
const Genre = require('../src/models/Genre');
const LastFmService = require('../src/services/lastFmService');
const EmbeddingService = require('../src/services/embeddingService');

// Đường dẫn Python và FFmpeg đã được cấu hình sẵn trong hệ thống
const PYTHON_PATH = 'C:\\Users\\Tho\\AppData\\Local\\Programs\\Python\\Python313\\python.exe';
const FFMPEG_DIR = 'C:\\Users\\Tho\\AppData\\Local\\Programs\\Python\\Python313\\Lib\\site-packages\\static_ffmpeg\\bin\\win32';

const AUDIOS_DIR = path.resolve(__dirname, '../public/uploads/audios');
const COVERS_DIR = path.resolve(__dirname, '../public/uploads/covers');

// Đảm bảo thư mục tồn tại
if (!fs.existsSync(AUDIOS_DIR)) fs.mkdirSync(AUDIOS_DIR, { recursive: true });
if (!fs.existsSync(COVERS_DIR)) fs.mkdirSync(COVERS_DIR, { recursive: true });

/**
 * Chuyển đổi chuỗi tiếng Việt thành slug an toàn cho tên file
 */
function slugify(text) {
  return text
    .toString()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '') || 'track_' + Date.now();
}

/**
 * Tải file hình ảnh qua URL
 */
function downloadFile(url, destPath) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(destPath);
    const client = url.startsWith('https') ? https : http;
    client.get(url, (response) => {
      if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
        return downloadFile(response.headers.location, destPath).then(resolve).catch(reject);
      }
      response.pipe(file);
      file.on('finish', () => {
        file.close(() => resolve(destPath));
      });
    }).on('error', (err) => {
      fs.unlink(destPath, () => {});
      reject(err);
    });
  });
}

/**
 * Làm sạch tên bài hát và nghệ sĩ từ tiêu đề YouTube hoặc metadata của YouTube Music
 */
function parseTitleAndArtist(rawTitle, uploader, info = {}) {
  if (info.artist && info.track) {
    return {
      artistName: info.artist.trim(),
      songTitle: info.track.trim()
    };
  }

  let clean = rawTitle
    .replace(/\(.*?\)/g, '')
    .replace(/\[.*?\]/g, '')
    .replace(/\|?\s*official\s*(music\s*video|mv|video|audio|lyric\s*video|lyrics)/gi, '')
    .replace(/\|?\s*mv\s*4k/gi, '')
    .replace(/\|?\s*4k/gi, '')
    .trim();

  let artistName = '';
  let songTitle = '';

  const splitters = ['|', ' - ', ' – ', ' : '];
  let parts = null;
  for (const s of splitters) {
    if (clean.includes(s)) {
      parts = clean.split(s).map(p => p.trim()).filter(Boolean);
      break;
    }
  }

  if (parts && parts.length >= 2) {
    artistName = parts[0];
    songTitle = parts.slice(1).join(' - ');
  } else {
    songTitle = clean;
    artistName = (info.creator || uploader).replace(/ - Topic$/i, '').replace(/Official$/i, '').trim();
  }

  // Làm gọn
  artistName = artistName.replace(/^["'\s]+|["'\s]+$/g, '');
  songTitle = songTitle.replace(/^["'\s]+|["'\s]+$/g, '');

  if (!songTitle) songTitle = rawTitle;
  if (!artistName) artistName = uploader || 'Various Artists';

  return { artistName, songTitle };
}

/**
 * Tra cứu lời bài hát trực tuyến từ LRCLIB API (Hỗ trợ lời chuẩn & karaoke có timestamp)
 */
function fetchOnlineLyrics(artistName, songTitle) {
  return new Promise((resolve) => {
    const cleanArtist = artistName.replace(/ft\..*|&.*/i, '').trim();
    const cleanTitle = songTitle.replace(/ft\..*|&.*/i, '').trim();
    const url = `https://lrclib.net/api/get?artist_name=${encodeURIComponent(cleanArtist)}&track_name=${encodeURIComponent(cleanTitle)}`;
    
    const req = https.get(url, { headers: { 'User-Agent': 'SpotifyClone/1.0' }, timeout: 4000 }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          if (json.plainLyrics && json.plainLyrics.length > 20) {
            return resolve(json.plainLyrics.trim());
          }
        } catch (e) {}
        resolve('');
      });
    });
    req.on('error', () => resolve(''));
    req.on('timeout', () => { req.destroy(); resolve(''); });
  });
}

/**
 * Bóc tách lời bài hát từ Description của YouTube
 */
function extractLyrics(description) {
  if (!description) return '';

  const patterns = [
    /(?:Lời bài hát|Lyrics|LỜI BÀI HÁT|LYRICS)[\s:*-]+([\s\S]*?)(?:[-=]{3,}|Theo dõi|Follow|Contact|Connect|MV|Audio|Sản xuất|Producer|Composer|$)/i,
    /(?:\[Lời bài hát\]|\[Lyrics\])[\s:*-]+([\s\S]*?)(?:\[|$)/i
  ];

  for (const pat of patterns) {
    const match = description.match(pat);
    if (match && match[1] && match[1].trim().length > 30) {
      return match[1].trim();
    }
  }

  return '';
}

/**
 * Phán đoán tâm trạng (Mood) và BPM sơ bộ từ tiêu đề/mô tả
 */
function inferMoodAndAudio(text) {
  const lower = text.toLowerCase();
  let moods = ['energetic'];
  let bpm = 120;
  let valence = 0.6;
  let energy = 0.7;
  let activities = ['party'];

  if (/buồn|suy|khóc|chia tay|cô đơn|đau lòng|nhớ em|nhớ anh|ballad/i.test(lower)) {
    moods = ['sad', 'melancholic'];
    bpm = 75;
    valence = 0.25;
    energy = 0.35;
    activities = ['relax'];
  } else if (/chill|thư giãn|mưa|lofi|lo-fi|ngủ|êm đềm|acoustic/i.test(lower)) {
    moods = ['calm', 'dreamy'];
    bpm = 85;
    valence = 0.5;
    energy = 0.4;
    activities = ['relax', 'study'];
  } else if (/tập trung|học|làm việc|code|study|focus/i.test(lower)) {
    moods = ['focused', 'calm'];
    bpm = 95;
    valence = 0.55;
    energy = 0.5;
    activities = ['focus', 'study'];
  } else if (/quẩy|cháy|rap|hip hop|remix|bốc|gym|chạy bộ/i.test(lower)) {
    moods = ['energetic', 'happy'];
    bpm = 132;
    valence = 0.8;
    energy = 0.9;
    activities = ['party', 'workout'];
  }

  return { moods, bpm, valence, energy, activities };
}

/**
 * Hàm chính: Nhập một bài hát từ URL hoặc Query YouTube
 */
async function importTrack(inputTarget) {
  console.log(`\n======================================================`);
  console.log(`🚀 ĐANG XỬ LÝ: "${inputTarget}"`);
  console.log(`======================================================`);

  // 1. Xác định target (URL hay Search query)
  let ytQuery = inputTarget.trim();
  if (!ytQuery.startsWith('http')) {
    ytQuery = `ytsearch1:${ytQuery}`;
  }

  // 2. Trích xuất metadata JSON qua yt-dlp
  console.log('🔍 Bước 1: Đang lấy thông tin siêu dữ liệu (Metadata) từ YouTube...');
  let info = null;
  try {
    const dumpCmd = `& "${PYTHON_PATH}" -m yt_dlp --dump-json --no-playlist "${ytQuery}"`;
    const stdout = execSync(dumpCmd, { shell: 'powershell.exe', maxBuffer: 10 * 1024 * 1024 }).toString();
    info = JSON.parse(stdout);
  } catch (err) {
    console.error('❌ Lỗi khi lấy thông tin YouTube:', err.message);
    return null;
  }

  const videoId = info.id;
  const webUrl = info.webpage_url || `https://www.youtube.com/watch?v=${videoId}`;
  const rawTitle = info.title || 'Unknown Title';
  const uploader = info.uploader || info.channel || 'Various Artists';
  const duration = Math.round(info.duration || 180);
  const description = info.description || '';
  const thumbnailUrl = info.thumbnail;

  console.log(`✅ Đã tìm thấy: "${rawTitle}" (Thời lượng: ${duration}s)`);

  // 3. Phân tích Tên bài & Tên ca sĩ
  const { artistName, songTitle } = parseTitleAndArtist(rawTitle, uploader, info);
  console.log(`🎤 Nghệ sĩ: ${artistName}`);
  console.log(`🎵 Tên bài hát: ${songTitle}`);

  // 4. Bóc tách Lời bài hát (Ưu tiên LRCLIB database, dự phòng Description YouTube)
  let lyrics = await fetchOnlineLyrics(artistName, songTitle);
  if (lyrics) {
    console.log(`📝 Đã lấy lời bài hát chuẩn từ cơ sở dữ liệu lời bài hát (${lyrics.split('\n').length} dòng)`);
  } else {
    lyrics = extractLyrics(description);
    if (lyrics) {
      console.log(`📝 Đã bóc tách lời bài hát từ mô tả video (${lyrics.split('\n').length} dòng)`);
    } else {
      console.log(`ℹ️ Không tìm thấy lời bài hát (sẽ để trống).`);
    }
  }

  // 5. Tải file âm thanh MP3
  const fileSlug = `${slugify(artistName)}_${slugify(songTitle)}_${videoId.substring(0, 6)}`;
  const audioFileName = `${fileSlug}.mp3`;
  const audioFilePath = path.join(AUDIOS_DIR, audioFileName);
  const relativeAudioUrl = `/uploads/audios/${audioFileName}`;

  console.log(`⬇️ Bước 2: Đang tải và chuyển đổi âm thanh MP3 chất lượng cao...`);
  try {
    const dlCmd = `& "${PYTHON_PATH}" -m yt_dlp -x --audio-format mp3 --audio-quality 0 --ffmpeg-location "${FFMPEG_DIR}" -o "${AUDIOS_DIR}/${fileSlug}.%(ext)s" "${webUrl}"`;
    execSync(dlCmd, { shell: 'powershell.exe', stdio: 'inherit' });
    console.log(`✅ Đã lưu file audio tại: ${audioFilePath}`);
  } catch (err) {
    console.error('❌ Lỗi tải audio:', err.message);
    return null;
  }

  // 6. Tải ảnh bìa (Cover image)
  const coverFileName = `${fileSlug}.jpg`;
  const coverFilePath = path.join(COVERS_DIR, coverFileName);
  const relativeCoverUrl = `/uploads/covers/${coverFileName}`;

  console.log(`🖼️ Bước 3: Đang tải ảnh bìa...`);
  try {
    if (thumbnailUrl) {
      await downloadFile(thumbnailUrl, coverFilePath);
      console.log(`✅ Đã lưu ảnh bìa tại: ${coverFilePath}`);
    }
  } catch (err) {
    console.warn('⚠️ Không thể tải ảnh bìa, dùng ảnh bìa mặc định:', err.message);
  }

  // 7. Cập nhật Cơ sở dữ liệu MongoDB
  console.log(`💾 Bước 4: Đang ghi dữ liệu vào MongoDB...`);

  // Tìm hoặc tạo Artist
  let artistDoc = await Artist.findOne({
    $or: [
      { name: new RegExp(`^${artistName.trim()}$`, 'i') },
      { name: new RegExp(artistName.trim().replace(/[&,]/g, '|'), 'i') }
    ]
  });

  if (!artistDoc) {
    let artistSlug = slugify(artistName);
    const existingSlug = await Artist.findOne({ slug: artistSlug });
    if (existingSlug) {
      artistSlug = `${artistSlug}-${Date.now().toString().slice(-4)}`;
    }

    artistDoc = await Artist.create({
      name: artistName,
      slug: artistSlug,
      bio: `Ca sĩ / Nhóm nhạc ${artistName}`,
      avatarUrl: fs.existsSync(coverFilePath) ? relativeCoverUrl : (thumbnailUrl || '')
    });
    console.log(`➕ Đã tạo nghệ sĩ mới: "${artistDoc.name}" (_id: ${artistDoc._id})`);
  } else {
    console.log(`🔗 Khớp nghệ sĩ có sẵn: "${artistDoc.name}" (_id: ${artistDoc._id})`);
  }

  // Tìm Thể loại phù hợp
  const { moods, bpm, valence, energy, activities } = inferMoodAndAudio(`${rawTitle} ${description}`);
  let defaultGenre = await Genre.findOne({ name: 'V-Pop' });
  if (/rap|hip hop/i.test(rawTitle)) {
    const rapGenre = await Genre.findOne({ name: /Hip Hop/i });
    if (rapGenre) defaultGenre = rapGenre;
  } else if (/us|uk|pop|english/i.test(rawTitle)) {
    const usGenre = await Genre.findOne({ name: /US-UK/i });
    if (usGenre) defaultGenre = usGenre;
  } else if (/kpop|k-pop|bts|blackpink/i.test(rawTitle)) {
    const kpopGenre = await Genre.findOne({ name: /K-Pop/i });
    if (kpopGenre) defaultGenre = kpopGenre;
  }

  // Upsert Song vào MongoDB
  let songDoc = await Song.findOne({
    title: new RegExp(`^${songTitle.trim()}$`, 'i'),
    artistId: artistDoc._id
  });

  const songData = {
    title: songTitle,
    artistId: artistDoc._id,
    genreIds: defaultGenre ? [defaultGenre._id] : [],
    duration: duration,
    audioUrl: relativeAudioUrl,
    coverImageUrl: fs.existsSync(coverFilePath) ? relativeCoverUrl : (thumbnailUrl || ''),
    lyrics: lyrics || '',
    year: new Date().getFullYear(),
    language: /^[a-zA-Z0-9\s.,?!'-]+$/.test(songTitle) ? 'en' : 'vi',
    moods: moods,
    mood: moods,
    activities: activities,
    description: `Bài hát ${songTitle} của ${artistDoc.name}`,
    tempo: bpm > 120 ? 'fast' : (bpm < 90 ? 'slow' : 'medium'),
    audio: {
      bpm: bpm,
      energy: energy,
      valence: valence,
      danceability: 0.6,
      acousticness: 0.3
    },
    audioFeatures: {
      tempoBpm: bpm,
      energy: energy,
      valence: valence,
      danceability: 0.6,
      acousticness: 0.3
    },
    isPublic: true
  };

  // Tự động sinh Tags và Vector Embedding (768 chiều) cho bài hát mới
  console.log('🧠 Bước 5: Đang sinh Tags và Vector Embedding 768 chiều...');
  try {
    const genreNames = defaultGenre ? [defaultGenre.name] : [];
    const enrichedTags = await LastFmService.enrichTags({
      title: songTitle,
      artist: artistDoc.name,
      genres: genreNames,
      lyrics: lyrics || ''
    });

    const semanticText = EmbeddingService.generateSongSemanticText(
      { title: songTitle, moods, activities, description: songData.description, lyrics },
      artistDoc.name,
      genreNames,
      enrichedTags
    );

    const embedding = await EmbeddingService.generateEmbedding(semanticText);

    songData.tags = enrichedTags;
    songData.semanticText = semanticText;
    songData.embedding = embedding;
    console.log(`✨ Đã sinh Vector 768 chiều thành công! Tags: [${enrichedTags.slice(0, 5).join(', ')}]`);
  } catch (vecErr) {
    console.warn('⚠️ Lỗi khi sinh vector embedding:', vecErr.message);
  }

  if (!songDoc) {
    songDoc = await Song.create(songData);
    console.log(`🎉 ĐÃ THÊM BÀI HÁT MỚI THÀNH CÔNG: "${songDoc.title}" (_id: ${songDoc._id})`);
  } else {
    Object.assign(songDoc, songData);
    await songDoc.save();
    console.log(`🔄 ĐÃ CẬP NHẬT BÀI HÁT CÓ SẴN: "${songDoc.title}" (_id: ${songDoc._id})`);
  }

  console.log(`✨ HOÀN TẤT! Bài hát đã sẵn sàng phát trên ứng dụng Android & tra cứu qua Chatbot AI!\n`);
  return songDoc;
}

/**
 * Hàm khởi chạy CLI
 */
async function main() {
  const args = process.argv.slice(2);
  if (args.length === 0) {
    console.log(`
Hướng dẫn sử dụng:
  node scripts/importFromYoutube.js "<URL_YOUTUBE hoặc TÊN_BÀI_HÁT>"
  node scripts/importFromYoutube.js --file <DUONG_DAN_FILE_TEXT>

Ví dụ:
  node scripts/importFromYoutube.js "https://www.youtube.com/watch?v=abPmZCZZrFA"
  node scripts/importFromYoutube.js "Đừng Làm Trái Tim Anh Đau Sơn Tùng M-TP"
  node scripts/importFromYoutube.js "Bao Tiền Một Mớ Bình Yên 14 Casper"
`);
    process.exit(0);
  }

  const mongoUri = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/music_app';
  console.log(`Đang kết nối MongoDB: ${mongoUri}...`);
  await mongoose.connect(mongoUri);

  try {
    if (args[0] === '--file') {
      const filePath = args[1];
      if (!fs.existsSync(filePath)) {
        console.error(`Không tìm thấy file: ${filePath}`);
        process.exit(1);
      }
      const lines = fs.readFileSync(filePath, 'utf8')
        .split('\n')
        .map(l => l.trim())
        .filter(l => l.length > 0 && !l.startsWith('#'));

      console.log(`Tìm thấy ${lines.length} bài hát trong danh sách cần nhập.`);
      for (let i = 0; i < lines.length; i++) {
        console.log(`[${i + 1}/${lines.length}] Đang xử lý: ${lines[i]}`);
        try {
          await importTrack(lines[i]);
        } catch (itemErr) {
          console.error(`⚠️ Lỗi khi xử lý bài hát [${i + 1}]:`, itemErr.message);
        }
      }
    } else {
      const query = args.join(' ');
      await importTrack(query);
    }
  } catch (err) {
    console.error('Lỗi nghiêm trọng:', err);
  } finally {
    await mongoose.disconnect();
    console.log('Đã ngắt kết nối CSDL.');
  }
}

main();
