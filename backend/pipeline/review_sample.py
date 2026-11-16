"""
Script Xuất Mẫu Kiểm Tra Chất Lượng Gắn Tag (Review Sample)
Xuất toàn bộ hoặc mẫu bài hát (tên, nghệ sĩ, BPM, energy, valence, moods, activities, description)
ra file CSV để người dùng/nhóm phát triển nghe thử và đối chiếu độ khớp.
"""

import os
import csv
import argparse
from pymongo import MongoClient
from dotenv import load_dotenv

ENV_PATH = os.path.join(os.path.dirname(__file__), '../.env')
load_dotenv(dotenv_path=ENV_PATH)

MONGODB_URI = os.getenv('MONGODB_URI', 'mongodb://127.0.0.1:27017/music_app')
DEFAULT_CSV = os.path.join(os.path.dirname(__file__), 'sample_review.csv')

def export_sample_review(output_csv: str, limit: int = 50):
    client = MongoClient(MONGODB_URI)
    db = client.get_database()
    songs_col = db["songs"]
    artists_col = db["artists"]

    artist_map = {str(a["_id"]): a["name"] for a in artists_col.find()}

    tracks = list(songs_col.find().limit(limit))
    print(f"Xuất {len(tracks)} bài hát ra file: {output_csv}")

    fields = [
        "Track ID",
        "Tên bài hát",
        "Nghệ sĩ",
        "BPM",
        "Energy",
        "Valence",
        "Danceability",
        "Acousticness",
        "Moods",
        "Activities",
        "Mô tả cảm xúc (Description)",
        "Nguồn Tag"
    ]

    with open(output_csv, mode="w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(fields)

        for t in tracks:
            audio = t.get("audio") or {}
            artist_id = str(t.get("artistId", ""))
            artist_name = artist_map.get(artist_id, "Nghệ sĩ")

            writer.writerow([
                str(t["_id"]),
                t.get("title", ""),
                artist_name,
                audio.get("bpm", ""),
                audio.get("energy", ""),
                audio.get("valence", ""),
                audio.get("danceability", ""),
                audio.get("acousticness", ""),
                ", ".join(t.get("moods", [])),
                ", ".join(t.get("activities", [])),
                t.get("description", ""),
                t.get("tagSource", "auto")
            ])

    print(f"Đã xuất thành công file kiểm thử: {output_csv}")

def main():
    parser = argparse.ArgumentParser(description="Xuất mẫu kiểm tra chất lượng tag cảm xúc")
    parser.add_argument("--output", default=DEFAULT_CSV, help="Đường dẫn file CSV đầu ra")
    parser.add_argument("--limit", type=int, default=100, help="Số lượng bài xuất")
    args = parser.parse_args()

    export_sample_review(args.output, args.limit)

if __name__ == "__main__":
    main()
