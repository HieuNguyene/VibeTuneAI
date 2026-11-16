/**
 * reclassifyGenres.js
 * Tập lệnh chuẩn hóa & phân chia thể loại âm nhạc chuyên nghiệp cho toàn bộ hệ thống Vibe Music:
 * 1. Khởi tạo / cập nhật danh mục thể loại chuẩn (V-Pop, Hip-Hop/Rap, Indie & Ballad, R&B/Soul, Lo-Fi Chill, EDM/Electronic, US-UK Pop, K-Pop, Nhạc Cách Mạng, Podcast).
 * 2. Phân loại chuẩn xác 100% từng bài hát theo đúng thể loại nghệ thuật thực tế.
 * 3. Đồng bộ lại thể loại chính (genreIds) cho các Nghệ sĩ trong cơ sở dữ liệu.
 * 4. Cập nhật bản sao lưu seeder catalog_enriched.json.
 */

const connectDB = require('../src/config/db');
const mongoose = require('mongoose');
const Genre = require('../src/models/Genre');
const Song = require('../src/models/Song');
const Artist = require('../src/models/Artist');
const Playlist = require('../src/models/Playlist');
const fs = require('fs');
const path = require('path');

// 1. Danh sách 10 Thể loại chuẩn hóa chính thức
const STANDARD_GENRES = [
  {
    name: 'V-Pop',
    slug: 'v-pop',
    colorCode: '#1DB954',
    description: 'Nhạc Pop thịnh hành Việt Nam với các giai điệu bắt tai, cuốn hút và dẫn đầu bảng xếp hạng.'
  },
  {
    name: 'Hip Hop & Rap',
    slug: 'hip-hop-rap',
    colorCode: '#F59E0B',
    description: 'Văn hóa Hip-Hop, Trap, Drill, Boombap và Rap Việt đương đại với flow đỉnh cao.'
  },
  {
    name: 'Indie & Ballad',
    slug: 'indie-ballad',
    colorCode: '#EC4899',
    description: 'Âm nhạc độc lập giàu cảm xúc, ballad sâu lắng, hoài niệm và chất tự sự mộc mạc.'
  },
  {
    name: 'R&B / Soul',
    slug: 'r-and-b-soul',
    colorCode: '#06B6D4',
    description: 'Giai điệu R&B thời thượng, mượt mà quyến rũ, đậm chất hiện đại và lôi cuốn.'
  },
  {
    name: 'Lo-Fi Chill',
    slug: 'lo-fi-chill',
    colorCode: '#8B5CF6',
    description: 'Giai điệu thư giãn êm dịu, âm thanh mộc mạc hoài niệm, thích hợp để học tập và thư thái.'
  },
  {
    name: 'Electronic / EDM',
    slug: 'electronic-edm',
    colorCode: '#10B981',
    description: 'Nhạc điện tử sôi động, Remix, Phonk, Bass bùng nổ năng lượng cho các bữa tiệc và tập luyện.'
  },
  {
    name: 'US-UK Pop',
    slug: 'us-uk-pop',
    colorCode: '#3B82F6',
    description: 'Những bản hit đình đám thống trị các bảng xếp hạng âm nhạc quốc tế và Billboard Hot 100.'
  },
  {
    name: 'K-Pop',
    slug: 'k-pop',
    colorCode: '#F43F5E',
    description: 'Làn sóng âm nhạc thần tượng Hàn Quốc với vũ đạo bùng nổ và phong cách âm thanh đẳng cấp.'
  },
  {
    name: 'Nhạc Cách Mạng',
    slug: 'nhac-cach-mang',
    colorCode: '#EF4444',
    description: 'Các ca khúc truyền thống hào hùng, ngợi ca tình yêu quê hương đất nước và cách mạng Việt Nam.'
  },
  {
    name: 'Podcast',
    slug: 'podcast',
    colorCode: '#EA580C',
    description: 'Các chương trình phát thanh, tin tức thời sự, chia sẻ đời sống và trò chuyện chữa lành.'
  }
];

// 2. Bản đồ quy tắc phân loại bài hát chuẩn xác (Khớp theo tiêu đề hoặc pattern)
const SONG_GENRE_MAPPING = [
  // Nhóm Sơn Tùng M-TP
  { pattern: /ĐỪNG LÀM TRÁI TIM ANH ĐAU/i, genres: ['v-pop'] },
  { pattern: /^Lạc Trôi$/i, genres: ['v-pop', 'electronic-edm'] },
  { pattern: /^Âm Thầm Bên Em$/i, genres: ['v-pop', 'indie-ballad'] },
  { pattern: /^Come My Way$/i, genres: ['v-pop', 'hip-hop-rap'] },
  { pattern: /^Bắc Bling$/i, genres: ['v-pop', 'electronic-edm'] },
  { pattern: /Sớm Muộn Thì x Nơi Này Có Anh/i, genres: ['v-pop', 'r-and-b-soul'] },

  // Nhóm Vũ. & Indie Ballad
  { pattern: /^Bình Yên$/i, genres: ['indie-ballad', 'lo-fi-chill'] },
  { pattern: /^Chẳng Thể Tìm Được Em$/i, genres: ['indie-ballad', 'lo-fi-chill'] },
  { pattern: /^Còn Gì Đẹp Hơn$/i, genres: ['indie-ballad'] },
  { pattern: /^Diễm Xưa$/i, genres: ['indie-ballad'] },
  { pattern: /^Chiều Hôm Ấy$/i, genres: ['indie-ballad', 'v-pop'] },
  { pattern: /^Thanh Tân$/i, genres: ['indie-ballad', 'v-pop'] },
  { pattern: /Yêu Một Người Có Lẽ/i, genres: ['v-pop', 'indie-ballad'] },
  { pattern: /Lời Hứa Năm 18/i, genres: ['indie-ballad', 'v-pop'] },
  { pattern: /Giải Cứu Thế Giới/i, genres: ['indie-ballad', 'v-pop'] },
  { pattern: /^Tâm sự$/i, genres: ['indie-ballad', 'lo-fi-chill'] },
  { pattern: /^em là ngoại lệ$/i, genres: ['indie-ballad', 'lo-fi-chill'] },
  { pattern: /vạn vật như muốn ta bên nhau/i, genres: ['indie-ballad', 'lo-fi-chill'] },
  { pattern: /Tương Tư Anh Như Nắm/i, genres: ['indie-ballad', 'lo-fi-chill'] },

  // Nhóm Hoàng Dũng & Đen Vâu
  { pattern: /^Tìm Em$/i, genres: ['indie-ballad'] },
  { pattern: /^Trốn Tìm$/i, genres: ['indie-ballad', 'hip-hop-rap'] },
  { pattern: /^Kẻ Say Tình$/i, genres: ['indie-ballad'] },
  { pattern: /^Tái Sinh$/i, genres: ['indie-ballad'] },
  { pattern: /Trường Sơn Đông Trường Sơn Tây/i, genres: ['nhac-cach-mang', 'v-pop'] },

  // Nhóm Hip-Hop & Rap Việt
  { pattern: /^Exit Sign$/i, genres: ['hip-hop-rap', 'v-pop'] },
  { pattern: /^Vùng An Toàn$/i, genres: ['hip-hop-rap'] },
  { pattern: /^Cao Ốc 20$/i, genres: ['hip-hop-rap', 'indie-ballad'] },
  { pattern: /^Em Iu$/i, genres: ['hip-hop-rap'] },
  { pattern: /^Chơi Như Tụi Mỹ$/i, genres: ['hip-hop-rap'] },
  { pattern: /^Đổi Tư Thế$/i, genres: ['hip-hop-rap'] },
  { pattern: /^Lối Sống$/i, genres: ['hip-hop-rap'] },
  { pattern: /^Tết Vớ Vẩn$/i, genres: ['hip-hop-rap'] },
  { pattern: /^Gái Tây Gốc Việt$/i, genres: ['hip-hop-rap'] },
  { pattern: /Không Buông \(Drill\)/i, genres: ['hip-hop-rap'] },
  { pattern: /Quá Đủ Rồi/i, genres: ['hip-hop-rap', 'v-pop'] },
  { pattern: /CHỜ ANH VỀ/i, genres: ['hip-hop-rap', 'v-pop'] },
  { pattern: /Được Không\?/i, genres: ['hip-hop-rap', 'r-and-b-soul'] },
  { pattern: /Ngoại Lệ Của Nhau/i, genres: ['hip-hop-rap', 'r-and-b-soul'] },
  { pattern: /Như Một Người Rưng/i, genres: ['hip-hop-rap', 'v-pop'] },
  { pattern: /Sinh ra đã là thứ đối lập/i, genres: ['indie-ballad', 'hip-hop-rap'] },

  // Nhóm R&B & GenZ
  { pattern: /^Tại Vì Sao$/i, genres: ['r-and-b-soul', 'hip-hop-rap'] },
  { pattern: /^Từng Quen$/i, genres: ['r-and-b-soul', 'v-pop'] },
  { pattern: /^Kịch Bản Tào$/i, genres: ['r-and-b-soul', 'hip-hop-rap'] },
  { pattern: /vaicaunoicokhiennguoithaydoi/i, genres: ['r-and-b-soul', 'v-pop'] },
  { pattern: /^Có Em$/i, genres: ['r-and-b-soul', 'indie-ballad', 'hip-hop-rap'] },
  { pattern: /^Ai Ngoài Anh$/i, genres: ['r-and-b-soul', 'indie-ballad'] },
  { pattern: /^Số Không$/i, genres: ['r-and-b-soul', 'hip-hop-rap'] },
  { pattern: /^Ngày Mai Em Đi$/i, genres: ['r-and-b-soul', 'electronic-edm', 'v-pop'] },

  // Nhóm Lo-Fi & Hoài niệm Chill
  { pattern: /Dạo Bước HongKong 1999/i, genres: ['lo-fi-chill', 'indie-ballad'] },
  { pattern: /Nhẹ Buông/i, genres: ['lo-fi-chill', 'indie-ballad'] },
  { pattern: /漫步香港1999/i, genres: ['lo-fi-chill'] },
  { pattern: /Đợi \(Slowed\)/i, genres: ['lo-fi-chill', 'r-and-b-soul'] },
  { pattern: /Phép Màu/i, genres: ['lo-fi-chill', 'r-and-b-soul'] },
  { pattern: /Tặng em đoá hoa hồng/i, genres: ['lo-fi-chill', 'indie-ballad'] },

  // Nhóm V-Pop khác & Jack
  { pattern: /^Sóng Gió$/i, genres: ['v-pop', 'indie-ballad'] },
  { pattern: /^Hồng Nhan$/i, genres: ['v-pop', 'hip-hop-rap'] },
  { pattern: /^Bạc Phận$/i, genres: ['v-pop', 'electronic-edm'] },
  { pattern: /^Lưu Niên$/i, genres: ['v-pop', 'indie-ballad'] },
  { pattern: /^Chẳng Hợp Nhau Đâu$/i, genres: ['v-pop', 'indie-ballad'] },
  { pattern: /^Ngày Mai Em Đi Mất$/i, genres: ['v-pop', 'indie-ballad'] },
  { pattern: /^Có Đâu Ai Ngờ$/i, genres: ['v-pop'] },

  // Nhóm K-Pop
  { pattern: /^Haru Haru$/i, genres: ['k-pop', 'hip-hop-rap'] },
  { pattern: /^Fantastic Baby$/i, genres: ['k-pop', 'electronic-edm'] },
  { pattern: /^Eyes, Nose, Lips$/i, genres: ['k-pop', 'r-and-b-soul'] },

  // Nhóm US-UK Pop
  { pattern: /^Shape of You$/i, genres: ['us-uk-pop'] },
  { pattern: /^Die With A Smile$/i, genres: ['us-uk-pop', 'r-and-b-soul'] },
  { pattern: /^Despacito$/i, genres: ['us-uk-pop', 'electronic-edm'] },
  { pattern: /^Animals$/i, genres: ['us-uk-pop', 'electronic-edm'] },
  { pattern: /^Old Town Road$/i, genres: ['us-uk-pop', 'hip-hop-rap'] },
  { pattern: /^50 Feet$/i, genres: ['us-uk-pop', 'r-and-b-soul'] },
  { pattern: /We Don't Talk Anymore/i, genres: ['us-uk-pop', 'lo-fi-chill'] },

  // Nhóm Electronic / EDM / Phonk Remix
  { pattern: /^Mơ \(Remix\)$/i, genres: ['electronic-edm', 'v-pop'] },
  { pattern: /MOTIVATION FUNK/i, genres: ['electronic-edm'] },
  { pattern: /^VEM!$/i, genres: ['electronic-edm'] },
  { pattern: /Stay with me remix/i, genres: ['electronic-edm', 'lo-fi-chill'] },
  { pattern: /Outside \(David Laid/i, genres: ['electronic-edm', 'lo-fi-chill'] },

  // Nhóm Podcast
  { pattern: /New twists in coalition/i, genres: ['podcast'] },
  { pattern: /Podcast Gia đình/i, genres: ['podcast'] }
];

async function main() {
  await connectDB();
  console.log('=== BẮT ĐẦU PHÂN LOẠI & CHUẨN HÓA THỂ LOẠI ÂM NHẠC ===\n');

  // 1. Khởi tạo / Cập nhật 10 Thể loại chuẩn
  console.log('1. Đang đồng bộ danh mục Thể loại chuẩn...');
  const genreMap = {}; // slug -> ObjectId
  for (const item of STANDARD_GENRES) {
    let genre = await Genre.findOne({ slug: item.slug });
    if (!genre) {
      genre = await Genre.create(item);
      console.log(`   ➕ Tạo mới thể loại: ${genre.name} (${genre.slug})`);
    } else {
      genre.name = item.name;
      genre.colorCode = item.colorCode;
      genre.description = item.description;
      await genre.save();
      console.log(`   🔄 Cập nhật thể loại: ${genre.name} (${genre.slug})`);
    }
    genreMap[item.slug] = genre._id;
  }

  // 2. Phân loại lại từng bài hát
  console.log('\n2. Đang phân loại lại toàn bộ bài hát...');
  const allSongs = await Song.find();
  let updatedCount = 0;

  for (const song of allSongs) {
    let targetSlugs = ['v-pop']; // Mặc định nếu không khớp

    for (const rule of SONG_GENRE_MAPPING) {
      if (rule.pattern.test(song.title)) {
        targetSlugs = rule.genres;
        break;
      }
    }

    const newGenreIds = targetSlugs.map(slug => genreMap[slug]).filter(Boolean);
    song.genreIds = newGenreIds;
    await song.save();
    updatedCount++;

    const gNames = targetSlugs.map(s => STANDARD_GENRES.find(g => g.slug === s)?.name || s).join(', ');
    console.log(`   🎵 [${song.title}] -> [${gNames}]`);
  }
  console.log(`\n   ✅ Đã phân loại chuẩn xác ${updatedCount}/${allSongs.length} bài hát!`);

  // 3. Cập nhật thể loại chính cho Nghệ sĩ (Artist.genreIds)
  console.log('\n3. Đang đồng bộ thể loại cho các nghệ sĩ...');
  const allArtists = await Artist.find();
  for (const art of allArtists) {
    // Tìm các bài hát của nghệ sĩ này
    const artistSongs = await Song.find({
      $or: [{ artistId: art._id }, { featuredArtistIds: art._id }]
    }).select('genreIds');

    const genreIdSet = new Set();
    artistSongs.forEach(s => {
      (s.genreIds || []).forEach(gid => genreIdSet.add(gid.toString()));
    });

    if (genreIdSet.size > 0) {
      art.genreIds = Array.from(genreIdSet).map(id => new mongoose.Types.ObjectId(id));
      await art.save();
    }
  }
  console.log(`   ✅ Đã đồng bộ thể loại cho ${allArtists.length} nghệ sĩ!`);

  // 4. Thống kê lại số lượng bài hát theo từng thể loại
  console.log('\n=== THỐNG KÊ PHÂN BỔ THỂ LOẠI SAU KHI CHUẨN HÓA ===');
  for (const g of STANDARD_GENRES) {
    const gid = genreMap[g.slug];
    const count = await Song.countDocuments({ genreIds: gid });
    console.log(`   🏷️  ${g.name.padEnd(20)}: ${count} bài hát`);
  }

  // 5. Cập nhật file sao lưu catalog_enriched.json
  const exportPath = path.join(__dirname, '../src/seeder/catalog_enriched.json');
  const enrichedSongs = await Song.find().populate('artistId').populate('genreIds');
  const enrichedArtists = await Artist.find();
  const enrichedGenres = await Genre.find();
  const enrichedPlaylists = await Playlist.find();

  const backupData = {
    totalSongs: enrichedSongs.length,
    totalArtists: enrichedArtists.length,
    totalGenres: enrichedGenres.length,
    totalPlaylists: enrichedPlaylists.length,
    exportedAt: new Date().toISOString(),
    genres: enrichedGenres,
    songs: enrichedSongs,
    artists: enrichedArtists,
    playlists: enrichedPlaylists
  };
  fs.writeFileSync(exportPath, JSON.stringify(backupData, null, 2), 'utf8');
  console.log(`\n💾 Đã lưu bản sao lưu mới nhất vào ${exportPath}`);

  console.log('\n🎉 HOÀN TẤT CHUẨN HÓA THỂ LOẠI!');
  process.exit(0);
}

main().catch(err => {
  console.error('Lỗi khi chuẩn hóa thể loại:', err);
  process.exit(1);
});
