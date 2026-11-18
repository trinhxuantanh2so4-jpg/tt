# 🎬 KẾ HOẠCH: TẢI DOUYIN KHÔNG LOGO + VIETSUB & THUYẾT MINH ON-DEVICE

> Tài liệu kế hoạch cho **TT Downloader** (Android, Kotlin, minSdk 26). Mục tiêu: tải video **Douyin (抖音) không logo** và tự động **phụ đề tiếng Việt + thuyết minh (lồng tiếng)** ngay trên máy, dùng [moonshine-ai/moonshine](https://github.com/moonshine-ai/moonshine) cho nhận dạng giọng nói.

---

## 1. Mục tiêu & phạm vi

| # | Tính năng | Mô tả người dùng thấy |
|---|-----------|------------------------|
| 1 | **Tải Douyin không logo** | Dán/chia sẻ link `v.douyin.com/...` hoặc `douyin.com/video/...` → card video (bìa, tiêu đề tiếng Trung, tác giả, thời lượng) → tải MP4 **không logo**, lưu như TikTok hiện tại |
| 2 | **Vietsub tự động** | Video tiếng Trung (Douyin) hoặc tiếng Anh (TikTok) → nhận dạng lời nói → **dịch sang tiếng Việt** → phụ đề hiển thị khi xem trong app + xuất file `.srt` |
| 3 | **Thuyết minh** | Mỗi câu phụ đề được **đọc thành tiếng Việt**, ghép thành track audio mới; tuỳ chọn *giữ âm gốc nhỏ* hoặc *thay hoàn toàn* |
| 4 | **Ghép cứng phụ đề (tuỳ chọn)** | Xuất thêm 1 bản MP4 có phụ đề “cháy” vào hình (không cần ffmpeg) |
| 5 | **Xem lại & sửa** | Danh sách câu phụ đề, sửa từng câu, nghe thử, xuất lại `.srt` |

**Không làm trong đợt này:** tải album ảnh Douyin, tải video Douyin riêng tư (cần cookie), dịch bằng API cloud (cần key/tiền), nhận dạng nhiều người nói (diarization).

---

## 2. Hiện trạng & khoảng trống

| Hạng mục | Hiện tại | Cần thêm |
|----------|----------|----------|
| Nguồn video | `ApiClient.fetchVideoInfo()` chỉ gọi `tikwm.com/api/` cho TikTok | `DouyinResolver` + nhận diện nền tảng |
| Kiểm tra link | `isTikTokUrl()` / `extractTikTokUrl()` | Mở rộng cho `douyin.com`, `v.douyin.com`, `iesdouyin.com` |
| Card video | chip “HD · Không logo”, thời lượng | thêm nhãn nền tảng **TikTok / Douyin** |
| Xử lý hậu kỳ | không có | pipeline ASR → dịch → TTS → mux |
| SQLite | `downloads` (v1) | migration v2: nền tảng, phụ đề, bản lồng tiếng, trạng thái job |
| Quyền | INTERNET, WRITE_EXTERNAL (≤28) | thêm `POST_NOTIFICATIONS` (API 33+) cho job nền |
| Thư viện | Retrofit/OkHttp, Coil, Material 3, SQLite, Firebase | Moonshine, ML Kit Translate, Media3 (Transformer/ExoPlayer), WorkManager |

---

## 3. Kiến trúc tổng thể

```
┌─────────────── NGƯỜI DÙNG ───────────────┐
│ Dán link / chia sẻ từ TikTok, Douyin     │
└───────────────┬──────────────────────────┘
                ▼
        UrlParser (TikTok | Douyin)
                │
      ┌─────────┴──────────┐
      ▼                    ▼
 TikTok: tikwm API   Douyin: DouyinResolver
      │                    │ (share page + fallback API)
      └─────────┬──────────┘
                ▼
        VideoMeta (bìa, tiêu đề, tác giả, duration, playUrl không logo)
                ▼
        VideoDownloader (MediaStore / SAF)  ← đã có
                ▼
      ┌───────── Pipeline hậu kỳ (tuỳ chọn) ─────────┐
      │ 1. AudioExtractor  MP4 → float[] mono 48k    │
      │ 2. AsrEngine       Moonshine (zh / en / vi)  │
      │ 3. Translator      ML Kit (zh→vi, en→vi)     │
      │ 4. SubtitleWriter  .srt + model cue          │
      │ 5. TtsEngine       System TTS / Moonshine TTS│
      │ 6. AudioTimeline   ghép TTS theo timestamp   │
      │ 7. VideoMuxer      Media3 Transformer        │
      └───────────────────┬──────────────────────────┘
                          ▼
     Video gốc + .srt + (bản lồng tiếng / bản phụ đề cứng)
                          ▼
        SQLite (downloads v2) + History + Player
```

---

## 4. Chọn công nghệ (đã kiểm chứng)

### 4.1 Nhận dạng giọng nói — Moonshine ✅

| Thông số | Giá trị |
|----------|---------|
| Dependency | `ai.moonshine:moonshine-voice:0.1.6` (Maven Central, AAR ~23,4 MB) |
| minSdk | 26 → **khớp app hiện tại** |
| Model | tải về lần đầu (không nhúng APK), cache trong thư mục app |
| Tiếng Trung | **Mandarin Tiny Streaming** – 34M tham số, CER 16,1 %, license **MIT** |
| Tiếng Việt | Vietnamese Tiny Streaming – 34M, WER 9,4 %, **MIT** |
| Tiếng Anh | Tiny/Small/Medium Streaming, **MIT** |
| API cần dùng | `Transcriber.transcribeWithoutStreaming(float[] audio, int sampleRate)` → `Transcript` với `TranscriptLine.startTime` + `duration` (giây) |
| Ghi chú | API nhận **audio mono, sample rate tuỳ ý** → không cần resample thủ công; model phi Latinh nên đặt `max_tokens_per_second = 13.0` để tránh cắt cụt |

**Vì sao không dùng:** Whisper.cpp (model 75MB–1,5GB, chậm hơn trên điện thoại), Android `SpeechRecognizer` (tiếng Trung cần online, không ổn định), Vosk (tiếng Trung kém chính xác hơn).

### 4.2 Dịch Trung → Việt — ML Kit Translate

- `com.google.mlkit:translate:17.0.3` — dịch **on-device**, model zh/vi tải lần đầu (~30 MB/ngôn ngữ), sau đó offline.
- Ưu: tích hợp nhanh, không cần server/key, độ trễ thấp.
- Nhược: cần **Google Play services** trên máy; chất lượng khẩu ngữ ở mức khá.
- Fallback khi máy không có Play services: hiển thị phụ đề gốc + cho phép copy; hoặc bật tuỳ chọn **Opus-MT zh→vi (ONNX, ~80 MB)** nếu cần thuần offline (để Phase sau).

### 4.3 Thuyết minh — 2 engine, mặc định là TTS hệ thống

| Engine | Chất lượng | Ghi chú |
|--------|-----------|---------|
| **Android TextToSpeech** (Google TTS `vi-VN`) | Tốt, giọng tự nhiên | miễn phí, offline sau khi tải giọng; dùng `synthesizeToFile()` |
| Moonshine TTS (`vi-vn`, Piper) | Trung bình | theo benchmark của Moonshine, G2P tiếng Việt còn yếu (CER ~79 %) → để **tuỳ chọn thử nghiệm**; có sẵn `synthesize()` trả PCM nên dễ ghép |

→ Thiết kế interface `TtsEngine` để đổi engine mà không sửa pipeline; mặc định `SystemTtsEngine`.

### 4.4 Ghép video / phụ đề cứng — AndroidX Media3

- `androidx.media3:media3-transformer` + `media3-effect` + `media3-exoplayer` + `media3-ui` (bản 1.5.x–1.6.x tương thích compileSdk 35; nâng compileSdk 36 nếu lên 1.8+).
- **Không dùng ffmpeg-kit**: dự án đã ngừng phát hành (2025), artifact bị gỡ khỏi Maven.
- Phụ đề cứng: `TextOverlay` là lớp **trừu tượng** với `getText(presentationTimeUs)` → trả text theo mốc thời gian ⇒ **một lần encode** là đủ cho toàn bộ phụ đề (không phải cắt video theo từng câu).
- Lồng tiếng: `Composition` gồm 2 `EditedMediaItemSequence` (video đã `setRemoveAudio(true)` + audio thuyết minh) chạy song song; hoặc mix nhỏ âm gốc nếu `AudioMixer` khả dụng.
- Phát lại kèm phụ đề mềm: `MediaItem.SubtitleConfiguration` (SRT) + `SubtitleView` → không cần encode.

### 4.5 Tải Douyin không logo — parse share page (không cần chữ ký)

Quy trình đã được nhiều dự án mã nguồn mở dùng và vẫn hoạt động:

1. Trích link từ text chia sẻ: `https?://v\.douyin\.com/[A-Za-z0-9_-]+` (hoặc `douyin.com/video/{id}`, `iesdouyin.com/share/video/{id}`).
2. `GET` link với **mobile User-Agent** (`Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) …`) → theo redirect → lấy `aweme_id`.
3. `GET https://www.iesdouyin.com/share/video/{aweme_id}` (mobile UA, `Referer: https://www.douyin.com/`).
4. Tìm `window._ROUTER_DATA` trong HTML, **cân bằng ngoặc** (không regex tham lam) → JSON.
5. Đọc `loaderData["video_(id)/page"].videoInfoRes.item_list[0]`:
   - `video.play_addr.url_list[0]` → thay `playwm` → `play` nếu có (bản không logo)
   - `video.cover.url_list[0]`, `desc`, `author.nickname`, `duration` (ms), `video.play_addr.data_size`
6. Nếu JSON rỗng/bé bất thường → **fallback** `www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=…` rồi tới API cấu hình được (tikwm/douyin.wtf) → cuối cùng là nút “Mở bằng trình duyệt”.

Rủi ro: Douyin đổi HTML/chặn UA → resolver là module **tách riêng**, dễ cập nhật; fallback + thông báo lỗi rõ ràng.

---

## 5. Thay đổi dữ liệu (SQLite v1 → v2)

```sql
ALTER TABLE downloads ADD COLUMN platform TEXT NOT NULL DEFAULT 'tiktok'; -- tiktok | douyin
ALTER TABLE downloads ADD COLUMN lang TEXT NOT NULL DEFAULT '';            -- zh | en | vi
ALTER TABLE downloads ADD COLUMN subtitle_path TEXT NOT NULL DEFAULT '';
ALTER TABLE downloads ADD COLUMN dub_path TEXT NOT NULL DEFAULT '';
ALTER TABLE downloads ADD COLUMN burn_path TEXT NOT NULL DEFAULT '';
ALTER TABLE downloads ADD COLUMN process_status INTEGER NOT NULL DEFAULT 0;
-- 0 = chưa xử lý, 1 = đang chạy, 2 = có phụ đề, 3 = có thuyết minh, 4 = lỗi
```

- `onUpgrade()` dùng `ALTER TABLE` (giữ dữ liệu người dùng), `DATABASE_VERSION = 2`.
- `DownloadRecord` thêm 6 field mặc định ⇒ code cũ không phải sửa nhiều.
- File phụ đề/âm thanh lưu trong `filesDir/subtitles/{videoId}.srt|.wav` (không cần quyền).

---

## 6. Các phase triển khai

### Phase A — Tải Douyin không logo (P1, ~1,5 ngày)

1. `network/UrlParser.kt`: nhận diện TikTok/Douyin, trích link từ text chia sẻ, chuẩn hoá.
2. `douyin/DouyinResolver.kt`: resolve redirect → parse `_ROUTER_DATA` → `VideoMeta`; cache 10 phút; timeout 20 s; UA mobile.
3. `network/VideoProvider.kt`: interface chung `resolve(url): VideoInfo`, 2 impl `TikTokProvider` (tikwm) & `DouyinProvider`.
4. `MainActivity`: dùng provider theo nền tảng; card hiện chip `Douyin · HD · Không logo`; lỗi riêng cho album ảnh/riêng tư.
5. `AndroidManifest`: thêm `intent-filter` ACTION_SEND đã có (không cần đổi); hiển thị app trong share sheet của Douyin tự động.
6. Test: link `v.douyin.com` (short), `douyin.com/video/{id}`, share text tiếng Trung; JSON mẫu lưu trong `test/resources`.

**Điều kiện hoàn thành:** tải được ≥ 3 video Douyin thật, không logo, không crash khi link sai.

### Phase B — Phụ đề tiếng Việt (P1, ~2 ngày)

1. `subtitle/AudioExtractor.kt`: `MediaExtractor` + `MediaCodec` (AAC/Opus → PCM 16-bit) → `float[]` mono (đúng sample rate gốc), bỏ qua track audio nếu video câm.
2. `subtitle/AsrEngine.kt`: bọc Moonshine `Transcriber`, tải model theo ngôn ngữ (`zh` mặc định cho Douyin, `en` cho TikTok), chạy trên `Dispatchers.Default`, trả `List<Cue(start, end, text)>`.
3. `subtitle/CueBuilder.kt`: gộp cue quá ngắn (< 0,8 s), cắt cue > 7 s, bỏ cue rỗng/ký tự rác, chèn dấu câu.
4. `subtitle/SrtWriter.kt`: xuất `.srt` chuẩn (`HH:MM:SS,mmm`), xuống dòng ≤ 42 ký tự.
5. **UI xem trước**: `SubtitleActivity` (mới) — danh sách cue, sửa text, nút “Xuất .srt”, “Lưu vào máy”.
6. **Xem trong app**: `PlayerActivity` (mới) dùng ExoPlayer + `SubtitleView`, nhận `subtitle_path`, có nút bật/tắt phụ đề.
7. Lưu `subtitle_path` + `process_status = 2` vào SQLite; History hiện badge **Vietsub**.

### Phase C — Dịch sang tiếng Việt (P1, ~1 ngày)

1. `subtitle/Translator.kt`: ML Kit `TranslatorOptions(language = VI)` + `translate(text)` theo lô 20 cue (giữ thứ tự, gộp lỗi), cache theo `(videoId, text)`.
2. Ngôn ngữ nguồn: chọn tay (Tự động/Trung/Anh/Việt) hoặc auto theo nền tảng (Douyin → zh, TikTok → en).
3. Giữ **bản gốc** và **bản dịch** trong model cue (hiển thị 2 dòng trong editor: “Gốc / Dịch”).
4. Không dịch được (offline/thiếu Play services) → vẫn giữ phụ đề gốc + toast hướng dẫn.

### Phase D — Thuyết minh (P2, ~2,5 ngày)

1. `subtitle/TtsEngine.kt` interface; `SystemTtsEngine` dùng `TextToSpeech.synthesizeToFile()` (đọc WAV header, resample tuyến tính về 24 kHz mono).
2. `subtitle/AudioTimeline.kt`: tạo buffer dài = duration video, đặt từng câu tại `start` (nếu câu dài hơn khoảng trống → **tăng tốc nhẹ** bằng `PlaybackParams`/giữ nguyên và đè nhẹ âm gốc), chuẩn hoá biên độ, ghi WAV.
3. `subtitle/VideoMuxer.kt`: Media3 `Transformer` + `Composition`:
   - video: `EditedMediaItem` gốc (`setRemoveAudio(true)`), hoặc giữ âm gốc ở mức 20 % (tuỳ chọn “giữ âm gốc nhỏ”)
   - audio: WAV thuyết minh
   - xuất MP4 H.264 + AAC vào thư mục app → copy sang vị trí người dùng chọn (SAF) nếu cần
4. Tuỳ chọn giọng: `vi-VN` nam/nữ theo TTS hệ thống; tốc độ 0,9×–1,2×; `MoonshineTtsEngine` (tuỳ chọn thử nghiệm).
5. Lưu `dub_path`, `process_status = 3`.

### Phase E — Ghép cứng phụ đề + job nền (P3, ~1,5 ngày)

1. `subtitle/BurnInEffect.kt`: custom `TextOverlay` trả cue theo `presentationTimeUs`; style: chữ trắng, viền đen, đáy khung, size theo chiều cao video; ghép cả phụ đề + thuyết minh trong **1 lần encode**.
2. `work/SubtitleWorker.kt` (WorkManager): chạy foreground (notification tiến độ theo bước), có **Huỷ**, chịu được app bị đóng; ghi trạng thái vào SQLite.
3. Ước lượng trước khi chạy: “Video 1:20 ≈ 25–60 giây xử lý” (dựa trên duration + model).
4. Chế độ xuất: `Nhanh` (chỉ phụ đề mềm + thuyết minh) / `Đầy đủ` (thêm bản phụ đề cứng); cảnh báo dung lượng & pin.

### Phase F — Hoàn thiện (P4, ~1 ngày)

- History: menu dài (Xem phụ đề · Xuất .srt · Mở bản lồng tiếng · Xoá bản xử lý).
- Profile → “AI & Ngôn ngữ”: tải/xoá model (Trung/Việt/Anh), chọn giọng TTS, chỉ tải model khi có Wi-Fi, xoá cache.
- Strings mới (100 % tiếng Việt), dimens/styles dùng lại design system hiện có.
- README: mục Douyin, sơ đồ pipeline, bảng model & dung lượng, hướng dẫn cấp quyền thông báo.

---

## 7. UI/UX chi tiết

| Màn | Thay đổi |
|-----|----------|
| **Tải video** | Ô link gợi ý “Dán link TikTok hoặc Douyin…”; chip nền tảng tự nhận; card có nhãn `TikTok/Douyin`; nút **“Phụ đề & thuyết minh”** (outlined, icon `ic_subtitles`) mở BottomSheet |
| **BottomSheet** | 3 công tắc: *Phụ đề tiếng Việt* (bật mặc định), *Lồng tiếng*, *Ghép cứng vào video*; chọn ngôn ngữ gốc; dòng ước lượng thời gian; nút **Bắt đầu xử lý** |
| **Tiến trình** | Stepper 5 bước: Tách âm thanh → Nhận dạng → Dịch → Lồng tiếng → Ghép video; mỗi bước có % + nút Huỷ; thông báo khi xong (mở video / xem phụ đề) |
| **SubtitleActivity** | Danh sách cue `00:03 → 00:05`, 2 dòng Gốc/Dịch, bấm để sửa, nút nghe thử, xuất `.srt`, “Lưu bản dịch” |
| **PlayerActivity** | ExoPlayer + SubtitleView; chọn bản: gốc / lồng tiếng / phụ đề cứng; tốc độ phát; phụ đề bật/tắt |
| **Lịch sử** | Badge `Vietsub`, `Lồng tiếng`; menu: xem, xuất srt, mở bản đã xử lý, xoá bản xử lý (giữ video gốc) |
| **Hồ sơ** | Khối “AI & Ngôn ngữ”: dung lượng model đã tải, nút tải/xoá, chọn giọng, Wi-Fi only |

Nguyên tắc: mọi thao tác > 0,3 s có phản hồi; mọi job dài có Huỷ + chạy nền; lỗi nói rõ **bước nào** hỏng.

---

## 8. Hiệu năng & dung lượng (ước tính)

| Hạng mục | Dung lượng | Ghi chú |
|----------|-----------|---------|
| `moonshine-voice` AAR (arm64-v8a) | ~8–12 MB trong APK | AAR gốc 23,4 MB gồm nhiều ABI; bật `abiFilters` chỉ arm64 + x86_64 |
| Model Mandarin Tiny Streaming | ~40–80 MB | tải lần đầu, cache |
| ML Kit zh→vi | ~30 MB mỗi model | tải lần đầu |
| Giọng TTS hệ thống | 20–50 MB | do Google TTS quản lý (ngoài app) |
| APK tăng | ~+10 MB | chưa tính model tải sau |

| Tác vụ (video 60 s, máy tầm trung) | Thời gian ước tính |
|---|---|
| Tách âm thanh | 2–5 s |
| ASR (Moonshine) | 5–15 s |
| Dịch (ML Kit) | 2–5 s |
| TTS (20–30 câu) | 5–15 s |
| Ghép audio | 1–3 s |
| Encode lại video (tuỳ chọn) | 30–120 s |
| **Tổng (không encode)** | **~15–40 s** |

---

## 9. Rủi ro & giảm thiểu

| Rủi ro | Mức | Giảm thiểu |
|--------|-----|-----------|
| Douyin đổi HTML/chặn UA | Cao | Resolver tách riêng + fallback API + nút mở trình duyệt; log lỗi rõ |
| Link Douyin album ảnh / video riêng tư | Trung bình | Thông báo “chưa hỗ trợ”, không crash |
| Máy không có Google Play services (ML Kit) | Trung bình | Vẫn tạo phụ đề gốc; tuỳ chọn Opus-MT ONNX sau |
| Chất lượng dịch khẩu ngữ | Trung bình | Cho sửa từng câu + glossary cá nhân (tên riêng, từ lóng) |
| Giọng TTS tiếng Việt kém (Moonshine) | Trung bình | Mặc định TTS hệ thống; Moonshine TTS là tuỳ chọn |
| Encode video nóng máy/tốn pin | Cao | Mặc định **không** burn-in; cảnh báo + chạy foreground + cho phép sạc mới chạy |
| Bản quyền/nội dung | Trung bình | Chỉ dùng cá nhân, ghi rõ trong README; không tải nội dung riêng tư |
| License model | Thấp | Mandarin/Vietnamese Tiny Streaming là **MIT**; tránh model “Community” (non-commercial) |
| APK phình to | Thấp | `abiFilters` + model tải sau, không nhúng assets |
| CI không có emulator | Thấp | Unit test JVM cho parser/SRT/timeline; test tay trên máy thật |

---

## 10. Kiểm thử

**Unit test (JVM, chạy trong CI):**
- `UrlParserTest`: link TikTok/Douyin/text chia sẻ tiếng Trung, link rác.
- `DouyinRouterDataTest`: JSON mẫu → `VideoMeta` đúng (play_addr, duration ms, không logo).
- `SrtWriterTest`: format thời gian, cue 0 ms, cue > 1 h, text nhiều dòng.
- `CueBuilderTest`: gộp/cắt cue, bỏ cue rỗng.
- `AudioTimelineTest`: đặt cue đúng offset, tổng dài = duration video.

**Checklist tay (máy thật):**
1. Dán link Douyin → tải → mở bằng trình phát ngoài, kiểm tra không logo.
2. Video Douyin 60 s → tạo phụ đề → mở trong app, phụ đề khớp tiếng.
3. Bật lồng tiếng → bản mới có tiếng Việt, không lệch tiếng (sai số < 300 ms/câu).
4. Bật “giữ âm gốc nhỏ” → vẫn nghe nhạc nền.
5. Ngắt mạng giữa job → thông báo lỗi đúng bước, có Thử lại.
6. Huỷ job → không còn file rác, trạng thái về “chưa xử lý”.
7. Xoay màn hình/đổi dark mode khi đang chạy → không crash.
8. Video không có tiếng → thông báo “video không có âm thanh”, không treo.

**CI:** thêm bước `gradle testDebugUnitTest` trước `assembleDebug`; lỗi unit test chặn build (kèm báo lỗi qua annotation như hiện tại).

---

## 11. Ưu tiên & thứ tự

```
Phase A (Douyin)  →  Phase B (ASR + SRT + player)  →  Phase C (dịch VI)
     1,5 ngày              2 ngày                        1 ngày
                                     ↓
                Phase D (thuyết minh)  →  Phase E (burn-in + job nền)  →  Phase F (polish)
                     2,5 ngày                    1,5 ngày                  1 ngày
```

| Ưu tiên | Hạng mục | Vì sao |
|---------|----------|--------|
| **P1** | Phase A | Yêu cầu trực tiếp, độc lập, ít rủi ro |
| **P1** | Phase B + C | Giá trị khác biệt chính (vietsub), demo được ngay |
| **P2** | Phase D | Gây ấn tượng mạnh nhưng phụ thuộc B/C |
| **P3** | Phase E | “Xịn” nhưng tốn pin/thời gian; chỉ nên là tuỳ chọn |
| **P4** | Phase F | Hoàn thiện trải nghiệm |

**MVP demo (≈4 ngày):** Douyin không logo + phụ đề tiếng Việt + xuất `.srt` + xem trong app.

---

## 12. Quyết định đã chốt

| # | Vấn đề | Chốt |
|---|--------|------|
| 1 | Engine thuyết minh | **Cả hai**: interface `TtsEngine`, mặc định **TTS hệ thống** (Google `vi-VN`), cho người dùng đổi sang **Moonshine TTS** trong Hồ sơ |
| 2 | Phụ đề cứng (burn-in) | **Để sau MVP** — MVP chỉ có phụ đề mềm (`.srt` + xem trong app); burn-in nằm ở Phase E, mặc định tắt |
| 3 | Ngôn ngữ nguồn | **Tự động theo nền tảng** (Douyin → `zh`, TikTok → `en`), vẫn cho đổi tay trong BottomSheet |
| 4 | Dịch | **Chỉ dùng ML Kit**; máy thiếu Play services → giữ phụ đề gốc + báo lỗi rõ, không thêm Opus-MT ở đợt này |

**Thứ tự thực hiện chốt:** Phase A → B → C (**MVP**, ~4 ngày) → D (thuyết minh, 2 engine) → E (burn-in + job nền) → F (polish).
