# Week 01 — AI çıktısı hakkındaki yargımız

> Dersin asıl teslimi burası: not AI'ın çıktısına değil, o çıktı hakkındaki
> yargımıza veriliyor.

## AI'ın doğru bulduğu en güçlü bulgu — neden doğru?

Burn-after-reading race'i (F-01). AI haklıydı çünkü:
- PostgreSQL varsayılanı READ COMMITTED; `findByTokenAndRevokedFalse` ile
  `revoked=true` yazımı arasında satır kilidi yoktu, yani iki eşzamanlı istek
  aynı `revoked=false` snapshot'ını görebilir.
- Dahası kodun kendi yorumu bunu zaten "best-effort against races" diye itiraf
  ediyordu — yani teorik değil, bilinen ve kabul edilmiş bir açıktı.
- Önerdiği çözümlerden (atomik koşullu UPDATE / pessimistic lock / @Version)
  ilkini uyguladık; `affectedRows == 1` kontrolü race'i kesin kapatıyor.

## AI'ın yanıldığı nokta — neden yanıldı?

Önceki değerlendirmede "email verification yok" (F-03) demişti. Yanıldı çünkü
muhtemelen yalnızca register akışına baktı, oysa doğrulama ayrı bir katmanda
(`User.emailVerified` + `GET /api/auth/verify` + erişim kontrolünde
`getAccessibleFileOrThrow` kontrolü) zaten vardı. AI'ın kendisi sonradan
IMPROVEMENTS.md notunda bu maddeyi "geçersiz" diye geri çekti — yani tek bir
dosyaya/diff'e bakıp bütünü ıskalama hatası.

## AI'ın kaçırdığı — MISS-01, neden kaçırmış olabilir?

Audience yolundaki aynı race'i (`recordAudienceOpenAndMaybeBurn`). İlginç olan:
AI onu *teşhis ederken* listelemişti ama fix'i uygularken kapatmadı — dikkatini
en belirgin/public çağrı yoluna (`resolveLinkForDownload`) verip "burn atomikliği
halledildi" sonucuna atladı. Ders açısından kritik çıkarım: **AI'ın "çözdüm"
demesi, kök nedenin tüm çağrı yollarında kapandığı anlamına gelmez.** Aynı
pattern'in ikinci kopyasını insan olarak biz yakaladık.

## Genel değerlendirme

Bu hafta AI iyi bir teşhisçiydi (gerçek ve bilinen bir açığı net gerekçelendirdi,
makul çözüm seti sundu) ama **eksik kapsama** eğilimli: hem yanlış-alarmı
(email) hem de kısmi-fix'i (audience race) bütünü değil parçayı görmekten
kaynaklandı. Alınan ders: AI'ın bulgusunu kök-neden düzeyinde ve tüm çağrı
yollarında doğrula.
