const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });
const config = require('../src/config/environment');

const songSchema = new mongoose.Schema({
  title: String,
  artist: { type: mongoose.Schema.Types.ObjectId, ref: 'Artist' },
  lyrics: String,
  duration: Number
}, { strict: false });

const artistSchema = new mongoose.Schema({
  name: String
}, { strict: false });

const Song = mongoose.model('Song', songSchema);
const Artist = mongoose.model('Artist', artistSchema);

function cleanString(str) {
  if (!str) return '';
  return str
    .replace(/\(.*?\)|\[.*?\]/g, '')
    .replace(/\|?\s*official\s*(music\s*video|mv|video|audio|lyric\s*video|lyrics)/gi, '')
    .replace(/ft\..*|feat\..*|x.*/gi, '')
    .trim();
}

async function fetchLrc(title, artistName) {
  const cleanT = cleanString(title);
  const cleanA = cleanString(artistName);

  const queries = [
    `${cleanT} ${cleanA}`,
    `${title} ${artistName}`,
    cleanT
  ];

  for (const q of queries) {
    try {
      const url = `https://lrclib.net/api/search?q=${encodeURIComponent(q.trim())}`;
      const res = await fetch(url, {
        headers: { 'User-Agent': 'VibeTune/1.0' },
        signal: AbortSignal.timeout(4000)
      });
      if (res.ok) {
        const list = await res.json();
        if (Array.isArray(list) && list.length > 0) {
          const synced = list.find(item => item.syncedLyrics && item.syncedLyrics.trim().length > 0);
          if (synced) {
            return synced.syncedLyrics;
          }
        }
      }
    } catch (e) {
      // Continue next query
    }
  }
  return null;
}

async function run() {
  await mongoose.connect(config.mongodbUri);
  console.log('Connected to MongoDB:', config.mongodbUri);

  const songs = await Song.find().populate('artist', 'name');
  console.log(`Found ${songs.length} songs. Scanning for missing or plain-text lyrics...`);

  let updatedCount = 0;
  let alreadyLrcCount = 0;
  let notFoundCount = 0;

  for (let i = 0; i < songs.length; i++) {
    const s = songs[i];
    const hasLrc = s.lyrics && s.lyrics.includes('[0');
    if (hasLrc) {
      alreadyLrcCount++;
      continue;
    }

    const artistName = (s.artist && s.artist.name) ? s.artist.name : '';
    console.log(`[${i + 1}/${songs.length}] Searching LRC for: "${s.title}" - "${artistName}"...`);
    const lrc = await fetchLrc(s.title, artistName);

    if (lrc) {
      s.lyrics = lrc;
      await s.save();
      updatedCount++;
      console.log(`   ✅ Đã cập nhật LRC synced lyrics (${lrc.split('\n').length} dòng)`);
    } else {
      notFoundCount++;
      console.log(`   ⚠️ Không tìm thấy LRC trên LRCLIB`);
    }

    // Delay 300ms to be polite to lrclib API
    await new Promise(r => setTimeout(r, 300));
  }

  console.log('\n--- KẾT QUẢ ĐỒNG BỘ LỜI BÀI HÁT ---');
  console.log(`Tổng bài hát: ${songs.length}`);
  console.log(`Đã có LRC từ trước: ${alreadyLrcCount}`);
  console.log(`Vừa cập nhật LRC chuẩn mới: ${updatedCount}`);
  console.log(`Chưa có LRC (giữ nguyên plain text): ${notFoundCount}`);
  console.log(`Tổng số bài hát có LRC hiện tại: ${alreadyLrcCount + updatedCount} / ${songs.length} (${Math.round((alreadyLrcCount + updatedCount) * 100 / songs.length)}%)`);

  await mongoose.disconnect();
  process.exit(0);
}

run().catch(err => {
  console.error(err);
  process.exit(1);
});
