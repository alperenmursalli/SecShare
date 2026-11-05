# Week 01 — Burn-after-reading atomikliği

> Döngü adımları ve dosya açıklamaları: [`../SECURITY_CYCLE.md`](../SECURITY_CYCLE.md)

## Bu haftanın konusu

Tek kullanımlık ("imha") paylaşım linklerinin eşzamanlı isteklerde **iki kez**
servis edilebildiği race condition'ı (CWE-362 / CWE-367). Burn-after-reading
garantisinin temel ihlali.

## Özet sonuç

| Kaynak | Bulgu | Sınıf | Durum |
|--------|-------|-------|-------|
| AI (dün, `IMPROVEMENTS.md` #1) | Public link indirme race'i (`resolveLinkForDownload`) | GERÇEK | ✅ Fix edildi |
| **Biz (MISS-01)** | Aynı race audience/grup yolunda (`recordAudienceOpenAndMaybeBurn`); FIRST modunda dosya eşzamanlı iki kez servis edilebiliyordu | GERÇEK | ✅ Fix edildi |
| Biz (MISS-02 adayı) | ALL modu purge-vs-serve dar penceresi | GERÇEK | ⬜ Week 02 |

## Uygulanan fix (link yolu)

`FileShareRepository.claimDownload` — atomik koşullu `UPDATE` + satır-yazma kilidi.
İki eşzamanlı istek kilitte serileşir; tam biri terminal indirmeyi kazanır ve
`revoked=true` yapar, diğerinin UPDATE'i 0 satır etkiler ve `410` ile reddedilir.
(`FileShareService.resolveLinkForDownload`, `FileShareRepository.java`)

## Dosyalar

- `transcript.md` — AI'ın ham çıktısı (düzenlenmez)
- `triage.md` — bulgu sınıflandırma + gerekçeler
- `misses.md` — AI'ın kaçırdığı/kapatmadığı gerçek açık (MISS-01)
- `critique.md` — AI çıktısı hakkındaki yargımız

## Teslim checklist

- [x] `./mvnw test` yeşil (17/17)
- [ ] `transcript.md` ham ve düzenlenmemiş (dünkü oturum dökümü yapıştırılacak)
- [x] `triage.md` gerekçeli
- [x] En az 1 `MISS-nn` (MISS-01, fix edildi)
- [x] `critique.md` dolu
- [x] Link-yolu + audience-yolu race'i fix + regresyon testleri
- [ ] `git tag -a week-01`
