# 🎯 Cải thiện phụ đề dịch & độ khớp thuyết minh

> Lập ngày 2026-11-21 sau QA: “câu sửa còn: có”, nhưng “tạo phụ đề muốn dịch luôn sang tiếng Việt”,
> “giọng có nhưng không khớp, nghe chưa hay”. Người dùng chọn làm **cả hai**: cải thiện local + đánh giá VoiceStudio.

## Phần A — Cải thiện pipeline hiện tại

| # | Vấn đề | Nguyên nhân | Cách sửa | Trạng thái |
|---|--------|-------------|----------|------------|
| U1 | Tạo phụ đề có lúc text trùng “Lời gốc”, không báo lỗi | `Translator.translateAll()` bắt lỗi rồi trả `null`, `CueBuilder` giữ câu gốc nên người dùng tưởng đã dịch | Báo lỗi rõ khi không tải được model ML Kit / không dịch được câu nào; hiện số câu đã dịch trong tiến độ | ✅ (`f7d3485`, run `44849096548`) |
| U2 | Thuyết minh không khớp câu, câu sau chồng câu trước | TTS đọc với 1 tốc độ cố định; clip dài hơn cửa sổ của câu (mốc `startMs` → câu kế tiếp) | Ước lượng tốc độ theo độ dài câu/cửa sổ, giới hạn 0.85–1.45×; nếu vẫn dài thì ép nhịp tối đa 1.45× trong `NarrationTimeline.fitToWindow()` | ✅ (`f7d3485`, run `44849096548`, unit test JVM pass) |
| U3 | Giọng nghe chưa hay | `pickVoice()` ưu tiên giọng offline đầu tiên, bỏ qua giọng Google/Neural chất lượng cao | Ưu tiên tên chứa `neural/studio/google/natural/premium/hd`; vẫn giữ theo giới tính Nữ/Nam | ✅ (`f7d3485`, run `44849096548`) |
| U4 | QA khó kiểm tra “có dịch chưa / có khớp chưa” | Form cũ chỉ hỏi PASS/FAIL chung | Thêm form §9 trong `QA_CHECKLIST.md` | ✅ |

### Giới hạn của phần A
- Vẫn dùng TTS hệ thống: chất lượng phụ thuộc gói giọng có trên máy. Giả lập thường chỉ có giọng cơ bản.
- Độ khớp được cải thiện theo **mốc câu**, không phải khớp khẩu hình từng chữ (lip-sync). Muốn lip-sync thật cần model dubbing chuyên dụng.
- ML Kit cần Google Play services; nếu thiếu thì báo lỗi rõ, không crash (đúng yêu cầu dự án).

## Phần B — Đánh giá VoiceStudio (`debpalash/VoiceStudio`)

Nguồn: README chính thức + API repo, kiểm tra ngày 2026-11-21.

| Tiêu chí | Kết quả | Ảnh hưởng với app này |
|----------|---------|------------------------|
| Bản chất | App AI voice desktop (Electron + Python), có **bản Android preview** (Android 10+, offline sau khi tải model) | Là **app độc lập**, không phải SDK/AAR → không thể `implementation` vào Gradle |
| Chức năng | Voice cloning, voice design, **video dubbing có căn thời gian theo gốc**, transcribe 646 ngôn ngữ | Đúng nhu cầu “hay hơn/khớp hơn”, nhưng chạy ở app khác |
| License code | **AGPL-3.0** (dùng thương mại được nhưng phải tuân thủ copyleft; có bản Pro thương mại) | Nếu gộp code vào app hoặc vận hành dịch vụ mạng, phải rà soát nghĩa vụ AGPL cho toàn bộ project |
| License model | Weight mặc định OmniVoice là **CC-BY-NC (phi thương mại)**; từng model có license riêng | **Không dùng được cho app phát hành thương mại** nếu không đổi model hoặc mua license |
| API | Local API `:3900` chủ yếu **ASR/transcription** (HTTP OpenAI-compatible, WebSocket, MCP); dubbing/TTS nằm ở UI app; Cloud API “coming soon” (chưa có) | Không có endpoint public ổn định để app gọi TTS/dubbing ngay bây giờ |
| Kích thước | Repo nguồn ~218 MB; model tải riêng (thường nhiều GB), cần CPU/GPU | APK hiện tại ~85 MB; không thể nhúng model. Phải tải lúc chạy hoặc dùng server riêng |
| Hạ tầng | Docker CPU/CUDA/ROCm, macOS/Windows/Linux, Android preview | Muốn dùng từ app mình cần chạy thêm app/server khác trên cùng máy hoặc LAN — phức tạp hoá luồng tải video |
| Kết luận | **Không tích hợp ngay.** Dùng thử như công cụ riêng để so sánh chất lượng thì được; trước khi phát hành thương mại phải giải quyết AGPL + CC-BY-NC | Ưu tiên tiếp tục cải thiện pipeline hiện tại (Phần A). Nếu sau này cần chất lượng cao, cân nhắc Cloud API chính thức hoặc model có license thương mại rõ ràng |

### Điều kiện để xem lại phương án VoiceStudio
1. Có API cloud/dubbing public và ổn định (hiện chưa có).
2. License model cho phép thương mại, hoặc dự án chấp nhận AGPL cho toàn bộ mã nguồn.
3. Có đoạn benchmark thực tế: cùng 1 video, so sánh độ trễ câu và chất lượng giọng giữa TTS hệ thống và VoiceStudio.

## Trạng thái tổng
- Phần A: đã sửa code, chờ CI + QA trên giả lập (có giọng vi-VN nhưng chất lượng cơ bản).
- Phần B: chỉ đánh giá, **không thay đổi dependency/license của dự án**.
