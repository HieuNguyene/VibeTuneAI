const mongoose = require('mongoose');
const config = require('./src/config/environment');
const models = require('./src/models');

async function run() {
  await mongoose.connect(config.mongodbUri);

  // 1. Cập nhật bài hát "Vùng An Toàn"
  await models.Song.updateMany(
    { title: { $regex: /Vùng An Toàn/i } },
    { $set: { audioUrl: '/uploads/audios/vung_an_toan_bray.mp3' } }
  );

  // 2. Thêm hoặc cập nhật các bài hát thật khác mà bạn đã thả vào thư mục audios
  const realSongs = [
    {
      title: 'Âm Thầm Bên Em',
      artistName: 'Sơn Tùng M-TP',
      audioUrl: '/uploads/audios/am_tham_ben_em.mp3',
      coverImageUrl: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&q=80',
      duration: 295
    },
    {
      title: 'Exit Sign',
      artistName: 'HIEUTHUHAI',
      audioUrl: '/uploads/audios/exit_sign_hieuthuhai.mp3',
      coverImageUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80',
      duration: 205
    },
    {
      title: 'Tìm Em',
      artistName: 'Hngle ft. Bảo Anh',
      audioUrl: '/uploads/audios/tim_em_hngle.mp3',
      coverImageUrl: 'https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=500&q=80',
      duration: 230
    },
    {
      title: 'Tuyển Tập Hngle x PhucXP',
      artistName: 'Hngle & PhucXP',
      audioUrl: '/uploads/audios/playlist_hngle_phucxp.mp3',
      coverImageUrl: 'https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=500&q=80',
      duration: 1200
    }
  ];

  for (const s of realSongs) {
    let artist = await models.Artist.findOne({ name: s.artistName });
    if (!artist) {
      artist = await models.Artist.create({
        name: s.artistName,
        slug: s.artistName.toLowerCase().replace(/\s+/g, '-'),
        avatarUrl: s.coverImageUrl,
        bio: 'Nghệ sĩ'
      });
    }

    const existing = await models.Song.findOne({ title: s.title });
    if (!existing) {
      await models.Song.create({
        title: s.title,
        artistId: artist._id,
        duration: s.duration,
        audioUrl: s.audioUrl,
        coverImageUrl: s.coverImageUrl,
        isPublic: true,
        playsCount: 100,
        likesCount: 10
      });
      console.log('Created song:', s.title);
    } else {
      await models.Song.updateOne({ _id: existing._id }, { $set: { audioUrl: s.audioUrl } });
      console.log('Updated song:', s.title);
    }
  }

  const allSongs = await models.Song.find().select('title audioUrl');
  console.log('All songs in DB:', allSongs);
  process.exit(0);
}

run();
