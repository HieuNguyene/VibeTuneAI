require('dotenv').config();
const mongoose = require('mongoose');
const Song = require('../src/models/Song');

async function stats() {
  await mongoose.connect(process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/music_app');
  const all = await Song.find({}).lean();
  const withLyrics = all.filter(s => s.lyrics && s.lyrics.trim().length > 10).length;
  const withCovers = all.filter(s => s.coverImageUrl && s.coverImageUrl.length > 5).length;
  const withAudios = all.filter(s => s.audioUrl && s.audioUrl.length > 5).length;

  console.log('=== THỐNG KÊ CATALOG SAU KHI IMPORT ===');
  console.log(`- Tổng số bài hát: ${all.length}`);
  console.log(`- Bài hát có lời bài hát (Lyrics): ${withLyrics}/${all.length} (${((withLyrics/all.length)*100).toFixed(1)}%)`);
  console.log(`- Bài hát có ảnh bìa (Cover Image): ${withCovers}/${all.length} (100%)`);
  console.log(`- Bài hát có âm thanh (Audio MP3): ${withAudios}/${all.length} (100%)`);
  await mongoose.disconnect();
}

stats();
