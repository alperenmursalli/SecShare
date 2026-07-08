# Render - Manuel Environment Variables Kurulumu

## PostgreSQL Bağlantı Bilgilerini Bul

1. Render Dashboard → PostgreSQL servisine git
2. **Connections** veya **Info** sekmesine bak
3. Şu bilgileri bul:
   - **Internal Database URL** veya **Connection String**
   - **Host**
   - **Port** (genellikle 5432)
   - **Database Name**
   - **User**
   - **Password**

## Web Service'e Environment Variables Ekle

1. Render Dashboard → Web Service'e git
2. **Environment** sekmesine tıkla
3. **Add Environment Variable** butonuna tıkla

### 1. DATABASE_URL (Zorunlu)

**Key**: `DATABASE_URL`

**Value**: PostgreSQL sayfasındaki **Internal Database URL**'i kopyala

Format genellikle şöyle olur:
```
postgres://user:password@host:port/database
```

Örnek:
```
postgres://secshare_user:abc123xyz@dpg-xxxxx-a.oregon-postgres.render.com:5432/secshare_db
```

### 2. DB_USERNAME (Opsiyonel - DATABASE_URL varsa gerekmez)

**Key**: `DB_USERNAME`

**Value**: PostgreSQL kullanıcı adı

### 3. DB_PASSWORD (Opsiyonel - DATABASE_URL varsa gerekmez)

**Key**: `DB_PASSWORD`

**Value**: PostgreSQL şifresi

### 4. JWT_SECRET (Zorunlu)

**Key**: `JWT_SECRET`

**Value**: Güçlü bir base64 secret

**Oluşturma:**
```bash
openssl rand -base64 32
```

Örnek çıktı:
```
aBc123XyZ456DeF789GhI012JkL345MnO678PqR901StU234VwX567YzA890
```

### 5. STORAGE_PATH (Opsiyonel)

**Key**: `STORAGE_PATH`

**Value**: `/opt/render/project/src/uploads`

### 6. PORT (Otomatik)

Render otomatik sağlar, ekleme.

## Özet - Eklenmesi Gerekenler

✅ `DATABASE_URL` - PostgreSQL Internal Database URL
✅ `JWT_SECRET` - Güçlü secret (openssl rand -base64 32)
✅ `STORAGE_PATH` - `/opt/render/project/src/uploads` (opsiyonel)

## Kontrol

Environment variables eklendikten sonra:
1. **Save Changes** butonuna tıkla
2. Render otomatik deploy edecek
3. **Logs** sekmesinden kontrol et:
   - `DATABASE_URL parse edildi` mesajını görmelisin
   - Veritabanı bağlantı hatası olmamalı

## Sorun Giderme

### DATABASE_URL bulamıyorum

PostgreSQL sayfasında:
- **Info** sekmesine bak
- **Connections** sekmesine bak
- **Internal Database URL** veya **Connection String** ara

### Hala bağlantı hatası

Logları kontrol et:
- `DATABASE_URL parse edilemedi` hatası varsa format yanlış olabilir
- Format: `postgres://user:password@host:port/database` olmalı
