# 📋 Task list QA — gửi người dùng (2026-11-21)

> Mục đích: bạn chạy test trên máy/giả lập, điền trực tiếp vào các ô `...` rồi gửi lại nguyên nội dung này cho mình.
> **Không dán API key hay link chứa token vào báo cáo.** Nếu có lỗi, copy log ở màn “Chi tiết lỗi gần nhất”.

- APK mới nhất (cố định, luôn là bản mới): https://github.com/trinhxuantanh2so4-jpg/tt/releases/download/v1.0.0/app-debug.apk
- Đã cài APK này chưa: có / không
- Thiết bị: giả lập / máy thật · Android ... · Google Play services: có / không
- Nguồn video dùng để test: Douyin / TikTok · link mẫu (có thể che token): ...
- Ngôn ngữ nguồn đã chọn: Tiếng Trung / Tiếng Anh

---

## A. Gemini AI (tính năng mới)

| # | Việc cần làm | Kết quả (điền) |
|---|---|---|
| A1 | Chưa nhập key → mở **Nguồn tải video (nâng cao)** → xem mục “Cấu hình Gemini AI” | Thấy ô key + nút “Kiểm tra Gemini Key”: có / không |
| A2 | Vào màn **Tạo / sửa phụ đề** khi CHƯA có key | Switch “Tối ưu văn phong bằng Gemini AI” bị disabled và có chữ “(Chưa nhập API Key)”: có / không |
| A3 | Nhập key Gemini thật → bấm “Kiểm tra Gemini Key” | Báo “Gemini Key hợp lệ!”: có / không · nếu lỗi, chép nguyên message tiếng Việt: ... |
| A4 | Bật switch Gemini → bấm “Bắt đầu xử lý” / “Tạo phụ đề” | Có hiện “Gemini đang trau chuốt phụ đề…”: có / không |
| A5 | So sánh 1 câu trong `.srt` lúc **TẮT** Gemini và lúc **BẬT** Gemini | Câu trước: ... · câu sau: ... · nghe tự nhiên hơn: có / không / chưa kịp so sánh |
| A6 | Để key sai rồi tạo phụ đề | App có crash/không tạo được file: có / không · text có bị rỗng/sai số câu: có / không |
| A7 | Thoát app → mở lại màn cấu hình | Key còn giữ: có / không |

## B. Dịch tiếng Việt & xem/sửa `.srt`

| # | Việc cần làm | Kết quả (điền) |
|---|---|---|
| B1 | Chọn đúng nguồn → Bắt đầu xử lý | Có hiện “Đã dịch x/y câu sang tiếng Việt”: có / không · ghi rõ x/y = ... |
| B2 | Sau khi xong, thoát rồi mở lại màn phụ đề | Thông báo “Đã dịch x/y…” còn giữ: có / không |
| B3 | Nếu text vẫn tiếng Trung/Anh | Chép NGUYÊN message lỗi tiếng Việt hiện ra: ... (không được chỉ ghi “bị rè”) |
| B4 | Bấm “Xem / sửa nội dung .srt” | Thấy đúng nội dung tiếng Việt: có / không |
| B5 | Sửa 1 dòng trong hộp thoại → Lưu → mở lại | Câu sửa còn: có / không · số câu không đổi: có / không |
| B6 | Thử xoá một dòng giờ trong hộp thoại rồi Lưu | Có báo lỗi tiếng Việt, không crash: có / không |

## C. Player & thuyết minh

| # | Việc cần làm | Kết quả (điền) |
|---|---|---|
| C1 | Lịch sử → chọn “Có phụ đề tiếng Việt” | Phụ đề hiện ngay dưới video: có / không |
| C2 | Đổi Gốc ↔ Vietsub ↔ Thuyết minh | Không crash: có / không · đúng bản: có / không |
| C3 | Tạo thuyết minh (giữ âm gốc 20%) | Câu đúng mốc: có / không · còn chồng: có / không · lời gốc lấn giọng đọc: có / không |
| C4 | Nghe kỹ chất lượng giọng | Còn rè/cao bất thường: có / không · tốc độ quá nhanh: có / không |
| C5 | Bấm spinner giọng | Thấy đủ 4 lựa chọn (Nữ/Nam × 1.0x/1.25x): có / không |
| C6 | Chọn “Nam · 1.25x” + “Chỉ thuyết minh” → tạo lại → thoát/mở lại | Giọng giữ: có / không · âm nền giữ: có / không · giọng đúng lựa chọn: có / không |
| C7 | Bấm “Tạo thuyết minh” khi máy KHÔNG có giọng vi-VN | Có hướng dẫn cài giọng: có / không · app tự thoát: có / không |

## D. Các ca còn thiếu trong §8 (trước đó bạn còn để trống)

| # | Ca test | Kết quả (điền) |
|---|---|---|
| D1 | **Ca 2** — badge Vietsub / Thuyết minh trên Lịch sử hiện đúng | Vietsub: có/không · Thuyết minh: có/không |
| D2 | **Ca 5** — Lịch sử → tạo thuyết minh khi đã có giọng vi-VN | Tạo được MP4: có/không · nghe có phụ đề chồng: có/không |
| D3 | **Ca 6** — Player đổi bản | Đã trả lời ở C1/C2, nếu khác thì ghi thêm: ... |
| D4 | **Ca 7** — Xoá bản xử lý | Sạch file SRT + thuyết minh: có/không · video gốc vẫn mở: có/không |
| D5 | **Ca 8** — Debug APK tự mở màn “Chi tiết lỗi gần nhất” | Có log lỗi không: có/không · nếu có, bấm “Sao chép log” rồi dán toàn bộ ở dưới |

## E. Log lỗi (chỉ dán nếu có lỗi)

```text
(dán toàn bộ nội dung “Sao chép log” vào đây; nhớ che API key)
...
```

---

## Cách gửi lại cho mình

Chỉ cần copy file này, điền vào các ô `...` / chọn có-không, rồi paste vào chat. Với mỗi dòng “không”, ghi thêm 1 câu bạn đang thấy gì — mình sẽ sửa tiếp ngay.
