# Week 01 — MISS kayıtları

AI'ın kaçırdığı / kapatmadığı gerçek açıklar. Format: `../SECURITY_CYCLE.md`.

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
