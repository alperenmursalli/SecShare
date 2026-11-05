# Week 01 — Triage

Sınıf tanımları: `../SECURITY_CYCLE.md`. Kaynak AI çıktısı: `transcript.md`
(dünkü oturum → `../../IMPROVEMENTS.md` #1 olarak kayıt altına alınmıştı).

| ID | Bulgu | AI'ın iddiası | Sınıf | Gerekçe | Aksiyon |
|----|-------|---------------|-------|---------|---------|
| F-01 | Public link indirme race'i (`resolveLinkForDownload`) tek-kullanımlık linki iki kez servis edebilir | "READ COMMITTED bunu engellemez; lock/@Version/atomik UPDATE yok" | **GERÇEK** | Doğru. Kodun kendisi yorumda race'i itiraf ediyordu (eski `FileShareService.java:396-398`). `findByTokenAndRevokedFalse` → kontrol → `revoked=true` arasında kilit yok; iki istek de `revoked=false` görebilir. | Fix (`claimDownload` atomik UPDATE) + regresyon testi |
| F-02 | At-rest şifreleme yok (disk düz metin) | `IMPROVEMENTS.md` #2 | GERÇEK | Doğru ama bu haftanın kapsamı dışı; ayrı iş. | Ertelendi (ileri hafta) |
| F-03 | "Email verification yok" (önceki değerlendirme) | Eski iddia | **YANLIŞ-ALARM** | AI yanılmıştı: email doğrulama mevcut (`User.emailVerified`, `GET /api/auth/verify`, `FileService.getAccessibleFileOrThrow:192-199`). `IMPROVEMENTS.md` notunda da düzeltildi. | Yok |

## F-01 reprodüksiyon (fix öncesi)

1. `maxDownloads=1` / burn-after-access bir link oluştur.
2. Aynı token'a aynı anda iki `GET` isteği gönder (ör. iki paralel curl).
3. Fix öncesi: her ikisi de `revoked=false` okur → **her ikisi de 200 + dosya**.
   Burn garantisi ihlal.
4. Fix sonrası (beklenen): tam biri `200`, diğeri `410 Gone`.

## Not — F-01 fix eksik kapsamı → MISS-01

AI race'i doğru teşhis etti ama IMPROVEMENTS.md #1'de **iki** çağrı yolu
listelenmişti (`resolveLinkForDownload` ve `recordAudienceOpenAndMaybeBurn`).
Uygulanan fix yalnız birincisini kapattı. Audience/grup yolu hâlâ açık →
`misses.md` MISS-01.
