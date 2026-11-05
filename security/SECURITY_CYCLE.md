# Haftalık Güvenlik Döngüsü — SecShare

Bu belge dönem projesinin haftalık çalışma yöntemidir. Hoca yönergesi:
**her hafta aynı döngü → AI güvenlik testi → triage → fix → tag → submit.**
Asıl teslim edilen şey AI'ın çıktısı değil, **o çıktı hakkındaki yargımızdır.**

> Not: Bu dosya insan ekibi içindir. Claude'un test sırasında uyması gereken
> kısa kurallar `CLAUDE.md` içindedir.

---

## Haftalık döngü (6 adım)

1. **Build** — Küçük ve **bitmiş** bir şey yap (*small & finished > large & broken*).
   `./mvnw test` yeşil olmalı. Hafta boyunca **parça parça commit** et (tek "dump" commit
   not kaybettirir).
2. **Freeze** — Commit'le ve `wNN` tag'i at (ör. `w01`). AI testi tam bu tag'e karşı koşar.
3. **5 standart AI promptu** — Tag'e karşı sırayla çalıştır, ham çıktıyı `ai/wNN/`'a yaz
   (**düzenlenmez**, AI'ın yanıldığı yerler dahil):
   1. **Attack surface** — giriş noktaları, güven sınırları, varlıklar
   2. **Control smoke test** — her güvenlik kontrolü gerçekten çalışıyor mu
   3. **Regression** — önceki fix'ler hâlâ duruyor mu, yeni değişiklik bir şey açtı mı
   4. **Diff review** — bu tag'in getirdiği diff'te yeni hata var mı
   5. **Abuse cases** — özelliğin kötüye kullanımı
4. **Triage** — Her bulguyu sınıflandır: `GERÇEK / YANLIŞ-ALARM / TEKRAR / WONTFIX`.
5. **MISS avı** — AI'ın **kaçırdığı en az bir gerçek açığı** kendin bul (`MISS-nn`).
6. **Fix + re-run + tag + submit** — Gerçek bulguları (ve MISS'i) düzelt + regresyon testi,
   testleri tekrar koş, tag'i güncel koda al, teslim et.

---

## Klasör yapısı

```
ai/
  w01/
    transcript.md      # 5 promptun HAM AI çıktısı — hiç düzenlenmez (yönerge: ai/)
  w02/ ...
security/
  SECURITY_CYCLE.md    # bu belge
  SECURITY_WRITEUP.md  # kümülatif güvenlik yazısı
  w01/
    README.md          # hafta özeti + teslim checklist
    triage.md          # bulgu sınıflandırma + gerekçeler
    misses.md          # MISS-nn kayıtları (AI'ın kaçırdıkları)
    critique.md        # AI çıktısı hakkındaki yargımız (notun en büyük farkı)
  w02/ ...
```

---

## Haftalık teslim (yönerge)

- **Private repo** + instructor collaborator (public olmaz, tek-dump commit not kaybettirir).
- `wNN` git tag'i.
- `ai/wNN/` içinde **verbatim** AI transcript (yanıldığı yerler dahil).
- **1 sayfalık PDF critique** → EgeDers, **Çarşamba 23:59**.
- Demo: hoca bir dosya seçip açıklatır — *"bunu AI yazdı"* geçersiz cevaptır; kodu anla.

---

## Triage tablosu şablonu (`triage.md`)

| ID | Bulgu | AI'ın iddiası | Sınıf | Gerekçe (neden?) | Aksiyon |
|----|-------|---------------|-------|------------------|---------|
| F-01 | ... | ... | GERÇEK | Doğru, çünkü ... | Fix + test |
| F-02 | ... | ... | YANLIŞ-ALARM | Yanılıyor, çünkü ... | Yok |
| F-03 | ... | ... | TEKRAR | F-01 ile aynı | Yok |
| F-04 | ... | ... | WONTFIX | Gerçek ama ... (risk kabul) | Belgelendi |

**Sınıf tanımları**
- **GERÇEK** — Sömürülebilir, kanıtlanabilir. Reprodüksiyon adımı yaz.
- **YANLIŞ-ALARM** — AI yanıldı. *Neden yanıldığını* açıkla (eksik bağlam, yanlış varsayım...).
- **TEKRAR** — Başka bir bulgunun kopyası / aynı kök neden.
- **WONTFIX** — Gerçek ama düzeltmiyoruz; riski neden kabul ettiğini yaz.

---

## MISS kayıt formatı (`misses.md`)

Her hafta **en az bir** `MISS-nn`. AI'ın kaçırdığı gerçek açık.

```
### MISS-01 — <kısa başlık>
- **Sınıf/CWE:** CWE-xxx
- **Nerede:** <dosya:satır veya endpoint>
- **AI neden kaçırdı:** <ör. sadece diff'e baktı, auth katmanını görmedi>
- **Reprodüksiyon:** <adımlar>
- **Fix:** <ne yapıldı> + <regresyon testi>
```

---

## Kritik: "yargı" dosyası (`critique.md`)

Slaytın asıl mesajı burası. Her hafta AI'ın çıktısı hakkında:
- AI'ın **doğru bulduğu** en güçlü bulgu — neden doğru?
- AI'ın **yanıldığı** bir nokta — neden yanıldı?
- AI'ın **kaçırdığı** (`MISS-nn`) — neden kaçırmış olabilir?
- Genel değerlendirme: bu hafta AI ne kadar işe yaradı?

---

## Git tag konvansiyonu

```
git tag -a week-NN -m "Week NN: <kısa özet, kaç bulgu fix>"
git push origin week-NN
```

---

## Teslim checklist (her hafta)

- [ ] `./mvnw test` yeşil
- [ ] `transcript.md` ham ve düzenlenmemiş
- [ ] `triage.md` her bulgu sınıflandırılmış + gerekçeli
- [ ] En az 1 `MISS-nn` kaydı var
- [ ] `critique.md` dolu
- [ ] Gerçek bulgular fix + regresyon testiyle kapatıldı
- [ ] `git tag week-NN` atıldı
