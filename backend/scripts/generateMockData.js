/**
 * generateMockData.js
 * Script tạo dữ liệu giả thực tế & chuyên nghiệp cho hệ thống Vibe Music:
 * 1. Chuẩn hóa & làm sạch Tên bài hát + Tên nghệ sĩ (xóa rác từ YouTube).
 * 2. Cập nhật hồ sơ Nghệ sĩ (Tiểu sử phong phú, Avatar chất lượng, Số người theo dõi thực tế, Tích xanh).
 * 3. Tạo số lượt nghe (playsCount) và lượt thích (likesCount) sống động theo xu hướng BXH V-Pop/Quốc tế.
 * 4. Tạo Playlist thịnh hành (Top 50 Thịnh Hành, Rap Việt, Acoustic Chill, Lofi Đêm Khuya...) chứa các bài hát thật.
 * 5. Tạo dữ liệu lịch sử nghe nhạc và danh sách yêu thích cho người dùng thử nghiệm.
 */

const connectDB = require('../src/config/db');
const mongoose = require('mongoose');
const Song = require('../src/models/Song');
const Artist = require('../src/models/Artist');
const Genre = require('../src/models/Genre');
const Playlist = require('../src/models/Playlist');
const PlaylistTrack = require('../src/models/PlaylistTrack');
const ListeningHistory = require('../src/models/ListeningHistory');
const UserFavorite = require('../src/models/UserFavorite');
const User = require('../src/models/User');

// Danh sách thông tin tiểu sử & avatar tiêu biểu cho các nghệ sĩ
const ARTIST_METADATA = {
  'Sơn Tùng M-TP': {
    followersCount: 5420000,
    isVerified: true,
    bio: 'Nguyễn Thanh Tùng, nghệ danh Sơn Tùng M-TP, là ca sĩ, nhạc sĩ kiêm nhà sản xuất âm nhạc hàng đầu Việt Nam. Sở hữu hàng loạt siêu hit kỷ lục như Lạc Trôi, Chúng Ta Không Thuộc Về Nhau, Đừng Làm Trái Tim Anh Đau.',
    avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&q=80'
  },
  'HIEUTHUHAI': {
    followersCount: 3100000,
    isVerified: true,
    bio: 'Trần Minh Hiếu, nghệ danh HIEUTHUHAI, thành viên nhóm GERDNANG. Nam rapper tài năng, biểu tượng phong cách thế hệ trẻ với các bản hit Vệ Tinh, NOLOVE NOHIPHOP, Exit Sign.',
    avatarUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80'
  },
  'Vũ.': {
    followersCount: 2150000,
    isVerified: true,
    bio: 'Hoàng Thái Vũ (Vũ.), được mệnh danh là "Hoàng tử Indie" của làng nhạc Việt. Nổi tiếng với chất giọng trầm ấm, ca từ sâu sắc qua các ca khúc Lạ Lùng, Bước Qua Mùa Cô Đơn, Bình Yên.',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&q=80'
  },
  'Bray': {
    followersCount: 1980000,
    isVerified: true,
    bio: 'Vũ Đức Thiện (B Ray), nam rapper kỳ cựu với kỹ thuật flow đỉnh cao, ca từ sắc bén, sở hữu hàng loạt bản hit đình đám như Ex\'s Hate Me, Do For Love, Cao Ốc 20.',
    avatarUrl: '/uploads/covers/VungAnToan_img.jpg'
  },
  'MCK & Wren Evans': {
    followersCount: 2650000,
    isVerified: true,
    bio: 'Hai gương mặt nghệ sĩ trẻ đột phá nhất thế hệ Z Việt Nam, kết hợp phong cách Hip-Hop biến hóa của MCK và chất Pop/R&B thời thượng đậm chất quốc tế của Wren Evans.',
    avatarUrl: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=500&q=80'
  },
  'Jack - J97': {
    followersCount: 3300000,
    isVerified: true,
    bio: 'Trịnh Trần Phương Tuấn (Jack - J97), ca sĩ mang màu sắc âm nhạc giao thoa giữa dân ca truyền thống Nam Bộ và giai điệu EDM/Pop hiện đại.',
    avatarUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=500&q=80'
  },
  'BIGBANG & Taeyang': {
    followersCount: 9800000,
    isVerified: true,
    bio: 'Biểu tượng K-Pop huyền thoại toàn cầu của YG Entertainment. Nhóm nhạc nam tiên phong đưa làn sóng Hallyu bùng nổ khắp năm châu.',
    avatarUrl: 'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=500&q=80'
  },
  'Ed Sheeran & Bruno Mars': {
    followersCount: 15400000,
    isVerified: true,
    bio: 'Những cỗ máy tạo hit số 1 làng nhạc thế giới. Sở hữu vô số giải thưởng Grammy danh giá và các siêu hit đứng đầu bảng xếp hạng Billboard Hot 100.',
    avatarUrl: 'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=500&q=80'
  },
  'Andree Right Hand & Wxrdie': {
    followersCount: 2200000,
    isVerified: true,
    bio: 'Bộ đôi đại diện cho lối sống Trap và Hip-Hop thời thượng, xa hoa, cá tính và lôi cuốn bậc nhất làng Rap Việt Nam.',
    avatarUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80'
  },
  'Hoàng Dũng & Đen Vâu': {
    followersCount: 4200000,
    isVerified: true,
    bio: 'Sự kết hợp hoàn hảo giữa giọng hát truyền cảm, sâu lắng của Hoàng Dũng và những vần thơ Rap mộc mạc, đậm chất đời của Đen Vâu.',
    avatarUrl: 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=500&q=80'
  },
  'GREY D, tlinh': {
    followersCount: 2450000,
    isVerified: true,
    bio: 'GREY D (cựu thành viên MONSTAR) và tlinh là bộ đôi tạo hit GenZ đình đám với chất liệu R&B lãng mạn, bắt tai và giàu cảm xúc.',
    avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&q=80'
  },
  'Madihu, Low G': {
    followersCount: 2100000,
    isVerified: true,
    bio: 'Sự kết hợp giữa chất Indie R&B êm đềm mê đắm của Madihu và chất giọng Rap vô cảm (deadpan flow) cực kỳ lôi cuốn của "phù thủy rap" Low G.',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&q=80'
  },
  'JayKii': {
    followersCount: 1150000,
    isVerified: true,
    bio: 'Trần Anh Quân (JayKii), giọng ca ballad ngọt ngào, cảm xúc sâu lắng được hàng triệu khán giả yêu mến qua bản hit quốc dân "Chiều Hôm Ấy".',
    avatarUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=500&q=80'
  },
  'Lou Hoàng, Miu Lê': {
    followersCount: 1850000,
    isVerified: true,
    bio: 'Hai tên tuổi nổi bật của V-Pop với sự ăn ý tuyệt vời trong các bản song ca lãng mạn, nổi bật nhất là ca khúc kinh điển "Yêu Một Người Có Lẽ".',
    avatarUrl: 'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=500&q=80'
  },
  'Lê Hiếu, Soobin Hoàng Sơn, Touliver': {
    followersCount: 3600000,
    isVerified: true,
    bio: 'Bộ ba nghệ sĩ đỉnh cao quy tụ "hoàng tử tình ca" Lê Hiếu, "hoàng tử R&B" Soobin Hoàng Sơn và "phù thủy âm thanh" Touliver từ SpaceSpeakers.',
    avatarUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80'
  },
  'OSAD, Hà An Huy': {
    followersCount: 1420000,
    isVerified: true,
    bio: 'Sự kết hợp ăn ý giữa phong cách rap năng động của OSAD và giọng ca nội lực, bay bổng của Quán quân Vietnam Idol Hà An Huy.',
    avatarUrl: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=500&q=80'
  },
  'ANH TRAI \'\'SAY HI\'\' 2025': {
    followersCount: 4800000,
    isVerified: true,
    bio: 'Chương trình truyền hình âm nhạc thực tế bùng nổ nhất năm, quy tụ dàn "anh trai" tài năng toàn năng dẫn đầu xu hướng nhạc trẻ Việt Nam.',
    avatarUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80'
  },
  'VSTRA, Tyronee': {
    followersCount: 950000,
    isVerified: true,
    bio: 'VSTRA (Hạnh Ngân) - nữ nghệ sĩ R&B/Indie độc đáo với chất giọng mộng mị, ma mị kết hợp cùng phong cách sản xuất hiện đại của Tyronee.',
    avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&q=80'
  },
  'Emcee L (Da LAB), Badbies': {
    followersCount: 1750000,
    isVerified: true,
    bio: 'Emcee L (thành viên Da LAB) - nhạc sĩ, ca sĩ sở hữu phong cách viết lời sâu sắc, chân thực kết hợp cùng giọng hát cá tính của Badbies.',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&q=80'
  },
  'VƯƠNG BÌNH': {
    followersCount: 880000,
    isVerified: true,
    bio: 'Vương Bình (cựu thành viên nhóm MONSTAR) với chất giọng ấm áp, phong cách âm nhạc lãng mạn, nhẹ nhàng ghi dấu ấn qua ca khúc "Thanh Tân".',
    avatarUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=500&q=80'
  },
  'Vũ Cát Tường': {
    followersCount: 2800000,
    isVerified: true,
    bio: 'Ca sĩ, nhạc sĩ cá tính với tư duy âm nhạc tiên phong, tác giả của hàng loạt bản hit bất hủ như Vết Mưa, Mơ, Yêu Xa, Có Người.',
    avatarUrl: 'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=500&q=80'
  },
  'Cầm': {
    followersCount: 790000,
    isVerified: true,
    bio: 'Giọng ca trẻ tài năng với chất giọng trong trẻo, phong cách Pop hiện đại được yêu mến nồng nhiệt qua "Có Đâu Ai Ngờ".',
    avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&q=80'
  },
  'NHONHO': {
    followersCount: 650000,
    isVerified: true,
    bio: 'Nghệ sĩ trẻ mang phong cách âm nhạc HongKong hoài niệm thập niên 90 kết hợp phối khí lofi êm dịu, ấm áp.',
    avatarUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80'
  },
  'Jaysonlei': {
    followersCount: 620000,
    isVerified: true,
    bio: 'Nhạc sĩ, ca sĩ trẻ tài năng với các giai điệu học đường trong sáng, ca từ thanh xuân đong đầy kỷ niệm.',
    avatarUrl: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=500&q=80'
  },
  'Bim.': {
    followersCount: 580000,
    isVerified: true,
    bio: 'Nghệ sĩ Indie với phong cách âm nhạc chillhop, ca từ gần gũi, ngọt ngào chạm đến trái tim khán giả trẻ.',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&q=80'
  },
  '52Hz': {
    followersCount: 820000,
    isVerified: true,
    bio: 'Nữ ca sĩ, nhạc sĩ đại diện cho âm nhạc chữa lành và những tần số cảm xúc riêng biệt, nổi tiếng với "Đợi", "Mê Cung".',
    avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&q=80'
  },
  'Binz': {
    followersCount: 3900000,
    isVerified: true,
    bio: 'Lê Nguyễn Trung Đan (Binz), "nhà thơ làng Rap" của SpaceSpeakers. Sở hữu giọng rap trầm khàn đặc trưng và phong cách lãng tử cuốn hút.',
    avatarUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80'
  },
  'Obito': {
    followersCount: 1650000,
    isVerified: true,
    bio: 'Lý Quốc Phong (Obito), thành viên OTĐ, nghệ sĩ đa tài tạo nên cơn sốt Simple Love và album Đánh Đổi đầy tính nghệ thuật.',
    avatarUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80'
  }
};

async function main() {
  await connectDB();
  console.log('=== BẮT ĐẦU TẠO DỮ LIỆU GIẢ CHO VIBE MUSIC ===\n');

  // 1. Chuẩn hóa các bài hát có tên/nghệ sĩ bị đảo hoặc có text thừa
  console.log('1. Đang chuẩn hóa thông tin bài hát và nghệ sĩ...');
  
  // Sửa bài Binz ft Châu Bùi
  const songBinz = await Song.findOne({ title: /Binz ft\. Châu Bùi/i });
  if (songBinz) {
    let artistBinz = await Artist.findOne({ name: 'Binz' });
    if (!artistBinz) {
      artistBinz = await Artist.create({
        name: 'Binz',
        slug: 'binz',
        bio: ARTIST_METADATA['Binz'].bio,
        followersCount: ARTIST_METADATA['Binz'].followersCount,
        isVerified: true,
        avatarUrl: ARTIST_METADATA['Binz'].avatarUrl
      });
    }
    songBinz.title = 'Được Không? (ft. Châu Bùi)';
    songBinz.artistId = artistBinz._id;
    await songBinz.save();
    console.log('   ✅ Đã chuẩn hóa: "Được Không?" - Binz');
  }

  // Sửa bài Vũ Cát Tường Mơ remix
  const songMo = await Song.findOne({ title: /Vũ Cát Tường/i, audioUrl: /vu_cat_tuong/i });
  if (songMo) {
    let artistVCT = await Artist.findOne({ name: 'Vũ Cát Tường' });
    if (!artistVCT) {
      artistVCT = await Artist.create({
        name: 'Vũ Cát Tường',
        slug: 'vu-cat-tuong',
        bio: ARTIST_METADATA['Vũ Cát Tường'].bio,
        followersCount: ARTIST_METADATA['Vũ Cát Tường'].followersCount,
        isVerified: true,
        avatarUrl: ARTIST_METADATA['Vũ Cát Tường'].avatarUrl
      });
    }
    songMo.title = 'Mơ (Remix)';
    songMo.artistId = artistVCT._id;
    await songMo.save();
    console.log('   ✅ Đã chuẩn hóa: "Mơ (Remix)" - Vũ Cát Tường');
  }

  // Sửa bài Obito Ngoại lệ của nhau
  const songObito = await Song.findOne({ title: /NGOẠI LỆ CỦA NHAU/i });
  if (songObito) {
    let artistObito = await Artist.findOne({ name: 'Obito' });
    if (!artistObito) {
      artistObito = await Artist.create({
        name: 'Obito',
        slug: 'obito',
        bio: ARTIST_METADATA['Obito'].bio,
        followersCount: ARTIST_METADATA['Obito'].followersCount,
        isVerified: true,
        avatarUrl: ARTIST_METADATA['Obito'].avatarUrl
      });
    }
    songObito.title = 'Ngoại Lệ Của Nhau';
    songObito.artistId = artistObito._id;
    await songObito.save();
    console.log('   ✅ Đã chuẩn hóa: "Ngoại Lệ Của Nhau" - Obito');
  }

  // Sửa bài Prod Zenkey
  const songZenkey = await Song.findOne({ title: /Prod\. Zenkey/i });
  if (songZenkey) {
    songZenkey.title = 'Không Buông (Drill)';
    await songZenkey.save();
    console.log('   ✅ Đã chuẩn hóa: "Không Buông (Drill)"');
  }

  // Sửa bài Giải cứu thế giới
  const songGiaiCuu = await Song.findOne({ title: /Giải cứu thế giới/i });
  if (songGiaiCuu) {
    songGiaiCuu.title = 'Giải Cứu Thế Giới (Nữ Cover)';
    await songGiaiCuu.save();
    console.log('   ✅ Đã chuẩn hóa: "Giải Cứu Thế Giới"');
  }

  // Sửa bài Đợi 52Hz
  const songDoi = await Song.findOne({ title: /ĐỢI/i });
  let artist52 = await Artist.findOne({ name: '52Hz' });
  if (!artist52) {
    artist52 = await Artist.create({
      name: '52Hz',
      slug: '52hz',
      bio: ARTIST_METADATA['52Hz'].bio,
      followersCount: ARTIST_METADATA['52Hz'].followersCount,
      isVerified: true,
      avatarUrl: ARTIST_METADATA['52Hz'].avatarUrl
    });
  }
  if (songDoi) {
    songDoi.title = 'Đợi (Slowed)';
    songDoi.artistId = artist52._id;
    await songDoi.save();
    console.log('   ✅ Đã chuẩn hóa: "Đợi (Slowed)" - 52Hz');
  }

  // Xóa artist thừa duplicate
  await Artist.deleteMany({ name: /52Hz, Trần Thị Phương/i });

  // Chuẩn hóa nghệ sĩ GREY D, tlinh
  const artGreyD = await Artist.findOne({ name: /GREY D/i });
  if (artGreyD) {
    artGreyD.name = 'GREY D, tlinh';
    artGreyD.slug = 'grey-d-tlinh';
    await artGreyD.save();
  }

  // Chuẩn hóa VSTRA, Tyronee
  const artVstra = await Artist.findOne({ name: /VSTRA/i });
  if (artVstra) {
    artVstra.name = 'VSTRA, Tyronee';
    artVstra.slug = 'vstra-tyronee';
    await artVstra.save();
  }

  // Chuẩn hóa RIO
  const artRio = await Artist.findOne({ name: /RIO,/i });
  if (artRio) {
    artRio.name = 'RIO';
    artRio.slug = 'rio';
    await artRio.save();
  }

  // Chuẩn hóa Nicole Nguyễn, Tommy Tèo
  const artNicole = await Artist.findOne({ name: /Nicole Nguyễn/i });
  if (artNicole) {
    artNicole.name = 'Nicole Nguyễn, Tommy Tèo';
    artNicole.slug = 'nicole-nguyen-tommy-teo';
    await artNicole.save();
  }

  // Chuẩn hóa Gia Huy x Young Tachi
  const artGiaHuy = await Artist.findOne({ name: /Thương Nhau Đến Thế/i });
  if (artGiaHuy) {
    artGiaHuy.name = 'Gia Huy x Young Tachi';
    artGiaHuy.slug = 'gia-huy-young-tachi';
    await artGiaHuy.save();
  }
  const songTuongTu = await Song.findOne({ title: /Tương Tư Anh Như Nắm/i });
  if (songTuongTu) {
    songTuongTu.title = 'Tương Tư Anh Như Nắm Tro Tàn';
    await songTuongTu.save();
  }

  // Chuẩn hóa Hngle x Ari
  const artHngle = await Artist.findOne({ name: /Không Buông Drill/i });
  if (artHngle) {
    artHngle.name = 'Hngle x Ari';
    artHngle.slug = 'hngle-x-ari';
    await artHngle.save();
  }

  // Chuẩn hóa Cứu Thế Giới
  const artCuuTheGioi = await Artist.findOne({ name: /Có phải em đã cứu/i });
  if (artCuuTheGioi) {
    artCuuTheGioi.name = 'Nữ Cover (Cứu Thế Giới)';
    artCuuTheGioi.slug = 'nu-cover-cuu-the-gioi';
    await artCuuTheGioi.save();
  }

  // Chuẩn hóa Anh Trai Say Hi
  let artSayHi = await Artist.findOne({ name: /ANH TRAI ''SAY HI'' 2025/i });
  if (artSayHi) {
    artSayHi.name = 'Anh Trai Say Hi';
    artSayHi.slug = 'anh-trai-say-hi';
    await artSayHi.save();
  } else {
    artSayHi = await Artist.findOne({ name: 'Anh Trai Say Hi' });
  }

  // Liên kết Sơn Tùng M-TP với bài "Sớm Muộn Thì x Nơi Này Có Anh"
  const songSomMuon = await Song.findOne({ title: /Sớm Muộn Thì/i });
  const sonTung = await Artist.findOne({ name: 'Sơn Tùng M-TP' });
  if (songSomMuon && sonTung) {
    songSomMuon.artistId = sonTung._id;
    if (artSayHi) {
      songSomMuon.featuredArtistIds = [artSayHi._id];
    }
    await songSomMuon.save();
    console.log('   ✅ Đã liên kết "Sớm Muộn Thì x Nơi Này Có Anh" với Sơn Tùng M-TP');
  }

  // Liên kết B Ray với "CHỜ ANH VỀ"
  const songChoAnhVe = await Song.findOne({ title: /CHỜ ANH VỀ/i });
  const bray = await Artist.findOne({ name: /Bray|B Ray/i });
  if (songChoAnhVe && bray) {
    if (!songChoAnhVe.featuredArtistIds) songChoAnhVe.featuredArtistIds = [];
    if (!songChoAnhVe.featuredArtistIds.includes(bray._id)) {
      songChoAnhVe.featuredArtistIds.push(bray._id);
    }
    await songChoAnhVe.save();
    console.log('   ✅ Đã liên kết "CHỜ ANH VỀ" với B Ray');
  }

  // Xóa artist thừa "Anh Trai Say Hi, Sơn Tùng M-TP"
  await Artist.deleteMany({ name: 'Anh Trai Say Hi, Sơn Tùng M-TP' });

  // 2. Cập nhật hồ sơ tất cả nghệ sĩ trong hệ thống
  console.log('\n2. Đang cập nhật hồ sơ Nghệ sĩ (Tiểu sử, Avatar, Người theo dõi)...');
  const allArtists = await Artist.find();
  const vpopGenre = await Genre.findOne({ slug: 'v-pop' });
  const hiphopGenre = await Genre.findOne({ slug: 'hip-hop-rap' });
  const indieGenre = await Genre.findOne({ slug: 'indie-ballad' });

  for (const art of allArtists) {
    let meta = ARTIST_METADATA[art.name];
    if (!meta) {
      // Tìm key gần đúng
      for (const [key, val] of Object.entries(ARTIST_METADATA)) {
        if (art.name.toLowerCase().includes(key.toLowerCase()) || key.toLowerCase().includes(art.name.toLowerCase())) {
          meta = val;
          break;
        }
      }
    }

    if (meta) {
      art.bio = meta.bio;
      art.followersCount = meta.followersCount;
      art.isVerified = meta.isVerified;
      if (meta.avatarUrl && (!art.avatarUrl || art.avatarUrl.includes('unsplash') || art.avatarUrl.includes('/uploads/'))) {
        art.avatarUrl = meta.avatarUrl;
      }
    } else {
      // Dữ liệu ngẫu nhiên hợp lý cho nghệ sĩ khác
      if (!art.followersCount || art.followersCount === 0) {
        art.followersCount = Math.floor(Math.random() * 450000) + 150000;
      }
      if (!art.bio || art.bio.startsWith('Ca sĩ / Nhóm nhạc')) {
        art.bio = `${art.name} là nghệ sĩ tài năng với nhiều sáng tác độc đáo được khán giả yêu thích và đón nhận trên các nền tảng streaming âm nhạc.`;
      }
      art.isVerified = true;
    }

    // Gán thể loại mặc định nếu chưa có
    if (!art.genreIds || art.genreIds.length === 0) {
      art.genreIds = [vpopGenre ? vpopGenre._id : indieGenre._id];
    }

    await art.save();
  }
  console.log(`   ✅ Đã cập nhật xong ${allArtists.length} nghệ sĩ!`);

  // 3. Tạo dữ liệu lượt nghe (playsCount) và lượt thích (likesCount) cho bảng xếp hạng Top Thịnh Hành
  console.log('\n3. Đang tạo dữ liệu lượt nghe & lượt thích theo thứ hạng Top Thịnh Hành...');
  
  // Danh sách các bài hát được xếp vào Top Thịnh Hành (Thứ hạng từ cao đến thấp)
  const TOP_TRENDING_TITLES = [
    { pattern: /ĐỪNG LÀM TRÁI TIM ANH ĐAU/i, rank: 1, basePlays: 4250000, baseLikes: 310000 },
    { pattern: /Có Em/i, rank: 2, basePlays: 3820000, baseLikes: 275000 },
    { pattern: /vaicaunoicokhiennguoithaydoi/i, rank: 3, basePlays: 3510000, baseLikes: 248000 },
    { pattern: /CHỜ ANH VỀ/i, rank: 4, basePlays: 3180000, baseLikes: 220000 },
    { pattern: /Từng Quen/i, rank: 5, basePlays: 2950000, baseLikes: 195000 },
    { pattern: /Chiều Hôm Ấy/i, rank: 6, basePlays: 2710000, baseLikes: 182000 },
    { pattern: /Yêu Một Người Có Lẽ/i, rank: 7, basePlays: 2540000, baseLikes: 171000 },
    { pattern: /Lạc Trôi/i, rank: 8, basePlays: 2390000, baseLikes: 165000 },
    { pattern: /^Ngày Mai Em Đi$/i, rank: 9, basePlays: 2180000, baseLikes: 154000 },
    { pattern: /Dạo Bước HongKong 1999/i, rank: 10, basePlays: 2020000, baseLikes: 142000 },
    { pattern: /Như Một Người/i, rank: 11, basePlays: 1880000, baseLikes: 129000 },
    { pattern: /Ai Ngoài Anh/i, rank: 12, basePlays: 1750000, baseLikes: 118000 },
    { pattern: /Thanh Tân/i, rank: 13, basePlays: 1620000, baseLikes: 109000 },
    { pattern: /Đợi/i, rank: 14, basePlays: 1510000, baseLikes: 98000 },
    { pattern: /Có Đâu Ai Ngờ/i, rank: 15, basePlays: 1430000, baseLikes: 91000 },
    { pattern: /Ngoại Lệ Của Nhau/i, rank: 16, basePlays: 1350000, baseLikes: 85000 },
    { pattern: /vạn vật như muốn/i, rank: 17, basePlays: 1260000, baseLikes: 79000 },
    { pattern: /Được Không/i, rank: 18, basePlays: 1180000, baseLikes: 74000 },
    { pattern: /Lời Hứa Năm 18/i, rank: 19, basePlays: 1090000, baseLikes: 68000 },
    { pattern: /Mơ/i, rank: 20, basePlays: 1020000, baseLikes: 63000 },
    { pattern: /Exit Sign/i, rank: 21, basePlays: 980000, baseLikes: 59000 },
    { pattern: /Âm Thầm Bên Em/i, rank: 22, basePlays: 940000, baseLikes: 55000 },
    { pattern: /Bình Yên/i, rank: 23, basePlays: 890000, baseLikes: 52000 },
    { pattern: /Cao Ốc 20/i, rank: 24, basePlays: 850000, baseLikes: 49000 },
    { pattern: /Tìm Em/i, rank: 25, basePlays: 810000, baseLikes: 46000 },
    { pattern: /Trốn Tìm/i, rank: 26, basePlays: 780000, baseLikes: 43000 },
    { pattern: /Die With A Smile/i, rank: 27, basePlays: 750000, baseLikes: 41000 },
    { pattern: /Tại Vì Sao/i, rank: 28, basePlays: 710000, baseLikes: 38000 },
    { pattern: /Sóng Gió/i, rank: 29, basePlays: 680000, baseLikes: 36000 },
    { pattern: /Haru Haru/i, rank: 30, basePlays: 650000, baseLikes: 34000 }
  ];

  const allSongs = await Song.find();
  const assignedSongIds = new Set();

  for (const item of TOP_TRENDING_TITLES) {
    const s = allSongs.find(song => !assignedSongIds.has(song._id.toString()) && item.pattern.test(song.title));
    if (s) {
      assignedSongIds.add(s._id.toString());
      s.playsCount = item.basePlays + Math.floor(Math.random() * 25000);
      s.likesCount = item.baseLikes + Math.floor(Math.random() * 2500);
      await s.save();
      console.log(`   🔥 Top #${item.rank}: "${s.title}" (${s.playsCount.toLocaleString()} plays, ${s.likesCount.toLocaleString()} likes)`);
    }
  }

  // Các bài còn lại gán lượt nghe thực tế từ 150k đến 580k
  for (const s of allSongs) {
    if (!assignedSongIds.has(s._id.toString())) {
      s.playsCount = Math.floor(Math.random() * 450000) + 120000;
      s.likesCount = Math.floor(s.playsCount * (Math.random() * 0.05 + 0.04));
      await s.save();
    }
  }
  console.log(`   ✅ Đã cập nhật chỉ số lượt nghe & thích cho toàn bộ ${allSongs.length} bài hát!`);

  // 4. Tạo các Playlist tuyển chọn thịnh hành (Featured Playlists)
  console.log('\n4. Đang tạo các Playlist Tuyển Chọn Thịnh Hành...');
  const adminUser = await User.findOne({ email: 'demo@spotifyclone.com' }) || await User.findOne();

  const curatedPlaylists = [
    {
      title: '🔥 Vibe Top 50 - Bảng Xếp Hạng Thịnh Hành',
      description: 'Cập nhật những ca khúc dẫn đầu bảng xếp hạng âm nhạc Việt Nam và quốc tế hot nhất hiện nay.',
      coverImageUrl: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80',
      filter: (songs) => [...songs].sort((a, b) => b.playsCount - a.playsCount).slice(0, 20)
    },
    {
      title: '🎤 GenZ & Rap Việt Đỉnh Nóc',
      description: 'Những bản rap và flow cháy phố từ HIEUTHUHAI, MCK, Low G, B Ray, tlinh, Andree, Obito...',
      coverImageUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80',
      filter: (songs) => songs.filter(s => /rap|hiphop|drill|say hi|ngoại lệ|có em|chờ anh về|exit sign|vùng an toàn|em iu/i.test(s.title + ' ' + (s.description || ''))).slice(0, 15)
    },
    {
      title: '🌧️ Lofi & Suy Nửa Đêm',
      description: 'Giai điệu chậm rãi, chữa lành tâm trạng những đêm một mình cùng những nỗi niềm chưa đặt tên.',
      coverImageUrl: 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&q=80',
      filter: (songs) => songs.filter(s => /chiều hôm ấy|yêu một người|dạo bước|đợi|buông|tương tư|lời hứa|bình yên|chẳng thể/i.test(s.title)).slice(0, 12)
    },
    {
      title: '☕ Acoustic & Indie Cafe',
      description: 'Thư thái không gian quán quen cùng những thanh âm mộc mạc từ Vũ., Hoàng Dũng, Madihu, Jaysonlei...',
      coverImageUrl: 'https://images.unsplash.com/photo-1442512595331-e89e73853f31?w=600&q=80',
      filter: (songs) => songs.filter(s => /có em|bình yên|trốn tìm|thanh tân|tâm sự|lời hứa|vạn vật/i.test(s.title)).slice(0, 12)
    }
  ];

  for (const p of curatedPlaylists) {
    let playlistDoc = await Playlist.findOne({ title: p.title });
    if (!playlistDoc) {
      playlistDoc = await Playlist.create({
        title: p.title,
        description: p.description,
        coverImageUrl: p.coverImageUrl,
        creatorId: adminUser._id,
        isPublic: true,
        totalTracks: 0
      });
    } else {
      playlistDoc.description = p.description;
      playlistDoc.coverImageUrl = p.coverImageUrl;
      await playlistDoc.save();
    }

    // Xóa tracks cũ và thêm tracks mới
    await PlaylistTrack.deleteMany({ playlistId: playlistDoc._id });
    const selectedSongs = p.filter(allSongs);
    for (let i = 0; i < selectedSongs.length; i++) {
      await PlaylistTrack.create({
        playlistId: playlistDoc._id,
        songId: selectedSongs[i]._id,
        order: i,
        addedAt: new Date(Date.now() - (selectedSongs.length - i) * 60000)
      });
    }
    playlistDoc.totalTracks = selectedSongs.length;
    await playlistDoc.save();
    console.log(`   🎵 Playlist: "${p.title}" (${selectedSongs.length} bài hát)`);
  }

  // 5. Tạo Lịch sử nghe nhạc & Yêu thích giả cho user
  console.log('\n5. Đang tạo Lịch sử nghe nhạc & Yêu thích cho các tài khoản người dùng...');
  const users = await User.find();
  const topRecent = [...allSongs].sort((a, b) => b.playsCount - a.playsCount).slice(0, 8);
  const favSongs = [...allSongs].sort((a, b) => b.likesCount - a.likesCount).slice(0, 12);

  for (const u of users) {
    await ListeningHistory.deleteMany({ userId: u._id });
    await UserFavorite.deleteMany({ userId: u._id });

    for (let i = 0; i < topRecent.length; i++) {
      await ListeningHistory.create({
        userId: u._id,
        songId: topRecent[i]._id,
        listenDuration: topRecent[i].duration || 180,
        completed: true,
        playbackSource: 'home_feed',
        timestamp: new Date(Date.now() - (i + 1) * 3600000)
      });
    }

    for (const fs of favSongs) {
      await UserFavorite.create({
        userId: u._id,
        songId: fs._id,
        createdAt: new Date()
      });
    }
  }
  console.log(`   🎧 Đã tạo lịch sử nghe gần đây và ${favSongs.length} bài yêu thích cho ${users.length} tài khoản người dùng!`);

  console.log('\n==============================================');
  console.log('🎉 TẠO DỮ LIỆU GIẢ HOÀN TẤT THÀNH CÔNG!');
  console.log('==============================================');
  process.exit(0);
}

main().catch(err => {
  console.error('Lỗi khi tạo dữ liệu giả:', err);
  process.exit(1);
});
