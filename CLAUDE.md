# SecShare

Spring Boot + PostgreSQL güvenli dosya paylaşım servisi. Dönem boyunca haftalık
güvenlik döngüsüyle sertleştiriliyor. Süreç: `security/SECURITY_CYCLE.md`.

## Güvenlik testi kuralları (bana talimat)

"Saldırgan gözüyle test et" denildiğinde:

1. **Ham çıktıyı bozma.** Bulgularımı transcript olarak `ai/wNN/transcript.md` altında,
   olduğu gibi üret. Sonradan "temizlenmiş" versiyon istemeyi kullanıcıya bırak.
2. **Her bulgu şu formatta:** başlık · CWE sınıfı · severity · dosya:satır / endpoint ·
   reprodüksiyon adımları · önerilen fix · regresyon testi fikri.
3. **Emin olmadığımda "emin değilim" de.** Spekülatif bulguyu gerçek gibi sunma;
   kullanıcı bunları triage'da YANLIŞ-ALARM olarak eleyecek, dürüstlük önemli.
4. **Sadece diff'e değil, auth/erişim katmanına bak.** MISS'ler genelde orada çıkıyor.
5. **Fix yaparken** her gerçek bulgu için regresyon testi ekle; `./mvnw test` yeşil kalmalı.

## Test / çalıştırma

- Testler: `./mvnw test` (E2E'ler Testcontainers Postgres kullanır).
- Teslim paketi ve klasör yapısı: `security/SECURITY_CYCLE.md`.
