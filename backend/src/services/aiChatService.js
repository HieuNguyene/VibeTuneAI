const config = require('../config/environment');
const logger = require('../utils/logger');
const Song = require('../models/Song');
const Artist = require('../models/Artist');
const Album = require('../models/Album');
const Genre = require('../models/Genre');

class AiChatService {
  /**
   * Trích xuất danh sách ID bài hát đã từng xuất hiện trong hội thoại để tránh lặp lại
   */
  static async extractExcludedSongIds(conversationHistory = []) {
    if (!Array.isArray(conversationHistory) || conversationHistory.length === 0) {
      return [];
    }
    const excludedIds = new Set();
    const historyText = JSON.stringify(conversationHistory).toLowerCase();

    // 1. Lấy ID trực tiếp nếu client đã đính kèm songIds hoặc suggestedSongs
    for (const item of conversationHistory) {
      if (item && Array.isArray(item.songIds)) {
        item.songIds.forEach((id) => {
          if (id) excludedIds.add(String(id));
        });
      }
      if (item && Array.isArray(item.suggestedSongs)) {
        item.suggestedSongs.forEach((s) => {
          if (s && s._id) excludedIds.add(String(s._id));
          if (s && s.id) excludedIds.add(String(s.id));
        });
      }
    }

    // 2. Đối soát tên bài hát xuất hiện trong văn bản tin nhắn
    const allSongs = await Song.find({ isPublic: true }).select('_id title').lean();
    for (const s of allSongs) {
      if (s.title && historyText.includes(s.title.toLowerCase())) {
        excludedIds.add(String(s._id));
      }
    }

    return Array.from(excludedIds);
  }

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
        suggestedReplies: ['Gợi ý nhạc chill ☕', 'Top bài hát thịnh hành 🔥', 'Nhạc của Sơn Tùng M-TP 🌟'],
        provider: 'fallback'
      };
    }

    // 0. Trích xuất danh sách bài hát đã xuất hiện để chống lặp lại (Anti-Repetition)
    const excludeSongIds = await this.extractExcludedSongIds(conversationHistory);

    // 1. Nhận diện ngay ý định "Thử lại", "Đổi bài khác", "Toàn bài cũ", "Nghe bài khác"
    const isRetryOrChange = /(thử lại|thu lai|bài khác|khác đi|khong con bai|không còn bài|còn bài nào khác|đổi bài|chuyển bài|nghe bài khác|thêm bài|toàn bài cũ|lặp lại|chán|bài mới|gợi ý khác|đổi gu)/i.test(rawMessage.toLowerCase());
    if (isRetryOrChange) {
      return this.handleRetryAndDiscovery(rawMessage, excludeSongIds);
    }

    // 2. Sử dụng AI Mood Chatbot Engine với Gemini & Vector Search 768 chiều
    try {
      const AiMoodChatbot = require('./aiMoodChatbot');
      const moodResult = await AiMoodChatbot.handleChat(userId, rawMessage, conversationHistory);
      if (moodResult && (moodResult.provider === 'safety_guard' || moodResult.provider === 'greeting' || moodResult.provider === 'domain_guard' || (moodResult.suggestedSongs && moodResult.suggestedSongs.length > 0))) {
        return moodResult;
      }
    } catch (moodErr) {
      logger.warn('AiMoodChatbot failed, continuing with legacy fallback:', moodErr.message);
    }

    // 3. Fallback: Tool Calling Chatbot Engine cũ
    try {
      const ToolCallingChatService = require('./toolCallingChat');
      const toolResult = await ToolCallingChatService.executeChat(userId, rawMessage, conversationHistory);
      if (toolResult && (toolResult.provider === 'safety_guard' || (toolResult.tracks && toolResult.tracks.length > 0))) {
        return toolResult;
      }
    } catch (toolErr) {
      logger.warn('Tool Calling Chat failed, continuing with Semantic DJ:', toolErr.message);
    }
    if (config.geminiApiKey) {
      try {
        const geminiPromise = this.callGeminiChat(rawMessage, conversationHistory);
        const timeoutPromise = new Promise((resolve) =>
          setTimeout(() => resolve(null), 5000)
        );
        const geminiResult = await Promise.race([geminiPromise, timeoutPromise]);
        if (geminiResult) {
          const songs = await this.searchRelevantSongs(geminiResult.keywords || [], rawMessage, excludeSongIds);
          return {
            reply: geminiResult.reply,
            suggestedSongs: songs,
            suggestedReplies: geminiResult.suggestedReplies || ['Đổi sang bài hát khác ✨', 'Gợi ý nhạc chill ☕', 'Nhạc rap sôi động 🔥'],
            provider: geminiResult.activeModel || 'gemini'
          };
        }
      } catch (err) {
        logger.error('Gemini chat error, falling back to Semantic DJ Engine:', err.message);
      }
    }

    // 3. Chạy bộ xử lý Semantic Music DJ Fallback thông minh với Anti-Repetition
    return this.fallbackSemanticDJ(rawMessage, conversationHistory, excludeSongIds);
  }

  /**
   * Gọi mô hình Google Gemini AI với cơ chế Multi-Model Failover & Fast Timeout
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

    let prompt = '';
    if (Array.isArray(conversationHistory) && conversationHistory.length > 0) {
      const historyText = conversationHistory
        .slice(-5)
        .map((h) => `${h.role === 'user' ? 'Người dùng' : 'AI DJ'}: ${h.parts || h.message || ''}`)
        .join('\n');
      prompt = `Lịch sử hội thoại trước đó:\n${historyText}\n\nTin nhắn mới nhất của người dùng: "${userMessage}"`;
    } else {
      prompt = `Tin nhắn của người dùng: "${userMessage}"`;
    }

    const modelCandidates = ['gemini-3.5-flash', 'gemini-3.1-flash-lite', 'gemini-3.5-flash-lite'];

    for (const modelName of modelCandidates) {
      try {
        const model = genAI.getGenerativeModel({
          model: modelName,
          generationConfig: {
            responseMimeType: 'application/json',
            temperature: 0.3
          },
          systemInstruction
        });

        const timeoutPromise = new Promise((_, reject) =>
          setTimeout(() => reject(new Error(`Timeout 4000ms on ${modelName}`)), 4000)
        );
        const result = await Promise.race([model.generateContent(prompt), timeoutPromise]);

        let text = result.response.text() || '';
        text = text.replace(/```json/gi, '').replace(/```/g, '').trim();
        const parsed = JSON.parse(text);
        return { ...parsed, activeModel: modelName };
      } catch (err) {
        logger.warn(`Gemini Chat: model ${modelName} failed (${err.message}), trying next candidate...`);
      }
    }

    return null;
  }

  /**
   * Xử lý trường hợp người dùng bấm "Thử lại", "Đổi bài", hoặc muốn nghe bài hát mới
   */
  static async handleRetryAndDiscovery(userMessage, excludeSongIds = []) {
    let query = { isPublic: true };
    if (excludeSongIds.length > 0) {
      query._id = { $nin: excludeSongIds };
    }

    let candidates = await Song.find(query)
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    // Nếu đã hết bài chưa nghe, reset lại toàn bộ kho nhạc
    if (candidates.length < 4) {
      candidates = await Song.find({ isPublic: true })
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('albumId', 'title coverImageUrl')
        .populate('genreIds', 'name colorCode')
        .lean();
    }

    // Xáo trộn ngẫu nhiên hoàn toàn
    candidates.sort(() => Math.random() - 0.5);
    const chosenSongs = candidates.slice(0, 4);

    const friendlyReplies = [
      'Đã đổi ngay sang 4 giai điệu mới toanh cho bạn rồi nè! 🎧✨ Bấm nghe ngay nhé:',
      'Đây là danh sách bài hát mới được Spotify AI DJ chọn riêng cho bạn, đảm bảo không trùng lặp! 🎶🔥:',
      'Đổi gió ngay cùng 4 ca khúc hấp dẫn khác trong kho nhạc nào! ☕🍃:'
    ];
    const replyText = friendlyReplies[Math.floor(Math.random() * friendlyReplies.length)];

    return {
      reply: replyText,
      suggestedSongs: chosenSongs,
      suggestedReplies: ['Đổi sang bài khác nữa ✨', 'Gợi ý nhạc buồn 🌧️', 'Nhạc rap sôi động 🔥'],
      provider: 'anti-repetition-discovery-engine'
    };
  }

  /**
   * Bộ xử lý Semantic Music DJ Fallback thông minh với Anti-Repetition
   */
  static async fallbackSemanticDJ(userMessage, conversationHistory = [], excludeSongIds = []) {
    const lower = userMessage.toLowerCase();

    // A. Lời yêu cầu đổi bài khác ("không còn bài khác à", "đổi bài", "bài khác", "thử lại")
    if (/(bài khác|khác đi|khong con bai|không còn bài|còn bài nào khác|đổi bài|chuyển bài|nghe bài khác|thêm bài|thử lại|thu lai|chán|toàn bài cũ|lặp lại)/i.test(lower)) {
      return this.handleRetryAndDiscovery(userMessage, excludeSongIds);
    }

    // B. Lời chào hỏi & Giới thiệu
    if (/^(chào|xin chào|hello|hi|helo|alo|hai|hế lô)/i.test(lower)) {
      let topSongs = await Song.find({ isPublic: true, ...(excludeSongIds.length > 0 ? { _id: { $nin: excludeSongIds } } : {}) })
        .sort({ playsCount: -1 })
        .limit(10)
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('albumId', 'title coverImageUrl')
        .populate('genreIds', 'name colorCode')
        .lean();

      if (topSongs.length < 3) {
        topSongs = await Song.find({ isPublic: true })
          .sort({ playsCount: -1 })
          .limit(10)
          .populate('artistId', 'name avatarUrl isVerified')
          .populate('albumId', 'title coverImageUrl')
          .populate('genreIds', 'name colorCode')
          .lean();
      }

      // Xáo trộn nhẹ top bài hát để chào hỏi luôn tươi mới
      topSongs.sort(() => Math.random() - 0.5);

      return {
        reply: 'Chào bạn! Mình là Spotify AI Music DJ 🎧. Mình có thể giúp bạn tìm bài hát theo tâm trạng, ca sĩ yêu thích hoặc gợi ý nhạc để thư giãn, học bài hay tập gym. Bạn đang muốn nghe nhạc như thế nào?',
        suggestedSongs: topSongs.slice(0, 3),
        suggestedReplies: ['Gợi ý nhạc chill học bài ☕', 'Bài hát đang hot 🔥', 'Nhạc của Sơn Tùng M-TP 🌟'],
        provider: 'smart-dj-heuristic'
      };
    }

    // C. Câu hỏi "bạn là ai", "bạn làm được gì"
    if (lower.includes('bạn là ai') || lower.includes('làm được gì') || lower.includes('trợ lý')) {
      return {
        reply: 'Mình là Trợ lý Âm nhạc AI (Spotify AI DJ) ✨. Mình luôn đồng hành cùng bạn để chọn bài hát hay nhất, tạo playlist theo cảm xúc và bật bài hát tức thì ngay trong khung chat này!',
        suggestedSongs: [],
        suggestedReplies: ['Bật nhạc Sơn Tùng M-TP 🌟', 'Gợi ý nhạc buồn mưa rơi 🌧️', 'Nhạc rap sôi động 🔥'],
        provider: 'smart-dj-heuristic'
      };
    }

    // D. Tìm theo nghệ sĩ
    const allArtists = await Artist.find().lean();
    const matchedArtist = this.matchArtistFromText(lower, allArtists);

    if (matchedArtist) {
      let artistSongs = await Song.find({
        isPublic: true,
        $or: [{ artistId: matchedArtist._id }, { featuredArtistIds: matchedArtist._id }],
        ...(excludeSongIds.length > 0 ? { _id: { $nin: excludeSongIds } } : {})
      })
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('albumId', 'title coverImageUrl')
        .populate('genreIds', 'name colorCode')
        .lean();

      if (artistSongs.length === 0) {
        artistSongs = await Song.find({
          isPublic: true,
          $or: [{ artistId: matchedArtist._id }, { featuredArtistIds: matchedArtist._id }]
        })
          .populate('artistId', 'name avatarUrl isVerified')
          .populate('albumId', 'title coverImageUrl')
          .populate('genreIds', 'name colorCode')
          .lean();
      }

      artistSongs.sort(() => Math.random() - 0.5);

      return {
        reply: `Đây là những ca khúc nổi bật của **${matchedArtist.name}** dành cho bạn 🌟. Bấm Phát để thưởng thức ngay nhé!`,
        suggestedSongs: artistSongs.slice(0, 4),
        suggestedReplies: [`Thêm bài hát của ${matchedArtist.name}`, 'Đổi sang ca sĩ khác', 'Gợi ý nhạc chill ☕'],
        provider: 'smart-dj-heuristic'
      };
    }

    // E. Gợi ý bài hát tương tự theo Cosine Similarity
    const isSimilarQuery = /(tương tự|giống bài|nhạc kiểu|radio bài|giống như bài)/i.test(lower);
    if (isSimilarQuery) {
      const allSongsList = await Song.find({ isPublic: true }).lean();
      const matchedSong = allSongsList.find((s) => lower.includes(s.title.toLowerCase()));
      if (matchedSong) {
        const CosineRecommender = require('../ai/cosineRecommender');
        const similarSongs = await CosineRecommender.findSimilarSongs(matchedSong._id, {
          limit: 4,
          excludeIds: excludeSongIds
        });
        return {
          reply: `Dưới đây là những bài hát có âm hưởng và giai điệu tương tự với **"${matchedSong.title}"** (phân tích bằng thuật toán Cosine Similarity) 🎧✨:`,
          suggestedSongs: similarSongs,
          suggestedReplies: ['Đổi sang bài hát khác ✨', 'Thêm nhạc cùng thể loại', 'Gợi ý nhạc chill ☕'],
          provider: 'cosine-similarity-engine'
        };
      }
    }

    // F. Tìm theo tên bài hát cụ thể
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
        suggestedReplies: ['Gợi ý bài tương tự ✨', 'Xem thêm nhạc của nghệ sĩ này', 'Nhạc vui năng lượng 🔥'],
        provider: 'smart-dj-heuristic'
      };
    }

    // G. Phân loại theo Tâm trạng / Hoạt động
    let queryMoods = [];
    let replyText = '';

    if (lower.includes('chill') || lower.includes('thư giãn') || lower.includes('nghỉ ngơi') || lower.includes('cafe')) {
      queryMoods = ['chill', 'relax', 'healing', 'lofi'];
      replyText = 'Một chút giai điệu nhẹ nhàng và êm dịu để bạn thư giãn và xua tan căng thẳng nhé ☕🍃:';
    } else if (lower.includes('buồn') || lower.includes('tâm trạng') || lower.includes('mưa') || lower.includes('khóc') || lower.includes('cô đơn') || lower.includes('chia tay')) {
      queryMoods = ['sad', 'melancholy', 'heartbroken', 'rain'];
      replyText = 'Âm nhạc luôn là liều thuốc chữa lành tuyệt vời nhất khi buồn 🌧️. Gửi bạn những bản tình ca sâu lắng này:';
    } else if (lower.includes('học') || lower.includes('tập trung') || lower.includes('code') || lower.includes('làm việc') || lower.includes('đọc sách')) {
      queryMoods = ['focus', 'lofi', 'study', 'relax'];
      replyText = 'Giai điệu du dương giúp kích hoạt sự tập trung cao độ cho buổi học tập và làm việc của bạn 📚✨:';
    } else if (lower.includes('vui') || lower.includes('năng lượng') || lower.includes('quẩy') || lower.includes('gym') || lower.includes('chạy') || lower.includes('tiệc') || lower.includes('party')) {
      queryMoods = ['energetic', 'party', 'trap', 'hype', 'dance', 'happy'];
      replyText = 'Bật chế độ tràn đầy năng lượng cùng những giai điệu sôi động và cuốn hút này nào! 🔥🎉:';
    } else if (lower.includes('yêu') || lower.includes('crush') || lower.includes('lãng mạn') || lower.includes('hẹn hò')) {
      queryMoods = ['romantic', 'love', 'sweet', 'r&b'];
      replyText = 'Không gian ngập tràn sự ngọt ngào với những bản tình ca lãng mạn dành riêng cho bạn 💕:';
    } else {
      queryMoods = [];
      replyText = 'Dưới đây là những giai điệu tuyển chọn mới mẻ và cuốn hút trên hệ thống Spotify 🎶✨:';
    }

    let filter = { isPublic: true };
    if (queryMoods.length > 0) {
      filter.mood = { $in: queryMoods };
    }
    if (excludeSongIds.length > 0) {
      filter._id = { $nin: excludeSongIds };
    }

    let candidateSongs = await Song.find(filter)
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    // Nếu không còn đủ bài theo filter loại trừ, bỏ điều kiện loại trừ
    if (candidateSongs.length < 4) {
      const fallbackFilter = { isPublic: true };
      if (queryMoods.length > 0) fallbackFilter.mood = { $in: queryMoods };
      candidateSongs = await Song.find(fallbackFilter)
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('albumId', 'title coverImageUrl')
        .populate('genreIds', 'name colorCode')
        .lean();
    }

    // Xáo trộn ngẫu nhiên để luôn đem lại cảm giác tươi mới
    candidateSongs.sort(() => Math.random() - 0.5);
    const chosenSongs = candidateSongs.slice(0, 4);

    return {
      reply: replyText,
      suggestedSongs: chosenSongs,
      suggestedReplies: ['Đổi sang bài hát khác ✨', 'Gợi ý nhạc buồn 🌧️', 'Nhạc rap sôi động 🔥'],
      provider: 'smart-dj-heuristic'
    };
  }

  /**
   * Nhận diện ca sĩ từ câu chat hoặc danh sách từ khoá
   */
  static matchArtistFromText(text, allArtists) {
    if (!text || !allArtists || allArtists.length === 0) return null;
    const lower = text.toLowerCase();
    for (const artist of allArtists) {
      const aName = artist.name.toLowerCase();
      const aliases = [aName];
      if (aName.includes('sơn tùng')) aliases.push('sơn tùng', 'son tung', 'm-tp', 'mtp', 'sếp');
      if (aName.includes('hieuthuhai')) aliases.push('hieuthuhai', 'hieu thu hai');
      if (aName.includes('vũ')) aliases.push('vũ', 'hoàng thái vũ');
      if (aName.includes('đen')) aliases.push('đen vâu', 'den vau', 'đen');
      if (aName.includes('hoàng dũng')) aliases.push('hoàng dũng', 'hoang dung');
      if (aName.includes('mck')) aliases.push('mck', 'wren evans');
      if (aName.includes('jack')) aliases.push('jack', 'j97');
      if (aName.includes('bigbang')) aliases.push('bigbang', 'taeyang', 'g-dragon');
      if (aName.includes('ed sheeran')) aliases.push('ed sheeran', 'bruno mars');
      if (aName.includes('andree')) aliases.push('andree', 'wxrdie');
      if (aName.includes('bray')) aliases.push('bray', 'b-ray');
      if (aName.includes('wean')) aliases.push('wean');

      const parts = aName.split(/[-&,]/).map((p) => p.trim());
      parts.forEach((p) => {
        if (p.length > 2) aliases.push(p);
      });

      if (aliases.some((al) => lower.includes(al))) {
        return artist;
      }
    }
    return null;
  }

  /**
   * Tìm bài hát dựa trên danh sách keywords từ Gemini hoặc prompt người dùng
   * Thứ tự ưu tiên: 1. Nghệ sĩ (Artist) -> 2. Tên bài hát (Title) -> 3. Cảm xúc (Mood) -> 4. Khám phá
   */
  static async searchRelevantSongs(keywords, originalPrompt, excludeSongIds = []) {
    const rawTokens = [...(keywords || []), originalPrompt]
      .filter((k) => typeof k === 'string' && k.trim().length > 0)
      .map((k) => k.trim());
    const combinedText = rawTokens.join(' ').toLowerCase();

    const baseFilter = {
      isPublic: true,
      ...(excludeSongIds.length > 0 ? { _id: { $nin: excludeSongIds } } : {})
    };

    // 1. ƯU TIÊN SỐ 1: TÌM THEO CA SĨ / NGHỆ SĨ (Artist First)
    const allArtists = await Artist.find().lean();
    const matchedArtist = this.matchArtistFromText(combinedText, allArtists);

    if (matchedArtist) {
      let artistSongs = await Song.find({
        ...baseFilter,
        $or: [{ artistId: matchedArtist._id }, { featuredArtistIds: matchedArtist._id }]
      })
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('albumId', 'title coverImageUrl')
        .populate('genreIds', 'name colorCode')
        .lean();

      if (artistSongs.length === 0) {
        artistSongs = await Song.find({
          isPublic: true,
          $or: [{ artistId: matchedArtist._id }, { featuredArtistIds: matchedArtist._id }]
        })
          .populate('artistId', 'name avatarUrl isVerified')
          .populate('albumId', 'title coverImageUrl')
          .populate('genreIds', 'name colorCode')
          .lean();
      }

      if (artistSongs.length > 0) {
        artistSongs.sort(() => Math.random() - 0.5);
        return artistSongs.slice(0, 4);
      }
    }

    // 2. ƯU TIÊN SỐ 2: TÌM THEO TÊN BÀI HÁT CỤ THỂ (Exact / Partial Song Title)
    const allSongs = await Song.find({ isPublic: true })
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    const matchedByTitle = allSongs.filter((s) => {
      const sTitle = s.title.toLowerCase();
      return (
        sTitle.length >= 3 &&
        (combinedText.includes(sTitle) || rawTokens.some((t) => t.toLowerCase() === sTitle))
      );
    });

    if (matchedByTitle.length > 0) {
      const nonExcluded = matchedByTitle.filter((s) => !excludeSongIds.map(String).includes(String(s._id)));
      const chosen = nonExcluded.length > 0 ? nonExcluded : matchedByTitle;
      chosen.sort(() => Math.random() - 0.5);
      return chosen.slice(0, 4);
    }

    // 3. ƯU TIÊN SỐ 3: TÌM THEO TÂM TRẠNG (Mood) & THỂ LOẠI (Genre)
    const escapeRegex = (str) => String(str).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regexArray = rawTokens
      .map((kw) => escapeRegex(kw.trim()))
      .filter(Boolean)
      .map((safeKw) => new RegExp(safeKw, 'i'));

    const moodMapping = {
      'buồn': ['sad', 'melancholy', 'heartbroken', 'rain', 'acoustic'],
      'tâm trạng': ['sad', 'melancholy', 'chill', 'acoustic'],
      'sad': ['sad', 'melancholy', 'heartbroken', 'rain', 'acoustic'],
      'mưa': ['rain', 'sad', 'chill', 'acoustic'],
      'chill': ['chill', 'relax', 'healing', 'lofi'],
      'thư giãn': ['chill', 'relax', 'healing', 'lofi'],
      'cafe': ['chill', 'acoustic', 'lofi'],
      'vui': ['happy', 'energetic', 'party', 'dance'],
      'happy': ['happy', 'energetic', 'party'],
      'quẩy': ['party', 'trap', 'energetic', 'dance'],
      'party': ['party', 'trap', 'energetic', 'dance'],
      'gym': ['energetic', 'trap', 'hype', 'workout'],
      'sôi động': ['energetic', 'party', 'trap', 'dance'],
      'energetic': ['energetic', 'party', 'trap', 'dance'],
      'học': ['focus', 'lofi', 'study', 'relax'],
      'tập trung': ['focus', 'lofi', 'study'],
      'focus': ['focus', 'lofi', 'study'],
      'lofi': ['lofi', 'chill', 'relax'],
      'yêu': ['romantic', 'love', 'sweet'],
      'lãng mạn': ['romantic', 'love', 'sweet'],
      'romantic': ['romantic', 'love', 'sweet'],
      'acoustic': ['acoustic', 'chill', 'sad']
    };

    const targetMoods = new Set();
    for (const [key, tags] of Object.entries(moodMapping)) {
      if (combinedText.includes(key)) {
        tags.forEach((t) => targetMoods.add(t));
      }
    }
    const moodArray = Array.from(targetMoods);

    const orConditions = [{ title: { $in: regexArray } }];
    if (moodArray.length > 0) {
      orConditions.push({ mood: { $in: moodArray } });
    }

    let songs = await Song.find({
      ...baseFilter,
      $or: orConditions
    })
      .limit(20)
      .populate('artistId', 'name avatarUrl isVerified')
      .populate('albumId', 'title coverImageUrl')
      .populate('genreIds', 'name colorCode')
      .lean();

    // Ràng buộc Audio Features theo Mood
    if (targetMoods.has('sad') || targetMoods.has('chill')) {
      const filtered = songs.filter((s) => !(s.audioFeatures && s.audioFeatures.energy > 0.65));
      if (filtered.length >= 2) songs = filtered;
    } else if (targetMoods.has('energetic') || targetMoods.has('party')) {
      const filtered = songs.filter((s) => !(s.audioFeatures && s.audioFeatures.energy < 0.45));
      if (filtered.length >= 2) songs = filtered;
    }

    // 4. Nếu vẫn chưa có, lấy theo thể loại
    if (songs.length === 0) {
      const matchedGenres = await Genre.find({ name: { $in: regexArray } }).select('_id').lean();
      const genreIds = matchedGenres.map((g) => g._id);

      if (genreIds.length > 0) {
        songs = await Song.find({
          ...baseFilter,
          genreIds: { $in: genreIds }
        })
          .limit(20)
          .populate('artistId', 'name avatarUrl isVerified')
          .populate('albumId', 'title coverImageUrl')
          .populate('genreIds', 'name colorCode')
          .lean();
      }
    }

    // 5. Fallback: Lấy các bài hát ngẫu nhiên chưa nghe
    if (songs.length === 0) {
      songs = await Song.find(baseFilter)
        .limit(10)
        .populate('artistId', 'name avatarUrl isVerified')
        .populate('albumId', 'title coverImageUrl')
        .populate('genreIds', 'name colorCode')
        .lean();

      if (songs.length === 0) {
        songs = await Song.find({ isPublic: true })
          .limit(10)
          .populate('artistId', 'name avatarUrl isVerified')
          .populate('albumId', 'title coverImageUrl')
          .populate('genreIds', 'name colorCode')
          .lean();
      }
    }

    songs.sort(() => Math.random() - 0.5);
    return songs.slice(0, 4);
  }
}

module.exports = AiChatService;
