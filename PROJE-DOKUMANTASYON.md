# SecShare — Kapsamlı Proje Dokümantasyonu

> Bu doküman, projeyi **hiç bilmeyen birine** anlatacak şekilde, adım adım ve
> mümkün olduğunca detaylı yazılmıştır. Yazılım terimlerini gördüğünüz yerde
> kısa bir açıklama da bulacaksınız. Amaç: bu depoyu ilk kez açan birinin
> "bu proje nedir, neyle yazılmış, hangi iş nasıl hallediliyor" sorularına
> baştan sona cevap verebilmek.

---

## 0. Bir cümlede proje

**SecShare**, kullanıcıların kayıt olup giriş yaptıktan sonra kendi dosyalarını
yükleyip indirebildiği, sildiği ve listeleyebildiği **güvenli bir dosya paylaşım
uygulamasıdır**. Her kullanıcı yalnızca kendi dosyalarını görebilir; başkasının
dosyasına erişemez. Kimlik doğrulama **JWT token** ile yapılır.

> ⚠️ **Not:** Bu uygulama eğitim / güvenlik testi amaçlı geliştirilmiştir.
> Herkese açık internette çalıştırırken hassas veri koymayın.

---

## 1. Terimler sözlüğü (önce bunları bilelim)

Doküman boyunca geçecek temel kavramlar:

| Terim | Basit açıklama |
|-------|----------------|
| **Backend** | Sunucu tarafı. İş mantığının, veritabanı erişiminin ve API'nin çalıştığı yer. Burada **Java + Spring Boot** ile yazılmış. |
| **Frontend** | Tarayıcıda görünen arayüz. Burada düz **HTML + CSS + JavaScript** sayfaları. |
| **API** | Backend'in dışarıya açtığı "kapılar" (adresler). Örn. `POST /api/auth/login`. Programlar bu kapılara istek atarak veri alışverişi yapar. |
| **REST API** | HTTP üzerinden, `GET/POST/DELETE` gibi metotlarla çalışan yaygın API tarzı. |
| **Veritabanı (DB)** | Kalıcı verilerin (kullanıcılar, dosya kayıtları) tutulduğu yer. Burada **PostgreSQL**. |
| **JWT** | "JSON Web Token". Giriş yaptıktan sonra size verilen, imzalı dijital kimlik kartı. Her istekte bunu gösterirsiniz. |
| **BCrypt** | Şifreleri veritabanına düz metin yerine, geri döndürülemez şekilde "hash"leyerek saklamaya yarayan algoritma. |
| **Docker** | Uygulamayı, çalışması için gereken her şeyle birlikte bir "kutuya" (container) paketleyen araç. "Bende çalışıyordu ama sunucuda çalışmadı" sorununu ortadan kaldırır. |
| **Nginx** | İstekleri karşılayıp arkadaki uygulamaya yönlendiren, HTTPS/TLS işini üstlenen ters proxy (reverse proxy). |
| **CI/CD** | Kod GitHub'a gönderilince otomatik test/derleme/deploy yapan otomasyon. Burada **GitHub Actions**. |

---

## 2. Teknoloji Yığını (Tech Stack) — ne, neden kullanılmış?

### 2.1 Backend (sunucu tarafı)

| Teknoloji | Sürüm | Ne işe yarar |
|-----------|-------|--------------|
| **Java** | 17 | Ana programlama dili. |
| **Spring Boot** | 3.4.2 | Java ile web uygulaması yazmayı çok kolaylaştıran çatı (framework). Sunucuyu, ayarları, bağımlılıkları kendisi yönetir. |
| **Spring Web (MVC)** | (Boot ile) | HTTP isteklerini karşılayan Controller'ları yazmayı sağlar. |
| **Spring Security** | (Boot ile) | Kimlik doğrulama + yetkilendirme altyapısı. Hangi adrese kimin erişebileceğini yönetir. |
| **Spring Data JPA + Hibernate** | (Boot ile) | Java nesnelerini veritabanı tablolarına otomatik eşleştirir (ORM). SQL yazmadan `save()`, `findById()` gibi metotlarla veri işlersiniz. |
| **jjwt** | 0.12.5 | JWT token üretme ve doğrulama kütüphanesi. |
| **PostgreSQL sürücüsü** | (runtime) | Java'nın PostgreSQL ile konuşmasını sağlayan driver. |
| **Spring Validation** | (Boot ile) | Gelen verinin kurallara uyup uymadığını (örn. geçerli e-posta, min. şifre uzunluğu) kontrol eder. |
| **Maven** | (mvnw wrapper) | Bağımlılık yönetimi ve derleme aracı. `pom.xml` dosyasıyla yönetilir. |

> **Not:** `pom.xml` içinde **Flyway** bağımlılığı da var ama
> `application.properties` içinde `spring.flyway.enabled=false` ile **kapalı**.
> Yani şu an veritabanı şeması Flyway migration'larıyla değil, Hibernate'in
> `ddl-auto=update` özelliğiyle otomatik oluşturuluyor (bkz. Bölüm 6).

### 2.2 Frontend (arayüz)

| Teknoloji | Ne işe yarar |
|-----------|--------------|
| **Düz HTML / CSS / JavaScript** | Arayüz, `src/main/resources/static/` altındaki statik `.html` dosyalarından oluşur. Herhangi bir build adımı gerektirmez; Spring Boot bunları doğrudan servis eder. |

> ⚠️ **Önemli düzeltme:** README dosyasında frontend için "React + TypeScript +
> Vite" yazıyor. Ancak **mevcut kodda React/Vite projesi yoktur** (`Secure-Share`
> klasörü boş). Gerçek arayüz, aşağıdaki vanilla HTML sayfalarıdır:
> - `index.html` — açılış / tanıtım sayfası (≈718 satır, kendi CSS'i ile).
> - `files.html` — giriş yapıp dosya yükleme/listeleme/indirme arayüzü.
> - `test.html` — hızlı test arayüzü (token alıp basit istekler denemek için).
> - `guide.html` — kullanım kılavuzu sayfası.

### 2.3 Veritabanı

| Teknoloji | Ne işe yarar |
|-----------|--------------|
| **PostgreSQL 16** | İlişkisel veritabanı. `users` ve `files` tablolarını tutar. |

### 2.4 DevOps / Dağıtım (Deployment)

| Teknoloji | Ne işe yarar |
|-----------|--------------|
| **Docker** | Uygulamayı imaj (image) olarak paketler. Çok aşamalı (multi-stage) build kullanılır. |
| **Docker Compose** | Uygulama + PostgreSQL'i tek komutla birlikte ayağa kaldırır. |
| **GitHub Actions** | `main` dalına her push'ta sunucuya otomatik deploy eder. |
| **Oracle Cloud** | Ana barındırma (hosting) ortamı (SSH ile deploy). |
| **DuckDNS** | Ücretsiz alan adı servisi — `sec-share.duckdns.org`. |
| **Nginx** | Önde ters proxy + HTTPS (mimari şemada; uygulama sadece localhost:8080'e bağlanır). |
| **Render** | Alternatif deploy hedefi için hazır yapılandırma dosyası (`render.yaml`) mevcut. |

---

## 3. Genel Mimari — parçalar nasıl birbirine bağlanıyor?

```
      Tarayıcı / curl / mobil istemci
                   │
                   │  HTTP(S) + "Authorization: Bearer <JWT>"
                   ▼
         ┌──────────────────────┐
         │  Nginx (ters proxy)  │   ← HTTPS/TLS burada sonlanır (public erişimde)
         └──────────┬───────────┘
                    │  http://127.0.0.1:8080
                    ▼
   ┌───────────────────────────────────────────────┐
   │        Spring Boot uygulaması (port 8080)      │
   │                                                │
   │  1) JwtAuthenticationFilter                    │  ← her istekte token kontrolü
   │  2) SecurityConfig (yetki kuralları)           │
   │  3) Controller katmanı:                        │
   │       • AuthController   /api/auth/**          │  ← kayıt, giriş, profil
   │       • FileController   /api/files/**         │  ← yükle/listele/indir/sil
   │       • InfoController   /api/info             │  ← API tanıtımı
   │       • HealthController /health, /healthz     │  ← sağlık kontrolü
   │       • TestController   /hello                │  ← kimlik testi
   │  4) Service katmanı (iş mantığı)               │
   │  5) Repository katmanı (veritabanı erişimi)    │
   │  6) Statik sayfalar (index/files/test/guide)   │
   └───────────────┬───────────────────┬────────────┘
                   │                   │
          ┌────────▼────────┐   ┌──────▼─────────────────┐
          │   PostgreSQL    │   │  Disk: /app/uploads    │
          │  users, files   │   │  (yüklenen dosyalar)   │
          └─────────────────┘   └────────────────────────┘
```

### Katmanlı mimari (çok önemli desen)

Backend, klasik **3 katmanlı** yapıyı izler. Bir isteğin izlediği yol:

```
Controller  →  Service  →  Repository  →  Veritabanı
 (HTTP)        (iş mantığı)  (veri erişimi)
```

- **Controller:** HTTP isteğini alır, gövdeyi (body) okur, doğrular, Service'i çağırır, sonucu HTTP cevabına çevirir. (Örn. `AuthController`, `FileController`)
- **Service:** Asıl iş mantığı burada. (Örn. şifreyi hash'le, kullanıcı var mı bak, token üret.) (Örn. `AuthService`, `FileService`)
- **Repository:** Veritabanı işlemleri. Spring Data JPA sayesinde metot ismini yazmak yeterli; SQL'i Spring üretir. (Örn. `UserRepository`, `SharedFileRepository`)

### Paket (klasör) yapısı

```
src/main/java/org/example/secshare/
├── SecshareApplication.java     → uygulamanın başlangıç noktası (main metodu)
├── HealthController.java        → /health, /healthz sağlık kontrolü
├── InfoController.java          → /api/info (API'nin makine-okunur tanıtımı)
├── TestController.java          → /hello (token doğru mu diye test)
│
├── auth/                        → KİMLİK DOĞRULAMA & GÜVENLİK
│   ├── AuthController.java       → /api/auth/register, /login, /me
│   ├── AuthService.java          → kayıt/giriş iş mantığı
│   ├── JwtService.java           → token üret & doğrula
│   ├── JwtAuthenticationFilter.java → her istekte token'ı kontrol eden filtre
│   ├── SecurityConfig.java       → erişim kuralları + BCrypt tanımı
│   ├── AdminSeeder.java          → başlangıçta admin hesabı oluşturma
│   ├── dto/                      → istek/cevap veri kalıpları (kayıt/giriş/profil)
│   └── security/UserPrincipal.java → giriş yapmış kullanıcının kimliği (id + email)
│
├── file/                        → DOSYA İŞLEMLERİ
│   ├── FileController.java        → /api/files/** uç noktaları
│   ├── FileService.java          → yükleme/indirme/silme iş mantığı + kurallar
│   ├── SharedFile.java           → "files" tablosunu temsil eden JPA entity
│   ├── SharedFileRepository.java → dosya veritabanı sorguları
│   └── dto/FileInfoResponse.java → dosya bilgisini dışarı verirken kullanılan kalıp
│
├── user/                        → KULLANICI VERİSİ
│   ├── User.java                 → "users" tablosunu temsil eden JPA entity
│   └── UserRepository.java       → kullanıcı veritabanı sorguları
│
└── config/
    └── DatabaseConfig.java       → DATABASE_URL'i esnek şekilde parse eden DB ayarı
```

---

## 4. İşler nasıl hallediliyor? (Konu konu derinlemesine)

Bu bölüm dokümanın kalbi. Her başlıkta "hangi ihtiyaç, hangi dosyada, nasıl
çözülmüş" anlatılıyor.

### 4.1 Kullanıcı Kaydı (Register)

**Amaç:** Yeni kullanıcı e-posta + şifre ile hesap açsın.

**Akış:**
1. İstemci `POST /api/auth/register` adresine `{ "email": "...", "password": "..." }` gönderir.
2. `AuthController.register()` bu gövdeyi `RegisterRequest` nesnesine dönüştürür.
3. `@Valid` sayesinde **doğrulama** çalışır (`RegisterRequest.java`):
   - `email` → geçerli e-posta formatında ve boş değil.
   - `password` → boş değil, **8–72 karakter** arası.
4. `AuthService.register()` çalışır:
   - Aynı e-posta zaten var mı? Varsa → **409 Conflict** ("Email already exists").
   - Yoksa yeni `User` oluşturulur:
     - `id` → rastgele **UUID**.
     - `email` → küçük harfe çevrilerek saklanır.
     - `passwordHash` → şifre **BCrypt (strength 12)** ile hash'lenir. **Düz şifre asla saklanmaz.**
     - `roles` → `"USER"`.
     - `createdAt` → şu anki zaman.
   - `userRepository.save(user)` ile veritabanına yazılır.
5. Başarılıysa → **201 Created**.

> **Neden UUID?** Ardışık sayı yerine tahmin edilemez kimlik kullanmak, kullanıcı
> ve dosyaların dışarıdan sıralanıp taranmasını zorlaştırır.

### 4.2 Giriş (Login) ve Token Üretimi

**Amaç:** Doğru kimlik bilgisiyle giren kullanıcıya bir JWT token vermek.

**Akış (`AuthService.login()`):**
1. `POST /api/auth/login` ile e-posta + şifre gelir.
2. E-posta ile kullanıcı bulunur. Bulunamazsa → **401 Unauthorized**.
3. `passwordEncoder.matches(gelenŞifre, kayıtlıHash)` ile şifre karşılaştırılır.
   Yanlışsa → **401 Unauthorized**.
   - Not: "kullanıcı yok" ve "şifre yanlış" durumlarının ikisi de aynı 401'i
     döner — bu, saldırganın hangi e-postaların kayıtlı olduğunu anlamasını
     engelleyen iyi bir güvenlik pratiğidir.
4. Doğruysa `JwtService.generateToken()` çağrılır ve token üretilir.
5. Cevap: `{ "accessToken": "eyJhbGciOi..." }`.

### 4.3 JWT Token'ın İçeriği ve Üretimi (`JwtService`)

Token, HMAC-SHA256 ile imzalanır ve şunları içerir:
- `subject` → kullanıcının UUID'si.
- `email` claim → kullanıcının e-postası.
- `roles` claim → kullanıcının rolleri (örn. `["USER"]` veya `["ADMIN","USER"]`).
- `issuedAt` / `expiration` → üretilme ve son geçerlilik zamanı (varsayılan **60 dakika**).

**Güvenlik önlemi:** `JwtService` kurulurken JWT gizli anahtarı (base64 çözülünce)
**en az 32 bayt** değilse uygulama **başlamayı reddeder**. Zayıf anahtar kullanımını
engeller.

**Stateless (durumsuz) çalışma:** Sunucu hiçbir oturum (session) tutmaz. Kimlik
tamamen token'dan gelir. Bu, sunucuları ölçeklemeyi kolaylaştırır ama "token'ı
sunucudan iptal etme" imkânı yoktur — token süresi dolana kadar geçerlidir.

### 4.4 Her İstekte Kimlik Kontrolü (`JwtAuthenticationFilter`)

Bu, her HTTP isteğinde **bir kez** çalışan bir filtredir (`OncePerRequestFilter`):
1. `Authorization` başlığına bakar. `Bearer ` ile başlamıyorsa hiçbir şey yapmadan devam eder (istek kimliksiz sayılır).
2. Token'ı alır, `JwtService.parseAndValidate()` ile **imzasını ve süresini** doğrular.
3. Geçerliyse token'dan `userId`, `email`, `roles` çıkarılır.
4. Roller `ROLE_` önekiyle Spring Security yetkisine çevrilir (örn. `ROLE_ADMIN`).
5. Bir `UserPrincipal(userId, email)` oluşturulur ve `SecurityContextHolder`'a
   yerleştirilir. Böylece Controller'larda `@AuthenticationPrincipal UserPrincipal user`
   ile giriş yapmış kullanıcıya doğrudan erişilir.
6. Token bozuk/süresi geçmişse context temizlenir (kullanıcı kimliksiz kabul edilir).

### 4.5 Yetkilendirme Kuralları (`SecurityConfig`)

Hangi adrese kimin erişebileceği burada tanımlı:

- **Herkese açık (token gerekmez):**
  `/health`, `/healthz`, `/api/auth/register`, `/api/auth/login`, `/api/info`,
  `/`, `/index.html`, `/guide.html`, `/files.html`, `/test.html`, `/error`,
  statik dosyalar (css/js/png/jpg/svg), `/favicon.ico`.
- **Sadece ADMIN rolü:** `/api/files/all`.
- **Geri kalan her şey:** giriş yapmış (authenticated) olmayı gerektirir.

Diğer ayarlar:
- **CSRF kapalı** → API token tabanlı ve stateless olduğu için CSRF korumasına gerek yok.
- **Session politikası: STATELESS** → sunucu oturum tutmaz.
- **`@EnableMethodSecurity`** → metot seviyesinde `@PreAuthorize("hasRole('ADMIN')")` gibi kurallar kullanılabilir (örn. `FileController.listAllFiles()`).
- **BCryptPasswordEncoder(12)** → şifre hash'leme algoritması ve "cost" değeri.

### 4.6 Dosya Yükleme (`FileService.upload`)

**Amaç:** Kullanıcı bir dosyayı güvenli şekilde yükleyebilsin.

**Uygulanan kontroller ve adımlar:**
1. Dosya boş mu? → **400 Bad Request**.
2. Boyut > **50 MB** mı? → **413 Payload Too Large**.
   (Ayrıca Spring'in `multipart.max-file-size=50MB` ayarı da sınır koyar.)
3. Uzantı izinli mi? İzin listesi: `pdf, png, jpg, jpeg, txt, doc, docx, xlsx, zip`.
   Değilse → **400 Bad Request**.
4. Diskteki dosya adı çakışmasın diye yeni ad üretilir: `<yeni-UUID>.<uzantı>`.
5. **Path traversal koruması:** Hedef yolun, izin verilen taban klasörün
   (`baseStoragePath`) içinde kaldığı doğrulanır (`targetPath.startsWith(baseStoragePath)`).
   Böylece `../../etc/passwd` gibi klasör dışına çıkma saldırıları engellenir.
6. Dosya diske kopyalanır.
7. Veritabanına bir `SharedFile` kaydı eklenir:
   - `id`, `owner` (yükleyen kullanıcı), `originalFilename` (kullanıcının verdiği ad),
     `storageFilename` (diskteki UUID'li ad), `contentType`, `sizeBytes`,
     `createdAt`, `deleted=false`.
8. Cevap: dosya bilgisi (`FileInfoResponse`).

> **Diskteki ad ≠ orijinal ad.** Diskte UUID'li ad tutulur (çakışma ve isim
> saldırıları engellenir); indirirken kullanıcıya orijinal ad geri verilir.

### 4.7 Dosya Listeleme / İndirme / Silme + Sahiplik Kontrolü

**Sahiplik kontrolü — kilit güvenlik kuralı** (`getOwnedFileOrThrow`):
Bir dosyaya erişmeden önce her seferinde:
1. Dosya var mı ve silinmemiş mi? Yoksa → **404 Not Found**.
2. Dosyanın sahibi, isteği yapan kullanıcı mı? Değilse → **403 Forbidden**
   ("You do not have access to this file").

Bu kontrol indirme, silme, içerik tipi ve orijinal ad alma işlemlerinin hepsinde
tekrar çalışır. Yani **kimse başkasının dosyasını indiremez/silemez.**

- **Listeleme** (`GET /api/files`): `findByOwnerAndDeletedFalseOrderByCreatedAtDesc`
  ile sadece o kullanıcının, silinmemiş dosyaları, en yeniden eskiye sıralı döner.
- **İndirme** (`GET /api/files/{id}`): Sahiplik doğrulanır, dosya diskten okunur,
  `Content-Disposition: attachment; filename="orijinalad"` başlığıyla döner.
- **Silme** (`DELETE /api/files/{id}`): **Soft-delete** yapılır — kayıt
  `deleted=true` işaretlenir (veritabanından tamamen silinmez), ayrıca fiziksel
  dosya diskten de silinir. Cevap → **204 No Content**.
- **Admin listeleme** (`GET /api/files/all`): Sadece ADMIN rolü; sistemdeki tüm
  (silinmemiş) dosyaları döner.

### 4.8 Admin Hesabı Oluşturma (`AdminSeeder`)

Uygulama başlarken (`CommandLineRunner`) çalışan bir bileşen:
- Ortam değişkenleri `ADMIN_EMAIL` ve `ADMIN_PASSWORD` **ikisi de doluysa** bir
  ADMIN hesabı oluşturur (roller: `ADMIN,USER`).
- İkisinden biri boşsa → seeding atlanır.
- O e-posta zaten varsa → **dokunulmaz** (şifre asla üzerine yazılmaz).
- Şifre BCrypt ile hash'lenir; düz metin loglanmaz/saklanmaz.

Böylece "ilk admin nasıl oluşur?" sorunu, koda gömülü sabit şifre olmadan,
güvenli biçimde çözülür.

### 4.9 Roller ve Yetki Modeli

- Roller `users` tablosunda **virgülle ayrılmış tek bir string** olarak tutulur
  (örn. `"USER"` veya `"ADMIN,USER"`).
- `AuthService.parseRoles()` bu string'i temiz bir listeye çevirir; boşsa
  varsayılan olarak `USER` verir.
- Login sırasında roller token'a yazılır; filtre bunları Spring yetkilerine
  (`ROLE_...`) dönüştürür.

### 4.10 Esnek Veritabanı Bağlantısı (`DatabaseConfig`)

Farklı bulut sağlayıcıları veritabanı adresini farklı biçimlerde verir. Bu sınıf:
- `DATABASE_URL` ortam değişkeni `postgres://kullanıcı:şifre@host:port/db`
  biçimindeyse bunu **parse edip** JDBC formatına (`jdbc:postgresql://...`) çevirir
  ve kullanıcı adı/şifreyi ayıklar (URL-decode ederek).
- `DATABASE_URL` yoksa `application.properties` içindeki standart ayarları kullanır.

Bu sayede aynı imaj Render, Oracle gibi ortamlarda ekstra değişiklik
olmadan çalışabilir.

### 4.11 Hata Yönetimi

İş mantığı hataları `ResponseStatusException` fırlatılarak yönetilir; Spring bunu
doğru HTTP koduna çevirir. Doğrulama hataları (`@Valid`) otomatik **400** döner.
Özet HTTP kodları:

| Kod | Anlamı |
|-----|--------|
| 201 | Kayıt başarılı |
| 204 | Silme başarılı (içerik yok) |
| 400 | Hatalı istek (boş/izinsiz dosya, kısa şifre, geçersiz e-posta) |
| 401 | Yanlış e-posta/şifre (login) |
| 403 | Token yok/geçersiz veya başkasının dosyası |
| 404 | Dosya bulunamadı |
| 409 | E-posta zaten kayıtlı |
| 413 | Dosya 50 MB'tan büyük |

---

## 5. API Referansı (uç noktalar)

Temel adres: `http://localhost:8080`
Kimlik başlığı: `Authorization: Bearer <accessToken>`

| Metot | Yol | Yetki | Açıklama |
|-------|-----|-------|----------|
| POST | `/api/auth/register` | ✗ | Kayıt. Gövde: `{email, password}`. → 201 |
| POST | `/api/auth/login` | ✗ | Giriş. Gövde: `{email, password}`. → `{accessToken}` |
| GET | `/api/auth/me` | ✓ | Giriş yapmış kullanıcının profili (id, email, roller, kayıt tarihi) |
| GET | `/hello` | ✓ | Kimlik testi. → `Hello <email>` |
| POST | `/api/files/upload` | ✓ | `multipart/form-data` içinde `file` alanı ile yükleme |
| GET | `/api/files` | ✓ | Kendi dosyalarını listele |
| GET | `/api/files/{id}` | ✓ | Dosya indir (sadece sahibi) |
| DELETE | `/api/files/{id}` | ✓ | Dosya sil (sadece sahibi). → 204 |
| GET | `/api/files/all` | ✓ ADMIN | Tüm dosyalar (yalnızca ADMIN) |
| GET | `/api/info` | ✗ | API'nin makine-okunur tanıtımı |
| GET | `/health`, `/healthz` | ✗ | Sağlık kontrolü. → `{"status":"ok"}` |

### Örnek uçtan uca deneme (curl)

```bash
# 1) Kayıt
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"password123"}'

# 2) Giriş → token al
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"password123"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

# 3) Dosya yükle
echo "merhaba secshare" > ornek.txt
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" -F "file=@ornek.txt"

# 4) Dosyaları listele
curl http://localhost:8080/api/files -H "Authorization: Bearer $TOKEN"

# 5) İndir
curl http://localhost:8080/api/files/<FILE_ID> \
  -H "Authorization: Bearer $TOKEN" -O -J

# 6) Sil
curl -X DELETE http://localhost:8080/api/files/<FILE_ID> \
  -H "Authorization: Bearer $TOKEN"
```

---

## 6. Veritabanı Şeması

Şema, Hibernate tarafından `ddl-auto=update` ile otomatik oluşturulur (uygulama
başlarken entity'lere göre eksik tablo/sütunları ekler).

### `users` tablosu (`User.java`)
| Sütun | Tip | Not |
|-------|-----|-----|
| `id` | UUID | Birincil anahtar |
| `email` | text | Benzersiz (unique), boş olamaz |
| `password_hash` | text | BCrypt hash, boş olamaz |
| `roles` | text | Virgülle ayrılmış roller (örn. `USER`, `ADMIN,USER`) |
| `created_at` | timestamp | Kayıt zamanı |

### `files` tablosu (`SharedFile.java`)
| Sütun | Tip | Not |
|-------|-----|-----|
| `id` | UUID | Birincil anahtar |
| `owner_id` | UUID | `users.id`'e bağlı (ManyToOne, boş olamaz) |
| `original_filename` | text | Kullanıcının yüklediği orijinal ad |
| `storage_filename` | text | Diskteki UUID'li ad (benzersiz) |
| `content_type` | text | MIME tipi |
| `size_bytes` | bigint | Boyut |
| `created_at` | timestamp | Yüklenme zamanı |
| `deleted` | boolean | Soft-delete işareti |

---

## 7. Yapılandırma (Ortam Değişkenleri)

`application.properties` bu değişkenleri okur (Docker Compose `.env`'den besler):

| Değişken | Varsayılan | Açıklama |
|----------|-----------|----------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/secshare` | Veritabanı adresi |
| `SPRING_DATASOURCE_USERNAME` | — | DB kullanıcı (`DB_USER`) |
| `SPRING_DATASOURCE_PASSWORD` | — | DB şifresi (`DB_PASSWORD`) |
| `DATABASE_URL` | — | (Varsa) `postgres://...` formatını otomatik parse eder |
| `JWT_SECRET` | (dev varsayılanı) | Base64, çözülünce ≥ 32 bayt olmalı |
| `JWT_EXPIRATION_MINUTES` | `60` | Token ömrü (dakika) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | boş | Doluysa başlangıçta admin oluşturur |
| `STORAGE_PATH` | `uploads` (Docker'da `/app/uploads`) | Dosyaların saklandığı klasör |
| `PORT` | `8080` | HTTP portu |
| `LOG_LEVEL` | `INFO` | Spring Security log seviyesi |

> `JWT_SECRET` üretmek için: `openssl rand -base64 32`

---

## 8. Çalıştırma (Docker ile)

Ön koşul: Docker + Docker Compose v2.

```bash
# 1) Ortam değişkenlerini hazırla
cp .env.example .env
# .env içinde DB_PASSWORD, JWT_SECRET (ve istenirse ADMIN_*) doldur.

# 2) Uygulama + PostgreSQL'i birlikte başlat
docker compose up -d --build

# 3) Logları izle
docker compose logs -f app
```

"Started SecshareApplication" satırını görünce hazırdır.

Tarayıcıda:
- `http://localhost:8080/` — açılış sayfası
- `http://localhost:8080/files.html` — dosya yönetim arayüzü
- `http://localhost:8080/test.html` — hızlı test arayüzü

Yönetim komutları:
```bash
docker compose ps                # durum
docker compose down              # durdur (veri korunur)
docker compose down -v           # durdur + DB & dosyaları sil
docker compose up -d --build     # kod değişince yeniden derle
```

### Docker imajı nasıl kuruluyor? (`Dockerfile`)

**Çok aşamalı (multi-stage) build:**
1. **Build aşaması** (`eclipse-temurin:17-jdk-alpine`): Maven wrapper ile önce
   bağımlılıklar indirilir (`dependency:go-offline` — katman önbelleği için),
   sonra `mvnw clean package -DskipTests` ile `.jar` üretilir.
2. **Çalıştırma aşaması** (`eclipse-temurin:17-jre-alpine`): Sadece üretilen
   `.jar` kopyalanır ve `java -jar app.jar` ile başlatılır.

> Neden iki aşama? Son imaj yalnızca JRE + jar içerir; JDK, Maven, kaynak kod
> gibi ağırlıkları taşımaz → **küçük ve hızlı imaj**.

### Docker Compose ne yapıyor? (`docker-compose.yml`)

- `db` servisi: PostgreSQL 16. Verisi `pgdata` volume'ünde kalıcı. **Host'a port
  açmaz** — sadece uygulama container'ı erişebilir. `pg_isready` ile healthcheck.
- `app` servisi: Uygulamayı derler/çalıştırır. `depends_on ... service_healthy`
  ile **veritabanı hazır olmadan başlamaz**. Yüklenen dosyalar `uploads`
  volume'ünde kalıcı. Port yalnızca `127.0.0.1:8080`'e bağlanır (dışarıya kapalı;
  önüne nginx/TLS koymak beklenir).

---

## 9. Dağıtım (Deployment) & CI/CD

### GitHub Actions (`.github/workflows/deploy.yml`)

`main` dalına her push olduğunda:
1. `appleboy/ssh-action` ile Oracle Cloud sunucusuna **SSH** bağlanır.
2. Sunucuda `~/secshare` klasörüne gidip `git fetch` + `git reset --hard origin/main`
   ile kodu **origin/main ile birebir eşitler**.
3. `docker-compose up -d --build` ile yeniden derleyip yeniden başlatır.

Gerekli GitHub Secrets: `SSH_HOST`, `SSH_PRIVATE_KEY`.

### Alternatif hedefler
- **Render** (`render.yaml`): Docker imajı + yönetilen PostgreSQL; `JWT_SECRET`
  Render tarafından otomatik üretilir; `STORAGE_PATH` proje diskine ayarlanır.

---

## 10. Güvenlik Önlemleri Özeti

Projede uygulanan başlıca güvenlik pratikleri:

- ✅ **Şifreler BCrypt (cost 12)** ile hash'lenir; düz metin asla saklanmaz.
- ✅ **JWT imzalı** ve süreli (varsayılan 60 dk); gizli anahtar ≥ 32 bayt zorunlu.
- ✅ **Stateless** mimari; sunucu oturum tutmaz.
- ✅ **Sahiplik kontrolü:** kimse başkasının dosyasına erişemez (403).
- ✅ **Path traversal koruması:** dosya yolu taban klasör dışına çıkamaz.
- ✅ **Uzantı allow-list** + **boyut limiti (50 MB)**.
- ✅ **Login'de bilgi sızdırmama:** "kullanıcı yok" ve "şifre yanlış" aynı 401.
- ✅ **UUID kimlikler:** tahmin edilerek taranamaz.
- ✅ **Admin hesabı** koda gömülü sabit şifre olmadan, ortam değişkeninden seed edilir.
- ✅ **DB portu dışarı kapalı**, app portu yalnızca localhost'a bağlı.
- ✅ **Soft-delete:** kayıt izlenebilir kalır, fiziksel dosya diskten silinir.

---

## 11. Test Durumu

`src/test/.../SecshareApplicationTests.java` içinde tek bir `contextLoads` testi
var ama `@Disabled` ile **devre dışı**. Yani şu an otomatik test kapsamı yok;
doğrulama manuel (curl / arayüz) yapılıyor.

---

## 12. Sık Karşılaşılan Sorunlar

- **Uygulama başlamıyor / DB hatası:** `docker compose logs db` ve `... app`'e
  bakın. DB `Up (healthy)` olana kadar app başlamaz.
- **`/` yerine 404:** Kök rota içerik döndürmüyorsa `/index.html`, `/files.html`
  veya `/test.html` deneyin.
- **Upload 400 dönüyor:** Uzantı izin listesinde mi? (`pdf, png, jpg, jpeg, txt,
  doc, docx, xlsx, zip`) ve dosya boş olmamalı.
- **İstek 403 dönüyor:** Token eksik/süresi geçmiş olabilir; tekrar giriş yapın.
- **Uygulama başlamıyor + "JWT secret must be at least 32 bytes":** `JWT_SECRET`
  zayıf. `openssl rand -base64 32` ile yeniden üretin.
- **Port çakışması:** 8080 veya 5432 doluysa `docker-compose.yml`'de port
  eşlemesini değiştirin.

---

## 13. Teknoloji Özeti (tek bakışta)

- **Backend:** Java 17, Spring Boot 3.4.2 (Web, Security, Data JPA, Validation)
- **Güvenlik:** Spring Security, JWT (jjwt 0.12.5), BCrypt (strength 12)
- **Veri:** Spring Data JPA + Hibernate + PostgreSQL 16 (`ddl-auto=update`)
- **Depolama:** Yerel dosya sistemi (`/app/uploads`, Docker volume ile kalıcı)
- **Frontend:** Vanilla HTML/CSS/JS (statik sayfalar) — *React/Vite değil*
- **Paketleme:** Docker (multi-stage) + Docker Compose
- **CI/CD:** GitHub Actions → Oracle Cloud (SSH deploy)
- **Alan adı / önyüz:** DuckDNS + Nginx (HTTPS/TLS)
```
