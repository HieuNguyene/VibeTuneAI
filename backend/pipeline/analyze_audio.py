"""
Audio Feature Extraction Pipeline (Signal Processing / DSP)
Phân tích sóng âm từ file MP3 thật, trích xuất:
- BPM (Tempo)
- Energy (RMS)
- Valence (Musical Positivity)
- Danceability (Rhythmic Regularity)
- Acousticness (Acoustic / Harmonic ratio)
Ghi kết quả vào MongoDB (collection 'songs', trường 'audio' và 'audioFeatures')
"""

import os
import sys
import argparse
import datetime
import math
import numpy as np
from scipy import signal
import miniaudio
from pymongo import MongoClient
from dotenv import load_dotenv

# Tải biến môi trường từ backend/.env
ENV_PATH = os.path.join(os.path.dirname(__file__), '../.env')
load_dotenv(dotenv_path=ENV_PATH)

MONGODB_URI = os.getenv('MONGODB_URI', 'mongodb://127.0.0.1:27017/music_app')
BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))

def extract_features_from_pcm(samples: np.ndarray, sample_rate: int) -> dict:
    """
    Trích xuất đặc trưng âm học từ mảng tín hiệu âm thanh PCM.
    Sử dụng các công thức DSP chuẩn hóa thang 0..1.
    """
    # Nếu stereo, chuyển về mono
    if samples.ndim > 1:
        mono = samples.mean(axis=1)
    else:
        mono = samples

    # Chuẩn hóa biên độ
    max_val = np.max(np.abs(mono))
    if max_val > 0:
        mono = mono / max_val
    else:
        mono = np.zeros_like(mono)

    N = len(mono)
    if N == 0:
        return {"bpm": 120, "energy": 0.5, "valence": 0.5, "danceability": 0.5, "acousticness": 0.5}

    # 1. RMS Energy (Độ lớn năng lượng âm thanh)
    rms = np.sqrt(np.mean(mono ** 2))
    energy = float(np.clip(rms * 3.5, 0.05, 0.98))

    # 2. Envelope & Onset Detection cho BPM & Danceability
    # Lấy mẫu phong bì năng lượng mỗi 20ms (frame_size = sr * 0.02)
    hop_length = int(sample_rate * 0.02)
    num_frames = max(1, N // hop_length)
    frames = mono[:num_frames * hop_length].reshape((num_frames, hop_length))
    frame_rms = np.sqrt(np.mean(frames ** 2, axis=1))

    # Onset strength: sự chênh lệch năng lượng giữa các frame liên tiếp
    onset_env = np.diff(frame_rms, prepend=frame_rms[0])
    onset_env = np.maximum(0, onset_env)

    # Autocorrelation của Onset Envelope để tìm nhịp BPM
    if len(onset_env) > 50:
        corr = signal.correlate(onset_env - np.mean(onset_env), onset_env - np.mean(onset_env), mode='full')
        corr = corr[len(corr)//2:]

        # Tìm lag tương ứng dải BPM từ 60 đến 180 (hop_length = 20ms => fps = 50)
        fps = sample_rate / hop_length
        min_lag = int(fps * 60 / 180) # 180 bpm => ~16 frames
        max_lag = int(fps * 60 / 60)  # 60 bpm => ~50 frames

        if max_lag < len(corr) and min_lag < max_lag:
            valid_corr = corr[min_lag:max_lag]
            best_lag = min_lag + np.argmax(valid_corr)
            raw_bpm = round((fps * 60) / best_lag)
            bpm = int(np.clip(raw_bpm, 65, 175))
            
            # Danceability tính từ độ nổi của đỉnh Autocorrelation so với trung bình
            peak_ratio = valid_corr[np.argmax(valid_corr)] / (np.mean(np.abs(valid_corr)) + 1e-6)
            danceability = float(np.clip(0.3 + (peak_ratio / 6.0) * 0.5, 0.1, 0.95))
        else:
            bpm = 120
            danceability = 0.5
    else:
        bpm = 120
        danceability = 0.5

    # 3. Spectral Centroid & Acousticness thông qua FFT
    # Phân tích FFT trên đoạn giữa bài hát (khoảng 10-30 giây)
    start_sec = min(15, N / sample_rate * 0.2)
    end_sec = min(start_sec + 20, N / sample_rate)
    clip_samples = mono[int(start_sec * sample_rate):int(end_sec * sample_rate)]

    if len(clip_samples) > 2048:
        # FFT
        fft_vals = np.abs(np.fft.rfft(clip_samples[:16384]))
        freqs = np.fft.rfftfreq(16384, 1.0 / sample_rate)

        # Trọng tâm phổ tần số (Spectral Centroid)
        sum_fft = np.sum(fft_vals)
        if sum_fft > 0:
            centroid = np.sum(freqs * fft_vals) / sum_fft
        else:
            centroid = 2000

        # Năng lượng dải trầm (<500Hz) vs dải trung/cao (>2500Hz)
        low_energy = np.sum(fft_vals[freqs < 500])
        high_energy = np.sum(fft_vals[freqs > 2500])

        # Acousticness: dải âm thanh tự nhiên, ít méo hài điện tử cao tần
        acoustic_ratio = low_energy / (sum_fft + 1e-6)
        acousticness = float(np.clip(acoustic_ratio * 1.6, 0.05, 0.95))

        # Valence (Độ tươi vui, tích cực):
        # Bài có tempo cao, năng lượng khá và phổ tần số sáng thường có valence cao
        brightness = np.clip(centroid / 3500.0, 0.1, 1.0)
        tempo_factor = np.clip((bpm - 70) / 90.0, 0.1, 1.0)
        valence = float(np.clip(0.25 * brightness + 0.45 * energy + 0.3 * tempo_factor, 0.08, 0.95))
    else:
        acousticness = 0.35
        valence = float(np.clip(energy * 0.8, 0.2, 0.8))

    return {
        "bpm": int(bpm),
        "energy": round(energy, 2),
        "valence": round(valence, 2),
        "danceability": round(danceability, 2),
        "acousticness": round(acousticness, 2)
    }

def analyze_track_file(file_path: str) -> dict:
    """
    Đọc giải mã file MP3 và trích xuất đặc trưng âm học
    """
    # Đọc tối đa 60 giây ở phần cao trào (giữa bài) để tăng tốc độ phân tích
    try:
        info = miniaudio.get_file_info(file_path)
        duration = info.duration
        sr = info.sample_rate

        # Đọc dữ liệu âm thanh
        decoded = miniaudio.decode_file(file_path, nchannels=1, sample_rate=22050)
        samples = np.frombuffer(decoded.samples, dtype=np.int16).astype(np.float32) / 32768.0

        features = extract_features_from_pcm(samples, decoded.sample_rate)
        return features
    except Exception as e:
        raise RuntimeError(f"Lỗi giải mã audio {file_path}: {str(e)}")

def main():
    parser = argparse.ArgumentParser(description="Phân tích sóng âm cho catalog bài hát")
    parser.add_argument("--force", action="store_true", help="Buộc phân tích lại tất cả bài hát")
    args = parser.parse_args()

    print("=== BẮT ĐẦU PIPELINE PHÂN TÍCH ÂM HỌC (ANALYZE AUDIO) ===")
    print(f"MongoDB URI: {MONGODB_URI}")

    client = MongoClient(MONGODB_URI)
    db = client.get_database()
    songs_collection = db["songs"]

    query = {} if args.force else {
        "$or": [
            {"audio": {"$exists": False}},
            {"audio.energy": {"$exists": False}},
            {"analyzedAt": None}
        ]
    }

    tracks = list(songs_collection.find(query))
    print(f"Số bài hát cần phân tích: {len(tracks)}")

    if not tracks:
        print("Tất cả bài hát đã có dữ liệu âm học (dùng --force nếu muốn chạy lại).")
        return

    success_count = 0
    error_count = 0

    for idx, track in enumerate(tracks, 1):
        track_id = track["_id"]
        title = track.get("title", "Unknown")
        file_path = track.get("filePath") or ""

        # Chuẩn hóa đường dẫn tuyệt đối
        if not os.path.isabs(file_path):
            abs_path = os.path.join(BASE_DIR, file_path)
        else:
            abs_path = file_path

        if not os.path.exists(abs_path):
            # Thử tìm trong public/uploads/audios/
            audio_url = track.get("audioUrl", "")
            if audio_url:
                rel = audio_url.lstrip("/\\")
                abs_path = os.path.join(BASE_DIR, "public", rel)

        if not os.path.exists(abs_path):
            print(f"[{idx}/{len(tracks)}] [BỎ QUA] Không tìm thấy file: {abs_path} (Bài: {title})")
            error_count += 1
            continue

        try:
            print(f"[{idx}/{len(tracks)}] Đang phân tích: {title} ({os.path.basename(abs_path)})...")
            features = analyze_track_file(abs_path)

            tempo_label = "slow" if features["bpm"] < 95 else ("fast" if features["bpm"] > 125 else "medium")

            # Cập nhật cả trường audio chuẩn và audioFeatures
            songs_collection.update_one(
                {"_id": track_id},
                {
                    "$set": {
                        "audio": features,
                        "audioFeatures": {
                            "danceability": features["danceability"],
                            "energy": features["energy"],
                            "valence": features["valence"],
                            "tempoBpm": features["bpm"],
                            "acousticness": features["acousticness"],
                            "instrumentalness": track.get("audioFeatures", {}).get("instrumentalness", 0.1),
                            "liveness": track.get("audioFeatures", {}).get("liveness", 0.15),
                            "speechiness": track.get("audioFeatures", {}).get("speechiness", 0.05)
                        },
                        "tempo": tempo_label,
                        "analyzedAt": datetime.datetime.now(datetime.timezone.utc)
                    }
                }
            )
            print(f"    -> BPM: {features['bpm']} | Energy: {features['energy']} | Valence: {features['valence']} | Dance: {features['danceability']} | Acoustic: {features['acousticness']}")
            success_count += 1
        except Exception as err:
            print(f"    [LỖI] {str(err)}")
            error_count += 1

    print("\n=== KẾT QUẢ PHÂN TÍCH ÂM HỌC ===")
    print(f"- Thành công: {success_count}/{len(tracks)}")
    print(f"- Lỗi/Bỏ qua: {error_count}")
    print("Hoàn thành analyze_audio.py.")

if __name__ == "__main__":
    main()
