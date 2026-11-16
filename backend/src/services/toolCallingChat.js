const fs = require('fs');
const path = require('path');
const { GoogleGenerativeAI } = require('@google/generative-ai');
const config = require('../config/environment');
const searchTracks = require('./searchTracks');
const CatalogBrain = require('./catalogBrain');
const { ListeningEvent, Song, Playlist, PlaylistTrack, UserPreference } = require('../models');
const { MOOD_LIST, ACTIVITY_LIST, ENERGY_LEVELS } = require('../config/labels');

// Đọc System Prompt tiếng Việt từ file
const PROMPT_FILE = path.join(__dirname, '../prompts/system.vi.txt');
let SYSTEM_PROMPT = '';
try {
  SYSTEM_PROMPT = fs.readFileSync(PROMPT_FILE, 'utf8');
} catch (e) {
  SYSTEM_PROMPT = 'Bạn là Trợ lý Âm nhạc AI thân thiện, trả lời ngắn gọn và chỉ gợi ý bài hát từ catalog thật.';
}

// Định nghĩa Tools
const CHAT_TOOLS = [
  {
    functionDeclarations: [
      {
        name: 'search_tracks',
        description: 'Tìm kiếm các bài hát thật có trong catalog của ứng dụng theo tâm trạng (moods), mức năng lượng (energy) hoặc hoạt động (activities).',
        parameters: {
          type: 'OBJECT',
          properties: {
            moods: {
              type: 'ARRAY',
              items: { type: 'STRING' },
              description: 'Danh sách tâm trạng hợp lệ: happy, sad, calm, energetic, romantic, melancholic, nostalgic, focused, angry, dreamy'
            },
            energy: {
              type: 'STRING',
              enum: ['low', 'medium', 'high'],
              description: 'Mức năng lượng: low (<0.45), medium (0.45-0.65), high (>0.65)'
            },
            activities: {
              type: 'ARRAY',
              items: { type: 'STRING' },
              description: 'Hoạt động: focus, study, sleep, workout, commute, drive, party, relax'
            },
            limit: {
              type: 'INTEGER',
              description: 'Số lượng bài hát gợi ý (1-8)'
            }
          }
        }
      },
      {
        name: 'get_user_taste',
        description: 'Lấy thông tin gu âm nhạc cá nhân hóa của người dùng hiện tại dựa trên lịch sử nghe gần nhất.',
        parameters: {
          type: 'OBJECT',
          properties: {}
        }
      },
      {
        name: 'create_playlist',
        description: 'Tạo một danh sách phát (playlist) mới từ danh sách ID bài hát được chọn.',
        parameters: {
          type: 'OBJECT',
          properties: {
            name: { type: 'STRING', description: 'Tên playlist' },
            trackIds: { type: 'ARRAY', items: { type: 'STRING' }, description: 'Danh sách trackId' }
          },
          required: ['name', 'trackIds']
        }
      }
    ]
  }
];

class ToolCallingChatService {
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
   * Tổng hợp gu nghe nhạc của người dùng từ listening_events (Tool: get_user_taste)
   */
  static async getUserTaste(userId) {
    if (!userId) {
      return { favoriteMoods: ['chill', 'happy'], topActivities: ['relax'] };
    }

    try {
      // Tìm các track được nghe nhiều hoặc like
      const events = await ListeningEvent.aggregate([
        { $match: { userId, event: { $in: ['play', 'complete', 'like'] } } },
        { $group: { _id: '$trackId', count: { $sum: 1 } } },
        { $sort: { count: -1 } },
        { $limit: 10 }
      ]);

      const trackIds = events.map(e => e._id);
      const songs = await Song.find({ _id: { $in: trackIds } }).select('moods activities genreIds');

      const moodCounts = {};
      const actCounts = {};
      songs.forEach(s => {
        (s.moods || []).forEach(m => { moodCounts[m] = (moodCounts[m] || 0) + 1; });
        (s.activities || []).forEach(a => { actCounts[a] = (actCounts[a] || 0) + 1; });
      });

      const topMoods = Object.keys(moodCounts).sort((a, b) => moodCounts[b] - moodCounts[a]).slice(0, 3);
      const topActs = Object.keys(actCounts).sort((a, b) => actCounts[b] - actCounts[a]).slice(0, 3);

      return {
        favoriteMoods: topMoods.length > 0 ? topMoods : ['calm', 'energetic'],
        topActivities: topActs.length > 0 ? topActs : ['relax', 'study']
      };
    } catch (e) {
      return { favoriteMoods: ['calm'], topActivities: ['relax'] };
    }
  }

  /**
   * Tạo playlist từ tool call
   */
  static async createPlaylist(userId, name, trackIds = []) {
    if (!userId) {
      return { success: false, message: 'Cần đăng nhập để lưu playlist' };
    }
    try {
      const playlist = await Playlist.create({
        title: name || 'AI Mood Playlist',
        creatorId: userId,
        description: 'Tạo tự động bởi AI Mood Chatbot',
        isPublic: true,
        totalTracks: trackIds.length
      });

      if (trackIds.length > 0) {
        const tracksToInsert = trackIds.map((tid, idx) => ({
          playlistId: playlist._id,
          songId: tid,
          order: idx
        }));
        await PlaylistTrack.insertMany(tracksToInsert);
      }

      return { success: true, playlistId: playlist._id, name: playlist.title, count: trackIds.length };
    } catch (e) {
      return { success: false, error: e.message };
    }
  }

  /**
   * Xử lý hội thoại AI với Tool Calling
   */
  static async executeChat(userId, userMessage, conversationHistory = []) {
    const rawMessage = (userMessage || '').trim();

    // 1. Kiểm tra an toàn tâm lý
    const crisisCheck = this.checkCrisis(rawMessage);
    if (crisisCheck.isCrisis) {
      return {
        text: crisisCheck.reply,
        reply: crisisCheck.reply,
        trackIds: [],
        tracks: [],
        suggestedSongs: [],
        suggestedReplies: ['Bật giai điệu êm dịu 🌿', 'Tôi muốn nghỉ ngơi ☕', 'Đổi chủ đề khác'],
        provider: 'safety_guard'
      };
    }

    // Trích xuất ID bài hát đã gợi ý để loại trừ tránh lặp lại
    const excludeIds = [];
    if (Array.isArray(conversationHistory)) {
      conversationHistory.forEach(item => {
        if (item.trackIds && Array.isArray(item.trackIds)) {
          item.trackIds.forEach(id => excludeIds.push(String(id)));
        }
        if (item.suggestedSongs && Array.isArray(item.suggestedSongs)) {
          item.suggestedSongs.forEach(s => excludeIds.push(String(s._id || s.id)));
        }
      });
    }

    // 2. Tra cứu bằng CatalogBrain từ tài liệu & CSDL thật của ứng dụng
    // Nếu người dùng yêu cầu ca sĩ, bài hát cụ thể hoặc khủng hoảng tâm lý, trả lời chuẩn xác ngay lập tức
    const brainCheck = await CatalogBrain.processQuery(rawMessage, { userId, excludeIds });
    if (
      brainCheck.intent === 'artist_search' ||
      brainCheck.intent === 'song_search' ||
      brainCheck.intent === 'artist_not_in_catalog' ||
      brainCheck.intent === 'crisis_safety'
    ) {
      const trackIds = brainCheck.tracks.map(t => String(t.id));
      return {
        text: brainCheck.reply,
        reply: brainCheck.reply,
        trackIds,
        tracks: brainCheck.tracks,
        suggestedSongs: brainCheck.tracks,
        suggestedReplies: brainCheck.suggestedReplies,
        provider: 'intelligent_catalog_brain'
      };
    }

    // 3. Chạy Tool Calling qua Gemini API nếu có API Key
    const apiKey = config.geminiApiKey;
    const modelName = process.env.LLM_MODEL || 'gemini-2.5-flash';

    if (apiKey) {
      try {
        const genAI = new GoogleGenerativeAI(apiKey);
        const model = genAI.getGenerativeModel({
          model: modelName,
          tools: CHAT_TOOLS,
          systemInstruction: SYSTEM_PROMPT
        });

        // Xây dựng history cho Gemini
        const chat = model.startChat({
          history: []
        });

        // Gửi tin nhắn của người dùng
        const result = await chat.sendMessage(rawMessage);
        let response = result.response;

        let functionCalls = response.functionCalls();
        let collectedTracks = [];
        let maxTurns = 4;

        // Vòng lặp giải quyết Tool Calling
        while (functionCalls && functionCalls.length > 0 && maxTurns > 0) {
          maxTurns--;
          const functionResponses = [];

          for (const call of functionCalls) {
            const toolName = call.name;
            const args = call.args || {};

            if (toolName === 'search_tracks') {
              const tracks = await searchTracks({
                moods: args.moods || [],
                energy: args.energy || null,
                activities: args.activities || [],
                excludeIds,
                limit: args.limit || 5
              });

              tracks.forEach(t => {
                if (!collectedTracks.some(ct => String(ct.id) === String(t.id))) {
                  collectedTracks.push(t);
                }
              });

              functionResponses.push({
                functionResponse: {
                  name: toolName,
                  response: {
                    tracks: tracks.map(t => ({
                      id: String(t.id),
                      title: t.title,
                      artist: t.artist,
                      moods: t.moods,
                      activities: t.activities,
                      description: t.description
                    }))
                  }
                }
              });
            } else if (toolName === 'get_user_taste') {
              const taste = await this.getUserTaste(userId);
              functionResponses.push({
                functionResponse: {
                  name: toolName,
                  response: {
                    content: taste
                  }
                }
              });
            } else if (toolName === 'create_playlist') {
              const res = await this.createPlaylist(userId, args.name, args.trackIds);
              functionResponses.push({
                functionResponse: {
                  name: toolName,
                  response: {
                    content: res
                  }
                }
              });
            }
          }

          // Gửi kết quả tool lại cho Gemini
          const nextResult = await chat.sendMessage(functionResponses);
          response = nextResult.response;
          functionCalls = response.functionCalls();
        }

        const replyText = response.text() || 'Dưới đây là một số bài hát mình gợi ý cho bạn:';
        const trackIds = collectedTracks.map(t => String(t.id));

        return {
          text: replyText,
          reply: replyText,
          trackIds,
          tracks: collectedTracks,
          suggestedSongs: collectedTracks,
          suggestedReplies: ['Thêm bài khác 🎧', 'Sôi động hơn một chút 🔥', 'Nhẹ nhàng hơn 🌿'],
          provider: 'gemini_tool_calling'
        };
      } catch (err) {
        console.warn('Gemini Tool Calling error, fallbacking to direct searchTracks:', err.message);
      }
    }

    // 4. Heuristic / Direct CatalogBrain Search Fallback (Khi mạng lỗi, quota Gemini hết hoặc không có API Key)
    const fallbackResult = await CatalogBrain.processQuery(rawMessage, { userId, excludeIds });
    const trackIds = (fallbackResult.tracks || []).map(t => String(t.id));

    return {
      text: fallbackResult.reply,
      reply: fallbackResult.reply,
      trackIds,
      tracks: fallbackResult.tracks,
      suggestedSongs: fallbackResult.tracks,
      suggestedReplies: fallbackResult.suggestedReplies || ['Thử gợi ý khác 🎵', 'Bài này hay lắm ❤️', 'Thêm bài sôi động 🔥'],
      provider: 'intelligent_catalog_brain'
    };
  }
}

module.exports = ToolCallingChatService;
