# 🎨 KẾ HOẠCH NÂNG CẤP GIAO DIỆN & TRẢI NGHIỆM NGƯỜI DÙNG (UI/UX)

> Tài liệu này là kế hoạch triển khai cho app **TT Downloader**. Mục tiêu: giao diện hiện đại (Material 3), thao tác nhanh hơn, ít lỗi thao tác hơn, dùng được cả chế độ sáng/tối và font lớn.

---

## 1. Hiện trạng & vấn đề

| # | Vấn đề hiện tại | Ảnh hưởng tới người dùng |
|---|-----------------|--------------------------|
| 1 | `Theme.TT` kế thừa `DayNight` nhưng **mọi màu đều hardcode tối** (`@color/tt_black`, `#1E1E2A`) | Bật chế độ sáng của máy → chữ/nền lệch, không đọc được |
| 2 | Toàn bộ chữ nằm rải trong layout (`android:text="..."`) | Không dịch được, khó sửa hàng loạt |
| 3 | Không có `dimens.xml` / `styles.xml` | Khoảng cách, bo góc, cỡ nút mỗi màn một kiểu |
| 4 | Main dùng `ScrollView` chứa `RecyclerView` (`nestedScrollingEnabled=false`) | Danh sách thịnh hành tải hết 1 lần, giật khi nhiều item |
| 5 | Không có trạng thái rỗng / lỗi / đang tải rõ ràng (chỉ Toast) | Người dùng bấm mà không biết app đang làm gì |
| 6 | Icon dùng emoji (🕘 🗑 🤍 ⬇) và drawable hệ thống cũ | Lệch kiểu, không đổi màu theo theme, TalkBack đọc sai |
| 7 | Không có điều hướng cố định (mỗi màn phải back) | Muốn xem lịch sử/hồ sơ phải về Main trước |
| 8 | Lịch sử: xoá hết **không hỏi xác nhận**, không tìm kiếm, không mở lại video | Dễ mất dữ liệu, khó tìm video cũ |
| 9 | Card video chưa có ảnh bo góc, thời lượng, % tải rõ ràng | Nhìn “thô”, thiếu thông tin quyết định |
| 10 | Login/Profile chưa validate inline, progress đặt rời bên dưới | Khó hiểu khi đăng nhập chậm/lỗi |

---

## 2. Mục tiêu UX (đo được)

- **≤ 2 chạm** để bắt đầu tải: mở app → (dán link tự động) → Tải.
- Mọi thao tác > 0.3s đều có phản hồi thị giác (skeleton, progress, nút disable).
- Không thao tác phá huỷ nào không có xác nhận/hoàn tác.
- Hiển thị đúng ở: màn 320dp, font scale 1.3, sáng & tối, TalkBack.
- Danh sách 60fps: ảnh lazy-load, `DiffUtil`, không lồng scroll.

---

## 3. Kế hoạch theo giai đoạn

### Phase 0 — Design system (nền tảng, làm trước)
- `res/values/colors.xml`: bảng màu đầy đủ
  `tt_cyan #25F4EE`, `tt_red #FE2C55`, `surface #12121A`, `surface_variant #1E1E2A`,
  `surface_elevated #24243A`, `text_primary #FFFFFF`, `text_secondary #B3FFFFFF`, `divider #1FFFFFFF`.
- `res/values-night/colors.xml`: bản sáng (nền trắng, chữ đen) — app tôn trọng chế độ máy.
- `values/themes.xml`: map `colorPrimary/colorSecondary/colorSurface/colorOnSurface`, `android:statusBarColor`, `windowLightStatusBar`; thêm `ThemeOverlay.TT.Toolbar`.
- `values/dimens.xml`: `space_4/8/12/16/24/32`, `radius_12/16/24`, `touch_target 48dp`, `cover_ratio`.
- `values/styles.xml`: `Widget.TT.Button.Primary` (nền `tt_red`), `Widget.TT.Button.Tonal`, `Widget.TT.Card`, `TextAppearance.TT.Title/Caption`.
- `values/strings.xml`: chuyển **toàn bộ** text hardcode sang string resource (kèm `values-en/strings.xml` nếu cần).
- **Kết quả:** mọi màn dùng chung token, đổi theme 1 chỗ.

### Phase 1 — Khung điều hướng
- Thay header tự chế bằng `MaterialToolbar` (tiêu đề + hành động).
- Thêm `BottomNavigationView` 4 mục: **Tải · Thịnh hành · Lịch sử · Hồ sơ**.
- *Phương án an toàn cho đồ án:* giữ 4 `Activity` hiện có, mỗi Activity gắn BottomNavigation (không refactor sang Fragment) → ít rủi ro logic, vẫn đạt UX.
- `SplashScreen` API (AndroidX) khi mở app.

### Phase 2 — Màn “Tải video” (trọng tâm)
- Ô nhập link: `TextInputLayout` + `endIcon` **Dán** (đọc clipboard), nút **Phân tích** to, rõ.
- Nhận `Intent.ACTION_SEND` (`text/plain`): chia sẻ link từ TikTok → mở thẳng app và tự điền.
- Gợi ý link trong clipboard (chip “Dán link vừa copy”).
- Card kết quả:
  - Ảnh bìa tỉ lệ 16:9, **bo góc**, overlay **thời lượng** góc phải, chip **HD · No logo**.
  - Tiêu đề 2 dòng, tác giả, dung lượng (định dạng KB/MB), ngày.
  - Dòng **“Lưu tại: Download/TikTok ›”** — bấm là mở trình chọn tệp (thay checkbox hiện tại, trực quan hơn).
  - Progress bar + **% + tốc độ (MB/s)** + nút **Huỷ**.
  - Nút tải full-width, có hiệu ứng ripple + disable khi đang tải.
- Trạng thái: `empty` (hướng dẫn 3 bước), `loading` (skeleton), `error` (nút **Thử lại**).

### Phase 3 — “Thịnh hành”
- Chuyển sang **grid 2 cột** (`GridLayoutManager`) ảnh 9:16, overlay gradient + duration.
- `SwipeRefreshLayout` kéo để làm mới, shimmer placeholder khi tải ảnh.
- Trạng thái lỗi có nút **Thử lại**; empty state riêng.

### Phase 4 — “Lịch sử”
- Thanh **tìm kiếm** theo tiêu đề/tác giả (`FilterChip`: Tất cả / Yêu thích).
- **Swipe-to-delete** + `Snackbar` **Hoàn tác** (thay vì xoá ngay).
- **Xoá hết** phải có `MaterialAlertDialog` xác nhận.
- Bấm item → mở video bằng `ACTION_VIEW` + `FileProvider` (nếu còn file), hoặc mở link gốc.
- Hiển thị **thời gian tương đối** (“2 giờ trước”) và trạng thái file còn/đã mất.

### Phase 5 — “Hồ sơ” & “Đăng nhập”
- Profile: avatar tròn có viền, thẻ thống kê (số video · tổng dung lượng), đổi tên inline có nút lưu + Snackbar, đăng xuất có xác nhận.
- Login: logo + slogan, validate inline (`TextInputLayout.error`), progress nằm **trong nút**, chuyển Đăng nhập/Đăng ký có animation, ghi nhớ email lần trước.

### Phase 6 — Polish & kiểm thử
- Chuyển cảnh: `MaterialContainerTransform` (list → chi tiết), fade/slide nhẹ; haptic khi tải xong.
- Accessibility: `contentDescription` cho mọi icon, touch target ≥ 48dp, contrast ≥ 4.5:1.
- Kiểm thử tay: xoay màn hình, dark/light, font 1.3, TalkBack, màn 320dp, mất mạng.
- Cập nhật README + ảnh chụp màn hình mới.

---

## 4. Ưu tiên (impact / công sức)

| Ưu tiên | Hạng mục | Vì sao |
|---------|----------|--------|
| **P1** | Phase 0 (token, strings, theme) | Làm nền cho tất cả, sửa 1 lần dùng mãi |
| **P1** | Phase 2 (card video + chọn nơi lưu trực quan + trạng thái) | Người dùng ở màn này 90% thời gian |
| **P2** | Phase 4 (lịch sử: tìm kiếm, xoá an toàn, mở video) | Tránh mất dữ liệu, tăng giá trị dùng lại |
| **P2** | Phase 1 (bottom nav) | Điều hướng rõ, ít bước hơn |
| **P3** | Phase 3 (grid thịnh hành) | Đẹp hơn, nhưng không chặn luồng chính |
| **P3** | Phase 5 (login/profile) | Dùng 1 lần/phiên |
| **P4** | Phase 6 (animation, splash) | Cảm giác “xịn”, không ảnh hưởng chức năng |

---

## 5. File sẽ tác động

| File | Thay đổi |
|------|----------|
| `res/values/{colors,themes,dimens,styles,strings}.xml`, `res/values-night/colors.xml` | **Mới/sửa** toàn bộ design token |
| `res/layout/activity_main.xml` | Toolbar, ô link có nút dán, card video mới, trạng thái rỗng/lỗi |
| `res/layout/item_trending.xml` → `item_trending_grid.xml` | Ảnh 9:16, overlay, grid |
| `res/layout/activity_history.xml`, `item_history.xml` | Search, filter chip, swipe, empty state |
| `res/layout/activity_profile.xml`, `activity_login.xml` | Avatar tròn, card thống kê, validate inline |
| `MainActivity.kt`, `HistoryActivity.kt`, `ProfileActivity.kt`, `LoginActivity.kt` | Gắn view mới, xử lý dán/share link, xác nhận xoá, mở video, xử lý trạng thái |
| `ui/TrendingAdapter.kt`, `ui/HistoryAdapter.kt` | `DiffUtil`, grid, shimmer, xử lý sự kiện |
| `AndroidManifest.xml` | `intent-filter` ACTION_SEND, `FileProvider` |
| `README.md` | Ảnh + mô tả giao diện mới |

---

## 6. Rủi ro & cách giảm thiểu

1. **Refactor sang Fragment** dễ vỡ logic Auth/SQLite → *giữ Activity*, chỉ thay UI (Phase 1 phương án an toàn).
2. **Đổi theme DayNight** có thể làm mất tương phản ở màn tối → test từng màn ở cả 2 chế độ, giữ palette riêng cho `values-night`.
3. **Swipe-to-delete + hoàn tác** cần `DiffUtil` đúng → dùng `ListAdapter` thay adapter thường.
4. **Số file thay đổi lớn** → chia PR/commit theo phase, mỗi phase build APK được ngay.
5. **Không build local được** (máy thiếu JDK 17/SDK 35) → dựa vào GitHub Actions, mỗi phase push 1 lần.

---

## 7. Thứ tự thực hiện đề xuất

```
Phase 0  →  Phase 2  →  Phase 4  →  Phase 1  →  Phase 3  →  Phase 5  →  Phase 6
(design)    (tải video)  (lịch sử)   (nav)       (trending)  (login/profile) (polish)
```

Mỗi phase: code → build CI → xem APK → chốt → sang phase tiếp theo.
