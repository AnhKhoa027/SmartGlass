SMARTGLASS – SMART GLASSES SUPPORT SYSTEM FOR VISUALLY IMPAIRED
==============================================================

1. GIỚI THIỆU
SmartGlass là một ứng dụng Android thuộc đề tài Nghiên cứu Khoa học,
nhằm hỗ trợ người khiếm thị thông qua kính thông minh.

Hệ thống kết hợp:
- Nhận dạng vật thể từ camera
- Dữ liệu cảm biến (khoảng cách, chuyển động)
- Điều khiển và phản hồi bằng giọng nói

Mục tiêu là tạo ra mô tả ngữ cảnh (context-aware description),
thay vì chỉ đọc tên vật thể đơn lẻ.

----------

2. YÊU CẦU HỆ THỐNG
Phần cứng:
- Kính thông minh (Smart Glasses) có:
  + Camera
  + Distance & Motion Sensor
  + Kết nối USB

Phần mềm:
- Điện thoại Android (Android 10+)
- Cho cách build từ source:
  + Android Studio Hedgehog hoặc mới hơn
  + JDK 17
  + Android SDK 34+

⚠️ YÊU CẦU QUAN TRỌNG
--------------------
Ứng dụng SmartGlass CHỈ hoạt động ĐẦY ĐỦ khi kết nối với kính thông minh
(Smart Glasses Hardware) có gắn camera và cảm biến.

Nếu không có kính:
- Ứng dụng có thể chạy
- Nhưng các chức năng nhận dạng, cảm biến và mô tả ngữ cảnh sẽ KHÔNG hoạt động

----------
  
3. CÁCH CÀI ĐẶT & CHẠY CHƯƠNG TRÌNH
4. 
CÁCH 1 (KHUYẾN NGHỊ): CÀI ĐẶT BẰNG FILE APK

Bước 1:
- Tải file APK từ Google Drive: https://drive.google.com/file/d/1IiAIWlCiq-MiJKsHk-TgDc9xh4x2rG9f/view?usp=sharing

Bước 2:
- Chép file APK vào điện thoại Android

Bước 3:
- Mở file APK và cho phép:
  + Install unknown apps (cài đặt ứng dụng không rõ nguồn)

Bước 4:
- Cài đặt ứng dụng
- Kết nối kính thông minh với điện thoại
- Mở ứng dụng SmartGlass và sử dụng

⚠️ Lưu ý:
- Cách này KHÔNG cần Android Studio
- Phù hợp cho giảng viên, hội đồng hoặc người dùng thử nghiệm


CÁCH 2: BUILD TỪ SOURCE CODE
----------------------------
Bước 1: Clone project từ GitHub
- Mở terminal hoặc Git Bash
- Chạy lệnh:
  git clone https://github.com/AnhKhoa027/SmartGlass.git

Bước 2: Mở project bằng Android Studio
- Open Android Studio
- Chọn "Open an existing project"
- Trỏ tới thư mục SmartGlass

Bước 3: Đồng bộ Gradle
- Đợi Android Studio tải dependency
- Kiểm tra Kotlin version và Compose Compiler

Bước 4: Kết nối thiết bị
- Kết nối điện thoại Android qua USB (USB Debugging)
  HOẶC
- Chạy bằng Android Emulator (chỉ để test giao diện)

Bước 5: Run ứng dụng
- Nhấn Run ▶
- Kết nối kính thông minh để sử dụng đầy đủ chức năng

----------


4. CÁC TOOLS & CÔNG NGHỆ SỬ DỤNG
Ngôn ngữ lập trình:
- Kotlin (Kotlin 2.0+)

Nền tảng:
- Android SDK
- Android Jetpack

UI:
- Jetpack Compose
- Material 3

Xử lý giọng nói:
- Text To Speech (TTS)
- Speech To Text (STT)
- Wake Word (Porcupine)

Xử lý camera & thị giác máy tính:
- UVC Camera
- Object Detection (YOLO , Seft-trained Model and Google Vision)

Cảm biến:
- Distance Sensor
- Motion Sensor

Kiến trúc & kỹ thuật:
- MVVM
- Dependency Injection
- Buffer dữ liệu cảm biến
- Context Inference Layer
- Multi-source Data Fusion

Công cụ hỗ trợ:
- Git & GitHub
- Gradle
- Logcat

----------

5. CẤU TRÚC TỔNG QUAN PROJECT
- app/
  - UI (Compose Screens)
  - ObjectDetection
  - DetectResponse
  - Sensor & Buffer
  - Voice (TTS/STT)
  - SettingAction
- gradle/
- AndroidManifest.xml
- build.gradle.kts

----------

6. GHI CHÚ
- Ứng dụng ưu tiên chạy trên thiết bị thật để test camera và sensor
- Một số tính năng có thể không hoạt động đầy đủ trên Emulator (Giả lập)
- Project phục vụ mục đích học tập và nghiên cứu khoa học

----------

7. TÁC GIẢ
- Nhóm: C1SE.16
- Ngành: Kỹ thuật Phần mềm
- Khoa: Đào tạo quốc tế
- Trường: Đại học Duy Tân
- Đề tài: SmartGlass – Kính thông minh hỗ trợ người khiếm thị
