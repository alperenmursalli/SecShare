# Haftalık Güvenlik Döngüsü — SecShare

Bu belge dönem projesinin haftalık çalışma yöntemidir. Hoca yönergesi:
**her hafta aynı döngü → AI güvenlik testi → triage → fix → tag → submit.**
Asıl teslim edilen şey AI'ın çıktısı değil, **o çıktı hakkındaki yargımızdır.**

> Not: Bu dosya insan ekibi içindir. Claude'un test sırasında uyması gereken
> kısa kurallar `CLAUDE.md` içindedir.

---

## Haftalık döngü (6 adım)

1. **Build** — Çalışan sürümü güncelle, `./mvnw test` yeşil olmalı.
2. **AI güvenlik testi** — Claude'a "bunu saldırgan gözüyle test et" de.
   Ham çıktı (transcript) olduğu gibi saklanır, **düzenlenmez.**
3. **Triage** — Her bulguyu sınıflandır: `GERÇEK / YANLIŞ-ALARM / TEKRAR / WONTFIX`.
4. **Fix** — Gerçek bulguları düzelt + her biri için regresyon testi yaz.
5. **MISS avı** — AI'ın **kaçırdığı en az bir gerçek açığı** kendin bul (`MISS-nn`).
6. **Tag + Submit** — `git tag week-NN` at, teslim paketini oluştur.

---

## Klasör yapısı

Her hafta için:

```
security/
  week-01/
    transcript.md      # AI'ın HAM çıktısı — hiç düzenlenmez
    triage.md          # bulgu sınıflandırma tablosu + gerekçeler
    misses.md          # MISS-nn kayıtları (AI'ın kaçırdıkları)
    critique.md        # AI çıktısı hakkındaki kendi yargımız
  week-02/
    ...
```

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
