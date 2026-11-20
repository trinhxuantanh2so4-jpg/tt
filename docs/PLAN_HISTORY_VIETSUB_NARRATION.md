# 🗺️ PLAN NÂNG CẤP LỊCH SỬ · VIETSUB · THUYẾT MINH

> Plan phụ, tách từ `docs/PLAN_API_DTK.md` sau khi Phase A–C (đổi nguồn tải) xong.
> Cập nhật: 2026-11-20. Mọi text UI tiếng Việt; Activity + ViewBinding; **không Fragment/Compose**.

## Mục tiêu

1. Lịch sử chọn được bản phát: **Gốc / Có vietsub / Thuyết minh**, và vào sửa được vietsub/thuyết minh ngay từ lịch sử.
2. Hoàn thiện vietsub: lưu đúng `subtitle_path` vào SQLite, giữ lại câu gốc khi mở lại file `.srt`, sửa từng câu rồi lưu là player dùng ngay bản mới.
3. Hoàn thiện thuyết minh: đổi giọng/âm nền ngay trong màn sửa, tạo lại từ phụ đề đã sửa, nghe lại có phụ đề chồng.
4. Không đụng schema DB (giữ version 3), không tự chạy TTS sau khi tải xong, CI build/test như cũ.

---

## Phase 1 — SQLite là nguồn sự thật (không tăng DB version)

- [x] `SubtitleActivity.generate()` / `saveSrt()` gọi `db.updateSubtitle(...)` với đường dẫn thật, trạng thái `done`, ngôn ngữ nguồn.
- [x] `DatabaseHelper`: overload `updateSubtitle(id, path, status, language, sourceLanguage)` ghi cả `subtitle_lang`; thêm `clearProcessed(id)` xoá sạch path/trạng thái mà giữ video gốc.
- [x] `HistoryActivity.deleteProcessed()` dùng `SubtitleRepository.delete(id)` + xoá WAV/MP4 thật, hết cảnh `File("")`.
- [x] Badge `Vietsub` / `Thuyết minh` trong lịch sử hiện đúng vì DB có path thật.

## Phase 2 — Màn sửa vietsub & thuyết minh hoàn chỉnh

- [x] Lưu câu gốc vào file phụ `${id}.sources` qua helper JVM thuần `SubtitleSourceCodec` (Base64 URL-safe, mỗi dòng 1 câu); đọc lại khi mở SRT để vẫn thấy "Lời gốc" sau khi sửa.
- [x] `SubtitleProcessor` lưu cả SRT và sources; `SubtitleRepository.delete()` xoá cả hai.
- [x] Thêm card **Tuỳ chọn thuyết minh** trong `activity_subtitle.xml`: spinner giọng đọc + radio âm nền. Giá trị ban đầu lấy từ intent (tab Douyin) hoặc `record.voice` (mở từ lịch sử).
- [x] `startNarration()` tự lưu SRT trước khi đọc; nếu đã có bản cũ thì hỏi xác nhận “Tạo lại thuyết minh?” (trừ khi đang auto-run từ nút **Bắt đầu xử lý**).
- [x] `openDub()` truyền cả video gốc, MP4 thuyết minh và SRT để nghe lại vẫn thấy phụ đề.
- [x] Sửa `SubtitleAdapter` dùng `absoluteAdapterPosition` an toàn khi TextWatcher gọi.

## Phase 3 — Lịch sử chọn bản phát / sửa

- [x] Nhấn vào card → chọn bản phát (Gốc / Có vietsub / Thuyết minh); nếu chỉ có 1 bản thì mở thẳng.
- [x] Nút “Tuỳ chọn video” trên mỗi card + nhấn giữ → menu xử lý: Sửa vietsub, Tạo/Tạo lại thuyết minh, Xem có phụ đề, Nghe thuyết minh, Xoá bản xử lý (có xác nhận).
- [x] `openSubtitle()` từ lịch sử truyền giọng đã lưu (`record.voice`) nhưng **không autoRun**.
- [x] Mở bản thuyết minh lỗi → toast rõ, không crash.

## Phase 4 — Player đổi bản tại chỗ

- [x] `PlayerActivity` chuyển sang ViewBinding, thêm spinner **Chọn bản phát** và bottom nav `nav_none` theo quy ước.
- [x] Nhận `EXTRA_DUB_URI` + `EXTRA_START_VARIANT`; dựng danh sách nguồn: Gốc, Có phụ đề, Thuyết minh (bản thuyết minh cũng chồng SRT nếu có).
- [x] Đổi spinner → rebuild `MediaItem` (vẫn kiểm tra file tồn tại trước khi gắn SubRip).

## Phase 5 — Docs, test, CI

- [x] Unit test JVM thuần cho `SubtitleSourceCodec` (không import Android).
- [x] `aapt2 compile --dir app/src/main/res` OK local.
- [x] Cập nhật `README.md`, `docs/QA_CHECKLIST.md`, `docs/PLAN_API_DTK.md`, `docs/PLAN_TIKTOK_DOUYIN.md`.
- [x] Push `main`, CI xanh (`3624bf7`, run `44687595777`), release `v1.0.0/app-debug.apk` được ghi đè (84,9 MB, SHA256 `24eb24666a90640522cc47a05b6e369eab96c59eb09ae850295d42549c8bf6eb`).

---

## Checklist nghiệm thu (QA tay)

- [ ] Tạo phụ đề Douyin → sửa 1 câu → **Lưu file .srt** → mở lại màn → câu sửa còn, "Lời gốc" vẫn hiện.
- [ ] Vào Lịch sử: badge `Vietsub` hiện; nhấn card → chọn “Có phụ đề” → phụ đề khớp câu đã sửa.
- [ ] Từ lịch sử chọn “Sửa vietsub” → đổi giọng sang Nam 1.25x, chọn “Chỉ thuyết minh” → tạo lại → badge `Thuyết minh` hiện.
- [ ] Nhấn card → chọn “Thuyết minh” → có giọng Việt, không tiếng gốc, phụ đề vẫn hiện.
- [ ] Trong player đổi spinner Gốc ↔ Có phụ đề ↔ Thuyết minh → không crash, tiêu đề đổi theo bản.
- [ ] “Xoá bản xử lý” → mở lại màn phụ đề thấy trống, video gốc vẫn mở được.
- [ ] Tải lại cùng video → cũ vietsub/thuyết minh không bị mất (DB giữ path).

## Trạng thái

Phase 1–5 xong; code đã push `3624bf7`, CI build + unit test xanh. Chờ QA tay trên máy thật theo
`docs/QA_CHECKLIST.md` §7 (và §6 cho DTK). Các mục còn lại nằm trong phần “Việc còn lại” của
`docs/PLAN_TIKTOK_DOUYIN.md`: burn-in cứng, tự co `speechRate`/fade, Tuỳ chọn Moonshine TTS,
cấu hình giọng trong `ProfileActivity`.
