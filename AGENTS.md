# AGENTS.md

App Android (Kotlin, Activity + ViewBinding, không dùng Fragment/Compose) để tải video TikTok/Douyin không logo, kèm tuỳ chọn phụ đề tiếng Việt (`.srt`) và thuyết minh bằng TTS.

## Build & kiểm tra

- Một module duy nhất `:app`, package `com.example.tt`; compileSdk/targetSdk 35, minSdk 26, Java 11, AGP 8.7.3, Kotlin 2.0.21.
- **Repo không có Gradle wrapper.** CI dùng Gradle hệ thống 8.10.2 (lệnh `gradle ...`, không phải `./gradlew`).
- Máy local của workspace này không có JDK/Android SDK nên không chạy Gradle được. Hãy push lên `main` rồi kiểm tra bằng CI.
  - Kiểm tra: `https://api.github.com/repos/trinhxuantanh2so4-jpg/tt/commits/<sha>/check-runs`. Log CI không truy cập được từ đây; khi fail đọc `/check-runs/<id>/annotations` (workflow ghi chú lỗi build/test bằng `::error::`).
  - Riêng resource có thể check local: `aapt2 compile --dir app/src/main/res -o app/build/tmpcheck/aapt2.zip` (binary ở `C:\Users\Admin\AppData\Local\Temp\opencode\aapt2\ext\aapt2.exe`).
- Unit test: `gradle testDebugUnitTest`. Chạy 1 class: `gradle testDebugUnitTest --tests "com.example.tt.subtitle.WavIoTest"`.
- Mỗi lần push lên `main`, CI build APK debug và ghi đè `app-debug.apk` trên release cố định `v1.0.0`.
- Trên PowerShell, commit bằng `git commit -F <file>`; dùng `-m` trực tiếp dễ lỗi với tiếng Việt/dấu ngoặc.

## Kiến trúc

- `BaseDownloadActivity` chứa toàn bộ logic tải dùng chung: phân tích URL, analyze, lưu bằng SAF, tiến độ/tốc độ, ghi SQLite, tăng bộ đếm Firestore, clipboard. `MainActivity` (TikTok) và `DouyinActivity` chỉ set `platformFilter`/`navItemId` và override các hook `onVideoCardShown`, `onDownloadCompleted`, `onWrongPlatform`. Luôn gọi `setupDownloadForm(...)` trước; trả về `false` nghĩa là Activity đang chuyển sang màn đăng nhập.
- `VideoResolver.resolve()` là cửa vào duy nhất khi phân tích link: TikTok → `ApiClient` (tikwm.com), Douyin → `douyin/DouyinResolver` (parse trang share `iesdouyin.com`, `_ROUTER_DATA`).
- Layout: `activity_main.xml` và `activity_douyin.xml` cùng include `part_download_form.xml` với id `form`. Khối chỉ dành cho Douyin là `part_subtitle_options.xml`, include với id `subtitleOptions`, và luôn ẩn ở tab TikTok.
- Bottom nav có đúng 4 mục (TikTok, Douyin, Lịch sử, Hồ sơ); Thịnh hành mở từ menu toolbar (`menu_main.xml`). Màn không thuộc 4 tab phải truyền `R.id.nav_none` (khai báo trong `values/ids.xml`) cho `setupBottomNav`.
- SQLite (`data/DatabaseHelper.kt`) đang ở version 3. Khi đổi schema: tăng `DATABASE_VERSION` và thêm cột trong `onUpgrade` bằng `addColumnIfMissing`; không tạo lại bảng.
- Pipeline phụ đề: `AudioExtractor` (PCM mono 16 kHz) → `AsrEngine` (Moonshine; model tải lúc chạy) → `Translator` (ML Kit) → `CueBuilder`/`SrtWriter` (`.srt` trong `filesDir/subtitles`).
- Pipeline thuyết minh: `SystemTtsEngine` (vi-VN) → `NarrationTimeline` (đặt cue theo `startMs`, trộn 20% âm nền, chuẩn hoá) → `WavIo` (WAV 24 kHz) → `NarrationMuxer` (Media3 Transformer 1.6.1, video tắt tiếng + WAV). Thuyết minh chỉ được bắt đầu khi người dùng bấm "Bắt đầu xử lý", tuyệt đối không tự chạy sau khi tải xong.

## Quy ước & lưu ý

- Toàn bộ text UI là tiếng Việt trong `res/values/strings.xml`; repo không có thư mục `values-*` cho đa ngôn ngữ.
- `app/google-services.json` là config demo đã commit. Không xoá; khi Firebase lỗi, `firebase/AuthRepository` tự chuyển sang đăng nhập offline bằng SQLite.
- Unit test phải thuần JVM: `WavIo` và `NarrationTimeline` được thiết kế không phụ thuộc Android. Không thêm import Android vào helper dưới `app/src/test`.
- Media3 Transformer cần `@androidx.annotation.OptIn(markerClass = [UnstableApi::class])`, và `Transformer.start(...)` phải gọi trên main thread.
- APK giới hạn ABI `arm64-v8a` + `x86_64` (`abiFilters`); native libs Moonshine làm APK ~81 MB.
- ML Kit cần Google Play services; TTS cần giọng hệ thống `vi-VN`. Khi thiếu, báo lỗi rõ ràng; không được crash.
- Tài liệu đầy đủ: `README.md` (API, Firebase, SQLite) và `docs/PLAN_TIKTOK_DOUYIN.md` (lộ trình và trạng thái hiện tại).
