# Render — Manual Environment Variables Setup

## Find your PostgreSQL connection details

1. Render Dashboard → go to your PostgreSQL service
2. Look at the **Connections** or **Info** tab
3. Find the following:
   - **Internal Database URL** or **Connection String**
   - **Host**
   - **Port** (usually 5432)
   - **Database Name**
   - **User**
   - **Password**

## Add environment variables to the Web Service

1. Render Dashboard → go to your Web Service
2. Click the **Environment** tab
3. Click the **Add Environment Variable** button

### 1. DATABASE_URL (Required)

**Key**: `DATABASE_URL`

**Value**: Copy the **Internal Database URL** from the PostgreSQL page

The format is usually:
```
postgres://user:password@host:port/database
```

Example:
```
postgres://secshare_user:abc123xyz@dpg-xxxxx-a.oregon-postgres.render.com:5432/secshare_db
```

### 2. DB_USERNAME (Optional — not needed if DATABASE_URL is set)

**Key**: `DB_USERNAME`

**Value**: PostgreSQL username

### 3. DB_PASSWORD (Optional — not needed if DATABASE_URL is set)

**Key**: `DB_PASSWORD`

**Value**: PostgreSQL password

### 4. JWT_SECRET (Required)

**Key**: `JWT_SECRET`

**Value**: A strong base64 secret

**Generate:**
```bash
openssl rand -base64 32
```

Example output:
```
aBc123XyZ456DeF789GhI012JkL345MnO678PqR901StU234VwX567YzA890
```

### 5. STORAGE_PATH (Optional)

**Key**: `STORAGE_PATH`

**Value**: `/opt/render/project/src/uploads`

### 6. PORT (Automatic)

Render provides this automatically, don't add it.

## Summary — what to add

✅ `DATABASE_URL` — PostgreSQL Internal Database URL
✅ `JWT_SECRET` — strong secret (openssl rand -base64 32)
✅ `STORAGE_PATH` — `/opt/render/project/src/uploads` (optional)

## Verify

After adding the environment variables:
1. Click **Save Changes**
2. Render will deploy automatically
3. Check the **Logs** tab:
   - You should see the `DATABASE_URL parsed` message
   - There should be no database connection error

## Troubleshooting

### I can't find DATABASE_URL

On the PostgreSQL page:
- Look at the **Info** tab
- Look at the **Connections** tab
- Search for **Internal Database URL** or **Connection String**

### Still getting a connection error

Check the logs:
- If you see the `Failed to parse DATABASE_URL` error, the format may be wrong
- The format should be: `postgres://user:password@host:port/database`
