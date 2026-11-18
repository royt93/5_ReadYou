# FRONT-02r (Test máy thật: chặn trùng nguồn Bluesky và nâng cấp DID khi đồng bộ, trên Room thật của Tecno)

**Date:** 2026-11-18  
**Branch:** `dev` (không push)  
**Scope:** chỉ thêm test, **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới. Chỉ có ba tệp androidTest: một hàm mới trong `SubscribeViewModelFactory.kt` (hàm cũ giữ hành vi), `BlueskyGuardRig.kt`, `BlueskyGuardsDeviceTest.kt`. Đây là test, không phải tính năng người dùng.  
**Spec / plan:** `docs/superpowers/specs/2026-11-18-front-02r-bluesky-guards-device-test-design.md`, `docs/superpowers/plans/2026-11-18-front-02r-bluesky-guards-device-test.md`.

## Điều test chứng minh

Trên **Tecno KJ7 (`115333744A005844`, Android 14)**, với **Room thật** và DAO thật, `SubscribeViewModel` thật (lấy qua `ViewModelProvider` của một `ComponentActivity`, không `MainActivity`, không quảng cáo) và `LocalRssSv` thật, nói chuyện với một **máy chủ Bluesky giả** trên `127.0.0.1`. Test **gọi `inputLink` và `search()` trực tiếp**, không gõ qua giao diện. Mọi nguồn thử có id bắt đầu `front02r_probe_` trong một nhóm tạm.

| Việc | Test | Điều chứng minh |
|---|---|---|
| 02i | `sameAccountAsApiSource…withoutAskingTheServer` | gõ lại đúng tên đã lưu: "Đã đăng ký", không có xem trước, **0 lần** hỏi máy chủ |
| 02i | `sameAccountAsOldRssSource…` | nguồn RSS cũ của cùng tài khoản: báo trùng |
| 02n | `typedAsDid_whenSavedAsHandle…` | gõ DID, nguồn lưu handle: báo trùng sau khi API nêu tên thứ hai (1 yêu cầu hồ sơ, 1 yêu cầu bài) |
| 02n | `typedAsHandle_whenSavedAsDid…` | ngược lại |
| 02o | `newSource_isStoredWithTheDid…` | nguồn mới lưu `url = bsky:<tên gõ>`, `sourceUrl = bsky:<DID>`; xem trước không ghi dòng nào |
| 02o | `renamedHandle_isCaughtThroughTheStoredDid` | **ca mục tiêu**: nguồn đã lưu DID, gõ handle mới của cùng DID: báo trùng |
| 02o | `aDifferentAccount_isNotBlocked` | tài khoản khác không bị chặn nhầm |
| 02q | `oldSource_isUpgradedToItsDid…` | nguồn cũ `sourceUrl = url = handle`: đồng bộ thành công thì `sourceUrl` thành DID, `url` **không đổi**, bài vẫn được ghi |
| 02q | `aFailedSync_leavesTheSourceUrlAlone` | máy chủ trả 500: `sourceUrl` không đổi, không bài |
| 02q | `aSyncThatFindsNoOwnPosts…` | hồ sơ hợp lệ nhưng chỉ có bài đăng lại: đồng bộ báo lỗi, `sourceUrl` không đổi |
| 02q | `aSecondSync_doesNotRewriteAnUpgradedSource` | lần hai khi đã là DID: không đổi |
| sạch | `afterCleanUp_nothingOfTheProbeIsLeftAnywhere…` | không sót dòng thử, và số nguồn và bài **thật** của Tecno đúng như trước |

## Nói thẳng về phạm vi

- **Không chứng minh:** 02p (menu sửa địa chỉ, owner đã loại), mạng thật của Bluesky, nút "Đăng ký" và hộp thoại thật (test đọc state ViewModel và dữ liệu Room, không đọc màn hình), `MainActivity`/`HomeEntry`, `SyncWorker` thật, nguồn đã đổi handle từ trước 02q (02q không nâng cấp được), nguồn nhập từ OPML.
- Chạy trên **một máy** (Tecno KJ7, Android 14); không so với Pixel.
- **Ghi tạm vào Room thật** (212 bài, 1 nguồn thật `BBC News`), chỉ bằng dòng `front02r_probe_`; `cleanUp` xóa nhóm (cascade) và kiểm `probeRowCount() == 0`; vòng nào cũng khôi phục byte-exact cuối vòng.
- **Chặn mạng ngoài 127.0.0.1:** `search()` rơi về RSS và newsletter khi API không dùng được; rig dùng một client từ chối mọi host khác `127.0.0.1` và đếm; mọi test ở nhánh API khẳng định `blockedRequests() == 0`. Không test nào đi vào đường RSS, nên con số này luôn 0 và **chưa được thử bằng một yêu cầu thật bị chặn**.
- Lint **không quét** `androidTest`, nên lint không kiểm các tệp test mới; chỉ biên dịch được kiểm.

## Kiểm chứng

- **Tecno KJ7, 41 lượt (một lượt thử + 20 + 20): 41/41 `OK (12 tests)`.** Sau mỗi lượt: `user_rotation=0`, `accelerometer_rotation=1`, không lần nào phải đặt lại; khôi phục byte-exact 6 tệp; băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` trùng; đã gỡ `com.mckimquyen.reader.test`; không dòng `front02r_probe_` nào sót (kiểm lại trên bản sao: Room trên máy **giống hệt** bản sao lưu từng byte). Cổng chỉ đọc qua trong lệnh riêng trước mỗi vòng.
- **Mutation chạy trên máy (đổi tạm mã production, build, cài, chạy, khôi phục, `cmp`): 8/8 bị bắt, mỗi cái làm ĐÚNG bộ test dự định đỏ** (so sánh tự động tập test đỏ thật với tập dự định, cả 8 khớp từng tên):

| Đột biến | Tệp | Test đỏ |
|---|---|---|
| `r01` bỏ kiểm trùng dạng API và RSS cũ | `SubscribeViewModel.kt` | `sameAccountAsApiSource…`, `sameAccountAsOldRssSource…` |
| `r02` bỏ riêng kiểm RSS cũ | `SubscribeViewModel.kt` | `sameAccountAsOldRssSource…` |
| `r03` bỏ kiểm tên thứ hai (`otherNames`) | `SubscribeViewModel.kt` | `typedAsDid…`, `typedAsHandle…` |
| `r04` bỏ kiểm theo DID đã lưu | `SubscribeViewModel.kt` | `renamedHandle…` |
| `r05` lưu tên gõ vào `sourceUrl` thay vì DID | `SubscribeViewModel.kt` | `newSource_isStoredWithTheDid…` |
| `r06` bỏ nâng cấp DID khi đồng bộ | `LocalRssSv.kt` | `oldSource_isUpgradedToItsDid…` |
| `r07` nâng cấp trước khi kiểm bài rỗng | `LocalRssSv.kt` | `aSyncThatFindsNoOwnPosts…` |
| `r08` `isFeedSourceExist` đọc cột `url` | `AbstractRssRepository.kt` | `renamedHandle…` |

  Hai lượt `base` (đầu và cuối) xanh `OK (12 tests)`. Sau batch: ba tệp production `identical`, `git diff app/src/main` rỗng, `RESTORE OK`, băm DataStore trùng, xoay `1 0`.
- **JVM full suite `--rerun-tasks`:** **3.797 test, 0 lỗi, 0 bỏ qua** (485 lớp); `assembleDevDebug assembleDevDebugAndroidTest` thành công. Chỉ thay đổi ở `androidTest` nên số JVM không do mã mới.
- Điều chỉnh đã làm khi thực thi (xem spec): không tái dùng rig 02h nguyên vẹn; thêm client chặn mạng ngoài; bài giả phải đúng tác giả; thêm test thứ 12 để `r07` nhắm đúng.

## Giới hạn

- Test đọc state ViewModel, không phải màn hình; **không** chứng minh chữ "Đã đăng ký" hiện ra trên màn hình.
- 41 lượt xanh chưa chứng minh không có chập chờn hiếm (như 02m, 1 đến 1,5% ở bước gõ chữ): test này **không gõ chữ**, nên nguồn đó đã biết không áp dụng, nhưng không loại trừ nguồn khác.
- `r02` và `r01` chồng nhau ở test `sameAccountAsOldRssSource…`; không phải lỗi, chỉ nghĩa là hai đột biến không phân biệt được bằng test này.

## Commits trên `dev`

`25f3348b` spec · `35adaa6f` plan · (commit test và tài liệu: xem `git log`)
