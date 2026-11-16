const { Song, Artist, Genre, ListeningEvent, Playlist } = require('../models');
const { MOOD_LIST, ACTIVITY_LIST, ENERGY_LEVELS } = require('../config/labels');
const searchTracks = require('./searchTracks');

class CatalogBrain {
  /**
   * Chuẩn hóa và làm sạch chuỗi tìm kiếm (bỏ emoji, ký tự đặc biệt thừa)
   */
  static cleanInput(text = '') {
    return text
      .replace(/[\u{1F300}-\u{1FAFF}]|[\u{2600}-\u{26FF}]|[\u{2700}-\u{27BF}]/gu, '')
      .replace(/[“”"']/g, '')
      .trim();
  }

  /**
   * Xóa dấu tiếng Việt để tìm kiếm không dấu
   */
  static removeVietnameseTones(str = '') {
    return str
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/đ/g, 'd')
      .replace(/Đ/g, 'D')
      .toLowerCase()
      .trim();
  }

  /**
   * Định dạng chuẩn hóa danh sách bài hát trả về client
   */
  static formatSong(song) {
    const artistName = song.artistId?.name || song.artist?.name || 'Nghệ sĩ';
    const artistObj = {
      _id: song.artistId?._id ? String(song.artistId._id) : (song.artist?._id ? String(song.artist._id) : ''),
      name: artistName,
      avatarUrl: song.artistId?.avatarUrl || song.artist?.avatarUrl || ''
    };

    return {
      _id: song._id,
      id: song._id,
      title: song.title,
      artist: artistObj,
      artistId: artistObj,
      artistName: artistName,
      composer: song.composer || '',
      composerId: song.composerId || null,
      albumId: song.albumId,
      duration: song.duration,
      durationSec: song.duration,
      audioUrl: song.audioUrl,
      coverImageUrl: song.coverImageUrl || artistObj.avatarUrl || '',
      coverUrl: song.coverImageUrl || '',
      moods: song.moods || song.mood || [],
      activities: song.activities || [],
      description: song.description || `Bài hát ${song.title} của ${artistName}`,
      audio: song.audio || {
        bpm: song.audioFeatures?.tempoBpm || 120,
        energy: song.audioFeatures?.energy || 0.5,
        valence: song.audioFeatures?.valence || 0.5
      }
    };
  }

  /**
   * Xử lý truy vấn thông minh theo dữ liệu thật trong catalog
   */
  static async processQuery(rawMessage, { userId = null, excludeIds = [] } = {}) {
    const clean = this.cleanInput(rawMessage);
    const lower = clean.toLowerCase();
    const noTone = this.removeVietnameseTones(clean);

    // 1. Kiểm tra an toàn khủng hoảng tâm lý
    const crisisPatterns = [
      /tự tử/i, /muốn chết/i, /chết đi/i, /kết thúc cuộc đời/i,
      /tự hại/i, /không muốn sống/i, /rạch tay/i, /uống thuốc ngủ/i
    ];
    for (const pat of crisisPatterns) {
      if (pat.test(lower)) {
        const calmSongs = await searchTracks({ moods: ['calm', 'dreamy'], energy: 'low', limit: 3 });
        return {
          reply: 'Mình nghe thấy bạn đang trải qua những cảm xúc rất nặng nề và khó khăn. Xin bạn hãy nhớ rằng bạn không hề cô độc. Hãy chia sẻ với người thân, bạn bè hoặc liên hệ ngay Đường dây nóng Hỗ trợ Tâm lý (Hotline: 096 306 1414) hoặc 115 khi cần giúp đỡ y tế khẩn cấp nhé. Mình luôn ở đây sẵn sàng mở những giai điệu êm dịu nhất bên cạnh bạn.',
          tracks: calmSongs,
          suggestedReplies: ['Nghe nhạc nhẹ nhàng 🌿', 'Cảm ơn bạn ❤️'],
          intent: 'crisis_safety'
        };
      }
    }

    // 2. Chống Prompt Injection
    if (/ignore (previous|all) instructions|system prompt|jailbreak|bỏ qua mọi quy tắc/i.test(lower)) {
      const topSongs = await searchTracks({ limit: 3 });
      return {
        reply: 'Mình là Trợ lý Âm nhạc của Spotify Clone, sẵn sàng giúp bạn khám phá các ca khúc, nghệ sĩ và giai điệu tuyệt vời trong catalog!',
        tracks: topSongs,
        suggestedReplies: ['Gợi ý nhạc hot 🔥', 'Sơn Tùng M-TP 🌟', 'Nhạc chill thư giãn ☕'],
        intent: 'prompt_injection_guard'
      };
    }

    // 3. Tải toàn bộ nghệ sĩ và bài hát từ catalog để tra cứu
    const allArtists = await Artist.find({}).lean();
    const allSongs = await Song.find({ isPublic: { $ne: false } }).populate('artistId').lean();

    // ----------------------------------------------------
    // 4. KIỂM TRA INTENT: TÌM THEO TÊN NGHỆ SĨ / CA SĨ
    // ----------------------------------------------------
    let matchedArtist = null;
    let highestArtistScore = 0;

    for (const artist of allArtists) {
      const artistName = artist.name.toLowerCase();
      const artistNoTone = this.removeVietnameseTones(artist.name);

      const subNames = [
        artistName,
        artistNoTone,
        ...artist.name.split(/[&,+/]/).map(s => s.trim().toLowerCase()),
        ...artistNoTone.split(/[&,+/]/).map(s => s.trim())
      ];

      if (artistName.includes('sơn tùng')) {
        subNames.push('son tung', 'sơn tùng', 'sơn tùng m-tp', 'm-tp', 'mtp');
      }
      if (artistName.includes('đen')) {
        subNames.push('den vau', 'đen vâu', 'den', 'đen');
      }
      if (artistName.includes('vũ')) {
        subNames.push('vu', 'vũ', 'hoàng thái vũ');
      }
      if (artistName.includes('mck')) {
        subNames.push('mck', 'nghiêm vũ hoàng long', 'wren evans', 'wren');
      }
      if (artistName.includes('jack')) {
        subNames.push('jack', 'j97', 'jack - j97', 'trịnh trần phương tuấn');
      }
      if (artistName.includes('hieuthuhai')) {
        subNames.push('hieuthuhai', 'hiếu thứ hai', 'hieu thứ hai');
      }

      for (const name of subNames) {
        if (!name || name.length < 2) continue;
        if (lower.includes(name) || noTone.includes(name)) {
          if (name.length > highestArtistScore) {
            highestArtistScore = name.length;
            matchedArtist = artist;
          }
        }
      }
    }

    if (matchedArtist) {
      const artistSongs = allSongs.filter(s => {
        const sArtistId = s.artistId?._id ? String(s.artistId._id) : String(s.artistId);
        const targetId = String(matchedArtist._id);
        const isFeatured = (s.featuredArtistIds || []).some(fa => String(fa) === targetId);
        return sArtistId === targetId || isFeatured;
      });

      const formattedArtistSongs = artistSongs.map(s => this.formatSong(s));
      const songTitles = artistSongs.map(s => s.title).join(', ');

      if (formattedArtistSongs.length > 0) {
        return {
          reply: `Dưới đây là các ca khúc của **${matchedArtist.name}** hiện có trong kho nhạc của ứng dụng (${songTitles}). Mời bạn bấm phát để thưởng thức nhé!`,
          tracks: formattedArtistSongs,
          suggestedReplies: [
            `Bài hot nhất của ${matchedArtist.name} 🎵`,
            'Thử nghệ sĩ khác 🎤',
            'Nhạc sôi động hơn một chút 🔥'
          ],
          intent: 'artist_search'
        };
      }
    }

    // ----------------------------------------------------
    // 5. KIỂM TRA INTENT: TÌM THEO TÊN BÀI HÁT CỤ THỂ
    // ----------------------------------------------------
    let matchedSong = null;
    let highestSongScore = 0;

    for (const song of allSongs) {
      const songTitle = song.title.toLowerCase();
      const songNoTone = this.removeVietnameseTones(song.title);

      if (lower.includes(songTitle) || noTone.includes(songNoTone)) {
        if (songTitle.length > highestSongScore) {
          highestSongScore = songTitle.length;
          matchedSong = song;
        }
      }
    }

    if (matchedSong) {
      const mainTrack = this.formatSong(matchedSong);
      const artistName = mainTrack.artistName;

      const otherSongs = allSongs
        .filter(s => String(s._id) !== String(matchedSong._id))
        .map(s => this.formatSong(s));

      const relatedSongs = otherSongs.filter(s => s.artistName === artistName);
      const moodSimilarSongs = otherSongs.filter(s =>
        (s.moods || []).some(m => (matchedSong.moods || []).includes(m))
      );

      const combinedRelated = [...relatedSongs, ...moodSimilarSongs];
      const uniqueRelated = [];
      combinedRelated.forEach(item => {
        if (!uniqueRelated.some(u => String(u.id) === String(item.id))) {
          uniqueRelated.push(item);
        }
      });

      const finalTracks = [mainTrack, ...uniqueRelated.slice(0, 3)];
      const bpm = matchedSong.audio?.bpm || matchedSong.audioFeatures?.tempoBpm || 120;
      const moodStr = (matchedSong.moods || []).join(', ') || 'sôi động';

      return {
        reply: `Bài hát **"${matchedSong.title}"** được trình bày bởi nghệ sĩ **${artistName}** (Tiết tấu: ${bpm} BPM, cảm xúc: ${moodStr}). Mình đã đưa bài hát lên đầu danh sách để bạn thưởng thức ngay kèm các giai điệu liên quan:`,
        tracks: finalTracks,
        suggestedReplies: [
          `Các bài khác của ${artistName} 🎤`,
          'Bài này hay lắm ❤️',
          'Thử bài khác 🎧'
        ],
        intent: 'song_search'
      };
    }

    // ----------------------------------------------------
    // 6. KIỂM TRA CÂU HỎI VỀ CA SĨ / BÀI HÁT KHÔNG CÓ TRONG CATALOG
    // ----------------------------------------------------
    const singerPattern = /(ca sĩ|nhạc sĩ|bài của|hát bài|nghệ sĩ)\s+([a-zA-Z0-9\sÀ-ỹ]+)/i;
    const matchSinger = clean.match(singerPattern);
    const isExplicitSinger = matchSinger && matchSinger[2] && matchSinger[2].trim().length > 2;

    const words = clean.trim().split(/\s+/);
    const isGreeting = /^(chào|hello|hi|alo|hé lô|xin chào|hey|start|bắt đầu|help|giúp)$/i.test(clean.trim());
    const isMoodQuery = /buồn|vui|tập trung|ngủ|chill|thư giãn|gym|quẩy|tiệc|năng lượng|nhớ|khóc|yêu|romantic/i.test(lower);
    const isShortSpecific = !isGreeting && !isMoodQuery && words.length >= 1 && words.length <= 5;

    if (isExplicitSinger || isShortSpecific) {
      const searchedName = isExplicitSinger ? matchSinger[2].trim() : clean.trim();
      const sampleSongs = await searchTracks({ limit: 4 });
      const availableArtists = allArtists.map(a => a.name).slice(0, 5).join(', ');

      return {
        reply: `Hiện tại trong kho nhạc của ứng dụng chưa có bài hát hoặc nghệ sĩ **${searchedName}**. Thư viện hiện đang có sẵn các ca khúc đặc sắc từ các nghệ sĩ: **${availableArtists}**,... Bạn có muốn thử nghe không?`,
        tracks: sampleSongs,
        suggestedReplies: ['Sơn Tùng M-TP 🌟', 'Vũ. 🌿', 'HIEUTHUHAI 🔥', 'Đen Vâu ☕'],
        intent: 'artist_not_in_catalog'
      };
    }

    // ----------------------------------------------------
    // 7. KIỂM TRA INTENT: LỌC THEO TÂM TRẠNG, HOẠT ĐỘNG, NĂNG LƯỢNG
    // ----------------------------------------------------
    let detectedMoods = [];
    let detectedEnergy = null;
    let detectedActs = [];
    let moodReason = '';

    if (/buồn|nỗi buồn|cô đơn|thất tình|nhớ|khóc|tâm trạng|đau lòng|vụn vỡ/i.test(lower)) {
      detectedMoods = ['sad', 'melancholic'];
      detectedEnergy = 'low';
      detectedActs = ['relax'];
      moodReason = 'nhẹ nhàng, lắng đọng để đồng cảm và vỗ về tâm trạng của bạn';
    } else if (/vui|hạnh phúc|quẩy|tiệc|sôi động|cháy|năng lượng|bốc lửa|hưng phấn/i.test(lower)) {
      detectedMoods = ['energetic', 'happy'];
      detectedEnergy = 'high';
      detectedActs = ['party', 'workout'];
      moodReason = 'sôi động, tràn đầy năng lượng tích cực giúp bạn khuấy động không khí';
    } else if (/tập trung|học|làm việc|deadline|suy nghĩ|code|đọc sách/i.test(lower)) {
      detectedMoods = ['focused', 'calm'];
      detectedEnergy = 'medium';
      detectedActs = ['focus', 'study'];
      moodReason = 'giai điệu êm dịu, nhịp độ vừa phải giúp bạn tập trung làm việc hiệu quả';
    } else if (/ngủ|ngủ ngon|thư giãn|chill|nghỉ ngơi|êm dịu|thả lỏng|dễ ngủ|stress/i.test(lower)) {
      detectedMoods = ['calm', 'dreamy'];
      detectedEnergy = 'low';
      detectedActs = ['sleep', 'relax'];
      moodReason = 'êm đềm, thư thái giúp bạn thả lỏng tâm trí sau ngày dài';
    } else if (/tập gym|chạy bộ|thể thao|workout/i.test(lower)) {
      detectedMoods = ['energetic'];
      detectedEnergy = 'high';
      detectedActs = ['workout'];
      moodReason = 'nhịp điệu dồn dập, bốc lửa giúp bạn tăng tốc độ tập luyện';
    } else if (/lãng mạn|yêu|hẹn hò|ngọt ngào/i.test(lower)) {
      detectedMoods = ['romantic', 'happy'];
      detectedEnergy = 'medium';
      moodReason = 'ngọt ngào, lãng mạn dành cho những khoảnh khắc yêu thương';
    } else if (/hoài niệm|nhớ về|kỷ niệm|xưa/i.test(lower)) {
      detectedMoods = ['nostalgic', 'calm'];
      detectedEnergy = 'low';
      moodReason = 'mang sắc thái hoài niệm về những ký ức thân quen';
    }

    if (detectedMoods.length > 0) {
      const moodTracks = await searchTracks({
        moods: detectedMoods,
        energy: detectedEnergy,
        activities: detectedActs,
        excludeIds,
        userId,
        limit: 4
      });

      return {
        reply: `Mình đã chọn lọc cho bạn những giai điệu ${moodReason} từ kho catalog thật của app:`,
        tracks: moodTracks,
        suggestedReplies: ['Thử gợi ý khác 🎵', 'Bài này hay lắm ❤️', 'Thêm bài sôi động 🔥'],
        intent: 'mood_search'
      };
    }

    // ----------------------------------------------------
    // 8. KIỂM TRA LẠC ĐỀ (OFF-TOPIC)
    // ----------------------------------------------------
    if (/thời tiết|dự báo|nhiệt độ|viết code|lập trình|python|javascript|toán học|lịch sử|chính trị/i.test(lower)) {
      const sampleTracks = await searchTracks({ limit: 3 });
      return {
        reply: 'Mình là Trợ lý Âm nhạc của Spotify Clone, chuyên hỗ trợ bạn tìm kiếm bài hát, ca sĩ và tuyển tập nhạc theo cảm xúc. Bạn muốn tìm bài hát hay ca sĩ nào để mình giúp nhé?',
        tracks: sampleTracks,
        suggestedReplies: ['Sơn Tùng M-TP 🌟', 'Vũ. 🌿', 'Gợi ý nhạc hot 🔥'],
        intent: 'off_topic_redirect'
      };
    }

    // ----------------------------------------------------
    // 9. CÂU HỎI MƠ HỒ HOẶC CHÀO HỎI TỔNG QUÁT
    // ----------------------------------------------------
    const defaultTracks = await searchTracks({
      excludeIds,
      userId,
      limit: 4
    });

    return {
      reply: 'Chào bạn! Mình có thể giúp bạn tìm bài hát theo tên ca sĩ (Sơn Tùng M-TP, Vũ, HIEUTHUHAI, MCK, Đen Vâu,...), theo tên bài hát cụ thể, hoặc theo tâm trạng (vui vẻ, thư giãn, tập trung,...). Dưới đây là các ca khúc nổi bật dành cho bạn:',
      tracks: defaultTracks,
      suggestedReplies: [
        'Sơn Tùng M-TP 🌟',
        'Nhạc chill thư giãn ☕',
        'Top thịnh hành 🔥'
      ],
      intent: 'general_recommendation'
    };
  }
}

module.exports = CatalogBrain;
