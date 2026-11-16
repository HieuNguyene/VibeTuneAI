# Nhật Ký Kiểm Thử Toàn Diện & Chỉnh Sửa Đồng Bộ Hệ Thống

- **Hệ thống:** Music Streaming App (`MongoDB` ↔ `Node.js / Express Backend` ↔ `Web Admin (admin.html)` ↔ `Android Java Native App`)
- **Ngày hoàn tất kiểm thử & đồng bộ:** `2026-11-15`
- **Môi trường kiểm thử thực tế:**
  - **Backend & Web Admin:** `http://localhost:5000` (API `http://localhost:5000/api/v1`, WebSocket `ws://localhost:5000/ws`)
  - **Database:** MongoDB `127.0.0.1/music_app`
  - **Android Emulator:** `emulator-5554` (`Pixel_10_Pro_XL`, độ phân giải `1344x2992`, API 37), APK `app-debug.apk` build bằng `.\gradlew.bat installDebug --console=plain`

---

## 1. Các Lỗi Phát Hiện Trong Phiên Kiểm Thử & Khắc Phục Đồng Bộ

### 1.1. Lỗi hiển thị ảnh đại diện DiceBear định dạng `.svg` trên Android (`Glide`)
- **Hiện tượng phát hiện (Logcat):** Khi kiểm thử E2E trên `emulator-5554`, `Glide` báo cảnh báo `Load failed for [https://api.dicebear.com/7.x/initials/svg?seed=user8702]` (`Failed DecodePath ... setDataSourceCallback failed`) do Android `Glide` mặc định không có bộ giải mã vector SVG, khiến avatar người dùng rơi về icon mặc định (`ic_person`).
- **Kiểm chứng URL thay thế:** Đã xác nhận `https://api.dicebear.com/7.x/initials/png?seed=user8702` và `https://api.dicebear.com/7.x/avataaars/png?seed=AcousticSoul` đều trả về `200 image/png`.
- **Chỉnh sửa đồng bộ đa tầng:**
  1. **Backend Auth (`backend/src/services/authService.js`, dòng `66`, `327`, `400`):** Đổi URL avatar mặc định khi đăng ký / OTP / Google OAuth từ `/initials/svg?` sang `/initials/png?`.
  2. **Backend Web Admin (`backend/src/controllers/AdminController.js`, dòng `1585`):** Đổi URL avatar mặc định khi quản trị viên tạo user từ `/initials/svg?` sang `/initials/png?`.
  3. **Android App (`android/app/src/main/java/com/example/spotifyclone/presentation/auth/ProfileActivity.java`, dòng `41-49` & `153`):** Đổi `PRESET_AVATARS` và hàm tạo ngẫu nhiên từ `/avataaars/svg?` sang `/avataaars/png?`.
  4. **Database (`users`):** Di trú `8/8` tài khoản có URL DiceBear `/svg` sang `/png` (`found: 8, updatedCount: 8, remaining: 0`). Kiểm tra chéo toàn bộ 6 collection (`users`, `artists`, `songs`, `albums`, `genres`, `playlists`) đều còn `0` URL DiceBear `.svg`.
- **Kết quả xác minh sau sửa & build lại APK:**
  - Chọn preset `Acoustic Soul (Thư giãn)` (`avataaars/png?seed=AcousticSoul`) trong `ProfileActivity` hiển thị ảnh PNG rõ nét (`e2e_avatar_preset_png.png`).
  - Lưu hồ sơ qua `PUT /api/v1/auth/profile` (`200`, `97.586 ms`), ảnh avatar PNG hiển thị đồng bộ ở góc phải trên `MainActivity` (`e2e_home_restored_dark.png`, `e2e_final_clean_home.png`).
  - Logcat cold-launch sau bản build cuối: `COLD_LAUNCH_GLIDE_FAIL_COUNT: 0`.

### 1.2. Lỗi tương phản nền tối cứng (`#CC000000`) trên thẻ/huy hiệu ở Chế độ Sáng (Light Mode)
- **Hiện tượng phát hiện (UI Light Mode):** Trên ảnh chụp kiểm thử `e2e_notif_light.png`, `@drawable/bg_badge_translucent` dùng nền đen bán trong suốt cố định `#CC000000` (chỉ thiết kế cho chữ trắng đè lên ảnh bìa ở `item_album_card.xml`), nhưng lại được gắn cho các thành phần dùng `@color/text_secondary` / `@color/text_primary` khiến nhãn `Quản trị`, `Playlist`, `Hệ thống` bị tối chìm ở chế độ Sáng.
- **Chỉnh sửa đồng bộ tại 3 file layout Android:**
  1. `android/app/src/main/res/layout/item_notification.xml` (`tvItemTypeBadge`, dòng `127-139`): Đổi `@drawable/bg_badge_translucent` sang `@drawable/bg_pill_inactive`.
  2. `android/app/src/main/res/layout/dialog_notification_detail.xml` (thẻ người gửi & thời gian, dòng `87`): Đổi `@drawable/bg_badge_translucent` sang `@drawable/bg_creator_readonly`.
  3. `android/app/src/main/res/layout/fragment_home.xml` (`tvArtistsCountBadge`, dòng `676`): Đổi `@drawable/bg_badge_translucent` sang `@drawable/bg_pill_inactive`.
- **Kết quả xác minh sau build APK (`BUILD SUCCESSFUL in 25s`, `Installed on 1 device`):**
  - **Light Mode:** Huy hiệu `104 nghệ sĩ` (`e2e_artists_light.png`), các nhãn `Quản trị` / `Playlist` / `Hệ thống` (`e2e_notif_fixed_light.png`) và thẻ người gửi trong chi tiết thông báo (`e2e_notif_detail_light.png`) hiển thị nền sáng xanh/xám dịu, viền mảnh, chữ đậm rõ.
  - **Dark Mode:** Các nhãn và thẻ thông báo tự thích ứng theo `drawable-night/bg_pill_inactive` và `@color/input_box_bg` (`e2e_notif_fixed_dark.png`, `e2e_notif_detail_dark.png`).

### 1.3. Dữ liệu Lời bài hát (Lyrics) sai lệch ngôn ngữ trong Database
- **Phát hiện khi rà soát catalog:** Trong số `125` bài hát có `lyrics` trong MongoDB `songs`, có 2 bản ghi chứa nội dung tiếng Anh / credit Genius bị ghép nhầm:
  - `6af5dee371a0451ebb7a4eca` (`Đợi (Slowed)`, `52Hz`): 1.158 ký tự tiếng Anh sai bài.
  - `6af6941404c4d60a7d18049e` (`Như Một Giấc Mơ`, `Mỹ Tâm`): đoạn credit Genius 55 ký tự.
- **Xử lý:** Xóa trắng `lyrics: ''` cho 2 bản ghi trên (`Cleared wrong lyrics count: 2`). Rà soát lại toàn bộ `125` bài hát còn lời: `VN title but non-VN lyrics: 0`.
- **Kiểm chứng hiển thị thực tế:** Bài `Lưu Niên` (`6af13924913713d2c54012e5` & `6af694f204c4d60a7d180516`, lời tiếng Việt 1.892 ký tự) hiển thị đúng lời tiếng Việt trên màn hình `PlayerActivity` (`e2e_player_lyrics_vn2.png`).
- **Ghi chú tồn tại ngoài runtime:** File seed tĩnh `backend/src/seeder/catalog_enriched.json` (dòng `20971`, `29756`) trên đĩa vẫn lưu đoạn text Genius cũ, tuy nhiên dữ liệu MongoDB đang chạy đã sạch (`Songs with Mulholland: []`).

### 1.4. Các hạng mục đồng bộ hệ thống đã rà soát & xác nhận ổn định
- **Web Admin (`backend/public/admin.html`, `267,941` bytes):** Đã cập nhật ở các dòng `2612`, `2867`, `2878`, `3094`; được kiểm chứng qua Browser Subagent (`6ba9494a-3911-45e6-bff7-e6b684b2757b`, `BROWSER_HOST_FACTS`: `visited_origins: http://127.0.0.1:5000, http://localhost:5000`, `blocked_origins: none`, ảnh cuối `final_step_103.png`) và xác nhận HTTP `200` trong `full_system_e2e.js`.
- **Tài sản media bài hát & nghệ sĩ (`Karik - Bạn đời`):** File `karik_ban_doi.jpg` (`959,428` bytes) và `karik.jpg` (`626,678` bytes) chuẩn định dạng JFIF JPEG (`HTTP 200`), hiển thị đúng khi tìm kiếm `Karik` trên Android.
- **Trình phát nhạc & MiniPlayer (`PlayerActivity`, Album & Playlist):** Tốc độ phát mặc định chuẩn `1.0x`, đầy đủ nút tua `-10s` / `+30s`, nút `Tốc độ 1.0x`, `Bật Mood Flow`; thanh MiniPlayer hiển thị ổn định phía trên thanh điều hướng trong chi tiết Album và Playlist ở cả Light & Dark Mode (`e2e_album_top_light.png`, `e2e_playlist_top_light.png`, `e2e_player_top_light.png`).

---

## 2. Kết Quả Chạy Lại Toàn Bộ Bộ Kiểm Thử Backend, API & Database (Sau Lần Chỉnh Sửa Cuối)

Backend server được khởi động lại với mã nguồn mới nhất trước khi chạy toàn bộ 12 tập lệnh kiểm thử dưới đây (tất cả đều kết thúc với `EXIT CODE: 0`):

| STT | Bộ kiểm thử / Script | Phạm vi kiểm tra | Kết quả thực tế quan sát | Exit |
|---:|---|---|---|---:|
| 1 | `scratch/full_system_e2e.js` | Toàn bộ 49 bước: Health, `admin.html`, Auth (Register/Login/OTP/Google/Disable), Catalog (Songs/Stream 206/Search/Genres/Artists/Albums/Playlists), Favorites/Follow, Playlist Private 403/Reorder/Cascade Delete, Playback History, MoodFlow Session/Next/Arc/Events, Recommendations/Off-topic Guard, WebSocket `/ws`, Notifications, Full Admin CRUD & Sync | **49/49 PASSED, 0 FAILED** | `0` |
| 2 | `npm test` (`node --test tests/*.test.js`) | Unit tests cho Arcs (`wind_down`, `workout`, `focus`, `steady`), Scoring (`cosineSimilarity`, `normalizedAudioDistance`, artist penalty), Selector & Session vector | **12/12 PASSED, 0 FAILED, 0 SKIPPED** (`1004.0862 ms`) | `0` |
| 3 | `node tests/run_tests.js` | 27 ca kiểm thử gợi ý bài hát (buồn, vui/sôi động, tập trung, ngủ ngon, tiếng lóng, mơ hồ, nghệ sĩ cụ thể, ngoài phạm vi `domain_guard`, an toàn `safety_guard` & `prompt_injection`) đối soát với catalog 153 bài | **27/27 ĐẠT (100.0%)** | `0` |
| 4 | `node scripts/verify-integrity.js` | Kiểm tra tham chiếu `artistId` trên toàn bộ `songs` và thống kê `artists` (`204` nghệ sĩ, `67` nghệ sĩ cộng tác/sáng tác không đứng tên chính nên `0 songs`) | `Songs with invalid artistId: 0` — **VERIFICATION PASSED PERFECTLY** | `0` |
| 5 | `node scripts/checkCatalogStats.js` | Kiểm tra tỷ lệ đủ dữ liệu media & lời bài hát trong catalog | `153` bài hát; Lyrics `125/153 (81.7%)`; Cover `153/153 (100%)`; Audio `153/153 (100%)` | `0` |
| 6 | `node scripts/test_home_tabs_endpoints.js` | Các API cho 5 tab Trang chủ & sắp xếp theo thể loại/nghệ sĩ | **PASS 100%** (`10` genres, `12` albums, `100` artists/library) | `0` |
| 7 | `node scripts/test_personalization_endpoints.js` | Playlist cá nhân (`personalOnly=true`), nghệ sĩ theo dõi, bật/tắt yêu thích Album & Bài hát | **ALL PERSONALIZATION ENDPOINT TESTS PASSED** | `0` |
| 8 | `node scripts/test_system_full_regression.js` | 19 ca hồi quy toàn diện (Streaming Range 206, Auth, Admin stats, Playlist Public/Private 403, dọn dẹp `PlaylistTrack`, History) | **19/19 ĐẠT (100%)** | `0` |
| 9 | `node scripts/test_album_playlist_permissions.js` | 16 ca kiểm thử đồng bộ `totalTracks`, `Song.albumId`, phân quyền Private Playlist `403`, quyền Admin xem/sửa playlist người dùng, chặn `POST /albums` phía user (`404`) | **16/16 ĐẠT (100%)** | `0` |
| 10 | `node scripts/system_audit_test.js` | Rà soát tổng hợp DB + 8 nhóm API (`health`, `songs`, `artists`, `auth/me`, `playlists`, `history`, `admin/stats`, `admin/data/playlists`) | **PASS** (Không phát hiện lỗi trong luồng chính) | `0` |
| 11 | `node scripts/test-moodflow-module.js` | Khởi tạo session MoodFlow thuần module, chuyển bài kế tiếp theo `wind_down` arc | **PASS** (`SUCCESS! Pure moodFlow module works perfectly`) | `0` |
| 12 | `node scripts/test-player-api.js` | REST APIs `/player/session` (`201`), `/player/next` (`200`, `135 ms`), `/player/session/:id/arc` (`200`), `/events` (`200`) | **ALL REST API TESTS PASSED SUCCESSFULLY** | `0` |

---

## 3. Kiểm Thử Thực Tế Giao Diện & Nghiệp Vụ Trên Android Emulator (`emulator-5554`)

- **Chế độ Tối (Dark Mode) & Sáng (Light Mode):**
  - Home (`Tất cả`, `Âm nhạc`, `Nghệ sĩ`, `Playlist`, `Album`): Chuyển đổi mượt mà qua `ProfileBottomSheet` / `ProfileActivity` (`ThemeManager`), huy hiệu `104 nghệ sĩ` và toàn bộ chip lọc rõ nét ở cả 2 chế độ.
  - Search: Đầy đủ 10 ảnh thể loại (`HTTP 200`), tìm kiếm `Karik` trả về `Bạn đời` với ảnh bìa hợp lệ (`GET /api/v1/songs/search?q=Karik 200`).
  - Library & CRUD Playlist riêng tư (trên Light Mode): Tạo mới playlist riêng tư `"E2E"` (`"Riêng tư • 0 bài hát • user8702"`), mở màn hình chi tiết xác nhận MiniPlayer hiển thị phía trên thanh điều hướng, sau đó xóa thành công qua menu tùy chọn (`"Đã xóa danh sách phát thành công"`).
  - Playlist Detail, Album Detail, Artist Detail (Sơn Tùng M-TP & The Weeknd): MiniPlayer luôn nằm phía trên menu điều hướng khi phát nhạc; thanh công cụ cố định gọn khi cuộn.
  - Notification & Notification Detail: Kiểm tra trực quan cả Light Mode (`e2e_notif_fixed_light.png`, `e2e_notif_detail_light.png`) và Dark Mode (`e2e_notif_fixed_dark.png`, `e2e_notif_detail_dark.png`) sau khi build lại APK — không còn lỗi chữ chìm nền đen.
  - Profile & Avatar Presets: Mở bảng `"Bộ sưu tập Avatar"` đã sạch mục lạc đề (`e2e_presets_fixed.png`), chọn & tải thành công DiceBear PNG (`e2e_avatar_preset_png.png`), lưu đồng bộ về server & phiên đăng nhập (`PUT /api/v1/auth/profile 200`).
  - Listening History, Trợ lý Âm nhạc (`"Nhạc chill thư giãn"` trả về 5 bài hát thật), PlayerActivity (`-10s`, `+30s`, `Tốc độ 1.0x`, lời bài hát tiếng Việt): Hoạt động chính xác, trực quan.
- **Độ ổn định runtime (`logcat` sau bản build cuối):** `COLD_LAUNCH_FATAL_COUNT: 0`, `COLD_LAUNCH_GLIDE_FAIL_COUNT: 0`.
- **Ghi chú màn hình Auth trên máy ảo:** Do `RegisterActivity` khai báo `android:exported="false"` (chuẩn bảo mật nội bộ) và phiên `user8702` đang đăng nhập sẵn phục vụ demo cho người dùng, nên các chỉnh sửa layout ở `activity_login.xml` / `activity_register.xml` không được ép đăng xuất để chụp lại trực tiếp sau build cuối, mà giữ nguyên phiên `user8702` ở Dark Mode mặc định.

---

## 4. Trạng Thái Cơ Sở Dữ Liệu MongoDB (`music_app`) Sau Kiểm Thử & Dọn Dẹp

Sau khi chạy toàn bộ bộ kiểm thử, đã dọn dẹp `2` bản ghi `play_sessions` và `1` bản ghi `listening_events` tạm do `test-moodflow-module.js` / `test-player-api.js` sinh ra, đồng thời xác minh tính toàn vẹn tham chiếu:

- **Kiểm tra vệ sinh dữ liệu:** `svgUsers: 0`, `tempUsers: 0`, `tempPlaylists: 0`, `orphanSongArtists: 0`, `orphanSongAlbums: 0`, `orphanSongGenres: 0`.
- **Số lượng bản ghi hiện tại theo đúng tên collection trong MongoDB:**
  - `songs`: `153`
  - `artists`: `204`
  - `albums`: `12`
  - `genres`: `10`
  - `playlists`: `19`
  - `users`: `10`
  - `notifications`: `19`
  - `playlisttracks`: `248`
  - `userfavorites`: `104`
  - `userpreferences`: `10`
  - `listeninghistories`: `182`
  - `play_sessions`: `12`
  - `listening_events`: `33`
  - `user_interactions`: `25`
  - `aipromptlogs`: `2`
  - `otpverifications`: `0`
  - `histories`: `0`

---

## 5. Phiên Sửa Lỗi Khẩn Cấp & Đồng Bộ Toàn Hệ Thống (WebSocket, Playlist Notification, Upload API, DOM & Schema)

### 5.1. Danh sách sửa lỗi đã áp dụng (sau rà soát dự án)
1. **Backend Core & Upload API:**
   - [backend/src/utils/ApiResponse.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/utils/ApiResponse.js): Thêm `ApiResponse.notFound(res, message)` trả về HTTP `404` (sửa lỗi HTTP `500 TypeError` ở `genreRoutes.js:21` và `ArtistController.js:164`).
   - [backend/src/middlewares/rateLimiter.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/middlewares/rateLimiter.js): Gỡ import `ApiResponse` dư thừa.
   - [backend/pipeline/requirements.txt](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/pipeline/requirements.txt): Bổ sung `miniaudio>=1.59`.
   - [backend/src/controllers/AdminController.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/controllers/AdminController.js): Đồng bộ `uploadFile` nhận cả `target` và `type`, chỉ cho phép audio/ảnh hợp lệ (trả `400` với định dạng cấm như `.html`/`.svg`/`.js`), phân loại đúng ảnh vào `/uploads/covers/`; sửa `deleteSong` dùng `PlaySession.updateMany` với `$pull` trên `recentTrackIds`/`dislikedTrackIds`.
   - [backend/src/services/songService.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/songService.js): Đồng bộ lọc `mood` trên cả hai trường `moods` và `mood`, kết hợp điều kiện bằng `$and` để không ghi đè `$or` của `artistId`.
   - [backend/src/services/toolCallingChat.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/toolCallingChat.js): Sửa `createPlaylist` dùng đúng schema `creatorId` (thay cho `userId`), đồng bộ `totalTracks` và `order` từ `0`.
   - [backend/src/services/aiChatService.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/aiChatService.js) & [backend/src/ai/playlistGenerator.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/ai/playlistGenerator.js): Escape ký tự đặc biệt Regex trước khi tạo `new RegExp(...)` tránh crash khi người dùng nhập `[` / `(` / `+` / `?`.
   - [backend/src/services/moodFlow/candidates.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/moodFlow/candidates.js): Bổ sung trích xuất `audioFeatures.tempoBpm` và fallback `song.audio.*` trong `extractAudioFeatures` và truy vấn `audio.energy`.
2. **Web Admin (`backend/public/admin.html`):**
   - Di chuyển `#notificationModal` và `#notificationDetailModal` ra khỏi `<head>` vào đúng vị trí trong `<body>`.
   - Hợp nhất `handleDirectFileUpload` và `handleNotificationCoverUpload` qua hàm dùng chung `uploadAdminMediaFile(file, kind)` gửi JSON base64 (`{ type, target, fileName, base64Data }`) lên `/api/v1/admin/upload-file` thay cho `FormData` cũ.
   - Gỡ bỏ `deleteHistoryRecord` & `deleteFavoriteRecord` bị trùng lặp không có nơi gọi.
3. **Android App (`android/`):**
   - [NotificationWebSocketManager.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/data/remote/NotificationWebSocketManager.java) & [ProfileBottomSheetDialog.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/auth/ProfileBottomSheetDialog.java): Sửa parser đọc `event`/`data` (có fallback `type`), hỗ trợ cả `NOTIFICATION_READ_ALL` và `NOTIFICATIONS_READ_ALL`, bảo vệ `JsonNull`, ẩn `?token=` trong log, tăng `lastKnownUnreadCount` khi `NEW_NOTIFICATION` không kèm `unreadCount`, và ngắt WS khi đăng xuất.
   - [NotificationDetailBottomSheet.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/notification/NotificationDetailBottomSheet.java) & [NotificationGroupAdapter.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/notification/NotificationGroupAdapter.java): Truyền `PlaylistDetailActivity.EXTRA_PLAYLIST_TYPE = TYPE_CUSTOM_PLAYLIST` khi mở playlist từ thông báo; thay placeholder `bg_badge_translucent` bằng `bg_card_rounded`.
   - [SongOptionsBottomSheetDialog.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/player/SongOptionsBottomSheetDialog.java), [PlayerActivity.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/player/PlayerActivity.java), [SessionManager.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/data/local/SessionManager.java), [MusicPlayerManager.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/player/MusicPlayerManager.java), [AndroidManifest.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/AndroidManifest.xml): Kiểm tra `response.isSuccessful()` khi thêm bài vào playlist, thêm lifecycle guard trước `Palette` callback, giữ nguyên `KEY_SERVER_URL` khi `clearSession()`, dùng `CopyOnWriteArrayList` cho `listeners`, và đặt `NotificationActivity android:exported="false"`.
4. **Đóng gói APK:** Sao chép bản build mới nhất sang `backend/public/vibe-music.apk` (`9,585,931` bytes).

### 5.2. Kết quả kiểm chứng sau bản sửa cuối
- **Backend test suites (`exit=0` toàn bộ):**
  - `npm test`: `12/12 PASSED`
  - `node tests/run_tests.js`: `27/27 ĐẠT (100.0%)`
  - `full_system_e2e.js`: `49/49 PASSED, 0 FAILED`
  - 9 script trong `backend/scripts/` (`verify-integrity.js`, `checkCatalogStats.js`, `test_home_tabs_endpoints.js`, `test_personalization_endpoints.js`, `test_system_full_regression.js` `19/19`, `test_album_playlist_permissions.js` `16/16`, `system_audit_test.js`, `test-moodflow-module.js`, `test-player-api.js`): tất cả `exit=0`.
  - `verify_targeted_fixes.js`: `ALL_TARGETED_API_CHECKS_PASSED` (`404` trên genre/artist không tồn tại, `upload-file` đưa `.png`/`.jpg` vào `/uploads/covers/` dù thiếu `target` và chặn `.html`/`.svg`/`.js` bằng `400`, lọc `mood` + `artistId`, escape regex, `createPlaylist` đúng `creatorId`/`totalTracks`/`order=0`, `extractAudioFeatures` trích xuất được BPM thực tế).
- **Android Build, Lint & E2E thực tế trên `emulator-5554`:**
  - `.\gradlew.bat installDebug lintDebug --console=plain`: `BUILD SUCCESSFUL in 56s`, Lint `619 warnings`, `0` errors.
  - WebSocket real-time (`NotificationWS`): log `Connecting to WebSocket: ws://10.0.2.2:5000/ws` (đã ẩn JWT), nhận `CONNECTED`, `NEW_NOTIFICATION`, và `NOTIFICATION_READ` thành công với `0` lỗi parse và `0` crash.
  - Mở chi tiết thông báo kiểm thử -> bấm `Xem Danh Sách Phát` -> mở đúng `PlaylistDetailActivity` hiển thị `"Top Hits Việt Nam 2026"` cùng `8` bài hát (`e2e_notif_sheet_latest.png`, `e2e_notif_playlist_latest.png`), sau đó đã xóa thông báo kiểm thử và đưa ứng dụng về màn hình chính sạch sẽ.
- **Web Admin (`http://localhost:5000/admin`) qua trình duyệt thực tế (`BROWSER_HOST_FACTS` xác nhận):**
  - DOM check: `body #notificationModal = 1`, `body #notificationDetailModal = 1`, `head #notificationModal = 0`, `head #notificationDetailModal = 0`.
  - Hiển thị modal `#notificationModal` không có lỗi console JS hay HTTP 4xx/5xx; thao tác `setInputFiles('test_notif_cover.png')` trên trình duyệt sandbox bị timeout 60s chờ người dùng bấm Allow trên hộp thoại cấp quyền upload của IDE, trong khi endpoint `/api/v1/admin/upload-file` với ảnh bìa đã được xác minh trực tiếp qua `verify_targeted_fixes.js`.
- **Lưu ý bảo mật có chủ đích (đã xử lý tại Mục 6 bên dưới):** Nhóm route `/api/v1/admin/*` trước đó chưa bật JWT admin middleware để tương thích trang Web Admin khi chưa có màn hình đăng nhập; hạng mục này cùng các lớp bảo mật hệ thống đã được hoàn thiện ở **Mục 6**.

---

## 6. Phiên Siết Bảo Mật Backend & Đồng Bộ Xác Thực Toàn Hệ Thống (Trước Demo / Triển Khai)

### 6.1. Các thay đổi siết bảo mật & đồng bộ đa tầng
1. **Phân quyền RBAC cho `/api/v1/admin/*` & Tài khoản Admin hệ thống:**
   - [backend/src/routes/adminRoutes.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/routes/adminRoutes.js): Gắn `router.use(authenticate, authorize('admin'))` cho toàn bộ endpoint `/api/v1/admin/*`.
   - [backend/src/seeder/ensureAdmin.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/seeder/ensureAdmin.js), [backend/src/config/environment.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/config/environment.js), [backend/.env](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/.env), [backend/.env.example](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/.env.example), [backend/server.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/server.js), [backend/src/seeder/seedCatalog.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/seeder/seedCatalog.js): Khởi tạo idempotent tài khoản Admin (`role: 'admin'`) khi server khởi động và khi seed catalog (`ADMIN_EMAIL=admin@vibetune.local`, `ADMIN_PASSWORD=VibeAdmin@2026`, `ADMIN_SYNC_PASSWORD` mặc định `false`).
   - [backend/src/controllers/AdminController.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/controllers/AdminController.js): Xác thực giá trị `role` (`user`|`admin`) và ngăn chặn tự xóa chính mình hoặc xóa/hạ quyền Admin đang hoạt động cuối cùng trong `createUser`/`updateUser`/`deleteUser`.
2. **Web Admin Login, Session & Logout ([backend/public/admin.html](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/public/admin.html)):**
   - Thêm màn hình khóa `#adminAuthOverlay` (tách khỏi bộ bắt phím `Escape` dành cho `[id$="Modal"]`), hiển thị `#adminSessionPill` và nút `#adminLogoutBtn` trên thanh topbar với CSS tương thích cả Dark Mode lẫn Light Mode.
   - `apiJson` tự động đính kèm Bearer JWT và khóa lại về `#adminAuthOverlay` khi gặp `401`/`403`; kết nối WebSocket truyền `?token=...` và ngắt an toàn khi đăng xuất; đồng bộ khởi tạo qua `bootstrapAdminSession()`.
3. **Xác thực Google ID Token thật (RS256 JWKS) & Loại bỏ Fallback giả mạo trên Android:**
   - [backend/src/services/googleAuthVerifier.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/googleAuthVerifier.js): Xác thực chữ ký RSA-SHA256 từ Google JWKS (`https://www.googleapis.com/oauth2/v3/certs`, có cache và làm mới khi đổi `kid`), kiểm tra `iss`, `exp`, `nbf`, `sub`, `email`, `email_verified === true` và đối chiếu `aud` theo `config.googleClientIds` (`GOOGLE_CLIENT_ID`).
   - [backend/src/controllers/AuthController.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/controllers/AuthController.js) & [backend/src/services/authService.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/authService.js): `loginWithGoogle` chỉ chấp nhận `{ idToken }` đã được xác thực qua `verifyGoogleIdToken`; loại bỏ hoàn toàn cơ chế tin `{ googleId, email, displayName }` do client gửi lên; `updateProfile` làm sạch `avatarUrl`.
   - [GoogleSignInHelper.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/auth/GoogleSignInHelper.java), [GoogleLoginRequest.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/data/model/GoogleLoginRequest.java), [strings.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/values/strings.xml), [LoginActivity.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/auth/LoginActivity.java), [RegisterActivity.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/auth/RegisterActivity.java): Chỉ gửi `idToken`, bổ sung `google_web_client_id`, và gỡ bỏ `showGoogleFallbackDialog()` tại cả màn hình Đăng nhập lẫn Đăng ký. *(Lưu ý triển khai: điền OAuth 2.0 Web Client ID thật vào `GOOGLE_CLIENT_ID` trong `backend/.env` và `google_web_client_id` trong `strings.xml` khi cấu hình Google Cloud).*
4. **Chặn IDOR & Siết bảo mật WebSocket (`socketServer.js`):**
   - [backend/src/routes/historyRoutes.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/routes/historyRoutes.js) & [backend/src/controllers/historyController.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/controllers/historyController.js): Áp dụng `router.use(authenticate)`, xóa fallback `req.query.userId`/`req.body.userId`, ép điều kiện `{ userId, _id }` khi `deleteMany`.
   - [backend/src/controllers/eventController.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/controllers/eventController.js), [backend/src/controllers/playerController.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/controllers/playerController.js), [backend/src/services/moodFlow/index.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/moodFlow/index.js): Xóa fallback `bodyUserId`/`req.body.userId`, bổ sung kiểm tra quyền sở hữu `PlaySession` (`401`/`403` nếu `session.userId != null`), giữ nguyên khả năng dùng phiên ẩn danh (`session.userId == null`).
   - [backend/src/websocket/socketServer.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/websocket/socketServer.js): Yêu cầu JWT hợp lệ + tài khoản `User` đang hoạt động khi kết nối WS (đóng mã `1008` nếu thất bại), gỡ bỏ `?userId=` và thông điệp `SUBSCRIBE` mạo danh, lưu `ws.userRole` và chỉ broadcast `ADMIN_SUMMARY_UPDATE` tới socket `role === 'admin'`.
5. **Lọc đuôi file, kiểm tra Magic Bytes & URL media an toàn:**
   - [backend/src/utils/fileValidator.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/utils/fileValidator.js): Kiểm tra danh sách đuôi cho phép (`.jpg`, `.png`, `.webp`, `.gif`, `.mp3`, `.wav`, `.flac`, `.m4a`, `.aac`, `.ogg`), kiểm tra MIME Data URI, giải mã base64 chuẩn, kiểm tra chữ ký Magic Bytes thực tế, giới hạn dung lượng, chống Path Traversal (`saveValidatedBase64Upload`), và hàm `sanitizeMediaUrl` chặn `javascript:`, `vbscript:`, `file:`, `data:` (`.html`, `.svg`, `.js`).
   - Đồng bộ `saveValidatedPlaylistCover` và `sanitizeMediaUrl` vào [AdminController.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/controllers/AdminController.js), [playlistService.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/playlistService.js), [authService.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/authService.js) và [notificationService.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/notificationService.js).

### 6.2. Kết quả kiểm thử lại toàn hệ thống (Sau khi siết bảo mật)
- **Backend Unit, AI, Security & E2E Tests (`exit=0` toàn bộ):**
  - `npm test`: `12/12 PASSED, 0 FAILED, 0 SKIPPED` (`625.7098 ms`).
  - `node tests/run_tests.js`: `27/27 ĐẠT (100.0%)`.
  - [backend/scripts/test_security_hardening.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/scripts/test_security_hardening.js): `13 PASSED, 0 FAILED` (kiểm thử RBAC `/admin/*`, từ chối Google payload giả/token giả, kiểm chứng RS256 JWKS verifier, chặn IDOR `/history*` + `PlaySession` + `/interactions`, chặn WS không xác thực bằng `1008` + cô lập `ADMIN_SUMMARY_UPDATE`, chặn upload `.html`/`.svg`/`.js`, Path Traversal, Magic Bytes giả mạo, URL nguy hiểm).
  - `scratch/full_system_e2e.js`: `50/50 PASSED, 0 FAILED` (đã cập nhật luồng đăng nhập Admin JWT).
  - `scratch/verify_targeted_fixes.js`: `ALL_TARGETED_API_CHECKS_PASSED`.
  - Toàn bộ 10 script trong `backend/scripts/` (`test_system_full_regression.js` `19/19`, `test_album_playlist_permissions.js` `16/16`, `system_audit_test.js`, `test_home_tabs_endpoints.js`, `test_personalization_endpoints.js`, `test-player-api.js`, `test-moodflow-module.js`, `verify-integrity.js`, `simulate-flow.js`, `checkCatalogStats.js`): tất cả kết thúc với `exit=0`.
- **Android Build, Lint & Đóng gói APK:**
  - `.\gradlew.bat assembleDebug lintDebug --console=plain`: `BUILD SUCCESSFUL in 34s` (`0` đề cập lỗi/cảnh báo đối với `GoogleSignInHelper`, `LoginActivity`, `RegisterActivity`, `GoogleLoginRequest`).
  - `.\gradlew.bat testDebugUnitTest --console=plain`: `BUILD SUCCESSFUL in 3s` (`:app:testDebugUnitTest NO-SOURCE`).
  - Đồng bộ APK tới [backend/public/vibe-music.apk](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/public/vibe-music.apk): `11,051,147` bytes (`SHA256: 3D775F2CC340C1CADC41803A69F07F2C94D5EA9413984AE997CB86C78FB9215C`), kiểm tra HTTP `HEAD /vibe-music.apk` trả về `200`, `Content-Length: 11051147`.
- **Kiểm chứng thực tế Android Emulator (`emulator-5554`):**
  - Cài đặt qua `adb install -r` thành công, khởi chạy `MainActivity` ổn định (`0` `AndroidRuntime:E`).
  - Logcat `NotificationWS` xác nhận kết nối `ws://10.0.2.2:5000/ws`, nhận `{"event":"CONNECTED",...,"role":"user"}` và nhận thông báo realtime `NEW_NOTIFICATION` khi Admin gửi qua `POST /api/v1/admin/notifications` (`200 OK`); đã dọn sạch thông báo kiểm thử ngay sau khi xác minh.
- **Kiểm chứng thực tế Web Admin (`http://localhost:5000/admin`) qua Browser Subagent (`BROWSER_HOST_FACTS` xác nhận `visited_origins: http://127.0.0.1:5000, http://localhost:5000`, `blocked_origins: none`, `final_step_139.png`):**
  - Màn hình khóa `#adminAuthOverlay` khóa dashboard khi chưa đăng nhập trên cả Dark Mode & Light Mode; từ chối mật khẩu sai (`401 Unauthorized` + thông báo `"Invalid login credentials"`).
  - Đăng nhập hợp lệ (`admin@vibetune.local` / `VibeAdmin@2026`) hiển thị `#adminSessionPill`, `#adminLogoutBtn`, đầy đủ KPI (`153` bài hát, `204` nghệ sĩ, `12` album), danh sách bài hát và `● Realtime Online`, với `0` lỗi Console JS / HTTP sau đăng nhập; kiểm thử chuyển đổi Light/Dark Mode, nút `#adminLogoutBtn` và đăng nhập lại trạng thái sẵn sàng.
- **Trạng thái dữ liệu sạch sau kiểm thử:** `songs: 153`, `artists: 204`, `albums: 12`, `playlists: 19`, `genres: 10`, `users: 11` (bao gồm `1` Admin hệ thống + các fixture cũ), `notifications: 19`; không còn bản ghi hay tệp upload kiểm thử phát sinh tồn đọng.

---

## 7. Phiên Làm Sạch Dữ Liệu, Dọn Tính Năng Dở, Đồng Bộ APK & Tối Ưu Tương Phản Dark Mode (`#38BDF8`)

### 7.1. Làm sạch dữ liệu Podcast, đồng bộ Seeder, Embeddings AI & Upload rác
1. **Loại bỏ hoàn toàn 2 bản ghi podcast khỏi catalog nhạc:**
   - Xóa `2` bản ghi không phải nhạc (`6af5ea06cc2b37017fd8535a`, `6af5ea11cc2b37017fd85362` / `SMWX`, `Nhã Lam`) khỏi `songs`, dọn `2` nghệ sĩ mồ côi (`SMWX`, `Nhã Lam`), `2` liên kết `PlaylistTrack`, `1` `UserInteraction`, tính lại `totalTracks` của playlist `6af97a6aa10b823943603ad`.
   - Xóa `10` tệp upload không còn tham chiếu trên đĩa (`6` tệp audio/cover/avatar của podcast + `4` ảnh dư thừa đã kiểm chứng), xóa collection rỗng `histories` (`hasHistoriesCol: false`).
   - Cập nhật [backend/src/seeder/catalog_enriched.json](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/seeder/catalog_enriched.json) (`totalSongs: 150`, `artists: 98`, gỡ các bài podcast và thẻ `podcast`/`talkshow`) và bổ sung xóa đầy đủ collection trong [backend/src/seeder/clearDatabase.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/seeder/clearDatabase.js) (`Genre`, `ListeningEvent`, `PlaySession`, `UserInteraction`, `UserPreference`, `Notification`, `OtpVerification`).
2. **Sửa quy tắc gắn thẻ & Tự động làm giàu AI Embedding (`768` chiều):**
   - [backend/src/services/lastFmService.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/lastFmService.js): Gỡ bỏ rule regex `/podcast|talk|.../i` khiến các ca khúc nhạc bị gắn nhầm tag `podcast`.
   - Làm sạch tag, tái tạo `semanticText` và sinh lại Gemini vector embedding `768` chiều cho `5` ca khúc từng bị nhiễm tag (`Vùng An Toàn`, `Shape of You`, `Tái Sinh`, `Tâm Sự`, `We Don't Talk Anymore (slowed + reverb)`) và bổ sung mới cho `"Bạn đời"` (`6af96c6e0046fd3676563112`) -> đạt `151/151` bài hát đều có `embedding` (`missingEmb: 0`, `podTags: 0`).
   - [backend/src/controllers/AdminController.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/controllers/AdminController.js): Bổ sung helper `syncSongAiMetadata` gắn vào `createSong` và `updateSong` để đồng bộ `moods` từ `mood`, lọc bỏ `podcast`/`talkshow`, tạo `semanticText` + `embedding` `768` chiều ngay khi Admin thêm/sửa bài hát.
   - [backend/src/services/moodFlow/index.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/moodFlow/index.js): Bổ sung `artistMap` trong `buildUserTasteMap(userId)` từ dữ liệu `Song` (`artistId`, `artistIds`).
   - [backend/src/services/authService.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/authService.js): Giới hạn allowlist trong `updatePreferences` (`favoriteGenreIds`, `favoriteArtistIds`, `streamingQuality`, `themeMode`, `allowAiRecommendations`).

### 7.2. Dọn tính năng dở, mã chết & Nối API thật trên Android
1. **Xóa tệp, chuỗi & drawable chết không còn sử dụng:**
   - Xóa `AiPlaylistFragment.java`, `fragment_ai_playlist.xml`, `ArtistCardAdapter.java` và `10` tệp drawable dư thừa (`bg_artist_avatar_ring.xml`, `bg_artist_grid_card.xml` day/night, `bg_droplet_btn_primary.xml` day/night, `bg_droplet_btn_secondary.xml` day/night, `bg_droplet_specular_overlay.xml`, `bg_edit_text_dark.xml`, `ic_sparkle_ai.xml`); dọn chuỗi thừa (`chip_podcasts`, `ai_prompt_hint`, `ai_generate_button`, `ai_generating_step_1..3`, `continue_with_facebook`) trong [strings.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/values/strings.xml).
   - Gỡ menu giả không có backend ở [layout_bottom_sheet_playlist_options.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/layout/layout_bottom_sheet_playlist_options.xml) / [PlaylistDetailActivity.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/playlist/PlaylistDetailActivity.java) (`itemPlaylistDownload`, `itemPlaylistAddToProfile`) và [layout_bottom_sheet_player_menu.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/layout/layout_bottom_sheet_player_menu.xml) / [PlayerActivity.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/player/PlayerActivity.java) (`itemMenuExcludeTaste` & `taste_profile_prefs`); chuẩn hóa tên thương hiệu `Spotify` -> `VibeTune` tại [MusicPlaybackService.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/service/MusicPlaybackService.java), [activity_login.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/layout/activity_login.xml), [activity_playlist_detail.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/layout/activity_playlist_detail.xml), [bottom_sheet_profile.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/layout/bottom_sheet_profile.xml) và [PlayerActivity.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/player/PlayerActivity.java).
2. **Nối API thật cho các nút trước đây chỉ hiện Toast / dữ liệu cứng:**
   - [AudioQualityHelper.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/util/AudioQualityHelper.java) kết nối [SessionManager.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/data/local/SessionManager.java), [User.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/data/model/User.java), [ApiService.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/data/remote/ApiService.java) (`PUT /auth/preferences`), đồng bộ giữa [ProfileActivity.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/auth/ProfileActivity.java) và [ProfileBottomSheetDialog.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/auth/ProfileBottomSheetDialog.java).
   - [PlayerActivity.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/player/PlayerActivity.java) & [SongOptionsBottomSheetDialog.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/player/SongOptionsBottomSheetDialog.java): `showAlbumDialog()` gọi `getAlbumDetails` phát toàn bộ album hoặc bài hiện tại; `showArtistDialog()` gọi `getSongsByArtist` + `toggleFollowArtist`; `showAddToPlaylistDialog()` nối `btnCreateNewPlaylist` -> `openCreatePlaylistAndAddSong` và dùng chung `addSongToPlaylist`; `showDeviceConnectDialog()` mở `Settings.ACTION_BLUETOOTH_SETTINGS`.
   - [LoginActivity.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/auth/LoginActivity.java): Nút Quên mật khẩu hiển thị hộp thoại `MaterialAlertDialogBuilder` hướng dẫn khôi phục tài khoản rõ ràng.

### 7.3. Tối ưu tương phản chữ đen (`#050506`) trên nút xanh `#38BDF8` ở Dark Mode
- **Android (`values/colors.xml`, `values-night/colors.xml`):** Định nghĩa token `@color/on_primary_accent` (`#FFFFFF` ở Light Mode, `#050506` ở Dark Mode).
- Áp dụng đồng bộ `@color/on_primary_accent` và `#38BDF8` cho:
  - `Widget.Spotify.Button.Primary` trong [themes.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/values/themes.xml) & [values-night/themes.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/values-night/themes.xml).
  - Các drawable/lớp liquid ([bg_circle_play_button.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/drawable-night/bg_circle_play_button.xml), [bg_pill_active.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/drawable-night/bg_pill_active.xml), [LiquidDropletPaint.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/ui/liquid/LiquidDropletPaint.java), [LiquidDropletHelper.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/ui/liquid/LiquidDropletHelper.java), [LiquidDropletNavBar.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/ui/liquid/LiquidDropletNavBar.java), [LiquidSelectionMenu.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/ui/liquid/LiquidSelectionMenu.java), [ThemeManager.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/util/ThemeManager.java)).
  - Toàn bộ nút/badge/bong bóng chat/nút phát tròn trên các layout (`activity_artist_detail.xml`, `activity_playlist_detail.xml`, `activity_profile.xml`, `bottom_sheet_profile.xml`, `dialog_notification_detail.xml`, `fragment_ai_chat.xml`, `fragment_home.xml`, `item_chat_user.xml`, `layout_bottom_sheet_album_info.xml`, `layout_bottom_sheet_artist_info.xml`, `layout_bottom_sheet_similar_songs.xml`) và `PlayerActivity.java`.
- **Web Admin ([backend/public/admin.html](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/public/admin.html)):** Quy tắc `html:not([data-theme="light"])` đổi `#primaryActionBtn`, `.bg-\[\#0EA5E9\]:not(.w-2)`, `#confirmDialogConfirmBtn.bg-\[\#0EA5E9\]`, `.brand-badge`, `.mobile-tab.active`, và trạng thái `:hover` của `.bg-\[\#0EA5E9\]\/15` sang nền `#38BDF8` (hover `#7DD3FC`) cùng chữ/icon `#050506`; giữ nguyên `#FFFFFF` trên `#0EA5E9`/`#0284C7` khi ở Light Mode.

### 7.4. Kết quả kiểm thử & đối soát thực tế (Sau các chỉnh sửa cuối cùng)
- **Kiểm tra cú pháp & Bộ kiểm thử Backend (`exit=0` toàn bộ trên Node backend mới khởi động lại):**
  - `node -c` cho `clearDatabase.js`, `AdminController.js`, `moodFlow/index.js`, `lastFmService.js`, `authService.js`: đạt.
  - `npm test`: `12/12 PASSED, 0 FAILED`.
  - `node tests/run_tests.js`: `27/27 ĐẠT (100.0%)` (trên catalog `151` bài hát).
  - `node scripts/test_security_hardening.js`: `13 PASSED, 0 FAILED`.
  - `node scripts/test_album_playlist_permissions.js`: `16/16 ĐẠT (100%)`.
  - `node scripts/test_system_full_regression.js`: `19/19 ĐẠT (100%)`.
  - `scratch/verify_targeted_fixes.js`: `ALL_TARGETED_API_CHECKS_PASSED`.
  - `scratch/full_system_e2e.js`: `50/50 PASSED, 0 FAILED`.
  - `scratch/verify_round11_hygiene.js`: `ALL_ROUND11_HYGIENE_CHECKS_PASSED` — xác nhận `HEAD /vibe-music.apk` trả về `200` (`11,044,761` bytes), `151/151` bài hát có embedding `768` chiều (`missingEmb: 0`), `0` tag podcast, collection `histories` đã xóa (`hasHistoriesCol: false`), truy vấn `"Bạn đời"` trả về `"Bạn đời"` đầu tiên, `PUT /auth/preferences` lưu `streamingQuality=lossless` an toàn theo allowlist, và `POST /admin/songs` tự sinh `moods`/`semanticText`/embedding `768` chiều đồng thời lọc bỏ tag `podcast`.
- **Android Build, Lint & Đồng bộ APK:**
  - `.\gradlew.bat assembleDebug lintDebug testDebugUnitTest --console=plain`: `BUILD SUCCESSFUL in 16s` (`0` lỗi; `582` cảnh báo Lint thường gặp ở mức warning; `:app:testDebugUnitTest NO-SOURCE`).
  - Sao chép [app-debug.apk](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/build/outputs/apk/debug/app-debug.apk) sang [backend/public/vibe-music.apk](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/public/vibe-music.apk): `11,044,761` bytes (`SHA256: 5326AB783240D857A0C99650EC5E920DA390FAD77E208E20BC53629831E1A31E`, khớp tuyệt đối).
- **Kiểm chứng giao diện thực tế Android (`emulator-5554`, `logcat AndroidRuntime:E` trống/`0` crash):**
  - Đã kiểm tra ảnh chụp thực tế ở **Dark Mode**: màn hình Home (`Tất cả` & `LiquidDropletNavBar` `"Trang chủ"` đều dùng nền `#38BDF8` với icon + chữ đen `#050506`), `ProfileBottomSheetDialog` (badge `VIBETUNE`, pill `Tối`), `ProfileActivity` (camera FAB, badge, nút `Lưu thay đổi`, picker `AudioQualityHelper`), `PlaylistDetailActivity` (nút play tròn `#38BDF8` icon đen), `PlayerActivity` (menu bỏ `itemMenuExcludeTaste`, `showAlbumDialog`, `showAddToPlaylistDialog` -> mở `CreatePlaylistBottomSheetDialog`), `AiChatFragment` (nút gửi tròn `#38BDF8` mũi tên đen).
  - Đã kiểm tra ảnh chụp thực tế ở **Light Mode**: Home (`Tất cả` & `LiquidDropletNavBar` `"Trang chủ"`) và `AiChatFragment` giữ chữ/icon trắng (`#FFFFFF`) sắc nét trên nền `#0EA5E9`/`#0284C7`, sau đó đã khôi phục lại Dark Mode mặc định.
- **Kiểm chứng Web Admin (`http://localhost:5000/admin`, `BROWSER_HOST_FACTS` xác nhận `visited_origins: http://127.0.0.1:5000, http://localhost:5000`, `blocked_origins: none`, `final_step_149.png`):**
  - KPI & bảng bài hát hiển thị đúng `151` bài hát, tìm kiếm `"podcast"` cho `0` kết quả (`SMWX` và `Nhã Lam` không còn).
  - Đo computed style Dark Mode trên `#primaryActionBtn`, `.brand-badge`, nút phân trang active, `.bg-[#0EA5E9]:not(.w-2)`, hover `.bg-[#0EA5E9]/15`: nền `#38BDF8` (`rgb(56, 189, 248)`), chữ/icon `#050506` (`rgb(5, 5, 6)`), mở modal Thêm bài hát tương phản tốt; ở Light Mode giữ `#0EA5E9`/`#0284C7` chữ trắng `#FFFFFF` (`rgb(255, 255, 255)`), `0` lỗi Console JS / HTTP, sau đó chuyển lại Dark Mode chuẩn.
- **Trạng thái cơ sở dữ liệu cuối cùng:** `songs: 151`, `artists: 202`, `albums: 12`, `playlists: 19`, `genres: 10`, `users: 11`, `notifs: 19`, `missingEmb: 0`, `podTags: 0`, `hasHistoriesCol: false`.

---

## 8. QUÉT TOÀN DIỆN HỆ THỐNG, DỌN DẸP TRÙNG LẶP CATALOG, TỐI ƯU MÃ NGUỒN VÀ ĐỒNG BỘ TOÀN DIỆN (ĐỢT 12)

### 8.1. Khử trùng lặp Catalog & Dọn dẹp dữ liệu thừa
1. **Khử trùng bài hát "Lưu Niên":**
   - Hợp nhất bản ghi YouTube trùng lặp `6af694f204c4d60a7d180516` vào bài hát album chuẩn `6af13924913713d2c54012e5` (trong Album "Hồng Nhan Bạc Phận Sóng Gió").
   - Gộp lượt nghe (`playsCount: 13`), gộp `PlaylistTrack` (khử trùng), `UserFavorite`, `ListeningHistory`, `ListeningEvent`, `UserInteraction`, `Notification`, và `PlaySession.recentTrackIds/dislikedTrackIds`.
   - Xóa an toàn 2 tệp media dư thừa trên đĩa: `j97_luu_nien_album_tam_thai_tu_track_no_4_jack_boKJ5X.mp3` và `j97_luu_nien_album_tam_thai_tu_track_no_4_jack_boKJ5X.jpg`.
2. **Khử trùng nghệ sĩ & dọn mồ côi:**
   - Gộp `DT TẬP RAP` (`6af6c276bd22c134d723bafe`) và `DT Tập Rap` (`6af6c276bd22c134d723bb08`) thành bản ghi chuẩn `6af6c276bd22c134d723bafe` (tên "DT Tập Rap", slug "dt-tap-rap", bảo toàn avatar thật), xóa nghệ sĩ trùng.
   - Gộp `DRUM7` (`6af6c276bd22c134d723bb01`) và `Drum7` (`6af6c276bd22c134d723bb05`) thành bản ghi chuẩn `6af6c276bd22c134d723bb05` (tên "Drum7", slug "drum7", bảo toàn avatar thật), xóa avatar trùng `drum7_23bb01.jpg` và nghệ sĩ trùng.
   - Làm sạch mảng trùng nghệ sĩ/nhạc sĩ trong bài "LỆ LƯU LY" (`6af692ea04c4d60a7d180438`).
   - Xóa 3 nghệ sĩ mồ côi hoàn toàn không có bài hát/album/lượt theo dõi: `Anh Tú (Tú Dưa)`, `Võ Thiện Thanh`, `Nguyễn Văn Chung` cùng ảnh rác `nguyen_van_chung_464479.jpg`.
3. **Bổ sung lời bài hát & Vector Embedding thực tế:**
   - Ca khúc "Bạn đời" (`6af96c6e0046fd3676563112`) được bổ sung 4 khổ lời bài hát, `semanticText` phong phú và vector embedding 768 chiều thật từ `EmbeddingService`.
   - Tính toán lại toàn bộ `Album.totalTracks` và `Playlist.totalTracks` đồng bộ tuyệt đối với số bài hát thực tế.
   - Kết quả audit database: `Song issues: 0`, `Ref & asset issues: 0`, `Unreferenced files: 0`, `Artists with 0 songs/albums: 0`. Kho nhạc chuẩn: `150 bài hát`, `197 nghệ sĩ`, `12 albums`, `19 playlists`, `246 playlistTracks`.

### 8.2. Hoàn thiện & Tối ưu Backend & Web Admin
1. **Hoàn thiện Model & Middleware:**
   - [backend/src/models/index.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/models/index.js): Bổ sung export `OtpVerification`.
   - [backend/src/middlewares/rateLimiter.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/middlewares/rateLimiter.js): Siết chặt bypass `x-test-suite` chỉ áp dụng khi `process.env.NODE_ENV === 'test'`.
2. **Tối ưu Song Streaming & Quản lý File:**
   - [backend/src/controllers/SongController.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/controllers/SongController.js): Cải tiến `streamSong` với dynamic MIME detection (`.mp3`, `.m4a`, `.aac`, `.flac`, `.wav`, `.ogg`), chuẩn hóa Range header bounds & xử lý lỗi stream `file.on('error')` không để crash server.
   - [backend/src/controllers/AdminController.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/controllers/AdminController.js):
     - Thêm `.select('-embedding')` ở `addSong` và `getAlbumTracks` tránh truyền payload vector khổng lồ qua network.
     - Trong `updateSongLyrics`: tự động kích hoạt `syncSongAiMetadata(song._id, { regenerateEmbedding: true })`.
     - Cho phép vai trò `'artist'` (`['user', 'admin', 'artist']`) trong `createUser` và `updateUser`.
     - Bổ sung helper `safelyDeleteLocalUploadIfUnused(url)` xác minh file upload cục bộ không còn được tham chiếu ở bất kỳ Song, Artist, Album hay Playlist nào trước khi xóa đĩa; tích hợp vào `deleteSong` và `deletePlaylist`.
3. **An toàn hóa Controller & Service:**
   - [backend/src/controllers/ArtistController.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/controllers/ArtistController.js): Thoát ký tự đặc biệt Regex trong tìm kiếm `q`.
   - [backend/src/controllers/eventController.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/controllers/eventController.js): Kiểm tra ObjectId hợp lệ và sự tồn tại của `Song` trước khi ghi `ListeningEvent`/`UserInteraction`; `await updateSessionOnEvent(...)`.
   - [backend/src/services/authService.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/authService.js): Kiểm tra `!user.passwordHash` cho tài khoản Google khi người dùng thử đăng nhập mật khẩu hoặc đổi mật khẩu; chuẩn hóa kiểu `displayName` trong `updateProfile`.
   - [backend/src/services/toolCallingChat.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/toolCallingChat.js): Chuẩn hóa cấu trúc Function Response theo Google Generative AI SDK (`functionResponse: { name, response }`); model mặc định `process.env.LLM_MODEL || 'gemini-2.5-flash'`.
   - [backend/src/services/aiMoodChatbot.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/services/aiMoodChatbot.js): Cập nhật danh sách candidate models sang các model Gemini 2.x/1.5; sử dụng MongoDB `$sample` ngẫu nhiên trong fallback.
   - [backend/src/websocket/socketServer.js](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/src/websocket/socketServer.js): Bổ sung helper `safeSend(ws, payload)` bao try-catch và hàm `close()` dọn dẹp interval & sockets khi shutdown.
4. **Tối ưu Web Admin:**
   - [backend/public/admin.html](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/public/admin.html):
     - Hiển thị chính xác `tokensUsed` (hỗ trợ cả object `{ totalTokens }` lẫn số nguyên) trong tab AI Logs.
     - Bổ sung phím `Escape` và click backdrop trong `showConfirmModal` giúp Promise luôn resolve gọn gàng không bị treo.
     - Bổ sung `<option value="artist">Nghệ sĩ (Artist)</option>` trong form Quản lý User.
     - Đặt lại `coverEl.src = ''` và thêm sự kiện `onerror` dự phòng cho modal Album/Playlist Tracks.

### 8.3. Dọn dẹp mã chết, sửa Lint & Chuẩn hóa Android App
1. **Loại bỏ mã & model không sử dụng:**
   - [ApiService.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/data/remote/ApiService.java): Xóa 5 phương thức Retrofit không còn sử dụng (`checkHealth`, `getMe`, `generateAiPlaylist`, `logInteraction`, `deleteHistoryBatch`).
   - Xóa bỏ 3 tệp model không dùng: `AiGenerateRequest.java`, `AiGenerateResponseData.java`, `AiCriteria.java`.
2. **Sửa cảnh báo Lint & Khả năng tiếp cận:**
   - [NotificationGroupAdapter.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/presentation/notification/NotificationGroupAdapter.java): Sửa `toUpperCase(Locale.getDefault())`.
   - [dialog_notification_detail.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/layout/dialog_notification_detail.xml): Bổ sung `layout_toStartOf="@id/btnDetailClose"` và `layout_marginEnd="8dp"` trên `tvDetailTypeBadge` tránh đè nút đóng.
   - [LiquidDropletNavBar.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/ui/liquid/LiquidDropletNavBar.java): Bổ sung `@SuppressLint("ClickableViewAccessibility")` bảo đảm chuẩn accessibility.
3. **Dọn dẹp tài nguyên & Chuẩn hóa thương hiệu:**
   - [themes.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/values/themes.xml): Xóa style mồ côi `TextAppearance.Spotify.Display`.
   - [strings.xml](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/res/values/strings.xml): Xóa 15 chuỗi không dùng (`chip_all`, `chip_music`, `section_recently_played`, `section_made_for_you`, `section_popular_artists`, `section_recommended`, `lyrics_title`, `queue_title`, `error_network`, `btn_retry`, `btn_undo`, `profile_title`, `premium_badge`, `offline_mode`, `profile_choose_preset`).
   - Chuẩn hóa SharedPreference & Service Channel từ `spotify_` sang `vibetune_` với cơ chế di chuyển mượt mà (seamless backward compatibility migration):
     - [SessionManager.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/data/local/SessionManager.java): `vibetune_session_prefs` (tự động kế thừa từ `spotify_session_prefs`).
     - [ThemeManager.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/util/ThemeManager.java): `vibetune_theme_prefs` (tự động kế thừa từ `spotify_theme_prefs`).
     - [MusicPlaybackService.java](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/src/main/java/com/example/spotifyclone/service/MusicPlaybackService.java): `CHANNEL_ID = "vibetune_playback_channel"`.
4. **Tạo ProGuard Rules:**
   - Tạo mới tệp [android/app/proguard-rules.pro](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/android/app/proguard-rules.pro) với đầy đủ quy tắc bảo vệ Retrofit, OkHttp, Gson, Glide, MediaSession, ViewBinding và Models.

### 8.4. Dọn dẹp Tooling, Scripts & Tài liệu
1. **Loại bỏ UTF-8 BOM:**
   - Tẩy sạch UTF-8 BOM khỏi 5 tệp: `docker-compose.yml`, `clean_database.bat`, `open_apk_folder.bat`, `open_music_folder.bat`, `start_mongodb_docker.bat`.
2. **Cập nhật Biến môi trường:**
   - [backend/.env.example](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/.env.example): Bổ sung đầy đủ 17 biến môi trường kèm hướng dẫn cấu hình (`ADMIN_SYNC_PASSWORD`, `LLM_MODEL`, `LASTFM_API_KEY`, Twilio/eSMS keys).
3. **Cập nhật Thiết kế Database:**
   - [DATABASE_DESIGN.md](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/DATABASE_DESIGN.md): Bổ sung tài liệu 5 collection thực tế (`listening_events`, `play_sessions`, `user_interactions`, `notifications`, `otp_verifications`), cập nhật sơ đồ kiến trúc 4 phân hệ và bảng thuộc tính chi tiết của `Song` (mảng float 768 chiều `embedding`, `artistIds`, `composerId`, `moods`, `activities`).

### 8.5. Kết quả Kiểm thử & Nghiệm thu Toàn diện
1. **Bộ kiểm thử Backend (137/137 test cases ĐẠT 100%):**
   - `npm test`: `12/12 PASSED` (Scoring, Arcs, Selector, Session vectors).
   - `node tests/run_tests.js`: `27/27 ĐẠT (100.0%)` trên kho nhạc thật 150 bài hát với provider `gemini + vector-search-768`.
   - `node scripts/test_security_hardening.js`: `13 PASSED, 0 FAILED` (RBAC, Google RS256 token, IDOR, WebSocket 1008, File Upload magic bytes).
   - `node scripts/test_album_playlist_permissions.js`: `16/16 ĐẠT (100%)` (Phân quyền Album & Playlist, đồng bộ `totalTracks`).
   - `node scripts/test_system_full_regression.js`: `19/19 ĐẠT (100%)` (Catalog, Stream, Range requests, Auth, Playlist lifecycle).
   - `scratch/full_db_audit.js`: `Song issues: 0`, `Ref & asset issues: 0`, `Unreferenced files: 0`, `Artists with 0 songs/albums: 0`.
   - `scratch/verify_targeted_fixes.js`: `ALL_TARGETED_API_CHECKS_PASSED`.
   - `scratch/full_system_e2e.js`: `50/50 PASSED, 0 FAILED (100%)`.
2. **Biên dịch & Kiểm thử Android App:**
   - `.\gradlew.bat assembleDebug`: `BUILD SUCCESSFUL in 23s`, `0` lỗi.
   - Đồng bộ file APK phân phối: [backend/public/vibe-music.apk](file:///c:/NGUYENMINHHIEU/T%C3%A0i%20li%E1%BB%87u/backend/public/vibe-music.apk) (`11,038,341` bytes).
   - Cài đặt APK lên thiết bị ảo `emulator-5554`: `adb install -r` hoàn thành với `Success`.
   - Khởi chạy màn hình chính: `com.example.spotifyclone/.presentation.main.MainActivity` thành công, kiểm tra logcat `AndroidRuntime:E` ghi nhận `0` lỗi / `0` crash.
   - Trạng thái DB cuối cùng: `150 bài hát`, `197 nghệ sĩ`, `12 albums`, `19 playlists`, `10 thể loại`, `246 playlistTracks`. Giao diện Web Admin và Android App vận hành đồng nhất, mượt mà và tối ưu.
