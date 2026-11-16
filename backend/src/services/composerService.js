const mongoose = require('mongoose');
const env = require('../config/environment');

// Hàm tạo slug tiếng Việt chuẩn
function slugify(text) {
  if (!text) return '';
  return text
    .toString()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

// Bảng tra cứu nhạc sĩ / tác giả (Composer Master Dictionary)
const COMPOSER_DICTIONARY = {
  // Sơn Tùng M-TP (Tự sáng tác)
  'am tham ben em': 'Sơn Tùng M-TP',
  'lac troi': 'Sơn Tùng M-TP',
  'come my way': 'Sơn Tùng M-TP',
  'bac bling': 'Sơn Tùng M-TP',
  'dung lam trai tim anh dau': 'Sơn Tùng M-TP',
  'chung ta cua hien tai': 'Sơn Tùng M-TP',
  'chung ta khong thuoc ve nhau': 'Sơn Tùng M-TP',
  'noi nay co anh': 'Sơn Tùng M-TP',
  'con mua ngang qua': 'Sơn Tùng M-TP',
  'em cua ngay hom qua': 'Sơn Tùng M-TP',
  'chay ngay di': 'Sơn Tùng M-TP',
  'muon roi ma sao con': 'Sơn Tùng M-TP',
  'som muon thi x noi nay co anh': 'Sơn Tùng M-TP',
  'hoa cai mai toc': 'Thông Đạt',

  // Bùi Anh Tuấn
  'noi tinh yeu bat dau': 'Tiến Minh',
  'noi tinh yeu ket thuc': 'Tiến Minh',
  'hen mot mai': 'Nguyễn Duy Anh',

  // Tuấn Hưng
  'tim lai bau troi': 'Khắc Việt',
  'nam lay tay anh': 'Anh Tú (Tú Dưa)',
  'cau vong khuyet': 'Minh Khang',
  'doc thoai': 'Nguyễn Hồng Thuận',

  // Mỹ Tâm
  'dau chi rieng em': 'Khắc Hưng',
  'nhu mot giac mo': 'Mỹ Tâm',
  'dung cung thanh sai': 'Khắc Hưng',
  'uoc gi': 'Võ Thiện Thanh',
  'nguoi hay quen em di': 'Khắc Hưng',
  'hoa mi toc nau': 'Quốc Bảo',

  // Jack - J97 (Tự sáng tác)
  'song gio': 'Jack - J97',
  'bac phan': 'Jack - J97',
  'hong nhan': 'Jack - J97',
  'hoa hai duong': 'Jack - J97',
  'cuoi cung thi': 'Jack - J97',
  'nguoi dung': 'Jack - J97',
  'luu nien': 'Jack - J97',
  'chang hop nhau dau': 'Jack - J97',
  'ngay mai em di mat': 'Đạt G',

  // Vũ. (Hoàng tử Indie tự sáng tác)
  'binh yen': 'Vũ.',
  'chang the tim duoc em': 'Vũ.',
  'con gi dep hon': 'Vũ.',
  'la lung': 'Vũ.',
  'buoc qua mua co don': 'Vũ.',
  'buoc qua nhau': 'Vũ.',
  'diem xua': 'Trịnh Công Sơn',

  // Phan Mạnh Quỳnh
  'sau loi tu khuoc': 'Phan Mạnh Quỳnh',
  'co chang trai viet len cay': 'Phan Mạnh Quỳnh',
  'vo nguoi ta': 'Phan Mạnh Quỳnh',
  'ngay chua giong bao': 'Phan Mạnh Quỳnh',
  'xuan thi': 'Phan Mạnh Quỳnh',

  // Hà Anh Tuấn
  'thang tu la loi noi doi cua em': 'Phạm Toàn Thắng',
  'chuyen cua mua dong': 'Phạm Toàn Thắng',

  // Tùng Dương & Nhạc tiền chiến / Cách mạng
  'que nha': 'Trần Tiến',
  'duong chung ta di': 'Huy Du (lời Xuân Sách)',
  'me yeu con': 'Nguyễn Văn Tý',
  'chiec khan pieu': 'Doãn Nho',
  'ha noi niem tin va hy vong': 'Phan Nhân',
  'chao em co gai lam hong': 'Ánh Dương',
  'mai dinh lang bien': 'Nguyễn Cường',
  'gio danh do dua': 'Dân ca Bắc Bộ',
  'truong son dong truong son tay': 'Hoàng Hiệp (thơ Phạm Tiến Duật)',

  // Quang Lê & Bolero trữ tình
  'ap vo cay dan': 'Hoa Linh Bảo (Lê Mộng Bảo)',
  'dap vo cay dan': 'Hoa Linh Bảo (Lê Mộng Bảo)',
  'suong trang mien que ngoai': 'Đinh Miên Vũ',
  'tuong tu nang ca si': 'Kông Thanh Bích',
  'dap mo cuoc tinh': 'Vũ Thanh',
  'dap mo cuoc tinh - pbn 126': 'Vũ Thanh',
  'xuan nay con ve me o dau': 'Nhật Ngân',

  // Phan Duy Anh
  'vo cung': 'Võ Hoài Phúc',
  'vo cung (vi anh thuong em)': 'Võ Hoài Phúc',
  'vo cung ( vi anh thuong em )': 'Võ Hoài Phúc',
  'tung yeu': 'Thanh Hưng',

  // Noo Phước Thịnh
  'cause i love you': 'Đỗ Hiếu',
  'cham khe tim anh mot chut thoi': 'Tăng Nhật Tuệ',

  // H2K, KProx (Bolero Lofi)
  'hoi vo ngoai thanh': 'Giao Tiên',
  'vo ngua tren doi co non': 'Giao Tiên',
  'sau tim thiep hong': 'Minh Kỳ, Hoài Linh',
  'sau tim hiep hong': 'Minh Kỳ, Hoài Linh',
  'go cua trai tim': 'Vinh Sử',
  'chuyen hoa sim': 'Anh Bằng (thơ Hữu Loan)',
  'chim trang mo coi': 'Minh Vy, Hồng Xương Long',

  // Pop / Indie / Rap Việt
  'ken ca chon canh': 'Tuấn Cry, Masew',
  'bat tinh yeu len': 'Tăng Duy Tân, Hiếu B',
  'ben tren tang lau': 'Tăng Duy Tân',
  'em dong y (i do)': 'Khắc Hưng, 911',
  'it nhung dai lau': 'NamDuc',
  'thanh xuan': 'Da LAB',
  'mot nha': 'Da LAB',
  'nen va hoa': 'Rhymastic',
  'yeu 5': 'Rhymastic',
  'gia nhu': 'SOOBIN',
  'phia sau mot co gai': 'Tiên Cookie',
  'di dau de thay hoa bay': 'Hoàng Dũng',
  'nang tho': 'Hoàng Dũng',
  'tim em': 'Hoàng Dũng',
  'tron tim': 'Đen Vâu',
  'ke say tinh': 'Hoàng Dũng',
  'ke say tinh 2': 'Hoàng Dũng',
  'tai sinh': 'Tăng Duy Tân',
  'cay': 'Khắc Hưng',
  '2 5': 'Táo',
  'mo (remix)': 'Vũ Cát Tường',
  'mo': 'Vũ Cát Tường',
  'duoc khong?': 'Binz',
  'duoc khong': 'Binz',
  'cho anh ve': 'B Ray, HIEUTHUHAI, Anh Trai Say Hi',
  'vung an toan': 'B Ray',
  'cao oc 20': 'B Ray',
  'tai vi sao': 'MCK',
  'tung quen': 'Wren Evans',
  'kich ban tao': 'MCK',
  'phep mau': 'Wren Evans',
  'neu nhu ta chang con': 'MCK, A$AP Ướt Mi',
  'buon hay vui': 'VSOUL, MCK, Obito, Ronboogz',
  'mong yu': 'AMEE, MCK',
  'exit sign': 'HIEUTHUHAI',
  'em iu': 'Andree Right Hand, Wxrdie',
  'choi nhu tui my': 'Andree Right Hand',
  'doi tu the': 'Andree Right Hand',
  'loi song': 'Andree Right Hand',
  'tet vo van': 'Andree Right Hand',
  'nghiem tong': 'Tháp Drill Tự Do',
  'nghiem tong (prod. gaz)': 'Tháp Drill Tự Do',
  'hen em o lan yeu thu 2': 'Đặng Tuấn Vũ',
  'le luu ly': 'Drum7, DT Tập Rap',
  'ngay mai em di': 'Thái Thịnh',
  'co em': 'Madihu, Low G',
  'yeu mot nguoi co le': 'Only C, Lou Hoàng',
  'sinh ra da la thu doi lap nhau': 'Emcee L (Da LAB)',
  'vaicaunoicokhiennguoithaydoi': 'GREY D',
  'chieu hom ay': 'JayKii',
  'co dau ai ngo': 'RedT',
  'ngoai le cua nhau': 'Obito',
  'doi': '52Hz',
  'doi (slowed)': '52Hz',
  'goi con tim lam qua': 'Nguyễn Hùng',
  'so khong': 'WEAN',
  'com ngoai hay com nha - cai nao hanh phuc?': 'Nhã Lam',

  // Quốc tế (US-UK, K-Pop, EDM)
  'shape of you': 'Ed Sheeran, Steve Mac, Johnny McDaid',
  'die with a smile': 'Bruno Mars, Lady Gaga, Andrew Watt, D\'Mile',
  'despacito': 'Luis Fonsi, Erika Ender, Daddy Yankee',
  'animals': 'Martin Garrix',
  'old town road': 'Lil Nas X, Trent Reznor, Atticus Ross',
  '50 feet': 'Ed Sheeran',
  'attention': 'Charlie Puth, Jacob Kasher',
  'waiting for love': 'Avicii, Martin Garrix, Salem Al Fakir',
  'bones': 'Dan Reynolds, Wayne Sermon, Ben McKee, Daniel Platzman',
  'blinding lights': 'Abel Tesfaye (The Weeknd), Max Martin, Oscar Holter',
  'dark horse': 'Katy Perry, Sarah Hudson, Juicy J, Max Martin',
  'haru haru': 'G-Dragon, Daishi Dance',
  'fantastic baby': 'G-Dragon, T.O.P, Teddy',
  'eyes, nose, lips': 'Teddy, Taeyang'
};

// Danh sách nghệ sĩ thường kiêm luôn nhạc sĩ sáng tác tác phẩm của mình (Singer-Songwriters)
const SINGER_SONGWRITERS = new Set([
  'sơn tùng m-tp',
  'jack - j97',
  'jack',
  'vũ.',
  'phan mạnh quỳnh',
  'rhymastic',
  'khắc hưng',
  'hieuthuhai',
  'mck',
  'wren evans',
  'b ray',
  'bray',
  'vũ cát tường',
  'da lab',
  'hoàng dũng',
  'đen vâu',
  'táo',
  'ed sheeran',
  'charlie puth',
  'avicii',
  'taylor swift',
  'bruno mars',
  'wean',
  'obito',
  '52hz',
  'madihu',
  'tháp drill tự do'
]);

class ComposerService {
  /**
   * Tự động nhận diện Nhạc sĩ / Người sáng tác của bài hát
   * Thứ tự ưu tiên:
   * 1. Nhạc sĩ được truyền rõ ràng từ client (nếu có)
   * 2. Phân tích nội dung lời bài hát (Lyrics regex)
   * 3. Tra cứu từ điển danh mục bài hát (Knowledge Dictionary)
   * 4. AI Gemini Query (nếu có cấu hình API Key)
   * 5. Heuristic Singer-Songwriter (nếu ca sĩ là tác giả chuyên sáng tác)
   * 6. Fallback theo tên ca sĩ
   */
  static async detectComposer({ title = '', artistName = '', lyrics = '', audioUrl = '', explicitComposer = '' }) {
    if (explicitComposer && explicitComposer.trim()) {
      return explicitComposer.trim();
    }

    const cleanTitle = (title || '')
      .replace(/[\[\(].*?[\]\)]/g, '')
      .replace(/ft\..*$/i, '')
      .replace(/feat\..*$/i, '')
      .replace(/-.*$/i, '')
      .trim();

    const titleSlug = slugify(cleanTitle).replace(/-/g, ' ');
    const fullTitleSlug = slugify(title).replace(/-/g, ' ');

    // 1. Tra cứu nhanh trong Từ điển Master
    if (COMPOSER_DICTIONARY[titleSlug]) {
      return COMPOSER_DICTIONARY[titleSlug];
    }
    if (COMPOSER_DICTIONARY[fullTitleSlug]) {
      return COMPOSER_DICTIONARY[fullTitleSlug];
    }

    // 2. Tìm trong Lời bài hát (Lyrics Regex)
    if (lyrics && lyrics.length > 5) {
      const match = lyrics.match(/(?:sáng tác|nhạc sĩ|tác giả|composer|nhạc & lời|nhạc và lời|lời & nhạc|nhạc|lời)\s*[:：\-]\s*([^\n\r,\.\(\)\[\]\<\>]+)/i);
      if (match && match[1]) {
        const found = match[1].trim();
        if (found.length > 1 && found.length < 50 && !found.toLowerCase().includes('http')) {
          return found;
        }
      }
    }

    // 3. Tra cứu bằng Google Gemini AI (nếu có key)
    const geminiKey = env.geminiApiKey || process.env.GEMINI_API_KEY;
    if (geminiKey && geminiKey.trim() && cleanTitle.length > 1) {
      try {
        const aiComposer = await this.queryComposerViaGemini(cleanTitle, artistName, geminiKey);
        if (aiComposer && aiComposer.trim()) {
          return aiComposer.trim();
        }
      } catch (err) {
        // Fallback nhẹ nhàng không gây gián đoạn
        console.warn(`[ComposerService] Gemini lookup skipped: ${err.message}`);
      }
    }

    // 4. Kiểm tra Heuristic Singer-Songwriter
    const cleanArtist = (artistName || '').toLowerCase().trim();
    for (const ssw of SINGER_SONGWRITERS) {
      if (cleanArtist.includes(ssw) || ssw.includes(cleanArtist)) {
        return artistName;
      }
    }

    // 5. Fallback mặc định
    return artistName || 'Đang cập nhật';
  }

  /**
   * Gọi AI Gemini để tra cứu nhạc sĩ chính xác cho bài hát mới
   */
  static async queryComposerViaGemini(title, artistName, apiKey) {
    const prompt = `Bạn là chuyên gia cơ sở dữ liệu âm nhạc Việt Nam và thế giới.
Hãy cho biết TÊN NHẠC SĨ / NGƯỜI SÁNG TÁC (Composer / Songwriter) của bài hát "${title}" do ca sĩ "${artistName}" thể hiện.
QUY TẮC: Chỉ trả về duy nhất tên nhạc sĩ/người sáng tác, không giải thích, không thêm dấu câu thừa. Nếu là ca sĩ tự sáng tác hãy trả về tên ca sĩ.`;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 2500); // 2.5s timeout an toàn

    try {
      const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`;
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: { maxOutputTokens: 30, temperature: 0.1 }
        }),
        signal: controller.signal
      });

      clearTimeout(timeout);
      if (!response.ok) return null;

      const data = await response.json();
      const rawText = data?.candidates?.[0]?.content?.parts?.[0]?.text;
      if (rawText) {
        const cleaned = rawText.replace(/[\n\r\*]/g, '').trim();
        if (cleaned.length > 1 && cleaned.length < 60) {
          return cleaned;
        }
      }
      return null;
    } catch {
      clearTimeout(timeout);
      return null;
    }
  }

  /**
   * Tìm hoặc tạo bản ghi Artist cho Nhạc sĩ để liên kết trong CSDL
   */
  static async findOrCreateComposerArtist(composerName, genreIds = []) {
    if (!composerName || composerName === 'Đang cập nhật' || composerName.length < 2) {
      return null;
    }

    const { Artist } = require('../models');
    const slug = slugify(composerName);

    let artistDoc = await Artist.findOne({
      $or: [
        { name: composerName },
        { slug: slug }
      ]
    });

    if (!artistDoc) {
      try {
        artistDoc = await Artist.create({
          name: composerName,
          slug: `${slug}-${Math.floor(Math.random() * 899 + 100)}`,
          avatarUrl: '/uploads/playlists/default_music_cover.jpg',
          bio: `Nhạc sĩ, tác giả sáng tác ca khúc nổi bật trên Vibe Music.`,
          followersCount: Math.floor(Math.random() * 400000 + 100000),
          isVerified: true,
          genreIds: genreIds || []
        });
      } catch (err) {
        // Trùng slug hoặc lỗi tạo
        artistDoc = await Artist.findOne({ name: composerName });
      }
    }

    return artistDoc ? artistDoc._id : null;
  }

  /**
   * Tách danh sách tác giả/nhạc sĩ/ca sĩ từ chuỗi ghép nối (dấu phẩy, x, ft, &, v.v.)
   */
  static splitAuthors(str) {
    if (!str || typeof str !== 'string') return [];
    return str
      .split(/(?:\s*[,;]\s*|\s+(?:ft\.?|feat\.?|[xX]|&)\s+)/)
      .map(s => s.trim())
      .filter(s => s.length > 0 && s !== 'Đang cập nhật');
  }

  /**
   * Tìm hoặc tạo các bản ghi Artist cho danh sách nhiều Nhạc sĩ
   */
  static async findOrCreateMultipleComposers(composerString, genreIds = []) {
    if (!composerString || typeof composerString !== 'string') return [];
    const names = this.splitAuthors(composerString);
    if (names.length === 0) return [];

    const ids = [];
    for (const name of names) {
      const id = await this.findOrCreateComposerArtist(name, genreIds);
      if (id && !ids.some(existing => existing.toString() === id.toString())) {
        ids.push(id);
      }
    }
    return ids;
  }

  /**
   * Bổ sung đầy đủ thông tin nhạc sĩ cho một bài hát trước khi lưu
   */
  static async enrichSongWithComposer(songData) {
    const composer = await this.detectComposer({
      title: songData.title,
      artistName: songData.artistName || (songData.artistId?.name),
      lyrics: songData.lyrics,
      audioUrl: songData.audioUrl,
      explicitComposer: songData.composer
    });

    songData.composer = composer;

    if (composer) {
      const composerIds = await this.findOrCreateMultipleComposers(composer, songData.genreIds);
      if (composerIds && composerIds.length > 0) {
        songData.composerIds = composerIds;
        songData.composerId = composerIds[0];
      }
    }

    return songData;
  }
}

module.exports = ComposerService;
