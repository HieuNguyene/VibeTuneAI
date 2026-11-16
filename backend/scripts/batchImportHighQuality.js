/**
 * batchImportHighQuality.js
 * CÔNG CỤ TỰ ĐỘNG NHẬP NHẠC HÀNG LOẠT TỪ YOUTUBE ĐẦY ĐỦ THÔNG TIN
 * - Bóc tách chính xác Ca sĩ & Tên bài hát (chống đảo ngược)
 * - Tải Audio MP3 chất lượng cao
 * - Tải Ảnh bìa độ nét cao
 * - Tra cứu Lời bài hát (LRCLIB synced lyrics + plain lyrics + YouTube Description)
 * - Phân loại Thể loại chuẩn (10 genres) & Cảm xúc (Mood)
 * - Tự động cập nhật CSDL MongoDB, sinh Vector Embedding 768 chiều và lưu backup
 */

require('dotenv').config();
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const https = require('https');
const http = require('http');
const mongoose = require('mongoose');

const Song = require('../src/models/Song');
const Artist = require('../src/models/Artist');
const Genre = require('../src/models/Genre');
const LastFmService = require('../src/services/lastFmService');
const EmbeddingService = require('../src/services/embeddingService');

const PYTHON_PATH = 'C:\\Users\\Tho\\AppData\\Local\\Programs\\Python\\Python313\\python.exe';
const FFMPEG_DIR = 'C:\\Users\\Tho\\AppData\\Local\\Programs\\Python\\Python313\\Lib\\site-packages\\static_ffmpeg\\bin\\win32';

const AUDIOS_DIR = path.resolve(__dirname, '../public/uploads/audios');
const COVERS_DIR = path.resolve(__dirname, '../public/uploads/covers');
const CATALOG_BACKUP = path.resolve(__dirname, '../src/seeder/catalog_enriched.json');

if (!fs.existsSync(AUDIOS_DIR)) fs.mkdirSync(AUDIOS_DIR, { recursive: true });
if (!fs.existsSync(COVERS_DIR)) fs.mkdirSync(COVERS_DIR, { recursive: true });

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
 * Tra cứu lời bài hát trực tuyến từ LRCLIB (hỗ trợ synced LRC có timestamp & plain text)
 */
async function fetchOnlineLyrics(songTitle, artistName) {
  try {
    const cleanT = songTitle.replace(/\(.*?\)|\[.*?\]/g, '').trim();
    const cleanA = artistName.replace(/\(.*?\)|\[.*?\]|ft\..*|feat\..*|x.*/gi, '').trim();

    // 1. Thử tìm kiếm theo cả Tên bài + Nghệ sĩ
    const searchUrl = `https://lrclib.net/api/search?q=${encodeURIComponent(cleanT + ' ' + cleanA)}`;
    const res = await fetch(searchUrl, { headers: { 'User-Agent': 'SpotifyClone/1.0' }, signal: AbortSignal.timeout(4000) });
    if (res.ok) {
      const list = await res.json();
      if (Array.isArray(list) && list.length > 0) {
        const found = list.find(item => item.syncedLyrics || item.plainLyrics);
        if (found) {
          return found.syncedLyrics || found.plainLyrics;
        }
      }
    }

    // 2. Thử tìm kiếm theo chỉ Tên bài
    const searchTitleUrl = `https://lrclib.net/api/search?q=${encodeURIComponent(cleanT)}`;
    const res2 = await fetch(searchTitleUrl, { headers: { 'User-Agent': 'SpotifyClone/1.0' }, signal: AbortSignal.timeout(4000) });
    if (res2.ok) {
      const list2 = await res2.json();
      if (Array.isArray(list2) && list2.length > 0) {
        const found2 = list2.find(item => item.syncedLyrics || item.plainLyrics);
        if (found2) {
          return found2.syncedLyrics || found2.plainLyrics;
        }
      }
    }
  } catch (err) {
    // Không làm gián đoạn nếu API timeout
  }
  return '';
}

/**
 * Bóc tách lời bài hát từ mô tả video YouTube
 */
function extractLyricsFromDescription(description) {
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
 * Phân tích thông minh Ca sĩ & Tên bài hát tránh bị đảo ngược
 */
function parseArtistAndTitle(rawTitle, uploader, knownArtistNames = []) {
  let clean = rawTitle
    .replace(/\|?\s*official\s*(music\s*video|mv|video|audio|lyric\s*video|lyrics)/gi, '')
    .replace(/\|?\s*mv\s*4k/gi, '')
    .replace(/\|?\s*4k/gi, '')
    .replace(/\(.*?(mv|video|audio|lyrics|album|ver).*?\)/gi, '')
    .replace(/\[.*?(mv|video|audio|lyrics|album|ver).*?\]/gi, '')
    .replace(/‘.*?’\s*(mini\s*album|album)/gi, '')
    .replace(/'.*?'\s*(mini\s*album|album)/gi, '')
    .trim();

  const splitters = [' - ', ' – ', ' | ', ' : '];
  let parts = null;
  for (const s of splitters) {
    if (clean.includes(s)) {
      parts = clean.split(s).map(p => p.trim()).filter(Boolean);
      break;
    }
  }

  const cleanUploader = (uploader || '')
    .replace(/ - Topic$/i, '')
    .replace(/Official$/i, '')
    .replace(/\/\/.*$/i, '')
    .trim();

  if (!parts || parts.length < 2) {
    return {
      artistName: cleanUploader || 'Various Artists',
      songTitle: clean
    };
  }

  const part0 = parts[0].trim();
  const part1 = parts.slice(1).join(' - ').trim();

  // Kiểm tra xem part nào là nghệ sĩ
  let part0IsArtist = false;
  let part1IsArtist = false;

  // 1. Kiểm tra khớp uploader
  if (cleanUploader) {
    const uploaderLower = cleanUploader.toLowerCase();
    if (part0.toLowerCase().includes(uploaderLower) || uploaderLower.includes(part0.toLowerCase())) {
      part0IsArtist = true;
    }
    if (part1.toLowerCase().includes(uploaderLower) || uploaderLower.includes(part1.toLowerCase())) {
      part1IsArtist = true;
    }
  }

  // 2. Kiểm tra dấu hiệu nghệ sĩ cộng tác (x, ft., feat., &)
  const collabPattern = /\s+(x|ft\.|feat\.|&)\s+/i;
  if (collabPattern.test(part1) && !collabPattern.test(part0)) {
    part1IsArtist = true;
  } else if (collabPattern.test(part0) && !collabPattern.test(part1)) {
    part0IsArtist = true;
  }

  // 3. Kiểm tra danh sách nghệ sĩ đã biết trong DB
  for (const known of knownArtistNames) {
    const kLower = known.toLowerCase();
    if (part0.toLowerCase() === kLower) part0IsArtist = true;
    if (part1.toLowerCase() === kLower) part1IsArtist = true;
  }

  let artistName = '';
  let songTitle = '';

  if (part1IsArtist && !part0IsArtist) {
    // Kiểu "Tên bài hát - Tên ca sĩ"
    songTitle = part0;
    artistName = part1;
  } else {
    // Mặc định hoặc part0IsArtist: "Tên ca sĩ - Tên bài hát"
    artistName = part0;
    songTitle = part1;
  }

  // Dọn dẹp ký tự thừa
  artistName = artistName.replace(/^["'\s]+|["'\s]+$/g, '').trim();
  songTitle = songTitle.replace(/^["'\s]+|["'\s]+$/g, '').trim();

  if (!songTitle) songTitle = clean;
  if (!artistName) artistName = cleanUploader || 'Various Artists';

  return { artistName, songTitle };
}

/**
 * Phân loại Thể loại (Genre) và Tâm trạng (Mood)
 */
function classifyGenreAndMood(rawTitle, artistName, description, allGenresMap) {
  const text = `${rawTitle} ${artistName} ${description}`.toLowerCase();
  
  let genreSlugs = [];
  let moods = ['energetic'];
  let bpm = 120;
  let valence = 0.6;
  let energy = 0.7;
  let activities = ['party'];

  // Phân loại Thể loại
  if (/rap|drill|hiphop|hip hop|trap|boombap|freestyle|cypher/i.test(text) || /mck|obito|binz|b ray|andree|tommy|vsoul/i.test(artistName.toLowerCase())) {
    genreSlugs.push('hip-hop-rap');
    moods = ['energetic', 'confident'];
    bpm = 130;
    energy = 0.85;
    activities = ['party', 'workout'];
  }
  if (/ballad|suy|acoustic|nhẹ nhàng|nỗi nhớ|buồn|cô đơn|chia tay|khóc/i.test(text) || /vũ\.|hoàng dũng|hà anh tuấn|đức phúc|mr\. siro/i.test(artistName.toLowerCase())) {
    genreSlugs.push('indie-ballad');
    moods = ['sad', 'melancholic'];
    bpm = 75;
    valence = 0.3;
    energy = 0.35;
    activities = ['relax'];
  }
  if (/r&b|soul|mượt mà|rnb/i.test(text) || /soobin|wren evans|grey d|madihu|hoàng tôn|justatee/i.test(artistName.toLowerCase())) {
    genreSlugs.push('r-and-b-soul');
    if (!moods.includes('calm')) moods.push('calm');
    bpm = 95;
    energy = 0.55;
    activities = ['relax', 'focus'];
  }
  if (/chill|lofi|lo-fi|slowed|reverb|thư giãn|mưa/i.test(text)) {
    genreSlugs.push('lo-fi-chill');
    moods = ['calm', 'dreamy'];
    bpm = 85;
    energy = 0.4;
    activities = ['study', 'relax'];
  }
  if (/edm|remix|phonk|funk|bass|house|vinahouse/i.test(text)) {
    genreSlugs.push('electronic-edm');
    moods = ['energetic', 'happy'];
    bpm = 135;
    energy = 0.95;
    activities = ['party', 'workout'];
  }
  if (/us-uk|english|pop us|billboard/i.test(text) || /^[a-zA-Z0-9\s.,?!'-]+$/.test(artistName) && !/v-pop/i.test(text) && /ed sheeran|bruno mars|taylor|ariana/i.test(artistName.toLowerCase())) {
    genreSlugs.push('us-uk-pop');
  }
  if (/k-pop|kpop|korean|bts|blackpink|bigbang/i.test(text)) {
    genreSlugs.push('k-pop');
  }
  if (/cách mạng|truyền thống|tiền chiến|hào hùng|quê hương/i.test(text)) {
    genreSlugs.push('nhac-cach-mang');
  }
  if (/podcast|talkshow|tập \d+|ep\d+/i.test(text)) {
    genreSlugs.push('podcast');
  }

  // Mặc định gán V-Pop nếu chưa có hoặc là nhạc Việt
  if (genreSlugs.length === 0 || (!genreSlugs.includes('v-pop') && !genreSlugs.includes('us-uk-pop') && !genreSlugs.includes('k-pop') && !genreSlugs.includes('podcast'))) {
    if (genreSlugs.length === 0 || !genreSlugs.includes('hip-hop-rap')) {
      genreSlugs.push('v-pop');
    }
  }

  const genreIds = genreSlugs
    .map(slug => allGenresMap[slug]?._id)
    .filter(Boolean);

  return {
    genreIds,
    genreSlugs,
    moods,
    bpm,
    valence,
    energy,
    activities
  };
}

async function runBatchImport() {
  console.log('====================================================================');
  console.log('   BẮT ĐẦU NHẬP NHẠC HÀNG LOẠT TỪ YOUTUBE VÀO SPOTIFY CLONE');
  console.log('====================================================================\n');

  const mongoUri = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/music_app';
  console.log(`📡 Kết nối MongoDB: ${mongoUri}...`);
  await mongoose.connect(mongoUri);

  const urlsFile = path.resolve(__dirname, 'batch_youtube_urls.txt');
  if (!fs.existsSync(urlsFile)) {
    console.error(`❌ Không tìm thấy file: ${urlsFile}`);
    process.exit(1);
  }

  const urls = fs.readFileSync(urlsFile, 'utf8')
    .split('\n')
    .map(l => l.trim())
    .filter(l => l.length > 0 && !l.startsWith('#'));

  console.log(`📋 Tổng cộng: ${urls.length} bài hát cần xử lý.\n`);

  // Lấy danh mục thể loại và nghệ sĩ hiện có để đối chiếu
  const genres = await Genre.find({}).lean();
  const allGenresMap = {};
  genres.forEach(g => { allGenresMap[g.slug] = g; });

  const existingArtists = await Artist.find({}).lean();
  const knownArtistNames = existingArtists.map(a => a.name);

  let successCount = 0;
  let skippedCount = 0;
  let errorCount = 0;

  for (let i = 0; i < urls.length; i++) {
    const rawUrl = urls[i];
    const matchVid = rawUrl.match(/[?&]v=([^&]+)/);
    const videoId = matchVid ? matchVid[1] : '';
    const cleanUrl = videoId ? `https://www.youtube.com/watch?v=${videoId}` : rawUrl;

    console.log(`--------------------------------------------------------------------`);
    console.log(`[${i + 1}/${urls.length}] 🎯 Đang xử lý: ${cleanUrl}`);

    try {
      // 1. Kiểm tra xem file audio đã tải trước đó chưa
      const existingSong = await Song.findOne({
        audioUrl: new RegExp(videoId.substring(0, 6), 'i')
      }).populate('artistId');

      if (existingSong) {
        console.log(`⏩ Đã tồn tại trong DB: "${existingSong.title}" - ${existingSong.artistId?.name || 'Unknown'}. Bỏ qua.`);
        skippedCount++;
        continue;
      }

      // 2. Lấy metadata từ YouTube
      let info = null;
      try {
        const dumpCmd = `& "${PYTHON_PATH}" -m yt_dlp --dump-json --no-playlist "${cleanUrl}"`;
        const stdout = execSync(dumpCmd, { shell: 'powershell.exe', maxBuffer: 10 * 1024 * 1024 }).toString();
        info = JSON.parse(stdout);
      } catch (dumpErr) {
        console.error(`❌ Lỗi lấy thông tin video [${videoId}]: ${dumpErr.message}`);
        errorCount++;
        continue;
      }

      const rawTitle = info.title || 'Unknown Title';
      const uploader = info.uploader || info.channel || '';
      const duration = Math.round(info.duration || 180);
      const description = info.description || '';
      const thumbnailUrl = info.thumbnail;

      // 3. Phân tích Ca sĩ & Bài hát thông minh
      const { artistName, songTitle } = parseArtistAndTitle(rawTitle, uploader, knownArtistNames);
      console.log(`   🎤 Ca sĩ : "${artistName}"`);
      console.log(`   🎵 Bài hát: "${songTitle}" (${duration}s)`);

      // 4. Tra cứu lời bài hát
      let lyrics = await fetchOnlineLyrics(songTitle, artistName);
      if (lyrics) {
        console.log(`   📝 Lời bài hát: Tìm thấy trên LRCLIB (${lyrics.split('\n').length} dòng)`);
      } else {
        lyrics = extractLyricsFromDescription(description);
        if (lyrics) {
          console.log(`   📝 Lời bài hát: Bóc tách từ mô tả video (${lyrics.split('\n').length} dòng)`);
        } else {
          console.log(`   ℹ️ Lời bài hát: Không có (sẽ để trống)`);
        }
      }

      // 5. Tải Audio MP3
      const fileSlug = `${slugify(artistName)}_${slugify(songTitle)}_${videoId.substring(0, 6)}`;
      const audioFileName = `${fileSlug}.mp3`;
      const audioFilePath = path.join(AUDIOS_DIR, audioFileName);
      const relativeAudioUrl = `/uploads/audios/${audioFileName}`;

      if (!fs.existsSync(audioFilePath)) {
        console.log(`   ⬇️ Đang tải âm thanh MP3 chất lượng cao...`);
        const dlCmd = `& "${PYTHON_PATH}" -m yt_dlp -x --audio-format mp3 --audio-quality 0 --ffmpeg-location "${FFMPEG_DIR}" --no-playlist -o "${AUDIOS_DIR}/${fileSlug}.%(ext)s" "${cleanUrl}"`;
        execSync(dlCmd, { shell: 'powershell.exe', stdio: 'ignore' });
      }

      // 6. Tải Ảnh bìa
      const coverFileName = `${fileSlug}.jpg`;
      const coverFilePath = path.join(COVERS_DIR, coverFileName);
      const relativeCoverUrl = `/uploads/covers/${coverFileName}`;

      if (!fs.existsSync(coverFilePath) && thumbnailUrl) {
        try {
          await downloadFile(thumbnailUrl, coverFilePath);
        } catch (coverErr) {
          console.warn(`   ⚠️ Lỗi tải ảnh bìa: ${coverErr.message}`);
        }
      }

      // 7. Tạo / Khớp Nghệ sĩ (Artist)
      let artistDoc = await Artist.findOne({
        name: new RegExp(`^${artistName.trim()}$`, 'i')
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
          bio: `Nghệ sĩ ${artistName}`,
          avatarUrl: fs.existsSync(coverFilePath) ? relativeCoverUrl : (thumbnailUrl || '')
        });
        if (!knownArtistNames.includes(artistName)) knownArtistNames.push(artistName);
        console.log(`   ➕ Tạo nghệ sĩ mới: "${artistDoc.name}"`);
      }

      // 8. Phân loại Thể loại & Tâm trạng
      const { genreIds, genreSlugs, moods, bpm, valence, energy, activities } = classifyGenreAndMood(
        rawTitle,
        artistName,
        description,
        allGenresMap
      );

      // 9. Tạo Bài hát (Song) trong MongoDB
      const songData = {
        title: songTitle,
        artistId: artistDoc._id,
        genreIds: genreIds,
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
          danceability: 0.65,
          acousticness: 0.3
        },
        audioFeatures: {
          tempoBpm: bpm,
          energy: energy,
          valence: valence,
          danceability: 0.65,
          acousticness: 0.3
        },
        playsCount: Math.floor(Math.random() * 800000) + 120000,
        likesCount: Math.floor(Math.random() * 45000) + 5000,
        isPublic: true
      };

      // 10. Sinh Tags và Vector Embedding 768 chiều
      try {
        const genreNames = genreSlugs.map(s => allGenresMap[s]?.name).filter(Boolean);
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
      } catch (embErr) {
        // Tiếp tục nếu quota embedding bị nghẽn
      }

      const createdSong = await Song.create(songData);
      console.log(`   ✅ Thêm thành công: "${createdSong.title}" [${genreSlugs.join(', ')}]`);
      successCount++;
    } catch (trackErr) {
      console.error(`   ❌ Lỗi xử lý bài hát: ${trackErr.message}`);
      errorCount++;
    }
  }

  console.log('\n====================================================================');
  console.log(`🎉 HOÀN TẤT NHẬP NHẠC!`);
  console.log(`   - Thành công : ${successCount} bài hát`);
  console.log(`   - Bỏ qua     : ${skippedCount} bài hát`);
  console.log(`   - Thất bại   : ${errorCount} bài hát`);
  console.log('====================================================================\n');

  // Đồng bộ lại genreIds cho nghệ sĩ
  console.log('🔄 Đang đồng bộ lại thể loại cho các nghệ sĩ...');
  const allArtists = await Artist.find({});
  for (const art of allArtists) {
    const artSongs = await Song.find({ artistId: art._id }).select('genreIds').lean();
    const gSet = new Set();
    artSongs.forEach(s => (s.genreIds || []).forEach(gid => gSet.add(gid.toString())));
    art.genreIds = Array.from(gSet);
    await art.save();
  }
  console.log('✅ Đã đồng bộ thể loại cho toàn bộ nghệ sĩ!');

  // Sao lưu toàn bộ CSDL ra catalog_enriched.json
  console.log('💾 Đang xuất file backup catalog_enriched.json...');
  const allSongsWithPop = await Song.find({ isPublic: true })
    .populate('artistId')
    .populate('genreIds')
    .lean();

  const backupData = {
    genres: await Genre.find({}).lean(),
    artists: await Artist.find({}).lean(),
    songs: allSongsWithPop,
    updatedAt: new Date().toISOString(),
    totalSongs: allSongsWithPop.length
  };

  fs.writeFileSync(CATALOG_BACKUP, JSON.stringify(backupData, null, 2), 'utf8');
  console.log(`✅ Đã lưu ${backupData.totalSongs} bài hát vào: ${CATALOG_BACKUP}`);

  await mongoose.disconnect();
  console.log('Đã ngắt kết nối CSDL.');
}

runBatchImport().catch(err => {
  console.error('Lỗi nghiêm trọng:', err);
  process.exit(1);
});
