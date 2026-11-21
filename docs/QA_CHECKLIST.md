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
| 9 | 🔴 Critical | Tạo phụ đề không ghi `subtitle_path` vào SQLite → lịch sử không có badge, "Xoá bản xử lý" xoá `File("")` và để file mồ côi | `SubtitleActivity.kt`, `DatabaseHelper.kt`, `HistoryActivity.kt` | Lưu path thật + `subtitle_lang`; thêm `clearProcessed(id)`; xoá qua `SubtitleRepository.delete(id)` + WAV/MP4 thật |
| 10 | 🟠 High | Mở lại `.srt` mất câu gốc (SRT chỉ chứa bản dịch), không so sánh được khi sửa | `SubtitleRepository.kt`, `SubtitleSourceCodec.kt` | Lưu câu gốc vào file phụ `${id}.sources` dạng Base64, đọc lại khi parse |
| 11 | 🟠 High | Vào lịch sử sửa phụ đề thì không đổi được giọng/âm nền thuyết minh | `activity_subtitle.xml`, `SubtitleActivity.kt` | Thêm card tuỳ chọn thuyết minh, lấy `record.voice` làm giá trị ban đầu |
| 12 | 🟠 High | Nghe thuyết minh từ lịch sử/SubtitleActivity không có phụ đề chồng và không đổi bản trong player | `HistoryActivity.kt`, `PlayerActivity.kt` | Truyền `EXTRA_DUB_URI` + `EXTRA_START_VARIANT`; spinner Gốc/Vietsub/Thuyết minh trong player |
| 13 | 🟡 Medium | `SubtitleAdapter` dùng `bindingAdapterPosition` trong `TextWatcher` có thể trượt vị trí | `SubtitleAdapter.kt` | Đổi sang `absoluteAdapterPosition` + kiểm tra `NO_POSITION` |
| 14 | 🟢 Low | `item_subtitle_cue.xml` có 2 attribute trên cùng 1 dòng | `item_subtitle_cue.xml` | Tách dòng (aapt2 vẫn compiles nhưng khó đọc) |
| 15 | 🔴 Critical | TextWatcher của ViewHolder tái sử dụng chạy trước `setText`, ghi đè nhầm cue → sửa câu xong mở lại mất | `SubtitleAdapter.kt`, `SubtitleActivity.kt` | Remove watcher cũ trước `setText`; callback dùng `cue.index` ổn định |
| 16 | 🔴 Critical | Ghi `.srt` xong không đọc lại → lệch số cue; SRT truyền sang player bằng bare path → ExoPlayer không mở được | `SubtitleActivity.kt`, `HistoryActivity.kt`, `PlayerActivity.kt` | Verify roundtrip `SrtWriter`; truyền content Uri qua FileProvider + cấp quyền đọc |
| 17 | 🔴 Critical | Không có giọng vi-VN → bấm tạo thuyết minh app tự thoát; lỗi TTS/Media3 là `Throwable` nên `catch (Exception)` không bắt | `NarrationProcessor.kt`, `SubtitleActivity.kt`, `TTApp.kt` | Precheck giọng có timeout 10s, `catch (Throwable)`, ghi crash log + toast lần mở tiếp |
| 18 | 🟠 High | Badge lịch sử mất với bản cũ (có file nhưng SQLite trống); đổi giọng/âm nền mất khi TTS lỗi | `HistoryAdapter.kt`, `HistoryActivity.kt`, `DatabaseHelper.kt` | Badge fallback theo file + tự heal DB; lưu giọng/âm nền ngay lúc đổi |

## 2. Kiểm tra tự động

| Hạng mục | Lệnh / cách kiểm tra | Trạng thái |
|----------|----------------------|-----------|
| Resource compile | `aapt2 compile --dir app/src/main/res -o app/build/tmpcheck/aapt2.zip` | ✅ pass |
| Unit test JVM | `gradle testDebugUnitTest` (CI) | ✅ pass (`f7d3485`, run `44849096548`; thêm test `fitToWindow`) |
| Build APK debug | `gradle assembleDebug` (CI) | ✅ pass (`f7d3485`, run `44849096548`) |
| Release asset | `v1.0.0/app-debug.apk` được ghi đè | ✅ đã cập nhật (`f7d3485`) |

> APK build lại ở mỗi push lên `main` nên **SHA256 thay đổi dù code không đổi**; luôn lấy SHA mới nhất
> từ API release, không ghi cứng vào docs.

Unit test hiện có: `UrlParserTest`, `DouyinParserTest`, `SrtWriterTest`, `CueBuilderTest`,
`WavIoTest`, `NarrationTimelineTest`, `DtkMapperTest`, `DtkErrorTest`, `SubtitleSourceCodecTest`.

> Các test số mới (`DtkMapperTest`, `DtkErrorTest`) chỉ là unit test JVM thuần, chưa thay thế
> QA trên máy thật có server DTK — xem `docs/PLAN_API_DTK.md` §7.1 và §5 của file này.
> Plan nâng cấp lịch sử/vietsub/thuyết minh: `docs/PLAN_HISTORY_VIETSUB_NARRATION.md`;
> test JVM mới `SubtitleSourceCodecTest` (không import Android).

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
- [ ] Sửa 1 câu → **Lưu file .srt** → mở lại vẫn thấy câu đã sửa và "Lời gốc" không mất.
- [ ] Lịch sử: nhấn card hiện chọn bản Gốc/Vietsub/Thuyết minh; nút `⋮` sửa được vietsub + đổi giọng/âm nền.
- [ ] Trong player đổi spinner giữa 3 bản → không crash, phụ đề đi theo bản Vietsub/Thuyết minh.
- [ ] Tạo lại thuyết minh có xác nhận; bản cũ bị thay thế, badge vẫn đúng.
- [ ] Xoá bản xử lý → mở lại màn phụ đề thấy trống, video gốc vẫn mở được.
- [ ] Xoay màn hình / dark mode khi đang chạy → không crash.

## 4. Rủi ro còn lại (chưa xử lý)

- Media3 Transformer có thể lỗi với WAV đầu vào trên một số thiết bị/API cũ; chưa có fallback chỉ xuất WAV.
- Câu dịch dài hơn khoảng trống chỉ chồng nhẹ, chưa tự tăng `speechRate`/fade.
- Burn-in phụ đề cứng chưa làm (`burn_path` mới có cột).
- Câu dịch dài hơn khoảng trống vẫn có thể chồng nhẹ; chưa tự tăng `speechRate`/fade.
- Media3 lỗi khi ghép WAV vẫn báo lỗi rõ, chưa có fallback phát riêng file WAV.
- ML Kit cần Google Play services; TTS cần giọng hệ thống `vi-VN`.
- DB giữ version 3: câu gốc lưu ở file phụ `${id}.sources`, không thêm cột mới.

## 5. Checklist nguồn tải DTK (mới, chạy khi có server + key)

Xem chi tiết 8 ca trong `docs/PLAN_API_DTK.md` §8. Những ca ưu tiên:

- [ ] Menu toolbar → **Nguồn tải video (nâng cao)**: để trống → 2 tab tải y hệt bản cũ.
- [ ] Lấy key demo để QA: https://demo.douyin.wtf → **Sign in** (tự điền sẵn) → **API keys**
      → copy key plaintext → Base URL `https://demo.douyin.wtf` → **Kiểm tra kết nối**
      → hiện scopes `douyin:read`, `tiktok:read` và `rate_limit_per_min`. Demo **chỉ để test**.
- [ ] Dán nguyên caption tiếng Trung có `https://v.douyin.com/...` → tải được MP4 không logo.
- [ ] Link `vm.tiktok.com` → tải được MP4 không logo.
- [ ] Album ảnh / video riêng tư → báo đúng lỗi, **không** fallback vô ích.
- [ ] Key sai / server tắt → báo lỗi rõ; xoá config → nguồn cũ vẫn tải được.
- [ ] Bấm **Huỷ** lúc đang poll task → coroutine dừng ngay.
- [ ] CDN trả 403 → `VideoDownloader` thử mirror từ `media.streams[].urls`.

## 6. Form báo cáo QA (copy khung này để trả lời)

```text
APK: đã cài / lỗi cài (ghi rõ)
Nguồn dùng để test: demo / tự host
Base URL: https://...
Scopes hiện sau "Kiểm tra kết nối": ...

0. Chưa có key → TikTok/Douyin cũ: PASS / FAIL | ghi chú:
1. Cấu hình + Kiểm tra kết nối: PASS / FAIL | dòng trạng thái:
2. TikTok (3 link, có vm.tiktok.com): PASS / FAIL | link fail (nếu có): | có logo không:
3. Douyin (3 link), trong đó dán NGUYÊN caption tiếng Trung: PASS / FAIL |
   message lỗi (nếu có): | caption có bị cắt thành mỗi URL không:
4. Album ảnh: PASS / FAIL | message nhận được:
5. Video riêng tư/xoá: PASS / FAIL | message nhận được: | có fallback không:
6. Key sai / tắt server / xoá config: PASS / FAIL | 3 bước:
7. Huỷ lúc poll task: PASS / FAIL | log server có còn request /tasks/{id} mới không:
8. CDN 403 → mirror: PASS / FAIL / SKIP:

Ảnh chụp màn hình (đã che key): ...
request_id / thời gian xảy ra (để so log DTK): ...
```

> Khi báo lỗi: che key, ghi đúng message tiếng Việt hiện trên app và (nếu có) `request_id`
> trong `meta` của response DTK. Không gửi key thật vào issue/chat công khai.

## 7. Form báo cáo QA nâng cấp Lịch sử / Vietsub / Thuyết minh

```text
APK: đã cài / lỗi cài (ghi rõ)
Thiết bị: máy thật / giả lập | Android ... | có giọng vi-VN: có/không

A. Tạo phụ đề + sửa: PASS / FAIL | câu sửa còn sau khi mở lại: có/không | "Lời gốc" còn: có/không
B. Lịch sử badge Vietsub/Thuyết minh: PASS / FAIL | nhấn card chọn bản: PASS / FAIL
C. Sửa từ lịch sử đổi giọng/âm nền: PASS / FAIL | giọng đã chọn: | âm nền:
D. Tạo lại thuyết minh (có xác nhận): PASS / FAIL | message lỗi nếu có:
E. Nghe thuyết minh có phụ đề chồng: PASS / FAIL
F. Player đổi Gốc ↔ Vietsub ↔ Thuyết minh: PASS / FAIL | bản bị lỗi (nếu có):
G. Xoá bản xử lý: PASS / FAIL | mở lại màn phụ đề thấy: trống/cũ | video gốc mở được: có/không
H. Tải lại cùng video giữ bản cũ: PASS / FAIL

Message lỗi tiếng Việt hiện trên app (nếu có): ...
Ảnh chụp màn hình (đã che key): ...
```

## 8. Form QA sau bản sửa lỗi (2026-11-20)

Bản sửa theo [`PLAN_FIX_SUBTITLE_NARRATION.md`](PLAN_FIX_SUBTITLE_NARRATION.md). Vì máy hiện
**không có giọng vi-VN**, ca tạo MP4 thuyết minh chỉ cần đạt "báo lỗi rõ, không crash";
sau khi cài gói giọng tiếng Việt mới đánh giá chất lượng giọng/âm nền.

```text
APK mới đã cài (release v1.0.0): có / không
Thiết bị: giả lập / máy thật | Android ... | có giọng vi-VN: có/không

1. Sửa 1 câu → Lưu file .srt → thoát màn → mở lại:
   câu sửa còn: có/không | lời gốc còn: có/không | số câu không đổi: có/không
   Cách làm: mở video có phụ đề → màn "Tạo/sửa phụ đề" → sửa 1 dòng ở ô text (không sửa "Lời gốc")
   → bấm "Lưu file .srt" → bấm quay lại → vào Lịch sử → mở tiếp màn phụ đề đó → so sánh lại câu đã sửa.
2. Lịch sử: badge Vietsub hiện đúng: có/không | badge Thuyết minh (nếu có file): có/không
   nhấn card → chọn được bản: Gốc / Có phụ đề / Thuyết minh
3. Từ lịch sử đổi giọng (Nam 1.25x) + "Chỉ thuyết minh" → thoát/mở lại:
   giọng giữ: có/không | âm nền giữ: có/không
4. Bấm "Tạo thuyết minh" khi KHÔNG có giọng vi-VN:
   có hiện hướng dẫn cài giọng: có/không | app có tự thoát: có/không
5. Bấm "Tạo thuyết minh" khi ĐÃ có giọng vi-VN (nếu có):
   tạo được MP4: có/không | nghe có phụ đề chồng: có/không | giọng đúng lựa chọn: có/không
6. Player đổi Gốc ↔ Vietsub ↔ Thuyết minh: không crash: có/không | phụ đề đúng bản: có/không
7. Xoá bản xử lý: sạch file: có/không | video gốc vẫn mở: có/không
8. Nếu vẫn có lỗi: debug APK sẽ tự mở màn "Chi tiết lỗi gần nhất" lúc mở app.
   Bấm "Sao chép log" rồi dán toàn bộ nội dung vào đây (không cần chụp màn hình từng dòng):
   ...
```

## 9. Form QA dịch tiếng Việt & độ khớp (2026-11-21)

Theo [`PLAN_SUBTITLE_SYNC_QUALITY.md`](PLAN_SUBTITLE_SYNC_QUALITY.md).

```text
APK mới đã cài: có / không
Thiết bị: giả lập / máy thật | Android ... | Play services: có / không
Nguồn video: Douyin / TikTok | ngôn ngữ nguồn đã chọn: Tiếng Trung / Tiếng Anh

1. Chọn đúng ngôn ngữ nguồn (Trung cho Douyin, Anh cho TikTok) → bấm "Bắt đầu xử lý":
   tiến độ có hiện "Đã dịch x/y câu sang tiếng Việt": có/không
   mở file .srt: text có phải tiếng Việt: có/không
   nếu vẫn là tiếng Trung/Anh: có hiện message lỗi tiếng Việt: ... (không được thoát app)
2. Thử chọn sai nguồn (video Trung nhưng chọn Tiếng Việt) → ghi rõ kết quả: ...
3. Tạo thuyết minh sau khi có phụ đề:
   câu đọc có đúng mốc phụ đề: có/không | câu sau có bị chồng: có/không
   giọng nghe tự nhiên hơn bản trước: có/không | tốc độ có bị quá nhanh: có/không
4. Đổi giọng sang Nam 1.25x rồi tạo lại: có áp dụng: có/không
5. Nhận xét thêm (ví dụ “vẫn trễ 1 giây ở câu 3”):
   ...
```
