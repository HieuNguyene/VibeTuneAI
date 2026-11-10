const config = require('../config/environment');
const logger = require('../utils/logger');
const Song = require('../models/Song');
const Artist = require('../models/Artist');
const Genre = require('../models/Genre');

class AiChatService {
  /**
   * Xử lý tin nhắn hội thoại với Trợ lý AI Music DJ
   * @param {string|null} userId 
   * @param {string} userMessage 
   * @param {Array} conversationHistory 
   */
  static async handleChat(userId, userMessage, conversationHistory = []) {
    const rawMessage = (userMessage || '').trim();
    if (!rawMessage) {
      return {
        reply: 'Chào bạn! Hãy gửi cho mình tâm trạng, ca sĩ hoặc thể loại nhạc bạn muốn nghe nhé! 🎵',
        suggestedSongs: [],
        suggestedReplies: ['Gợi ý nhạc chill', 'Top bài hát thịnh hành', 'Nhạc của Sơn Tùng M-TP'],
        provider: 'fallback'
      };
    }

    // 1. Thử gọi Gemini AI nếu có cấu hình GEMINI_API_KEY
    if (config.geminiApiKey) {
      try {
        const geminiResult = await this.callGeminiChat(rawMessage, conversationHistory);
        if (geminiResult) {
          const songs = await this.searchRelevantSongs(geminiResult.keywords || [], rawMessage);
          return {
            reply: geminiResult.reply,
            suggestedSongs: songs,
            suggestedReplies: geminiResult.suggestedReplies || ['Nghe thêm bài khác', 'Đổi sang nhạc sôi động', 'Tạo playlist riêng'],
            provider: 'gemini-1.5-flash'
          };
        }
      } catch (err) {
        logger.error('Gemini chat error, falling back to Semantic DJ Engine:', err.message);
      }
    }

    // 2. Chạy bộ xử lý Semantic Music DJ Fallback cực kỳ thông minh
    return this.fallbackSemanticDJ(rawMessage);
  }

  /**
   * Gọi mô hình Google Gemini 1.5 Flash
   */
  static async callGeminiChat(userMessage, conversationHistory = []) {
    const { GoogleGenerativeAI } = require('@google/generative-ai');
    const genAI = new GoogleGenerativeAI(config.geminiApiKey);

    const systemInstruction = `
Bạn là "Spotify AI DJ & Music Assistant" - một trợ lý âm nhạc thông minh, trẻ trung, am hiểu mọi thể loại và nghệ sĩ.
Phong cách trò chuyện:
- Thân thiện, duyên dáng, tràn đầy năng lượng âm nhạc, dùng emoji hợp lý (🎵, 🎧, ✨, ☕, 🔥).
- Giúp người dùng chọn nhạc phù hợp tâm trạng (chill, buồn, vui, học bài, quẩy), giải đáp thông tin bài hát, nghệ sĩ.
- Trả về kết quả ĐÚNG ĐỊNH DẠNG JSON sau:
{
  "reply": "Câu trả lời bằng Tiếng Việt thân thiện, súc tích (1-3 câu)",
  "keywords": ["từ khoá nghệ sĩ hoặc bài hát", "thể loại hoặc tâm trạng để tìm trong kho nhạc"],
  "suggestedReplies": ["Gợi ý câu hỏi 1", "Gợi ý câu hỏi 2", "Gợi ý câu hỏi 3"]
}
`;

    const model = genAI.getGenerativeModel({
      model: 'gemini-1.5-flash',
      generationConfig: {
        responseMimeType: 'application/json',
        temperature: 0.3
      },
      systemInstruction
    });

    const prompt = `Tin nhắn của người dùng: "${userMessage}"`;
    const result = await model.generateContent(prompt);
    let text = result.response.text() || '';
    text = text.replace(/```json/gi, '').replace(/```/g, '').trim();

    try {
      return JSON.parse(text);
    } catch (e) {
      logger.warn('Failed to parse Gemini JSON response:', text);
      return null;
    }
  }

  /**
   * Bộ xử lý Semantic Music DJ Fallback thông minh
   */
  static async fallbackSemanticDJ(userMessage) {
    const lower = userMessage.toLowerCase();

    // A. Lời chào hỏi & Giới thiệu
    if (/^(chào|xin chào|hello|hi|helo|alo|hai|hế lô)/i.test(lower)) {
      const topSongs = await Song.find({ isPublic: true })
        .sort({ playsCount: -1 })
        .limit(3)
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('albumId', 'title coverImageUrl')
        .populate('genreIds', 'name colorCode')
        .lean();

      return {
        reply: 'Chào bạn! Mình là Spotify AI Music DJ 🎧. Mình có thể giúp bạn tìm bài hát theo tâm trạng, ca sĩ yêu thích hoặc gợi ý nhạc để thư giãn, học bài hay tập gym. Bạn đang muốn nghe nhạc như thế nào?',
        suggestedSongs: topSongs,
        suggestedReplies: ['Gợi ý nhạc chill học bài ☕', 'Bài hát đang hot 🔥', 'Nhạc của Sơn Tùng M-TP 🌟'],
        provider: 'smart-dj-heuristic'
      };
    }

    // B. Câu hỏi "bạn là ai", "bạn làm được gì"
    if (lower.includes('bạn là ai') || lower.includes('làm được gì') || lower.includes('trợ lý')) {
      return {
        reply: 'Mình là Trợ lý Âm nhạc AI (Spotify AI DJ) ✨. Mình luôn đồng hành cùng bạn để chọn bài hát hay nhất, tạo playlist theo cảm xúc và bật bài hát tức thì ngay trong khung chat này!',
        suggestedSongs: [],
        suggestedReplies: ['Bật nhạc Sơn Tùng M-TP', 'Gợi ý nhạc buồn mưa rơi 🌧️', 'Nhạc rap sôi động 🔥'],
        provider: 'smart-dj-heuristic'
      };
    }

    // C. Tìm theo nghệ sĩ
    const allArtists = await Artist.find().lean();
    let matchedArtist = null;
    for (const artist of allArtists) {
      const artistLower = artist.name.toLowerCase();
      // Chuẩn hóa loại bỏ các hậu tố và ký tự đặc biệt: " - m-tp", " - j97", v.v.
      const simplifiedName = artistLower.replace(/[-–—]?\s*(m-tp|mtp|j97)/gi, '').trim();
      const parts = artistLower.split(/[-&,]/).map((p) => p.trim());

      if (
        lower.includes(artistLower) ||
        (simplifiedName.length > 2 && lower.includes(simplifiedName)) ||
        parts.some((p) => p.length > 2 && lower.includes(p))
      ) {
        matchedArtist = artist;
        break;
      }
    }

    if (matchedArtist) {
      const artistSongs = await Song.find({
        isPublic: true,
        $or: [{ artistId: matchedArtist._id }, { featuredArtistIds: matchedArtist._id }]
      })
        .limit(4)
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('albumId', 'title coverImageUrl')
        .populate('genreIds', 'name colorCode')
        .lean();

      return {
        reply: `Đây là những ca khúc nổi bật nhất của **${matchedArtist.name}** dành cho bạn 🌟. Bạn có thể bấm Phát để thưởng thức ngay nhé!`,
        suggestedSongs: artistSongs,
        suggestedReplies: [`Thêm bài hát của ${matchedArtist.name}`, 'Đổi sang ca sĩ khác', 'Gợi ý nhạc chill'],
        provider: 'smart-dj-heuristic'
      };
    }

    // D. Tìm theo tên bài hát cụ thể
    const allSongs = await Song.find({ isPublic: true })
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    const matchedSong = allSongs.find((s) => lower.includes(s.title.toLowerCase()));
    if (matchedSong) {
      return {
        reply: `Mình đã tìm thấy bài hát **"${matchedSong.title}"** của **${matchedSong.artistId?.name || 'Nghệ sĩ'}**! Hãy bấm nút Phát bên dưới để nghe ngay nhé 🎶`,
        suggestedSongs: [matchedSong],
        suggestedReplies: ['Gợi ý bài tương tự', 'Xem thêm nhạc của nghệ sĩ này', 'Nhạc vui năng lượng'],
        provider: 'smart-dj-heuristic'
      };
    }

    // E. Tìm theo Tâm trạng / Hoạt động
    let queryMoods = [];
    let replyText = '';

    if (lower.includes('chill') || lower.includes('thư giãn') || lower.includes('nghỉ ngơi') || lower.includes('cafe')) {
      queryMoods = ['chill'];
      replyText = 'Một chút giai điệu nhẹ nhàng và êm dịu để bạn thư giãn và xua tan căng thẳng nhé ☕🍃:';
    } else if (lower.includes('buồn') || lower.includes('tâm trạng') || lower.includes('mưa') || lower.includes('khóc') || lower.includes('cô đơn')) {
      queryMoods = ['chill', 'sad'];
      replyText = 'Âm nhạc luôn là liều thuốc chữa lành tuyệt vời nhất khi buồn 🌧️. Gửi bạn những bản tình ca sâu lắng này:';
    } else if (lower.includes('học') || lower.includes('tập trung') || lower.includes('code') || lower.includes('làm việc') || lower.includes('đọc sách')) {
      queryMoods = ['chill', 'focus'];
      replyText = 'Giai điệu du dương giúp kích hoạt sự tập trung cao độ cho buổi học tập và làm việc của bạn 📚✨:';
    } else if (lower.includes('vui') || lower.includes('năng lượng') || lower.includes('quẩy') || lower.includes('gym') || lower.includes('chạy') || lower.includes('tiệc')) {
      queryMoods = ['happy', 'energetic', 'party'];
      replyText = 'Bật chế độ tràn đầy năng lượng cùng những giai điệu sôi động và cuốn hút này nào! 🔥🎉:';
    } else if (lower.includes('yêu') || lower.includes('crush') || lower.includes('lãng mạn') || lower.includes('hẹn hò')) {
      queryMoods = ['romantic', 'happy'];
      replyText = 'Không gian ngập tràn sự ngọt ngào với những bản tình ca lãng mạn dành riêng cho bạn 💕:';
    } else {
      // Mặc định: Gợi ý nhạc tổng hợp hay
      replyText = 'Dưới đây là những bài hát được nghe nhiều nhất và đang rất được yêu thích trên hệ thống Spotify 🎶:';
    }

    const matchedSongs = await Song.find({
      isPublic: true,
      ...(queryMoods.length > 0 ? { mood: { $in: queryMoods } } : {})
    })
      .sort({ playsCount: -1 })
      .limit(4)
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    return {
      reply: replyText,
      suggestedSongs: matchedSongs,
      suggestedReplies: ['Gợi ý thêm nhạc chill ☕', 'Nhạc rap sôi động 🔥', 'Top bài hát V-Pop ⭐'],
      provider: 'smart-dj-heuristic'
    };
  }

  /**
   * Tìm bài hát dựa trên danh sách keywords từ Gemini
   */
  static async searchRelevantSongs(keywords, originalPrompt) {
    if (!keywords || keywords.length === 0) {
      keywords = [originalPrompt];
    }

    const regexArray = keywords.map((kw) => new RegExp(kw.trim(), 'i'));

    let songs = await Song.find({
      isPublic: true,
      $or: [
        { title: { $in: regexArray } },
        { mood: { $in: keywords.map((k) => k.toLowerCase()) } }
      ]
    })
      .limit(4)
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    if (songs.length === 0) {
      // Fallback: Lấy top bài hát nhiều lượt nghe
      songs = await Song.find({ isPublic: true })
        .sort({ playsCount: -1 })
        .limit(3)
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('albumId', 'title coverImageUrl')
        .populate('genreIds', 'name colorCode')
        .lean();
    }

    return songs;
  }
}

module.exports = AiChatService;
