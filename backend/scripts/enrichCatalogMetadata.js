/**
 * ==============================================================================
 * CÔNG CỤ TỰ ĐỘNG BỔ SUNG LỜI BÀI HÁT & ẢNH BÌA THẬT CHO TOÀN BỘ KHO NHẠC
 * ==============================================================================
 * Quét toàn bộ bài hát trong MongoDB:
 *  1. Tìm và cập nhật LỜI BÀI HÁT HOÀN CHỈNH (từ LRCLIB API & YouTube)
 *  2. Tìm và tải ẢNH BÌA GỐC ĐỘ PHÂN GIẢI CAO (từ iTunes & YouTube)
 *     thay thế toàn bộ ảnh mẫu Unsplash tạm thời.
 *  3. Lưu trực tiếp ảnh bìa vào public/uploads/covers/
 *  4. Đồng bộ CSDL MongoDB
 * ==============================================================================
 */

require('dotenv').config();
const fs = require('fs');
const path = require('path');
const https = require('https');
const http = require('http');
const { execSync } = require('child_process');
const mongoose = require('mongoose');

const Song = require('../src/models/Song');
const Artist = require('../src/models/Artist');

const PYTHON_PATH = 'C:\\Users\\Tho\\AppData\\Local\\Programs\\Python\\Python313\\python.exe';
const COVERS_DIR = path.resolve(__dirname, '../public/uploads/covers');
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

function downloadImage(url, destPath) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(destPath);
    const client = url.startsWith('https') ? https : http;
    client.get(url, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        return downloadImage(res.headers.location, destPath).then(resolve).catch(reject);
      }
      res.pipe(file);
      file.on('finish', () => file.close(() => resolve(destPath)));
    }).on('error', (err) => {
      fs.unlink(destPath, () => {});
      reject(err);
    });
  });
}

function httpGetJson(url) {
  return new Promise((resolve) => {
    const client = url.startsWith('https') ? https : http;
    const req = client.get(url, { headers: { 'User-Agent': 'SpotifyClone/1.0' }, timeout: 6000 }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          resolve(null);
        }
      });
    });
    req.on('error', () => resolve(null));
    req.on('timeout', () => { req.destroy(); resolve(null); });
  });
}

/**
 * Tìm lời bài hát qua LRCLIB API
 */
async function searchLyrics(artistName, songTitle) {
  // Thử 1: Direct GET
  const cleanArtist = artistName.replace(/&.*/, '').replace(/ft\..*/i, '').trim();
  const cleanTitle = songTitle.replace(/\(.*?\)/g, '').trim();

  let url1 = `https://lrclib.net/api/get?artist_name=${encodeURIComponent(cleanArtist)}&track_name=${encodeURIComponent(cleanTitle)}`;
  let res1 = await httpGetJson(url1);
  if (res1 && res1.plainLyrics && res1.plainLyrics.length > 50) {
    return res1.plainLyrics.trim();
  }

  // Thử 2: Search theo query
  let url2 = `https://lrclib.net/api/search?q=${encodeURIComponent(`${cleanTitle} ${cleanArtist}`)}`;
  let res2 = await httpGetJson(url2);
  if (Array.isArray(res2) && res2.length > 0) {
    const match = res2.find(item => item.plainLyrics && item.plainLyrics.length > 50);
    if (match) return match.plainLyrics.trim();
  }

  // Thử 3: Search chỉ theo title
  let url3 = `https://lrclib.net/api/search?q=${encodeURIComponent(cleanTitle)}`;
  let res3 = await httpGetJson(url3);
  if (Array.isArray(res3) && res3.length > 0) {
    const match = res3.find(item => item.plainLyrics && item.plainLyrics.length > 50);
    if (match) return match.plainLyrics.trim();
  }

  return null;
}

/**
 * Tìm ảnh bìa từ iTunes Search API (Chất lượng 600x600 chuẩn hãng phát hành)
 */
async function searchItunesCover(artistName, songTitle) {
  const cleanArtist = artistName.replace(/&.*/, '').trim();
  const cleanTitle = songTitle.replace(/\(.*?\)/g, '').trim();
  const url = `https://itunes.apple.com/search?term=${encodeURIComponent(`${cleanArtist} ${cleanTitle}`)}&entity=song&limit=1`;
  
  const json = await httpGetJson(url);
  if (json && json.results && json.results.length > 0 && json.results[0].artworkUrl100) {
    return json.results[0].artworkUrl100.replace('100x100bb', '600x600bb');
  }
  return null;
}

/**
 * Tìm ảnh bìa & lời từ YouTube qua yt-dlp khi các nguồn khác không có
 */
async function searchYoutubeBackup(artistName, songTitle) {
  try {
    const query = `ytsearch1:${songTitle} ${artistName}`;
    const cmd = `& "${PYTHON_PATH}" -m yt_dlp --dump-json --no-playlist "${query}"`;
    const stdout = execSync(cmd, { shell: 'powershell.exe', maxBuffer: 10 * 1024 * 1024 }).toString();
    const info = JSON.parse(stdout);
    
    let lyrics = '';
    if (info.description) {
      const match = info.description.match(/(?:Lời bài hát|Lyrics|LỜI BÀI HÁT|LYRICS)[\s:*-]+([\s\S]*?)(?:[-=]{3,}|Theo dõi|Follow|Contact|Connect|MV|Audio|Sản xuất|Producer|Composer|$)/i);
      if (match && match[1] && match[1].trim().length > 50) {
        lyrics = match[1].trim();
      }
    }
    return {
      thumbnail: info.thumbnail,
      lyrics: lyrics
    };
  } catch (e) {
    return { thumbnail: null, lyrics: null };
  }
}

async function main() {
  const mongoUri = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/music_app';
  console.log('Đang kết nối MongoDB...');
  await mongoose.connect(mongoUri);

  const songs = await Song.find({}).populate('artistId');
  console.log(`Tìm thấy ${songs.length} bài hát trong cơ sở dữ liệu.\n`);

  let lyricsUpdatedCount = 0;
  let coversUpdatedCount = 0;

  for (let i = 0; i < songs.length; i++) {
    const song = songs[i];
    const artistName = song.artistId ? song.artistId.name : 'Unknown Artist';
    const isLyricsDummy = !song.lyrics || song.lyrics.trim().length < 80;
    const isCoverUnsplash = !song.coverImageUrl || song.coverImageUrl.includes('images.unsplash.com');

    console.log(`------------------------------------------------------`);
    console.log(`[${i + 1}/${songs.length}] 🎵 "${song.title}" - ${artistName}`);
    console.log(`   Trạng thái hiện tại: Lyrics=${isLyricsDummy ? '⚠️ THIẾU' : '✅ ĐẦY ĐỦ'} | Cover=${isCoverUnsplash ? '⚠️ ẢNH TẠM' : '✅ ĐÃ CÓ ẢNH THẬT'}`);

    let needsSave = false;

    // 1. CẬP NHẬT LỜI BÀI HÁT
    if (isLyricsDummy) {
      process.stdout.write(`   🔍 Đang tìm lời bài hát... `);
      let foundLyrics = await searchLyrics(artistName, song.title);
      
      if (!foundLyrics) {
        // Dự phòng: Tìm qua YouTube
        const yt = await searchYoutubeBackup(artistName, song.title);
        if (yt.lyrics) foundLyrics = yt.lyrics;
      }

      if (foundLyrics) {
        song.lyrics = foundLyrics;
        needsSave = true;
        lyricsUpdatedCount++;
        console.log(`✅ Đã tìm thấy (${foundLyrics.split('\n').length} dòng)`);
      } else {
        console.log(`❌ Chưa tìm thấy lời`);
      }
    }

    // 2. CẬP NHẬT ẢNH BÌA
    if (isCoverUnsplash) {
      process.stdout.write(`   🖼️ Đang tìm ảnh bìa chính thức... `);
      let coverUrl = await searchItunesCover(artistName, song.title);

      if (!coverUrl) {
        const yt = await searchYoutubeBackup(artistName, song.title);
        if (yt.thumbnail) coverUrl = yt.thumbnail;
      }

      if (coverUrl) {
        const fileSlug = `${slugify(artistName)}_${slugify(song.title)}`;
        const localFileName = `${fileSlug}.jpg`;
        const localFilePath = path.join(COVERS_DIR, localFileName);
        
        try {
          await downloadImage(coverUrl, localFilePath);
          song.coverImageUrl = `/uploads/covers/${localFileName}`;
          needsSave = true;
          coversUpdatedCount++;
          console.log(`✅ Đã tải về: /uploads/covers/${localFileName}`);
        } catch (e) {
          console.log(`❌ Lỗi tải ảnh: ${e.message}`);
        }
      } else {
        console.log(`❌ Không tìm thấy ảnh`);
      }
    }

    if (needsSave) {
      await song.save();
    }
  }

  console.log(`\n======================================================`);
  console.log(`🎉 HOÀN TẤT BỔ SUNG DỮ LIỆU!`);
  console.log(`- Số bài hát đã cập nhật Lời đầy đủ: ${lyricsUpdatedCount}`);
  console.log(`- Số bài hát đã cập nhật Ảnh bìa thật: ${coversUpdatedCount}`);
  console.log(`======================================================\n`);

  await mongoose.disconnect();
}

main();
