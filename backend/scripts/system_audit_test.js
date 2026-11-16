const http = require('http');
const mongoose = require('mongoose');
const path = require('path');
const config = require('../src/config/environment');
const { User, Song, Artist, Album, Playlist, PlaylistTrack, ListeningHistory } = require('../src/models');
const jwt = require('jsonwebtoken');

const BASE_URL = 'http://127.0.0.1:5000/api/v1';

async function request(endpoint, options = {}) {
  const url = new URL(BASE_URL + endpoint);
  const opt = {
    method: options.method || 'GET',
    headers: options.headers || {}
  };

  return new Promise((resolve, reject) => {
    const req = http.request(url, opt, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        let json = null;
        try {
          json = JSON.parse(data);
        } catch (e) {
          json = data;
        }
        resolve({
          status: res.statusMessage,
          statusCode: res.statusCode,
          headers: res.headers,
          data: json
        });
      });
    });

    req.on('error', reject);

    if (options.body) {
      const payload = typeof options.body === 'string' ? options.body : JSON.stringify(options.body);
      if (!opt.headers['Content-Type']) {
        opt.headers['Content-Type'] = 'application/json';
      }
      req.write(payload);
    }
    req.end();
  });
}

async function runAudit() {
  console.log('====================================================');
  console.log('🔍 KIỂM THỬ TOÀN DIỆN HỆ THỐNG (SYSTEM COMPREHENSIVE AUDIT)');
  console.log('====================================================\n');

  await mongoose.connect(config.mongodbUri);
  console.log('✅ 1. Kết nối MongoDB thành công');

  const issues = [];
  const optimizations = [];

  // 1. Database Integrity Checks
  console.log('\n--- 1. KIỂM TRA TÍNH TOÀN VẸN CƠ SỞ DỮ LIỆU ---');
  const songCount = await Song.countDocuments();
  const artistCount = await Artist.countDocuments();
  const albumCount = await Album.countDocuments();
  const playlistCount = await Playlist.countDocuments();
  const historyCount = await ListeningHistory.countDocuments();
  const userCount = await User.countDocuments();

  console.log(`- Songs: ${songCount}, Artists: ${artistCount}, Albums: ${albumCount}, Playlists: ${playlistCount}, Users: ${userCount}, Histories: ${historyCount}`);

  // Check Songs with missing/invalid artists
  const allSongs = await Song.find().lean();
  let invalidArtistSongs = 0;
  let singleArtistOnlyCount = 0;
  let multiArtistCount = 0;

  for (const s of allSongs) {
    const extraCount = (Array.isArray(s.featuredArtistIds) ? s.featuredArtistIds.length : 0)
      + (Array.isArray(s.artistIds) ? s.artistIds.length : 0)
      + (Array.isArray(s.composerIds) ? s.composerIds.length : 0);
    if (!s.artistId && extraCount === 0) {
      invalidArtistSongs++;
    } else if (extraCount > 1 || (s.artistId && (Array.isArray(s.featuredArtistIds) && s.featuredArtistIds.length > 0))) {
      multiArtistCount++;
    } else {
      singleArtistOnlyCount++;
    }
  }

  if (invalidArtistSongs > 0) {
    issues.push(`Có ${invalidArtistSongs} bài hát không có bất kỳ ca sĩ/nghệ sĩ nào liên kết.`);
  }
  console.log(`- Phân tích nghệ sĩ bài hát: ${multiArtistCount} bài đa ca sĩ/nhạc sĩ, ${singleArtistOnlyCount} bài đơn nghệ sĩ.`);

  // Check Playlists with orphan creator
  const allPlaylists = await Playlist.find().lean();
  let orphanPlaylists = 0;
  for (const pl of allPlaylists) {
    if (pl.creatorId) {
      const u = await User.findById(pl.creatorId).lean();
      if (!u) orphanPlaylists++;
    }
  }
  if (orphanPlaylists > 0) {
    issues.push(`Có ${orphanPlaylists} playlist có creatorId không tồn tại trong User collection.`);
  }

  // 2. Health & Base APIs
  console.log('\n--- 2. KIỂM THỬ API CƠ BẢN & HEALTH CHECK ---');
  const healthRes = await request('/health');
  if (healthRes.statusCode === 200) {
    console.log('✅ GET /health: 200 OK');
  } else {
    issues.push(`GET /health trả về mã ${healthRes.statusCode}`);
  }

  // 3. Songs API
  console.log('\n--- 3. KIỂM THỬ API SONGS ---');
  const songsRes = await request('/songs?page=1&limit=10');
  if (songsRes.statusCode === 200 && songsRes.data?.data) {
    console.log(`✅ GET /songs: 200 OK (Trả về ${songsRes.data.data.songs?.length || 0} bài)`);
  } else {
    issues.push(`GET /songs lỗi: ${songsRes.statusCode}`);
  }

  // Test Song Detail with multi-artist
  if (allSongs.length > 0) {
    const sampleSong = allSongs[0];
    const songDetailRes = await request(`/songs/${sampleSong._id}`);
    if (songDetailRes.statusCode === 200) {
      console.log(`✅ GET /songs/:id: 200 OK (Chi tiết bài: ${sampleSong.title})`);
    } else {
      issues.push(`GET /songs/${sampleSong._id} trả về mã ${songDetailRes.statusCode}`);
    }

    // Audio stream range test
    const streamRes = await request(`/songs/${sampleSong._id}/stream`, {
      headers: { 'Range': 'bytes=0-1024' }
    });
    if (streamRes.statusCode === 206 || streamRes.statusCode === 200) {
      console.log(`✅ GET /songs/:id/stream: ${streamRes.statusCode} Streaming OK`);
    } else {
      issues.push(`Streaming bài hát trả về mã ${streamRes.statusCode}`);
    }
  }

  // 4. Artists API & Multi-artist consistency
  console.log('\n--- 4. KIỂM THỬ API ARTISTS & ĐA NGHỆ SĨ ---');
  const artistsRes = await request('/artists?page=1&limit=5');
  if (artistsRes.statusCode === 200) {
    console.log(`✅ GET /artists: 200 OK`);
  } else {
    issues.push(`GET /artists trả về ${artistsRes.statusCode}`);
  }

  // Test artist songs query
  const sampleArtist = await Artist.findOne().lean();
  if (sampleArtist) {
    const artistSongsRes = await request(`/artists/${sampleArtist._id}/songs`);
    if (artistSongsRes.statusCode === 200) {
      console.log(`✅ GET /artists/:id/songs: 200 OK (Nghệ sĩ: ${sampleArtist.name})`);
    } else {
      issues.push(`GET /artists/:id/songs trả về ${artistSongsRes.statusCode}`);
    }
  }

  // 5. Auth & User Profile API
  console.log('\n--- 5. KIỂM THỬ AUTH & TOKEN ---');
  const testUser = await User.findOne().lean();
  let token = '';
  if (testUser) {
    token = jwt.sign({ id: testUser._id }, config.jwtSecret, { expiresIn: '7d' });
    const meRes = await request('/auth/me', {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (meRes.statusCode === 200) {
      console.log(`✅ GET /auth/me: 200 OK (User: ${testUser.username})`);
    } else {
      issues.push(`GET /auth/me trả về ${meRes.statusCode}`);
    }
  }

  // 6. Playlists API (Public vs Private)
  console.log('\n--- 6. KIỂM THỬ PLAYLISTS & QUYỀN RIÊNG TƯ ---');
  const myPlaylistsRes = await request('/playlists/my-playlists', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (myPlaylistsRes.statusCode === 200) {
    console.log(`✅ GET /playlists/my-playlists: 200 OK (${myPlaylistsRes.data?.data?.length || 0} playlists)`);
  } else {
    issues.push(`GET /playlists/my-playlists trả về ${myPlaylistsRes.statusCode}`);
  }

  // 7. Listening History API
  console.log('\n--- 7. KIỂM THỬ LỊCH SỬ NGHE NHẠC ---');
  const historyRes = await request('/history', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  if (historyRes.statusCode === 200) {
    console.log(`✅ GET /history: 200 OK (${historyRes.data?.data?.length || 0} bản ghi)`);
  } else {
    issues.push(`GET /history trả về ${historyRes.statusCode}`);
  }

  // 8. Admin APIs
  console.log('\n--- 8. KIỂM THỬ WEB ADMIN APIS ---');
  const adminUser = await User.findOne({ isActive: true, role: 'admin' }).lean();
  const adminToken = adminUser ? jwt.sign({ id: adminUser._id }, config.jwtSecret, { expiresIn: '7d' }) : '';
  const adminHeaders = adminToken ? { 'Authorization': `Bearer ${adminToken}` } : {};

  const adminStatsRes = await request('/admin/stats', { headers: adminHeaders });
  if (adminStatsRes.statusCode === 200) {
    console.log(`✅ GET /admin/stats: 200 OK`);
  } else {
    issues.push(`GET /admin/stats trả về ${adminStatsRes.statusCode}`);
  }

  const adminPlaylistsRes = await request('/admin/data/playlists', { headers: adminHeaders });
  if (adminPlaylistsRes.statusCode === 200) {
    console.log(`✅ GET /admin/data/playlists: 200 OK (${adminPlaylistsRes.data?.data?.count || 0} playlists)`);
  } else {
    issues.push(`GET /admin/data/playlists trả về ${adminPlaylistsRes.statusCode}`);
  }

  // Summary
  console.log('\n====================================================');
  console.log('📊 KẾT QUẢ RÀ SOÁT HỆ THỐNG');
  console.log('====================================================');
  if (issues.length === 0) {
    console.log('🎉 Không phát hiện lỗi nghiêm trọng trong các luồng chính!');
  } else {
    console.log(`⚠️ Phát hiện ${issues.length} vấn đề:`);
    issues.forEach((iss, idx) => console.log(`  ${idx + 1}. ${iss}`));
  }

  await mongoose.disconnect();
}

runAudit().catch(err => {
  console.error('Audit error:', err);
  process.exit(1);
});
