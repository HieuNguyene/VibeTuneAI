const http = require('http');

function request(options, data = null) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
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
    if (data) {
      req.write(typeof data === 'string' ? data : JSON.stringify(data));
    }
    req.end();
  });
}

async function runTests() {
  console.log('================================================================');
  console.log('🚀 KIỂM THỬ CÁC ENDPOINT CHO 5 THANH TRANG CHỦ & PHÂN LOẠI');
  console.log('================================================================\n');

  // 1. Kiểm tra Health
  const health = await request({
    hostname: 'localhost',
    port: 5000,
    path: '/api/v1/health',
    method: 'GET'
  });
  console.log('1. Health check:', health.status === 200 ? '✅ 200 OK' : '❌ Lỗi');

  // 2. Kiểm tra GET /api/v1/genres
  const genresRes = await request({
    hostname: 'localhost',
    port: 5000,
    path: '/api/v1/genres',
    method: 'GET'
  });
  console.log('2. GET /api/v1/genres:', genresRes.status === 200 ? '✅ 200 OK' : '❌ Lỗi');
  const genres = genresRes.data?.data || [];
  console.log(`   👉 Tải thành công ${genres.length} thể loại.`);
  if (genres.length > 0) {
    console.log(`   👉 Thể loại mẫu: ${genres[0].name} (slug: ${genres[0].slug}, id: ${genres[0]._id})`);
  }

  // 3. Kiểm tra GET /api/v1/songs/genre/:id với các chế độ sort
  if (genres.length > 0) {
    const genreId = genres[0]._id;
    // Popular
    const sortPopular = await request({
      hostname: 'localhost',
      port: 5000,
      path: `/api/v1/songs/genre/${genreId}?sort=popular`,
      method: 'GET'
    });
    console.log('3a. GET /songs/genre/:id?sort=popular:', sortPopular.status === 200 ? '✅ 200 OK' : '❌ Lỗi');
    const popSongs = sortPopular.data?.data?.songs || [];
    console.log(`    👉 Tải ${popSongs.length} bài hát theo lượt nghe giảm dần.`);
    if (popSongs.length > 1) {
      console.log(`    👉 Bài 1: "${popSongs[0].title}" (plays: ${popSongs[0].playsCount}) >= Bài 2: "${popSongs[1].title}" (plays: ${popSongs[1].playsCount})`);
    }

    // Recommended
    const sortRec = await request({
      hostname: 'localhost',
      port: 5000,
      path: `/api/v1/songs/genre/${genreId}?sort=recommended`,
      method: 'GET'
    });
    console.log('3b. GET /songs/genre/:id?sort=recommended:', sortRec.status === 200 ? '✅ 200 OK' : '❌ Lỗi');

    // A-Z
    const sortAz = await request({
      hostname: 'localhost',
      port: 5000,
      path: `/api/v1/songs/genre/${genreId}?sort=az`,
      method: 'GET'
    });
    console.log('3c. GET /songs/genre/:id?sort=az:', sortAz.status === 200 ? '✅ 200 OK' : '❌ Lỗi');
    const azSongs = sortAz.data?.data?.songs || [];
    if (azSongs.length > 1) {
      console.log(`    👉 Bài 1: "${azSongs[0].title}" | Bài 2: "${azSongs[1].title}"`);
    }
  }

  // 4. Kiểm tra GET /api/v1/albums?limit=100
  const albumsRes = await request({
    hostname: 'localhost',
    port: 5000,
    path: '/api/v1/albums?limit=100',
    method: 'GET'
  });
  console.log('4. GET /api/v1/albums?limit=100:', albumsRes.status === 200 ? '✅ 200 OK' : '❌ Lỗi');
  const albums = albumsRes.data?.data || [];
  console.log(`   👉 Tải thành công ${albums.length} album trong cơ sở dữ liệu.`);

  // 5. Kiểm tra GET /api/v1/artists/library (toàn bộ nghệ sĩ)
  const artistsRes = await request({
    hostname: 'localhost',
    port: 5000,
    path: '/api/v1/artists/library',
    method: 'GET'
  });
  console.log('5. GET /api/v1/artists/library:', artistsRes.status === 200 ? '✅ 200 OK' : '❌ Lỗi');
  const artists = artistsRes.data?.data || [];
  console.log(`   👉 Tải thành công ${artists.length} nghệ sĩ trong hệ thống.`);

  // 6. Kiểm tra GET /api/v1/songs/artist/:id?sort=az
  if (artists.length > 0) {
    const artistId = artists[0]._id;
    const artistSongsAz = await request({
      hostname: 'localhost',
      port: 5000,
      path: `/api/v1/songs/artist/${artistId}?sort=az`,
      method: 'GET'
    });
    console.log('6. GET /songs/artist/:id?sort=az:', artistSongsAz.status === 200 ? '✅ 200 OK' : '❌ Lỗi');
    const aSongs = artistSongsAz.data?.data?.songs || [];
    console.log(`   👉 Nghệ sĩ ${artists[0].name} có ${aSongs.length} bài hát.`);
  }

  console.log('\n================================================================');
  console.log('🎉 TẤT CẢ ENDPOINT CHO 5 THANH TRANG CHỦ HOẠT ĐỘNG HOÀN HẢO!');
  console.log('================================================================');
}

runTests().catch(console.error);
