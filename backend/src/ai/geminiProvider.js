const config = require('../config/environment');
const logger = require('../utils/logger');

class GeminiProvider {
  /**
   * Trích xuất tiêu chí âm nhạc có cấu trúc (Structured Music Criteria) từ Prompt tự nhiên
   */
  static async extractMusicCriteria(userPrompt) {
    const startTime = Date.now();

    // Fallback nếu không có API Key
    if (!config.geminiApiKey) {
      logger.warn('GEMINI_API_KEY is not set. Using intelligent heuristic fallback parser.');
      return this.fallbackHeuristicParser(userPrompt, startTime);
    }

    try {
      const { GoogleGenerativeAI } = require('@google/generative-ai');
      const genAI = new GoogleGenerativeAI(config.geminiApiKey);
      const model = genAI.getGenerativeModel({
        model: 'gemini-1.5-flash',
        generationConfig: {
          responseMimeType: 'application/json',
          temperature: 0.2
        },
        systemInstruction: `
Bạn là chuyên gia phân loại âm nhạc của hệ thống Spotify.
Nhiệm vụ: Phân tích prompt ngôn ngữ tự nhiên của người dùng và trích xuất thành định dạng JSON tiêu chuẩn.
Chỉ trả về JSON thuần tuý:
{
  "title": "Tên playlist ngắn gọn, hấp dẫn (Tiếng Việt)",
  "description": "Mô tả playlist 1-2 câu",
  "mood": ["chill", "energetic", "sad", "happy", "focus", "romantic", "party", "acoustic"],
  "genres": ["Pop", "Lo-Fi", "R&B", "Ballad", "Hip Hop", "Electronic"],
  "tempo": "slow" | "medium" | "fast",
  "keywords": ["từ khoá bài hát", "chủ đề", "nghệ sĩ nếu có"]
}
`
      });

      const result = await model.generateContent(userPrompt);
      const latencyMs = Date.now() - startTime;
      let text = result.response.text() || '';
      text = text.replace(/```json/gi, '').replace(/```/g, '').trim();

      const parsed = JSON.parse(text);

      return {
        criteria: {
          title: parsed.title || 'AI Curated Playlist',
          description: parsed.description || `Generated from prompt: "${userPrompt}"`,
          mood: Array.isArray(parsed.mood) ? parsed.mood : ['chill'],
          genres: Array.isArray(parsed.genres) ? parsed.genres : ['Lo-Fi'],
          tempo: parsed.tempo || 'medium',
          keywords: Array.isArray(parsed.keywords) ? parsed.keywords : []
        },
        provider: 'gemini-1.5-flash',
        latencyMs,
        tokensUsed: { promptTokens: 0, completionTokens: 0, totalTokens: 0 }
      };
    } catch (error) {
      logger.error('Gemini API call failed, falling back to heuristic parser:', error.message);
      return this.fallbackHeuristicParser(userPrompt, startTime);
    }
  }

  /**
   * Bộ phân tích dự phòng thông minh (Heuristic Rule-Based Fallback)
   * Đảm bảo app vẫn hoạt động 100% mượt mà ngay cả khi offline hoặc chưa nhập Gemini API Key
   */
  static fallbackHeuristicParser(prompt, startTime) {
    const lower = prompt.toLowerCase();
    const moods = [];
    const genres = [];
    let tempo = 'medium';

    if (lower.includes('chill') || lower.includes('thư giãn') || lower.includes('ngủ') || lower.includes('đêm')) {
      moods.push('chill');
      tempo = 'slow';
    }
    if (lower.includes('buồn') || lower.includes('tâm trạng') || lower.includes('mưa') || lower.includes('chia tay')) {
      moods.push('sad');
      genres.push('Indie & Ballad');
      tempo = 'slow';
    }
    if (lower.includes('tập trung') || lower.includes('học') || lower.includes('code') || lower.includes('làm việc')) {
      moods.push('focus');
      genres.push('Lo-Fi Chill');
    }
    if (lower.includes('vui') || lower.includes('năng lượng') || lower.includes('gym') || lower.includes('chạy bộ') || lower.includes('workout')) {
      moods.push('energetic');
      genres.push('Electronic / EDM', 'V-Pop');
      tempo = 'fast';
    }
    if (lower.includes('yêu') || lower.includes('lãng mạn') || lower.includes('crush') || lower.includes('hẹn hò')) {
      moods.push('romantic');
      genres.push('R&B / Soul', 'V-Pop');
    }
    if (lower.includes('tiệc') || lower.includes('party') || lower.includes('quẩy')) {
      moods.push('party');
      genres.push('Electronic / EDM', 'Hip Hop & Rap');
      tempo = 'fast';
    }

    if (moods.length === 0) moods.push('chill');
    if (genres.length === 0) genres.push('Lo-Fi Chill', 'V-Pop');

    const words = prompt
      .replace(/[^\p{L}\p{N}\s]/gu, '')
      .split(/\s+/)
      .filter((w) => w.length > 2);

    return {
      criteria: {
        title: `Giai điệu ${moods[0].toUpperCase()} - ${genres[0] || 'V-Pop'}`,
        description: `Danh sách bài hát được tuyển chọn phù hợp với: "${prompt}"`,
        mood: moods,
        genres,
        tempo,
        keywords: words.slice(0, 5)
      },
      provider: 'heuristic-rule-engine',
      latencyMs: Date.now() - startTime,
      tokensUsed: { promptTokens: 0, completionTokens: 0, totalTokens: 0 }
    };
  }
}

module.exports = GeminiProvider;
