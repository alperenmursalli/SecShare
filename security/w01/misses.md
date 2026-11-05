# w01 — MISS kayıtları

AI'ın kaçırdığı / kapatmadığı gerçek açıklar. Format: `../SECURITY_CYCLE.md`.
Kaynak transcript: `../../ai/w01/transcript.md`.

- **MISS-01** — burn fix'in audience yolunu atlaması (dünkü oturumun kaçırdığı).
- **MISS-02** — 5-prompt AI geçişinin kaçırdığı: USER-grant authenticated burn yolu.

---

### MISS-02 — Burn race'i authenticated USER-grant yolunda hâlâ açık (5-prompt AI bunu kaçırdı)

- **Sınıf/CWE:** CWE-362 Race Condition / CWE-367 TOCTOU (burn-after-reading ihlali)
- **Nerede:** `FileShareService.recordAuthenticatedAccessAndMaybeBurn` → USER-grant dalı
  (`src/main/java/org/example/secshare/file/FileShareService.java:475-488`) +
  `FileController.download` (`:734-740`)
- **AI neden kaçırdı:** Prompt 4 (diff review) *yalnızca diff'teki değişen satırlara* baktı
  ve değişmeyen kodu açıkça kapsam dışı bıraktı ("code not touched by the diff was out of
  scope"). Burn fix'i `resolveLinkForDownload` + audience'ı atomik yaptı ama **aynı bug
  pattern'inin üçüncü kopyası** diff'e hiç girmeyen `recordAuthenticatedAccessAndMaybeBurn`
  içinde duruyordu. Ders: *AI diff'i inceler, kök-neden'in diff dışındaki kardeş
  kopyalarını görmez.* Prompt 2 (control smoke test) de burn'ü "link path" diye kontrol
  edip authenticated USER path'i atladı.
- **Kanıt / reprodüksiyon:** Bir dosyayı `type=USER`, `burnMode=FIRST` ile bir kullanıcıya
  paylaş. Alıcı aynı anda iki kez `GET /api/files/{id}` çağırsın:
  1. İki istek de `getAccessibleFileOrThrow`'u geçer (dosya henüz silinmedi).
  2. İkisi de `recordAuthenticatedAccessAndMaybeBurn` → USER dalı: `downloadCount++` +
     `save` (kilitsiz), `switch FIRST -> true`. `share.revoked` **hiç set edilmiyor**.
  3. İkisi de `burn=true` → controller ikisinde de `readAllBytes` + `purge` + serve.
  → Dosya **iki kez** servis edilir; burn-after-reading ihlali. (ALL modu da
  `downloadCount > 0` okumasıyla lost-update'e açık.)
- **Uygulanan fix:** `FileShareRepository.lockActiveShare` (`@Lock(PESSIMISTIC_WRITE)` +
  `revoked=false` guard). USER dalı artık grant satırını kilitliyor; FIRST'te terminal açılış
  `revoked=true` yapıp burn eder, eşzamanlı kaybeden `lockActiveShare`'den boş döner ve `410`
  alır — dosya ikinci kez servis edilmez. ALL'da sayaç kendi satır kilidi altında artıyor
  (lost-update yok). (`FileShareService.recordAuthenticatedAccessAndMaybeBurn:475-500`)
- **Regresyon testi:** `FileShareE2ETest.first_mode_user_grant_served_at_most_once_under_concurrent_authenticated_downloads`
  (8 eşzamanlı authenticated indirme → tam 1 adet 2xx).
- **Durum:** ✅ Fix edildi (16/16 test yeşil).
- **Artık (Week 02):** ALL modunda iki farklı alıcının eşzamanlı *son* açılışı, her ikisi de
  diğerini "açmamış" görürse burn ertelenebilir (çift-servis değil; dosya imha edilmeden
  kalır). Audience ALL purge-vs-serve edge'i ile birlikte Week 02'ye.

---

### MISS-01 — Burn race'i audience (grup) yolunda açık kaldı

- **Sınıf/CWE:** CWE-362 Race Condition / CWE-367 TOCTOU (burn-after-reading ihlali)
- **Nerede:** `FileShareService.recordAudienceOpenAndMaybeBurn`
  (`src/main/java/org/example/secshare/file/FileShareService.java:353-382`)
- **AI neden kaçırdı / kapatmadı:** Dünkü oturum race'i teşhis ederken her iki
  çağrı yolunu da (`IMPROVEMENTS.md` #1) listeledi, ama uygulanan fix yalnızca
  `resolveLinkForDownload`'u atomik `claimDownload` ile kapattı. Audience yolu
  aynı "oku-değiştir-kaydet" desenini kilitsiz sürdürüyor; "burn atomikliği
  çözüldü" varsayımı eksik.
- **Reprodüksiyon:**
  - **FIRST modu:** Bir audience/grup paylaşımı `BurnMode.FIRST` ile kur. Aynı
    üye (veya eşzamanlı iki üye) aynı anda iki kez açsın. Her iki istek de
    `share.revoked == false` okur (satır 366 öncesi kilit yok) → her ikisi de
    `return true` alır → dosya **iki kez** servis/imha edilir. Tek-açılış
    garantisi ihlal.
  - **ALL modu:** İki üye ilk açılışlarını eşzamanlı yapar. `openedCount++`
    (satır 373) kilitsiz read-modify-write olduğundan lost-update olur:
    `openedCount` eksik sayılır ve `openedCount >= memberCount` eşiği hiç
    tetiklenmeyebilir → dosya hiç imha edilmez (burn-after-all başarısız).
- **Fix sırasında çıkan daha ciddi ayrıntı:** FIRST modunda sorun sadece "çift
  burn" değildi. `recordAudienceOpenAndMaybeBurn` kaybeden eşzamanlı isteğe
  yalnızca `burnNow=false` döndürüyordu; `resolveAudienceTokenForDownload` yine
  de `LinkDownload`'u başarıyla döndürüp controller dosyayı **servis ediyordu**
  (`PublicShareController:48-49` `loadResource`). Yani iki eşzamanlı açılış
  dosyayı **iki kez indirebiliyordu** — burn-after-reading'in asıl ihlali. Fix,
  link yolundaki (`resolveLinkForDownload` → 410) davranışı taklit edecek şekilde
  FIRST modunda claim'i kaybedeni `410` ile reddediyor.
- **Uygulanan fix:** `FileShareRepository.claimRevoke` (atomik koşullu revoke),
  `AudienceMemberRepository.claimFirstOpen` + `incrementDownloadCount`,
  `AudienceRepository.incrementOpenedCount` + `currentOpenedCount`. FIRST modunda
  önce claim, kaybeden `410`. ALL modunda `openedCount` atomik increment + canlı
  re-read ile lost-update kapandı. (`FileShareService.recordAudienceOpenAndMaybeBurn`)
- **Regresyon testi:** `FileShareE2ETest.first_mode_audience_share_served_at_most_once_under_concurrent_opens`
  (8 eşzamanlı açılış → tam 1 adet 2xx). Link yolu için de
  `burn_link_served_at_most_once_under_concurrent_downloads` eklendi.
- **Durum:** ✅ Fix edildi (17/17 test yeşil).

**Artık (residual) — dürüst not:** ALL modunda terminal açılış dosyayı imha
ederken (`purge`), tam o anda eşik altındaki başka bir eşzamanlı açılış
`loadResource` ile imha edilmiş byte'lara erişmeye çalışabilir (dar pencere,
`500` riski). Sayaç doğruluğu ve FIRST serve-once garantisi kapandı; bu ALL-modu
purge-vs-serve yarışı bilinçli olarak Week 02'ye bırakıldı (MISS-02 adayı).
