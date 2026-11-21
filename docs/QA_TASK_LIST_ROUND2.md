# QA checklist vòng 2 — Gemini + OOM (2026-11-21)

> Điền trực tiếp vào các ô `...` rồi gửi lại nguyên nội dung file này cho mình.
> APK: https://github.com/trinhxuantanh2so4-jpg/tt/releases/download/v1.0.0/app-debug.apk
> Bản kiểm tra: commit `0edac1f` · CI `44892468642` (xanh).

**⚠️ Không dán API key vào báo cáo.** Key đã lộ ở các lần trước phải revoke/rotate và tự nhập key mới trong app. Nếu app báo lỗi Gemini, chỉ chép phần message `Lỗi Gemini: HTTP ...` (app đã tự che key bằng `***`).

---

## 0. Thông tin môi trường

- [ ] Đã cài lại APK mới nhất: có / không
- Thiết bị: giả lập / máy thật · Android ... · RAM ... GB · Google Play services: có / không
- Video dùng để test: nguồn Douyin / TikTok · độ dài ... phút · có âm thanh: có / không
- Ngôn ngữ nguồn đã chọn: Tiếng Trung / Tiếng Anh

## 1. OOM / bộ nhớ (ưu tiên cao nhất)

- [ ] **M1** — Dùng chính video đang bị OOM, bấm tạo phụ đề → kết quả: thành công / vẫn hết bộ nhớ / lỗi khác: ...
- [ ] **M2** — Nếu M1 vẫn lỗi: thử video **dưới 1 phút**, **tắt** switch Gemini → kết quả: ...
- [ ] **M3** — Video dưới 1 phút, **bật** Gemini → kết quả: ... (để phân biệt OOM do Gemini hay do giải mã âm thanh)
- [ ] **M4** — Video dài 3–5 phút, **tắt** Gemini → kết quả: ...
- [ ] **M5** — Khi hết nhớ, thông báo có hiện tiếng Việt “Thiết bị hết bộ nhớ khi xử lý…” không: có / không
- [ ] **M6** — Sau khi OOM, app có tự thoát không: có / không · có mở màn “Chi tiết lỗi gần nhất” không: có / không
- [ ] **M7** — Bấm tạo phụ đề 2 lần liên tiếp → lần sau có chậm hơn / OOM sớm hơn không: có / không

## 2. Gemini key

- [ ] **G1** — Vào “Nguồn tải video (nâng cao)” → bấm “Xoá cấu hình” → mở màn phụ đề: switch bị disable + hiện “(Chưa nhập API Key)”: có / không
- [ ] **G2** — Nhập key MỚI → bấm “Kiểm tra Gemini Key” → báo “Gemini Key hợp lệ!”: có / không · nếu lỗi, chép nguyên: `Lỗi Gemini: HTTP ...`: ...
- [ ] **G3** — Bật switch Gemini → tạo phụ đề → có hiện “Gemini đang trau chuốt phụ đề…”: có / không
- [ ] **G4** — Để key sai rồi tạo phụ đề → app crash: có / không · file `.srt` vẫn tạo và đủ số câu: có / không

## 3. Regression tối thiểu (chạy sau khi M1 thành công)

- [ ] **R1** — Trong lúc xử lý có hiện “Đã dịch x/y câu sang tiếng Việt”: có / không · x/y = ...
- [ ] **R2** — Mở “Xem / sửa nội dung .srt” → thấy tiếng Việt: có / không · sửa 1 dòng → Lưu → mở lại còn: có / không
- [ ] **R3** — Lịch sử → mở video có phụ đề → phụ đề hiện dưới video: có / không
- [ ] **R4** — Sau khi tải xong, thuyết minh **không tự chạy** (chỉ chạy khi bấm “Tạo thuyết minh”): có / không
- [ ] **R5** — Nghe bản thuyết minh (nếu tạo được): âm gốc còn rè/cao: có / không · câu bị chồng: có / không

## 4. Log lỗi (chỉ dán nếu có)

```text
(bấm “Sao chép log” ở màn “Chi tiết lỗi gần nhất” rồi dán vào đây; nhớ che key)
...
```

---

## Cách gửi lại

Copy file này, điền vào các ô `...` / khoanh có-không, rồi paste vào chat. Với mỗi dòng “không”, ghi thêm 1 câu mô tả điều bạn thấy — mình sẽ sửa tiếp ngay.
