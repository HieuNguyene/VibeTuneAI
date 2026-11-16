# Music Mood Audio Analysis & Tagging Pipeline

Pipeline độc lập phân tích đặc trưng âm học của các file nhạc thật trong catalog và gắn mood tự động dựa trên số liệu đo đạc kết hợp LLM.

## Cấu trúc thư mục

- `labels.py`: Danh sách nhãn chuẩn (`MOOD_LIST`, `ACTIVITY_LIST`, `ENERGY_LEVELS`).
- `analyze_audio.py`: Trích xuất đặc trưng âm học từ file MP3 (BPM/tempo, RMS energy, valence, danceability, acousticness) và lưu vào `tracks.audio` trong MongoDB.
- `tag_moods.py`: Đọc số liệu âm học thật từ MongoDB, gọi LLM suy luận `moods`, `activities` và tạo `description` tiếng Việt chuẩn (1-2 câu).
- `review_sample.py`: Xuất mẫu đánh giá ra file CSV để nghe thử đối chiếu chất lượng.
- `requirements.txt`: Các thư viện Python cần thiết.

## Cài đặt

```bash
pip install -r requirements.txt
```

## Cách chạy

1. **Bước 1 - Phân tích sóng âm:**
   ```bash
   python analyze_audio.py
   # Hoặc phân tích lại toàn bộ:
   python analyze_audio.py --force
   ```

2. **Bước 2 - Gắn tag Mood & Activity:**
   ```bash
   python tag_moods.py
   # Hoặc gắn tag lại:
   python tag_moods.py --force
   ```

3. **Bước 3 - Xuất mẫu kiểm tra chất lượng:**
   ```bash
   python review_sample.py --output sample_review.csv
   ```

## Lưu ý về giấy phép (Essentia & Khai thác thương mại)

- Nếu sử dụng thư viện **Essentia** và các mô hình pre-trained mood của Essentia trong môi trường production thương mại, hãy lưu ý:
  - Essentia được phát hành theo giấy phép **Affero GPL v3 (AGPLv3)**.
  - Các mô hình học sâu của Essentia có thể có các điều khoản bản quyền học thuật riêng của Music Technology Group (MTG - Universitat Pompeu Fabra).
  - Đối với dự án mã nguồn đóng hoặc ứng dụng thương mại cần giấy phép riêng (commercial license) từ MTG, hoặc sử dụng pipeline phân tích dựa trên tín hiệu số (DSP / Librosa / SciPy) được cung cấp sẵn trong pipeline này.
