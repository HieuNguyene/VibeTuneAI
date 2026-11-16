const mongoose = require('mongoose');
const { Song, Artist, Genre } = require('../src/models');
const env = require('../src/config/environment');

// Hàm tạo slug chuẩn hóa tiếng Việt
function slugify(text) {
  if (!text) return '';
  return text
    .toString()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

// Bảng sửa lỗi hoán đổi tiêu đề và tên nghệ sĩ hoặc chuẩn hóa bài hát
const SONG_METADATA_FIXES = {
  // Lỗi hoán đổi Tên bài hát <-> Tên ca sĩ
  'Noo Phước Thịnh []': { title: 'Cause I Love You', artistName: 'Noo Phước Thịnh' },
  'Bùi Anh Tuấn': { title: 'Nơi Tình Yêu Kết Thúc', artistName: 'Bùi Anh Tuấn' },
  'Tùng Dương, Trọng Tấn': { title: 'Mẹ Yêu Con', artistName: 'Tùng Dương, Trọng Tấn' },
  'Tùng Dương': { title: 'Đường Chúng Ta Đi', artistName: 'Tùng Dương' },
  'Phan Duy Anh': { title: 'Vô Cùng (Vì Anh Thương Em)', artistName: 'Phan Duy Anh' },

  // Dọn dẹp ký tự rác từ Youtube
  'giá như |  ()': { title: 'Giá Như', artistName: 'SOOBIN' },
  'Thanh Xuân ()': { title: 'Thanh Xuân', artistName: 'Da LAB' },
  'Nến Và Hoa ()': { title: 'Nến Và Hoa', artistName: 'Rhymastic' },
  'Tháng Tư Là Lời Nói Dối Của Em ()': { title: 'Tháng Tư Là Lời Nói Dối Của Em', artistName: 'Hà Anh Tuấn' },
  'Đi Đâu Để Thấy Hoa Bay []': { title: 'Đi Đâu Để Thấy Hoa Bay', artistName: 'Hoàng Dũng' },
  'Đập Vỡ Cây Đàn (Hoa Linh Bảo) PBN 82': { title: 'Đập Vỡ Cây Đàn', artistName: 'Quang Lê' },
  'Sương Trắng Miền Quê Ngoại': { title: 'Sương Trắng Miền Quê Ngoại', artistName: 'Quang Lê' },
  'Đắp Mộ Cuộc Tình - PBN 126': { title: 'Đắp Mộ Cuộc Tình', artistName: 'Đan Nguyên, Bằng Kiều, Quang Lê' },
  'Tương Tư Nàng Ca Sĩ': { title: 'Tương Tư Nàng Ca Sĩ', artistName: 'Quang Lê' },
  'Quê Nhà': { title: 'Quê Nhà', artistName: 'Tùng Dương' },
  'Hà Nội niềm tin và hy vọng': { title: 'Hà Nội Niềm Tin Và Hy Vọng', artistName: 'Vũ Thắng Lợi' },
  'Mái Đình Làng Biển': { title: 'Mái Đình Làng Biển', artistName: 'Ngọc Hạ' },
  'Gió đánh đò đưa': { title: 'Gió Đánh Đò Đưa', artistName: 'Tạ Quang Thắng, Hồng Duyên' },
  'CHIẾC KHĂN PIÊU': { title: 'Chiếc Khăn Piêu', artistName: 'Tùng Dương, SOOBIN' },
  'ÍT NHƯNG DÀI LÂU': { title: 'Ít Nhưng Dài Lâu', artistName: 'Chu Thúy Quỳnh' },
  'ĐÂU CHỈ RIÊNG EM': { title: 'Đâu Chỉ Riêng Em', artistName: 'Mỹ Tâm' },
  'Như Một Giấc Mơ (LIKE A DREAM) M/V': { title: 'Như Một Giấc Mơ', artistName: 'Mỹ Tâm' },
  'ĐÚNG CŨNG THÀNH SAI': { title: 'Đúng Cũng Thành Sai', artistName: 'Mỹ Tâm' },
  'Tìm Lại Bầu Trời': { title: 'Tìm Lại Bầu Trời', artistName: 'Tuấn Hưng' },
  'Kén Cá Chọn Canh': { title: 'Kén Cá Chọn Canh', artistName: 'Hòa Minzy, Tuấn Cry, Masew' },
  'Gói Con Tim Làm Quà (Bò Sữa Bay OST)': { title: 'Gói Con Tim Làm Quà', artistName: 'Mounter, Nguyễn Hùng' },
  'Waiting For Love': { title: 'Waiting For Love', artistName: 'Avicii' },
  'Attention []': { title: 'Attention', artistName: 'Charlie Puth' },
  'Bones ()': { title: 'Bones', artistName: 'Imagine Dragons' },
  'Blinding Lights ()': { title: 'Blinding Lights', artistName: 'The Weeknd' },
  'Dark Horse ft. Juicy J': { title: 'Dark Horse', artistName: 'Katy Perry' },
  'Hẹn Em Ở Lần Yêu Thứ 2': { title: 'Hẹn Em Ở Lần Yêu Thứ 2', artistName: 'Nguyenn, Đặng Tuấn Vũ' },
  'BUỒN HAY VUI': { title: 'Buồn Hay Vui', artistName: 'VSOUL, MCK, Obito, Ronboogz, Boyzed' },
  'MỘNG YU': { title: 'Mộng Yu', artistName: 'AMEE, MCK' },
  'nghiem tong prod. gaz': { title: 'Nghiêm Tông (prod. Gaz)', artistName: 'Tháp Drill Tự Do' },
  'Nếu Như Ta Chẳng Còn': { title: 'Nếu Như Ta Chẳng Còn', artistName: 'MCK, A$AP Ướt Mi' },
  'ĐỪNG LÀM TRÁI TIM ANH ĐAU': { title: 'Đừng Làm Trái Tim Anh Đau', artistName: 'Sơn Tùng M-TP' },
  'em là ngoại lệ': { title: 'Em Là Ngoại Lệ', artistName: 'Bim.' },
  'Tâm sự': { title: 'Tâm Sự', artistName: 'Solmee, Dyteller, hoangkiet' },
  'vạn vật như muốn ta bên nhau': { title: 'Vạn Vật Như Muốn Ta Bên Nhau', artistName: 'RIO' },
  'Được Không? (ft. Châu Bùi)': { title: 'Được Không?', artistName: 'Binz' },
  'CHỜ ANH VỀ (Feat. ANH TRAI "SAY HI", B Ray, AMEE)': { title: 'Chờ Anh Về', artistName: 'Anh Trai Say Hi' },
  'Mơ (Remix)': { title: 'Mơ (Remix)', artistName: 'Vũ Cát Tường' },
  '2 5 | Live at HỘI - KHAI HỘI': { title: '2 5', artistName: 'Táo' },
  'Chào Em Cô Gái Lam Hồng': { title: 'Chào Em Cô Gái Lam Hồng', artistName: 'Kiều Hưng' },
  'Bật Tình Yêu Lên': { title: 'Bật Tình Yêu Lên', artistName: 'Hòa Minzy, Tăng Duy Tân' },
  'Em Đồng Ý (I Do) (feat. 911 & Khắc Hưng) () | Valentine 2023': { title: 'Em Đồng Ý (I Do)', artistName: 'Đức Phúc, 911' },
  'Cay ft. Jimmi Nguyen': { title: 'Cay', artistName: 'Khắc Hưng' },
  'Kẻ Say Tình 2': { title: 'Kẻ Say Tình 2', artistName: 'Quốc Thiên' },
  'Hoa Hải Đường': { title: 'Hoa Hải Đường', artistName: 'Jack - J97' },
  'J97 | Cuối Cùng Thì | Special Stage Video': { title: 'Cuối Cùng Thì', artistName: 'Jack - J97' },
  'Hỏi Vợ Ngoại Thành': { title: 'Hỏi Vợ Ngoại Thành', artistName: 'H2K, KProx' },
  'Vó Ngựa Trên Đồi Cỏ Non': { title: 'Vó Ngựa Trên Đồi Cỏ Non', artistName: 'H2K, KProx' },
  'Sầu Tím Hiệp Hồng': { title: 'Sầu Tím Thiệp Hồng', artistName: 'H2K, KProx' },
  'Gõ Cửa Trái Tim': { title: 'Gõ Cửa Trái Tim', artistName: 'H2K, KProx' },
  'Chuyện Hoa Sim': { title: 'Chuyện Hoa Sim', artistName: 'H2K, KProx' },
  'Chim Trắng Mồ Côi  x Phạm Thiêng Ngân ~ Thương nghe câu mái đẩy nhớ điệu lý tình tang...': { title: 'Chim Trắng Mồ Côi', artistName: 'Phạm Thiêng Ngân' },
  'Podcast Gia đình | CƠM NGOÀI hay CƠM NHÀ - Cái Nào Hạnh Phúc? | EP03': { title: 'Cơm Ngoài Hay Cơm Nhà - Cái Nào Hạnh Phúc?', artistName: 'Nhã Lam' }
};

// Từ điển thông tin nghệ sĩ chi tiết (Chân dung sắc nét, tiểu sử sâu sắc, người theo dõi chuẩn)
const ARTIST_METADATA_DICTIONARY = {
  'Mỹ Tâm': {
    slug: 'my-tam',
    avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&q=80',
    bio: 'Phan Thị Mỹ Tâm (Mỹ Tâm - Họa mi tóc nâu), nữ ca sĩ quyền lực số 1 làng nhạc Việt Nam với sự nghiệp hơn 20 năm rực rỡ, sở hữu hàng trăm bản hit vượt thời gian như Ước Gì, Đâu Chỉ Riêng Em, Người Hãy Quên Em Đi.',
    followersCount: 6850000,
    isVerified: true,
    genreSlugs: ['v-pop', 'indie-ballad']
  },
  'Tuấn Hưng': {
    slug: 'tuan-hung',
    avatarUrl: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=500&q=80',
    bio: 'Nguyễn Tuấn Hưng, nam ca sĩ với chất giọng khàn nam tính đặc trưng đầy nội lực, biểu tượng Pop/Ballad Việt với các siêu hit Tìm Lại Bầu Trời, Nắm Lấy Tay Anh, Cầu Vồng Khuyết.',
    followersCount: 4520000,
    isVerified: true,
    genreSlugs: ['v-pop', 'indie-ballad']
  },
  'Quang Lê': {
    slug: 'quang-le',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&q=80',
    bio: 'Quang Lê, giọng ca vàng của dòng nhạc quê hương và Bolero trữ tình hải ngoại. Sở hữu chất giọng ngọt ngào, ấm áp đi vào lòng người qua Đập Vỡ Cây Đàn, Sương Trắng Miền Quê Ngoại, Tương Tư Nàng Ca Sĩ.',
    followersCount: 3150000,
    isVerified: true,
    genreSlugs: ['indie-ballad']
  },
  'Noo Phước Thịnh': {
    slug: 'noo-phuoc-thinh',
    avatarUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=500&q=80',
    bio: 'Nguyễn Phước Thịnh (Noo Phước Thịnh), nam ca sĩ Pop/Dance hàng đầu Việt Nam sở hữu ngoại hình điển trai, kỹ năng trình diễn cuốn hút cùng hàng loạt hit đình đám như Cause I Love You, Chạm Khẽ Tim Anh Một Chút Thôi.',
    followersCount: 3950000,
    isVerified: true,
    genreSlugs: ['v-pop', 'electronic-edm']
  },
  'Bùi Anh Tuấn': {
    slug: 'bui-anh-tuan',
    avatarUrl: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=500&q=80',
    bio: 'Bùi Anh Tuấn, "Hoàng tử ballad" với âm vực cao vút, đầy cảm xúc được khán giả cả nước yêu mến qua The Voice Việt Nam với các ca khúc Nơi Tình Yêu Bắt Đầu, Nơi Tình Yêu Kết Thúc, Hẹn Một Mai.',
    followersCount: 2780000,
    isVerified: true,
    genreSlugs: ['indie-ballad', 'v-pop']
  },
  'Tùng Dương': {
    slug: 'tung-duong',
    avatarUrl: 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=500&q=80',
    bio: 'Tùng Dương, Divo quái kiệt của nền âm nhạc đương đại Việt Nam, sở hữu kỹ thuật thanh nhạc thượng thừa, phong cách biểu diễn ma mị và lập kỷ lục nhận nhiều giải thưởng Cống Hiến nhất.',
    followersCount: 1890000,
    isVerified: true,
    genreSlugs: ['nhac-cach-mang', 'indie-ballad']
  },
  'Tùng Dương, Trọng Tấn': {
    slug: 'tung-duong-trong-tan',
    avatarUrl: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=500&q=80',
    bio: 'Sự hòa quyện tuyệt đỉnh giữa hai giọng ca nam hàng đầu Việt Nam: chất thính phòng hào sảng của Trọng Tấn và kỹ thuật đương đại đầy cảm xúc của Tùng Dương.',
    followersCount: 1540000,
    isVerified: true,
    genreSlugs: ['nhac-cach-mang', 'indie-ballad']
  },
  'Tùng Dương, SOOBIN': {
    slug: 'tung-duong-soobin',
    avatarUrl: 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500&q=80',
    bio: 'Màn kết hợp bùng nổ giữa Divo Tùng Dương và ngôi sao R&B SOOBIN trong nhạc phẩm Chiếc Khăn Piêu, kết hợp âm hưởng Tây Bắc hào hùng với nhịp điệu hiện đại.',
    followersCount: 2650000,
    isVerified: true,
    genreSlugs: ['nhac-cach-mang', 'v-pop']
  },
  'SOOBIN': {
    slug: 'soobin',
    avatarUrl: 'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=500&q=80',
    bio: 'Nguyễn Hoàng Sơn (SOOBIN), nghệ sĩ đa tài hàng đầu SpaceSpeakers với phong cách R&B mượt mà, kỹ năng trình diễn đỉnh cao qua Phía Sau Một Cô Gái, Giá Như, Daydreams.',
    followersCount: 3580000,
    isVerified: true,
    genreSlugs: ['v-pop', 'r-and-b-soul']
  },
  'Hà Anh Tuấn': {
    slug: 'ha-anh-tuan',
    avatarUrl: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=500&q=80',
    bio: 'Hà Anh Tuấn, nam ca sĩ biểu tượng của sự lịch lãm và âm nhạc tự sự truyền cảm hứng, chủ nhân của chuỗi dự án âm nhạc đình đám See Sing Share và các live concert cháy vé.',
    followersCount: 4250000,
    isVerified: true,
    genreSlugs: ['indie-ballad', 'v-pop']
  },
  'Phan Mạnh Quỳnh': {
    slug: 'phan-manh-quynh',
    avatarUrl: 'https://images.unsplash.com/photo-1501196354995-cbb51c65aaea?w=500&q=80',
    bio: 'Phan Mạnh Quỳnh, nhạc sĩ kiêm ca sĩ tài hoa với phong cách sáng tác độc đáo, ca từ đậm chất văn học và triết lý nhân sinh qua Vợ Người Ta, Có Chàng Trai Viết Lên Cây, Sau Lời Từ Khước.',
    followersCount: 3820000,
    isVerified: true,
    genreSlugs: ['indie-ballad', 'v-pop']
  },
  'Da LAB': {
    slug: 'da-lab',
    avatarUrl: 'https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=500&q=80',
    bio: 'Nhóm nhạc Underground/Indie được yêu mến hàng đầu Việt Nam với âm nhạc tích cực, đời thường và giàu năng lượng qua Một Nhà, Thanh Xuân, Gác Lại Âu Lo.',
    followersCount: 2350000,
    isVerified: true,
    genreSlugs: ['hip-hop-rap', 'v-pop']
  },
  'Rhymastic': {
    slug: 'rhymastic',
    avatarUrl: 'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=500&q=80',
    bio: 'Vũ Đức Thiện (Rhymastic), nhà sản xuất âm nhạc kiêm rapper chủ chốt của SpaceSpeakers, "phù thủy âm nhạc" với tư duy hòa âm phối khí thượng thừa qua Yêu 5, Nến Và Hoa.',
    followersCount: 2540000,
    isVerified: true,
    genreSlugs: ['hip-hop-rap', 'electronic-edm']
  },
  'Phan Duy Anh': {
    slug: 'phan-duy-anh',
    avatarUrl: 'https://images.unsplash.com/photo-1480429370139-e0132c086e2a?w=500&q=80',
    bio: 'Phan Duy Anh, nam ca sĩ sở hữu chất giọng nội lực, trầm ấm chạm đáy cảm xúc với các bản hit ballad triệu view Vô Cùng (Vì Anh Thương Em), Từng Yêu.',
    followersCount: 1250000,
    isVerified: true,
    genreSlugs: ['indie-ballad', 'v-pop']
  },
  'Hòa Minzy, Tuấn Cry, Masew': {
    slug: 'hoa-minzy-tuan-cry-masew',
    avatarUrl: 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=500&q=80',
    bio: 'Sự kết hợp bùng nổ giữa giọng ca nội lực Hòa Minzy, flow hài hước của Tuấn Cry và chất phối EDM dân gian đương đại của phù thủy Masew trong Kén Cá Chọn Canh.',
    followersCount: 4120000,
    isVerified: true,
    genreSlugs: ['v-pop', 'electronic-edm']
  },
  'Hòa Minzy, Tăng Duy Tân': {
    slug: 'hoa-minzy-tang-duy-tan',
    avatarUrl: 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=500&q=80',
    bio: 'Sự kết hợp ăn ý bùng nổ năng lượng ngọt ngào giữa giọng ca Hòa Minzy và hit-maker Tăng Duy Tân trong ca khúc Bật Tình Yêu Lên.',
    followersCount: 3800000,
    isVerified: true,
    genreSlugs: ['v-pop']
  },
  'Ngọc Hạ': {
    slug: 'ngoc-ha',
    avatarUrl: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=500&q=80',
    bio: 'Ngọc Hạ, nữ ca sĩ với chất giọng soprano thanh thoát, trong trẻo chuyên thể hiện các nhạc phẩm quê hương, tiền chiến và dân gian đương đại như Mái Đình Làng Biển, Không Thể Và Có Thể.',
    followersCount: 780000,
    isVerified: true,
    genreSlugs: ['indie-ballad']
  },
  'Vũ Thắng Lợi': {
    slug: 'vu-thang-loi',
    avatarUrl: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=500&q=80',
    bio: 'Vũ Thắng Lợi, ca sĩ thính phòng xuất sắc của dòng nhạc Cách mạng và trữ tình quê hương, giải Nhì Sao Mai 2011 với chất giọng hào sảng, truyền cảm.',
    followersCount: 690000,
    isVerified: true,
    genreSlugs: ['nhac-cach-mang']
  },
  'Kiều Hưng': {
    slug: 'kieu-hung',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&q=80',
    bio: 'Nghệ sĩ Nhân dân Kiều Hưng, một trong những giọng ca nam cao trữ tình hàng đầu của nền thanh nhạc Cách mạng Việt Nam với những ca khúc đi cùng năm tháng như Chào Em Cô Gái Lam Hồng.',
    followersCount: 950000,
    isVerified: true,
    genreSlugs: ['nhac-cach-mang']
  },
  'Chu Thúy Quỳnh': {
    slug: 'chu-thuy-quynh',
    avatarUrl: 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500&q=80',
    bio: 'Chu Thúy Quỳnh, nữ ca sĩ với chất giọng trầm khàn đặc biệt, gây sốt mạng xã hội qua hàng loạt bản live acoustic và hit Ít Nhưng Dài Lâu, Xem Như Em Chẳng May.',
    followersCount: 1420000,
    isVerified: true,
    genreSlugs: ['indie-ballad', 'v-pop']
  },
  'Đan Nguyên, Bằng Kiều, Quang Lê': {
    slug: 'dan-nguyen-bang-kieu-quang-le',
    avatarUrl: 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=500&q=80',
    bio: 'Bộ ba danh ca Bolero và nhạc trữ tình hải ngoại hàng đầu, hòa giọng trong siêu phẩm Đắp Mộ Cuộc Tình đi vào lòng người.',
    followersCount: 3890000,
    isVerified: true,
    genreSlugs: ['indie-ballad']
  },
  'Tạ Quang Thắng, Hồng Duyên': {
    slug: 'ta-quang-thang-hong-duyen',
    avatarUrl: 'https://images.unsplash.com/photo-1463453091185-61582044d556?w=500&q=80',
    bio: 'Tạ Quang Thắng và Hồng Duyên kết hợp mộc mạc mang âm hưởng dân ca đồng bằng Bắc Bộ qua tác phẩm Gió Đánh Đò Đưa.',
    followersCount: 820000,
    isVerified: true,
    genreSlugs: ['indie-ballad']
  },
  'Hoàng Dũng': {
    slug: 'hoang-dung',
    avatarUrl: 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=500&q=80',
    bio: 'Hoàng Dũng, giọng ca trữ tình ấm áp của làng nhạc Việt, tác giả của các bản hit Nàng Thơ, Đi Đâu Để Thấy Hoa Bay, Đôi Lời Tình Ca.',
    followersCount: 2450000,
    isVerified: true,
    genreSlugs: ['indie-ballad', 'v-pop']
  },
  'Jack - J97': {
    slug: 'jack-j97',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&q=80',
    bio: 'Trịnh Trần Phương Tuấn (Jack - J97), nam ca sĩ kiêm nhạc sĩ sở hữu phong cách âm nhạc ngũ cung kết hợp Pop hiện đại cùng hàng loạt hit trăm triệu view như Sóng Gió, Bạc Phận, Hồng Nhan, Hoa Hải Đường, Cuối Cùng Thì.',
    followersCount: 5200000,
    isVerified: true,
    genreSlugs: ['v-pop']
  },
  'Quốc Thiên': {
    slug: 'quoc-thien',
    avatarUrl: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=500&q=80',
    bio: 'Trần Quốc Thiên, Quán quân Vietnam Idol mùa 2, sở hữu giọng hát trữ tình điêu luyện, cảm xúc chạm tới trái tim người nghe qua Kẻ Say Tình, Chia Cách Bình Yên.',
    followersCount: 1650000,
    isVerified: true,
    genreSlugs: ['indie-ballad', 'v-pop']
  },
  'Đức Phúc, 911': {
    slug: 'duc-phuc-911',
    avatarUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=500&q=80',
    bio: 'Màn kết hợp quốc tế bất ngờ và lãng mạn giữa Quán quân The Voice Đức Phúc và boyband huyền thoại thế giới 911 trong ca khúc Em Đồng Ý (I Do).',
    followersCount: 3200000,
    isVerified: true,
    genreSlugs: ['v-pop', 'us-uk-pop']
  },
  'H2K, KProx': {
    slug: 'h2k-kprox',
    avatarUrl: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=500&q=80',
    bio: 'Cặp đôi producer và ca sĩ chuyên làm mới các làn điệu Bolero quê hương theo phong cách Lofi hiện đại, thư giãn và bắt tai qua chuỗi bài Hỏi Vợ Ngoại Thành, Sầu Tím Thiệp Hồng, Gõ Cửa Trái Tim.',
    followersCount: 1100000,
    isVerified: true,
    genreSlugs: ['lo-fi-chill', 'indie-ballad']
  },
  'Phạm Thiêng Ngân': {
    slug: 'pham-thieng-ngan',
    avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&q=80',
    bio: 'Phạm Thiêng Ngân, giọng ca dân ca quê hương ngọt ngào, mộc mạc và sâu lắng qua Chim Trắng Mồ Côi.',
    followersCount: 650000,
    isVerified: true,
    genreSlugs: ['indie-ballad']
  },
  'Táo': {
    slug: 'tao',
    avatarUrl: 'https://images.unsplash.com/photo-1501196354995-cbb51c65aaea?w=500&q=80',
    bio: 'Võ Hồ Thanh Tư (Táo), nam rapper kì cựu của rap Việt với phong cách tự sự tăm tối, giàu triết lý và ca từ sâu sắc qua 2 5, Blue Tequila.',
    followersCount: 1450000,
    isVerified: true,
    genreSlugs: ['hip-hop-rap', 'indie-ballad']
  },
  'Khắc Hưng': {
    slug: 'khac-hung',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&q=80',
    bio: 'Khắc Hưng, nhạc sĩ kiêm nhà sản xuất âm nhạc số 1 Việt Nam, chủ nhân của hàng loạt hit quốc dân Sau Tất Cả, Ghen, Người Hãy Quên Em Đi.',
    followersCount: 1980000,
    isVerified: true,
    genreSlugs: ['v-pop']
  },
  'Nhã Lam': {
    slug: 'nha-lam',
    avatarUrl: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=500&q=80',
    bio: 'Nhã Lam Podcast, kênh chia sẻ tâm sự về hôn nhân, cuộc sống gia đình và cảm xúc tích cực cho người trẻ hiện đại.',
    followersCount: 450000,
    isVerified: true,
    genreSlugs: ['podcast']
  },
  'Tháp Drill Tự Do': {
    slug: 'thap-drill-tu-do',
    avatarUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80',
    bio: 'Nhóm nghệ sĩ Drill Underground Việt Nam với phong cách đường phố gai góc và beat trap mạnh mẽ.',
    followersCount: 420000,
    isVerified: false,
    genreSlugs: ['hip-hop-rap']
  },
  'Avicii': {
    slug: 'avicii',
    avatarUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80',
    bio: 'Tim Bergling (Avicii), huyền thoại DJ và nhà sản xuất âm nhạc điện tử thế giới với các siêu phẩm bất hủ Wake Me Up, Waiting For Love, The Nights.',
    followersCount: 24500000,
    isVerified: true,
    genreSlugs: ['electronic-edm', 'us-uk-pop']
  },
  'Charlie Puth': {
    slug: 'charlie-puth',
    avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&q=80',
    bio: 'Charlie Puth, ca sĩ kiêm nhà sản xuất âm nhạc Mỹ sở hữu khả năng nhận biết cao độ tuyệt đối (perfect pitch) và hàng loạt siêu hit toàn cầu như Attention, We Don\'t Talk Anymore.',
    followersCount: 21800000,
    isVerified: true,
    genreSlugs: ['us-uk-pop', 'v-pop']
  },
  'Imagine Dragons': {
    slug: 'imagine-dragons',
    avatarUrl: 'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=500&q=80',
    bio: 'Imagine Dragons, ban nhạc Pop Rock đình đám nước Mỹ, chủ nhân các bản hit bùng nổ năng lượng như Believer, Radioactive, Bones, Demons.',
    followersCount: 27900000,
    isVerified: true,
    genreSlugs: ['us-uk-pop', 'electronic-edm']
  },
  'The Weeknd': {
    slug: 'the-weeknd',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&q=80',
    bio: 'Abel Tesfaye (The Weeknd), siêu sao âm nhạc toàn cầu người Canada với các siêu phẩm Blinding Lights, Starboy, Save Your Tears lập kỷ lục phát trực tuyến.',
    followersCount: 38500000,
    isVerified: true,
    genreSlugs: ['r-and-b-soul', 'us-uk-pop']
  },
  'Katy Perry': {
    slug: 'katy-perry',
    avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&q=80',
    bio: 'Katy Perry, một trong những nữ nghệ sĩ nhạc Pop bán chạy nhất mọi thời đại với chuỗi hit tỉ view Roar, Firework, Dark Horse.',
    followersCount: 32000000,
    isVerified: true,
    genreSlugs: ['us-uk-pop']
  },
  'VSOUL, MCK, Obito, Ronboogz, Boyzed': {
    slug: 'vsoul-mck-obito-ronboogz-boyzed',
    avatarUrl: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=500&q=80',
    bio: 'Màn hội ngộ ấn tượng của dàn rapper trẻ tài năng làng Rap Việt trong bản hit Buồn Hay Vui với giai điệu catchy và flow cuốn hút.',
    followersCount: 3100000,
    isVerified: true,
    genreSlugs: ['hip-hop-rap', 'r-and-b-soul']
  },
  'AMEE, MCK': {
    slug: 'amee-mck',
    avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&q=80',
    bio: 'Sự kết hợp ngọt ngào giữa nàng thơ V-Pop AMEE và nam rapper MCK trong ca khúc Mộng Yu.',
    followersCount: 3450000,
    isVerified: true,
    genreSlugs: ['v-pop', 'hip-hop-rap']
  },
  'Nguyenn, Đặng Tuấn Vũ': {
    slug: 'nguyenn-dang-tuan-vu',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&q=80',
    bio: 'Sản phẩm âm nhạc giàu xúc cảm Hẹn Em Ở Lần Yêu Thứ 2 kết hợp giọng ca trầm lắng của Nguyenn và Đặng Tuấn Vũ.',
    followersCount: 790000,
    isVerified: true,
    genreSlugs: ['indie-ballad']
  },
  'MCK, A$AP Ướt Mi': {
    slug: 'mck-asap-uot-mi',
    avatarUrl: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=500&q=80',
    bio: 'Bản phối cảm xúc Nếu Như Ta Chẳng Còn thể hiện màu sắc tự sự đặc trưng của MCK.',
    followersCount: 2850000,
    isVerified: true,
    genreSlugs: ['hip-hop-rap', 'r-and-b-soul']
  },
  'Mounter, Nguyễn Hùng': {
    slug: 'mounter-nguyen-hung',
    avatarUrl: 'https://images.unsplash.com/photo-1463453091185-61582044d556?w=500&q=80',
    bio: 'Ca khúc nhạc phim Gói Con Tim Làm Quà với giai điệu ấm áp do Mounter và Nguyễn Hùng thể hiện.',
    followersCount: 350000,
    isVerified: false,
    genreSlugs: ['indie-ballad']
  }
};

async function getUniqueSlug(baseSlug, existingId = null) {
  let slug = baseSlug || 'artist';
  let counter = 1;
  while (true) {
    const existing = await Artist.findOne({ slug });
    if (!existing || (existingId && existing._id.toString() === existingId.toString())) {
      return slug;
    }
    slug = `${baseSlug}-${counter++}`;
  }
}

async function generateArtists() {
  const mongoUri = env.mongoUri || 'mongodb://127.0.0.1:27017/music_app';
  console.log(`[generate-artists] Connecting to MongoDB: ${mongoUri}`);
  await mongoose.connect(mongoUri);

  // 1. Tải toàn bộ thể loại (Genres) để map ID
  const allGenres = await Genre.find().lean();
  const genreSlugMap = new Map();
  allGenres.forEach(g => genreSlugMap.set(g.slug, g._id));

  // 2. Tải toàn bộ danh sách bài hát
  const allSongs = await Song.find().populate('artistId').exec();
  console.log(`[generate-artists] Found ${allSongs.length} songs in database.`);

  // 3. Tiến hành chuẩn hóa và tạo dữ liệu nghệ sĩ
  let updatedSongsCount = 0;
  const artistMap = new Map(); // key: cleanName, value: Artist Document

  for (const song of allSongs) {
    let cleanTitle = song.title;
    let cleanArtistName = song.artistId?.name || song.artistName || 'Nghệ sĩ';

    // A. Xử lý trường hợp bài hát JACK
    if (song.title === 'JACK' || song.title === 'Jack') {
      if (song.audioUrl && song.audioUrl.includes('nguoi_dung')) {
        cleanTitle = 'Người Dưng';
      } else if (song.audioUrl && song.audioUrl.includes('luu_nien')) {
        cleanTitle = 'Lưu Niên';
      }
      cleanArtistName = 'Jack - J97';
    }

    // B. Áp dụng sửa lỗi cụ thể nếu nằm trong danh sách sửa
    if (SONG_METADATA_FIXES[song.title]) {
      const fix = SONG_METADATA_FIXES[song.title];
      cleanTitle = fix.title;
      cleanArtistName = fix.artistName;
    }

    // C. Chuẩn hóa tên nghệ sĩ tự động loại bỏ rác YouTube
    cleanArtistName = cleanArtistName
      .replace(/^PBN \d+\s*\|\s*/i, '')
      .replace(/\|\s*MV Lyrics.*$/i, '')
      .replace(/\|\s*Special Stage.*$/i, '')
      .replace(/\|\s*Live Performance.*$/i, '')
      .replace(/\|\s*Hà Nội Vi Vu.*$/i, '')
      .replace(/\|\s*Liveshow.*$/i, '')
      .replace(/live at.*$/i, '')
      .replace(/- Lyric.*$/i, '')
      .replace(/x @Dangtuanvu.*$/i, '')
      .replace(/\s*\(Genshin Impact\)/i, '')
      .replace(/「Lo\s*-\s*Fi Ver\..*$/i, '')
      .replace(/「Lo-Fi Ver\..*$/i, '')
      .replace(/\(Thu thanh trước 1975\)/i, '')
      .trim();

    // Hợp nhất tên viết hoa / biến thể
    if (cleanArtistName.toUpperCase() === 'MỸ TÂM') cleanArtistName = 'Mỹ Tâm';
    if (cleanArtistName.toUpperCase() === 'TUNG DUONG') cleanArtistName = 'Tùng Dương';
    if (cleanArtistName === 'Jack') cleanArtistName = 'Jack - J97';
    if (cleanArtistName.toLowerCase().startsWith('thap drill tu do')) cleanArtistName = 'Tháp Drill Tự Do';
    if (cleanArtistName.toLowerCase().startsWith('h2k')) cleanArtistName = 'H2K, KProx';
    if (cleanArtistName.toLowerCase().startsWith('tùng dương & soobin')) cleanArtistName = 'Tùng Dương, SOOBIN';
    if (cleanArtistName.toLowerCase().startsWith('nguyenn x @dangtuanvu')) cleanArtistName = 'Nguyenn, Đặng Tuấn Vũ';
    if (cleanArtistName.toLowerCase().startsWith('hòa minzy x tăng duy tân')) cleanArtistName = 'Hòa Minzy, Tăng Duy Tân';
    if (cleanArtistName.toLowerCase().startsWith('hòa minzy x tuấn cry')) cleanArtistName = 'Hòa Minzy, Tuấn Cry, Masew';
    if (cleanArtistName.toLowerCase().startsWith('tạ quang thắng, hồng duyên')) cleanArtistName = 'Tạ Quang Thắng, Hồng Duyên';
    if (cleanArtistName.toLowerCase().startsWith('mounter x nguyễn hùng')) cleanArtistName = 'Mounter, Nguyễn Hùng';
    if (cleanArtistName.toLowerCase().startsWith('quốc thiên')) cleanArtistName = 'Quốc Thiên';
    if (cleanArtistName.toLowerCase().startsWith('kiều hưng')) cleanArtistName = 'Kiều Hưng';
    if (cleanArtistName.toLowerCase().startsWith('nhã lam')) cleanArtistName = 'Nhã Lam';

    // D. Tìm hoặc tạo mới Document Nghệ sĩ (Artist)
    let artistDoc = artistMap.get(cleanArtistName);
    if (!artistDoc) {
      // Tìm trong DB xem đã có nghệ sĩ tên này chưa
      artistDoc = await Artist.findOne({
        name: cleanArtistName
      });

      // Lấy metadata từ từ điển hoặc sinh tự động
      const meta = ARTIST_METADATA_DICTIONARY[cleanArtistName] || {};
      const baseSlug = meta.slug || slugify(cleanArtistName);
      const avatarUrl = meta.avatarUrl || (song.coverImageUrl || 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80');
      const bio = meta.bio || (artistDoc && artistDoc.bio) || `${cleanArtistName} là nghệ sĩ nổi bật trên Vibe Music với các ca khúc được cộng đồng yêu thích.`;
      const followersCount = meta.followersCount || (artistDoc && artistDoc.followersCount) || Math.floor(Math.random() * 800000 + 200000);
      const isVerified = meta.isVerified !== undefined ? meta.isVerified : (artistDoc ? artistDoc.isVerified : true);
      const genreIds = (meta.genreSlugs || ['v-pop'])
        .map(s => genreSlugMap.get(s))
        .filter(Boolean);

      if (!artistDoc) {
        const uniqueSlug = await getUniqueSlug(baseSlug);
        artistDoc = await Artist.create({
          name: cleanArtistName,
          slug: uniqueSlug,
          avatarUrl,
          bio,
          followersCount,
          isVerified,
          genreIds: genreIds.length > 0 ? genreIds : [(genreSlugMap.get('v-pop') || allGenres[0]._id)]
        });
        console.log(`[generate-artists] [NEW] Đã tạo nghệ sĩ: "${cleanArtistName}" (slug: ${uniqueSlug})`);
      } else {
        // Cập nhật thông tin nghệ sĩ đã có cho chuẩn đẹp
        artistDoc.name = cleanArtistName;
        if (meta.slug) {
          artistDoc.slug = await getUniqueSlug(meta.slug, artistDoc._id);
        }
        if (meta.avatarUrl) artistDoc.avatarUrl = meta.avatarUrl;
        if (meta.bio) artistDoc.bio = meta.bio;
        if (meta.followersCount) artistDoc.followersCount = meta.followersCount;
        artistDoc.isVerified = isVerified;
        if (genreIds.length > 0) artistDoc.genreIds = genreIds;
        await artistDoc.save();
        console.log(`[generate-artists] [UPDATE] Đã chuẩn hóa nghệ sĩ: "${cleanArtistName}"`);
      }

      artistMap.set(cleanArtistName, artistDoc);
    }

    // E. Cập nhật bài hát trỏ đúng vào nghệ sĩ
    const needsUpdate = (
      song.title !== cleanTitle ||
      !song.artistId ||
      song.artistId._id.toString() !== artistDoc._id.toString()
    );

    if (needsUpdate) {
      song.title = cleanTitle;
      song.artistId = artistDoc._id;
      await song.save();
      updatedSongsCount++;
    }
  }

  // 4. Xóa các Artist "mồ côi" (không còn bài hát nào thuộc về do đã gộp tên)
  const allArtistsInDb = await Artist.find().lean();
  let deletedOrphans = 0;
  for (const art of allArtistsInDb) {
    const count = await Song.countDocuments({
      $or: [
        { artistId: art._id },
        { featuredArtistIds: art._id }
      ]
    });
    if (count === 0) {
      await Artist.findByIdAndDelete(art._id);
      deletedOrphans++;
      console.log(`[generate-artists] [DELETE ORPHAN] Đã xóa nghệ sĩ rác/trùng: "${art.name}" (${art._id})`);
    }
  }

  // 5. In bảng tổng kết
  const finalArtists = await Artist.find().sort({ followersCount: -1 }).lean();
  console.log(`\n========================================================================`);
  console.log(`   HOÀN TẤT TẠO DỮ LIỆU NGHỆ SĨ TỪ BÀI HÁT`);
  console.log(`========================================================================`);
  console.log(`Tổng số bài hát đã được chuẩn hóa & gắn artist: ${allSongs.length} (Cập nhật: ${updatedSongsCount})`);
  console.log(`Tổng số nghệ sĩ đang quản lý: ${finalArtists.length}`);
  console.log(`Số bản ghi nghệ sĩ rác đã dọn dẹp: ${deletedOrphans}`);
  console.log(`------------------------------------------------------------------------`);
  console.log(`Top nghệ sĩ hàng đầu và số bài hát tương ứng:`);
  for (let i = 0; i < Math.min(30, finalArtists.length); i++) {
    const a = finalArtists[i];
    const sCount = await Song.countDocuments({
      $or: [
        { artistId: a._id },
        { featuredArtistIds: a._id }
      ]
    });
    console.log(`${String(i + 1).padStart(2)}. [${a.name}] - ${sCount} bài | Followers: ${(a.followersCount || 0).toLocaleString()} | Slug: ${a.slug}`);
  }
  console.log(`========================================================================\n`);

  process.exit(0);
}

generateArtists().catch(err => {
  console.error('[generate-artists] Lỗi:', err);
  process.exit(1);
});
