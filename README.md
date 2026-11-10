# TT Downloader 📱

App Android tải video **TikTok không logo** (no watermark), kèm **đăng nhập bằng Firebase**.

Tập trung 3 kỹ thuật:

| # | Kỹ thuật | Áp dụng trong app |
|---|---|---|
| 1 | **SQLite** | Lịch sử tải video, cache profile, phiên đăng nhập, tài khoản offline dự phòng — CRUD đầy đủ |
| 2 | **Gọi API mạng** | Retrofit gọi API miễn phí tikwm.com để phân tích link TikTok + danh sách video thịnh hành |
| 3 | **Firebase backend** | Đăng ký / Đăng nhập (Auth) + lưu & đọc profile (Firestore) |

## Màn hình

1. **LoginActivity** — Đăng nhập / Đăng ký (Firebase Auth, tự dự phòng offline bằng SQLite nếu chưa cấu hình Firebase)
2. **MainActivity** — Dán link TikTok → phân tích → tải video không logo; hiển thị video thịnh hành từ API
3. **HistoryActivity** — Lịch sử tải từ SQLite: yêu thích (UPDATE), xóa 1 dòng / xóa hết (DELETE)
4. **ProfileActivity** — Hồ sơ từ Firestore: xem tên, email, số video đã tải; đổi tên (ghi lên backend); đăng xuất

## Cách chạy

1. Mở **Android Studio** → **Open** → chọn thư mục này
2. Chờ Gradle Sync
3. Nhấn **Run ▶** — build APK: **Build → Build APK(s)** → `app/build/outputs/apk/debug/app-debug.apk`

## ⚠️ Bật Firebase (bắt buộc nếu muốn đăng nhập online)

File `app/google-services.json` hiện là **bản demo** để app build được. Muốn dùng Firebase thật:

1. Vào [console.firebase.google.com](https://console.firebase.google.com) → **Create project** (vd: tt-app)
2. **Build → Authentication → Sign-in method → bật Email/Password**
3. **Project settings → Your apps → Android** → nhập package `com.example.tt` → **Download google-services.json**
4. **Chép đè** vào `app/google-services.json` → Run lại app

Firestore rules (Build → Firestore Database → Rules):

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

## Cấu trúc code

```
app/src/main/java/com/example/tt/
├── TTApp.kt                        # Application
├── LoginActivity.kt                # Đăng nhập/đăng ký (Kỹ thuật 3)
├── MainActivity.kt                 # Tải video (Kỹ thuật 2)
├── HistoryActivity.kt              # Lịch sử SQLite (Kỹ thuật 1)
├── ProfileActivity.kt              # Profile Firestore (Kỹ thuật 3)
├── data/                           # KỸ THUẬT 1: SQLITE
│   ├── DatabaseHelper.kt           #   3 bảng + index + transaction + CRUD
│   ├── DownloadRecord.kt           #   model lịch sử
│   └── UserRepository.kt           #   users cache + offline auth + session
├── network/                        # KỸ THUẬT 2: API
│   ├── TikTokApi.kt                #   Retrofit interface (tikwm.com)
│   ├── ApiClient.kt                #   singleton + chuẩn hoá kết quả
│   └── TikTokModels.kt             #   response models
├── firebase/                       # KỸ THUẬT 3: BACKEND
│   ├── AuthRepository.kt          #   Auth: register/login/logout
│   └── ProfileRepository.kt       #   Firestore: profile + counter
├── download/VideoDownloader.kt     # OkHttp tải file + MediaStore
└── ui/                             # RecyclerView adapters
```

## Công nghệ

Kotlin · ViewBinding · Coroutines · Retrofit + OkHttp + Gson · Coil · Material 3 · SQLiteOpenHelper · Firebase Auth + Firestore

- **minSdk:** 26 (Android 8.0+) · **targetSdk:** 35
- Video lưu: Android 10+ → Download/TikTok (MediaStore, không cần quyền) · Android 8–9 → thư mục riêng của app
