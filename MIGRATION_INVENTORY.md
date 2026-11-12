# Kiểm kê school-fee (Jmix 3) để port sang React + Spring (dth-framework)

Nguồn: `D:\DTH\main\school-fee` — quét tự động từ code. Package gốc `com.company.schoolfee`.
Quy mô: 34 entity/enum, ~20 service, 36 view XML, 21 file test, 26 changelog Liquibase.

## 0. Phát hiện quan trọng (ảnh hưởng thiết kế)

1. **Multi-tenant ở mọi bảng**: mọi entity nghiệp vụ đều có cột `tenant` (String), dùng add-on `jmix-multitenancy`. dth-framework **chưa có multi-tenant** → phải quyết định cách làm trước khi tạo schema.
2. **Phụ thuộc add-on Jmix cần thay thế**: quartz (lịch gửi thông báo), email queue, message templates, datatools, localfs (avatar).
3. **Logic lớn tập trung ở 3 service**: `FeeCollectionBatchService` (1381 dòng), `FeeNotificationService` (597), `StudentManagementService` (584), `FeeCollectionPaymentService` (665).
4. **Chưa có test cho** `FeeRegistrationService`, `FeePaymentRequestService`, `FeeCollectionStudentRegistrationService`, `PriorityCategoryService`.
5. **Dữ liệu tiền lưu BigDecimal** (số tiền phải đối chiếu hai bản).
6. `application.properties` có datasource trỏ DB nội bộ và thông tin tài khoản ngân hàng test — không copy nguyên sang project mới.

## 1. Entity (tenant = có cột `tenant`)

### Năm học / cấu trúc trường — `entity/academic`
| Entity | Bảng | Trường chính | Quan hệ |
|---|---|---|---|
| AcademicYear | ACADEMIC_YEAR | name, startDate, endDate | Composition → periods |
| AcademicPeriod | ACADEMIC_PERIOD | name, startDate, endDate | N-1 AcademicYear |
| ClassGroup (khối) | CLASS_GROUP | name | N-1 AcademicYear; Composition → classes |
| SchoolClass (lớp) | SCHOOL_CLASS | name | N-1 ClassGroup |

### Danh mục — `entity/catalog`
| Entity | Bảng | Trường chính | Quan hệ |
|---|---|---|---|
| MeasurementUnit | MEASUREMENT_UNIT | name | — |
| TrainingSystem | TRAINING_SYSTEM | name | — |
| PriorityCategory (đối tượng ưu tiên) | PRIORITY_CATEGORY | name, discountType, discountValue, applicationPeriod, fromMonth, toMonth | N-1 AcademicYear |
| enum | DiscountType, PriorityApplicationPeriod | | |

### Học sinh — `entity/student`
| Entity | Bảng | Trường chính | Quan hệ |
|---|---|---|---|
| Student | STUDENT | studentCode, fullName, dateOfBirth, gender, personalId, passportNumber, statisticCode, admissionYear, healthInsuranceCode, địa chỉ (province/district/ward/area/houseNumber/hamlet/address), guardianName/Phone/Email, bank (name/holder/number), needsInvoice | — |
| StudentAcademicEnrollment (học sinh theo năm) | STUDENT_ACADEMIC_ENROLLMENT | transferInDate, note | N-1 Student, AcademicYear, SchoolClass, TrainingSystem, sourceEnrollment (tự tham chiếu) |
| enum | Gender | | |

### Khoản thu — `entity/fee`
| Entity | Bảng | Trường chính | Quan hệ |
|---|---|---|---|
| FeeItem | FEE_ITEM | name, scope, priceMode, basePrice, vatRate, nature, collectionCycle, cờ: discountEligible/mandatory/serviceFee/refundable/invoiceEnabled/receiptEnabled/internalCollection, invoiceRate, active | N-1 AcademicYear, ClassGroup, SchoolClass, MeasurementUnit; Composition → prices, collectionSchedules |
| FeeItemPrice | FEE_ITEM_PRICE | areaName, amount | N-1 FeeItem, ClassGroup |
| FeeCollectionSchedule | FEE_COLLECTION_SCHEDULE | sequence, collectionMonth | N-1 FeeItem, AcademicPeriod |
| StudentFeeRegistration | STUDENT_FEE_REGISTRATION | source | N-1 Enrollment, FeeItem |

### Đợt thu & công nợ
| Entity | Bảng | Trường chính | Quan hệ |
|---|---|---|---|
| FeeCollectionBatch | FEE_COLLECTION_BATCH | name, collectionMonth, scope, feeItemCount, studentCount, receivableAmount, collectedAmount, status | N-1 AcademicYear, ClassGroup, SchoolClass |
| FeeCollectionBatchItem | FEE_COLLECTION_BATCH_ITEM | feeItemName, unitName, quantity, sequence | N-1 Batch, FeeItem |
| FeeCollectionBatchStudent | FEE_COLLECTION_BATCH_STUDENT | — | N-1 Batch, Enrollment, ClassGroup, SchoolClass |
| StudentFeeReceivable | STUDENT_FEE_RECEIVABLE | unitPrice, quantity, receivableAmount, collectedAmount | N-1 Batch, BatchItem, BatchStudent |

### Thu tiền
| Entity | Bảng | Trường chính | Quan hệ |
|---|---|---|---|
| FeePaymentRequest | FEE_PAYMENT_REQUEST | orderCode, requestedAmount, paidAmount, currencyCode, provider, thông tin ngân hàng + VietQR, deadline, status, audit | N-1 BatchStudent; 1-N NotificationRecipient |
| FeeReceipt (phiếu thu) | FEE_RECEIPT | receiptNumber, source, status, amount, cashReceived, changeAmount, payerName, note, collectedAt/By, bankTransactionRef, cancelReason/At/By, audit | N-1 Batch, BatchStudent, PaymentRequest; Composition → lines |
| FeeReceiptLine | FEE_RECEIPT_LINE | feeItemName, amount, sequence | N-1 Receipt, Receivable |

### Thông báo
| Entity | Bảng | Trường chính | Quan hệ |
|---|---|---|---|
| FeeNotification | FEE_NOTIFICATION | subject, content, deadline, sendAt, collectionPeriod, status, bộ đếm (recipient/queued/sent/failed/missingEmail), snapshot template/logo/bank/VietQR, errorMessage, audit | N-1 Batch |
| FeeNotificationRecipient | FEE_NOTIFICATION_RECIPIENT | snapshot học sinh/phụ huynh, email, feeLinesJson, totalAmount, status, deliveryClaimed, sendingMessageId, queuedAt, sentAt, errorMessage | N-1 Notification, BatchStudent, PaymentRequest |

### Enum nghiệp vụ (`entity/fee`)
FeeScope, FeePriceMode, FeeNature, FeeCollectionCycle, FeeCollectionBatchScope, FeeCollectionBatchStatus, FeeReceiptSource, FeeReceiptStatus, FeePaymentProvider, FeePaymentRequestStatus, FeeNotificationStatus, FeeNotificationRecipientStatus, FeeRegistrationApplicationScope, FeeRegistrationSource.

### Hệ thống
| Entity | Ghi chú |
|---|---|
| User (USER_) | username, password, họ tên, email, phone, joinDate, position, active, timeZoneId, passwordChangeRequired, avatar (FileRef), **tenant** → map sang module user của dth-framework, phải thêm trường tenant |
| Lịch sử đăng nhập | `AuthenticationEventListener` + view `login-history-list` |

## 2. Service nghiệp vụ (logic cần port)

| Nhóm | Service | Dòng | Việc chính |
|---|---|---|---|
| Năm học | AcademicYearContext, AcademicYearSelectionService, SchoolOrganizationService | 88/38/133 | Chọn năm học đang làm việc (session), validate năm học/kỳ |
| Danh mục | PriorityCategoryService, listener PriorityCategorySavingListener | 36/35 | Validate đối tượng ưu tiên |
| Khoản thu | FeeItemFormService, FeeItemValidationService, FeeRegistrationService | 138/80/211 | Lịch thu, validate, đăng ký khoản thu tự chọn theo học sinh |
| Học sinh | StudentManagementService, SchoolStructureTreeService | 584/303 | Xếp lớp, import từ năm trước, cây Khối→Lớp |
| **Đợt thu** | **FeeCollectionBatchService** | **1381** | Tạo đợt, tính công nợ, giá theo khối/khu vực, tổng quan theo tháng |
| Thu tiền | FeeCollectionPaymentService, FeeCollectionStudentRegistrationService, FeePaymentRequestService | 665/267/179 | Thu tiền mặt, sinh QR, hủy phiếu, yêu cầu thanh toán |
| Thông báo | FeeNotificationService, PreviewService, EmailRenderer, DispatchService, DispatchBatchService, Scheduler, DispatchJob | 597/201/309/35/125/85/29 | Soạn thông báo, render email, lên lịch Quartz, gửi theo lô |
| Tiện ích | VietnameseMoneyWords, VietQrUrls | 93/43 | Đọc số tiền thành chữ, tạo URL VietQR |
| Tenant | TenantDisplayNameService | 56 | Tên trường theo tenant |
| Cấu hình | FeePaymentProperties, FeeNotificationProperties | | Thông tin ngân hàng, VietQR, batch size, time zone |

DTO không lưu DB (nguồn cho API/response của frontend): FeeCollectionBatchItemRow, FeeCollectionOverviewRow, FeeCollectionRegistrationOptionRow, FeeCollectionStudentRow, FeeScopeOption, SchoolStructureNode.

## 3. Màn hình (36 view) → màn React cần dựng

| Nhóm menu | View | Loại |
|---|---|---|
| Năm học | academic-year list/detail, academic-period detail | CRUD (kỳ nằm trong năm học) |
| Cấu trúc trường | class-group list/detail, school-class detail | CRUD dạng cây Khối→Lớp |
| Học sinh | student list, student-academic-enrollment detail, student-previous-year-import | Danh sách + form + **import từ năm trước** |
| Khoản thu | fee-item list/detail, fee-item-price detail, fee-registration list | CRUD + bảng giá + đăng ký |
| Ưu tiên | priority-category list/detail | CRUD |
| Danh mục | measurement-unit, training-system (list/detail) | CRUD đơn giản |
| Đợt thu | fee-collection-batch list/detail, batch-overview, batch-update-options (dialog), student-registration (dialog) | **Màn phức tạp** |
| Thu tiền | fee-collection-payment-view | **Màn phức tạp nhất** (thu, QR, in/hủy phiếu) |
| Thông báo | notification-compose, recipient-list, recipient-selection | Soạn + chọn người nhận + trạng thái |
| Quản trị | user list/detail, user-profile, login-history | Đã có phần lớn ở dth-framework |
| Vỏ | login, main-view (menu, chọn năm học, tenant) | Layout |

## 4. Phân quyền hiện tại (resource role Jmix)

| Role | Phạm vi |
|---|---|
| `system-full-access` | Toàn quyền entity, view, menu, specific |
| `school-catalog-manager` | ALL trên danh mục, học sinh, khoản thu, đợt thu, thu tiền, thông báo; READ trên các DTO; một số entity (FeeItem, FeeCollectionBatch, FeeReceipt, FeeReceiptLine, StudentFeeRegistration) có action giới hạn riêng; view policy + menu policy |
| `ui-minimal` | Chỉ MainView và LoginView |

→ Chuyển sang Permissions (resource:action) + Data scope của dth-framework. Cần xem chi tiết action của 5 entity có giới hạn riêng khi map.

## 5. Add-on Jmix và hướng thay thế

| Jmix | Dùng cho | Hướng ở Spring/dth-framework |
|---|---|---|
| multitenancy | Cách ly dữ liệu theo trường | **Cần quyết định**: `@TenantId` Hibernate hoặc Data scope theo `tenant`; framework chưa có |
| quartz (jdbc, clustered) | Lên lịch gửi thông báo | Spring + Quartz (giữ lại) hoặc `@Scheduled` nếu không cần cluster |
| email + email queue | Gửi mail thông báo, cron 10s | spring-boot-starter-mail + bảng hàng đợi (hoặc dùng luôn FeeNotificationRecipient làm hàng đợi) |
| messagetemplates | Template email | Thymeleaf/FreeMarker hoặc lưu template trong DB |
| localfs | Avatar | Lưu file local/S3 |
| datatools | Công cụ admin Jmix | Bỏ |
| security + flowui | Role, UI | Thay bằng cơ chế của dth-framework |

## 6. Test hiện có (21 file) — dùng làm bộ đối chiếu hành vi
- Học thuật: AcademicYearContextTest, SchoolOrganizationServiceTest, PriorityCategoryAcademicYearTest
- Khoản thu: FeeItemFormServiceTest, FeeItemPersistenceTest
- **Thu tiền** (8): FeeCollectionBatchServiceTest, FeeCollectionBatchPostgresPerformanceTest, FeeCollectionPaymentServiceTest, FeeNotification{Service,Dispatch,EmailRenderer,Scheduler}Test, VietnameseMoneyWordsTest
- Học sinh: StudentManagementServiceTest, SchoolStructureTreeServiceTest
- Tenant: TenantAutoAssignmentTest
- UI: FeeCollectionBatchOverviewUiTest, SchoolCatalogUiTest, UserTest, UserUiTest

→ Dịch các test service sang JUnit của project mới trước khi port logic.

## 7. Thứ tự port đề xuất
1. **Quyết định multi-tenant** (chặn mọi thứ phía sau).
2. Schema: dựng entity + migration theo mục 1 (Liquibase 26 changelog hiện có là tham chiếu, không copy máy móc).
3. Danh mục + năm học + cấu trúc trường → học sinh/enrollment (kèm import năm trước).
4. Khoản thu (giá, lịch thu, đăng ký).
5. Đợt thu + công nợ (`FeeCollectionBatchService`) — port cùng test.
6. Thu tiền + phiếu thu + VietQR.
7. Thông báo + email + Quartz.
8. Phân quyền: map role mục 4 sang Permissions + Data scope.
9. Frontend theo thứ tự backend hoàn thành; design tokens/theme làm một lần từ đầu.
