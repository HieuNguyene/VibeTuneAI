const mongoose = require('mongoose');

async function test() {
  const titles = [
    'Exit Sign',
    'Từng Quen',
    'Âm Thầm Bên Em',
    'Lạc Trôi',
    'Đừng Làm Trái Tim Anh Đau',
    'Shape of You',
    'Die With A Smile',
    'Kịch Bản Tào',
    'Bình Yên',
    'Vùng An Toàn',
    'Cao Ốc 20',
    'Tại Vì Sao',
    'Trốn Tìm'
  ];

  for (const t of titles) {
    try {
      const url = `https://lrclib.net/api/search?q=${encodeURIComponent(t)}`;
      const res = await fetch(url, { headers: { 'User-Agent': 'VibeTune/1.0' }, signal: AbortSignal.timeout(4000) });
      if (res.ok) {
        const data = await res.json();
        const found = Array.isArray(data) ? data.find(d => d.syncedLyrics) : null;
        console.log(`${t} -> Synced LRC: ${!!found} ${found ? `(${found.trackName} - ${found.artistName})` : 'NO_LRC'}`);
      } else {
        console.log(`${t} -> HTTP ${res.status}`);
      }
    } catch (e) {
      console.log(`${t} -> Error: ${e.message}`);
    }
  }
}

test();
