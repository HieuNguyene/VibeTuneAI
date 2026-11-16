const http = require('http');
const mongoose = require('mongoose');
const jwt = require('jsonwebtoken');
const config = require('../src/config/environment');
const { User, Song, Artist, Playlist, PlaylistTrack, ListeningHistory } = require('../src/models');

const BASE_URL = 'http://127.0.0.1:5000/api/v1';

function request(endpoint, options = {}) {
  const url = new URL(BASE_URL + endpoint);
  const headers = { ...(options.headers || {}) };
  let payload = null;

  if (options.body) {
    payload = typeof options.body === 'string' ? options.body : JSON.stringify(options.body);
    if (!headers['Content-Type']) {
      headers['Content-Type'] = 'application/json';
    }
    headers['Content-Length'] = Buffer.byteLength(payload);
  }

  const opt = {
    method: options.method || 'GET',
    headers
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
          statusCode: res.statusCode,
          headers: res.headers,
          data: json
        });
      });
    });

    req.on('error', reject);

    if (payload) {
      req.write(payload);
    }
    req.end();
  });
}

function assert(condition, message) {
  if (!condition) {
    throw new Error('ASSERTION FAILED: ' + message);
  }
}

async function runRegressionSuite() {
  console.log('================================================================');
  console.log('🚀 BẮT ĐẦU CHẠY BỘ KIỂM THỬ HỒI QUY TOÀN DIỆN (FULL REGRESSION)');
  console.log('================================================================\n');

  await mongoose.connect(config.mongodbUri);
  console.log('✅ Đã kết nối MongoDB thành công.');

  // Find two active users for permission testing + an active admin user
  const users = await User.find({ isActive: true, role: 'user' }).sort({ createdAt: 1 }).limit(2).lean();
  const fallbackUsers = users.length >= 2 ? users : await User.find({ isActive: true }).sort({ createdAt: 1 }).limit(2).lean();
  assert(fallbackUsers.length >= 2, 'Cần ít nhất 2 user trong database');
  const userA = fallbackUsers[0];
  const userB = fallbackUsers[1];
  const adminUser = await User.findOne({ isActive: true, role: 'admin' }).lean();
  assert(adminUser, 'Cần ít nhất 1 tài khoản admin trong database');
  const tokenA = jwt.sign({ id: userA._id.toString() }, config.jwtSecret, { expiresIn: '7d' });
  const tokenB = jwt.sign({ id: userB._id.toString() }, config.jwtSecret, { expiresIn: '7d' });
  const adminToken = jwt.sign({ id: adminUser._id.toString() }, config.jwtSecret, { expiresIn: '7d' });

  let testsPassed = 0;
  let totalTests = 0;

  async function test(name, fn) {
    totalTests++;
    process.stdout.write(`[Test #${totalTests}] ${name} ... `);
    try {
      await fn();
      testsPassed++;
      console.log('✅ ĐẠT');
    } catch (err) {
      console.log('❌ THẤT BẠI:', err.message);
    }
  }

  // 1. Health check
  await test('GET /health phản hồi 200 và healthy', async () => {
    const res = await request('/health');
    assert(res.statusCode === 200, `Expected 200, got ${res.statusCode}`);
    assert(res.data.status === 'healthy', 'Status should be healthy');
  });

  // 2. GET /songs (New REST endpoint)
  await test('GET /songs phân trang và lọc mặc định', async () => {
    const res = await request('/songs?page=1&limit=10');
    assert(res.statusCode === 200, `Expected 200, got ${res.statusCode}`);
    assert(res.data.success === true, 'Success should be true');
    assert(Array.isArray(res.data.data.songs), 'songs should be array');
    assert(res.data.data.songs.length <= 10, 'limit should be respected');
    assert(res.data.data.pagination.total > 0, 'total should be > 0');
  });

  // 3. GET /songs search
  await test('GET /songs?search=Âm Thầm Bên Em', async () => {
    const res = await request('/songs?search=' + encodeURIComponent('Âm Thầm Bên Em'));
    assert(res.statusCode === 200, `Expected 200, got ${res.statusCode}`);
    assert(res.data.data.songs.length > 0, 'Should find at least 1 song');
    assert(res.data.data.songs[0].title.includes('Âm Thầm'), 'Title should match');
  });

  // 4. GET /songs sort by title
  await test('GET /songs?sort=title sắp xếp theo tên A-Z', async () => {
    const res = await request('/songs?sort=title&limit=5');
    assert(res.statusCode === 200, `Expected 200, got ${res.statusCode}`);
    assert(res.data.data.songs.length > 0, 'Should return songs');
  });

  // 5. GET /songs/:id
  const sampleSong = await Song.findOne({ isPublic: true }).lean();
  await test('GET /songs/:id lấy chi tiết bài hát', async () => {
    const res = await request(`/songs/${sampleSong._id}`);
    assert(res.statusCode === 200, `Expected 200, got ${res.statusCode}`);
    assert(res.data.data.title === sampleSong.title, 'Title should match');
  });

  // 6. Streaming Range request
  await test('GET /songs/:id/stream streaming audio Range bytes=0-1024', async () => {
    const res = await request(`/songs/${sampleSong._id}/stream`, {
      headers: { 'Range': 'bytes=0-1024' }
    });
    assert(res.statusCode === 206, `Expected 206 Partial Content, got ${res.statusCode}`);
    assert(res.headers['content-range'], 'Should have Content-Range header');
    assert(res.headers['content-type'] === 'audio/mpeg', 'Content-Type should be audio/mpeg');
  });

  // 7. Artists list
  await test('GET /artists lấy danh sách nghệ sĩ', async () => {
    const res = await request('/artists?limit=5');
    assert(res.statusCode === 200, `Expected 200, got ${res.statusCode}`);
    assert(Array.isArray(res.data.data.artists), 'artists should be array');
  });

  // 8. GET /artists/:id/songs (New REST endpoint)
  const sampleArtist = await Artist.findOne().lean();
  await test('GET /artists/:id/songs lấy bài hát theo nghệ sĩ', async () => {
    const res = await request(`/artists/${sampleArtist._id}/songs`);
    assert(res.statusCode === 200, `Expected 200, got ${res.statusCode}`);
    assert(res.data.success === true, 'Success should be true');
    assert(res.data.data.artist != null, 'Artist info should exist');
  });

  // 9. GET /songs/artist/:id (Legacy endpoint đồng bộ)
  await test('GET /songs/artist/:id hoạt động đồng nhất', async () => {
    const res = await request(`/songs/artist/${sampleArtist._id}`);
    assert(res.statusCode === 200, `Expected 200, got ${res.statusCode}`);
    assert(res.data.success === true, 'Success should be true');
  });

  // 10. Auth Me
  await test('GET /auth/me xác thực Bearer token', async () => {
    const res = await request('/auth/me', {
      headers: { 'Authorization': `Bearer ${tokenA}` }
    });
    if (res.statusCode !== 200) {
      console.log('\n[DEBUG AUTH ME ERROR]', res.statusCode, res.data);
    }
    assert(res.statusCode === 200, `Expected 200, got ${res.statusCode}`);
    assert(res.data.data.user.username === userA.username, 'Username should match');
  });

  // 11. Admin Summary & Stats Alias (with Admin RBAC Bearer JWT)
  await test('GET /admin/summary và GET /admin/stats đồng bộ dữ liệu', async () => {
    const adminHeaders = { 'Authorization': `Bearer ${adminToken}` };
    const unauth = await request('/admin/summary');
    assert(unauth.statusCode === 401, `Unauth /admin/summary should be 401, got ${unauth.statusCode}`);
    const nonAdmin = await request('/admin/summary', { headers: { 'Authorization': `Bearer ${tokenA}` } });
    assert(nonAdmin.statusCode === 403, `Non-admin /admin/summary should be 403, got ${nonAdmin.statusCode}`);
    const res1 = await request('/admin/summary', { headers: adminHeaders });
    const res2 = await request('/admin/stats', { headers: adminHeaders });
    assert(res1.statusCode === 200, 'summary should be 200');
    assert(res2.statusCode === 200, 'stats should be 200');
    assert(res1.data.data.summary.songs === res2.data.data.summary.songs, 'Counts should match');
  });

  // 12. Playlist Full Lifecycle (Create -> Update -> Add Track -> View Multi-artist -> Privacy Isolation -> Delete)
  let createdPlaylistId = null;

  await test('POST /playlists User A tạo playlist mới', async () => {
    const res = await request('/playlists', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${tokenA}` },
      body: {
        title: 'Regression Test Playlist',
        description: 'Mo ta kiem thu hoi quy',
        isPublic: false
      }
    });
    assert(res.statusCode === 201, `Expected 201 Created, got ${res.statusCode}`);
    assert(res.data.data._id, 'Should return created playlist ID');
    createdPlaylistId = res.data.data._id;
  });

  await test('PUT /playlists/:id User A cập nhật tiêu đề và đổi sang Public', async () => {
    const res = await request(`/playlists/${createdPlaylistId}`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${tokenA}` },
      body: {
        title: 'Regression Test Playlist Updated',
        description: 'Mo ta sau cap nhat',
        isPublic: true
      }
    });
    assert(res.statusCode === 200, `Expected 200 OK, got ${res.statusCode}`);
    assert(res.data.data.title === 'Regression Test Playlist Updated', 'Title should update');
    assert(res.data.data.isPublic === true, 'isPublic should be true');
  });

  // Find a multi-artist song to test track population
  const multiArtistSong = await Song.findOne({
    $expr: { $gt: [{ $size: { $ifNull: ["$artistIds", []] } }, 1] }
  }).lean() || sampleSong;

  await test('POST /playlists/:id/tracks User A thêm bài hát vào playlist', async () => {
    const res = await request(`/playlists/${createdPlaylistId}/tracks`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${tokenA}` },
      body: { songId: multiArtistSong._id.toString() }
    });
    assert(res.statusCode === 201, `Expected 201 Created, got ${res.statusCode}`);
  });

  await test('GET /playlists/:id xem chi tiết playlist và bảo toàn đa nghệ sĩ', async () => {
    const res = await request(`/playlists/${createdPlaylistId}`, {
      headers: { 'Authorization': `Bearer ${tokenA}` }
    });
    assert(res.statusCode === 200, `Expected 200 OK, got ${res.statusCode}`);
    assert(res.data.data.tracks.length === 1, 'Should have 1 track');
    const track = res.data.data.tracks[0];
    assert(track.title != null, 'Track title should exist');
    assert(track.artistId != null, 'Primary artist should exist');
  });

  await test('PUT /playlists/:id đổi lại sang Private và User B bị chặn 403 Forbidden', async () => {
    // Switch to private
    await request(`/playlists/${createdPlaylistId}`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${tokenA}` },
      body: { isPublic: false }
    });

    // User B tries to view
    const res = await request(`/playlists/${createdPlaylistId}`, {
      headers: { 'Authorization': `Bearer ${tokenB}` }
    });
    assert(res.statusCode === 403, `Expected 403 Forbidden for User B, got ${res.statusCode}`);
  });

  await test('DELETE /playlists/:id User B cố xóa playlist của User A bị chặn 403', async () => {
    const res = await request(`/playlists/${createdPlaylistId}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${tokenB}` }
    });
    assert(res.statusCode === 403, `Expected 403 Forbidden, got ${res.statusCode}`);
  });

  await test('DELETE /playlists/:id User A xóa playlist thành công và dọn dẹp PlaylistTrack', async () => {
    const res = await request(`/playlists/${createdPlaylistId}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${tokenA}` }
    });
    assert(res.statusCode === 200, `Expected 200 OK, got ${res.statusCode}`);

    // Verify DB
    const checkPlaylist = await Playlist.findById(createdPlaylistId);
    assert(checkPlaylist == null, 'Playlist document should be deleted');
    const remainingTracks = await PlaylistTrack.countDocuments({ playlistId: createdPlaylistId });
    assert(remainingTracks === 0, 'All tracks for this playlist should be deleted');
  });

  // 13. History API
  await test('GET /history lấy lịch sử nghe nhạc của người dùng', async () => {
    const res = await request('/history', {
      headers: { 'Authorization': `Bearer ${tokenA}` }
    });
    assert(res.statusCode === 200, `Expected 200 OK, got ${res.statusCode}`);
    assert(Array.isArray(res.data.data), 'History should be an array');
  });

  console.log('\n================================================================');
  console.log(`📊 TỔNG KẾT: ĐÃ HOÀN THÀNH ${testsPassed}/${totalTests} BÀI KIỂM THỬ (${Math.round((testsPassed/totalTests)*100)}%)`);
  console.log('================================================================\n');

  await mongoose.disconnect();
  process.exit(testsPassed === totalTests ? 0 : 1);
}

runRegressionSuite().catch(err => {
  console.error('Lỗi chạy regression test suite:', err);
  process.exit(1);
});
