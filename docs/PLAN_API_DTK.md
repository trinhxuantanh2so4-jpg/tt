# 🚀 KẾ HOẠCH TỐI ƯU TẢI VIDEO BẰNG DTK API

> Nguồn: [Evil0ctal/Douyin_TikTok_Download_API](https://github.com/Evil0ctal/Douyin_TikTok_Download_API)
> (gọi tắt là **DTK**). Cập nhật 2026-11-20 đối chiếu tài liệu `main` v5 (`documents/en/11-api.md`,
> `src/dtk/models/content.py`, `01-quickstart.md`).

## 0. Quyết định ưu tiên (quan trọng nhất)

| Việc | Quyết định đợt này | Lý do |
|------|--------------------|-------|
| **Tải video TikTok + Douyin không logo** | ✅ **Ưu tiên số 1, làm ngay** | Đây là nguồn lỗi lớn nhất hiện tại: TikTok phụ thuộc `tikwm.com`, Douyin phải cào HTML `iesdouyin.com` + sửa `playwm → play`. Một trong hai bên đổi là app hỏng. |
| **Phụ đề tiếng Việt (vietsub)** | ⏸ **Giữ nguyên code, không đụng vào đợt này** | Pipeline `AudioExtractor → AsrEngine → Translator → SrtWriter` đang chạy ổn. Đổi nguồn tải cũng không cải thiện chất lượng ASR. |
| **Thuyết minh (TTS + mux)** | ⏸ **Giữ nguyên code, không đụng vào đợt này** | Không liên quan tới việc lấy link stream. Chỉ nên xem lại sau khi đã có nguồn audio ổn định vài tuần. |
| Thịnh hành, Lịch sử, Hồ sơ, Player | ⏸ Không đổi (trừ 1 màn cấu hình API nhỏ) | Tránh rủi ro lan rộng. |

**Nguyên tắc:** đợt này chỉ thay "cái để lấy link MP4". Mọi thứ sau khi đã có `DownloadRecord`
(SQLite, vietsub, thuyết minh, player, lịch sử) **giữ nguyên 100%**.
Nếu DTK chưa được cấu hình, app vẫn chạy như hiện tại → không có trạng thái "hỏng trong lúc migrate".

---

## 1. Thực trạng và cái DTK giải quyết được

| Hiện tại | Vấn đề | DTK v5 |
|----------|--------|--------|
| TikTok: `GET https://tikwm.com/api/?url=...&hd=1` | Dịch vụ bên thứ ba, có thể giới hạn/đổi response, không kiểm soát được | Một API tự host, không phụ thuộc bên thứ ba |
| Douyin: GET share page `iesdouyin.com`, parse `window._ROUTER_DATA`, đổi `playwm → play` | Rất dễ vỡ khi Douyin đổi HTML/cookie; text chia sẻ tiếng Trung phải tự tách URL | `POST /api/v1/parse` nhận **nguyên text chia sẻ**, trả stream sạch logo (`watermark: false`) |
| Hai resolver, hai schema, hai kiểu lỗi | Code lặp (`ApiClient` + `DouyinResolver`), khó test | Một schema `Content` chuẩn cho cả 2 nền tảng |
| Không có `duration_ms` / `size_bytes` nhất quán | Chip hiển thị đôi khi thiếu | Có sẵn `duration_ms`, `media.video.size_bytes` |
| Album ảnh: báo lỗi chung chung | Không phân biệt được | `kind: "image_album"` → báo đúng "link album ảnh, chưa hỗ trợ" |

---

## 2. DTK v5 — những gì cần nhớ trước khi code

### 2.1 Gọi API

```bash
# Base URL tự host (demo: https://demo.douyin.wtf — CHỈ để thử nghiệm)
X-API-Key: dtk_<prefix>_<secret>       # hoặc Authorization: Bearer <key>

POST /api/v1/parse?wait=20            # nhận nguyên text chia sẻ, 1 endpoint cho cả 2 nền tảng
Content-Type: application/json
{"url": "7.95 复制打开抖音... https://v.douyin.com/xxxx/"}

GET  /api/v1/tasks/{task_id}          # khi submit trả 202
GET  /api/v1/auth/me                  # kiểm tra key còn sống + scopes
GET  /api/v1/system/status            # version, health, pool
Swagger: {BASE}/swagger
```

- Mọi JSON đều có envelope: `{ success, data, error, meta }`.
  **Branch theo `error.code`, không branch theo `error.message`.**
- Content endpoint là **async**: mặc định trả `202 { task_id, state: "queued" }`.
  `?wait=20` giữ kết nối tối đa 20s (ceiling mặc định 30s). Hết 20s vẫn trả `202` —
  **đây không phải lỗi**, phải lấy `task_id` đi poll.
- Poll: `GET /api/v1/tasks/{id}` → `data.state = queued|running|done|failed`.
  Khi `done`, payload nằm ở **`body.data.data`** (lồng 2 lớp). Task **failed vẫn là HTTP 200,
  `success: true`** — lỗi nằm trong `data.error`.
- Kết quả task giữ **24h** (`retention.task_result_hours`), quá hạn `TASK_NOT_FOUND` → submit lại.
- Scope tối thiểu: `douyin:read` + `tiktok:read`. **Không** cấp `media:*`, `identity:manage`, `admin`.
- Rate limit mặc định 120 req/phút/key; demo công khai ~30 req/10s. Có cache 30 phút cho 1 post
  và coalesce 90s cho request trùng → double-tap không tốn identity.
- URL CDN đã ký **hết hạn trong vài giờ** → phải tải ngay sau khi resolve (luồng hiện tại đã vậy).
  Không lưu `downloadUrl` để dùng lại từ Lịch sử.

### 2.2 Schema `Content` trả về (theo `src/dtk/models/content.py`)

```jsonc
{
  "platform": "douyin",            // hoặc "tiktok"
  "content_id": "7123456789012345678",
  "kind": "video",                 // "image_album" → chưa hỗ trợ
  "web_url": "https://www.douyin.com/video/...",
  "title": "dòng đầu của mô tả",
  "description": "...",
  "duration_ms": 31200,
  "author": { "uid": "...", "sec_uid": "...", "unique_id": "...", "nickname": "...",
              "avatar": { "url": "..." } },
  "media": {
    "covers": [ { "url": "...", "urls": ["..."] } ],
    "video": { "url": "...", "urls": ["..."], "width": 1080, "height": 1920,
               "size_bytes": 2411724, "watermark": false },
    "streams": [ /* bitrate khác, cũng có watermark flag */ ],
    "images": []
  }
}
```

`raw` (payload gốc của nền tảng) **không trả về mặc định** → không cần bật `?include_raw=`
( Large payload, tốn thêm một lần gọi upstream ).

---

## 3. Ánh xạ `Content` → `VideoInfo` (không đổi kiểu dữ liệu app)

| `DownloadRecord` / `VideoInfo` | Nguồn từ DTK | Ghi chú |
|--------------------------------|--------------|---------|
| `videoId` | `content_id` | String, 19 chữ số → **giữ dạng String**, không parse Long |
| `title` | `title`, fallback `description`, rồi `"Video Douyin/TikTok"` | |
| `author` | `author.nickname` → `author.unique_id` → `"Không rõ"` | |
| `coverUrl` | `media.covers.firstOrNull()?.url` | |
| `sourceUrl` | text người dùng nhập (giữ nguyên) | fallback `web_url` |
| `downloadUrl` | `media.video?.url` nếu `watermark == false`, else `media.streams.first { !it.watermark }?.url` | **Không** lấy stream có `watermark: true` |
| `quality` | `"HD"` nếu `height >= 1080`, else `"NO_WATERMARK"` | chỉ để hiện chip, logic tải không đổi |
| `fileSize` | `media.video?.size_bytes ?: 0` | Chip dung lượng chính xác hơn hiện tại |
| `platform` | map `"douyin"/"tiktok"` → `VideoPlatform` | phải khớp `platformFilter` của tab |
| `duration` | `duration_ms / 1000` | Chip thời lượng |

Nếu `kind != "video"` → ném lỗi `"Link này là album ảnh, chưa hỗ trợ tải"`.
Nếu không có stream `watermark == false` → ném lỗi `"Video riêng tư hoặc đã bị xoá"`.

---

## 4. Kiến trúc đề xuất (tối giản, không đụng luồng cũ)

```
BaseDownloadActivity.analyze()            (GIỮ NGUYÊN)
        │
        ▼
VideoResolver.resolve(source)            (cửa vào duy nhất - GIỮ NGUYÊN chữ ký)
        │
        ├─ có cấu hình DTK? ──► DtkResolver.resolve(source) ──► DtkMapper.toVideoInfo()
        │                              │ 202 → poll /tasks/{id}
        │                              └ lỗi retryable → fallback (xem §6)
        │
        ├─ TikTok ──► ApiClient.fetchVideoInfo()   (tikwm, giữ làm fallback)
        └─ Douyin ──► DouyinResolver.resolve()     (iesdouyin, giữ làm fallback)
```

### File mới

| File | Trách nhiệm |
|------|-------------|
| `network/dtk/DtkConfig.kt` | Đọc base URL + key từ `SharedPreferences`; `enabled = baseUrl & key không trống`; mặc định rỗng → DTK tắt |
| `network/dtk/DtkApi.kt` | Retrofit: `parse()`, `task()`, `authMe()`. `data` kiểu `JsonElement` vì 200/202 trả shape khác nhau |
| `network/dtk/DtkModels.kt` | `DtkEnvelope<T>`, `DtkError`, `DtkTask`, `DtkContent`, `DtkMedia`, `DtkVideoStream`, `DtkAuthor` |
| `network/dtk/DtkMapper.kt` | Pure Kotlin: JSON → `VideoInfo`. **Không import Android** → test JVM được |
| `network/dtk/DtkResolver.kt` | Submit → xử lý 200/202 → poll → map lỗi; `withContext(IO)`; `ensureActive()` giữa các lần poll |
| `network/dtk/DtkException.kt` | `code`, `retryable`, `messageVi` — dùng để quyết định fallback |
| `ui/ApiSettingsActivity.kt` + `activity_api_settings.xml` | Màn nhập Base URL / API key, nút "Kiểm tra kết nối" (`authMe`) và "Xoá cấu hình" |

### File sửa (rất ít)

| File | Thay đổi |
|------|----------|
| `network/VideoResolver.kt` | Thêm nhánh DTK ở đầu; truyền **text gốc** cho `DtkResolver` (vì `/parse` nhận cả caption) |
| `menu_main.xml`, `MainActivity.kt`, `DouyinActivity.kt` | Thêm 1 item 🔧 mở `ApiSettingsActivity` (dùng `R.id.nav_none`) |
| `download/VideoDownloader.kt` | Thêm tham số `headers: Map<String,String> = emptyMap()`; nếu CDN trả 403 thì trimer `Referer` theo nền tảng |
| `strings.xml` | ~12 chuỗi VI mới (tiêu đề màn config, lỗi DTK, trạng thái kết nối) |
| `AndroidManifest.xml` | Khai báo `ApiSettingsActivity` |

**Không sửa:** `BaseDownloadActivity` (luồng tải), `SubtitleActivity`, `Narration*`, `DatabaseHelper`,
`PlayerActivity`, `HistoryActivity`. Không tăng `DATABASE_VERSION`.

---

## 5. Cấu hình & bảo mật key

- **Không nhét key vào APK.** Release `v1.0.0` là APK công khai → bất kỳ key nào nhúng vào
  BuildConfig/CI secrets đều bị lộ sau khi decode. Dùng cấu hình **lúc chạy**, lưu trong
  `SharedPreferences` riêng (`dtk_prefs`), `allowBackup=false` đã có trong manifest cần kiểm tra lại.
- Key chỉ có 2 scope `douyin:read`, `tiktok:read`, đặt `rate_limit` riêng (vd 60/phút), **có thể revoke**.
- Nút "Kiểm tra kết nối" gọi `GET /api/v1/auth/me`, hiện `via`, scopes và `rate_limit_per_min`
  để người dùng biết key đang dùng đúng chưa.
- Nếu base URL là `http://` (LAN/dev), cần `networkSecurityConfig` cho phép cleartext **chỉ cho domain
  do người dùng nhập** — không bật `usesCleartextTraffic=true` toàn app. Production dùng HTTPS/Caddy.
- Dùng demo `https://demo.douyin.wtf` chỉ để thử nghiệm; không đưa vào config mặc định của app.

### Self-host tối thiểu (tài liệu chính: `01-quickstart.md`)

```bash
git clone https://github.com/Evil0ctal/Douyin_TikTok_Download_API.git
cp .env.example .env
# điền POSTGRES_PASSWORD, REDIS_PASSWORD, DTK_SECRET_KEY (>= 32 ký tự)
docker compose -p dtk -f docker/compose.yml up -d --wait --wait-timeout 300
# muốn tự mint identity Douyin/TikTok:
echo 'DTK_BROWSER_RPC_URL=http://browser-rpc:9000' >> .env
docker compose -p dtk -f docker/compose.yml --profile browser up -d --build
# lấy setup token từ log, mở http://<host>:8000/setup?token=... để tạo account admin
# Console → API keys → Create key: tick douyin:read + tiktok:read, copy key 1 lần duy nhất
curl -s http://<host>:8000/api/v1/auth/me -H "X-API-Key: dtk_..."   # verify
```

Đặt reverse proxy TLS trước port 8000; không expose Postgres/Redis ra internet.

---

## 6. Chiến lược fallback & xử lý lỗi

DTK là provider **ưu tiên khi đã cấu hình**, nhưng chỉ fallback về provider cũ khi lỗi
**thuộc về hạ tầng** (mạng/server), không fallback khi lỗi **về nội dung**
(video private/xoá/sai link) — nếu không sẽ tốn 2 lần gọi và hiện thông báo sai.

| Nhóm `error.code` | Hành động | Fallback? |
|-------------------|-----------|-----------|
| `RATE_LIMITED`, `QUEUE_FULL`, `IDENTITY_POOL_EXHAUSTED`, `ENDPOINT_CIRCUIT_OPEN`, `UPSTREAM_RISK_CONTROL`, `UPSTREAM_UNREACHABLE`, `SIGNING_FAILED`, `INTERNAL` | retry có backoff, tôn trọng `retry_after` | ✅ có |
| `INVALID_URL`, `UNSUPPORTED_CONTENT`, `NOT_FOUND`, `CONTENT_PRIVATE`, `MEDIA_WITHHELD`, `IDENTITY_LOGIN_REQUIRED`, `INVALID_PARAM` | dừng, hiện message VI cụ thể | ❌ không |
| `UNAUTHENTICATED`, `FORBIDDEN_SCOPE` | dừng, gợi ý vào màn cấu hình kiểm tra key/scope | ❌ không |
| `TASK_NOT_FOUND` | submit lại 1 lần | ✅ có |
| `IOException` / timeout / HTTP 5xx | retry 1 lần rồi fallback | ✅ có |

Thông báo VI mẫu: `Không kết nối được máy chủ API`, `Key API hết hạn hoặc thiếu quyền`,
`Video này là album ảnh, chưa hỗ trợ`, `Máy chủ API đang quá tải, thử lại sau ... giây`.

**Poll loop:** interval 800ms, deadline 90s, `ensureActive()` mỗi vòng (Huỷ của `BaseDownloadActivity`
phải dừng được poll), read timeout OkHttp ≥ 35s để không cắt `?wait=20`.

---

## 7. Các phase triển khai

| Phase | Việc | Ước lượng | Xong khi |
|-------|------|-----------|----------|
| **A. Client + mapping** ✅ | `DtkApi/Models/Mapper/Resolver/Exception`, unit test fixture JSON thật, chưa nối vào UI | 0.5 ngày | `gradle testDebugUnitTest` xanh, mapper đúng 6 link mẫu |
| **B. Cấu hình + nối vào** ✅ | `ApiSettingsActivity`, `menu_main`, `VideoResolver` chọn provider + fallback | 0.5–1 ngày | Có key → analyze dùng DTK; không có key → app chạy y hệt cũ |
| **C. Tải & QA** ✅ code / ⏳ QA máy thật | Header `Referer` nếu CDN 403, xử lý link rút gọn/caption, checklist tay, CI xanh | 0.5 ngày | 3 link TikTok + 3 link Douyin tải được, CI xanh, APK release cập nhật |
| **D. Dọn dẹp (sau)** | Khi DTK chạy ổn định: bỏ `tikwm`/`iesdouyin`, bỏ fallback, cập nhật README | — | Chưa làm |
| **E. Vietsub & thuyết minh (sau nữa)** | Chỉ xem lại nếu có lý do liên quan nguồn audio; không nằm trong đợt này | — | Chưa làm |

### 7.1 Trạng thái code (2026-11-20)

Đã triển khai xong phần code của Phase A–C:

- Mới: `network/dtk/{DtkModels,DtkException,DtkConfig,DtkApi,DtkStreamFallbacks,DtkMapper,DtkResolver}.kt`.
- Mới: `ui/ApiSettingsActivity.kt` + `res/layout/activity_api_settings.xml`; item menu
  `action_api_settings` trên toolbar của cả 2 tab; khai báo trong `AndroidManifest.xml`.
- Sửa: `VideoResolver.kt` (chọn DTK khi có config, fallback chỉ khi lỗi hạ tầng),
  `TTApp.kt` (`DtkConfig.init`), `BaseDownloadActivity.kt` (truyền text gốc cho `/parse`
  và gửi `Referer` theo nền tảng), `VideoDownloader.kt` (thử mirror CDN khi 403/404).
- Test JVM: `DtkMapperTest`, `DtkErrorTest`.
- **Không đổi:** `DatabaseHelper` (vẫn version 3), `SubtitleActivity`, `Narration*`,
  `PlayerActivity`, `HistoryActivity`, giao diện 2 tab.

Chưa làm được ở local: chạy Gradle (không JDK/SDK) và QA 8 ca trên máy thật/có key DTK.

---

## 8. Kiểm thử

**Unit test JVM (`app/src/test`, thuần Kotlin/JVM — không import Android):**
- `DtkMapperTest`: fixture `Content_video.json` (Douyin + TikTok), `Content_task.json`,
  `Content_image_album.json` → kiểm tra `videoId`, `downloadUrl` không watermark, `duration`, `size`.
- `DtkErrorTest`: map 12 mã lỗi → `retryable` đúng; failed task (`state=failed`, HTTP 200) vẫn ném lỗi.

**CI (mỗi push `main`):** `testDebugUnitTest` → `assembleDebug` → ghi đè `app-debug.apk` trên `v1.0.0`.
Không truyền key vào CI.

**Checklist tay (8 ca):**
1. Không cấu hình DTK → tab TikTok/Douyin tải như cũ (regression).
2. Cấu hình đúng key → "Kiểm tra kết nối" hiện scopes `douyin:read`, `tiktok:read`.
3. Link TikTok thường + link `vm.tiktok.com` → tải MP4 không logo.
4. Link Douyin thường + link `v.douyin.com` + **nguyên caption tiếng Trung** → tải MP4 không logo.
5. Link album ảnh → báo đúng lỗi, không crash.
6. Video riêng tư/xoá → báo lỗi ngắn, **không** fallback vô ích.
7. Key sai / server tắt → báo lỗi rõ, không crash; bật lại provider cũ vẫn tải được.
8. Bấm Huỷ lúc đang poll task → dừng ngay, không còn job chạy nền.

---

## 9. Rủi ro & giảm thiểu

| Rủi ro | Mức | Giảm thiểu |
|--------|-----|-----------|
| Phải tự vận hành server (Docker, Postgres, Redis, identity pool) | Cao | Giữ fallback cũ; hướng dẫn self-host trong doc; mặc định DTK tắt |
| Key dính trong APK công khai | Cao | Chỉ cấu hình runtime, scope read-only, có thể revoke; không dùng CI secret |
| Identity pool cạn / upstream risk control | Trung bình | Backoff theo `retry_after`, coalesce/cacheserver, fallback provider cũ |
| CDN URL hết hạn trước khi tải xong | Thấp | Tải ngay sau resolve; nếu 403 → thử `media.streams[].urls` rồi báo lỗi rõ |
| `?wait=20` bị hiểu nhầm là thất bại khi vẫn trả 202 | Trung bình | Code xử lý 202 như bình thường, poll tiếp; unit test cover |
| Thay đổi `VideoResolver` làm hỏng 2 tab | Trung bình | Nhánh DTK nằm trước nhưng bật/tắt bằng config; checklist ca 1 |
| DTK trả `kind: image_album` / `CONTENT_PRIVATE` | Thấp | Báo lỗi VI cụ thể, không fallback |

---

## 10. Tiêu chí hoàn thành đợt này

- [x] Có plan này, phân tách rõ "tải trước — vietsub/thuyết minh sau".
- [x] Code `DtkResolver` lấy MP4 không logo cho cả 2 nền tảng qua 1 API (⏳ cần thử bằng key thật).
- [x] Không có key nào trong source/APK; config nhập lúc chạy (`dtk_prefs`).
- [x] Không cấu hình → app chạy y hệt bản `fbe8ba3` (mặc định `DtkConfig.enabled = false`).
- [x] Không đụng code vietsub/thuyết minh/DB/player.
- [x] CI xanh (`e518b12`), `app-debug.apk` trên `v1.0.0` được ghi đè (84,9 MB).
- [ ] QA 8 ca trên máy thật có server DTK + key read-only.

> Lần đầu push (`2e49de6`) CI đỏ do Gson overload không rõ trong `DtkErrorTest`;
> đã sửa ở `e518b12` bằng `JsonParser.parseString(...)` rồi build lại thành công.

## 11. Phạm vi không làm trong đợt này

- Không sửa pipeline phụ đề/thuyết minh (`SubtitleActivity`, `NarrationProcessor`, `WavIo`, TTS).
- Không đổi schema SQLite (giữ version 3).
- Không đổi giao diện 2 tab, bottom nav, lịch sử, player.
- Không chuyển tab Thịnh hành sang DTK: `/api/v1/{platform}/trending` trả **danh sách từ khoá**,
  không phải danh sách video như `tikwm/api/feed/list` hiện tại → giữ nguyên.
- Không hỗ trợ tải album ảnh/image posts.
- Không dùng transcription API của DTK để thay ASR trên máy.
