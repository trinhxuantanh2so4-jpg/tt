# TT Downloader 📱

App Android tải video **TikTok không logo** (no watermark), tích hợp **đăng nhập Firebase** và **lịch sử tải SQLite**.

> 🔗 **Repo:** https://github.com/trinhxuantanh2so4-jpg/tt
> 📥 **Tải APK chạy thử ngay:** https://github.com/trinhxuantanh2so4-jpg/tt/releases (file `app-debug.apk`)

App trình diễn **3 kỹ thuật chính**:

| # | Kỹ thuật | Áp dụng trong app |
|---|----------|-------------------|
| 1 | **SQLite** | Lịch sử tải video, cache profile, phiên đăng nhập, tài khoản offline dự phòng — CRUD đầy đủ (3 bảng, index, transaction) |
| 2 | **Gọi API mạng** | Retrofit + OkHttp gọi API miễn phí `tikwm.com` để phân tích link TikTok + danh sách video thịnh hành |
| 3 | **Firebase backend** | Đăng ký / Đăng nhập (Firebase Auth) + lưu & đọc profile người dùng (Cloud Firestore) — app gọi tới backend mình tự tạo trên Firebase Console |

## Màn hình

| Màn hình | Chức năng |
|----------|-----------|
| **LoginActivity** | Đăng nhập / Đăng ký (Firebase Auth, tự dự phòng offline bằng SQLite nếu chưa cấu hình Firebase) |
| **MainActivity** | Dán link TikTok → phân tích qua API → tải video không logo; hiển thị video thịnh hành từ API |
| **HistoryActivity** | Lịch sử tải từ SQLite: xem danh sách (SELECT), yêu thích (UPDATE), xóa 1 dòng / xóa hết (DELETE) |
| **ProfileActivity** | Hồ sơ từ Firestore: xem tên, email, số video đã tải; đổi tên (ghi lên backend); đăng xuất |

---

# 🌐 KỸ THUẬT 2 — TÀI LIỆU API MẠNG

## API dùng trong app: [tikwm.com](https://tikwm.com)

API **miễn phí, không cần API key, không giới hạn request**. App gọi 2 endpoint:

### Endpoint 1: Phân tích link TikTok (lấy link video không logo)

```
GET https://tikwm.com/api/?url={LINK_TIKTOK}&hd=1
```

| Tham số | Bắt buộc | Ý nghĩa |
|---------|----------|---------|
| `url`   | ✅ | Link video TikTok cần phân tích (dạng `https://www.tiktok.com/@user/video/123...` hoặc link rút gọn `vm.tiktok.com/...`) |
| `hd`    | ❌ | `1` = ưu tiên trả link HD không logo |

**Response mẫu** (đã rút gọn):

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "7676427551027350802",
    "title": "#fyp #xuhuong",
    "cover": "https://p19-common-sign.tiktokcdn.com/....jpeg",
    "duration": 7,
    "play": "https://v19.tiktokcdn-us.com/video/....mp4",
    "hdplay": "https://v16-notes.tiktokcdn-us.com/video/....mp4",
    "wmplay": "https://v19.tiktokcdn-us.com/video/....mp4",
    "size": 401394,
    "wm_size": 510980,
    "hd_size": 401394,
    "music": "https://....mp3",
    "author": {
      "id": "6920012640840582145",
      "unique_id": "tieuthat2000",
      "nickname": "Nàng tiên nhỏ",
      "avatar": "https://....jpeg"
    }
  }
}
```

**Các trường quan trọng:**

| Trường | Ý nghĩa |
|--------|---------|
| `code` | `0` = thành công, `-1` = lỗi (xem `msg`) |
| `data.play` | Link video **KHÔNG logo** (chất lượng SD) |
| `data.hdplay` | Link video **KHÔNG logo** (chất lượng HD) ⭐ app ưu tiên dùng cái này |
| `data.wmplay` | Link video **CÓ logo** (app không dùng) |
| `data.size` / `hd_size` | Dung lượng file (bytes) |
| `data.cover` | Ảnh bìa video |
| `data.author` | Thông tin tác giả |

→ App luôn chọn `hdplay` nếu có, không thì dùng `play` ⇒ **video tải về không có logo TikTok**.

### Endpoint 2: Danh sách video thịnh hành (trending feed)

```
GET https://tikwm.com/api/feed/list?count=10&region=VN
```

| Tham số | Bắt buộc | Ý nghĩa |
|---------|----------|---------|
| `count` | ❌ | Số video muốn lấy (mặc định app lấy 10) |
| `region` | ✅ | Mã quốc gia lọc video: `VN`, `US`, `PK`... **Bắt buộc** — thiếu là API trả lỗi `region empty` |

**Response mẫu** (đã rút gọn — chú ý `data` là **mảng trực tiếp**):

```json
{
  "code": 0,
  "msg": "success",
  "data": [
    {
      "video_id": "7702698223114128671",
      "title": "#fyp #xuhuong",
      "cover": "https://....webp",
      "duration": 5,
      "play": "https://....mp4",
      "size": 54901,
      "author": { "unique_id": "ncfddfgd", "nickname": "masti" }
    }
  ]
}
```

> ⚠️ Khác biệt với endpoint 1: video ở feed dùng trường `video_id` (không phải `id`). Model trong app khai báo `@SerializedName(value = "id", alternate = ["video_id"])` để map được cả hai.

## Code gọi API (Retrofit)

**Interface** — `app/src/main/java/com/example/tt/network/TikTokApi.kt`:

```kotlin
interface TikTokApi {
    @GET("api/")
    suspend fun getVideo(@Query("url") url: String, @Query("hd") hd: Int = 1): TikTokApiResponse

    @GET("api/feed/list")
    suspend fun getTrending(@Query("count") count: Int = 10, @Query("region") region: String = "VN"): FeedResponse
}
```

**Client + parse** — `ApiClient.kt` (Retrofit + Gson + OkHttp, timeout 15s/30s) và `TikTokModels.kt` (data class map JSON).

**Thử API trực tiếp bằng trình duyệt:**

- Phân tích link: `https://tikwm.com/api/?url=https://www.tiktok.com/@tieuthat2000/video/7676427551027350802&hd=1`
- Thịnh hành VN: `https://tikwm.com/api/feed/list?count=10&region=VN`

---

# 🔥 KỸ THUẬT 3 — FIREBASE BACKEND (tự tạo trên Firebase Console)

App dùng **Firebase Auth** (đăng ký/đăng nhập) + **Cloud Firestore** (CSDL backend lưu profile người dùng).

## 🔗 Các link web Firebase

| Mục đích | Link |
|----------|------|
| **Firebase Console** (quản lý backend) | https://console.firebase.google.com |
| Tạo project mới | https://console.firebase.google.com (nút **Create a project**) |
| **Authentication** (quản lý người dùng) | https://console.firebase.google.com → project → **Build → Authentication** |
| **Firestore Database** (CSDL backend) | https://console.firebase.google.com → project → **Build → Firestore Database** |
| Cài đặt project / tải `google-services.json` | **Project settings (⚙️) → Your apps → Android app** |
| Tài liệu Auth | https://firebase.google.com/docs/auth/android/password-auth |
| Tài liệu Firestore | https://firebase.google.com/docs/firestore |
| Giá (gói Spark miễn phí) | https://firebase.google.com/pricing |

## 🛠 Hướng dẫn bật Firebase backend thật (5 phút)

File `app/google-services.json` trong repo là **bản demo** để app build được ngay không cần cấu hình (khi đó app tự chạy chế độ offline SQLite). Muốn dùng backend Firebase thật:

**Bước 1 — Tạo project:**
Vào https://console.firebase.google.com → **Create a project** → đặt tên (vd `tt-app`) → không cần bật Google Analytics.

**Bước 2 — Bật đăng nhập Email/Password:**
Console → **Build → Authentication → Get started → Sign-in method → Email/Password → Enable → Save**

🔗 Link trực tiếp: `https://console.firebase.google.com/project/{PROJECT_ID}/authentication/providers`

**Bước 3 — Tạo Android app + tải file cấu hình:**
Console → **⚙️ Project settings → Your apps → Android icon (🤖)** → nhập:
- Package name: **`com.example.tt`** (phải chính xác — trùng `applicationId` trong `app/build.gradle.kts`)
- App nickname: `TT Downloader` (tùy ý)

→ Nhấn **Register app → Download `google-services.json`**

**Bước 4 — Chép file vào project:**
Chép đè file vừa tải vào `app/google-services.json` (thay bản demo) → build/run lại app.

**Bước 5 — Tạo Firestore Database + Rules:**
Console → **Build → Firestore Database → Create database** → chọn vùng (vd `asia-southeast1`) → chế độ **production** → tab **Rules** dán:

🔗 Link trực tiếp: `https://console.firebase.google.com/project/{PROJECT_ID}/firestore/rules`

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

*(Rule này chỉ cho phép mỗi người dùng đọc/ghi document của chính mình.)*

## 📊 Cấu trúc CSDL trên Firestore (backend)

Collection **`users`** — mỗi người dùng 1 document, khóa là `uid` Firebase Auth:

```
users (collection)
 └── {uid} (document)
      ├── name: "Nguyễn Văn A"          # tên hiển thị
      ├── email: "a@gmail.com"          # email đăng nhập
      ├── avatar: ""                     # link ảnh đại diện
      ├── downloadCount: 5              # số video đã tải (tăng tự động)
      └── updatedAt: 1794362400000      # timestamp lần cập nhật cuối
```

## 📞 App gọi backend như thế nào

**Auth** — `firebase/AuthRepository.kt`:

| Chức năng | Phương thức Firebase | Gọi trong |
|-----------|----------------------|------------|
| Đăng ký | `createUserWithEmailAndPassword()` + `updateProfile(displayName)` | `LoginActivity` |
| Đăng nhập | `signInWithEmailAndPassword()` | `LoginActivity` |
| Đăng xuất | `signOut()` | `ProfileActivity` |

**Firestore** — `firebase/ProfileRepository.kt`:

| Chức năng | Phương thức Firestore | Gọi trong |
|-----------|----------------------|------------|
| Đọc profile | `collection("users").document(uid).get()` | `ProfileActivity` |
| Lưu/sửa tên | `collection("users").document(uid).set(map)` | `ProfileActivity` (nút lưu) |
| Đếm số video đã tải | `runTransaction { +1 downloadCount }` | `MainActivity` (sau mỗi lần tải xong) |

**Chiến lược dự phòng (offline-first):** nếu Firebase chưa cấu hình thật hoặc mất mạng, `AuthRepository` tự chuyển sang **đăng ký/đăng nhập offline bằng SQLite** (bảng `users` + `sessions`) nên app luôn chạy được.

## 👤 Xem người dùng đã đăng ký trên backend

Console → **Build → Authentication → Users tab** → thấy danh sách email đã đăng ký.

🔗 Link trực tiếp: `https://console.firebase.google.com/project/{PROJECT_ID}/authentication/users`

---

# 💾 KỸ THUẬT 1 — SQLITE (CSDL local trên máy)

File: `app/src/main/java/com/example/tt/data/DatabaseHelper.kt` — 3 bảng:

**Bảng `downloads`** (lịch sử tải video):

| Cột | Kiểu | Ý nghĩa |
|-----|------|---------|
| `id` | INTEGER PK | tự tăng |
| `video_id` | TEXT UNIQUE | mã video TikTok (có index tìm nhanh) |
| `title`, `author`, `cover_url` | TEXT | thông tin hiển thị |
| `source_url`, `download_url` | TEXT | link gốc + link tải không logo |
| `quality` | TEXT | `HD` / `NO_WATERMARK` |
| `file_path`, `file_size` | TEXT/INTEGER | file đã lưu + dung lượng |
| `favorite` | INTEGER | 0/1 yêu thích |
| `created_at` | INTEGER | timestamp |

**Bảng `users`** (cache profile Firebase + tài khoản offline dự phòng): `uid` PK, `email` UNIQUE, `name`, `avatar`, `password_hash` (SHA-256 salt email), `is_offline`, `last_login`.

**Bảng `sessions`** (phiên đăng nhập hiện tại): `uid` PK, `logged_in`.

Thao tác CRUD đầy đủ: `insertWithOnConflict` (CREATE), `query` (READ), `update` (UPDATE), `delete` (DELETE), `beginTransaction` (transaction), `CREATE UNIQUE INDEX` (index), `rawQuery COUNT` (aggregate).

---

# 🚀 Cách chạy app

## Cách 1 — Tải APK có sẵn (nhanh nhất)

Vào https://github.com/trinhxuantanh2so4-jpg/tt/releases → tải `app-debug.apk` → mở file trên điện thoại → cho phép "cài từ nguồn không xác định" → mở app.

## Cách 2 — Build bằng Android Studio

1. **Android Studio** → **Open** → chọn thư mục project
2. Chờ Gradle Sync xong
3. Nhấn **Run ▶** (cần máy ảo hoặc điện thoại bật USB debugging)
4. Hoặc build APK: **Build → Build APK(s)** → file tại `app/build/outputs/apk/debug/app-debug.apk`

## Cách 3 — Build tự động trên GitHub Actions

Mỗi lần push lên nhánh `main`, workflow `.github/workflows/build-apk.yml` tự:
1. Checkout code → cài JDK 17 → chấp nhận Android SDK license
2. Chạy `gradle assembleDebug`
3. Upload `app-debug.apk` lên **Release v1.0.0**: https://github.com/trinhxuantanh2so4-jpg/tt/releases

Xem lịch sử build: https://github.com/trinhxuantanh2so4-jpg/tt/actions

---

# 📁 Cấu trúc code

```
app/src/main/java/com/example/tt/
├── TTApp.kt                        # Application
├── LoginActivity.kt                # Đăng nhập/đăng ký (Kỹ thuật 3)
├── MainActivity.kt                 # Tải video + trending (Kỹ thuật 2)
├── HistoryActivity.kt              # Lịch sử SQLite (Kỹ thuật 1)
├── ProfileActivity.kt              # Profile Firestore (Kỹ thuật 3)
├── data/                           # KỸ THUẬT 1: SQLITE
│   ├── DatabaseHelper.kt           #   3 bảng + index + transaction + CRUD
│   ├── DownloadRecord.kt           #   model lịch sử
│   └── UserRepository.kt           #   users cache + offline auth + session
├── network/                        # KỸ THUẬT 2: API
│   ├── TikTokApi.kt                #   Retrofit interface (tikwm.com)
│   ├── ApiClient.kt                #   singleton + chuẩn hoá kết quả
│   └── TikTokModels.kt             #   response models (Gson)
├── firebase/                       # KỸ THUẬT 3: BACKEND
│   ├── AuthRepository.kt           #   Auth: register/login/logout + fallback offline
│   └── ProfileRepository.kt        #   Firestore: đọc/ghi profile + transaction counter
├── download/VideoDownloader.kt     # OkHttp tải file + MediaStore (có % tiến độ)
└── ui/                             # RecyclerView adapters
    ├── HistoryAdapter.kt
    └── TrendingAdapter.kt
```

# ⚙️ Công nghệ

Kotlin · ViewBinding · Coroutines · Retrofit + OkHttp + Gson · Coil (tải ảnh) · Material 3 · SQLiteOpenHelper · Firebase Auth + Cloud Firestore

- **minSdk:** 26 (Android 8.0+) · **targetSdk:** 35 · AGP 8.7.3 · Kotlin 2.0.21 · Gradle 8.10.2
- Video lưu vào: Android 10+ → `Download/TikTok` (qua MediaStore, không cần quyền) · Android 8–9 → thư mục riêng của app
- Quyền sử dụng: `INTERNET`, `ACCESS_NETWORK_STATE`, `WRITE_EXTERNAL_STORAGE` (chỉ Android ≤ 9)

# 📝 Ghi chú pháp lý

App dùng API công khai tikwm.com với mục đích học tập/demo. Vui lòng tôn trọng bản quyền video và điều khoản sử dụng của TikTok.
