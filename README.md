# VibeTune AI - Nền Tảng Nghe Nhạc Trực Tuyến & Gợi Ý AI Theo Tâm Trạng

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android%20Native%20(Java)-green.svg)](https://developer.android.com/)
[![Backend](https://img.shields.io/badge/Backend-Node.js%20%7C%20Express-blue.svg)](https://nodejs.org/)
[![Database](https://img.shields.io/badge/Database-MongoDB%20%7C%20Mongoose-brightgreen.svg)](https://www.mongodb.com/)
[![AI](https://img.shields.io/badge/AI%20Engine-Google%20Gemini%20API-orange.svg)](https://ai.google.dev/)

---

## 📌 Giới Thiệu Dự Án

**VibeTune AI** là nền tảng streaming âm nhạc di động hoàn chỉnh, kết hợp kiến trúc micro-service hiện đại với trí tuệ nhân tạo (Google Gemini API). Hệ thống cho phép người dùng thưởng thức âm nhạc độ nét cao, đồng bộ lời bài hát thời gian thực (Synchronized LRC), đồng thời tương tác với trợ lý âm nhạc AI thông minh để khám phá âm nhạc theo cảm xúc, hoàn cảnh và sở thích cá nhân hóa.

- **Giảng viên hướng dẫn:** ThS. Trần Thị Huệ
- **Sinh viên thực hiện:**
  - Nguyễn Minh Hiếu
  - Đinh Hoàng Hà

---

## 🚀 Các Tính Năng Nổi Bật

### 📱 1. Ứng Dụng Android Native (Java MVVM)
- **Giao diện Material 3 & Hiệu ứng Liquid Gradient:** Màu nền màn hình phát nhạc tự động thích ứng mượt mà theo màu sắc chủ đạo của ảnh bìa album (Palette API).
- **Trình phát nhạc chuẩn AndroidX Media3 (ExoPlayer):** Chạy ngầm liên tục thông qua `Foreground Service`, hỗ trợ điều khiển trên thanh thông báo hệ thống và màn hình khóa.
- **Đồng bộ lời bài hát thời gian thực (LRC Parser):** Hiển thị lời bài hát cuộn tự động chính xác từng mili-giây, cho phép chạm vào câu bất kỳ để tua nhanh (Seek-to-line).
- **Phân loại tâm trạng & Dynamic Chip Bar:** Lọc nhanh kho nhạc theo Tâm trạng (*Vui vẻ, Chill, Năng động, Tập trung, Sầu lắng...*) và Thể loại (*V-Pop, Hip-Hop, Indie, Ballad, EDM...*).
- **Bộ nhớ đệm & Khả năng chịu lỗi (Resilient Cache):** Tự động lưu cache offline, giảm thời gian phản hồi API và cho phép chuyển đổi máy chủ backend linh hoạt.

### 🧠 2. Trợ Lý Âm Nhạc AI (Google Gemini Tool-Calling)
- **Chatbot gợi ý thông minh:** Tích hợp mô hình Gemini 2.5 với cơ chế Function Calling (`searchTracks`, `recommendMoodPlaylist`, `getArtistInsights`).
- **Phân tích cảm xúc & Ngữ cảnh:** Tự động phát hiện tâm trạng người dùng qua câu chat tự nhiên (ví dụ: *"Hôm nay trời mưa buồn quá, cho tôi nghe vài bài acoustic nhẹ nhàng"*), tạo ngay danh sách phát tương ứng.
- **Mood Flow Continuous Engine:** Thuật toán theo dõi telemetry phiên nghe (tỷ lệ nghe hết bài, số lần bỏ qua bài hát) để tự động điều chỉnh danh sách bài hát tiếp theo phù hợp với dòng cảm xúc.

### 💻 3. Hệ Thống Backend & Quản Trị (Node.js + MongoDB)
- **Kiến trúc RESTful API 4 phân hệ:** Quản lý Người dùng & RBAC, Danh mục Âm nhạc, Playlist cá nhân, và Dữ liệu Telemetry / AI Logs.
- **Web Admin Dashboard tích hợp:** Cung cấp giao diện trực quan quản lý bài hát, nghệ sĩ, album, duyệt danh sách người dùng và thống kê lượt nghe.
- **Trình nhập nhạc YouTube tự động:** Công cụ dòng lệnh hỗ trợ trích xuất metadata, tải file audio và tự động gắn nhãn tâm trạng (Mood Enrichment).
- **Bảo mật & Tối ưu hiệu năng:** Xác thực JWT, Bcrypt mã hóa mật khẩu, Helmet chống tấn công HTTP headers, Rate Limiting chống spam request.

---

## 🛠️ Kiến Trúc Công Nghệ

```
┌─────────────────────────────────────────────────────────────┐
│                 VibeTune Android Client (Java)              │
│     - AndroidX Media3 (ExoPlayer) + Foreground Service      │
│     - Retrofit 2 + OkHttp 3 + Glide Image Loader            │
│     - ViewBinding + Shimmer Loading + Palette API           │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTPS / RESTful API & WebSocket
┌──────────────────────────────▼──────────────────────────────┐
│                 Node.js Express API Server                  │
│     - Router / Controllers / Services Layer                 │
│     - Authentication: JWT + Google OAuth2.0 Verifier        │
│     - Gemini AI Tool-Calling Engine (Google Gen AI SDK)     │
│     - WebSocket Telemetry Hub & Notification Service        │
└──────────────────────────────┬──────────────────────────────┘
                               │ Mongoose ODM
┌──────────────────────────────▼──────────────────────────────┐
│                    MongoDB Database                         │
│     - Catalog Module (Songs, Artists, Albums, Genres)       │
│     - User Module (Users, Preferences, Favorites)           │
│     - Playlist Module (Playlists, PlaylistTracks)           │
│     - AI Analytics (ListeningEvents, PlaySessions, History) │
└─────────────────────────────────────────────────────────────┘
```

---

## 📂 Cấu Trúc Thư Mục Dự Án

```text
VibeTuneAI/
├── android/                         # Mã nguồn ứng dụng di động Android (Java)
│   ├── app/
│   │   ├── src/main/java/com/example/spotifyclone/
│   │   │   ├── data/               # Models, SessionManager, Retrofit ApiClient
│   │   │   ├── player/             # Trình phát nhạc Media3, LRC Parser
│   │   │   ├── presentation/       # Activities, Fragments, Adapters (UI)
│   │   │   ├── service/            # Foreground Playback Service
│   │   │   └── util/               # Helper cấu hình Server IP, Network tools
│   │   └── src/main/res/           # Layouts, Drawables, Values, Colors
│   └── build.gradle                # Cấu hình Gradle dependencies
├── backend/                        # Máy chủ API Node.js & Web Admin
│   ├── public/                     # Giao diện Web Admin và tài nguyên tĩnh
│   ├── src/
│   │   ├── ai/                     # Thuật toán gợi ý Cosine Similarity
│   │   ├── controllers/            # Xử lý logic API (Song, Auth, AI, Playlist...)
│   │   ├── models/                 # Mongoose Schemas (Song, User, History...)
│   │   ├── routes/                 # Định tuyến REST endpoints
│   │   ├── seeder/                 # Kịch bản nạp dữ liệu mẫu Catalog
│   │   ├── services/               # Gemini AI Chatbot, Catalog Brain, Notifications
│   │   └── websocket/              # Máy chủ WebSocket thông báo thời gian thực
│   ├── package.json
│   └── server.js                   # Điểm khởi chạy Backend server
├── DATABASE_DESIGN.md              # Tài liệu thiết kế chi tiết 4 phân hệ CSDL
├── docker-compose.yml              # Cấu hình chạy nhanh MongoDB với Docker
└── README.md                       # Tài liệu hướng dẫn dự án
```

---

## ⚙️ Hướng Dẫn Cài Đặt & Chạy Thử

### 1. Yêu Cầu Môi Trường
- **Node.js**: Phiên bản 18.x trở lên
- **MongoDB**: Phiên bản 6.x trở lên (hoặc kết nối MongoDB Atlas)
- **Android Studio**: Ladybug / Koala hoặc mới hơn với Android SDK 34 (API 34)
- **JDK**: Java 17

---

### 2. Khởi Chạy Máy Chủ Backend

1. Di chuyển vào thư mục backend và cài đặt thư viện:
   ```bash
   cd backend
   npm install
   ```

2. Tạo file cấu hình môi trường `.env` từ file mẫu:
   ```bash
   cp .env.example .env
   ```
   *Cập nhật `MONGODB_URI` và `GEMINI_API_KEY` (lấy miễn phí tại [Google AI Studio](https://aistudio.google.com/)).*

3. Nạp dữ liệu catalog bài hát mẫu:
   ```bash
   npm run seed
   ```

4. Khởi chạy máy chủ ở chế độ phát triển:
   ```bash
   npm run dev
   # Hoặc npm start
   # Server lắng nghe tại: http://localhost:5000
   # Trang quản trị Web Admin: http://localhost:5000/admin.html
   ```

---

### 3. Cài Đặt & Chạy Ứng Dụng Android

1. Mở thư mục `android/` bằng **Android Studio**.
2. Chờ Gradle đồng bộ các thư viện (Sync Project with Gradle Files).
3. **Cấu hình máy chủ Backend:**
   - **Chạy trên Máy Ảo (Emulator):** Ứng dụng tự động nhận diện và trỏ về `http://10.0.2.2:5000/`.
   - **Chạy trên Máy Thật:** Ứng dụng có sẵn nút **"Đổi IP máy chủ"** ngay tại màn hình chính hoặc màn hình đăng nhập. Bạn chỉ cần nhập địa chỉ IP Wi-Fi của máy tính (ví dụ: `http://192.168.1.x:5000/`) và bấm *"Kiểm tra kết nối"*.
4. Bấm **Run 'app'** (Shift + F10) để biên dịch và cài đặt lên thiết bị.

---

## 📄 Bản Quyền & Giấy Phép

Dự án được xây dựng phục vụ mục đích nghiên cứu học thuật và bài tập lớn đại học. Toàn bộ mã nguồn được cấp phép theo tiêu chuẩn **[MIT License](LICENSE)**.
