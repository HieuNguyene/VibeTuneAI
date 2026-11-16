"""
Pipeline Gắn Mood và Activity Tự Động (Tag Moods)
Đọc các thông số âm học THẬT từ MongoDB, gọi LLM để:
- Chọn moods (trong MOOD_LIST)
- Chọn activities (trong ACTIVITY_LIST)
- Tạo description tiếng Việt 1-2 câu
Không ghi đè các bài có tagSource == 'manual'.
"""

import os
import sys
import json
import argparse
import requests
from pymongo import MongoClient
from dotenv import load_dotenv

# Nhập danh sách nhãn chuẩn
from labels import MOOD_LIST, ACTIVITY_LIST, ENERGY_LEVELS

ENV_PATH = os.path.join(os.path.dirname(__file__), '../.env')
load_dotenv(dotenv_path=ENV_PATH)

MONGODB_URI = os.getenv('MONGODB_URI', 'mongodb://127.0.0.1:27017/music_app')
GEMINI_API_KEY = os.getenv('GEMINI_API_KEY', '')
LLM_MODEL = os.getenv('LLM_MODEL', 'gemini-2.5-flash')

def infer_tags_fallback(title: str, artist: str, audio: dict) -> dict:
    """
    Hàm quy luật dự phòng (rule-based fallback) khi API LLM gặp lỗi mạng/rate limit.
    Đảm bảo 100% bài hát luôn có nhãn chuẩn và mô tả tiếng Việt mạch lạc.
    """
    bpm = audio.get('bpm', 120)
    energy = audio.get('energy', 0.5)
    valence = audio.get('valence', 0.5)
    danceability = audio.get('danceability', 0.5)
    acousticness = audio.get('acousticness', 0.5)

    moods = []
    activities = []

    # Suy luận mood từ năng lượng, hóa âm và nhịp độ
    if energy >= 0.75 and valence >= 0.6:
        moods.extend(['energetic', 'happy'])
        activities.extend(['workout', 'party'])
    elif energy >= 0.7:
        moods.extend(['energetic'])
        activities.extend(['workout', 'drive'])
    elif energy <= 0.45 and valence <= 0.45:
        moods.extend(['sad', 'melancholic'])
        activities.extend(['relax', 'sleep'])
    elif acousticness >= 0.6:
        moods.extend(['calm', 'nostalgic'])
        activities.extend(['focus', 'study', 'relax'])
    elif valence >= 0.6:
        moods.extend(['happy', 'dreamy'])
        activities.extend(['commute', 'relax'])
    else:
        moods.extend(['focused', 'romantic'])
        activities.extend(['study', 'focus'])

    if danceability >= 0.75:
        if 'party' not in activities: activities.append('party')
    if bpm <= 80:
        if 'sleep' not in activities and len(activities) < 3: activities.append('sleep')

    # Giữ tối đa 3 mood, 3 activity
    moods = list(dict.fromkeys([m for m in moods if m in MOOD_LIST]))[:3]
    activities = list(dict.fromkeys([a for a in activities if a in ACTIVITY_LIST]))[:3]
    if not moods: moods = ['calm']
    if not activities: activities = ['relax']

    # Tạo mô tả tiếng Việt
    mood_str = ", ".join(moods)
    act_str = ", ".join(activities)
    desc = f"Giai điệu nhịp độ {bpm} BPM với mức năng lượng {energy:.2f}, phù hợp với cảm xúc {mood_str} và không gian {act_str}."

    return {
        "moods": moods,
        "activities": activities,
        "description": desc
    }

def call_gemini_tagging(title: str, artist: str, genre_names: list, audio: dict) -> dict:
    """
    Gọi Gemini API để gắn nhãn cảm xúc và sinh lời bình ngắn tiếng Việt.
    """
    if not GEMINI_API_KEY:
        return infer_tags_fallback(title, artist, audio)

    prompt = f"""Bạn là chuyên gia phân loại âm nhạc và tâm trạng học.
Nhiệm vụ của bạn: Dựa trên SỐ LIỆU ĐO ĐẠC ÂM HỌC THỰC TẾ của bài hát, hãy chọn nhãn cảm xúc (moods), hoạt động phù hợp (activities) và viết lời mô tả ngắn gọn (1-2 câu tiếng Việt).

THÔNG TIN BÀI HÁT:
- Tên bài: {title}
- Nghệ sĩ: {artist}
- Thể loại: {', '.join(genre_names) if genre_names else 'Pop'}
- Số liệu sóng âm thực tế:
  + BPM: {audio.get('bpm')}
  + Mức năng lượng (Energy): {audio.get('energy')} (0.0 đến 1.0)
  + Độ tươi vui/tích cực (Valence): {audio.get('valence')} (0.0 đến 1.0)
  + Độ bắt nhịp (Danceability): {audio.get('danceability')} (0.0 đến 1.0)
  + Chất mộc mạc (Acousticness): {audio.get('acousticness')} (0.0 đến 1.0)

DANH SÁCH NHÃN HỢP LỆ (BẮT BUỘC CHỈ ĐƯỢC CHỌN TRONG DANH SÁCH NÀY):
- MOOD_LIST: {json.dumps(MOOD_LIST)}
- ACTIVITY_LIST: {json.dumps(ACTIVITY_LIST)}

QUY TẮC BẮT BUỘC:
1. Chỉ suy luận từ số liệu âm học được cung cấp, không bịa thông tin về bài hát.
2. 'moods' chứa từ 1 đến 3 nhãn chính xác từ MOOD_LIST.
3. 'activities' chứa từ 1 đến 3 nhãn chính xác từ ACTIVITY_LIST.
4. 'description': Đúng 1-2 câu tiếng Việt tự nhiên, ấm áp, giải thích vì sao bài hát hợp với tâm trạng đó dựa trên giai điệu/năng lượng đã đo.
5. CHỈ TRẢ VỀ DUY NHẤT 1 ĐỐI TƯỢNG JSON HỢP LỆ theo cấu trúc:
{{"moods": ["..."], "activities": ["..."], "description": "..."}}
"""

    url = f"https://generativelanguage.googleapis.com/v1beta/models/{LLM_MODEL}:generateContent?key={GEMINI_API_KEY}"
    headers = {"Content-Type": "application/json"}
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.2,
            "maxOutputTokens": 300,
            "responseMimeType": "application/json"
        }
    }

    try:
        resp = requests.post(url, headers=headers, json=payload, timeout=12)
        if resp.status_code == 200:
            data = resp.json()
            raw_text = data["candidates"][0]["content"]["parts"][0]["text"].strip()
            # Lọc bỏ markdown code fences nếu có
            if raw_text.startswith("```"):
                raw_text = raw_text.split("\n", 1)[1]
                if raw_text.endswith("```"):
                    raw_text = raw_text.rsplit("```", 1)[0]
            result = json.loads(raw_text)

            # Validate nhãn
            valid_moods = [m for m in result.get("moods", []) if m in MOOD_LIST]
            valid_activities = [a for a in result.get("activities", []) if a in ACTIVITY_LIST]
            description = result.get("description", "").strip()

            if valid_moods and valid_activities and description:
                return {
                    "moods": valid_moods,
                    "activities": valid_activities,
                    "description": description
                }
    except Exception as e:
        print(f"      [Gemini API warning]: {str(e)[:100]}, chuyển sang quy luật chuẩn hóa...")

    return infer_tags_fallback(title, artist, audio)

def main():
    parser = argparse.ArgumentParser(description="Gắn tag Moods và Activities cho catalog")
    parser.add_argument("--force", action="store_true", help="Buộc gắn nhãn lại cho toàn bộ bài hát")
    args = parser.parse_args()

    print("=== BẮT ĐẦU PIPELINE GẮN TAG MOOD & ACTIVITY (TAG MOODS) ===")
    print(f"Mô hình LLM: {LLM_MODEL}")

    client = MongoClient(MONGODB_URI)
    db = client.get_database()
    songs_collection = db["songs"]
    artists_collection = db["artists"]
    genres_collection = db["genres"]

    # Tra cứu bản đồ Artist và Genre để lấy tên
    artist_map = {str(a["_id"]): a["name"] for a in artists_collection.find()}
    genre_map = {str(g["_id"]): g["name"] for g in genres_collection.find()}

    query = {"tagSource": {"$ne": "manual"}}
    if not args.force:
        query["$or"] = [
            {"description": {"$in": ["", None]}},
            {"moods": {"$size": 0}},
            {"activities": {"$size": 0}}
        ]

    tracks = list(songs_collection.find(query))
    print(f"Số bài hát cần gắn tag: {len(tracks)}")

    if not tracks:
        print("Tất cả bài hát đã có đầy đủ moods, activities và description.")
        return

    tagged_count = 0
    for idx, track in enumerate(tracks, 1):
        title = track.get("title", "Unknown")
        artist_id = str(track.get("artistId", ""))
        artist_name = artist_map.get(artist_id, "Nghệ sĩ")
        genre_ids = [str(gid) for gid in track.get("genreIds", [])]
        genre_names = [genre_map.get(gid) for gid in genre_ids if gid in genre_map]

        audio = track.get("audio") or {}
        if not audio.get("energy"):
            # Lấy từ audioFeatures nếu có
            af = track.get("audioFeatures") or {}
            audio = {
                "bpm": af.get("tempoBpm", 120),
                "energy": af.get("energy", 0.5),
                "valence": af.get("valence", 0.5),
                "danceability": af.get("danceability", 0.5),
                "acousticness": af.get("acousticness", 0.3)
            }

        print(f"[{idx}/{len(tracks)}] Gắn tag: {title} - {artist_name} (BPM: {audio.get('bpm')}, Energy: {audio.get('energy')})...")
        tags = call_gemini_tagging(title, artist_name, genre_names, audio)

        # Cập nhật MongoDB
        songs_collection.update_one(
            {"_id": track["_id"]},
            {
                "$set": {
                    "moods": tags["moods"],
                    "mood": tags["moods"], # Đồng bộ với trường cũ mood
                    "activities": tags["activities"],
                    "description": tags["description"],
                    "tagSource": "auto"
                }
            }
        )
        print(f"    Moods: {tags['moods']}")
        print(f"    Activities: {tags['activities']}")
        print(f"    Desc: {tags['description']}")
        tagged_count += 1

    print("\n=== HOÀN TẤT GẮN TAG MOOD ===")
    print(f"- Đã gắn tag thành công: {tagged_count}/{len(tracks)}")

if __name__ == "__main__":
    main()
