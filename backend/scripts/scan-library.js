const path = require('path');
const fs = require('fs');
const mongoose = require('mongoose');
const config = require('../src/config/environment');
const { Song, Artist, Album, Genre } = require('../src/models');

const AUDIO_DIR = path.join(__dirname, '../public/uploads/audios');
const COVERS_DIR = path.join(__dirname, '../public/uploads/covers');
const REPORT_FILE = path.join(__dirname, '../missing-metadata.json');

async function scanLibrary() {
  console.log('=== BẮT ĐẦU QUÉT THƯ VIỆN NHẠC (GIAI ĐOẠN 1) ===');
  console.log(`Thư mục nhạc: ${AUDIO_DIR}`);

  if (!fs.existsSync(AUDIO_DIR)) {
    console.error(`Thư mục không tồn tại: ${AUDIO_DIR}`);
    process.exit(1);
  }

  await mongoose.connect(config.mongodbUri);
  console.log(`Đã kết nối MongoDB: ${config.mongodbUri}`);

  const mm = await import('music-metadata');

  const files = fs.readdirSync(AUDIO_DIR).filter(file => {
    const ext = path.extname(file).toLowerCase();
    return ['.mp3', '.m4a', '.flac', '.wav', '.ogg'].includes(ext);
  });

  console.log(`Tìm thấy ${files.length} file âm thanh trong thư mục.`);

  const missingMetadata = [];
  let updatedCount = 0;
  let createdCount = 0;

  // Lấy danh sách Genre và Artist hiện có để ánh xạ
  const defaultGenre = await Genre.findOne({ name: /Pop|V-Pop/i }) || await Genre.findOne();
  const defaultArtist = await Artist.findOne({ name: /Sơn Tùng|Various Artists/i }) || await Artist.findOne();

  let i = 0;
  for (const fileName of files) {
    i++;
    console.log(`[${i}/${files.length}] Đang xử lý: ${fileName}`);
    const fullPath = path.join(AUDIO_DIR, fileName);
    const audioUrl = `/uploads/audios/${fileName}`;
    const relativeFilePath = `public/uploads/audios/${fileName}`;

    let metadata = null;
    try {
      metadata = await mm.parseFile(fullPath);
    } catch (err) {
      console.warn(`Lỗi đọc metadata file ${fileName}:`, err.message);
      missingMetadata.push({
        fileName,
        filePath: relativeFilePath,
        reason: `Lỗi đọc ID3: ${err.message}`
      });
      continue;
    }

    const common = metadata.common || {};
    const format = metadata.format || {};

    const rawTitle = common.title ? common.title.trim() : '';
    const rawArtist = common.artist ? common.artist.trim() : '';
    const rawAlbum = common.album ? common.album.trim() : '';
    const rawGenre = Array.isArray(common.genre) ? common.genre.join(', ') : (common.genre || '');
    const rawYear = common.year || (common.date ? parseInt(common.date.substring(0, 4), 10) : null);
    const duration = Math.round(format.duration || 0);

    // Kiểm tra thiếu tag
    const missingFields = [];
    if (!rawTitle) missingFields.push('title');
    if (!rawArtist) missingFields.push('artist');
    if (!rawAlbum) missingFields.push('album');
    if (!rawGenre) missingFields.push('genre');
    if (!rawYear) missingFields.push('year');

    if (missingFields.length > 0) {
      missingMetadata.push({
        fileName,
        filePath: relativeFilePath,
        parsedTitle: rawTitle || null,
        parsedArtist: rawArtist || null,
        missingFields
      });
    }

    // Xử lý ảnh bìa nhúng nếu có
    let coverUrl = '';
    if (common.picture && common.picture.length > 0) {
      const pic = common.picture[0];
      const ext = pic.format.includes('png') ? '.png' : '.jpg';
      const coverFileName = `${path.parse(fileName).name}_cover${ext}`;
      const coverPath = path.join(COVERS_DIR, coverFileName);
      if (!fs.existsSync(coverPath)) {
        if (!fs.existsSync(COVERS_DIR)) fs.mkdirSync(COVERS_DIR, { recursive: true });
        fs.writeFileSync(coverPath, pic.data);
      }
      coverUrl = `/uploads/covers/${coverFileName}`;
    }

    // Tìm bài hát trong DB theo audioUrl hoặc filePath hoặc title tương đồng
    let song = await Song.findOne({
      $or: [
        { audioUrl: audioUrl },
        { filePath: relativeFilePath },
        { filePath: fullPath }
      ]
    });

    if (song) {
      // Cập nhật thông tin file và audio
      song.filePath = relativeFilePath;
      if (duration > 0 && (!song.duration || song.duration === 0)) {
        song.duration = duration;
      }
      if (rawYear && !song.year) {
        song.year = rawYear;
      }
      if (coverUrl && (!song.coverImageUrl || song.coverImageUrl.startsWith('http'))) {
        song.coverImageUrl = coverUrl;
      }

      // Đồng bộ mood sang moods nếu moods rỗng
      if ((!song.moods || song.moods.length === 0) && song.mood && song.mood.length > 0) {
        song.moods = [...song.mood];
      }

      // Đồng bộ audio từ audioFeatures nếu audio rỗng
      if (song.audioFeatures) {
        song.audio = {
          bpm: song.audioFeatures.tempoBpm || 120,
          energy: song.audioFeatures.energy || 0.5,
          valence: song.audioFeatures.valence || 0.5,
          danceability: song.audioFeatures.danceability || 0.5,
          acousticness: song.audioFeatures.acousticness || 0.3
        };
      }

      await song.save();
      updatedCount++;
    } else {
      // Nếu bài hát hoàn toàn mới chưa có trong DB, tạo mới nếu có tối thiểu title/artist
      const titleToUse = rawTitle || path.parse(fileName).name.replace(/_/g, ' ');
      
      // Tìm hoặc tạo Artist
      let artistId = defaultArtist ? defaultArtist._id : null;
      if (rawArtist) {
        let artistDoc = await Artist.findOne({ name: new RegExp('^' + rawArtist.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$', 'i') });
        if (!artistDoc) {
          artistDoc = await Artist.create({
            name: rawArtist,
            bio: `Nghệ sĩ ${rawArtist}`,
            genres: defaultGenre ? [defaultGenre._id] : []
          });
        }
        artistId = artistDoc._id;
      }

      const newSong = await Song.create({
        title: titleToUse,
        artistId,
        genreIds: defaultGenre ? [defaultGenre._id] : [],
        duration: duration || 180,
        audioUrl,
        filePath: relativeFilePath,
        coverImageUrl: coverUrl || '',
        year: rawYear || 2024,
        language: 'vi',
        mood: ['chill'],
        moods: ['chill'],
        activities: ['relax'],
        audio: {
          bpm: 120,
          energy: 0.5,
          valence: 0.5,
          danceability: 0.5,
          acousticness: 0.3
        },
        audioFeatures: {
          tempoBpm: 120,
          energy: 0.5,
          valence: 0.5,
          danceability: 0.5,
          acousticness: 0.3
        },
        tagSource: 'auto'
      });
      createdCount++;
    }
  }

  // Ghi báo cáo missing-metadata.json
  fs.writeFileSync(REPORT_FILE, JSON.stringify(missingMetadata, null, 2), 'utf8');

  console.log('=== KẾT QUẢ QUÉT THƯ VIỆN NHẠC ===');
  console.log(`- Tổng số file quét: ${files.length}`);
  console.log(`- Bài hát đã cập nhật trong DB: ${updatedCount}`);
  console.log(`- Bài hát mới thêm: ${createdCount}`);
  console.log(`- Bài thiếu tag ID3: ${missingMetadata.length} (chi tiết tại: missing-metadata.json)`);

  await mongoose.disconnect();
  console.log('Hoàn thành Giai đoạn 1.');
}

scanLibrary().catch(err => {
  console.error('Lỗi scanLibrary:', err);
  process.exit(1);
});
