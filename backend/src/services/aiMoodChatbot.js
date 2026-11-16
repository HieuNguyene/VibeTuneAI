const { GoogleGenerativeAI } = require('@google/generative-ai');
require('dotenv').config();

const EmbeddingService = require('./embeddingService');
const VectorSearchEngine = require('./vectorSearchEngine');
const { UserInteraction, Song } = require('../models');

const apiKey = process.env.GEMINI_API_KEY;
let genAI = null;
if (apiKey) {
  genAI = new GoogleGenerativeAI(apiKey);
}

class AiMoodChatbot {
  /**
   * Kiểm tra an toàn khủng hoảng tâm lý
   */
  static checkCrisis(message = '') {
    const text = message.toLowerCase();
    const crisisPatterns = [
      /tự tử/i, /muốn chết/i, /chết đi/i, /kết thúc cuộc đời/i,
      /tự hại/i, /không muốn sống/i, /rạch tay/i, /uống thuốc ngủ/i
    ];

    for (const pattern of crisisPatterns) {
      if (pattern.test(text)) {
        return {
          isCrisis: true,
          reply: 'Mình nghe thấy bạn đang trải qua những cảm xúc rất nặng nề và khó khăn. Xin bạn hãy nhớ rằng bạn không hề cô độc. Hãy chia sẻ với người thân, bạn bè hoặc liên hệ ngay Đường dây nóng Hỗ trợ Tâm lý & Ngày Mai (Hotline: 096 306 1414) hoặc 115 khi cần giúp đỡ y tế khẩn cấp nhé. Mình luôn ở đây sẵn sàng mở những giai điệu êm dịu nhất bên cạnh bạn.'
        };
      }
    }
    return { isCrisis: false };
  }

  /**
   * Kiểm tra câu hỏi ngoài phạm vi âm nhạc (toán học, lập trình, thời tiết thuần túy, giá cả...)
   */
  static isOffTopicQuery(message = '') {
    const text = message.toLowerCase().trim();
    const hasMusicContext = /(nhạc|bài hát|ca sĩ|nghệ sĩ|playlist|album|giai điệu|nghe|tâm trạng|buồn|vui|chill|lofi|rap|ballad|rock|pop|edm|indie|acoustic|jazz|piano|ost|song|music|vibe|lyrics|lời bài hát|tập gym|học bài|lái xe|ngủ|thư giãn)/i.test(text);
    if (hasMusicContext) return false;

    const offTopicPatterns = [
      /^\d+\s*[+\-*/^]\s*\d+(\s*=|\s*bằng|\s*là|$)/i,
      /\b(mấy giờ|dự báo thời tiết|thời tiết hôm nay|nhiệt độ|giá vàng|giá bitcoin|tỷ giá|chứng khoán)\b/i,
      /\b(viết code|lập trình|python|javascript|java\b|c\+\+|html|css|sql|giải.*phương trình|phương trình bậc|đạo hàm|tích phân)\b/i,
      /\b(nấu ăn|công thức nấu|thủ đô của|tổng thống|bầu cử)\b/i
    ];
    return offTopicPatterns.some((p) => p.test(text));
  }

  /**
   * Xử lý hội thoại Âm nhạc chính (Vector-First RAG Pipeline)
   */
  static async handleChat(userId, userMessage, conversationHistory = []) {
    const cleanMsg = (userMessage || '').trim();
    if (!cleanMsg) {
      return {
        reply: 'Chào bạn, hôm nay tâm trạng của bạn thế nào? Hãy cho mình biết gu nhạc hoặc cảm xúc lúc này nhé.',
        suggestedSongs: [],
        tracks: [],
        trackIds: [],
        suggestedReplies: ['Nhạc chill thư giãn', 'Nhạc vui tích cực', 'Nhạc buồn tâm trạng'],
        provider: 'fallback'
      };
    }

    // 1. Kiểm tra an toàn khủng hoảng tâm lý
    const crisis = this.checkCrisis(cleanMsg);
    if (crisis.isCrisis) {
      const calmSongs = await Song.find({ isPublic: true, moods: 'calm' })
        .populate('artistId', 'name avatarUrl')
        .limit(3)
        .lean();

      const tracks = calmSongs.map(s => ({
        id: s._id,
        _id: s._id,
        title: s.title,
        artistName: s.artistId?.name || 'Nghệ sĩ',
        artist: { name: s.artistId?.name || 'Nghệ sĩ', avatarUrl: s.artistId?.avatarUrl || '' },
        audioUrl: s.audioUrl,
        coverImageUrl: s.coverImageUrl || s.artistId?.avatarUrl || '',
        duration: s.duration
      }));

      return {
        reply: crisis.reply,
        suggestedSongs: tracks,
        tracks: tracks,
        trackIds: tracks.map(t => String(t.id)),
        suggestedReplies: ['Nhạc piano êm dịu', 'Nhạc không lời thư giãn'],
        provider: 'safety_guard'
      };
    }

    // 2. Chào hỏi thông thường (Greeting)
    const isGreeting = /^(chào|xin chào|hello|hi|hey|alo|bạn là ai|help|giúp đỡ)\b/i.test(cleanMsg) && cleanMsg.length < 20;
    if (isGreeting) {
      return {
        reply: 'Xin chào, mình là Trợ lý Âm nhạc. Mình có thể tìm bài hát, nghệ sĩ hoặc tạo playlist hợp với tâm trạng hiện tại của bạn. Bạn muốn nghe gì lúc này?',
        suggestedSongs: [],
        tracks: [],
        trackIds: [],
        suggestedReplies: ['Nhạc chill thư giãn', 'Nhạc sôi động tập gym', 'Nhạc Sơn Tùng M-TP'],
        provider: 'greeting'
      };
    }

    // 2.1. Chống Prompt Injection & bảo mật hệ thống
    const isPromptInjection = /(bỏ qua tất cả|bỏ qua mọi|ignore (all|previous) instructions|jailbreak|system prompt|in toàn bộ|database password)/i.test(cleanMsg);
    if (isPromptInjection) {
      const topSongs = await Song.find({ isPublic: true })
        .sort({ playsCount: -1 })
        .populate('artistId', 'name avatarUrl')
        .limit(3)
        .lean();

      const tracks = topSongs.map(s => ({
        id: s._id,
        _id: s._id,
        title: s.title,
        artistName: s.artistId?.name || 'Nghệ sĩ',
        artist: { name: s.artistId?.name || 'Nghệ sĩ', avatarUrl: s.artistId?.avatarUrl || '' },
        audioUrl: s.audioUrl,
        coverImageUrl: s.coverImageUrl || s.artistId?.avatarUrl || '',
        duration: s.duration
      }));

      return {
        reply: 'Mình là Trợ lý Âm nhạc của hệ thống. Mình luôn sẵn sàng chọn lọc và gợi ý những bài hát phù hợp nhất trong thư viện cho bạn.',
        suggestedSongs: tracks,
        tracks: tracks,
        trackIds: tracks.map(t => String(t.id)),
        suggestedReplies: ['Gợi ý nhạc thịnh hành', 'Sơn Tùng M-TP', 'Nhạc chill thư giãn'],
        provider: 'safety_guard'
      };
    }

    // 2.2. Bảo vệ phạm vi âm nhạc (Domain Guard)
    if (this.isOffTopicQuery(cleanMsg)) {
      return {
        reply: 'Mình chuyên hỗ trợ tìm bài hát, nghệ sĩ, thể loại và gợi ý nhạc theo tâm trạng trong thư viện. Bạn muốn mình mở giai điệu nào ngay lúc này?',
        suggestedSongs: [],
        tracks: [],
        trackIds: [],
        suggestedReplies: ['Nhạc chill buổi tối', 'Nhạc Ballad Việt', 'Top bài nghe nhiều'],
        provider: 'domain_guard'
      };
    }

    try {
      // 3. Bước 1: Sinh Vector Embedding 768 chiều cho câu hỏi của người dùng
      const queryVector = await EmbeddingService.generateEmbedding(cleanMsg);

      // 4. Bước 2: Truy vấn Vector Similarity Search trực tiếp trên MongoDB Local
      const songs = await VectorSearchEngine.searchByVector(queryVector, {
        limit: 5,
        minSimilarity: 0.3,
        userId
      });

      if (!songs || songs.length === 0) {
        return this.fallbackSearch(cleanMsg, userId);
      }

      // 5. Bước 3: Gọi Gemini để sinh lời bình DJ đồng cảm và tự nhiên (RAG Response)
      let djReply = '';
      let usedModel = 'gemini';
      if (genAI) {
        const candidateModels = [
          process.env.LLM_MODEL,
          'gemini-2.5-flash',
          'gemini-1.5-flash',
          'gemini-2.0-flash',
          'gemini-1.5-flash-8b'
        ].filter(Boolean);

        const prompt = [
          'Bạn là người tuyển chọn âm nhạc thân thiện, tinh tế và am hiểu tâm trạng người nghe.',
          'Người dùng nhắn: "' + cleanMsg + '"',
          'Các bài hát phù hợp nhất trong thư viện đã được tìm thấy:',
          songs.map(s => `- "${s.title}" của ${s.artistName} (Độ khớp: ${s.similarityScore}%)`).join('\n'),
          'Hãy viết lời phản hồi bằng tiếng Việt ngắn gọn (2-3 câu), tự nhiên và giới thiệu khéo léo những bài hát này (không dùng quá nhiều biểu tượng cảm xúc). Tuyệt đối không bịa tên bài hát khác ngoài danh sách trên.'
        ].join('\n');

        for (const candidate of candidateModels) {
          try {
            const model = genAI.getGenerativeModel({ model: candidate });
            const genResult = await model.generateContent(prompt);
            const text = genResult?.response?.text()?.trim();
            if (text) {
              djReply = text;
              usedModel = candidate;
              break;
            }
          } catch (llmErr) {
            console.warn(`[AiMoodChatbot] Model ${candidate} failed (${llmErr.message.slice(0, 80)}), trying next fallback...`);
          }
        }

        if (!djReply) {
          djReply = `Mình đã chọn lọc những bài hát phù hợp nhất với tâm trạng "${cleanMsg}" của bạn. Chúc bạn có những phút giây nghe nhạc thật tuyệt vời! 🎵`;
        }
      } else {
        djReply = `Dưới đây là những bài hát phù hợp nhất với tâm trạng "${cleanMsg}" dành cho bạn:`;
      }

      return {
        reply: djReply,
        suggestedSongs: songs,
        tracks: songs,
        trackIds: songs.map(t => String(t.id)),
        suggestedReplies: ['Đổi gợi ý khác', 'Sôi động hơn', 'Nhẹ nhàng hơn'],
        provider: `${usedModel} + vector-search-768`
      };

    } catch (err) {
      console.error('[AiMoodChatbot] Pipeline Error:', err);
      return this.fallbackSearch(cleanMsg, userId);
    }
  }

  /**
   * Fallback an toàn khi có sự cố mạng
   */
  static async fallbackSearch(cleanMsg, userId) {
    try {
      const queryVector = await EmbeddingService.generateEmbedding(cleanMsg);
      const songs = await VectorSearchEngine.searchByVector(queryVector, { limit: 5, userId });
      return {
        reply: `Mình đã chọn lọc những bài hát phù hợp với "${cleanMsg}". Chúc bạn nghe nhạc thư giãn nhé.`,
        suggestedSongs: songs,
        tracks: songs,
        trackIds: songs.map(t => String(t.id)),
        suggestedReplies: ['Đổi gợi ý khác', 'Nhạc chill thư giãn'],
        provider: 'vector-search-fallback'
      };
    } catch (e) {
      const sampleSongs = await Song.aggregate([
        { $match: { isPublic: true } },
        { $sample: { size: 5 } }
      ]);
      await Song.populate(sampleSongs, { path: 'artistId', select: 'name avatarUrl' });

      const formatted = sampleSongs.map(s => ({
        id: s._id,
        _id: s._id,
        title: s.title,
        artistName: s.artistId?.name || 'Nghệ sĩ',
        artist: { name: s.artistId?.name || 'Nghệ sĩ', avatarUrl: s.artistId?.avatarUrl || '' },
        audioUrl: s.audioUrl,
        coverImageUrl: s.coverImageUrl || s.artistId?.avatarUrl || '',
        duration: s.duration
      }));

      return {
        reply: 'Dưới đây là một số bài hát nổi bật dành cho bạn:',
        suggestedSongs: formatted,
        tracks: formatted,
        trackIds: formatted.map(t => String(t.id)),
        suggestedReplies: ['Nhạc chill thư giãn', 'Nhạc sôi động'],
        provider: 'mongo-random-fallback'
      };
    }
  }
}

module.exports = AiMoodChatbot;
