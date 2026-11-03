# EXC-05a — Manual Encrypted LAN Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chuyển thủ công một gói feed và trạng thái đọc/sao giữa hai bản RSS Cat Hub trên LAN, mã hoá đầu-cuối, xem trước rồi mới apply.

**Architecture:** Gói JSON theo allowlist được validate hoàn toàn trước khi ghi; PBKDF2-HMAC-SHA256 + AES-GCM bọc gói bằng envelope có version/salt/nonce/tag. Gói nhận tạo pure sync plan; một Room transaction chỉ thêm feed thiếu và merge trạng thái cho article đã có. UI Send/Receive dùng phiên HTTP LAN tái sử dụng lõi EXC-13, nhưng secret và ciphertext ở hai kênh riêng.

**Tech Stack:** Kotlin/JCA (`PBKDF2WithHmacSHA256`, `AES/GCM/NoPadding`), Room v20, `MirrorServer` hiện có, Hilt, coroutines, Compose Material3 1.0.1, JUnit4, Robolectric, MockK.

## Global Constraints

- Chỉ nhánh `dev`; không worktree; không commit `.idea/*`; không push; không nhắc/gợi ý PR.
- Đây là **EXC-05a**, một chiều và thủ công; **không** đánh dấu EXC-05 gốc hoàn tất, không xoá todo gốc. Cần hai Android thật để hoàn tất acceptance criteria NSD/tự động/2 chiều.
- Tecno KJ7 `115333744A005844` בלבד (owner: “tiếp tục với tecno đi”); không chạm A50s `R58MA6WYRPE` hay Pixel `2B051FDH3006MU`; không `connectedDevDebugAndroidTest`; instrument bằng `adb -s 115333744A005844 shell am instrument -w -e class <Class> com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`.
- Không đổi Room schema v20, không thêm thư viện, không AI/API key/quảng cáo/IAP/tiền, không cloud/NSD/QR/foreground service. AES-GCM/PBKDF2 là JCA.
- Dữ liệu duy nhất được đóng gói: feed URL/tên/group label; `article.link`, `isUnread`, `isStarred`, `readAt`. Không account IDs, feed/article IDs, title, body/content/image, `aiSummary`, audio, notes, highlight, position, settings, DataStore, account credentials, API key.
- Gói sai bất kỳ điều kiện nào (magic/version/tag/JSON/schema/field thừa/URL/duplicate/cap) thì reject **trước mọi DB write**. Apply chạy một transaction; DB error rollback nguyên khối. Receiver chỉ áp dụng vào Local account đang chọn.
- Feed identity: URL chuẩn hoá; article identity: `(normalized feed URL, article.link exact)`; không sinh article mới, không tải mạng. Feed trùng giữ metadata receiver; feed thiếu vào default group; tên trùng thêm ` (2)`, ` (3)`…
- Merge monotonic: `isUnread = old AND incoming`; `isStarred = old OR incoming`; `readAt = min(non-null)`. Không thể lan trạng thái mark-unread/bỏ sao.
- Secret 16 byte/128 bit từ `SecureRandom`, URL-safe 32 ký tự hex, **một lần**, nhập/chuyển riêng. URL chứa token phiên riêng; không bao giờ gửi secret qua HTTP, không log/persist; cảnh báo token không thay encryption.
- Envelope version 1: ASCII `RCS1` (4B) + salt (16B) + nonce (12B) + AES-GCM ciphertext/tag; key PBKDF2-HMAC-SHA256, 310.000 iterations, 256 bit; tag 128 bit. `MAX_ENVELOPE_BYTES=8 MiB`, `MAX_FEEDS=5_000`, `MAX_ARTICLE_STATES=20_000`, URL feed ≤2.048 chars, article link ≤4.096, name ≤300. Đo KDF thật trên Tecno, không chạy Main.
- UI theo Material You: screen `BaseScaffold` + `FeedbackIconButton(ArrowBack)` và edge-to-edge; tất cả chuỗi mới đủ 38 locale (EN/VI viết tay, còn lại English), không `PluralsCandidate`, không `UnusedResources`; parity hiện hành 6/6.
- Hilt: mỗi `@HiltViewModel` đúng một `@Inject` constructor, không function-typed constructor params. I/O trên IO/Default; không giữ Activity/Context/clipboard trong ViewModel.
- Smoke: trước mọi ghi lên thiết bị sao lưu Room 3 file + `settings.preferences_pb`, nonempty/integrity check trên bản copy; khôi phục và `cmp` 4 file; **không bao giờ xoá DataStore**. `/tmp/gtw-backup.sh`, `/tmp/gtw-restore.sh`, `/tmp/exc13-tecno-lib.sh`, `/tmp/exc13-tecno-ad.sh` có thể đã bị dọn: `ls`/đọc nội dung trước khi dùng, nếu thiếu, viết lại script nhỏ cùng chức năng (adb -s serial, `run-as`, `cmp`) rồi Read lại trước khi chạy.
- Ads: chỉ bấm “Tiếp tục đến ứng dụng”/“Continue to app” lấy từ dump `uiautomator` mới; kiểm `topResumedActivity` và `awake` trước từng tap; nếu Tecno Dozing/locked, dừng và nhờ owner mở khoá. Không bấm creative.
- Test mutation-check mọi rào bảo mật/merge, và xác nhận mutant làm test đỏ; nếu không bị bắt, thêm test. Đọc lỗi từ `app/build/test-results/testDevDebugUnitTest/*.xml`. Script dài ghi ra file; dùng `printf`, không `echo =====`. Trước commit, `git status --short | grep -v .idea` không còn thay đổi chưa stage lẫn lộn; commit từng task kèm trailer `Co-Authored-By: Claude Code <noreply@anthropic.com>`.

---

## File map

- Pure: `domain/p2psync/{SyncLimits,PairingSecret,SyncEnvelopeCrypto,FeedUrlNormalizer,SyncPackage,SyncPackageValidator,LocalSyncPlanner}.kt`.
- Room: `domain/repository/{FeedDao,GroupDao,ArticleDao}.kt` thêm query transaction tối thiểu; **không** entity/schema/migration.
- Infra: `infrastructure/p2psync/{LocalSyncRepository,LocalSyncServer,LocalSyncTransport}.kt`; reuse `MirrorServer` chỉ làm HTTP/transport. Payload sync route riêng trả ciphertext, không mirror route/state/SSE plaintext.
- UI: `ui/page/p2psync/{LocalSyncUiState,LocalSyncViewModel,LocalSyncPage,SyncPackagePreview}.kt`; `HomeEntry.kt`/`RouteName.kt` thêm route màn mới. Màn mới có `BaseScaffold` + ArrowBack, edge-to-edge.
- Tests under matching `app/src/test/java/com/mckimquyen/reader/{domain/p2psync,infrastructure/p2psync,ui/page/p2psync}`; Room integration Robolectric; device test under `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/p2psync/`.
- Docs: `doc/task/done/EXC-05a_manual-lan-sync_DONE.md`; `doc/feature.md`; `doc/task/todo/05_EXCLUSIVE_KILLER_FEATURES.md` update EXC-05 to **In progress / EXC-05a partial**, keep `EXC-05_local-p2p-sync.md`.

## Interfaces

```kotlin
object SyncLimits { const val MAX_ENVELOPE_BYTES=8*1024*1024; const val MAX_FEEDS=5_000
  const val MAX_ARTICLE_STATES=20_000; const val MAX_FEED_URL_CHARS=2_048; const val MAX_ARTICLE_LINK_CHARS=4_096
  const val MAX_NAME_CHARS=300; const val SECRET_BYTES=16; const val SALT_BYTES=16; const val GCM_NONCE_BYTES=12
  const val GCM_TAG_BITS=128; const val PBKDF2_ITERATIONS=310_000; const val KEY_BITS=256 }
// Members of SyncEnvelopeCrypto: magic[0..3], version[4], salt[5..20], nonce[21..32], ciphertext+tag[33..].
// const val MAGIC_OFFSET=0, VERSION_OFFSET=4, SALT_OFFSET=5, NONCE_OFFSET=21, CIPHERTEXT_OFFSET=33, OVERHEAD_BYTES=49 (33 header + 16 tag)
data class PairingSecret(val hex:String) { companion object { fun generate(random:SecureRandom=SecureRandom()):PairingSecret } }
object SyncEnvelopeCrypto { fun encrypt(plain:ByteArray, secret:PairingSecret, random:SecureRandom=SecureRandom()):ByteArray
  fun decrypt(envelope:ByteArray, secret:PairingSecret):ByteArray }
data class SyncFeed(val url:String,val name:String,val groupName:String)
data class SyncArticleState(val feedUrl:String,val link:String,val isUnread:Boolean,val isStarred:Boolean,val readAt:Long?)
data class SyncPackage(val version:Int,val sourceId:String,val createdAt:Long,val feeds:List<SyncFeed>,val articles:List<SyncArticleState>)
sealed interface SyncPackageResult { data class Valid(val data:SyncPackage); data class Invalid(val reason:SyncPackageError) }
object SyncPackageCodec { fun encode(data:SyncPackage):ByteArray; fun decodeAndValidate(bytes:ByteArray):SyncPackageResult }
object FeedUrlNormalizer { fun normalize(url:String):String? }
data class SyncArticleUpdate(val receiverArticleId:String,val isUnread:Boolean,val isStarred:Boolean,val readAt:Long?)
data class LocalSyncPlan(val feedsToAdd:List<SyncFeedToAdd>,val duplicateFeeds:Int,val matchedArticles:Int,
  val unmatchedArticles:Int,val updates:List<SyncArticleUpdate>)
data class SyncFeedToAdd(val id:String,val name:String,val url:String,val groupId:String)  // planner output; Task 5 maps it to Feed
data class SyncArticleSnapshot(val id:String,val accountId:Int,val feedUrl:String,val link:String,val isUnread:Boolean,val isStarred:Boolean,val readAt:Long?)
object LocalSyncPlanner { fun plan(incoming:SyncPackage, receiverFeeds:List<Feed>, receiverArticles:List<SyncArticleSnapshot>,
  receiverAccountId:Int, defaultGroupId:String, newId:()->String = { UUID.randomUUID().toString() }):LocalSyncPlan }
data class LocalSyncApplyResult(val feedsAdded:Int,val duplicateFeeds:Int,val matchedArticles:Int,val unmatchedArticles:Int,val updatesApplied:Int)
class LocalSyncRepository(db:AndroidDatabase, newId:()->String) { suspend fun snapshot(localAccountId:Int):SyncPackage
  suspend fun preview(localAccountId:Int,incoming:SyncPackage):LocalSyncPlan
  suspend fun apply(localAccountId:Int,incoming:SyncPackage):LocalSyncApplyResult }
```


### Task 1: Secret pairing + mã hoá AES-GCM

**Files:** Create `domain/p2psync/{SyncLimits,PairingSecret,SyncEnvelopeCrypto}.kt`; tests `PairingSecretTest.kt`, `SyncEnvelopeCryptoTest.kt`.

**Interfaces:** không consume; produce APIs ở đầu plan.

- [ ] **Step 1: Test đỏ — secret.**

```kotlin
package com.mckimquyen.reader.domain.p2psync
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
class PairingSecretTest {
 @Test fun secretIs128BitLowerHex() { val s=PairingSecret.generate(); assertEquals(32,s.hex.length); assertTrue(s.hex.all { it in '0'..'9'||it in 'a'..'f' }) }
 @Test fun eachSessionUsesFreshRandomBytes() { assertEquals(200,(1..200).map { PairingSecret.generate().hex }.toSet().size) }
 @Test fun injectedRandomMakesSecretReproducible() { val r=object:SecureRandom(){ override fun nextBytes(b:ByteArray){b.fill(0xAB.toByte())} }; assertEquals("ab".repeat(16),PairingSecret.generate(r).hex) }
 @Test fun parserRejectsWrongLengthCaseAndNonHex() { listOf("","a","0".repeat(31),"0".repeat(33),"A".repeat(32),"g".repeat(32)).forEach { assertTrue(runCatching { PairingSecret.fromHex(it) }.isFailure) } }
}
```

- [ ] **Step 2: Test đỏ — envelope.**

```kotlin
package com.mckimquyen.reader.domain.p2psync
import org.junit.Assert.*
import org.junit.Test
class SyncEnvelopeCryptoTest {
 private val secret=PairingSecret.fromHex("0123456789abcdef".repeat(2))
 private val plain="private-state-α".toByteArray(Charsets.UTF_8)
 @Test fun encryptDecryptRoundTrip(){ assertArrayEquals(plain,SyncEnvelopeCrypto.decrypt(SyncEnvelopeCrypto.encrypt(plain,secret),secret)) }
 @Test fun saltAndNonceAreFreshEveryTime(){ val a=SyncEnvelopeCrypto.encrypt(plain,secret); val b=SyncEnvelopeCrypto.encrypt(plain,secret); assertFalse(a.contentEquals(b)); assertFalse(a.copyOfRange(SyncEnvelopeCrypto.SALT_OFFSET,SyncEnvelopeCrypto.NONCE_OFFSET).contentEquals(b.copyOfRange(SyncEnvelopeCrypto.SALT_OFFSET,SyncEnvelopeCrypto.NONCE_OFFSET))); assertFalse(a.copyOfRange(SyncEnvelopeCrypto.NONCE_OFFSET,SyncEnvelopeCrypto.CIPHERTEXT_OFFSET).contentEquals(b.copyOfRange(SyncEnvelopeCrypto.NONCE_OFFSET,SyncEnvelopeCrypto.CIPHERTEXT_OFFSET))) }
 @Test fun wrongSecretFailsAuthentication(){ val e=SyncEnvelopeCrypto.encrypt(plain,secret); val wrong=PairingSecret.fromHex("fedcba9876543210".repeat(2)); assertTrue(runCatching { SyncEnvelopeCrypto.decrypt(e,wrong) }.isFailure) }
 @Test fun changedMagicVersionSaltNonceCiphertextOrTagIsRejected(){ val e=SyncEnvelopeCrypto.encrypt(plain,secret); listOf(0,3,SyncEnvelopeCrypto.SALT_OFFSET,SyncEnvelopeCrypto.NONCE_OFFSET,SyncEnvelopeCrypto.CIPHERTEXT_OFFSET,e.lastIndex).forEach { i-> val bad=e.copyOf().also { it[i]=(it[i].toInt() xor 1).toByte() }; assertTrue("byte $i",runCatching { SyncEnvelopeCrypto.decrypt(bad,secret) }.isFailure) } }
 @Test fun malformedAndOversizeEnvelopeRejected(){ val e=SyncEnvelopeCrypto.encrypt(plain,secret); listOf(byteArrayOf(),ByteArray(SyncEnvelopeCrypto.OVERHEAD_BYTES-1),e.copyOf().also { it[0]=0 },e.copyOf().also { it[SyncEnvelopeCrypto.VERSION_OFFSET]=99.toByte() },ByteArray(SyncLimits.MAX_ENVELOPE_BYTES+1)).forEach { assertTrue(runCatching { SyncEnvelopeCrypto.decrypt(it,secret) }.isFailure) } }
 @Test fun plaintextMarkerIsNotPresentInTheEnvelope(){ val p="RssCatHubPrivateMarker-7f90".toByteArray(); val e=SyncEnvelopeCrypto.encrypt(p,secret); assertFalse(e.toString(Charsets.ISO_8859_1).contains(String(p,Charsets.ISO_8859_1))) }
 @Test fun maxAllowedCiphertextRoundTripsAndOneByteOverIsRejected(){ val max=ByteArray(SyncLimits.MAX_ENVELOPE_BYTES-SyncEnvelopeCrypto.OVERHEAD_BYTES){(it%251).toByte()}; assertArrayEquals(max,SyncEnvelopeCrypto.decrypt(SyncEnvelopeCrypto.encrypt(max,secret),secret)); assertTrue(runCatching { SyncEnvelopeCrypto.encrypt(ByteArray(SyncLimits.MAX_ENVELOPE_BYTES),secret) }.isFailure) }
}
```

- [ ] **Step 3: Red.** `/tmp/exc05a-run.sh '--tests *PairingSecretTest --tests *SyncEnvelopeCryptoTest' t1red`; expect unresolved refs.
- [ ] **Step 4: Implement.** `PairingSecret.generate` = 16 random bytes, lower-hex. `fromHex` requires exactly 32 lowercase hex. Envelope: magic `RCS1`, version byte 1, salt16, nonce12, ciphertext+GCM tag16. Key: `PBEKeySpec(secret.hex.toCharArray(),salt,310_000,256)`, `SecretKeyFactory PBKDF2WithHmacSHA256`; clear password finally. AES/GCM/NoPadding, 128-bit GCM tag, `updateAAD(magic+version)`. Encrypt rejects plaintext > `MAX_ENVELOPE_BYTES-OVERHEAD`; decrypt checks encoded length before copying/deriving, magic/version before KDF, authentication before returning bytes. Never log inputs, secret or output. Inject `SecureRandom` for deterministic secret tests; crypto test checks uniqueness rather than brittle provider-specific ciphertext bytes.
- [ ] **Step 5: Green** (4 secret + 8 envelope tests). `oversizeEnvelopeIsRejectedBeforeAnyKeyDerivation` compares oversize rejection time against one valid KDF in the same test; this ensures cap runs before expensive PBKDF2 without fixed timing assumptions. Add one instrumentation timing test later on Tecno, report actual PBKDF2 duration; do not lower rounds silently.
- [ ] **Step 6: Mutate**: fixed salt/nonce -> freshness test red; skip GCM AAD / wrong magic -> header/tamper test red; skip size check -> oversize test red. Restore and rerun.
- [ ] **Step 7: Commit** `feat(exc-05a): add pairing secrets and AES-GCM envelopes` (+ trailer).

---

### Task 2: Feed URL normalizer

**Files:** Create `domain/p2psync/FeedUrlNormalizer.kt`; test `FeedUrlNormalizerTest.kt`.

**Interfaces:** produces `normalize(raw): String?`. Do **not** reuse `BundleSubscribePlanner.normalizeUrl`: it preserves fragment/user-info, doesn't remove default ports, and returns malformed text unchanged.

- [ ] **Step 1: Test đỏ.**

```kotlin
package com.mckimquyen.reader.domain.p2psync
import org.junit.Assert.*
import org.junit.Test
class FeedUrlNormalizerTest {
 @Test fun schemeAndHostLowercaseFragmentDefaultPortAndTrailingSlashNormalize(){assertEquals("https://example.org/rss",FeedUrlNormalizer.normalize("HTTPS://EXAMPLE.ORG:443/rss/#part"));assertEquals("http://example.org/",FeedUrlNormalizer.normalize("HTTP://Example.org:80/#f"));assertEquals("https://example.org/rss",FeedUrlNormalizer.normalize("https://Example.org/rss/"))}
 @Test fun nonDefaultPortPathCaseAndQueryArePreserved(){assertEquals("https://example.org:8443/RSS?x=1",FeedUrlNormalizer.normalize("https://EXAMPLE.org:8443/RSS?x=1#top"));assertNotEquals(FeedUrlNormalizer.normalize("https://example.org/A"),FeedUrlNormalizer.normalize("https://example.org/a"))}
 @Test fun queryIsIdentityButFragmentIsNot(){assertEquals(FeedUrlNormalizer.normalize("https://e.org/f?id=1#a"),FeedUrlNormalizer.normalize("https://e.org/f?id=1#b"));assertNotEquals(FeedUrlNormalizer.normalize("https://e.org/f?id=1"),FeedUrlNormalizer.normalize("https://e.org/f?id=2"))}
 @Test fun onlyHttpAndHttpsAreAccepted(){listOf("file:///etc/passwd","ftp://example.org/rss","javascript:alert(1)","content://x/y").forEach { assertNull(it,FeedUrlNormalizer.normalize(it)) }}
 @Test fun malformedRelativeAndHostlessUrlsAreRejected(){listOf(""," ","https:","/feed.xml","example.org/rss","https:///rss","http://").forEach { assertNull(it,FeedUrlNormalizer.normalize(it)) }}
 @Test fun credentialsAreRejectedNotStripped(){assertNull(FeedUrlNormalizer.normalize("https://user:secret@example.org/rss"));assertNull(FeedUrlNormalizer.normalize("https://user@example.org/rss"))}
 @Test fun controlsWhitespaceAndOversizeInputAreRejected(){listOf("https://e.org/a\nb","https://e.org/\u0000x","https://e.org/a b").forEach { assertNull(FeedUrlNormalizer.normalize(it)) };assertNull(FeedUrlNormalizer.normalize("https://e.org/"+"a".repeat(SyncLimits.MAX_FEED_URL_CHARS)))}
 @Test fun privateLanHttpIsAllowedButNeverFetchedByNormalizer(){assertEquals("http://192.168.1.5:8080/feed",FeedUrlNormalizer.normalize("HTTP://192.168.1.5:8080/feed"))}
}
```

- [ ] **Step 2:** `/tmp/exc05a-run.sh '--tests *FeedUrlNormalizerTest' t2red`, expect unresolved.
- [ ] **Step 3: Implement** with `java.net.URI`; trim outer whitespace only; reject internal space/control, user-info, scheme outside http/https, missing host, URL over 2,048 chars; lowercase scheme/host; strip fragment; strip :80 for HTTP and :443 for HTTPS; empty path -> `/`, remove one trailing `/` except root; keep query exactly. Catch `URISyntaxException` -> null.
- [ ] **Step 4:** 8 tests green. Mutation preserve fragment -> fragment test red; accept user-info -> credentials test red.
- [ ] **Step 5: Commit** `feat(exc-05a): normalize feed URLs for sync identity` (+ trailer).

---


### Task 3: Gói đồng bộ v1 + validator strict

**Files:** Create `domain/p2psync/{SyncPackage,SyncPackageError,SyncPackageCodec}.kt`; tests `SyncPackageValidatorTest.kt`, `SyncPackageCodecTest.kt`.

**Interfaces:** consumes `FeedUrlNormalizer`, `SyncLimits`; produces data classes and `decodeAndValidate(bytes): SyncPackageResult`.

- [ ] **Step 1: Tests đỏ** (Robolectric `@Config(manifest=Config.NONE)` vì `org.json` được Android runtime cung cấp; JSON helper phải dùng `JSONObject`/`JSONArray`, test constants trên app).

```kotlin
package com.mckimquyen.reader.domain.p2psync
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class SyncPackageValidatorTest {
 private val src="a".repeat(32)
 private fun valid()="""{"version":1,"sourceId":"$src","createdAt":1,"feeds":[{"url":"https://a.test/rss","name":"News","groupName":"World"}],"articles":[{"feedUrl":"https://a.test/rss","link":"https://a.test/story","isUnread":false,"isStarred":true,"readAt":10}]}""".toByteArray()
 private fun fail(bytes:ByteArray)=assertTrue(SyncPackageCodec.decodeAndValidate(bytes) is SyncPackageResult.Invalid)
 @Test fun acceptsV1AndNormalizesFeedUrl(){val p=(SyncPackageCodec.decodeAndValidate(valid()) as SyncPackageResult.Valid).data;assertEquals(1,p.version);assertEquals("https://a.test/rss",p.feeds.single().url);assertEquals("https://a.test/rss",p.articles.single().feedUrl)}
 @Test fun rejectsWrongVersionSourceIdAndNegativeTimestamp(){val b=String(valid());listOf(b.replace("\"version\":1","\"version\":2"),b.replace(src,"bad"),b.replace("\"createdAt\":1","\"createdAt\":-1")).forEach { fail(it.toByteArray()) }}
 @Test fun rejectsExtraForbiddenAndUnknownKeys(){val b=String(valid());listOf("title","content","apiKey","password","accountId","articleId","fullContent","rawDescription","aiSummary","notes","unknown").forEach { key-> val c=b.replace("\"version\":1","\"version\":1,\"$key\":\"x\""); fail(c.toByteArray()) }}
 @Test fun rejectsMissingRequiredNullAndWrongTypes(){val b=String(valid());listOf(b.replace(",\"articles\":[",",\"oops\":["),b.replace("\"isUnread\":false","\"isUnread\":null"),b.replace("\"readAt\":10","\"readAt\":\"ten\"")).forEach { fail(it.toByteArray()) }}
 @Test fun rejectsDuplicateNormalizedFeedUrls(){val b=String(valid());val changed=b.replace("],\"articles\"", ",{\"url\":\"https://A.test/rss/#fragment\",\"name\":\"Again\",\"groupName\":\"Other\"}],\"articles\"");fail(changed.toByteArray())}
 @Test fun rejectsDuplicateFeedUrlAndLinkArticleKeys(){val b=String(valid());val changed=b.replace("}],\"articles\"", "},{\"feedUrl\":\"https://a.test/rss\",\"link\":\"https://a.test/story\",\"isUnread\":true,\"isStarred\":false,\"readAt\":null}],\"articles\"");fail(changed.toByteArray())}
 @Test fun rejectsBadUrlsUserInfoAndControls(){val b=String(valid());listOf(b.replace("https://a.test/rss","file:///etc/passwd"),b.replace("https://a.test/rss","https://u:p@a.test/rss"),b.replace("\"News\"","\"Bad\\nName\"")).forEach { fail(it.toByteArray()) }}
 @Test fun rejectsBadJsonAndPackageByteCap(){fail("{".toByteArray());fail(ByteArray(SyncLimits.MAX_ENVELOPE_BYTES+1))}
 @Test fun rejectsFeedAndArticleCountCapsBeforeMapping(){val feeds=(0..SyncLimits.MAX_FEEDS).joinToString(",") { "{\"url\":\"https://h$it.test/rss\",\"name\":\"n\",\"groupName\":\"g\"}" };fail("""{"version":1,"sourceId":"$src","createdAt":1,"feeds":[$feeds],"articles":[]}""".toByteArray())}
}

class SyncPackageCodecTest {
 @Test fun roundTripEncodesOnlyTheAllowlistedFields(){val input=SyncPackage(1,"a".repeat(32),10,listOf(SyncFeed("https://a.test/rss","A","G")),listOf(SyncArticleState("https://a.test/rss","https://a.test/x",false,true,3)));val bytes=SyncPackageCodec.encode(input);val decoded=(SyncPackageCodec.decodeAndValidate(bytes) as SyncPackageResult.Valid).data;assertEquals(input,decoded);val raw=bytes.toString(Charsets.UTF_8);listOf("apiKey","password","accountId","fullContent","rawDescription","aiSummary","DataStore").forEach { assertFalse(raw.contains(it)) }}
 @Test fun fieldOrderAndEncodingAreDeterministic(){val p=SyncPackage(1,"a".repeat(32),1,emptyList(),emptyList());assertArrayEquals(SyncPackageCodec.encode(p),SyncPackageCodec.encode(p))}
}
```

- [ ] **Step 2:** `/tmp/exc05a-run.sh '--tests *SyncPackageValidatorTest --tests *SyncPackageCodecTest' t3red`; unresolved expected.
- [ ] **Step 3: Implement.** JSON root/feed/article require exact keys; no unknowns, missing/null/wrong types. Check input byte cap before `JSONObject`; check array length before mapping. Normalize feed URLs before duplicate check; exact article link duplicate within same normalized feed reject. `sourceId` exactly 32 lower-hex, version exactly 1, timestamps nonnegative, booleans exact, names/URLs caps and control-free; feed URL/user-info via normalizer, article link only HTTP(S), host, no user-info/control, cap (do not normalize path/query). `encode` uses deterministic key insertion order, allowlist only, re-run validator on generated package; deterministic feed/article ordering sorted by `(url)` and `(feedUrl,link)`.
- [ ] **Step 4:** 11 validator + 2 codec green. Mutation: allow unknown key -> `rejectsExtra...` red; omit duplicate check -> duplicate tests red; move byte/array cap after mapping -> caps test red; serialize forbidden `accountId` -> allowlist test red.
- [ ] **Step 5: Commit** `feat(exc-05a): validate a strict allowlisted sync package` (+ trailer).

---


### Task 4: Pure `LocalSyncPlanner`

**Files:** Create `domain/p2psync/LocalSyncPlanner.kt`; test `LocalSyncPlannerTest.kt`.

**Interfaces:** consumes validated `SyncPackage`, receiver `Feed`, `SyncArticleSnapshot` (id/accountId/feedUrl/link/state only), Local account ID/default group ID; produces `LocalSyncPlan`.

- [ ] **Step 1: Tests đỏ.** Use helpers per plan types.

```kotlin
package com.mckimquyen.reader.domain.p2psync
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.*
import org.junit.Test

class LocalSyncPlannerTest {
 private var nextId = 0
 private fun feed(id:String,url:String,name:String=id,group:String="default")=Feed(id,name,url=url,groupId=group,accountId=1)
 private fun article(id:String,f:Feed,link:String,unread:Boolean,starred:Boolean,readAt:Long?=null)=SyncArticleSnapshot(id,1,f.url,link,unread,starred,readAt)
 private fun pkg(feeds:List<SyncFeed>, articles:List<SyncArticleState>)=SyncPackage(1,"a".repeat(32),1,feeds,articles)
 private val source=SyncFeed("https://EXAMPLE.org:443/rss/#fragment","Sender name","Sender group")

 @Test fun matchingFeedKeepsReceiverNameGroupAndIcon() {
  val f=feed("local-feed","https://example.org/rss",name="My local name",group="my-group")
  val receiver=f.copy(icon="local-icon")
  val p=LocalSyncPlanner.plan(pkg(listOf(source),emptyList()),listOf(receiver),emptyList(),1,"default",newId={"new"})
  assertTrue(p.feedsToAdd.isEmpty());assertEquals(1,p.duplicateFeeds)
  assertEquals("My local name",receiver.name);assertEquals("my-group",receiver.groupId);assertEquals("local-icon",receiver.icon)
 }

 @Test fun missingFeedGoesToDefaultGroupWithoutFetchingAnything() {
  val p=LocalSyncPlanner.plan(pkg(listOf(source),emptyList()),emptyList(),emptyList(),1,"default",newId={"id-1"})
  assertEquals("id-1",p.feedsToAdd.single().id);assertEquals("default",p.feedsToAdd.single().groupId);assertEquals("Sender name",p.feedsToAdd.single().name)
 }

 @Test fun collisionsAmongNewNamesGetStableSuffixes() {
  val feeds=listOf(source,source.copy(url="https://example.org/other"),source.copy(url="https://example.org/third"))
  val p=LocalSyncPlanner.plan(pkg(feeds,emptyList()),emptyList(),emptyList(),1,"default",newId={"id-${nextId++}"})
  assertEquals(listOf("Sender name","Sender name (2)","Sender name (3)"),p.feedsToAdd.map { it.name })
 }

 @Test fun pairsByNormalizedFeedUrlAndExactLinkNotSenderUuid() {
  val f=feed("receiver-random-id","https://example.org/rss")
  val a=article("receiver-article-uuid",f,"https://example.org/story?id=1",true,false)
  val incoming=SyncArticleState("https://example.org/rss",a.link,false,true,50)
  val p=LocalSyncPlanner.plan(pkg(listOf(source),listOf(incoming)),listOf(f),listOf(a),1,"default")
  assertEquals("receiver-article-uuid",p.updates.single().receiverArticleId);assertEquals(1,p.matchedArticles);assertEquals(0,p.unmatchedArticles)
 }

 @Test fun exactArticleLinkMeansQueryDifferenceDoesNotMatch() {
  val f=feed("f","https://example.org/rss");val a=article("a",f,"https://example.org/story?x=1",true,false)
  val p=LocalSyncPlanner.plan(pkg(listOf(source),listOf(SyncArticleState(source.url,"https://example.org/story?x=2",false,true,null))),listOf(f),listOf(a),1,"default")
  assertEquals(0,p.matchedArticles);assertEquals(1,p.unmatchedArticles);assertTrue(p.updates.isEmpty())
 }

 @Test fun unreadUsesAndAndStarredUsesOr() {
  val f=feed("f",source.url);val a=article("a",f,"https://e.test/a",false,true,100)
  val p=LocalSyncPlanner.plan(pkg(listOf(source),listOf(SyncArticleState(f.url,a.link,true,false,200))),listOf(f),listOf(a),1,"default")
  val u=p.updates.single();assertFalse(u.isUnread);assertTrue(u.isStarred);assertEquals(100,u.readAt)
 }

 @Test fun readAtUsesTheEarliestNonNullValue() {
  val f=feed("f",source.url);fun merged(old:Long?, incoming:Long?)=LocalSyncPlanner.plan(pkg(listOf(source),listOf(SyncArticleState(f.url,"L",false,false,incoming))),listOf(f),listOf(article("a",f,"L",true,false,old)),1,"g").updates.single().readAt
  assertEquals(20L,merged(null,20));assertEquals(10L,merged(10,null));assertEquals(10L,merged(10,20));assertNull(merged(null,null))
 }

 @Test fun unmatchedRowsAreCountedNotInsertedAndUnmatchedFeedStillCreatesFeed() {
  val p=LocalSyncPlanner.plan(pkg(listOf(source),listOf(SyncArticleState(source.url,"missing",false,true,null))),emptyList(),emptyList(),1,"default",newId={"new"})
  assertEquals(1,p.feedsToAdd.size);assertEquals(0,p.matchedArticles);assertEquals(1,p.unmatchedArticles);assertTrue(p.updates.isEmpty())
 }

 @Test fun duplicateInputFeedOrArticleKeysAreRejected() {
  val duplicateFeeds=listOf(source,source.copy(url="https://example.org/rss"))
  assertTrue(runCatching { LocalSyncPlanner.plan(pkg(duplicateFeeds,emptyList()),emptyList(),emptyList(),1,"g") }.isFailure)
  val duplicateArticles=listOf(SyncArticleState(source.url,"L",true,false,null),SyncArticleState("https://example.org/rss","L",false,true,null))
  assertTrue(runCatching { LocalSyncPlanner.plan(pkg(listOf(source),duplicateArticles),emptyList(),emptyList(),1,"g") }.isFailure)
 }

 @Test fun invalidAccountIdOrMissingDefaultGroupIsRejected() {
  assertTrue(runCatching { LocalSyncPlanner.plan(pkg(emptyList(),emptyList()),emptyList(),emptyList(),0,"g") }.isFailure)
  assertTrue(runCatching { LocalSyncPlanner.plan(pkg(emptyList(),emptyList()),emptyList(),emptyList(),1,"") }.isFailure)
 }
}
```

Test supplier `newId={"id-${nextId++}"}` and API callback are both `() -> String`.

- [ ] **Step 2:** `/tmp/exc05a-run.sh '--tests *LocalSyncPlannerTest' t4red`; unresolved expected.
- [ ] **Step 3: Implement.** Require positive receiver account ID and nonblank default group; Local account *type* is checked by `LocalSyncRepository`, which reads `AccountDao.queryById` and requires `account.type.id == AccountType.Local.id`. Build URL-normalized receiver feeds in stable `id` order; preserve receiver metadata. Incoming duplicate normalized URL/article key rejects. Missing feeds get injected `newId()` (prod UUID); collision names suffix `(2)`, `(3)` comparing case-insensitive within receiver groups and newly planned feeds. Build article index from only receiver `accountId`, keyed normalized feed URL + exact `link`, stable first ID. Every incoming article with no receiver pair increments unmatched; never creates Article. For match calculate unread AND, star OR, earliest non-null readAt; only emit update if any field differs; sorted deterministic result.
- [ ] **Step 4:** 15 tests green. Mutations unread OR, star AND, match by sender UUID, remove receiver account filter, reverse readAt choice, remove input feed/article duplicate checks, skip name suffixing, or emit no-op updates; each is caught by named test. New feed-only output plus unchanged receiver feed assertions cover receiver metadata preservation; unmatched article test forbids placeholder rows.
- [ ] **Step 5: Commit** `feat(exc-05a): plan local sync merges without database writes` (+ trailer).

---


### Task 5: Room apply nguyên tử, không schema change

**Resolved interface:** `LocalSyncRepository.apply(localAccountId: Int, incoming: SyncPackage): LocalSyncApplyResult`; it rebuilds `LocalSyncPlan` from current DB snapshot inside `db.withTransaction`. `LocalSyncApplyResult` contains counts (`feedsAdded`, `duplicateFeeds`, `matchedArticles`, `unmatchedArticles`, `updatesApplied`). Do not accept a caller-supplied `LocalSyncPlan` for persistence.

**Verified test APIs:** `AccountDao.insert(Account): Long`, `GroupDao.insert(vararg Group)`, `FeedDao.queryAll(accountId)` and `queryByLink(accountId,url)` (exact equality on stored URL), `FeedDao.insert(vararg Feed)`, `ArticleDao.queryById(id): ArticleWithFeed?`, `ArticleDao.insert(vararg Article)`, `ArticleReadPositionDao.upsertPosition(position)` / `getPosition(articleId)`. `ArticleReadPosition` constructor has `isSynced` default false. `Feed`/`Group` IDs are strings; use the production `1.getDefaultGroupId()` helper, not an invented literal.

**Files:** Modify `domain/repository/{FeedDao,GroupDao,ArticleDao}.kt`; create `infrastructure/p2psync/LocalSyncRepository.kt`; test Robolectric `LocalSyncRoomIntegrationTest.kt`.

**Interfaces:** Consumes validated `SyncPackage`, DAOs/`AndroidDatabase`; produces `snapshot`, read-only `preview`, and authoritative `apply` (replans inside transaction).

- [ ] **Step 1: Viết test đỏ** (in-memory Room, `@RunWith(RobolectricTestRunner::class) @Config(manifest = Config.NONE)`). Setup hai Local + một Fever account; default/custom groups; local duplicate feed + existing article; Fever feed/article. Gọi `apply` sẽ chưa compile.

```kotlin
package com.mckimquyen.reader.infrastructure.p2psync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleReadPosition
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.ui.ext.getDefaultGroupId
import com.mckimquyen.reader.domain.p2psync.*
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LocalSyncRoomIntegrationTest {
 private lateinit var db: AndroidDatabase
 private lateinit var repo: LocalSyncRepository
 private val incoming = SyncPackage(1,"a".repeat(32),10,
   listOf(SyncFeed("https://example.org/rss","Sender Name","Sender Group"),SyncFeed("https://new.org/rss","New Feed","New Group")),
   listOf(SyncArticleState("https://example.org/rss","https://example.org/a",false,true,50L),
         SyncArticleState("https://example.org/rss","https://example.org/missing",false,true,60L),
         SyncArticleState("https://fever.org/rss","https://example.org/fever",false,true,70L)))

 @Before fun setup()=runBlocking {
   db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AndroidDatabase::class.java).allowMainThreadQueries().build()
   db.accountDao().insert(Account(id=1,name="Local",type=AccountType.Local))
   db.accountDao().insert(Account(id=2,name="Other Local",type=AccountType.Local))
   db.accountDao().insert(Account(id=3,name="Fever",type=AccountType.Fever))
   db.groupDao().insert(Group(id=1.getDefaultGroupId(),name="Default",accountId=1))
   db.groupDao().insert(Group(id="mygroup",name="My group",accountId=1))
   db.groupDao().insert(Group(id=2.getDefaultGroupId(),name="Default",accountId=2))
   db.groupDao().insert(Group(id=3.getDefaultGroupId(),name="Default",accountId=3))
   db.feedDao().insert(Feed("local-feed","Receiver Name",url="https://EXAMPLE.org:443/rss/#fragment",groupId="mygroup",accountId=1,icon="local-icon"))
   db.feedDao().insert(Feed("other-feed","Other account copy",url="https://EXAMPLE.org:443/rss/#fragment",groupId=2.getDefaultGroupId(),accountId=2))
   db.feedDao().insert(Feed("fever-feed","Fever",url="https://fever.org/rss",groupId=3.getDefaultGroupId(),accountId=3))
   db.articleDao().insert(article("local-uuid","local-feed",1,"https://example.org/a",unread=true,starred=false,readAt=100L))
   db.articleDao().insert(article("other-local-uuid","other-feed",2,"https://example.org/a",unread=true,starred=false,readAt=null))
   db.articleDao().insert(article("fever-uuid","fever-feed",3,"https://example.org/fever",unread=true,starred=false,readAt=null))
   repo=LocalSyncRepository(db, newId={ "new-id" })
 }

 @After fun teardown()=db.close()

 @Test fun duplicateFeedKeepsReceiverMetadataAndNewFeedUsesDefaultGroup()=runBlocking {
   repo.apply(localAccountId=1, incoming)
   val feeds=db.feedDao().queryAll(1).associateBy { it.url }
   val duplicate=feeds.getValue("https://EXAMPLE.org:443/rss/#fragment")
   assertEquals("Receiver Name",duplicate.name);assertEquals("mygroup",duplicate.groupId);assertEquals("local-icon",duplicate.icon)
   val added=db.feedDao().queryAll(1).single { it.url=="https://new.org/rss" }
   assertEquals(1.getDefaultGroupId(),added.groupId);assertEquals("New Feed",added.name)
 }

 @Test fun updatesOnlyReadStarAndReadAtOnMatchedLocalArticle()=runBlocking {
   val before=db.articleDao().queryById("local-uuid")!!.article
   repo.apply(1,incoming)
   val after=db.articleDao().queryById("local-uuid")!!.article
   assertFalse(after.isUnread);assertTrue(after.isStarred);assertEquals(50L,after.readAt)
   assertEquals(before.title,after.title);assertEquals(before.date,after.date);assertEquals(before.feedId,after.feedId)
   assertEquals(before.accountId,after.accountId);assertEquals(before.rawDescription,after.rawDescription)
   assertEquals(before.fullContent,after.fullContent);assertEquals(before.aiSummary,after.aiSummary);assertEquals(before.audioUrl,after.audioUrl)
 }

 @Test fun unmatchedArticleIsSkippedAndFeverAccountIsUntouched()=runBlocking {
   repo.apply(1,incoming)
   assertNull(db.articleDao().queryArticleByLink("https://example.org/missing","local-feed",1))
   val otherLocal=db.articleDao().queryArticleByLink("https://example.org/a","other-feed",2)
   assertNotNull(otherLocal)
   val otherLocalArticle=db.articleDao().queryById("other-local-uuid")!!.article
   assertTrue(otherLocalArticle.isUnread);assertFalse(otherLocalArticle.isStarred);assertNull(otherLocalArticle.readAt)
   val fever=db.articleDao().queryById("fever-uuid")!!.article
   assertTrue(fever.isUnread);assertFalse(fever.isStarred);assertNull(fever.readAt)
 }

 @Test fun applyingTheSamePackageTwiceIsIdempotent()=runBlocking {
   repo.apply(1,incoming);val once=db.feedDao().queryAll(1).sortedBy { it.id }
   repo.apply(1,incoming);val twice=db.feedDao().queryAll(1).sortedBy { it.id }
   assertEquals(once.map { it.id to it.url },twice.map { it.id to it.url })
   assertEquals(1,twice.count { FeedUrlNormalizer.normalize(it.url)=="https://example.org/rss" })
 }

 @Test fun databaseFailureAfterAddingFeedRollsTheWholeApplyBack()=runBlocking {
   db.openHelper.writableDatabase.execSQL("CREATE TRIGGER abort_sync_update BEFORE UPDATE OF isUnread ON article BEGIN SELECT RAISE(ABORT, 'injected'); END")
   assertTrue(runCatching { repo.apply(1,incoming) }.isFailure)
   assertNull(db.feedDao().queryByLink(1,"https://new.org/rss").firstOrNull())
   assertTrue(db.articleDao().queryById("local-uuid")!!.article.isUnread)
 }

 @Test fun rejectsNonLocalAccountBeforeAnyWrite()=runBlocking {
   assertTrue(runCatching { repo.apply(3,incoming) }.isFailure)
   assertNull(db.feedDao().queryByLink(3,"https://new.org/rss").firstOrNull())
 }

 @Test fun statePlanDoesNotTouchReadPosition()=runBlocking {
   db.articleReadPositionDao().upsertPosition(ArticleReadPosition("local-uuid",1,"https://example.org/rss","https://example.org/a",0.4f,77L,false))
   repo.apply(1,incoming)
   assertEquals(0.4f,db.articleReadPositionDao().getPosition("local-uuid")!!.progress,0f)
 }

 private fun article(id:String,feedId:String,accountId:Int,link:String,unread:Boolean,starred:Boolean,readAt:Long?)=Article(
   id=id,date=Date(123L),title="Keep title",author="Keep author",rawDescription="RAW BODY",shortDescription="SHORT BODY",
   fullContent="FULL BODY",img="https://example.org/img",link=link,feedId=feedId,accountId=accountId,isUnread=unread,
   isStarred=starred,isReadLater=false,updateAt=Date(99L),aiSummary="KEEP SUMMARY",readAt=readAt,audioUrl="KEEP AUDIO",
   audioMime="audio/aac",audioDurationSec=11)
}
```

`ArticleWithFeed.article` is an `Article`, so field assertions above are valid. The unmatched assertion uses the existing `ArticleDao.queryArticleByLink(link, feedId, accountId)` and is exact. The second Local account deliberately owns a feed with the same normalized URL and article link; its state assertion catches missing account scoping.

- [ ] **Step 2:** `/tmp/exc05a-run.sh '--tests *LocalSyncRoomIntegrationTest' t5red`; unresolved `LocalSyncRepository` expected.
- [ ] **Step 3: DAO operations (verified: no such queries exist yet).** Add to `ArticleDao`:

```kotlin
    /** EXC-05a: only the columns sync needs; no title, summary, audio or body ever leaves the cursor (FIX-17). */
    @Query("SELECT a.id AS id, a.accountId AS accountId, f.url AS feedUrl, a.link AS link, a.isUnread AS isUnread, a.isStarred AS isStarred, a.readAt AS readAt FROM article AS a JOIN feed AS f ON f.id = a.feedId WHERE a.accountId = :accountId ORDER BY a.id")
    suspend fun querySyncSnapshots(accountId: Int): List<SyncArticleSnapshot>

    /** EXC-05a: the only article write sync performs; touches exactly three columns. */
    @Query("UPDATE article SET isUnread = :isUnread, isStarred = :isStarred, readAt = :readAt WHERE id = :articleId AND accountId = :accountId")
    suspend fun updateSyncState(accountId: Int, articleId: String, isUnread: Boolean, isStarred: Boolean, readAt: Long?): Int
```

`FeedDao.queryAll(accountId)` and `GroupDao.insert` already exist; `FeedDao.queryByLink` compares the stored URL exactly, so the planner (not SQL) matches by normalized URL. Use existing `AccountDao.queryById(id): Account?` and reject missing accounts; no DAO addition needed. Room `@Transaction` cannot reach other DAOs, so `LocalSyncRepository.apply` uses `androidx.room.withTransaction`: inside it read feeds/articles, call `LocalSyncPlanner.plan`, `feedDao.insert(*newFeeds)`, then `articleDao.updateSyncState` per update; assert each returns `1` or throw to roll back. Never call `AbstractRssRepository`.
- [ ] **Step 4: Repository.** `class LocalSyncRepository @Inject constructor(private val db: AndroidDatabase)` plus an internal secondary constructor taking `newId: () -> String` for tests (one `@Inject` constructor only; production id = `UUID.randomUUID().toString()`). Methods: `snapshot(localAccountId)`, `preview(localAccountId, incoming): LocalSyncPlan`, `apply(localAccountId, incoming): LocalSyncApplyResult`. Each first loads the account and throws unless `type.id == AccountType.Local.id`; default group id is `localAccountId.getDefaultGroupId()`. `SyncArticleSnapshot` is a plain data class in `domain/p2psync` (Room maps the aliased columns by name). `snapshot` maps feeds to `SyncFeed(normalizedUrl, name, groupName)` (skip feeds whose URL fails normalization) and articles to `SyncArticleState` using only `link/isUnread/isStarred/readAt`, then runs `SyncPackageCodec` validation. `apply` re-plans inside the transaction (authoritative); `preview` is informational.
- [ ] **Step 5:** Run 7 integration tests; verify schema stays 20 and `git diff -- app/schemas` empty. Mutation: replace `withTransaction` with sequential DAO calls -> trigger rollback test red; remove `accountId` filter -> Fever untouched test red; update title too -> preservation test red.
- [ ] **Step 6: Commit.** `feat(exc-05a): apply sync plans in one Room transaction` (+ trailer).

---


### Task 6: HTTP session ciphertext-only, token/rate-limit isolation

**Files:** Modify `infrastructure/mirror/{MirrorEngine,MirrorServer}.kt`; create `domain/mirror/SyncSessionRouter.kt`, `infrastructure/mirror/SyncSessionEngine.kt` only if separating engine interface proves needed; tests `domain/mirror/SyncSessionRouterTest.kt`, `infrastructure/mirror/MirrorServerSyncTest.kt`.

**Verified existing API:** `MirrorServer.start(bindAddress)` creates one `MirrorToken` with `MirrorLimits.TOKEN_TTL_MILLIS`, owns bounded socket workers/timeouts and `MirrorRateLimiter`, then calls `MirrorRouter.route(request, clock())`. Current router accepts only `^/m/<32-hex>(/state|/events)?$`; current `MirrorResponse` has no byte response. `MirrorHttp.reply(out,status,contentType,body,...)` takes `String`, so do not convert arbitrary ciphertext to text. `MirrorToken.matches(candidate, nowMillis)` does constant-time token comparison and expiry check. Do not point sync traffic at `/m/.../state`, whose body is plaintext JSON.

**Interfaces:** `SyncSessionEngine.start(bindAddress: InetAddress, ciphertext: ByteArray): MirrorStartResult`, `stop()`, `isRunning`; `LocalSyncTransport.fetch(url: String): ByteArray`. A sync session starts on a specific LAN bind address with already-encrypted bytes; returns existing `MirrorStartResult.Started(port, token)`. Its own exact-match router returns `NotFound | Ciphertext(ByteArray)` and never accepts a plaintext package or secret. Use existing token, expiry, limiter, socket timeout, bounded parser/worker lifecycle; sync-only session must not expose mirror page/state/SSE endpoints. Existing mirror behavior and paths remain unchanged. Client parses a user-entered `http` URL with host/port/path, rejects user-info/fragment/control/over-limit values, disables redirects, applies socket timeout and 8 MiB response cap before allocation, and reports generic local errors. Session URL remains separate from the pairing secret.

- [ ] **Step 1: Tests đỏ.** Add router tests with fixed token and ciphertext; assert exact token route returns identical bytes, wrong/expired token and unknown/legacy mirror paths return `NotFound`, and response object has no plaintext/package/secret field.

```kotlin
package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.*
import org.junit.Test

class SyncSessionRouterTest {
    private val token = MirrorToken("a".repeat(32), 5_000L)
    private val ciphertext = byteArrayOf(0, 1, 2, -1)
    private val router = SyncSessionRouter({ token }, { ciphertext })

    @Test fun validExactPathReturnsOnlyCiphertext() {
        val response = router.route(MirrorRequest("/s/${token.value}"), 4_999L)
        assertArrayEquals(ciphertext, (response as SyncSessionResponse.Ciphertext).bytes)
    }

    @Test fun wrongExpiredAndNonSyncPathsAreIndistinguishable() {
        listOf(
            MirrorRequest("/s/${"b".repeat(32)}"),
            MirrorRequest("/s/${token.value}"),
            MirrorRequest("/s/${token.value}/state"),
            MirrorRequest("/m/${token.value}/state"),
            MirrorRequest("/s/${token.value}?x=1"),
        ).forEachIndexed { index, request ->
            val now = if (index == 1) 5_000L else 4_999L
            assertSame(SyncSessionResponse.NotFound, router.route(request, now))
        }
    }

    @Test fun endpointNeverReturnsPlaintextOrSecretAsAResponseVariant() {
        assertEquals(setOf("NotFound", "Ciphertext"), SyncSessionResponse::class.sealedSubclasses.map { it.simpleName }.toSet())
        val response = router.route(MirrorRequest("/s/${token.value}"), 4_999L) as SyncSessionResponse.Ciphertext
        assertFalse(response.bytes.contentEquals("secret".toByteArray()))
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests '*SyncSessionRouterTest'`; expected unresolved sync router/response.
- [ ] **Step 3: HTTP server integration test.** Bind only loopback, start sync-only session with opaque bytes, GET its tokenized URL through a Java `Socket`, parse status/headers/body, assert body byte-for-byte ciphertext and `Content-Type: application/octet-stream`; GET `/m/<token>/state` and malformed/wrong-token URL, assert generic 404 with no JSON or ciphertext. Assert response body does not contain UTF-8 marker bytes from source package or secret. Stop server and verify `isRunning == false`; test expiry with injected clock. Add test for per-IP rate limiter threshold using existing limits; do not relax constants.
- [ ] **Step 4: Implement isolated routing.** Add `SyncSessionRouter` and binary response variant. Extend `MirrorServer` with a sync-only start/session mode that carries only `ByteArray` ciphertext and routes exclusively through `SyncSessionRouter`; reuse `MirrorToken`, parser, `MirrorRateLimiter`, bounded socket handling, `MirrorHttp` header/body writer and shutdown behavior. Add a binary `MirrorHttp.reply` overload that writes raw bytes, computes `Content-Length`, flushes, and sets `Connection: close`; never stringify ciphertext. Keep existing `MirrorRouter` and `/m` behavior byte-for-byte. Store ciphertext only in the in-memory session; zero the server's retained array on stop where ownership permits. No logging of URL token, peer IP, secret, plaintext, or ciphertext.
- [ ] **Step 5: Define replay behavior.** One session URL can be fetched repeatedly until 5-minute token expiry; network retry is allowed. Apply replay is safe because feed insertion is deduplicated and merge is monotonic/idempotent. No claim of one-time-download or anti-replay. Stop/expiry invalidates further requests; test both. Do not add replay cache/schema.
- [ ] **Step 6: Run tests/mutations.** Run router + server tests and all existing `*Mirror*Test`. Mutations: accept `/m/<token>/state` in sync-only router; bypass `token.matches`; use `MirrorRouter` plaintext state; replace binary response with UTF-8 conversion; each must fail a named assertion. Confirm existing EXC-13 tests stay green and legacy routes unchanged.
- [ ] **Step 7: Commit.** `feat(exc-05a): serve encrypted sync envelopes on isolated LAN route` + attribution trailer.

---

### Task 7: Send/Receive ViewModel, explicit lifecycle

**Files:** Create `ui/page/p2psync/{LocalSyncUiState,LocalSyncViewModel}.kt`; Hilt binding for `SyncSessionEngine` in existing infrastructure DI module only if interface is introduced; tests `ui/page/p2psync/LocalSyncViewModelTest.kt`.

**Interfaces:** `LocalSyncUiState` sealed states: `Idle`, `Starting`, `Sending(url, secretHex, expiresAtMillis)`, `Receiving`, `Preview(incoming, plan)`, `Applying`, `Done(result)`, `Error(kind)`. `LocalSyncViewModel` consumes injected repositories/transport/engine, `CoroutineDispatcher` qualifiers already in `CoroutineDispatcherModule`, exposes `StateFlow<LocalSyncUiState>`; explicit actions `startSend(localAccountId, bindAddress)`, `stopSend()`, `loadPreview(localAccountId,url,secretHex)`, `cancelPreview()`, `applyPreview(localAccountId)`, `onScreenStopped()`. No `Context`, `Activity`, clipboard, `SecureRandom`, or function-typed Hilt constructor arguments; production crypto generates randomness internally.

- [ ] **Step 1: Tests đỏ.** Use MockK and `StandardTestDispatcher`; cover send state only after successful server start, stop clears secret and stops engine, receive validates/decrypts before preview, malformed/wrong-secret leaves repository `apply` untouched, cancel never applies, apply calls repository once only after Preview, repeated apply ignored while Applying/Done, account must be Local, session expiry/network loss/onScreenStopped stop server and clear secret/preview, dispatcher keeps KDF/network off Main.
- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests '*LocalSyncViewModelTest'`.
- [ ] **Step 3: Implement minimal state machine.** Inject one constructor per `@HiltViewModel`; use `viewModelScope`, `withContext(ioDispatcher)` for sockets/Room and `defaultDispatcher` for PBKDF2/JSON. Keep secret only in `Sending` state until stop/expiry/navigation/background; don't persist to SavedState/DataStore and don't log errors containing raw input. Do not mark Sending until snapshot validated, encrypted and sync-only engine reports Started. Receive parses URL, reads bytes, decrypts/authenticates, strict-validates package, then computes preview. `applyPreview` re-plans authoritatively inside repository transaction and transitions to Done only after commit; failure retains no plaintext package in Error.
- [ ] **Step 4: Lifecycle wiring.** Use `DisposableEffect`/screen callbacks for navigation stop and `ProcessLifecycleOwner` observer owned by ViewModel, removed in `onCleared`; observe connectivity with a registered callback that is unregistered in `onCleared`. On ON_STOP/network loss/5-minute expiry, stop server, cancel network jobs, erase secret and incoming package from state. Validate expiry with injected clock and scheduled coroutine; cancel timer/job on stop/restart. No foreground service.
- [ ] **Step 5: Green + mutation tests.** Mutate the failure branch to call Apply, persist secret, omit stop on background, bypass Local-only check; each regression must fail. Run targeted test and Hilt compile. Commit `feat(exc-05a): add sync send receive state machine` + trailer.

---

### Task 8: Material You screen + 38 locales

**Files:** Create `ui/page/p2psync/{LocalSyncPage,SyncPackagePreview}.kt`; modify `ui/page/common/{RouteName,HomeEntry,HomeEntryRoutes}.kt` as actual navigation structure requires; add used strings to all maintained `values-*/strings.xml`; test `LocalSyncPageTest.kt` and existing `StringResourceParityTest`.

**UI constraints:** `BaseScaffold` with `FeedbackIconButton(Icons.Rounded.ArrowBack)`; MaterialTheme colors; edge-to-edge `WindowInsets.safeDrawing`; 48dp targets; Send/Receive tabs or sections; no `ModalBottomSheet`. Keep one-time secret visible only on active Send state. Separate URL and secret copy/share actions; never construct a combined share payload. Receive preview includes feed added/duplicate, article matched/unmatched, monotonic warning and explicit Apply confirmation. Error state has no Apply action.

- [ ] **Step 1: Tests đỏ.** Compose Robolectric `@Config(sdk=[34], application=Application::class)`, `@GraphicsMode(NATIVE)`, one `setContent` per test. Tests: Back calls callback; Idle Send/Receive choices visible; secret hidden before Sending and shown only in Sending; URL/secret copy buttons call separate callbacks with distinct values; Preview counts render; tapping Apply opens confirm but does not call ViewModel until confirm; Cancel calls cancel and never apply; Error has no Apply; non-Local disables Send/Receive/Apply; warning explains secret is separate and HTTP carries ciphertext only. Use complete helper with all callbacks, no omitted arguments.

```kotlin
private fun showSyncPage(
    state: LocalSyncUiState,
    isLocal: Boolean,
    onBack: () -> Unit = {},
    onStartSend: () -> Unit = {},
    onLoadPreview: (String, String) -> Unit = { _, _ -> },
    onCancel: () -> Unit = {},
    onApply: () -> Unit = {},
    onCopyUrl: (String) -> Unit = {},
    onCopySecret: (String) -> Unit = {},
    onShareUrl: (String) -> Unit = {},
    onShareSecret: (String) -> Unit = {},
) = LocalSyncPage(
    state = state,
    isLocal = isLocal,
    onBack = onBack,
    onStartSend = onStartSend,
    onLoadPreview = onLoadPreview,
    onCancel = onCancel,
    onApply = onApply,
    onCopyUrl = onCopyUrl,
    onCopySecret = onCopySecret,
    onShareUrl = onShareUrl,
    onShareSecret = onShareSecret,
)
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests '*LocalSyncPageTest'`.
- [ ] **Step 3: Add exact resources.** Required keys: `local_sync_title`, `local_sync_send`, `local_sync_receive`, `local_sync_start`, `local_sync_stop`, `local_sync_server_url`, `local_sync_pairing_secret`, `local_sync_copy_url`, `local_sync_copy_secret`, `local_sync_share_url`, `local_sync_share_secret`, `local_sync_secret_warning`, `local_sync_http_warning`, `local_sync_enter_url`, `local_sync_enter_secret`, `local_sync_load_preview`, `local_sync_preview_title`, `local_sync_feeds_added`, `local_sync_feeds_existing`, `local_sync_articles_matched`, `local_sync_articles_unmatched`, `local_sync_monotonic_warning`, `local_sync_apply`, `local_sync_confirm_apply`, `local_sync_cancel`, `local_sync_done`, `local_sync_error`, `local_sync_not_local`. EN + VI manually; remaining 36 languages English. Use count-first labels (`Feeds added: %1$d`) to avoid PluralsCandidate. Every key must be referenced in UI/tests or remove it. Use `/tmp/gtw_strings.py` or `/tmp/exc13_strings.py` only after reading and verifying current contents; otherwise use current repo's parity script.
- [ ] **Step 4: Implement route/screen.** Follow existing `HomeEntry` + `RouteName` screen route patterns and existing Settings/Exclusive features entry point, choosing only one entry. Route passes selected account ID/type, ViewModel collects StateFlow, Back stops session before navigation. Do not add background path. UI callbacks invoke explicit ViewModel actions; confirmation dialog names exact counts from current Preview.
- [ ] **Step 5: Locale/lint.** Run `./gradlew testDevDebugUnitTest --tests '*StringResourceParityTest'` and `./gradlew lintDevDebug`; require parity pass, zero new `UnusedResources` and `PluralsCandidate`.
- [ ] **Step 6: Mutations.** Remove Apply confirmation, expose secret in Idle/Receive, combine share text, allow non-Local action; each UI test must fail. Restore and rerun. Commit `feat(exc-05a): add localized manual sync screen` + trailer.

---

### Task 9: Device verification, Tecno + Mac, partial record

**Files:** Create `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/p2psync/LocalSyncDeviceTest.kt`, `doc/task/done/EXC-05a_manual-lan-sync_DONE.md`; modify `doc/feature.md`, `doc/task/todo/05_EXCLUSIVE_KILLER_FEATURES.md`. Keep `doc/task/todo/EXC-05_local-p2p-sync.md` unchanged and open.

- [ ] **Step 1: Instrumentation test.** Test actual JCA crypto + loopback sync-only HTTP endpoint + in-memory Room/repository. Assert raw response equals ciphertext, decrypt/validate succeeds, explicit Apply updates matching Local flags, Fever row unchanged, malformed/wrong secret/tampered envelope cause no DB changes, and expired token returns 404. Run only on locked Tecno: `adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.reader.infrastructure.p2psync.LocalSyncDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`. Never use `connectedDevDebugAndroidTest`.
- [ ] **Step 2: Full verification.** Sequentially run `./gradlew testDevDebugUnitTest --rerun`, `./gradlew lintDevDebug --rerun-tasks`, `./gradlew assembleDevDebug assembleDevDebugAndroidTest`; inspect all `app/build/test-results/testDevDebugUnitTest/*.xml`, lint report, and build outputs. Record actual pass/fail counts and warnings; don't claim success from process exit alone. Measure PBKDF2 derive duration on Tecno via instrumentation and record actual milliseconds.
- [ ] **Step 3: Backup before smoke.** Inspect `/tmp/gtw-backup.sh`, `/tmp/gtw-restore.sh`, `/tmp/exc13-tecno-lib.sh`, `/tmp/exc13-tecno-ad.sh` with `Read` before reuse; don't trust stale scripts. On Tecno `115333744A005844`, make fresh backup of the three Room DB files and `files/datastore/settings.preferences_pb` into `/tmp/exc05a-tecno-backup`; verify each nonempty. Run `PRAGMA integrity_check` and `foreign_key_check` on copies. Never delete DataStore. If screen is Dozing/keyguard locked, stop and ask owner to unlock. For any ad, use only labelled Continue control from a fresh UI dump and re-check `topResumedActivity`/awake before every tap.
- [ ] **Step 4: Smoke with disposable state.** Use only backed-up copy/isolated test DB for seed data; do not rewrite personal feed state to construct test package. From Tecno UI start Send, separately transfer session URL and one-time secret to Mac; on Mac use an installed trusted AES-GCM provider only after confirming import works. Script must not log/print secret, plaintext package, or ciphertext; use stdin prompt for secret. Mac fetches ciphertext, decrypts, validates allowlist, shows preview-equivalent counts; send receive package back only via product Receive UI when safe isolated data permits. Confirm explicit Apply, read DB after apply, test wrong secret and one-byte tamper leave DB unchanged. If trusted Mac crypto unavailable, stop at ciphertext transport verification; do not implement crypto yourself and do not claim decrypt/apply smoke. Mac is simulated peer, never a second Android.
- [ ] **Step 5: Restore always.** Stop/force-stop app; restore all three Room files and DataStore from fresh backup using verified helper, read back and `cmp` all four; run integrity checks. Never delete DataStore. Remove test APK only after verification if that does not alter backed-up user data. Report failed/skipped restoration truthfully; do not claim completion if `cmp` differs.
- [ ] **Step 6: Completion record.** DONE title: `EXC-05a (partial; EXC-05 remains in progress)`. Record crypto parameters, allowlist, merge rules, test/lint/build exact counts, PBKDF2 timing, backup/restore `cmp`, Tecno + Mac scope, HTTP metadata exposure, and unverified two-Android/NSD/automatic/two-way criteria. Add feature entry marked EXC-05a partial; update epic row `🟡 In progress (manual one-way encrypted sync; NSD/two-Android pending)` with link. Keep original todo file and no claim EXC-05 complete.
- [ ] **Step 7: Commit docs/tests.** `feat(exc-05a): verify and document partial LAN sync slice` + attribution trailer. Branch `dev`; no `.idea` files; no push.

---

## Self-review

- Task 6 reuses only proven transport primitives: existing `MirrorServer` already owns the session token, constant-time validation/expiry, rate limiter, parser bounds, timeouts, worker pool, and socket close; the new sync-only router is distinct and cannot call mirror JSON/SSE routes. Binary HTTP response avoids converting ciphertext to a string.
- Replay is deliberately retryable until token expiry; monotonic/idempotent Apply makes repeated download/apply safe. This is not one-time retrieval and does not claim anti-replay protection.
- Task 7–8 state/UI APIs line up: ViewModel state carries `Preview(incoming, plan)`, UI invokes `applyPreview(localAccountId)` only after confirmation; stop clears secrets and pending package.
- Task 9 preserves safety gates: only Tecno serial, backup four files, inspect scripts, no DataStore deletion, no false two-Android claim.
- Task 5 reconciled: `apply(localAccountId, incoming): LocalSyncApplyResult` re-plans inside the transaction; fixtures use `getDefaultGroupId()`, a counter-based `newId`, Local account 1, a second Local account 2 and Fever account 3 (non-Local rejection test uses account 3).
