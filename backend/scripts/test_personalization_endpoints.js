const http = require('http');
const mongoose = require('mongoose');
const jwt = require('jsonwebtoken');
const config = require('../src/config/environment');
const { User } = require('../src/models');

function request(options, data = null) {
  return new Promise((resolve, reject) => {
    const headers = { ...(options.headers || {}) };
    let payload = null;
    if (data) {
      payload = typeof data === 'string' ? data : JSON.stringify(data);
      if (!headers['Content-Type']) headers['Content-Type'] = 'application/json';
      headers['Content-Length'] = Buffer.byteLength(payload);
    }
    const opt = { ...options, headers };

    const req = http.request(opt, (res) => {
      let body = '';
      res.on('data', (chunk) => (body += chunk));
      res.on('end', () => {
        try {
          const parsed = JSON.parse(body);
          resolve({ status: res.statusCode, headers: res.headers, data: parsed });
        } catch (e) {
          resolve({ status: res.statusCode, headers: res.headers, raw: body });
        }
      });
    });
    req.on('error', reject);
    if (payload) {
      req.write(payload);
    }
    req.end();
  });
}

async function runTests() {
  console.log('--- Testing Personalization Endpoints ---');

  await mongoose.connect(config.mongodbUri);

  const user = await User.findOne({ isActive: true }).lean();
  if (!user) {
    throw new Error('No active user found in DB');
  }
  const token = jwt.sign({ id: user._id.toString() }, config.jwtSecret, { expiresIn: '7d' });
  const userId = user._id;
  console.log(`[PASS] Using test user: ${user.username || user.displayName} (${userId})`);

  const authHeaders = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  };

  // 2. Test personalOnly=true on /api/v1/playlists/my-playlists
  const plRes = await request({
    hostname: 'localhost',
    port: 5000,
    path: '/api/v1/playlists/my-playlists?personalOnly=true',
    method: 'GET',
    headers: authHeaders
  });

  if (plRes.status !== 200 || !Array.isArray(plRes.data.data)) {
    throw new Error('Failed to get personal playlists: ' + JSON.stringify(plRes.data));
  }

  // All returned playlists must be created by userId
  for (const pl of plRes.data.data) {
    const creatorId = pl.creatorId ? (pl.creatorId._id || pl.creatorId) : null;
    if (creatorId && creatorId.toString() !== userId.toString()) {
      throw new Error(`Found non-personal playlist in personalOnly list: ${pl.title} by ${creatorId}`);
    }
  }
  console.log(`[PASS] personalOnly=true returned ${plRes.data.data.length} personal playlists`);

  // 3. Test followedOnly=true on /api/v1/artists/library
  const artRes = await request({
    hostname: 'localhost',
    port: 5000,
    path: '/api/v1/artists/library?followedOnly=true',
    method: 'GET',
    headers: authHeaders
  });

  if (artRes.status !== 200 || !Array.isArray(artRes.data.data)) {
    throw new Error('Failed to get followed artists: ' + JSON.stringify(artRes.data));
  }

  // All returned artists must have isFollowed === true
  for (const a of artRes.data.data) {
    if (!a.isFollowed) {
      throw new Error(`Found non-followed artist in followedOnly list: ${a.name}`);
    }
  }
  console.log(`[PASS] followedOnly=true returned ${artRes.data.data.length} followed artists`);

  // 4. Test Album Favorites:
  // First get a popular album
  const popAlbumsRes = await request({
    hostname: 'localhost',
    port: 5000,
    path: '/api/v1/albums',
    method: 'GET'
  });

  if (!popAlbumsRes.data || !popAlbumsRes.data.data || popAlbumsRes.data.data.length === 0) {
    throw new Error('No albums found to test favorites');
  }
  const testAlbum = popAlbumsRes.data.data[0];
  const albumId = testAlbum._id;
  console.log(`[INFO] Testing favorites with album: ${testAlbum.title} (${albumId})`);

  // Toggle favorite (ensure it is favorited)
  const fav1 = await request({
    hostname: 'localhost',
    port: 5000,
    path: `/api/v1/albums/${albumId}/favorite`,
    method: 'POST',
    headers: authHeaders
  });

  if (fav1.status !== 200) {
    throw new Error('Toggle favorite failed: ' + JSON.stringify(fav1.data));
  }
  console.log(`[PASS] Toggle favorite response: isFavorite = ${fav1.data.data.isFavorite}`);

  // If it was unfavorited, toggle again to make sure it's true
  if (!fav1.data.data.isFavorite) {
    const fav2 = await request({
      hostname: 'localhost',
      port: 5000,
      path: `/api/v1/albums/${albumId}/favorite`,
      method: 'POST',
      headers: authHeaders
    });
    console.log(`[PASS] Toggled again to ensure favorite: isFavorite = ${fav2.data.data.isFavorite}`);
  }

  // Check /api/v1/albums/favorites
  const myFavAlbumsRes = await request({
    hostname: 'localhost',
    port: 5000,
    path: '/api/v1/albums/favorites',
    method: 'GET',
    headers: authHeaders
  });

  if (myFavAlbumsRes.status !== 200 || !Array.isArray(myFavAlbumsRes.data.data)) {
    throw new Error('Failed to get favorite albums: ' + JSON.stringify(myFavAlbumsRes.data));
  }

  const foundFav = myFavAlbumsRes.data.data.some((a) => a._id.toString() === albumId.toString());
  if (!foundFav) {
    throw new Error(`Album ${albumId} not found in favorite albums list`);
  }
  console.log(`[PASS] Album ${testAlbum.title} successfully found in /api/v1/albums/favorites (total: ${myFavAlbumsRes.data.data.length})`);

  // Check /api/v1/albums/:id returns isFavorite === true
  const albumDetailRes = await request({
    hostname: 'localhost',
    port: 5000,
    path: `/api/v1/albums/${albumId}`,
    method: 'GET',
    headers: authHeaders
  });

  if (!albumDetailRes.data.data || albumDetailRes.data.data.isFavorite !== true) {
    throw new Error(`Expected isFavorite to be true for album ${albumId}`);
  }
  console.log(`[PASS] Album detail correctly reports isFavorite: true`);

  // 5. Test /api/v1/songs/favorites
  const songsFavRes = await request({
    hostname: 'localhost',
    port: 5000,
    path: '/api/v1/songs/favorites',
    method: 'GET',
    headers: authHeaders
  });

  if (songsFavRes.status !== 200 || !songsFavRes.data.data || !Array.isArray(songsFavRes.data.data.songs)) {
    throw new Error('Failed to get favorite songs: ' + JSON.stringify(songsFavRes.data));
  }
  console.log(`[PASS] /api/v1/songs/favorites returned ${songsFavRes.data.data.songs.length} favorite songs`);

  await mongoose.disconnect();
  console.log('\n=== ALL PERSONALIZATION ENDPOINT TESTS PASSED ===\n');
}

runTests().catch((err) => {
  console.error('[FAIL]', err);
  process.exit(1);
});
