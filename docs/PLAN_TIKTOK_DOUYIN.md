# 🧭 KẾ HOẠCH TÁCH 2 PHẦN: TIKTOK (API) & DOUYIN (VIETSUB + THUYẾT MINH)

> Tài liệu kế hoạch cho **TT Downloader**. Mục tiêu: tách app thành 2 luồng rõ ràng —
> **(1) Tải TikTok bằng API** đơn giản, nhanh; **(2) Tải Douyin không logo kèm tuỳ chọn
> phụ đề tiếng Việt và thuyết minh (lồng tiếng) ngay trên máy**.
> Trạng thái: **Phase 0–5 + tinh chỉnh luồng Douyin đã hoàn tất**, CI xanh, APK debug ~81 MB trên release `v1.0.0`.

## Trạng thái triển khai (cập nhật)

| Phase | Trạng thái |
|-------|-----------|
| 0. Nền & CI, tách layout + `BaseDownloadActivity`, bottom nav 4 mục | ✅ xong (CI xanh `efdf4e6`) |
| 1. Tab TikTok chỉ nhận TikTok, chặn link Douyin + chuyển tab | ✅ xong |
| 2. Tab Douyin + tuỳ chọn vietsub/thuyết minh | ✅ xong |
| 3. Thuyết minh: TTS hệ thống → WAV → Media3 Transformer, DB v3 | ✅ xong (CI xanh `086ea0e`) |
| 4. Lịch sử: badge Vietsub/Thuyết minh, menu mở bản lồng tiếng/xoá bản xử lý | ✅ xong (CI xanh `c294d16`) |
| 5. `abiFilters`, README, strings, unit test | ✅ xong (CI xanh `c294d16`) |
| 6. Tinh chỉnh luồng Douyin: không tự chạy, chờ bấm "Bắt đầu xử lý" | ✅ xong (CI xanh `e8e2955`) |
| 7. `AGENTS.md` tiếng Việt cho session sau | ✅ xong (`7568690`) |
| 8. QA: rà soát, sửa 8 lỗi (player crash, mất dữ liệu khi tải lại, nút thuyết minh...) | ✅ xong, xem `docs/QA_CHECKLIST.md` |

### Khác biệt so với kế hoạch ban đầu (đã triển khai thực tế)

| Nội dung kế hoạch | Thực tế đã làm |
|---|---|
| Tự mở `SubtitleActivity` sau khi tải nếu bật vietsub | **Không tự mở.** Tải xong chỉ hiện gợi ý; nút **Bắt đầu xử lý** mới bật khi có `filePath` + vietsub đang bật, và mở `SubtitleActivity` với `autoRun = true`. |
| `process_status` dạng số `0..4` | Dùng TEXT: `""` (chưa xử lý), `"done"`, `"error"`; trạng thái vietsub/thuyết minh suy ra từ `subtitle_path` / `dub_path`. |
| `updateProcessing(subtitlePath, dubPath, status)` | Tách thành `updateSubtitle(...)` + `updateDub(...)` để không ghi đè chéo. |
| Cache TTS trong `filesDir/tts` | TTS ghi file tạm trong `cacheDir` rồi xoá ngay; WAV/MP4 cuối ở `filesDir/narration/{id}.wav` và `{id}.mp4`. |
| Tự tăng `speechRate` khi câu dài hơn khoảng trống | Chưa làm; clip được đặt đúng `startMs`, phần dư chồng nhẹ lên cue sau, không fade, chuẩn hoá đỉnh ≤ 0.9. |
| `PlayerActivity` chọn nguồn Gốc / Vietsub / Thuyết minh | Không đổi `PlayerActivity`; nơi gọi truyền `EXTRA_VIDEO_URI` + `EXTRA_SUBTITLE_URI` tương ứng. |
| `ProfileActivity` chọn giọng đọc, xoá model | Chưa có; giọng chọn ngay ở tab Douyin. |
| APK ~127 MB | ~81 MB nhờ `abiFilters` `arm64-v8a` + `x86_64`. |

### Việc còn lại (post-MVP)

- **Ưu tiên tiếp theo: chuyển nguồn tải sang DTK API** — xem `docs/PLAN_API_DTK.md`
  (tải trước; vietsub/thuyết minh giữ nguyên, phát triển sau).
- Burn-in phụ đề cứng vào hình (`burn_path` đã có cột, chưa dùng).
- Tự co giãn `speechRate` / fade khi cue dài hơn khoảng trống.
- Màn chọn nguồn phát trong `PlayerActivity` (gốc / vietsub / thuyết minh).
- Tuỳ chọn Moonshine TTS thay TTS hệ thống.
- Cấu hình giọng đọc + xoá model AI trong `ProfileActivity`.

---

## 1. Mục tiêu người dùng thấy

| # | Phần | Người dùng làm gì | App làm gì |
|---|------|-------------------|------------|
| 1 | **Tab TikTok** | Dán/chia sẻ link TikTok → Phân tích → Tải | Gọi API `tikwm.com` lấy MP4 **không logo**, lưu máy, ghi lịch sử. Không có tuỳ chọn phụ đề. |
| 2 | **Tab Douyin** | Dán/chia sẻ link Douyin → Phân tích → chọn tuỳ chọn → Tải | Resolve trang share `iesdouyin.com` lấy MP4 **không logo**, rồi (tuỳ chọn) chạy pipeline **ASR → dịch VI → `.srt`**, và **TTS → ghép track thuyết minh → xuất MP4 lồng tiếng**. |
| 3 | **Lịch sử** | Mở video đã tải | Xem video gốc, xem/sửa phụ đề, mở bản thuyết minh, badge `Vietsub` / `Thuyết minh`. |
| 4 | **Hồ sơ** | Chọn giọng đọc, xoá model/cache *(post-MVP)* | Cấu hình TTS + dung lượng model AI. Hiện giọng đọc chọn ngay ở tab Douyin. |

**Ngoài phạm vi đợt này:** burn-in phụ đề cứng vào hình, tải album ảnh Douyin, dịch bằng cloud API.

---

## 2. Điều hướng mới

### Phương án chốt: 4 mục bottom nav, tách hẳn TikTok / Douyin

```
[ TikTok ]   [ Douyin ]   [ Lịch sử ]   [ Hồ sơ ]
 MainActivity  DouyinActivity  HistoryActivity  ProfileActivity
```

- `MainActivity` **đổi nhãn thành "TikTok"**, chỉ nhận link TikTok; dán link Douyin →
  toast *"Link Douyin — chuyển sang tab Douyin"* + điều hướng.
- `DouyinActivity` **mới**, có thêm khối tuỳ chọn vietsub/thuyết minh.
- Tab **Thịnh hành** rời bottom nav, vẫn truy cập được bằng nút *"Video thịnh hành"*
  (icon `ic_whatshot`) ở góc màn TikTok → mở `TrendingActivity` như cũ.
- Không đổi sang Fragment.

> Đã chốt với người dùng: chỉ cần tách TikTok và Douyin; thuyết minh **chờ bấm "Bắt đầu xử lý"**.

### Thay đổi cụ thể

| File | Việc |
|------|------|
| `res/menu/menu_bottom.xml` | Đổi title `nav_download` → "TikTok"; thêm item `nav_douyin` (icon mới `ic_douyin`); bỏ `nav_trending`. |
| `ui/BottomNav.kt` | Map `nav_douyin` → `DouyinActivity`; bỏ nhánh `nav_trending`. |
| `AndroidManifest.xml` | Đăng ký `DouyinActivity` (`exported=false`). |

---

## 3. Tái cấu trúc code (không Fragment, không copy-paste)

Hiện `MainActivity` ôm toàn bộ luồng phân tích + tải + SAF + tiến độ. Nếu copy sang
`DouyinActivity` sẽ thành 2 bản trùng ~400 dòng.

### Cách làm: layout dùng chung + Activity cha

```
res/layout/
├── part_download_form.xml     # ô link, nút Dán/Phân tích, card video, nơi lưu, progress, Huỷ
├── part_subtitle_options.xml  # CHỈ dùng ở Douyin: vietsub + thuyết minh
├── activity_main.xml          # toolbar + <include part_download_form> + bottomNav
└── activity_douyin.xml        # toolbar + <include part_download_form> + <include part_subtitle_options> + bottomNav
```

```
BaseDownloadActivity (abstract)
├── binding: ActivityMainBinding/ActivityDouyinBinding? → tách PartDownloadFormBinding
├── analyze(url) / startDownload(uri) / uploadHistory() / progress UI
├── abstract val platformFilter: VideoPlatform?   // TikTok chỉ nhận TIKTOK
└── open hook: protected open fun onDownloadCompleted(record: DownloadRecord) {}

MainActivity   : BaseDownloadActivity()  // filter = TIKTOK
DouyinActivity : BaseDownloadActivity()  // filter = DOUYIN, override onDownloadCompleted()
```

- ViewBinding với `<include>`: đặt `android:id="@+id/form"` → `binding.form.btnAnalyze`.
- `BaseDownloadActivity` giữ nguyên logic SAF launcher, tiến độ, tốc độ, huỷ, ghi SQLite.
- `DouyinActivity` chỉ thêm phần đọc tuỳ chọn + gọi pipeline.

**Rủi ro thấp** vì hành vi tải giữ nguyên, chỉ di chuyển code; test tay lại 1 lần.

---

## 4. UI tab Douyin (sau khi phân tích link)

```
┌──────────────────────────────────────────┐
│  Ảnh bìa video            [Douyin · HD]  │
│  Tiêu đề tiếng Trung                     │
│  Tác giả · 00:32                         │
├──────────────────────────────────────────┤
│  Nơi lưu: Download/Douyin/...       [>]  │
│  [ Tải video ]        [ Huỷ ]            │
├──────────────────────────────────────────┤
│  🎬 Vietsub & thuyết minh                │
│  [✓] Phụ đề tiếng Việt                   │
│  [ ] Thuyết minh bằng giọng đọc          │
│  Ngôn ngữ video:  [ Tiếng Trung  ▾ ]     │
│  Giọng đọc:       [ Nữ · 1.0x     ▾ ]    │
│  Âm thanh nền:    (•) Giữ nhỏ 20%        │
│                   ( ) Chỉ thuyết minh    │
│  Ước tính: ~35 giây xử lý                │
│  [ Bắt đầu xử lý ]                       │
└──────────────────────────────────────────┘
```

Quy tắc UX:
- Công tắc **Thuyết minh** chỉ bật được khi **Phụ đề** đang bật.
- Nút **Bắt đầu xử lý** luôn hiển thị nhưng **disabled** cho tới khi video đã tải xong (có `filePath`)
  và vietsub đang bật; tải xong chỉ hiện toast gợi ý, không tự chạy.
- Nếu người dùng không bật gì → chỉ tải video như bình thường.
- Mặc định: phụ đề **bật**, thuyết minh **tắt**, ngôn ngữ **Tiếng Trung**, giọng **Nữ · 1.0x**,
  nền **Giữ nhỏ 20%**.
- Mọi bước dài: progress theo bước; xong → toast xác nhận, trong `SubtitleActivity` có nút
  *Xem video có phụ đề* / *Nghe bản thuyết minh*.

---

## 5. Pipeline thuyết minh (Phase D)

```
.mp4 (đã tải)
  │
  ├─ AudioExtractor ──► PCM gốc (mono 16k, float)
  │
  ├─ SRT đã có (từ Phase B/C) ──► danh sách cue + bản dịch VI
  │
  ├─ SystemTtsEngine ──► mỗi cue → WAV tạm trong cacheDir (đọc PCM rồi xoá)
  │
  ├─ NarrationTimeline ──► buffer dài = duration video, PCM 24 kHz
  │     • đặt từng clip tại startMs
  │     • clip dài hơn khoảng trống → chồng nhẹ lên cue sau (chưa fade)
  │     • trộn PCM gốc (hệ số 0.2 nếu "giữ nền") + clip thuyết minh
  │     • chuẩn hoá đỉnh ≤ 0.9, ghi WAV
  │
  └─ NarrationMuxer (Media3 Transformer)
        Composition(
          sequence 1: video gốc, removeAudio = true,
          sequence 2: WAV thuyết minh (không loop)
        ) ──► filesDir/narration/{id}.mp4 (giữ nội bộ, không copy ra ngoài)
```

### Thành phần mới

| File | Nhiệm vụ |
|------|----------|
| `subtitle/TtsEngine.kt` | Interface: `isAvailable(locale)`, `suspend synthesize(text, rate, voice): FloatArray`, `shutdown()`. |
| `subtitle/SystemTtsEngine.kt` | `TextToSpeech` + `synthesizeToFile()`; `UtteranceProgressListener` bọc `suspendCancellableCoroutine`; đọc WAV trả PCM. |
| `subtitle/WavIo.kt` | Đọc/ghi WAV PCM 16-bit mono (dùng cho TTS và track cuối). |
| `subtitle/NarrationTimeline.kt` | Đặt clip theo timestamp, trộn nền, chuẩn hoá, ghi WAV. |
| `subtitle/NarrationMuxer.kt` | Media3 `Transformer` + `Composition` (video muted + audio thuyết minh), callback tiến độ. |
| `subtitle/NarrationProcessor.kt` | Ghép 3 bước trên + `ensureActive()`, trả `dubPath`. |

### Dependency thêm

```toml
media3Transformer = "1.6.1"
# androidx.media3:media3-transformer:1.6.1
```
(`media3-exoplayer`, `media3-ui` đã có; `Media3 Transformer` hỗ trợ trộn nhiều track audio từ 1.2+.)

### Ghi chú kỹ thuật

- Ưu tiên **trộn PCM thủ công** (đã có `AudioExtractor`) rồi chỉ để Transformer ghép video + 1 track audio
  → tránh phụ thuộc API gain/volume còn `@UnstableApi`, và kiểm soát được độ to nhỏ.
- TTS hệ thống là mặc định (Google TTS `vi-VN`). Moonshine TTS để tuỳ chọn Phase sau.
- Máy thiếu giọng VI: `isLanguageAvailable` = false → hiện hộp thoại mở
  `Settings.ACTION_TTS_SETTINGS` / `ACTION_INSTALL_TTS_DATA`, không crash.
- Video > 10 phút: cảnh báo dung lượng/CPU; giới hạn mềm 15 phút cho MVP.

---

## 6. SQLite v2 → v3

```sql
ALTER TABLE downloads ADD COLUMN dub_path      TEXT NOT NULL DEFAULT ''; -- MP4 thuyết minh
ALTER TABLE downloads ADD COLUMN voice         TEXT NOT NULL DEFAULT ''; -- vi-VN nu/nam/1.0x
ALTER TABLE downloads ADD COLUMN subtitle_lang TEXT NOT NULL DEFAULT ''; -- zh/en/vi
-- để dành, chưa dùng ở đợt này
ALTER TABLE downloads ADD COLUMN burn_path     TEXT NOT NULL DEFAULT '';
```

- `DATABASE_VERSION = 3`; `onUpgrade` chạy tiếp `if (oldVersion < 3) { ... }` với `addColumnIfMissing`.
- `DownloadRecord` thêm 4 field default ⇒ code cũ không phải sửa.
- Thực tế dùng `updateSubtitle(id, path, status, language)` và `updateDub(id, dubPath, voice, status)`
  (không gộp thành `updateProcessing`).
- `process_status` là TEXT: `""` chưa xử lý · `"done"` xong · `"error"` lỗi. Trạng thái vietsub /
  thuyết minh suy ra từ `subtitle_path` / `dub_path` thay vì mã số.

---

## 7. Các phase triển khai

| Phase | Nội dung | Ước lượng |
|-------|----------|-----------|
| **0. Nền & CI** | Sửa CI xanh (đã sửa `Theme.Material3.Compact`), tách `part_download_form.xml` + `BaseDownloadActivity`, bottom nav 4 mục (TikTok/Douyin/Lịch sử/Hồ sơ), đổi nhãn TikTok, đưa Thịnh hành vào nút phụ | 0,5–1 ngày |
| **1. Tab TikTok** | `MainActivity` chỉ nhận TikTok; chặn link Douyin + gợi ý chuyển tab; bỏ khối phụ đề khỏi màn TikTok | 0,5 ngày |
| **2. Tab Douyin** | `DouyinActivity` + `part_subtitle_options.xml`; lưu tuỳ chọn; nút **Bắt đầu xử lý** mở `SubtitleActivity` với `autoRun` khi đã tải xong | 1 ngày |
| **3. Thuyết minh** | `TtsEngine` + `SystemTtsEngine` + `WavIo` + `NarrationTimeline` + `NarrationMuxer` + `NarrationProcessor`; UI giọng đọc; progress 2 bước TTS/Mux | 2–2,5 ngày |
| **4. Xem & lịch sử** | History badge + menu mở bản lồng tiếng, xoá bản xử lý; `PlayerActivity` giữ nguyên, nhận URI từ nơi gọi | 1 ngày |
| **5. Hoàn thiện** | Strings VI, dọn file tạm, `abiFilters` giảm APK, README + sơ đồ, unit test mới, CI + release asset | 0,5–1 ngày |

**Tổng:** ~5,5–6,5 ngày làm việc. MVP của đợt này = Phase 0 → 4.

---

## 8. Danh sách file thay đổi

### Mới

```
app/src/main/java/com/example/tt/
├── DouyinActivity.kt
├── BaseDownloadActivity.kt
├── subtitle/TtsEngine.kt
├── subtitle/SystemTtsEngine.kt
├── subtitle/WavIo.kt
├── subtitle/NarrationTimeline.kt
├── subtitle/NarrationMuxer.kt
└── subtitle/NarrationProcessor.kt

app/src/main/res/
├── layout/part_download_form.xml
├── layout/part_subtitle_options.xml
├── layout/activity_douyin.xml
├── drawable/ic_douyin.xml
└── drawable/ic_translate.xml

app/src/test/java/com/example/tt/subtitle/
├── WavIoTest.kt
└── NarrationTimelineTest.kt
```

### Sửa

| File | Thay đổi |
|------|----------|
| `MainActivity.kt` | Kế thừa `BaseDownloadActivity`; chỉ TikTok; mở DouyinActivity khi gặp link Douyin |
| `activity_main.xml` | Dùng `<include part_download_form>`; bỏ `btnSubtitle` khỏi card |
| `menu_bottom.xml`, `ui/BottomNav.kt` | 4 mục, map `nav_douyin`; bỏ `nav_trending` |
| `SubtitleActivity.kt` | Nhận thêm tuỳ chọn thuyết minh; nút *Tạo thuyết minh* / *Nghe bản thuyết minh*; progress nhiều bước; `autoRun` |
| `PlayerActivity.kt` | Không sửa; nhận `EXTRA_VIDEO_URI` / `EXTRA_SUBTITLE_URI` từ nơi gọi |
| `HistoryActivity.kt`, `ui/HistoryAdapter.kt` | Badge + menu ngữ cảnh |
| `data/DatabaseHelper.kt`, `data/DownloadRecord.kt` | v3 + 4 cột mới |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | `media3-transformer` |
| `AndroidManifest.xml`, `values/strings.xml` | Activity mới + toàn bộ chuỗi VI |
| `README.md` | Sơ đồ 2 phần + hướng dẫn TTS |
| `AGENTS.md` | Hướng dẫn build/kiến trúc/gotchas cho session sau |

---

## 9. Rủi ro & giảm thiểu

| Rủi ro | Mức | Giảm thiểu |
|--------|-----|-----------|
| Máy không có giọng TTS tiếng Việt | Trung bình | Check `isLanguageAvailable`, mở Settings TTS, cho phép xuất `.srt` dù không thuyết minh được |
| Câu dịch dài hơn khoảng trống → lệch tiếng | Cao | Cho chồng nhẹ lên cue sau (hiện tại); tự tăng `speechRate`/fade là việc post-MVP |
| Encode video chậm/nóng máy | Trung bình | Chỉ encode khi bật thuyết minh, progress từng bước |
| Transformer lỗi thiết bị cũ | Thấp | Fallback: chỉ xuất `.srt` + WAV thuyết minh rời, báo rõ |
| Douyin đổi HTML | Cao | Resolver tách riêng (đã có), fallback API + nút mở trình duyệt |
| Tách code làm hỏng luồng tải cũ | Trung bình | Phase 0 làm trước, test tay 3 link TikTok + 3 link Douyin, unit test parser giữ nguyên |
| Dung lượng file (MP4 + SRT + MP4 lồng tiếng) | Thấp | Cho xoá bản xử lý trong Lịch sử, hiển thị dung lượng |
| APK phình to do native libs Moonshine | Trung bình | Đã bật `abiFilters` chỉ `arm64-v8a` + `x86_64`: APK ~81 MB (trước đó ~127 MB); model vẫn tải sau, không nhúng |

---

## 10. Kiểm thử

**Unit test JVM (CI):**
- `WavIoTest`: ghi → đọc lại đúng PCM, header 44 byte.
- `NarrationTimelineTest`: clip đặt đúng `startMs`, tổng dài = duration video, trộn không tràn biên độ.
- Giữ `UrlParserTest`, `DouyinParserTest`, `SrtWriterTest`, `CueBuilderTest`.

**Checklist tay:**
1. Tab TikTok: dán link TikTok → tải → mở được, không logo, không có nút phụ đề.
2. Dán link Douyin ở tab TikTok → toast + chuyển tab Douyin.
3. Tab Douyin: tải video → nút **Bắt đầu xử lý** mới bật → bấm → có `.srt`, xem trong app khớp tiếng.
4. Bật thuyết minh (giữ nền 20%) → MP4 mới có giọng Việt + nhạc nền nhỏ.
5. Chọn "Chỉ thuyết minh" → MP4 không còn tiếng gốc.
6. Video không có tiếng → báo lỗi đúng bước, không treo.
7. Huỷ giữa encode → không còn file rác, DB về trạng thái cũ.
8. Máy thiếu giọng TTS → hộp thoại hướng dẫn, app không crash.
9. Xoay màn hình / dark mode khi đang chạy → không crash.
10. Lịch sử: badge đúng, mở được bản gốc và bản thuyết minh, xoá bản xử lý không xoá video gốc.
11. Chưa tải xong mà bấm **Bắt đầu xử lý** → nút disabled (không chạy pipeline).

**CI:** mỗi push `main` chạy `testDebugUnitTest` → `assembleDebug` → ghi đè `app-debug.apk`
trên release `v1.0.0`. Kết quả xem qua check-runs / annotations (xem `AGENTS.md`).

---

## 11. Quyết định đã chốt

| # | Vấn đề | Chốt |
|---|--------|------|
| 1 | Điều hướng | **4 mục: TikTok · Douyin · Lịch sử · Hồ sơ**; Thịnh hành mở bằng icon 🔥 trên toolbar (`menu_main.xml`) |
| 2 | Thời điểm thuyết minh | **Chờ người dùng bấm "Bắt đầu xử lý"** sau khi tải xong |
| 3 | Giọng mặc định | Nữ · 1.0x · giữ nền 20% |
| 4 | Vietsub cho TikTok | Không đưa lên UI chính; giữ code để dùng lại sau |
| 5 | Burn-in phụ đề cứng | Để phase sau |

