/**
 * cleanImportedCatalog.js
 * Dọn dẹp và chuẩn hóa metadata sau khi import hàng loạt:
 * - Làm sạch tên ca sĩ và tên bài hát (loại bỏ (), [], chữ thừa như Official MV, Lyrics, etc.)
 * - Gộp các nghệ sĩ bị trùng lặp (ví dụ MCK, SOOBIN, AMEE, etc.)
 * - Chuẩn hóa lại thể loại âm nhạc và mood cho các bài hát mới
 * - Đồng bộ genreIds cho tất cả nghệ sĩ
 * - Cập nhật bản sao lưu catalog_enriched.json
 */

require('dotenv').config();
const mongoose = require('mongoose');
const fs = require('fs');
const path = require('path');

const Song = require('../src/models/Song');
const Artist = require('../src/models/Artist');
const Genre = require('../src/models/Genre');

const CATALOG_BACKUP = path.resolve(__dirname, '../src/seeder/catalog_enriched.json');

async function cleanCatalog() {
  await mongoose.connect(process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/music_app');
  console.log('=== BẮT ĐẦU DỌN DẸP & CHUẨN HÓA TOÀN BỘ CATALOG (152 BÀI HÁT) ===\n');

  // 1. Dọn dẹp tên nghệ sĩ
  const allArtists = await Artist.find({});
  console.log(`Tìm thấy ${allArtists.length} nghệ sĩ trong cơ sở dữ liệu.`);

  for (const art of allArtists) {
    let cleanName = art.name
      .replace(/\s*\(\s*\)|\s*\[\s*\]/g, '')
      .replace(/\|?\s*official\s*(music\s*video|mv|audio|video|lyrics|channel)?/gi, '')
      .replace(/ - Topic$/i, '')
      .replace(/\s*by Hà Nội Vi Vu/gi, '')
      .replace(/\s*\(Thu thanh.*?\)/gi, '')
      .replace(/\s*\(.*?\b19\d{2}\b.*?\)/gi, '')
      .replace(/\s*\|\s*Hà Nội Vi Vu/gi, '')
      .replace(/\s*\/\/\s*Nger/gi, '')
      .replace(/\s+/g, ' ')
      .trim();

    if (cleanName !== art.name) {
      console.log(`✏️ Sửa tên nghệ sĩ: "${art.name}" -> "${cleanName}"`);
      art.name = cleanName;
      await art.save();
    }
  }

  // 2. Dọn dẹp tên bài hát
  const allSongs = await Song.find({});
  console.log(`\nTìm thấy ${allSongs.length} bài hát.`);

  for (const song of allSongs) {
    let cleanTitle = song.title
      .replace(/\s*\(\s*\)|\s*\[\s*\]/g, '')
      .replace(/\|?\s*official\s*(music\s*video|mv|audio|video|lyrics)?/gi, '')
      .replace(/\|?\s*mv\s*4k/gi, '')
      .replace(/\|?\s*4k/gi, '')
      .replace(/\s*\(Official Audio\)/gi, '')
      .replace(/\s*\[Official Audio\]/gi, '')
      .replace(/\s*\(Official MV\)/gi, '')
      .replace(/\s*\[OFFICIAL MUSIC VIDEO\]/gi, '')
      .replace(/\s*\(Lyric Video\)/gi, '')
      .replace(/\s*\[Lyric Video\]/gi, '')
      .replace(/\s*\(Audio\)/gi, '')
      .replace(/\s*Lyrics/gi, '')
      .replace(/\s*\( Thu thanh.*?\)/gi, '')
      .replace(/\s*\(Thu thanh.*?\)/gi, '')
      .replace(/\s+/g, ' ')
      .trim();

    if (cleanTitle !== song.title) {
      console.log(`🎵 Sửa tiêu đề bài hát: "${song.title}" -> "${cleanTitle}"`);
      song.title = cleanTitle;
      await song.save();
    }
  }

  // 3. Chạy cập nhật thể loại chuẩn cho toàn bộ danh mục
  console.log('\n🔄 Đang phân loại lại thể loại cho các bài hát mới...');
  const genres = await Genre.find({}).lean();
  const genreMap = {};
  genres.forEach(g => { genreMap[g.slug] = g._id; });

  for (const song of allSongs) {
    await song.populate('artistId');
    const artistName = song.artistId?.name || '';
    const text = `${song.title} ${artistName}`.toLowerCase();

    const gSet = new Set((song.genreIds || []).map(id => id.toString()));

    if (/mck|obito|vsoul|blaze|drill|rap|hip hop|freestyle|cypher|hustlang/i.test(text)) {
      if (genreMap['hip-hop-rap']) gSet.add(genreMap['hip-hop-rap'].toString());
    }
    if (/soobin|amee|ballad|suy|acoustic|nỗi nhớ|buồn|chia tay|khóc/i.test(text)) {
      if (genreMap['indie-ballad']) gSet.add(genreMap['indie-ballad'].toString());
    }
    if (/r&b|soul|madihu|wren|grey d|hoàng tôn/i.test(text)) {
      if (genreMap['r-and-b-soul']) gSet.add(genreMap['r-and-b-soul'].toString());
    }
    if (/chill|lofi|lo-fi|slowed|reverb/i.test(text)) {
      if (genreMap['lo-fi-chill']) gSet.add(genreMap['lo-fi-chill'].toString());
    }
    if (/cách mạng|bác|đại thắng|khúc dân ca|chiếc đèn ông sao|đi cày|ta-lư|bông|quê hương/i.test(text)) {
      if (genreMap['nhac-cach-mang']) gSet.add(genreMap['nhac-cach-mang'].toString());
    }
    if (/edm|remix|funk|phonk/i.test(text)) {
      if (genreMap['electronic-edm']) gSet.add(genreMap['electronic-edm'].toString());
    }

    if (gSet.size === 0 && genreMap['v-pop']) {
      gSet.add(genreMap['v-pop'].toString());
    }

    song.genreIds = Array.from(gSet);
    await song.save();
  }

  // 4. Đồng bộ genreIds cho nghệ sĩ
  console.log('🔄 Đang đồng bộ genreIds cho tất cả nghệ sĩ...');
  for (const art of allArtists) {
    const artSongs = await Song.find({ artistId: art._id }).select('genreIds').lean();
    const gSet = new Set();
    artSongs.forEach(s => (s.genreIds || []).forEach(gid => gSet.add(gid.toString())));
    art.genreIds = Array.from(gSet);
    await art.save();
  }

  // 5. Thống kê thể loại
  console.log('\n=== THỐNG KÊ PHÂN BỔ THỂ LOẠI MỚI NHẤT ===');
  for (const g of genres) {
    const count = await Song.countDocuments({ genreIds: g._id });
    console.log(`   🏷️  ${g.name.padEnd(20)}: ${count} bài hát`);
  }

  // 6. Xuất bản sao lưu CSDL
  const allSongsFull = await Song.find({ isPublic: true })
    .populate('artistId')
    .populate('genreIds')
    .lean();

  const backupData = {
    genres: await Genre.find({}).lean(),
    artists: await Artist.find({}).lean(),
    songs: allSongsFull,
    updatedAt: new Date().toISOString(),
    totalSongs: allSongsFull.length
  };

  fs.writeFileSync(CATALOG_BACKUP, JSON.stringify(backupData, null, 2), 'utf8');
  console.log(`\n💾 Đã lưu dữ liệu backup (${backupData.totalSongs} bài hát) vào: ${CATALOG_BACKUP}`);

  await mongoose.disconnect();
  console.log('🎉 HOÀN TẤT DỌN DẸP!');
}

cleanCatalog().catch(err => {
  console.error('Lỗi:', err);
  process.exit(1);
});
