const { GoogleGenerativeAI } = require('@google/generative-ai');
require('dotenv').config();

const apiKey = process.env.GEMINI_API_KEY;
let genAI = null;
if (apiKey) {
  genAI = new GoogleGenerativeAI(apiKey);
}

class EmbeddingService {
  /**
   * Tạo chuỗi văn bản ngữ nghĩa (Semantic Text) hoàn chỉnh của bài hát để sinh Vector
   */
  static generateSongSemanticText(song, artistName = '', genreNames = [], tags = []) {
    const parts = [];

    parts.push(`Bài hát: ${song.title}`);
    if (artistName) {
      parts.push(`Nghệ sĩ: ${artistName}`);
    }
    if (genreNames && genreNames.length > 0) {
      parts.push(`Thể loại nhạc: ${genreNames.join(', ')}`);
    }
    const moods = song.moods || song.mood || [];
    if (moods.length > 0) {
      parts.push(`Tâm trạng cảm xúc: ${moods.join(', ')}`);
    }
    const activities = song.activities || [];
    if (activities.length > 0) {
      parts.push(`Phù hợp hoạt động: ${activities.join(', ')}`);
    }
    if (tags && tags.length > 0) {
      parts.push(`Thẻ phân loại: ${tags.join(', ')}`);
    }
    if (song.description) {
      parts.push(`Mô tả phong cách: ${song.description}`);
    }
    if (song.lyrics) {
      // Lấy 250 ký tự đầu của lời bài hát để tăng cường ngữ nghĩa cảm xúc
      const cleanLyrics = song.lyrics
        .replace(/\[\d{2}:\d{2}\.\d{2}\]/g, '')
        .replace(/\s+/g, ' ')
        .trim()
        .slice(0, 250);
      if (cleanLyrics) {
        parts.push(`Lời bài hát: ${cleanLyrics}`);
      }
    }

    return parts.join('. ') + '.';
  }

  /**
   * Sinh vector embedding 768 chiều từ chuỗi văn bản sử dụng Gemini Embedding Model
   * @param {string} text 
   * @param {number} retries Số lần thử lại nếu nghẽn mạng
   * @returns {Promise<number[]>} Mảng 768 số thực
   */
  static async generateEmbedding(text, retries = 3) {
    if (!text || typeof text !== 'string' || text.trim() === '') {
      throw new Error('Embedding input text cannot be empty');
    }

    if (!genAI) {
      throw new Error('GEMINI_API_KEY is not configured in backend/.env');
    }

    const cleanText = text.slice(0, 2048); // Giới hạn ký tự hợp lý

    for (let attempt = 1; attempt <= retries; attempt++) {
      try {
        const model = genAI.getGenerativeModel({ model: 'gemini-embedding-001' });
        const result = await model.embedContent({
          content: { parts: [{ text: cleanText }] },
          outputDimensionality: 768
        });

        if (result && result.embedding && Array.isArray(result.embedding.values)) {
          return result.embedding.values;
        }

        throw new Error('Invalid embedding response from Gemini API');
      } catch (err) {
        if (attempt === retries) {
          throw new Error(`Failed to generate embedding after ${retries} attempts: ${err.message}`);
        }
        // Đợi tăng dần theo hàm mũ nếu bị rate limit
        await new Promise(r => setTimeout(r, attempt * 1200));
      }
    }
  }
}

module.exports = EmbeddingService;
