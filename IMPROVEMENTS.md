# SecShare — İyileştirme & Güvenlik Yol Haritası

Koda dayalı (dosya:satır referanslı) eksiklik listesi. Sırayla çözülecek; her madde
bitince `[ ]` → `[x]` yapılır ve **Durum** satırı güncellenir.

> Değerlendirme tarihi: 2026-11-04 · `main` branch

---

## Öncelik sırası (özet)

| # | İş | Öncelik | Durum |
|---|---|---|---|
| 1 | Burn-after-reading atomikliği (eşzamanlı çift indirme race'i) | 🔴 Kritik | ✅ Tamamlandı (`fix/burn-atomicity`, Week 01) |
| 2 | Dosyaların disk üzerinde şifrelenmesi (at-rest, AES-GCM) | 🔴 Kritik | ⬜ Bekliyor |
| 3 | Per-IP rate limiting | 🟠 Yüksek | ✅ Tamamlandı (`feature/rate-limiting`) |
| 4 | Password reset akışı | 🟠 Yüksek | ⬜ Bekliyor |
| 5 | İçerik taramasını genişlet + async + quarantine | 🟡 Orta | ⬜ Bekliyor |
| 6 | Hard storage quota (hesap başına) | 🟡 Orta | ⬜ Bekliyor |
| 7 | Content-Disposition dosya adı sanitizasyonu | 🟢 Düşük | ⬜ Bekliyor |

> Not: Önceki değerlendirmedeki "email verification yok" maddesi **geçersiz** — email
> doğrulama mevcut (`User.emailVerified`, `GET /api/auth/verify`,
> `FileService.getAccessibleFileOrThrow:192-199`). Listeye alınmadı.

---

## 1. Burn-after-reading atomikliği 🔴

**Problem.** Tek kullanımlık "imha" linki eşzamanlı iki istekte iki kez servis edilebilir;
burn-after-reading garantisi ihlal olur. Kod bu race'i yorumda zaten itiraf ediyor
(`FileShareService.java:396-398`).

**Kanıt.**
- `resolveLinkForDownload` (`FileShareService.java:400-433`): `findByTokenAndRevokedFalse`
  → kontrol → `downloadCount++` → `revoked=true`. İki istek de adım 1'de `revoked=false`
  görebilir (PostgreSQL READ COMMITTED bunu engellemez). Pessimistic lock / `@Version` /
  atomik conditional UPDATE **yok**.
- `recordAudienceOpenAndMaybeBurn` (`FileShareService.java:353-382`): `openedCount++` ve
  `memberCount` eşik karşılaştırması kilitsiz — ALL modunda da aynı race.
- `read → purge` resolve transaction'ı commit olduktan **sonra** ve transaction **dışında**
  yapılıyor (`PublicShareController.java:43-50`, `FileController.java:55-57`).

**Çözüm (seçilecek).**
- (Tercih) Atomik koşullu UPDATE: `UPDATE file_share SET download_count = download_count+1,
  revoked = true WHERE id = ? AND revoked = false` → `affectedRows == 1` kontrolü.
- Alternatif: repository'de `@Lock(PESSIMISTIC_WRITE)` + `SELECT ... FOR UPDATE`, veya
  `@Version` optimistic lock + retry.
- read+purge aynı transaction kapsamına alınmalı.
- Regresyon testi: iki eşzamanlı indirme → tam olarak biri 2xx, diğeri 410.

**Yapıldı.** Üç çağrı yolu da kapatıldı: link (`claimDownload`), audience
(`claimRevoke`/`claimFirstOpen`/`incrementOpenedCount` + canlı `currentOpenedCount`),
authenticated USER-grant (`lockActiveShare`, pessimistic lock — MISS-02). FIRST modunda
claim'i kaybeden eşzamanlı açılış artık `410`. Regresyon: `FileShareE2ETest` içinde 3
concurrency testi. Ayrıntı: `security/w01/`, transcript `ai/w01/`.

**Durum.** ✅ Tamamlandı (ALL-modu purge-vs-serve + USER-ALL burn-timing edge'leri Week 02'ye)

---

## 2. Dosyaların disk üzerinde şifrelenmesi (at-rest) 🔴

**Problem.** Dosyalar diske düz metin yazılıyor; sunucuya/disk'e erişen biri içerikleri
doğrudan okuyabilir. "Security-focused" iddiasının en zayıf halkası.

**Kanıt.** `FileService.upload` → `Files.write(targetPath, content)` (`FileService.java:109`);
`readAllBytes`/`loadResource` düz okuyor (`:153-164`, `:242-249`). Kodda cipher yok.

**Çözüm (seçilecek).**
- Her dosya için AES-GCM; anahtar env/secret'tan (KEK) türetilir, dosya başına rastgele IV.
- Yazarken şifrele, okurken çöz (`loadResource`/`readAllBytes` sarmalanır).
- Anahtar rotasyonu ve IV/tag saklama şeması kararlaştırılacak.

**Durum.** ⬜ Bekliyor

---

## 3. Per-IP rate limiting 🟠 ✅

**Problem.** Kimlik doğrulamasız, suistimale açık uçlar (login/register/verify + public
share linkleri) brute-force / token enumeration'a açıktı.

**Yapıldı.** In-memory token-bucket (`config/RateLimiter`, `config/RateLimitFilter`),
`SecurityConfig`'te JWT filtresinden önce zincire eklendi. `auth` (10/60s) ve `download`
(30/60s) specs, env ile ayarlanabilir, `RATELIMIT_ENABLED` off switch. 429 + `Retry-After`.
`RateLimitE2ETest` ile doğrulandı. Branch: `feature/rate-limiting`.

**Durum.** ✅ Tamamlandı

---

## 4. Password reset akışı 🟠

**Problem.** Şifresini unutan kullanıcı hesabını kurtaramıyor; reset endpoint'i yok.

**Kanıt.** Kodda `reset` ile ilgili hiçbir endpoint/servis yok.

**Çözüm.** Mevcut doğrulama token altyapısını (SecureRandom token + TTL + email outbox)
yeniden kullan: `POST /api/auth/forgot-password` (token üret + mail kuyruğa al),
`POST /api/auth/reset-password` (token doğrula + yeni şifre set et, tek kullanımlık).
Rate limiting zaten `/api/auth/**` için var.

**Durum.** ⬜ Bekliyor

---

## 5. İçerik taramasını genişlet + async + quarantine 🟡

**Problem.** İçerik denetimi dar: sadece pdf/png/jpg için magic-byte uyuşmazlığı;
txt/doc/docx/xlsx/zip içerik olarak denetlenmiyor. Magic-byte sadece offset 0. Tarama
senkron ve tüm dosyayı belleğe alıyor (DoS yüzeyi). MIME type güvenlik kontrolü değil.

**Kanıt.** `BuiltInFileScanner.java:75-88` (sadece 3 format), `FileService.java:87`
(`file.getBytes()` tümü belleğe), `:122` (browser Content-Type doğrulanmadan saklanıyor).

**Çözüm.** Zip/office konteynerleri için denetim; async (post-upload) tarama +
`ScanStatus.QUARANTINED` durumu; ClamAV'ı prod'da varsayılan önerilen hale getir.

**Durum.** ⬜ Bekliyor

---

## 6. Hard storage quota 🟡

**Problem.** Hesap başına toplam depolama limiti yok; kullanıcı diski aşırı tüketebilir.

**Kanıt.** Sadece 50MB/dosya limiti (`FileService.java:32`). Toplam kota enforcement yok.

**Çözüm.** Hesap başına yapılandırılabilir kota; upload'ta mevcut kullanım + yeni dosya
boyutu kontrolü, aşımda `413`/`507`.

**Durum.** ⬜ Bekliyor

---

## 7. Content-Disposition dosya adı sanitizasyonu 🟢

**Problem.** Kullanıcı dosya adı response header'ına tırnak içinde doğrudan konuyor;
`"` veya CRLF içeren ad header'ı bozabilir (header injection).

**Kanıt.** `PublicShareController.java:54-55` (ve authenticated indirme yolu).

**Çözüm.** RFC 5987 `filename*=UTF-8''...` kodlaması veya ad temizleme/escape.

**Durum.** ⬜ Bekliyor
