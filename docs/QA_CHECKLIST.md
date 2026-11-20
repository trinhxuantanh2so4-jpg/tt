# ✅ QA_CHECKLIST — Rà soát & sửa lỗi

Phạm vi: 2 tab TikTok/Douyin, vietsub, thuyết minh, lịch sử, player.
Cập nhật: 2026-11-20.

## 1. Lỗi đã tìm thấy và sửa

| # | Mức | Lỗi | File | Cách sửa |
|---|-----|-----|------|----------|
| 1 | 🔴 Critical | `PlayerActivity` không gọi `setContentView` → `findViewById` trả null, crash ngay khi mở video có phụ đề / bản thuyết minh | `PlayerActivity.kt` | Gọi `setContentView(R.layout.activity_player)` trước khi bind view |
| 2 | 🟠 High | Mở `SubtitleActivity` từ Lịch sử (không auto-run) làm nút **Tạo thuyết minh** bị ẩn → không thể tạo bản lồng tiếng cho video đã có vietsub | `SubtitleActivity.kt` | Hiện nút khi đã có cue, không phụ thuộc `withNarration` |
| 3 | 🟠 High | Tải lại cùng một video dùng `CONFLICT_REPLACE` + unique index `video_id` → mất `id`, mất `subtitle_path`/`dub_path`, file `.srt`/`.mp4` bị mồ côi | `DatabaseHelper.kt` | Nếu đã có row cùng `video_id` thì `UPDATE` giữ id và dữ liệu xử lý cũ; chỉ insert khi chưa có |
| 4 | 🟡 Medium | Nút **Bắt đầu xử lý** không cập nhật trạng thái khi quay lại tab Douyin hoặc sau khi analyze lỗi | `DouyinActivity.kt` | Override `onResume()` gọi `updateStartButton()` |
| 5 | 🟡 Medium | Player gắn `SubtitleConfiguration` kể cả khi file `.srt` không tồn tại → ExoPlayer báo lỗi load phụ đề | `PlayerActivity.kt` | Chỉ gắn khi `content://` hoặc file thực sự tồn tại |
| 6 | 🟡 Medium | Ngôn ngữ nguồn mặc định có thể là "Tiếng Trung" cho video TikTok do `setSelection` chạy trước khi gắn listener | `SubtitleActivity.kt` | Gán `selectedSourceLanguage` tường minh theo nền tảng (`zh`/`en`) |
| 7 | 🟢 Low | Nút **Tạo thuyết minh** bị kẹt ẩn nếu pipeline tạo phụ đề lỗi | `SubtitleActivity.kt` | Khôi phục visibility trong `finally` theo danh sách cue |
| 8 | 🟢 Low | `PlayerActivity` không hiển thị tiêu đề video | `PlayerActivity.kt` | Set `toolbar.title` từ `EXTRA_TITLE` |

## 2. Kiểm tra tự động

| Hạng mục | Lệnh / cách kiểm tra | Trạng thái |
|----------|----------------------|-----------|
| Resource compile | `aapt2 compile --dir app/src/main/res -o app/build/tmpcheck/aapt2.zip` | ✅ pass |
| Unit test JVM | `gradle testDebugUnitTest` (CI) | ✅ pass (`7444501`) |
| Build APK debug | `gradle assembleDebug` (CI) | ✅ pass (`7444501`) |
| Release asset | `v1.0.0/app-debug.apk` được ghi đè | ✅ đã cập nhật (`7444501`) |

Unit test hiện có: `UrlParserTest`, `DouyinParserTest`, `SrtWriterTest`, `CueBuilderTest`,
`WavIoTest`, `NarrationTimelineTest`, `DtkMapperTest`, `DtkErrorTest`.

> trạng thái các dòng "✅ pass (`7444501`)" ở bảng trên là của bản trước khi đổi nguồn tải
> sang DTK. Kết quả của commit DTK được ghi nhận riêng trong `docs/PLAN_API_DTK.md` §7.1.

## 3. Checklist tay trên máy thật (chưa chạy trong CI)

- [ ] Tab TikTok: dán link TikTok → tải → mở được, không logo, không có tuỳ chọn phụ đề.
- [ ] Dán link Douyin ở tab TikTok → toast + chuyển sang tab Douyin.
- [ ] Tab Douyin: tải xong → nút **Bắt đầu xử lý** mới bật; bấm khi chưa tải → không chạy.
- [ ] Vietsub: tạo `.srt`, sửa từng câu, mở player thấy phụ đề khớp tiếng.
- [ ] Thuyết minh giữ nền 20%: bản MP4 có giọng Việt + nhạc nền nhỏ.
- [ ] Thuyết minh "Chỉ thuyết minh": MP4 không còn tiếng gốc.
- [ ] Video không có tiếng → báo lỗi đúng bước, không treo.
- [ ] Máy thiếu giọng `vi-VN` → báo lỗi rõ, app không crash.
- [ ] Lịch sử: badge `Vietsub` / `Thuyết minh`; nhấn giữ mở menu; xoá bản xử lý không xoá video gốc.
- [ ] Tải lại cùng video: không mất vietsub/thuyết minh cũ, không tạo file mồ côi.
- [ ] Mở lại bản thuyết minh từ Lịch sử và từ `SubtitleActivity` → không crash.
- [ ] Xoay màn hình / dark mode khi đang chạy → không crash.

## 4. Rủi ro còn lại (chưa xử lý)

- Media3 Transformer có thể lỗi với WAV đầu vào trên một số thiết bị/API cũ; chưa có fallback chỉ xuất WAV.
- Câu dịch dài hơn khoảng trống chỉ chồng nhẹ, chưa tự tăng `speechRate`/fade.
- Burn-in phụ đề cứng chưa làm (`burn_path` mới có cột).
- `HistoryActivity` mở video gốc bằng trình phát ngoài, chưa có màn chọn Gốc / Vietsub / Thuyết minh.
- ML Kit cần Google Play services; TTS cần giọng hệ thống `vi-VN`.

## 5. Checklist nguồn tải DTK (mới, chạy khi có server + key)

Xem chi tiết 8 ca trong `docs/PLAN_API_DTK.md` §8. Những ca ưu tiên:

- [ ] Menu toolbar → **Nguồn tải video**: để trống → 2 tab tải y hệt bản cũ.
- [ ] Nhập Base URL `https://` + key, bấm **Kiểm tra kết nối** → hiện scopes
      `douyin:read`, `tiktok:read` và `rate_limit_per_min`.
- [ ] Dán nguyên caption tiếng Trung có `https://v.douyin.com/...` → tải được MP4 không logo.
- [ ] Link `vm.tiktok.com` → tải được MP4 không logo.
- [ ] Album ảnh / video riêng tư → báo đúng lỗi, **không** fallback vô ích.
- [ ] Key sai / server tắt → báo lỗi rõ; xoá config → nguồn cũ vẫn tải được.
- [ ] Bấm **Huỷ** lúc đang poll task → coroutine dừng ngay.
- [ ] CDN trả 403 → `VideoDownloader` thử mirror từ `media.streams[].urls`.
