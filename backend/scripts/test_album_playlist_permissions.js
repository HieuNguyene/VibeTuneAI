const http = require('http');
const mongoose = require('mongoose');
const jwt = require('jsonwebtoken');
const config = require('../src/config/environment');
const { User, Song, Album, Playlist, PlaylistTrack } = require('../src/models');

const BASE_URL = 'http://localhost:5000/api/v1';

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
    if (payload) req.write(payload);
    req.end();
  });
}

function assert(condition, message) {
  if (!condition) {
    throw new Error('ASSERTION FAILED: ' + message);
  }
}

async function runTests() {
  console.log('================================================================');
  console.log('🚀 BẮT ĐẦU TEST PHÂN QUYỀN VÀ ĐỒNG BỘ BÀI HÁT ALBUM & PLAYLIST');
  console.log('================================================================\n');

  await mongoose.connect(config.mongodbUri);
  console.log('✅ Đã kết nối MongoDB thành công.');

  // Find two users for user test + admin user
  const users = await User.find({ isActive: true, role: 'user' }).sort({ createdAt: 1 }).limit(2).lean();
  const fallbackUsers = users.length >= 2 ? users : await User.find({ isActive: true }).sort({ createdAt: 1 }).limit(2).lean();
  assert(fallbackUsers.length >= 2, 'Cần ít nhất 2 user');
  const userA = fallbackUsers[0];
  const userB = fallbackUsers[1];
  const adminUser = await User.findOne({ isActive: true, role: 'admin' }).lean();
  assert(adminUser, 'Cần ít nhất 1 admin');
  const tokenA = jwt.sign({ id: userA._id.toString() }, config.jwtSecret, { expiresIn: '7d' });
  const tokenB = jwt.sign({ id: userB._id.toString() }, config.jwtSecret, { expiresIn: '7d' });
  const adminToken = jwt.sign({ id: adminUser._id.toString() }, config.jwtSecret, { expiresIn: '7d' });
  const adminHeaders = { 'Authorization': `Bearer ${adminToken}` };

  // 1. Admin Options Check
  console.log('1. Kiểm tra Admin Options (Danh mục bài hát sẵn sàng cho Web Admin)...');
  const optionsRes = await request('/admin/options', { headers: adminHeaders });
  assert(optionsRes.statusCode === 200, 'Admin options response status 200');
  assert(Array.isArray(optionsRes.data.data.songs), 'Options songs is array');
  assert(optionsRes.data.data.songs.length > 0, 'Catalog songs > 0');
  console.log(`   ✅ Admin options tải thành công ${optionsRes.data.data.songs.length} bài hát trong kho.`);

  // 2. Select test album and song (save original albumId to restore after test)
  const album = await Album.findOne().lean();
  assert(album, 'Cần ít nhất 1 album trong DB');
  const initialTrackCount = album.totalTracks || 0;
  const testSong = optionsRes.data.data.songs.find((s) => String(s.albumId || '') !== String(album._id)) || optionsRes.data.data.songs[0];
  const originalSongAlbumId = testSong.albumId ? String(testSong.albumId) : null;
  console.log(`2. Album kiểm thử: "${album.title}" (_id: ${album._id}, totalTracks ban đầu: ${initialTrackCount})`);
  console.log(`   Bài hát kiểm thử: "${testSong.title}" (_id: ${testSong._id})`);

  // 3. Admin: Get Album Tracks
  console.log('3. Admin: Lấy danh sách bài hát của Album...');
  const getAlbumTracksRes = await request(`/admin/albums/${album._id}/tracks`, { headers: adminHeaders });
  assert(getAlbumTracksRes.statusCode === 200, 'Get album tracks 200');
  assert(getAlbumTracksRes.data.success === true, 'Get album tracks success true');
  console.log(`   ✅ Đã tải ${getAlbumTracksRes.data.data.tracks.length} bài hát của album qua Admin API.`);

  // 4. Admin: Add Song to Album & Sync totalTracks
  console.log('4. Admin: Thêm bài hát vào Album và đồng bộ tổng số bài...');
  const addSongAlbumRes = await request(`/admin/albums/${album._id}/tracks`, {
    method: 'POST',
    headers: adminHeaders,
    body: { songId: testSong._id }
  });
  assert(addSongAlbumRes.statusCode === 200, 'Add song to album 200');
  assert(addSongAlbumRes.data.success === true, 'Add song success');
  const updatedAlbum = await Album.findById(album._id).lean();
  console.log(`   ✅ Thêm thành công! totalTracks mới của Album: ${updatedAlbum.totalTracks}`);

  // Check Song now has albumId
  const verifiedSong = await Song.findById(testSong._id).lean();
  assert(verifiedSong.albumId.toString() === album._id.toString(), 'Song.albumId matched album._id');
  console.log(`   ✅ Đã đồng bộ Song.albumId = ${album._id}`);

  // 5. Admin: Remove Song from Album & Sync totalTracks
  console.log('5. Admin: Xóa bài hát khỏi Album và đồng bộ tổng số bài...');
  const removeSongAlbumRes = await request(`/admin/albums/${album._id}/tracks/${testSong._id}`, {
    method: 'DELETE',
    headers: adminHeaders
  });
  assert(removeSongAlbumRes.statusCode === 200, 'Remove song from album 200');
  const albumAfterRemove = await Album.findById(album._id).lean();
  console.log(`   ✅ Gỡ thành công! totalTracks mới của Album: ${albumAfterRemove.totalTracks}`);

  const songAfterRemove = await Song.findById(testSong._id).lean();
  assert(!songAfterRemove.albumId, 'Song.albumId should now be null');
  console.log(`   ✅ Song.albumId đã được đưa về null.`);

  if (originalSongAlbumId) {
    await request(`/admin/albums/${originalSongAlbumId}/tracks`, {
      method: 'POST',
      headers: adminHeaders,
      body: { songId: testSong._id }
    });
    console.log(`   ↩️ Đã khôi phục lại album gốc (${originalSongAlbumId}) cho bài hát kiểm thử.`);
  }

  // 6. User: Create Private Custom Playlist
  console.log('6. User A: Tạo Playlist cá nhân riêng tư...');
  const createPlRes = await request('/playlists', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${tokenA}` },
    body: {
      title: 'Playlist Của User A',
      description: 'Chỉ mình User A và Admin xem được',
      isPublic: false
    }
  });
  assert(createPlRes.statusCode === 201, 'Create playlist 201');
  const playlistA = createPlRes.data.data;
  assert(playlistA.isPublic === false, 'Playlist is private');
  console.log(`   ✅ User A đã tạo Playlist: "${playlistA.title}" (_id: ${playlistA._id})`);

  // 7. User A: Add Song to Playlist
  console.log('7. User A: Thêm bài hát vào Playlist của mình...');
  const userAddSongRes = await request(`/playlists/${playlistA._id}/tracks`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${tokenA}` },
    body: { songId: testSong._id }
  });
  assert(userAddSongRes.statusCode === 201 || userAddSongRes.statusCode === 200, 'User add song status 200 or 201');
  console.log(`   ✅ User A đã thêm bài hát vào playlist thành công.`);

  // 8. User B CANNOT see User A's private playlist (403 Forbidden)
  console.log('8. Kiểm tra phân quyền: User B không thể truy cập Playlist riêng tư của User A...');
  const userBGetRes = await request(`/playlists/${playlistA._id}`, {
    headers: { 'Authorization': `Bearer ${tokenB}` }
  });
  assert(userBGetRes.statusCode === 403, 'User B must get 403 Forbidden for private playlist');
  console.log(`   ✅ Phân quyền bảo mật: User B nhận mã lỗi 403 Forbidden chính xác.`);

  // 9. Admin CAN view & manage User A's private playlist
  console.log('9. Web Admin: Xem danh sách bài hát trong Playlist riêng tư của User A...');
  const adminGetPlRes = await request(`/admin/playlists/${playlistA._id}/tracks`, { headers: adminHeaders });
  assert(adminGetPlRes.statusCode === 200, 'Admin can view any playlist tracks 200');
  assert(adminGetPlRes.data.data.tracks.length === 1, 'Admin sees 1 track');
  console.log(`   ✅ Admin xem được Playlist của User A (số bài: ${adminGetPlRes.data.data.tracks.length}).`);

  // 10. Admin CAN add a song to user's playlist
  const song2 = optionsRes.data.data.songs[1] || optionsRes.data.data.songs[0];
  console.log(`10. Web Admin: Thêm bài hát "${song2.title}" vào Playlist của User A...`);
  const adminAddSongRes = await request(`/admin/playlists/${playlistA._id}/tracks`, {
    method: 'POST',
    headers: adminHeaders,
    body: { songId: song2._id }
  });
  assert(adminAddSongRes.statusCode === 200, 'Admin add song to playlist 200');
  assert(adminAddSongRes.data.data.playlist.totalTracks === 2, 'Total tracks incremented to 2');
  console.log(`   ✅ Admin thêm bài hát thành công, totalTracks cập nhật lên ${adminAddSongRes.data.data.playlist.totalTracks}.`);

  // 11. Admin CAN remove a song from user's playlist
  console.log(`11. Web Admin: Gỡ bài hát "${song2.title}" khỏi Playlist của User A...`);
  const adminRemoveSongRes = await request(`/admin/playlists/${playlistA._id}/tracks/${song2._id}`, {
    method: 'DELETE',
    headers: adminHeaders
  });
  assert(adminRemoveSongRes.statusCode === 200, 'Admin remove song from playlist 200');
  assert(adminRemoveSongRes.data.data.playlist.totalTracks === 1, 'Total tracks decremented to 1');
  console.log(`   ✅ Admin gỡ bài hát thành công, totalTracks cập nhật về ${adminRemoveSongRes.data.data.playlist.totalTracks}.`);

  // 12. User A: Remove Track from Playlist
  console.log('12. User A: Gỡ bài hát khỏi Playlist của mình...');
  const userRemoveSongRes = await request(`/playlists/${playlistA._id}/tracks/${testSong._id}`, {
    method: 'DELETE',
    headers: { 'Authorization': `Bearer ${tokenA}` }
  });
  assert(userRemoveSongRes.statusCode === 200, 'User remove song 200');
  console.log(`   ✅ User A gỡ bài hát khỏi playlist thành công.`);

  // 13. User A: Update Playlist info
  console.log('13. User A: Cập nhật thông tin Playlist (Tiêu đề, mô tả, chuyển sang công khai)...');
  const userUpdateRes = await request(`/playlists/${playlistA._id}`, {
    method: 'PUT',
    headers: { 'Authorization': `Bearer ${tokenA}` },
    body: {
      title: 'Playlist Của User A (Đã Sửa)',
      description: 'Đã chuyển thành công khai cho mọi người',
      isPublic: true
    }
  });
  assert(userUpdateRes.statusCode === 200, 'User update playlist 200');
  assert(userUpdateRes.data.data.isPublic === true, 'Playlist is now public');
  console.log(`   ✅ Cập nhật thành công: isPublic = ${userUpdateRes.data.data.isPublic}, title = "${userUpdateRes.data.data.title}"`);

  // 14. Now User B CAN view it because it is public
  console.log('14. User B xem playlist sau khi đã chuyển sang công khai...');
  const userBGetPublicRes = await request(`/playlists/${playlistA._id}`, {
    headers: { 'Authorization': `Bearer ${tokenB}` }
  });
  assert(userBGetPublicRes.statusCode === 200, 'User B can now view public playlist');
  console.log(`   ✅ User B truy cập thành công sau khi playlist được công khai.`);

  // 15. User A: Delete Playlist
  console.log('15. User A: Xóa Playlist của mình...');
  const userDeleteRes = await request(`/playlists/${playlistA._id}`, {
    method: 'DELETE',
    headers: { 'Authorization': `Bearer ${tokenA}` }
  });
  assert(userDeleteRes.statusCode === 200, 'User delete playlist 200');
  console.log(`   ✅ User A đã xóa Playlist thành công.`);

  // 16. Verify public album routes are strictly read-only for users (no POST/PUT/DELETE)
  console.log('16. Kiểm tra bảo mật: Endpoint Album phía người dùng không cho phép can thiệp thêm/sửa/xóa...');
  const userPostAlbumRes = await request(`/albums`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${tokenA}` },
    body: { title: 'Hacked Album' }
  });
  assert(userPostAlbumRes.statusCode === 404, 'User POST /albums should not exist (404)');
  console.log(`   ✅ POST /albums trả về 404 Not Found (Không tồn tại endpoint cho người dùng).`);

  console.log('\n================================================================');
  console.log('🎉 TẤT CẢ 16/16 BÀI KIỂM THỬ PHÂN QUYỀN VÀ ĐỒNG BỘ ĐÃ ĐẠT 100%!');
  console.log('================================================================');

  await mongoose.disconnect();
  process.exit(0);
}

runTests().catch(err => {
  console.error('\n❌ TEST RUN FAILED:', err);
  process.exit(1);
});
