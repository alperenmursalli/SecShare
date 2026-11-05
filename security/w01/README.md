# w01 — Burn-after-reading atomikliği

> Döngü adımları ve dosya açıklamaları: [`../SECURITY_CYCLE.md`](../SECURITY_CYCLE.md)

## Bu haftanın konusu

Tek kullanımlık ("imha") paylaşımların eşzamanlı isteklerde **iki kez** servis
edilebildiği race condition'ı (CWE-362 / CWE-367). Burn-after-reading garantisinin
temel ihlali. Build = bu race'in fix'i; sonra tag `w01`'e karşı 5 standart prompt koştu.

## Özet sonuç

| Kaynak | Bulgu | Sınıf | Durum |
|--------|-------|-------|-------|
| AI (önceki oturum, `IMPROVEMENTS.md` #1) | Link indirme race'i (`resolveLinkForDownload`) | GERÇEK | ✅ Fix |
| **Biz — MISS-01** | Aynı race audience yolunda (`recordAudienceOpenAndMaybeBurn`); FIRST'te çift servis | GERÇEK | ✅ Fix |
| 5-prompt AI (bu hafta) | F-2 rate limit yok · F-7 tarama dar · F-4/F-1/F-8/F-5 düşük | GERÇEK | backlog/ayrı hafta |
| 5-prompt AI (yanıldı) | F-6 email spoofing · F-10 spam relay · F-3 filename XSS | YANLIŞ-ALARM | — |
| **Biz — MISS-02** | 5-prompt'un kaçırdığı: USER-grant authenticated burn yolu hâlâ açıktı | GERÇEK | ✅ Fix |
| Biz | ALL-modu purge-vs-serve + USER-ALL burn-timing edge | GERÇEK | ⬜ Week 02 |

Detay: `triage.md` (10 bulgu sınıflandırma), `misses.md` (MISS-01/02), `critique.md`.

## Uygulanan fix (burn race)

- **Link yolu:** `FileShareRepository.claimDownload` — atomik koşullu `UPDATE` + satır
  kilidi; kaybeden `410`.
- **Audience yolu (MISS-01):** `claimRevoke` / `claimFirstOpen` / `incrementOpenedCount`
  + canlı `currentOpenedCount`; FIRST'te kaybeden `410`.
- Regresyon: `FileShareE2ETest`'te 2 concurrency testi (8 eşzamanlı → tam 1 adet 2xx).

## Dosyalar

- `../../ai/w01/transcript.md` — 5 promptun ham AI çıktısı (düzenlenmez)
- `triage.md` · `misses.md` · `critique.md`

## Teslim checklist

- [x] Build küçük & bitmiş, `./mvnw test` yeşil (16/16)
- [x] `ai/w01/transcript.md` ham (5 prompt, düzenlenmemiş)
- [x] `triage.md` gerekçeli (GERÇEK/YANLIŞ-ALARM/WONTFIX)
- [x] En az 1 `MISS-nn` (MISS-01 + MISS-02, ikisi de fix)
- [x] `critique.md` dolu
- [x] Burn race fix (link + audience + USER-grant) + 3 regresyon testi
- [ ] Repo **private** + instructor collaborator
- [ ] `git tag -a w01`
- [ ] 1 sayfalık PDF critique → EgeDers (Çarşamba 23:59)
