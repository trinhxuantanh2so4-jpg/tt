# 🔧 TASK LIST SỬA LỖI PHỤ ĐỀ & THUYẾT MINH

> Lập sau kết quả QA ngày 2026-11-20: A–D FAIL, D **tự thoát ứng dụng**, máy **không có giọng vi-VN**.
> Mục tiêu: sửa nguyên nhân gốc, không chỉ che triệu chứng. Giữ DB version 3, không Fragment/Compose,
> text UI tiếng Việt, thuyết minh vẫn chỉ chạy khi người dùng bấm nút.

## Bảng task

| # | Mức | Triệu chứng QA | Nguyên nhân tìm được trong code | Việc cần sửa | Trạng thái |
|---|-----|----------------|----------------------------------|--------------|------------|
| T1 | 🔴 | A: sửa câu xong mở lại mất; "Lời gốc" thì còn | `SubtitleAdapter.bind()` gọi `setText()` **trước khi remove TextWatcher cũ**. View recycling làm watcher của cue trước chạy, ghi đè nhầm vào `cues[position]` | Remove watcher trước `setText`; callback dùng `cue.index` thay vì adapter position | ✅ |
| T2 | 🔴 | A: phụ đề bị lỗi sau lưu | `persistSubtitleNow()` ghi file nhưng không kiểm tra đọc lại; lệch giữa `cues` và `.srt` | Ghi xong parse lại, so số cue; báo lỗi rõ nếu lệch; unit test JVM roundtrip `SrtWriter` | ✅ |
| T3 | 🔴 | B: badge Vietsub/Thuyết minh không hiện | `HistoryAdapter` chỉ đọc `rec.subtitlePath`/`rec.dubPath` từ SQLite; bản cũ có file `.srt` nhưng DB trống thì mất badge | Badge fallback theo file tồn tại; khi reload tự heal DB path nếu file có sẵn | ✅ |
| T4 | 🟠 | B: nhấn card chọn bản fail | SRT truyền sang player dưới dạng `Uri.parse("/data/.../x.srt")` (không có scheme) → ExoPlayer không mở được | Đổi sang content Uri qua `MediaUri.resolve` + `FLAG_GRANT_READ_URI_PERMISSION`; player đọc `String extra`, validate file trước khi gắn SubRip | ✅ |
| T5 | 🔴 | D: bấm tạo thuyết minh → app tự thoát | Luồng coroutine chỉ `catch (Exception)`; lỗi TTS/OOM/Transformer có thể là `Throwable`; không có precheck giọng nên người dùng bấm xác nhận rồi mới lỗi | Precheck giọng có timeout + message hướng dẫn; `catch (Throwable)` ở `generate()`/`runNarration()`/`play()`; log crash ra file | ✅ |
| T6 | 🟠 | C: đổi giọng/âm nền không áp dụng | Giọng/âm nền chỉ lưu sau khi mux MP4 thành công; máy không có giọng → mux fail → mọi thay đổi mất | Lưu giọng vào SQLite và âm nền vào prefs **ngay khi đổi**, load lại lúc vào màn | ✅ |
| T7 | 🟡 | Không có log khi crash | Không có crash handler | `TTApp` ghi stack trace ra `filesDir/crash_last.txt`, hiện toast tóm tắt lần mở tiếp theo | ✅ |
| T8 | 🟢 | CI / tài liệu | — | `aapt2 compile`, unit test JVM, push `main`, CI xanh, cập nhật `QA_CHECKLIST` form mới | ✅ (`3c58cab`, run `44711443774`; asset 84.929.976 byte) |
| T9 | 🔴 | QA vòng 2: mở Vietsub/Thuyết minh từ lịch sử → app thoát, toast `Lỗi trước đó: RuntimeException: Unable to instantiate activity ... SubtitleActivity: NullPointerException` | `SubtitleActivity` khởi tạo eager `SubtitleRepository(this)`/`SubtitleProcessor(this)`: lúc constructor chạy, Activity **chưa attachBaseContext** nên `context.applicationContext` là null | Đổi 3 field sang `by lazy`; chỉ tạo repository/processor sau khi Activity đã attach | ✅ (`265fc96`, run `44719591284`) |
| T10 | 🟡 | Toast chỉ cắt 160 ký tự của exception ngoài, không thấy nguyên nhân thật | `TTApp` lưu `throwable.toString()` của outer exception | Lưu **root cause** vào summary + full stack vào file; debug APK tự mở `CrashLogActivity` hiện full stack, nút Sao chép/Đóng | ✅ (`265fc96`, run `44719591284`) |

## Thứ tự triển khai

1. T1 + T2 (phụ đề sửa/lưu) → T3 + T4 (lịch sử/player) → T5 + T6 + T7 (thuyết minh/crash) → T8.
2. Mỗi task xong tick ở bảng trên; KHÔNG tick khi mới chỉ sửa code mà chưa qua `aapt2`/CI.

## Tiêu chí nghiệm thu lại (form §8 của `QA_CHECKLIST.md`)

- Sửa 1 câu → Lưu → thoát màn → mở lại: câu sửa còn, lời gốc còn, số cue không đổi.
- Lịch sử hiện badge đúng cả với video đã tạo phụ đề ở bản cũ; nhấn card chọn được 3 bản (khi có đủ).
- Máy **không có giọng vi-VN**: bấm tạo thuyết minh → hiện hướng dẫn cài giọng, **không thoát app**.
- Đổi giọng Nam 1.25x + "Chỉ thuyết minh" → thoát/mở lại vẫn giữ lựa chọn (dù chưa mux được).
- Player đổi bản không crash; báo lỗi bằng tiếng Việt nếu file thiếu.

## Ghi chú cho người test

Máy đang dùng **không có giọng vi-VN** nên ca tạo MP4 thuyết minh chắc chắn chưa tạo được file.
Đây là giới hạn môi trường, không phải lỗi code; sau bản sửa phải **báo lỗi rõ ràng và không crash**.
Để test thành công pipeline TTS, cài gói tiếng Việt trong
`Cài đặt > Ngôn ngữ & nhập > Chuyển văn bản thành giọng nói`.
