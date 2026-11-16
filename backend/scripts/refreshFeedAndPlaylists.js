/**
 * refreshFeedAndPlaylists.js
 * Cập nhật danh sách phát (Curated Playlists) & BXH cho toàn bộ 152 bài hát
 */

require('dotenv').config();
const mongoose = require('mongoose');
const Song = require('../src/models/Song');
const Artist = require('../src/models/Artist');
const Genre = require('../src/models/Genre');
const Playlist = require('../src/models/Playlist');
const PlaylistTrack = require('../src/models/PlaylistTrack');
const User = require('../src/models/User');

async function refresh() {
  await mongoose.connect(process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/music_app');
  console.log('=== CẬP NHẬT PLAYLIST & FEED CHO TOÀN BỘ 152 BÀI HÁT ===\n');

  let adminUser = await User.findOne({ role: 'admin' });
  if (!adminUser) {
    adminUser = await User.findOne({});
  }

  // 1. Phân bổ playsCount & likesCount thực tế cho các bài hát chưa có số liệu đẹp
  const allSongs = await Song.find({});
  console.log(`Đang kiểm tra và tạo chỉ số lượt nghe cho ${allSongs.length} bài hát...`);

  for (const song of allSongs) {
    if (!song.playsCount || song.playsCount < 10000) {
      song.playsCount = Math.floor(Math.random() * 2500000) + 150000;
      song.likesCount = Math.floor(song.playsCount * (Math.random() * 0.08 + 0.03));
      await song.save();
    }
  }

  // 2. Cập nhật Playlist: Top 50 Thịnh Hành (Lấy top 50 bài hát nhiều lượt nghe nhất)
  const topTrendingSongs = await Song.find({ isPublic: true })
    .sort({ playsCount: -1 })
    .limit(50);

  let top50Playlist = await Playlist.findOne({ title: /Top 50 Thịnh Hành/i });
  if (!top50Playlist) {
    top50Playlist = await Playlist.create({
      title: '🔥 Top 50 Thịnh Hành Việt Nam',
      description: 'Bảng xếp hạng 50 ca khúc được nghe nhiều nhất tuần này trên Vibe Music.',
      coverImageUrl: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80',
      creatorId: adminUser._id,
      isPublic: true,
      totalTracks: 0
    });
  }

  await PlaylistTrack.deleteMany({ playlistId: top50Playlist._id });
  for (let i = 0; i < topTrendingSongs.length; i++) {
    await PlaylistTrack.create({
      playlistId: top50Playlist._id,
      songId: topTrendingSongs[i]._id,
      order: i,
      addedAt: new Date(Date.now() - i * 3600000)
    });
  }
  top50Playlist.totalTracks = topTrendingSongs.length;
  await top50Playlist.save();
  console.log(`✅ Đã cập nhật playlist: "${top50Playlist.title}" (${topTrendingSongs.length} bài hát)`);

  // 3. Cập nhật Playlist: GenZ Rap Việt
  const rapGenre = await Genre.findOne({ slug: 'hip-hop-rap' });
  if (rapGenre) {
    const rapSongs = await Song.find({ genreIds: rapGenre._id, isPublic: true })
      .sort({ playsCount: -1 })
      .limit(30);

    let rapPlaylist = await Playlist.findOne({ title: /Rap Việt/i });
    if (!rapPlaylist) {
      rapPlaylist = await Playlist.create({
        title: '⚡ GenZ Rap Việt Đỉnh Cao',
        description: 'Tuyển tập những con flow bùng nổ, trap, drill và hip-hop gai góc nhất.',
        coverImageUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80',
        creatorId: adminUser._id,
        isPublic: true,
        totalTracks: 0
      });
    }

    await PlaylistTrack.deleteMany({ playlistId: rapPlaylist._id });
    for (let i = 0; i < rapSongs.length; i++) {
      await PlaylistTrack.create({
        playlistId: rapPlaylist._id,
        songId: rapSongs[i]._id,
        order: i
      });
    }
    rapPlaylist.totalTracks = rapSongs.length;
    await rapPlaylist.save();
    console.log(`✅ Đã cập nhật playlist: "${rapPlaylist.title}" (${rapSongs.length} bài hát)`);
  }

  // 4. Cập nhật Playlist: Acoustic & Indie Cafe
  const indieGenre = await Genre.findOne({ slug: 'indie-ballad' });
  if (indieGenre) {
    const indieSongs = await Song.find({ genreIds: indieGenre._id, isPublic: true })
      .sort({ playsCount: -1 })
      .limit(30);

    let indiePlaylist = await Playlist.findOne({ title: /Indie|Acoustic/i });
    if (!indiePlaylist) {
      indiePlaylist = await Playlist.create({
        title: '☕ Acoustic & Indie Cafe',
        description: 'Những giai điệu mộc mạc sâu lắng, mang lại cảm giác bình yên bên tách cà phê ấm.',
        coverImageUrl: 'https://images.unsplash.com/photo-1442512595331-e89e73853f31?w=600&q=80',
        creatorId: adminUser._id,
        isPublic: true,
        totalTracks: 0
      });
    }

    await PlaylistTrack.deleteMany({ playlistId: indiePlaylist._id });
    for (let i = 0; i < indieSongs.length; i++) {
      await PlaylistTrack.create({
        playlistId: indiePlaylist._id,
        songId: indieSongs[i]._id,
        order: i
      });
    }
    indiePlaylist.totalTracks = indieSongs.length;
    await indiePlaylist.save();
    console.log(`✅ Đã cập nhật playlist: "${indiePlaylist.title}" (${indieSongs.length} bài hát)`);
  }

  // 5. Cập nhật Playlist: Lofi & Suy Nửa Đêm
  const lofiGenre = await Genre.findOne({ slug: 'lo-fi-chill' });
  if (lofiGenre) {
    const lofiSongs = await Song.find({ genreIds: lofiGenre._id, isPublic: true })
      .sort({ playsCount: -1 })
      .limit(30);

    let lofiPlaylist = await Playlist.findOne({ title: /Lofi/i });
    if (!lofiPlaylist) {
      lofiPlaylist = await Playlist.create({
        title: '🌧️ Lofi & Suy Nửa Đêm',
        description: 'Những bản nhạc êm dịu, nhịp beat thư giãn xoa dịu những đêm mất ngủ.',
        coverImageUrl: 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&q=80',
        creatorId: adminUser._id,
        isPublic: true,
        totalTracks: 0
      });
    }

    await PlaylistTrack.deleteMany({ playlistId: lofiPlaylist._id });
    for (let i = 0; i < lofiSongs.length; i++) {
      await PlaylistTrack.create({
        playlistId: lofiPlaylist._id,
        songId: lofiSongs[i]._id,
        order: i
      });
    }
    lofiPlaylist.totalTracks = lofiSongs.length;
    await lofiPlaylist.save();
    console.log(`✅ Đã cập nhật playlist: "${lofiPlaylist.title}" (${lofiSongs.length} bài hát)`);
  }

  // 6. Cập nhật followers cho các nghệ sĩ mới
  const allArtists = await Artist.find({});
  for (const art of allArtists) {
    if (!art.followersCount || art.followersCount < 1000) {
      art.followersCount = Math.floor(Math.random() * 850000) + 25000;
      await art.save();
    }
  }

  console.log('\n🎉 ĐÃ CẬP NHẬT HOÀN TẤT TOÀN BỘ FEED & PLAYLIST CHO 152 BÀI HÁT!');
  await mongoose.disconnect();
}

refresh().catch(err => {
  console.error('Lỗi:', err);
  process.exit(1);
});
