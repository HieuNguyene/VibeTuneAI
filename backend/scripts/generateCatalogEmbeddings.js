/**
 * Worker tự động làm giàu Tags và sinh Vector Embedding (768 chiều) cho toàn bộ kho nhạc
 * Chạy lệnh: node scripts/generateCatalogEmbeddings.js
 */
const mongoose = require('mongoose');
require('dotenv').config();

const { Song, Artist, Genre } = require('../src/models');
const LastFmService = require('../src/services/lastFmService');
const EmbeddingService = require('../src/services/embeddingService');

const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/music_app';

async function run() {
  console.log('===============================================================');
  console.log('🚀 BẮT ĐẦU INGESTION & VECTOR GENERATOR WORKER (ZERO-PYTHON)');
  console.log('===============================================================');
  console.log(`📡 Đang kết nối tới MongoDB: ${MONGODB_URI}...`);

  await mongoose.connect(MONGODB_URI);
  console.log('✅ Đã kết nối CSDL thành công!');

  const songs = await Song.find({})
    .populate('artistId', 'name')
    .populate('genreIds', 'name')
    .sort({ createdAt: 1 });

  console.log(`🎵 Tổng số bài hát cần xử lý: ${songs.length} bài.\n`);

  let successCount = 0;
  let skipCount = 0;
  let errorCount = 0;

  for (let i = 0; i < songs.length; i++) {
    const song = songs[i];
    const artistName = song.artistId?.name || 'Nghệ sĩ';
    const genreNames = (song.genreIds || []).map(g => g.name || String(g));

    console.log(`[${i + 1}/${songs.length}] 🎧 Đang xử lý: "${song.title}" - ${artistName}...`);

    try {
      // 1. Enrich Tags từ Last.fm / Tag Rules
      const enrichedTags = await LastFmService.enrichTags({
        title: song.title,
        artist: artistName,
        genres: genreNames,
        lyrics: song.lyrics || ''
      });

      // 2. Tạo chuỗi văn bản ngữ nghĩa Semantic Text
      const semanticText = EmbeddingService.generateSongSemanticText(
        song,
        artistName,
        genreNames,
        enrichedTags
      );

      // 3. Sinh Vector Embedding 768 chiều từ Gemini
      const embedding = await EmbeddingService.generateEmbedding(semanticText);

      // 4. Cập nhật bài hát trong MongoDB
      song.tags = enrichedTags;
      song.semanticText = semanticText;
      song.embedding = embedding;
      await song.save();

      successCount++;
      console.log(`   ✨ Thành công! Đã tạo vector 768 chiều. Tags: [${enrichedTags.slice(0, 4).join(', ')}]`);

      // Tạm dừng 400ms để tránh rate limit của Gemini API
      await new Promise(r => setTimeout(r, 400));
    } catch (err) {
      errorCount++;
      console.error(`   ❌ Lỗi khi xử lý bài "${song.title}":`, err.message);
      // Tạm dừng 1.5s nếu gặp lỗi mạng
      await new Promise(r => setTimeout(r, 1500));
    }
  }

  console.log('\n===============================================================');
  console.log('🎉 HOÀN THÀNH INGESTION & VECTOR GENERATION!');
  console.log(`   - Tổng số bài: ${songs.length}`);
  console.log(`   - Thành công: ${successCount}`);
  console.log(`   - Lỗi: ${errorCount}`);
  console.log('===============================================================');

  await mongoose.disconnect();
  console.log('👋 Đã ngắt kết nối CSDL.');
}

run().catch(err => {
  console.error('Fatal Error:', err);
  process.exit(1);
});
