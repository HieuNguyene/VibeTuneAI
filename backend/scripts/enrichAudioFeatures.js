const mongoose = require('mongoose');
const config = require('../src/config/environment');
const Song = require('../src/models/Song');
const Genre = require('../src/models/Genre');

// Bản đồ đặc tính âm nhạc theo bài hát cụ thể (nếu có)
const SONG_SPECIFIC_FEATURES = {
  'shape of you': { danceability: 0.82, energy: 0.65, valence: 0.93, tempoBpm: 96, acousticness: 0.58, instrumentalness: 0.00, liveness: 0.09, speechiness: 0.08 },
  'animals': { danceability: 0.73, energy: 0.74, valence: 0.63, tempoBpm: 95, acousticness: 0.02, instrumentalness: 0.00, liveness: 0.12, speechiness: 0.06 },
  'lạc trôi': { danceability: 0.67, energy: 0.75, valence: 0.58, tempoBpm: 120, acousticness: 0.18, instrumentalness: 0.01, liveness: 0.11, speechiness: 0.09 },
  'âm thầm bên em': { danceability: 0.52, energy: 0.42, valence: 0.38, tempoBpm: 85, acousticness: 0.72, instrumentalness: 0.00, liveness: 0.10, speechiness: 0.04 },
  'bình yên': { danceability: 0.48, energy: 0.34, valence: 0.32, tempoBpm: 80, acousticness: 0.85, instrumentalness: 0.02, liveness: 0.08, speechiness: 0.04 },
  'còn gì đẹp hơn': { danceability: 0.44, energy: 0.31, valence: 0.28, tempoBpm: 78, acousticness: 0.88, instrumentalness: 0.03, liveness: 0.09, speechiness: 0.03 },
  'chơi như tụi mỹ': { danceability: 0.85, energy: 0.82, valence: 0.72, tempoBpm: 130, acousticness: 0.05, instrumentalness: 0.00, liveness: 0.15, speechiness: 0.42 },
  'em iu': { danceability: 0.81, energy: 0.78, valence: 0.68, tempoBpm: 125, acousticness: 0.08, instrumentalness: 0.00, liveness: 0.14, speechiness: 0.38 },
  'fantastic baby': { danceability: 0.78, energy: 0.92, valence: 0.84, tempoBpm: 130, acousticness: 0.04, instrumentalness: 0.02, liveness: 0.22, speechiness: 0.09 },
  'haru haru': { danceability: 0.62, energy: 0.76, valence: 0.45, tempoBpm: 128, acousticness: 0.22, instrumentalness: 0.00, liveness: 0.16, speechiness: 0.06 },
  'eyes nose lips': { danceability: 0.58, energy: 0.55, valence: 0.35, tempoBpm: 72, acousticness: 0.65, instrumentalness: 0.00, liveness: 0.12, speechiness: 0.04 },
  'despacito': { danceability: 0.89, energy: 0.80, valence: 0.85, tempoBpm: 89, acousticness: 0.20, instrumentalness: 0.00, liveness: 0.10, speechiness: 0.15 },
  'die with a smile': { danceability: 0.53, energy: 0.56, valence: 0.52, tempoBpm: 158, acousticness: 0.41, instrumentalness: 0.00, liveness: 0.11, speechiness: 0.03 },
  'old town road': { danceability: 0.88, energy: 0.62, valence: 0.64, tempoBpm: 136, acousticness: 0.05, instrumentalness: 0.00, liveness: 0.11, speechiness: 0.10 },
  'exit sign': { danceability: 0.78, energy: 0.72, valence: 0.65, tempoBpm: 115, acousticness: 0.12, instrumentalness: 0.00, liveness: 0.12, speechiness: 0.28 },
  'từng quen': { danceability: 0.74, energy: 0.60, valence: 0.62, tempoBpm: 110, acousticness: 0.38, instrumentalness: 0.00, liveness: 0.10, speechiness: 0.12 },
  'tại vì sao': { danceability: 0.76, energy: 0.68, valence: 0.54, tempoBpm: 120, acousticness: 0.15, instrumentalness: 0.01, liveness: 0.11, speechiness: 0.22 },
  'bắc bling': { danceability: 0.75, energy: 0.78, valence: 0.82, tempoBpm: 124, acousticness: 0.15, instrumentalness: 0.00, liveness: 0.14, speechiness: 0.08 },
  'vùng an toàn': { danceability: 0.79, energy: 0.75, valence: 0.60, tempoBpm: 130, acousticness: 0.08, instrumentalness: 0.00, liveness: 0.12, speechiness: 0.35 }
};

// Hàm sinh đặc tính theo Thể loại cho các bài còn lại
function generateFeaturesByGenre(genreNames = [], title = '') {
  const gList = genreNames.map(g => g.toLowerCase());
  
  if (gList.some(g => g.includes('ballad') || g.includes('lo-fi'))) {
    return {
      danceability: 0.45 + (Math.random() * 0.10),
      energy: 0.30 + (Math.random() * 0.15),
      valence: 0.28 + (Math.random() * 0.14),
      tempoBpm: Math.floor(75 + Math.random() * 20),
      acousticness: 0.70 + (Math.random() * 0.18),
      instrumentalness: 0.05 + (Math.random() * 0.15),
      liveness: 0.08 + (Math.random() * 0.06),
      speechiness: 0.03 + (Math.random() * 0.03)
    };
  }

  if (gList.some(g => g.includes('hip hop') || g.includes('rap'))) {
    return {
      danceability: 0.78 + (Math.random() * 0.10),
      energy: 0.72 + (Math.random() * 0.14),
      valence: 0.60 + (Math.random() * 0.15),
      tempoBpm: Math.floor(120 + Math.random() * 25),
      acousticness: 0.05 + (Math.random() * 0.12),
      instrumentalness: 0.00 + (Math.random() * 0.02),
      liveness: 0.10 + (Math.random() * 0.08),
      speechiness: 0.30 + (Math.random() * 0.20)
    };
  }

  if (gList.some(g => g.includes('electronic') || g.includes('edm') || g.includes('k-pop'))) {
    return {
      danceability: 0.74 + (Math.random() * 0.12),
      energy: 0.82 + (Math.random() * 0.14),
      valence: 0.68 + (Math.random() * 0.18),
      tempoBpm: Math.floor(124 + Math.random() * 12),
      acousticness: 0.03 + (Math.random() * 0.08),
      instrumentalness: 0.05 + (Math.random() * 0.20),
      liveness: 0.12 + (Math.random() * 0.15),
      speechiness: 0.06 + (Math.random() * 0.06)
    };
  }

  if (gList.some(g => g.includes('r&b') || g.includes('soul'))) {
    return {
      danceability: 0.65 + (Math.random() * 0.10),
      energy: 0.55 + (Math.random() * 0.12),
      valence: 0.50 + (Math.random() * 0.15),
      tempoBpm: Math.floor(90 + Math.random() * 20),
      acousticness: 0.30 + (Math.random() * 0.18),
      instrumentalness: 0.00 + (Math.random() * 0.04),
      liveness: 0.10 + (Math.random() * 0.06),
      speechiness: 0.07 + (Math.random() * 0.08)
    };
  }

  // Mặc định Pop / V-Pop
  return {
    danceability: 0.68 + (Math.random() * 0.12),
    energy: 0.65 + (Math.random() * 0.15),
    valence: 0.58 + (Math.random() * 0.20),
    tempoBpm: Math.floor(105 + Math.random() * 25),
    acousticness: 0.20 + (Math.random() * 0.18),
    instrumentalness: 0.00 + (Math.random() * 0.02),
    liveness: 0.10 + (Math.random() * 0.06),
    speechiness: 0.04 + (Math.random() * 0.05)
  };
}

// Làm tròn 3 chữ số thập phân
function roundFeature(val) {
  return Math.round(val * 1000) / 1000;
}

async function enrichAudioFeatures() {
  console.log('🔄 Đang kết nối MongoDB...');
  await mongoose.connect(config.mongodbUri);
  console.log('✅ Đã kết nối MongoDB thành công.');

  const songs = await Song.find().populate('genreIds');
  console.log(`🎵 Tìm thấy ${songs.length} bài hát cần chuẩn hóa đặc tính âm thanh...`);

  let updatedCount = 0;
  for (const song of songs) {
    const titleKey = song.title.toLowerCase().trim();
    const genreNames = song.genreIds.map(g => g.name);

    let features;
    if (SONG_SPECIFIC_FEATURES[titleKey]) {
      features = { ...SONG_SPECIFIC_FEATURES[titleKey] };
    } else {
      features = generateFeaturesByGenre(genreNames, song.title);
    }

    // Làm tròn
    const cleanFeatures = {
      danceability: roundFeature(features.danceability),
      energy: roundFeature(features.energy),
      valence: roundFeature(features.valence),
      tempoBpm: Math.round(features.tempoBpm),
      acousticness: roundFeature(features.acousticness),
      instrumentalness: roundFeature(features.instrumentalness),
      liveness: roundFeature(features.liveness),
      speechiness: roundFeature(features.speechiness)
    };

    song.audioFeatures = cleanFeatures;
    await song.save();
    updatedCount++;
    console.log(`[${updatedCount}/${songs.length}] Đã chuẩn hóa: "${song.title}" -> Energy: ${cleanFeatures.energy}, Valence: ${cleanFeatures.valence}, Acousticness: ${cleanFeatures.acousticness}`);
  }

  console.log(`🎉 Hoàn tất chuẩn hóa Audio Features cho toàn bộ ${updatedCount} bài hát!`);
  await mongoose.disconnect();
}

enrichAudioFeatures().catch(err => {
  console.error('❌ Lỗi khi làm giàu Audio Features:', err);
  process.exit(1);
});
