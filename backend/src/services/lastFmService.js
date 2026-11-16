const https = require('https');

/**
 * Từ điển phân loại âm nhạc phong phú kết hợp V-Pop, US-UK, K-Pop và ngữ cảnh cảm xúc
 */
const KEYWORD_TAG_RULES = [
  { match: /phonk|funk|drift|gym|workout|motivation|fitness|tập gym|thể hình/i, tags: ['phonk', 'funk', 'gym', 'workout', 'energetic', 'motivation', 'sôi động'] },
  { match: /slowed|reverb|ambient|chillout|speed up/i, tags: ['slowed-reverb', 'chill', 'aesthetic', 'ambient', 'tâm trạng'] },
  { match: /ballad|buồn|chia tay|khóc|nước mắt|cô đơn|lặng|diễm xưa|say tình/i, tags: ['ballad', 'sad', 'melancholic', 'tâm trạng', 'buồn'] },
  { match: /chill|bình yên|phép màu|lo-fi|lofi|thư giãn|nhẹ nhàng|cà phê|ngủ ngon/i, tags: ['chill', 'lo-fi', 'relax', 'calm', 'thư giãn'] },
  { match: /rap|hip hop|hiphop|tụi mỹ|lối sống|tetvoven|mck|andree|wxrdie|bray/i, tags: ['hip-hop', 'rap', 'street', 'swag', 'energetic', 'sôi động'] },
  { match: /edm|bạc phận|bắc bling|lạc trôi|dance|remix|electronic/i, tags: ['edm', 'electronic', 'dance', 'party', 'năng lượng'] },
  { match: /k-pop|kpop|bigbang|taeyang|fantastic|haru/i, tags: ['k-pop', 'kpop', 'korean', 'dance', 'catchy'] },
  { match: /us-uk|usuk|sheeran|fonsi|maroon|nas x|somo|gaga|animals|despacito/i, tags: ['us-uk', 'pop', 'global', 'hit'] },
  { match: /cách mạng|trường sơn|tiền chiến|truyền thống/i, tags: ['cách mạng', 'truyền thống', 'hùng tráng', 'quê hương'] },
  { match: /r&b|soul|từng quen|wren evans/i, tags: ['r&b', 'soul', 'mượt mà', 'groove'] },
  { match: /rock|metal|guitar/i, tags: ['rock', 'energetic', 'guitar'] },
  { match: /acoustic|mộc mạc|guitar acoustic/i, tags: ['acoustic', 'chill', 'mộc mạc'] },
  { match: /sơn tùng|m-tp|come my way|âm thầm bên em/i, tags: ['v-pop', 'top-trending', 'pop', 'hiện đại'] },
  { match: /jack|hồng nhan|sóng gió|lưu niên/i, tags: ['v-pop', 'indie', 'dân gian đương đại', 'tình cảm'] }
];

class LastFmService {
  /**
   * Gọi Last.fm API nếu có LASTFM_API_KEY trong .env
   */
  static async fetchTagsFromApi(artist, track) {
    const apiKey = process.env.LASTFM_API_KEY;
    if (!apiKey) return [];

    return new Promise((resolve) => {
      const url = `https://ws.audioscrobbler.com/2.0/?method=track.gettoptags&artist=${encodeURIComponent(artist)}&track=${encodeURIComponent(track)}&api_key=${apiKey}&format=json`;
      
      const req = https.get(url, { timeout: 4000 }, (res) => {
        let data = '';
        res.on('data', chunk => data += chunk);
        res.on('end', () => {
          try {
            const json = JSON.parse(data);
            const rawTags = json.toptags?.tag || [];
            const tags = rawTags
              .slice(0, 5)
              .map(t => String(t.name).toLowerCase().trim())
              .filter(t => t.length > 1);
            resolve(tags);
          } catch (e) {
            resolve([]);
          }
        });
      });

      req.on('error', () => resolve([]));
      req.on('timeout', () => {
        req.destroy();
        resolve([]);
      });
    });
  }

  /**
   * Trích xuất danh sách tags đa tầng kết hợp phân tích từ khóa và Last.fm API
   */
  static async enrichTags({ title = '', artist = '', genres = [], lyrics = '' }) {
    const combinedText = `${title} ${artist} ${(genres || []).join(' ')} ${lyrics}`.toLowerCase();
    const tagSet = new Set();

    // 1. Phân tích từ điển ngữ nghĩa
    for (const rule of KEYWORD_TAG_RULES) {
      if (rule.match.test(combinedText)) {
        rule.tags.forEach(t => tagSet.add(t));
      }
    }

    // 2. Thêm genres nếu có
    if (Array.isArray(genres)) {
      genres.forEach(g => {
        if (g && typeof g === 'string') {
          tagSet.add(g.toLowerCase().trim());
        }
      });
    }

    // 3. Gọi Last.fm API bổ sung nếu có cấu hình
    try {
      const apiTags = await this.fetchTagsFromApi(artist, title);
      apiTags.forEach(t => tagSet.add(t));
    } catch (e) {
      // Bỏ qua lỗi mạng
    }

    // Nếu chưa có tag nào, kiểm tra tiếng Việt hoặc để pop mặc định
    if (tagSet.size === 0) {
      const hasVietnamese = /[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]/i.test(combinedText);
      tagSet.add(hasVietnamese ? 'v-pop' : 'pop');
    }

    return Array.from(tagSet);
  }
}

module.exports = LastFmService;
