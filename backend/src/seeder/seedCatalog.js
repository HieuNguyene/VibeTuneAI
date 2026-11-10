const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const config = require('../config/environment');
const logger = require('../utils/logger');

// Import Models
const User = require('../models/User');
const UserPreference = require('../models/UserPreference');
const Artist = require('../models/Artist');
const Album = require('../models/Album');
const Genre = require('../models/Genre');
const Song = require('../models/Song');
const Playlist = require('../models/Playlist');
const PlaylistTrack = require('../models/PlaylistTrack');
const ListeningHistory = require('../models/ListeningHistory');
const AiPromptLog = require('../models/AiPromptLog');

// 1. THỂ LOẠI NHẠC
const genresData = [
  {
    name: 'V-Pop',
    slug: 'v-pop',
    colorCode: '#1DB954',
    iconUrl: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&q=80',
    description: 'Nhạc Pop Việt Nam hiện đại, giai điệu bắt tai.'
  },
  {
    name: 'Lo-Fi Chill',
    slug: 'lo-fi-chill',
    colorCode: '#8B5CF6',
    iconUrl: 'https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=300&q=80',
    description: 'Âm thanh êm dịu, nhịp beat thư giãn học tập và làm việc.'
  },
  {
    name: 'Indie & Ballad',
    slug: 'indie-ballad',
    colorCode: '#EC4899',
    iconUrl: 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=300&q=80',
    description: 'Những bản tình ca sâu lắng, chất chứa cảm xúc.'
  },
  {
    name: 'Hip Hop & Rap',
    slug: 'hip-hop-rap',
    colorCode: '#F59E0B',
    iconUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=300&q=80',
    description: 'Nhịp beat mạnh mẽ, flow cuốn hút.'
  },
  {
    name: 'R&B / Soul',
    slug: 'r-and-b-soul',
    colorCode: '#06B6D4',
    iconUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300&q=80',
    description: 'Giai điệu mượt mà, quyến rũ.'
  },
  {
    name: 'Electronic / EDM',
    slug: 'electronic-edm',
    colorCode: '#10B981',
    iconUrl: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=300&q=80',
    description: 'Năng lượng bùng nổ cho tiệc tùng và tập luyện thể thao.'
  },
  {
    name: 'US-UK Pop',
    slug: 'us-uk-pop',
    colorCode: '#3B82F6',
    iconUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300&q=80',
    description: 'Các bản hit hàng đầu thế giới trên bảng xếp hạng Billboard.'
  },
  {
    name: 'K-Pop',
    slug: 'k-pop',
    colorCode: '#F43F5E',
    iconUrl: 'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=300&q=80',
    description: 'Làn sóng âm nhạc thần tượng Hàn Quốc sôi động.'
  }
];

// 2. NGHỆ SĨ THẬT
const artistsData = [
  {
    name: 'Sơn Tùng M-TP',
    slug: 'son-tung-mtp',
    avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&q=80',
    bio: 'Ca sĩ, nhạc sĩ hàng đầu Việt Nam.',
    followersCount: 5200000,
    isVerified: true
  },
  {
    name: 'HIEUTHUHAI',
    slug: 'hieuthuhai',
    avatarUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80',
    bio: 'Rapper, ca sĩ tài năng của dòng nhạc Hip-Hop/Rap đương đại.',
    followersCount: 2800000,
    isVerified: true
  },
  {
    name: 'Vũ.',
    slug: 'vu',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500&q=80',
    bio: 'Hoàng tử Indie Việt Nam với chất giọng trầm ấm đặc trưng.',
    followersCount: 1850000,
    isVerified: true
  },
  {
    name: 'Bray',
    slug: 'bray',
    avatarUrl: '/uploads/covers/VungAnToan_img.jpg',
    bio: 'Rapper nổi tiếng với phong cách rap đặc trưng và ca từ phóng khoáng.',
    followersCount: 1650000,
    isVerified: true
  },
  {
    name: 'MCK & Wren Evans',
    slug: 'mck-wren',
    avatarUrl: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=500&q=80',
    bio: 'Bộ đôi nghệ sĩ trẻ đột phá định hình âm nhạc thế hệ Z Việt Nam.',
    followersCount: 2200000,
    isVerified: true
  },
  {
    name: 'Jack - J97',
    slug: 'jack-j97',
    avatarUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=500&q=80',
    bio: 'Ca sĩ mang âm hưởng dân ca hiện đại miền Tây Nam Bộ.',
    followersCount: 3100000,
    isVerified: true
  },
  {
    name: 'BIGBANG & Taeyang',
    slug: 'bigbang-taeyang',
    avatarUrl: 'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=500&q=80',
    bio: 'Những vị vua K-Pop huyền thoại của thế hệ gen 2.',
    followersCount: 9500000,
    isVerified: true
  },
  {
    name: 'Ed Sheeran & Bruno Mars',
    slug: 'ed-bruno',
    avatarUrl: 'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=500&q=80',
    bio: 'Những cỗ máy tạo hit toàn cầu thống trị mọi bảng xếp hạng quốc tế.',
    followersCount: 14000000,
    isVerified: true
  },
  {
    name: 'Andree Right Hand & Wxrdie',
    slug: 'andree-wxrdie',
    avatarUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80',
    bio: 'Biểu tượng Trap và Hip-Hop xa hoa đẳng cấp hàng đầu Việt Nam.',
    followersCount: 1900000,
    isVerified: true
  },
  {
    name: 'Hoàng Dũng & Đen Vâu',
    slug: 'hoang-dung-den',
    avatarUrl: 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=500&q=80',
    bio: 'Những nghệ sĩ mộc mạc mang âm nhạc chữa lành tâm hồn người nghe.',
    followersCount: 3800000,
    isVerified: true
  }
];

// 3. ALBUM NỔI TIẾNG
const albumsData = [
  {
    title: 'm-tp M-TP & Tuyệt Phẩm',
    slug: 'album-son-tung',
    artistSlug: 'son-tung-mtp',
    coverImageUrl: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80',
    releaseYear: 2024,
    genreSlug: 'v-pop'
  },
  {
    title: 'Chúng Ta & Skyler',
    slug: 'album-son-tung-2',
    artistSlug: 'son-tung-mtp',
    coverImageUrl: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&q=80',
    releaseYear: 2021,
    genreSlug: 'v-pop'
  },
  {
    title: 'Ai Cũng Phải Bắt Đầu Từ Đâu Đó',
    slug: 'album-hieuthuhai',
    artistSlug: 'hieuthuhai',
    coverImageUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80',
    releaseYear: 2023,
    genreSlug: 'hip-hop-rap'
  },
  {
    title: 'Một Vạn Năm',
    slug: 'album-vu',
    artistSlug: 'vu',
    coverImageUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=600&q=80',
    releaseYear: 2022,
    genreSlug: 'indie-ballad'
  },
  {
    title: 'Bảo Tàng Của Nuối Tiếc',
    slug: 'album-vu-2',
    artistSlug: 'vu',
    coverImageUrl: 'https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=600&q=80',
    releaseYear: 2024,
    genreSlug: 'indie-ballad'
  },
  {
    title: '99% & GenZ Energy',
    slug: 'album-mck-wren',
    artistSlug: 'mck-wren',
    coverImageUrl: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=600&q=80',
    releaseYear: 2024,
    genreSlug: 'r-and-b-soul'
  },
  {
    title: 'Vùng An Toàn & Cao Ốc',
    slug: 'album-bray',
    artistSlug: 'bray',
    coverImageUrl: '/uploads/covers/VungAnToan_img.jpg',
    releaseYear: 2023,
    genreSlug: 'hip-hop-rap'
  },
  {
    title: 'Hồng Nhan Bạc Phận Sóng Gió',
    slug: 'album-jack',
    artistSlug: 'jack-j97',
    coverImageUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=600&q=80',
    releaseYear: 2021,
    genreSlug: 'v-pop'
  },
  {
    title: 'MADE & Rise K-Pop Giants',
    slug: 'album-bigbang',
    artistSlug: 'bigbang-taeyang',
    coverImageUrl: 'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&q=80',
    releaseYear: 2023,
    genreSlug: 'k-pop'
  },
  {
    title: 'Global Super Hits & Billboard',
    slug: 'album-global',
    artistSlug: 'ed-bruno',
    coverImageUrl: 'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&q=80',
    releaseYear: 2024,
    genreSlug: 'us-uk-pop'
  },
  {
    title: 'Tay Chơi & Underground',
    slug: 'album-andree-wxrdie',
    artistSlug: 'andree-wxrdie',
    coverImageUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&q=80',
    releaseYear: 2023,
    genreSlug: 'hip-hop-rap'
  },
  {
    title: '25 & Lối Nhỏ Mộc Mạc',
    slug: 'album-hoang-dung-den',
    artistSlug: 'hoang-dung-den',
    coverImageUrl: 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&q=80',
    releaseYear: 2022,
    genreSlug: 'indie-ballad'
  }
];

// 4. TOÀN BỘ 40 BÀI HÁT THẬT CHẤT LƯỢNG CAO
const allSongsCatalog = [
  // Sơn Tùng M-TP
  {
    title: 'Âm Thầm Bên Em',
    artistSlug: 'son-tung-mtp',
    albumSlug: 'album-son-tung',
    genreSlugs: ['v-pop', 'indie-ballad'],
    duration: 292,
    audioUrl: '/uploads/audios/am_tham_ben_em.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80',
    lyrics: 'Khi bên em anh thấy lòng bình yên...'
  },
  {
    title: 'Lạc Trôi',
    artistSlug: 'son-tung-mtp',
    albumSlug: 'album-son-tung',
    genreSlugs: ['v-pop', 'electronic-edm'],
    duration: 232,
    audioUrl: '/uploads/audios/lac_troi_son_tung_mtp.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80',
    lyrics: 'Người theo hương hoa mây mù giăng lối...'
  },
  {
    title: 'Come My Way',
    artistSlug: 'son-tung-mtp',
    albumSlug: 'album-son-tung-2',
    genreSlugs: ['v-pop', 'hip-hop-rap'],
    duration: 215,
    audioUrl: '/uploads/audios/come_my_way_son_tung_tyga.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=600&q=80',
    lyrics: 'Baby come my way tonight...'
  },

  // HIEUTHUHAI
  {
    title: 'Exit Sign',
    artistSlug: 'hieuthuhai',
    albumSlug: 'album-hieuthuhai',
    genreSlugs: ['hip-hop-rap', 'v-pop'],
    duration: 202,
    audioUrl: '/uploads/audios/exit_sign_hieuthuhai.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80',
    lyrics: 'I see the exit sign and I know I have to leave...'
  },

  // Vũ. & Hoàng Dũng
  {
    title: 'Bình Yên',
    artistSlug: 'vu',
    albumSlug: 'album-vu',
    genreSlugs: ['indie-ballad', 'lo-fi-chill'],
    duration: 254,
    audioUrl: '/uploads/audios/binh_yen_vu_binz.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=600&q=80',
    lyrics: 'Bình yên giữa những ngày giông bão...'
  },
  {
    title: 'Chẳng Thể Tìm Được Em',
    artistSlug: 'vu',
    albumSlug: 'album-vu',
    genreSlugs: ['lo-fi-chill', 'indie-ballad'],
    duration: 245,
    audioUrl: '/uploads/audios/playlist_hngle_phucxp.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&q=80',
    lyrics: 'Chẳng thể tìm được em giữa những thênh thang...'
  },
  {
    title: 'Tìm Em',
    artistSlug: 'hoang-dung-den',
    albumSlug: 'album-hoang-dung-den',
    genreSlugs: ['indie-ballad'],
    duration: 236,
    audioUrl: '/uploads/audios/tim_em_hngle.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&q=80',
    lyrics: 'Đi tìm một người trong những giấc mơ...'
  },
  {
    title: 'Trốn Tìm',
    artistSlug: 'hoang-dung-den',
    albumSlug: 'album-hoang-dung-den',
    genreSlugs: ['indie-ballad', 'hip-hop-rap'],
    duration: 250,
    audioUrl: '/uploads/audios/tron_tim_den_vau_mtv.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&q=80',
    lyrics: 'Câu chuyện tình ta như trò trốn tìm ngày bé...'
  },

  // Bray
  {
    title: 'Vùng An Toàn',
    artistSlug: 'bray',
    albumSlug: 'album-bray',
    genreSlugs: ['hip-hop-rap'],
    duration: 218,
    audioUrl: '/uploads/audios/vung_an_toan_bray.mp3',
    coverImageUrl: '/uploads/covers/VungAnToan_img.jpg',
    lyrics: 'Rời xa vùng an toàn để bước tiếp...'
  },
  {
    title: 'Cao Ốc 20',
    artistSlug: 'bray',
    albumSlug: 'album-bray',
    genreSlugs: ['hip-hop-rap', 'indie-ballad'],
    duration: 238,
    audioUrl: '/uploads/audios/cao_oc_20_bray_datg.mp3',
    coverImageUrl: '/uploads/covers/VungAnToan_img.jpg',
    lyrics: 'Từ trên cao ốc tầng hai mươi nhìn xuống thành phố...'
  },

  // MCK & Wren Evans
  {
    title: 'Tại Vì Sao',
    artistSlug: 'mck-wren',
    albumSlug: 'album-mck-wren',
    genreSlugs: ['r-and-b-soul', 'hip-hop-rap'],
    duration: 210,
    audioUrl: '/uploads/audios/tai_vi_sao_rpt_mck.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=600&q=80',
    lyrics: 'Tại vì sao đôi ta cách xa...'
  },
  {
    title: 'Từng Quen',
    artistSlug: 'mck-wren',
    albumSlug: 'album-mck-wren',
    genreSlugs: ['r-and-b-soul', 'v-pop'],
    duration: 198,
    audioUrl: '/uploads/audios/tung_quen_wren_evans.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=600&q=80',
    lyrics: 'Từng quen nhau như thế, sao giờ như người dưng...'
  },

  // Jack - J97
  {
    title: 'Sóng Gió',
    artistSlug: 'jack-j97',
    albumSlug: 'album-jack',
    genreSlugs: ['v-pop', 'indie-ballad'],
    duration: 254,
    audioUrl: '/uploads/audios/song_gio_jack_kicm.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=600&q=80',
    lyrics: 'Sóng gió trùng phùng duyên phận nổi trôi...'
  },
  {
    title: 'Hồng Nhan',
    artistSlug: 'jack-j97',
    albumSlug: 'album-jack',
    genreSlugs: ['v-pop'],
    duration: 235,
    audioUrl: '/uploads/audios/hong_nhan_jack.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=600&q=80',
    lyrics: 'Hồng nhan một kiếp phong ba...'
  },
  {
    title: 'Bạc Phận',
    artistSlug: 'jack-j97',
    albumSlug: 'album-jack',
    genreSlugs: ['v-pop'],
    duration: 242,
    audioUrl: '/uploads/audios/bac_phan_jack_kicm.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=600&q=80',
    lyrics: 'Phận duyên lỡ làng duyên bẽ bàng...'
  },
  {
    title: 'Lưu Niên',
    artistSlug: 'jack-j97',
    albumSlug: 'album-jack',
    genreSlugs: ['v-pop', 'indie-ballad'],
    duration: 228,
    audioUrl: '/uploads/audios/luu_nien_jack_j97.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=600&q=80',
    lyrics: 'Lưu niên theo năm tháng phai nhoà...'
  },

  // BIGBANG & Taeyang
  {
    title: 'Haru Haru',
    artistSlug: 'bigbang-taeyang',
    albumSlug: 'album-bigbang',
    genreSlugs: ['k-pop', 'hip-hop-rap'],
    duration: 255,
    audioUrl: '/uploads/audios/haru_haru_bigbang.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&q=80',
    lyrics: 'Haru haru day by day...'
  },
  {
    title: 'Fantastic Baby',
    artistSlug: 'bigbang-taeyang',
    albumSlug: 'album-bigbang',
    genreSlugs: ['k-pop', 'electronic-edm'],
    duration: 231,
    audioUrl: '/uploads/audios/fantastic_baby_bigbang.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&q=80',
    lyrics: 'Wow fantastic baby dance dance...'
  },
  {
    title: 'Eyes, Nose, Lips',
    artistSlug: 'bigbang-taeyang',
    albumSlug: 'album-bigbang',
    genreSlugs: ['k-pop', 'r-and-b-soul'],
    duration: 270,
    audioUrl: '/uploads/audios/eyes_nose_lips_taeyang.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&q=80',
    lyrics: 'Your eyes, nose, lips and touch...'
  },

  // US-UK Billboard Hits
  {
    title: 'Shape of You',
    artistSlug: 'ed-bruno',
    albumSlug: 'album-global',
    genreSlugs: ['us-uk-pop'],
    duration: 233,
    audioUrl: '/uploads/audios/shape_of_you_ed_sheeran.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&q=80',
    lyrics: 'I am in love with the shape of you...'
  },
  {
    title: 'Die With A Smile',
    artistSlug: 'ed-bruno',
    albumSlug: 'album-global',
    genreSlugs: ['us-uk-pop', 'r-and-b-soul'],
    duration: 251,
    audioUrl: '/uploads/audios/die_with_a_smile_gaga_mars.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&q=80',
    lyrics: 'If the world was ending, I would wanna be next to you...'
  },
  {
    title: 'Despacito',
    artistSlug: 'ed-bruno',
    albumSlug: 'album-global',
    genreSlugs: ['us-uk-pop', 'electronic-edm'],
    duration: 229,
    audioUrl: '/uploads/audios/despacito_luis_fonsi_bieber.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&q=80',
    lyrics: 'Despacito, quiero respirar tu cuello despacito...'
  },
  {
    title: 'Animals',
    artistSlug: 'ed-bruno',
    albumSlug: 'album-global',
    genreSlugs: ['us-uk-pop'],
    duration: 231,
    audioUrl: '/uploads/audios/animals_maroon_5.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&q=80',
    lyrics: 'Baby, I am preying on you tonight...'
  },
  {
    title: 'Old Town Road',
    artistSlug: 'ed-bruno',
    albumSlug: 'album-global',
    genreSlugs: ['us-uk-pop', 'hip-hop-rap'],
    duration: 157,
    audioUrl: '/uploads/audios/old_town_road_lil_nas_x.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&q=80',
    lyrics: 'Yeah, I am gonna take my horse to the old town road...'
  },
  {
    title: '50 Feet',
    artistSlug: 'ed-bruno',
    albumSlug: 'album-global',
    genreSlugs: ['us-uk-pop', 'r-and-b-soul'],
    duration: 205,
    audioUrl: '/uploads/audios/50_feet_somo.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&q=80',
    lyrics: 'Stay fifty feet away...'
  },

  // Andree & Wxrdie
  {
    title: 'Em Iu',
    artistSlug: 'andree-wxrdie',
    albumSlug: 'album-andree-wxrdie',
    genreSlugs: ['hip-hop-rap'],
    duration: 224,
    audioUrl: '/uploads/audios/em_iu_andree_wxrdie_binhgold.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80',
    lyrics: 'Em iu em iu...'
  },
  {
    title: 'Chơi Như Tụi Mỹ',
    artistSlug: 'andree-wxrdie',
    albumSlug: 'album-andree-wxrdie',
    genreSlugs: ['hip-hop-rap'],
    duration: 212,
    audioUrl: '/uploads/audios/choi_nhu_tui_my_andree.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80',
    lyrics: 'Tụi anh chơi như tụi Mỹ...'
  },
  {
    title: 'Đổi Tư Thế',
    artistSlug: 'andree-wxrdie',
    albumSlug: 'album-andree-wxrdie',
    genreSlugs: ['hip-hop-rap'],
    duration: 198,
    audioUrl: '/uploads/audios/doi_tu_the_donald_gold_andree.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80',
    lyrics: 'Đổi tư thế nhịp beat...'
  },
  {
    title: 'Lối Sống',
    artistSlug: 'andree-wxrdie',
    albumSlug: 'album-andree-wxrdie',
    genreSlugs: ['hip-hop-rap'],
    duration: 205,
    audioUrl: '/uploads/audios/loi_song_wxrdie.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80',
    lyrics: 'Lối sống tự do phóng khoáng...'
  },
  {
    title: 'Tết Vớ Vẩn',
    artistSlug: 'andree-wxrdie',
    albumSlug: 'album-andree-wxrdie',
    genreSlugs: ['hip-hop-rap'],
    duration: 195,
    audioUrl: '/uploads/audios/tetvoven_wxrdie_andree.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80',
    lyrics: 'Xuân về đón Tết thật vui...'
  },

  // Ca khúc V-Pop đa sắc màu
  {
    title: 'Bắc Bling',
    artistSlug: 'son-tung-mtp',
    albumSlug: 'album-son-tung',
    genreSlugs: ['v-pop'],
    duration: 215,
    audioUrl: '/uploads/audios/bac_bling_hoa_minzy.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80',
    lyrics: 'Bắc Bling giai điệu tươi vui...'
  },
  {
    title: 'Kẻ Say Tình',
    artistSlug: 'hoang-dung-den',
    albumSlug: 'album-hoang-dung-den',
    genreSlugs: ['v-pop', 'indie-ballad'],
    duration: 260,
    audioUrl: '/uploads/audios/ke_say_tinh_quoc_thien.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&q=80',
    lyrics: 'Kẻ say tình giữa đêm lạnh...'
  },
  {
    title: 'Tái Sinh',
    artistSlug: 'hoang-dung-den',
    albumSlug: 'album-hoang-dung-den',
    genreSlugs: ['v-pop', 'indie-ballad'],
    duration: 285,
    audioUrl: '/uploads/audios/tai_sinh_tung_duong.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&q=80',
    lyrics: 'Tái sinh một cuộc đời mới rực rỡ...'
  },
  {
    title: 'Chẳng Hợp Nhau Đâu',
    artistSlug: 'jack-j97',
    albumSlug: 'album-jack',
    genreSlugs: ['v-pop'],
    duration: 240,
    audioUrl: '/uploads/audios/chang_hop_nhau_dau_le_bao_binh.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=600&q=80',
    lyrics: 'Ta chẳng hợp nhau đâu em ơi...'
  },
  {
    title: 'Kịch Bản Tào',
    artistSlug: 'mck-wren',
    albumSlug: 'album-mck-wren',
    genreSlugs: ['v-pop'],
    duration: 215,
    audioUrl: '/uploads/audios/kich_ban_tao_hoang_yen_chibi.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=600&q=80',
    lyrics: 'Kịch bản do em viết nên...'
  },
  {
    title: 'Còn Gì Đẹp Hơn',
    artistSlug: 'vu',
    albumSlug: 'album-vu-2',
    genreSlugs: ['indie-ballad'],
    duration: 248,
    audioUrl: '/uploads/audios/con_gi_dep_hon_nguyen_hung.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=600&q=80',
    lyrics: 'Còn gì đẹp hơn ánh mắt em...'
  },
  {
    title: 'Diễm Xưa',
    artistSlug: 'vu',
    albumSlug: 'album-vu-2',
    genreSlugs: ['indie-ballad'],
    duration: 275,
    audioUrl: '/uploads/audios/diem_xua_ha_an_huy.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=600&q=80',
    lyrics: 'Mưa vẫn mưa bay trên tầng tháp cổ...'
  },
  {
    title: 'Ngày Mai Em Đi Mất',
    artistSlug: 'jack-j97',
    albumSlug: 'album-jack',
    genreSlugs: ['v-pop'],
    duration: 232,
    audioUrl: '/uploads/audios/ngay_mai_em_di_mat_khai_dang.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=600&q=80',
    lyrics: 'Ngày mai em đi mất anh biết tìm ai...'
  },
  {
    title: 'Phép Màu',
    artistSlug: 'mck-wren',
    albumSlug: 'album-mck-wren',
    genreSlugs: ['v-pop', 'lo-fi-chill'],
    duration: 220,
    audioUrl: '/uploads/audios/phep_mau_mounter_maydays.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=600&q=80',
    lyrics: 'Phép màu xuất hiện trong tình yêu...'
  },
  {
    title: 'Trường Sơn Đông Trường Sơn Tây',
    artistSlug: 'hoang-dung-den',
    albumSlug: 'album-hoang-dung-den',
    genreSlugs: ['v-pop'],
    duration: 260,
    audioUrl: '/uploads/audios/truong_son_dong_truong_son_tay.mp3',
    coverImageUrl: 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&q=80',
    lyrics: 'Cùng mắc võng trên rừng Trường Sơn...'
  }
];

const seedDatabase = async () => {
  try {
    logger.info('Dang ket noi toi MongoDB...');
    await mongoose.connect(config.mongodbUri);
    logger.success('Ket noi CSDL thanh cong.');

    // 1. Don dep toan bo catalog cu (GIU NGUYEN USER DATABASE)
    logger.info('Xoa toan bo catalog cu (bao toan User)...');
    await Promise.all([
      Genre.deleteMany({}),
      Artist.deleteMany({}),
      Album.deleteMany({}),
      Song.deleteMany({}),
      Playlist.deleteMany({}),
      PlaylistTrack.deleteMany({}),
      ListeningHistory.deleteMany({}),
      AiPromptLog.deleteMany({})
    ]);

    // 2. Nap The loai
    logger.info('Khoi tao cac the loai...');
    const insertedGenres = await Genre.insertMany(genresData);
    const genreMap = {};
    insertedGenres.forEach((g) => {
      genreMap[g.slug] = g._id;
    });

    // 3. Nap Nghe si
    logger.info('Khoi tao danh sach nghe si...');
    const insertedArtists = await Artist.insertMany(artistsData);
    const artistMap = {};
    insertedArtists.forEach((a) => {
      artistMap[a.slug] = a._id;
    });

    // 4. Nap Album noi tieng
    logger.info('Khoi tao cac Album noi tieng...');
    const albumDocs = albumsData.map((alb) => ({
      title: alb.title,
      artistId: artistMap[alb.artistSlug] || insertedArtists[0]._id,
      coverImageUrl: alb.coverImageUrl,
      releaseYear: alb.releaseYear,
      genreIds: [genreMap[alb.genreSlug] || insertedGenres[0]._id],
      totalTracks: 0
    }));
    const insertedAlbums = await Album.insertMany(albumDocs);
    const albumMap = {};
    albumsData.forEach((alb, idx) => {
      albumMap[alb.slug] = insertedAlbums[idx]._id;
    });

    // 5. Nap 40 Bai hat that chat luong cao
    logger.info(`Dang nap toan bo ${allSongsCatalog.length} bai hat that...`);
    const songDocs = allSongsCatalog.map((s) => ({
      title: s.title,
      artistId: artistMap[s.artistSlug] || insertedArtists[0]._id,
      albumId: albumMap[s.albumSlug] || null,
      genreIds: (s.genreSlugs || []).map((slug) => genreMap[slug] || insertedGenres[0]._id),
      duration: s.duration || 220,
      audioUrl: s.audioUrl,
      coverImageUrl: s.coverImageUrl || 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80',
      lyrics: s.lyrics || '',
      mood: ['chill', 'happy'],
      tempo: 'medium',
      playsCount: Math.floor(Math.random() * 8000) + 1500,
      likesCount: Math.floor(Math.random() * 1200) + 200,
      isPublic: true
    }));

    const insertedSongs = await Song.insertMany(songDocs);
    logger.success(`Da nap thanh cong ${insertedSongs.length} bai hat that!`);

    // Cap nhat totalTracks cho moi Album
    for (const alb of insertedAlbums) {
      const count = await Song.countDocuments({ albumId: alb._id });
      await Album.findByIdAndUpdate(alb._id, { totalTracks: count });
    }

    // 6. Tao Playlists tuyen chon phong phu
    let demoUser = await User.findOne({ role: 'admin' }) || await User.findOne({});
    if (!demoUser) {
      const salt = await bcrypt.genSalt(10);
      const demoPasswordHash = await bcrypt.hash('Password123@', salt);
      demoUser = await User.create({
        username: 'spotify_tester',
        email: 'demo@spotifyclone.com',
        passwordHash: demoPasswordHash,
        displayName: 'Spotify Editor',
        avatarUrl: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&q=80',
        role: 'user'
      });
    }

    const playlistsData = [
      {
        title: 'Top Hits Việt Nam 2026',
        description: 'Những bản hit V-Pop & Rap Việt đỉnh cao đang làm mưa làm gió.',
        coverImageUrl: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&q=80',
        songIndices: [0, 1, 3, 4, 8, 10, 11, 12]
      },
      {
        title: 'Rap Việt Cực Cháy',
        description: 'Beat gắt, flow đỉnh với dàn sao Rap: Andree, HIEUTHUHAI, Bray, MCK, Wxrdie.',
        coverImageUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80',
        songIndices: [3, 8, 9, 10, 25, 26, 27, 28]
      },
      {
        title: 'Acoustic & Indie Chữa Lành',
        description: 'Những giai điệu sâu lắng, êm dịu sưởi ấm tâm hồn bạn.',
        coverImageUrl: 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&q=80',
        songIndices: [4, 5, 6, 7, 32, 33, 35, 36]
      },
      {
        title: 'US-UK Billboard All-Stars',
        description: 'Đắm chìm cùng những siêu sao thế giới Ed Sheeran, Bruno Mars, Lady Gaga, Maroon 5.',
        coverImageUrl: 'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&q=80',
        songIndices: [19, 20, 21, 22, 23, 24]
      },
      {
        title: 'K-Pop Huyền Thoại',
        description: 'Cùng trở về thời hoàng kim với các bản hit bất hủ của BIGBANG và Taeyang.',
        coverImageUrl: 'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&q=80',
        songIndices: [16, 17, 18]
      },
      {
        title: 'V-Pop Ballad Trữ Tình',
        description: 'Những câu chuyện tình yêu da diết và đầy cảm xúc.',
        coverImageUrl: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80',
        songIndices: [0, 6, 12, 13, 14, 15, 31, 34]
      }
    ];

    for (const pl of playlistsData) {
      const createdPl = await Playlist.create({
        title: pl.title,
        description: pl.description,
        coverImageUrl: pl.coverImageUrl,
        creatorId: demoUser._id,
        isPublic: true,
        totalTracks: pl.songIndices.length
      });

      const ptDocs = pl.songIndices.map((sIdx, order) => ({
        playlistId: createdPl._id,
        songId: insertedSongs[sIdx]._id,
        order
      }));
      await PlaylistTrack.insertMany(ptDocs);
    }

    logger.success('====================================================');
    logger.success(` HOÀN TẤT! ĐÃ NẠP:`);
    logger.success(` - ${insertedGenres.length} Thể loại`);
    logger.success(` - ${insertedArtists.length} Nghệ sĩ`);
    logger.success(` - ${insertedAlbums.length} Album nổi tiếng`);
    logger.success(` - ${insertedSongs.length} Bài hát thật chất lượng cao`);
    logger.success(` - ${playlistsData.length} Playlist tuyển chọn phong phú`);
    logger.success('====================================================');

    process.exit(0);
  } catch (error) {
    logger.error('Loi khi seed du lieu:', error.message);
    process.exit(1);
  }
};

seedDatabase();
