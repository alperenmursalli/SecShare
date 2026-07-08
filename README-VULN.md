# SecShare — Vulnerable Mode

> ⚠️ **UYARI:** Bu profil **bilerek güvensizdir**. Yalnızca **yetkili pentest / eğitim**
> ortamında, **izole** bir makinede çalıştırın. **İnternete açık deploy ETMEYİN.**
> `main` / varsayılan profil güvenlidir; zafiyetler yalnızca `vuln` profilinde ve
> ilgili bayrak açıkken devrededir.

## Çalıştırma

```bash
# H2 in-memory (Docker/Postgres gerekmez) + tüm zafiyet bayrakları açık
SPRING_PROFILES_ACTIVE=vuln ./mvnw spring-boot:run
```

Uygulama `http://localhost:8080` üzerinde açılır. Açılışta seed verisi loglanır.

## Seed Hesaplar

| Email | Şifre | Rol | Not |
|-------|-------|-----|-----|
| `alice@corp.local` | `Summer2024!` | USER | zayıf şifre (brute force) |
| `bob@corp.local` | `password123` | USER | zayıf şifre; dosyasında SSRF ipucu |
| `admin@secshare.local` | *(güçlü)* | ADMIN | login ile erişilemez → JWT forge |

## Zafiyet Bayrakları

Tümü `src/main/resources/application-vuln.properties` içinde. Bir açığı kapatmak için
`false` yapın; **hepsi `false` iken uygulama güvenli davranır** (before/after eğitimi).

| Bayrak | Açık | OWASP |
|--------|------|-------|
| `vuln.jwt.weak-secret` | Bilinen zayıf HMAC secret → JWT forge | A02 |
| `vuln.jwt.allow-none` | `alg:none` kabulü | A02 |
| `vuln.auth.user-enum` | Kullanıcı enumeration | A07 |
| `vuln.auth.no-rate-limit` | Brute force (rate limit yok) | A07 |
| `vuln.access.legacy-download` | IDOR indirme | A01 |
| `vuln.search.sqli` | SQL injection | A03 |
| `vuln.file.path-traversal` | Path traversal / LFI | A01/A05 |
| `vuln.import.ssrf` | SSRF | A10 |
| `vuln.import.xxe` | XXE | A05 |
| `vuln.process.cmd-injection` | Command injection / RCE | A03 |
| `vuln.serve.stored-xss` | Stored XSS | A03 |
| `vuln.cors.wildcard` | CORS misconfiguration | A05 |
| `vuln.errors.verbose` | Bilgi sızıntısı | A05 |

## Dokümanlar
- `docs/CHALLENGES.md` — kursiyer için görevler ve ipuçları (spoiler yok)
- `docs/SOLUTIONS.md` — eğitmen için tam exploit adımları (**spoiler**)
