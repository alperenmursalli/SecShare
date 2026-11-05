# w01 — AI çıktısı hakkındaki yargımız

> Dersin asıl teslimi burası: not AI'ın çıktısına değil, o çıktı hakkındaki yargımıza
> veriliyor. Kaynak: `../../ai/w01/transcript.md` (5 standart prompt, tag `w01`).

## AI'ın doğru bulduğu en güçlü bulgular — neden doğru?

- **F-2 (rate limiting yok):** En değerli bulgu. Doğru, çünkü `w01` build'inde
  `RateLimitFilter` zincire eklenmiş değil (branch merge edilmemiş); `login`/`register`/
  `verify` ve public token uçları ölçümsüz. AI'ın build'in gerçek durumunu (branch'in
  içermediğini) fark etmesi isabetli.
- **F-7 (tarama dar + in-memory):** Doğru. `BuiltInFileScanner` yalnız pdf/png/jpg magic
  kontrolü + exec/EICAR yapıyor; zip/docx/xlsx içerik denetlenmiyor ve `file.getBytes()`
  tüm dosyayı belleğe alıyor. Somut dosya:satır verdi.
- **F-4 (Content-Disposition), F-8 (user enumeration), F-1 (JWT revocation yok), F-5
  (ALL-modu purge penceresi):** Hepsi gerçek ama düşük; AI severity'leri makul verdi.

## AI'ın yanıldığı noktalar — neden yanıldı?

- **F-6 (email-claim spoofing) — YANLIŞ.** AI "exploitable" dedi; değil. JWT HS256 ile
  imzalı, `email` claim'i secret olmadan forge edilemez ve ayrıca `emailVerified` şartı
  var. AI muhtemelen "email tabanlı authz" kalıbını görüp otomatik "spoofable" etiketi
  yapıştırdı — imza katmanını dikkate almadı. Klasik AI aşırı-genellemesi.
- **F-10 (spam relay) — ABARTILI.** Gerçek bir risk çekirdeği var (kimliksiz mail) ama
  "open relay" değil: owner authenticated olmalı, `MAX_AUDIENCE_MEMBERS` cap var, mail
  yalnız configure edilmişse çıkar. AI etki büyüklüğünü şişirdi.
- **F-3 (filename stored XSS) — YANLIŞ (kendi kabul etti).** AI "frontend'i görmedim"
  deyip medium verdi; kontrol edince `share.html:220` `textContent` kullanıyor → XSS yok.
  Dürüst ama eksik (sunucu-only bakış).

## AI'ın kaçırdığı — MISS-02, neden kaçırdı?

Burn-after-reading race'inin **authenticated USER-grant yolu**
(`recordAuthenticatedAccessAndMaybeBurn`, satır 475-488). AI bunu kaçırdı çünkü:
- **Prompt 4 (diff review)** kendi ağzıyla "code not touched by the diff was out of scope"
  dedi — yani kök nedenin diff'e girmeyen üçüncü kopyasını hiç aramadı.
- **Prompt 2 (control smoke test)** burn'ü yalnız "link path" olarak doğruladı; authenticated
  USER path'i es geçti.

Kritik ders: **AI'ın "diff temiz, fix tam" demesi, aynı bug pattern'inin başka çağrı
yollarında olmadığı anlamına gelmez.** Diff-kapsamlı inceleme, kök-neden kapsamlı
inceleme değildir. Bu MISS'i diff dışına çıkıp "aynı FIRST-mode burn mantığı başka nerede
tekrar ediyor?" diye sorarak bulduk.

## Genel değerlendirme

5-promptluk geçiş iyi bir **kapsam haritası** ve birkaç gerçek düşük/orta bulgu verdi
(F-2 ve F-7 değerli). Ama iki sistematik zaafı var: (1) **imza/kripto katmanını atlayıp**
yüzeysel kalıp eşleştirme (F-6), (2) **diff/path-kapsamlı düşünme** yüzünden kök-nedenin
tekrar eden kopyalarını kaçırma (MISS-02). İkisi de "AI'a güven ama kök-neden ve tüm çağrı
yollarında doğrula" dersini pekiştiriyor. Triage'da 10 bulgunun 4'ü elendi (YANLIŞ-ALARM/
WONTFIX) — sinyal/gürültü oranı orta.
