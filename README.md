# TT App 📱

Ứng dụng Android mẫu (APK) — khởi đầu cho dự án của bạn.

## Yêu cầu môi trường

- [Android Studio](https://developer.android.com/studio) (Ladybug trở lên)
- JDK 17 (Android Studio đã kèm sẵn)

## Cách chạy & build APK

1. Mở **Android Studio** → **Open** → chọn thư mục dự án này
2. Chờ Gradle Sync hoàn tất (tự động tải dependencies lần đầu)
3. Chạy trên máy ảo / máy thật: nhấn nút **Run ▶**
4. Build file APK: menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
   - File APK nằm tại: `app/build/outputs/apk/debug/app-debug.apk`

## Cấu trúc dự án

```
tt/
├── app/
│   ├── build.gradle.kts        # Cấu hình module app
│   └── src/main/
│       ├── AndroidManifest.xml # Khai báo Activity, icon, tên app
│       ├── java/com/example/tt/MainActivity.kt
│       └── res/                # Layout, chuỗi, màu, icon
├── build.gradle.kts            # Cấu hình Gradle root
├── settings.gradle.kts
└── gradle/libs.versions.toml   # Version catalog các thư viện
```

## Công nghệ sử dụng

- **Ngôn ngữ:** Kotlin
- **minSdk:** 26 (Android 8.0+) · **targetSdk:** 35
- **UI:** View + XML (ConstraintLayout, Material 3)
