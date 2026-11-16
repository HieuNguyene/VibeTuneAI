const mongoose = require('mongoose');
const { Song, Artist } = require('../src/models');
const ComposerService = require('../src/services/composerService');
const env = require('../src/config/environment');

async function enrichAllSongs() {
  const mongoUri = env.mongoUri || 'mongodb://127.0.0.1:27017/music_app';
  console.log(`[enrich-composers] Connecting to MongoDB: ${mongoUri}`);
  await mongoose.connect(mongoUri);

  const songs = await Song.find().populate('artistId');
  console.log(`[enrich-composers] Loaded ${songs.length} songs from database.`);

  let updatedCount = 0;
  for (const song of songs) {
    const artistName = song.artistId?.name || '';
    const detectedComposer = await ComposerService.detectComposer({
      title: song.title,
      artistName: artistName,
      lyrics: song.lyrics,
      audioUrl: song.audioUrl,
      explicitComposer: song.composer
    });

    const composerArtistId = await ComposerService.findOrCreateComposerArtist(
      detectedComposer,
      song.genreIds
    );

    let needsSave = false;
    if (song.composer !== detectedComposer) {
      song.composer = detectedComposer;
      needsSave = true;
    }
    if (composerArtistId && (!song.composerId || song.composerId.toString() !== composerArtistId.toString())) {
      song.composerId = composerArtistId;
      needsSave = true;
    }

    if (needsSave) {
      await song.save();
      updatedCount++;
      console.log(`[OK] "${song.title}" (${artistName}) -> Nhạc sĩ: "${detectedComposer}"`);
    }
  }

  console.log(`\n======================================================`);
  console.log(`   HOÀN TẤT ĐỒNG BỘ THÔNG TIN NHẠC SĨ (COMPOSERS)`);
  console.log(`======================================================`);
  console.log(`Tổng số bài hát: ${songs.length}`);
  console.log(`Số bài hát đã cập nhật thông tin nhạc sĩ: ${updatedCount}`);
  console.log(`======================================================\n`);

  process.exit(0);
}

enrichAllSongs().catch(err => {
  console.error('[enrich-composers] Lỗi:', err);
  process.exit(1);
});
