# w01 — Triage

Kaynak AI çıktısı: [`../../ai/w01/transcript.md`](../../ai/w01/transcript.md) (5 standart
prompt, tag `w01`'e karşı). Sınıf tanımları: `../SECURITY_CYCLE.md`
(GERÇEK / YANLIŞ-ALARM / TEKRAR / WONTFIX).

| ID | Bulgu | AI sev. | Sınıf | Gerekçe (neden?) | Aksiyon |
|----|-------|---------|-------|------------------|---------|
| F-1 | Logout/token revocation yok (leaked JWT expiry'ye kadar geçerli) | Low | **GERÇEK** | Doğru. Stateless JWT, deny-list yok. Tehdit modeli için düşük; expiry kısa tutulmalı. | Kabul/backlog (expiry env ile kısa) |
| F-2 | Rate limiting bu build'de yok → brute-force/credential stuffing | High | **GERÇEK** | Doğru. `feature/rate-limiting` merge edilmemiş; `w01` kodunda throttle yok. | Ayrı hafta (rate-limiting branch merge) |
| F-3 | `originalFilename` → stored XSS riski (frontend) | Med | **YANLIŞ-ALARM** | AI frontend'i görmedi; `share.html:220` `textContent` kullanıyor (innerHTML değil), `index.html`'de escape helper (`:991`) var. XSS yok. | Yok |
| F-4 | `Content-Disposition` filename sanitize edilmiyor (header injection) | Low | **GERÇEK** | Doğru. `"`/CRLF içeren ad header'ı bozabilir (`PublicShareController:54-55`, `FileController:744`). | Backlog (RFC 5987) — IMPROVEMENTS #7 |
| F-5 | ALL-modu purge-vs-serve dar penceresi | Low | **GERÇEK** | Doğru. Terminal purge ile eşik-altı eşzamanlı `loadResource` yarışı. | Week 02 (MISS-02 değil; ayrı edge) |
| F-6 | Audience/USER authz `email` claim'ine güveniyor → spoof edilebilir | Med | **YANLIŞ-ALARM** | AI yanıldı. JWT **imzalı** (HS256, secret ≥32B); claim secret olmadan forge edilemez. Üstelik `emailVerified` şartı var. Sömürülemez. | Yok |
| F-7 | Tarama dar (zip/office/txt içerik denetlenmiyor) + full-file in-memory DoS | Med | **GERÇEK** | Doğru. `BuiltInFileScanner` sadece pdf/png/jpg magic-byte + exec/EICAR. zip/docx geçer. | Backlog — IMPROVEMENTS #5 |
| F-8 | Kullanıcı enumerasyonu: `register` 409 "Email already exists" | Low | **GERÇEK** | Doğru. Kayıtlı e-postalar ayırt edilebiliyor. Düşük etki. | Backlog (jenerik mesaj/rate limit) |
| F-9 | Parola öncesi meta sızıntısı (`/public/shares/{token}` filename/size) | Low | **WONTFIX** | Gerçek ama **tasarım gereği** — indirme sayfası dosya adını göstermeli. Risk kabul. | Belgelendi |
| F-10 | Sınırsız audience fan-out → spam relay | Med | **YANLIŞ-ALARM (abartılı)** | Büyük ölçüde yanlış: owner authenticated olmalı, `MAX_AUDIENCE_MEMBERS` cap var, mail yalnız configure edilmişse gider. "Open relay" değil. Düşük kötüye kullanım, GERÇEK kısmı F-2 (rate limit) ile örtüşür. | Yok (F-2'ye bağlı) |

## Önceki (roadmap) bulguların regresyon durumu
- Prior #1 (unverified-email IDOR) → hâlâ fix (`getAccessibleFileOrThrow` emailVerified). ✅
- Prior #4 (expired-authenticated) → hâlâ fix (`isActive()`). ✅
- Prior #2 (stored XSS) → legacy sayfalar kalkmış, frontend `textContent`. ✅

## AI'ın kaçırdığı → `misses.md`
AI "diff review" (Prompt 4) yalnızca **değişen dosyalara** baktı ve değişmeyen kodu kapsam
dışı bıraktığını açıkça yazdı. Bu yüzden burn race'inin **üçüncü çağrı yolunu**
(`recordAuthenticatedAccessAndMaybeBurn`'ün USER-grant dalı, diff'te değil) kaçırdı →
**MISS-02**.
