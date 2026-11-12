# ZaraMoney — Mobile Integration Guide

Everything the Expo / React Native app needs to talk to the ZaraMoney backend.
**This file is kept up to date with every backend change that affects the app.** See the [Changelog](#14-changelog) at the bottom for what changed and when.

> Status legend: ✅ live on `develop` · 🚧 built, not yet deployed · 🔜 planned (contract may still change)

---

## Table of contents
1. [Basics](#1-basics)
2. [Error format](#2-error-format)
3. [Device keys (one-time setup)](#3-device-keys-one-time-setup)
4. [Registration and login](#4-registration-and-login)
5. [Signing every request](#5-signing-every-request)
6. [Tokens: refresh, logout, expiry](#6-tokens-refresh-logout-expiry)
7. [Onboarding: wallet + KYC](#7-onboarding-wallet--kyc)
8. [Transaction PIN](#8-transaction-pin)
9. [Money: send and withdraw](#9-money-send-and-withdraw)
10. [History, receipts, notifications](#10-history-receipts-notifications)
11. [Sessions and devices screen](#11-sessions-and-devices-screen)
12. [Rate limits](#12-rate-limits)
13. [Error code reference](#13-error-code-reference)
14. [Changelog](#14-changelog)

---

## 1. Basics

| Item | Value |
|---|---|
| Base URL | `https://<api-host>` (all paths below start with `/api`) |
| Format | JSON (`Content-Type: application/json`) except onboarding (multipart) |
| Auth | `Authorization: Bearer <access_token>` + device signature headers (section 5) |
| Money | Decimal strings or numbers with **2 decimals** (`"25.00"`). Never use JS floats for maths, use a decimal library |
| Currency | `USD` (wallets are USD today; `TND` exists in the enum but is not usable yet) |
| Dates | ISO-8601 UTC (`2026-11-12T10:15:30Z`) |
| Pagination | `?page=0&size=20&sort=transactionDate,desc`. The `/plus` list endpoints return a Spring `Page` object: `{ "content": [...], "totalElements": 42, "totalPages": 3, "number": 0, "size": 20, "last": false, ... }` |

**Endpoints the app may call** (everything else is admin-only and returns 403):

| Area | Endpoints |
|---|---|
| Auth | `GET /api/auth/challenge`, `POST /api/authenticate`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `POST /api/auth/transaction-key` |
| Account (JHipster) | `POST /api/register`, `GET /api/account`, `POST /api/account`, `POST /api/account/change-password`, `POST /api/account/reset-password/init`, `POST /api/account/reset-password/finish` |
| Sessions | `GET /api/account/sessions`, `DELETE /api/account/sessions/{sid}`, `POST /api/account/sessions/revoke-all` |
| Wallet / KYC | `POST /api/user-account/create`, `GET /api/user-account` |
| PIN | `GET /api/plus/security/pin`, `PUT /api/plus/security/pin` |
| Money | `POST /api/plus/transaction-records/send`, `POST /api/plus/transaction-records/withdrawal` |
| History | `GET /api/plus/transaction-records`, `GET /api/plus/transaction-records/{id}`, `GET /api/ledger-entries/plus/me`, `GET /api/ledger-entries/plus/transaction/{id}` |
| Lookup | `GET /api/bank-accounts/plus/accounts/{accountNumber}/verify` |
| Notifications | `GET /api/plus/notifications`, `GET /api/plus/notifications/unread-count`, `PUT /api/plus/notifications/{id}/read`, `PUT /api/plus/notifications/read-all`, `PUT /api/plus/push-token` |

⚠️ **Never call** `/api/bank-accounts`, `/api/transaction-records`, `/api/ledger-entries`, `/api/transaction-fees`, `/api/transaction-limits`, `/api/idempotency-records`, `/api/notifications`, `/api/user-details-accounts` or `/api/users`: they are admin-only (403).

---

## 2. Error format

Every error is Problem JSON (`application/problem+json`):

```json
{
  "type": "https://www.jhipster.tech/problem/problem-with-message",
  "title": "Daily limit exceeded",
  "status": 400,
  "message": "error.dailylimitexceeded",
  "path": "/api/plus/transaction-records/send"
}
```

- **Switch on `message`** (`error.<code>`), not on `title` (`title` is English text for logs and may change).
- `429` responses carry a `Retry-After` header (seconds).
- `device_signature_expired` also carries `serverTime` (epoch ms) so the app can fix its clock offset.
- Validation errors (`400`, `message: "error.validation"`) include `fieldErrors: [{objectName, field, message}]`.

Full list: [section 13](#13-error-code-reference).

---

## 3. Device keys (one-time setup) 🚧

The app holds **two EC P-256 key pairs** created in the Secure Enclave (iOS) / Android Keystore (StrongBox if available). They are **non-exportable**, so this needs an **Expo dev build with a native module** (Expo Go cannot do it).

| Key | User presence | Used for |
|---|---|---|
| **Device key** | none | signing login and every API request |
| **Transaction key** | **biometric** (Face ID / fingerprint) required on every use | additionally signing money operations and the PIN change |

- Public keys are sent as **base64 X.509 SubjectPublicKeyInfo (DER)**: that is what iOS `SecKeyCopyExternalRepresentation` + SPKI header / Android `getPublic().getEncoded()` produce.
- Signatures: **ECDSA with SHA-256**, encoded as **base64 DER** (raw 64-byte `r||s` is also accepted).
- `deviceId`: a random ID generated once at install, 8–64 chars `[A-Za-z0-9_-]`, kept in secure storage. A new install = a new device.
- If the user enrols a new fingerprint, Android/iOS invalidate the transaction key. Create a new one and register it with [`POST /api/auth/transaction-key`](#transaction-key-replacement).

---

## 4. Registration and login

### 4.1 Register ✅
`POST /api/register` (public)
```json
{ "login": "jane@example.com", "email": "jane@example.com", "password": "...", "firstName": "Jane", "lastName": "Doe", "langKey": "en" }
```
`201` on success. Use the e-mail as `login`. The account is active immediately. Password: 4–100 chars.

### 4.2 Login (device-bound) 🚧

1. `GET /api/auth/challenge` (public)
   ```json
   { "nonce": "q2X...", "expiresAt": 1794470400000, "serverTime": 1794470280000 }
   ```
   The nonce is **single use** and valid **2 minutes**. Store `serverTime - Date.now()` as your clock offset.
2. Sign with the **device key**:
   ```
   signature = sign_device("login\n" + nonce + "\n" + deviceId + "\n" + username)
   ```
3. `POST /api/authenticate`
   ```json
   {
     "username": "jane@example.com",
     "password": "...",
     "deviceId": "a1b2c3d4e5f6",
     "deviceName": "Jane's Pixel 8",
     "platform": "android",
     "devicePublicKey": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
     "transactionPublicKey": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
     "nonce": "q2X...",
     "signature": "MEUCIQ..."
   }
   ```
4. Response `200`:
   ```json
   {
     "access_token": "eyJ...",
     "id_token": "eyJ...",
     "token_type": "Bearer",
     "expires_in": 900,
     "refresh_token": "Xk3...",
     "refresh_expires_in": 2592000,
     "session_id": "6f1c...",
     "session_type": "DEVICE"
   }
   ```
   (`id_token` = `access_token`, kept for old app versions. `expires_in` is in seconds.)

Rules:
- One session per device: logging in again on the same `deviceId` replaces the old session.
- Max **5 devices** per user: the oldest session is revoked automatically.
- Wrong password 5 times in 15 min → `429 loginlocked` for that login (`Retry-After`).
- Blocked user/device → `403 account_blocked` / `device_blocked`.

**Old app versions** (no device fields) still get a `LEGACY` session (24 h token, no refresh token) while the server runs in `optional` mode. Once the server is switched to `enforce`, they get `400/401 device_binding_required` → show "Please update the app".

---

## 5. Signing every request 🚧

Every authenticated request (and `POST /api/auth/refresh`) carries:

| Header | Value |
|---|---|
| `Authorization` | `Bearer <access_token>` (not on `/api/auth/refresh`) |
| `X-Device-Id` | the `deviceId` used at login |
| `X-Timestamp` | epoch **milliseconds**, corrected with the server clock offset |
| `X-Nonce` | a fresh UUID (8–64 chars `[A-Za-z0-9_-]`), **never reused** |
| `X-Signature` | `base64(sign_device(canonical))` |
| `X-Tx-Signature` | **money operations only**: `base64(sign_transaction(canonical))` (biometric prompt) |

```
canonical = METHOD + "\n"
          + PATH[?QUERY] + "\n"        // exactly as sent, e.g. /api/plus/transaction-records?page=0&size=20
          + X-Timestamp + "\n"
          + X-Nonce + "\n"
          + X-Device-Id + "\n"
          + base64(SHA-256(raw body bytes))
```
- Hash **the exact bytes you send** (serialize the JSON once, hash it, send that same string).
- Empty body (GET/DELETE) → hash of zero bytes = `47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=`.
- Multipart (onboarding) → the literal string `MULTIPART` instead of the hash.
- Timestamp window: **±60 s**. Outside → `401 device_signature_expired` with `serverTime`: update the offset and retry once with a new nonce.

**Endpoints that need `X-Tx-Signature`** (show the biometric prompt):
- `POST /api/plus/transaction-records/send`
- `POST /api/plus/transaction-records/withdrawal`
- `PUT /api/plus/security/pin`

Pseudo-code:
```ts
async function signedFetch(method, path, body?, { tx = false } = {}) {
  const bodyStr = body === undefined ? "" : JSON.stringify(body);
  const ts = String(Date.now() + clockOffset);
  const nonce = uuid();
  const bodyHash = base64(sha256(utf8(bodyStr)));
  const canonical = [method, path, ts, nonce, deviceId, bodyHash].join("\n");
  const headers = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${accessToken}`,
    "X-Device-Id": deviceId, "X-Timestamp": ts, "X-Nonce": nonce,
    "X-Signature": await deviceKey.sign(canonical),
  };
  if (tx) headers["X-Tx-Signature"] = await transactionKey.sign(canonical); // biometric prompt
  return fetch(BASE + path, { method, headers, body: bodyStr || undefined });
}
```

---

## 6. Tokens: refresh, logout, expiry 🚧

| Token | Lifetime | Storage |
|---|---|---|
| Access token | **15 min** | memory |
| Refresh token | **30 days**, rotated on every refresh; the session dies after **90 days** in any case | `expo-secure-store` with `requireAuthentication: true` |

### Refresh
`POST /api/auth/refresh` — **signed** (section 5) but **without** `Authorization`:
```json
{ "refresh_token": "Xk3..." }
```
→ same body as login, with a **new** `refresh_token`. Replace the stored one immediately.

- Refresh proactively ~1 min before `expires_in`, or on `401` with `session_invalid` / expired token.
- Run **one refresh at a time** (a mutex). If two run in parallel, the second gets `409 refresh_in_progress`: wait and use the result of the first.
- `401 refresh_token_reused` / `refresh_token_expired` / `refresh_token_invalid` / `session_revoked` → **wipe tokens and go to login**.
- Re-sending an old refresh token after 30 s is treated as theft: the whole session is revoked.

### Logout
`POST /api/auth/logout` (signed) → `204`. Then delete the tokens locally.

### Sessions revoked by the server
The server revokes sessions on: password change (other devices), password reset (all), PIN change (other devices), PIN locked (all), refresh-token reuse, user/device blocked. The next request returns `401 session_revoked` → go to login.

### Transaction key replacement
`POST /api/auth/transaction-key` (device-signed, no `X-Tx-Signature`)
```json
{ "transactionPublicKey": "MFkw...", "password": "...", "pin": "4826" }
```
→ `204`. Use it when the biometric key was invalidated (`transaction_signature_invalid` keeps happening, or the OS reports the key as invalidated).

---

## 7. Onboarding: wallet + KYC

### 7.1 Create the wallet ✅
`POST /api/user-account/create` — `multipart/form-data` (signed with body hash `MULTIPART`)

| Field | Type | Rules |
|---|---|---|
| `phoneNumber` | text | required |
| `country` | text | required |
| `address` | text | required |
| `expoPushToken` | text | required (`ExponentPushToken[...]`) |
| `facePicture` | file | **required**, JPEG/PNG/WEBP (checked from the bytes), max **500 KB** |
| `idCardPicture` | file | **required**, same rules |

Compress photos on the device before upload (e.g. `expo-image-manipulator`, JPEG quality ~0.6, max 1280 px).

Result `2xx` with a `UserAccountDto` body (see 7.2). The user gets:
- one USD wallet, opened at `0.00`, then credited with a **$5 welcome bonus** (shows in history as a `BONUS` transaction)
- default limits: **$1 000 / day**, **$10 000 / month**
- KYC status **`PENDING`**

Errors: `409 accountalreadyexists` (a wallet already exists → just load it), `400 kycdocumentsrequired`, `filetoolarge`, `invalidimagetype`, `emptyfile`, `unreadablefile`, `502 uploadfailed` (retry).

### 7.2 Load the wallet ✅
`GET /api/user-account`
```json
{
  "firstName": "Jane", "lastName": "Doe", "email": "jane@example.com",
  "phoneNumber": "+243...", "kycStatus": "PENDING",
  "usdAccount": { "id": 12, "accountNumber": "12345678", "balance": 5.00, "currency": "USD", "status": "ACTIVE", "accountType": "CUSTOMER", "createdAt": "..." }
}
```
`usdAccount` is the user's own wallet only.

`404 noaccount` → the user has not onboarded yet: show the onboarding flow (before Phase 3 this was a 500).

### 7.3 KYC states
| `kycStatus` | Show | Can send / withdraw |
|---|---|---|
| `PENDING` | "Your identity is being verified" | No → `403 kycnotverified` |
| `VERIFIED` | normal home | Yes |
| `REJECTED` | "Verification failed, contact support" | No |

An admin approves/rejects. The user then gets a **push** and a stored notification (`GET /api/plus/notifications`, `type: "SYSTEM"`, `deepLink: "account"`):

| Decision | Title | Message |
|---|---|---|
| `VERIFIED` | Compte vérifié | Votre identité a été vérifiée. Vous pouvez maintenant envoyer et retirer de l'argent. |
| `REJECTED` | Vérification refusée | Votre vérification d'identité a été refusée. Veuillez contacter le support ZaraMoney. |

On receiving it (`data.type === "KYC_STATUS"`), refresh `GET /api/user-account` and show the matching state. Only a real change is notified (approving twice sends one push). Still refresh `GET /api/user-account` on app start / pull-to-refresh: a push can be missed (no token, notifications disabled).

---

## 8. Transaction PIN ✅

A 4–6 digit PIN, separate from the login password, required for every money operation.

- `GET /api/plus/security/pin` → `{ "pinSet": false }`. If `false`, show the **"Create your PIN"** screen before allowing transfers (existing users have no PIN yet).
- `PUT /api/plus/security/pin` (needs `X-Tx-Signature` 🚧)
  ```json
  { "password": "<login password>", "pin": "4826" }
  ```
  → `204`. Also used to change the PIN (logs out the user's other devices).

Errors: `400 invalidpassword`, `invalidpinformat` (not 4–6 digits), `weakpin` (1111, 1234, 4321, …), `pinnotset`, `invalidpin` (wrong PIN on a money operation; `title` says how many attempts are left), `423 pinlocked` (+ `Retry-After`).
**5 wrong PINs → PIN locked 30 minutes** (`423 pinlocked`) **and all sessions are revoked**.

---

## 9. Money: send and withdraw ✅

### 9.1 Idempotency (important)
Every money request carries an `idempotencyKey` (8–128 chars):
- Generate a **new UUID per user attempt** (when the user taps "Confirm").
- **Reuse the same key only to retry** the same request after a timeout / network error.
- A retry with the same key returns the **original receipt** (no double payment).
- Same key with a different amount/receiver → `409 idempotencykeyreused`.
- Same key while the first call is still running → `409 requestinprogress` (wait, then retry).

### 9.2 Check the receiver (optional, before confirming)
`GET /api/bank-accounts/plus/accounts/{accountNumber}/verify` → `true` / `false`. Rate-limited to 30/min.

### 9.3 Send (P2P)
`POST /api/plus/transaction-records/send` — signed + **`X-Tx-Signature`** 🚧
```json
{
  "receiverAccountNumber": "87654321",
  "senderAccountNumber": "12345678",
  "sendAmount": "25.00",
  "description": "Rent",
  "idempotencyKey": "4f6c1b8e-6f0e-4c1e-9b7a-2d3f4a5b6c7d",
  "pin": "4826"
}
```
- `sendAmount` ≥ 1 with **at most 2 decimals** (`10.005` → 400). `description` ≤ 255 chars.
- `senderAccountNumber` (optional, 8–16 chars): which of the user's own accounts to debit. Omit it when the user has one account. If the user has several accounts it is **required** (`400 senderaccountrequired`); an account that isn't the user's → `400 senderaccountnotfound`. It is part of the idempotency payload: send the same value on retries.
- `transactionType`: **no longer needed** (the server always records a `TRANSFER` and ignores this field). Old app versions that still send it keep working.
- `currencySendAmount` / `currencyReceiveAmount`: **optional**. There is no currency conversion: if sent, they must equal the accounts' currency (`USD`), otherwise `400 currencymismatch`. Sending to an account in another currency also gives `currencymismatch`.
- Both accounts must be `ACTIVE`: a suspended/closed sender → `403 accountnotactive` ("contact support"); a suspended/closed receiver or agent → `400 receivernotactive`.

**Fee (P2P):**
| Amount | Fee |
|---|---|
| < 101 | free |
| 101 – 2 500 | 0.95 % |
| > 2 500 | 0.75 % |

The fee is **added** to the amount: the sender is debited `amount + fee`, the receiver gets `amount`. After the debit, the sender must keep at least **$5.00** (`minimumbalance`). So: `balance - (amount + fee) >= 5`.

### 9.4 Withdraw at an agent
`POST /api/plus/transaction-records/withdrawal` — signed + **`X-Tx-Signature`** 🚧
```json
{ "agentAccountNumber": "11223344", "sendAmount": "40.00", "senderAccountNumber": "12345678", "idempotencyKey": "…", "pin": "4826" }
```
`senderAccountNumber` is optional, same rules as for `/send`.
**Fee (withdrawal):**
| Amount | Fee |
|---|---|
| 1 – 5 | 1.30 % |
| 5.01 – 20 | 0.80 % |
| 20.01 – 50 | 2.50 % |
| > 50 | 1.99 % |

Same `+5` minimum balance rule. The receiver must be an agent (`400 notanagent`). `sendAmount` ≥ 1 with at most 2 decimals.

Fees are rounded half-up to the cent (e.g. 5.00 at 1.30 % = 0.065 → **0.07**). The fee schedule is server configuration and may change: always show the `fee` returned in the receipt rather than a hard-coded table, and treat the tables above as a preview only.

### 9.5 Response (both endpoints): `TransactionReceiptDTO`
```json
{
  "id": 345,
  "transactionReference": "<server-generated reference>",
  "transactionType": "TRANSFER",
  "direction": "SENT",
  "amount": 25.00,
  "fee": 0.00,
  "total": 25.00,
  "currency": "USD",
  "status": "COMPLETED",
  "description": "Rent",
  "transactionDate": "2026-11-12T10:15:30Z",
  "accountNumber": "12345678",
  "counterpartyName": "John D.",
  "counterpartyAccountNumber": "********4321"
}
```
- `direction`: `SENT` or `RECEIVED` (from the current user's point of view).
- `accountNumber`: the user's **own** account. The counterparty account is **masked** (all but the last 4 digits) and the counterparty name is `"Firstname L."` (surname initial only; `"Client ZaraMoney"` if the user has no name; `"ZaraMoney"` for deposits / bonus)
- No other user's balance, e-mail or IDs are ever returned. Refresh the balance with `GET /api/user-account`.

### 9.6 Errors to handle
| Code | Status | UX |
|---|---|---|
| `kycnotverified` | 403 | "Identity verification pending" |
| `pinnotset` | 400 | open "Create PIN" |
| `invalidpin` | 400 | "Wrong PIN" (the `title` contains the attempts left) |
| `pinlocked` | 423 | "PIN locked for 30 min"; user is logged out |
| `insufficientbalance` / `minimumbalance` | 400 | show balance, amount + fee, and the $5 minimum |
| `dailylimitexceeded` / `monthlylimitexceeded` | 400 | "Limit reached" |
| `receivernotfound` / `agentnotfound` / `notanagent` | 400 | "Check the account number" |
| `sendtomyself` | 400 | "You can't send to yourself" |
| `senderaccountrequired` | 400 | ask the user which account to pay from |
| `senderaccountnotfound` | 400 | the chosen account isn't the user's → reload accounts |
| `accountnotowned` | 403 | should not happen (account changed owner) → reload |
| `noaccount` | 400 | go to onboarding |
| `accountnotactive` | 403 | "Your account is suspended/closed, contact support" |
| `receivernotactive` | 400 | "This account can't receive money" |
| `currencymismatch` | 400 | only same-currency transfers are possible |
| `transactionblocked` | 403 | blocked by the fraud rules (nothing was debited). Show "This payment was blocked for security reasons. Contact ZaraMoney support." Do **not** auto-retry; a retry with the same key is checked again. Only happens once the server runs fraud scoring in `enforce` mode (it starts in `monitor` mode, where nothing is blocked) |
| `idempotencykeyreused` / `requestinprogress` | 409 | see 9.1 |
| `transaction_signature_invalid` | 401 | biometric failed/cancelled, or key invalidated → re-register it (section 6) |
| `ratelimited` | 429 | wait `Retry-After` |

---

## 10. History, receipts, notifications ✅

- `GET /api/plus/transaction-records?page=0&size=20` → `Page<TransactionReceiptDTO>` (only the user's own; items in `content`)
- Transaction types the user can see: `TRANSFER` (sent or received), `WITHDRAWAL`, `BONUS` (welcome bonus) and `DEPOSIT` (cash credited by ZaraMoney staff). For `BONUS` and `DEPOSIT` the counterparty is `"ZaraMoney"` and `counterpartyAccountNumber` is `null`
- `GET /api/plus/transaction-records/{id}` → one receipt (`404 transactionnotfound` if not the user's)
- `GET /api/ledger-entries/plus/me?page=0&size=20` → `Page` of `{ "entryType": "DEBIT" | "CREDIT", "transactionRecord": TransactionReceiptDTO }`
- `GET /api/ledger-entries/plus/transaction/{id}` → one entry (`404` if not the user's)
- `GET /api/plus/notifications` → array (not paginated), **newest first**, of the user's notifications: `id`, `title`, `message`, `type`, `channel`, `status`, `isRead`, `deepLink`, `createdAt`, `sentAt`, `data` (JSON string), `transaction: { id, transactionReference }` (`null` for non-transaction notifications, e.g. KYC). Nothing else (no `user` object). 
- Push delivery state on each notification: `status` = `PENDING` (push queued) → `SENT` (accepted by Expo; `sentAt` set) or `FAILED` (no push token, token no longer valid, or Expo kept failing). The notification is listed in all three cases: the in-app list does not depend on the push. Don't show `status` to users
- `GET /api/plus/notifications/unread-count` → `{ "count": 3 }` (badge)
- `PUT /api/plus/notifications/{id}/read` → `204` (idempotent). `404 notificationnotfound` if the id is not the user's. Call it when the user opens a notification
- `PUT /api/plus/notifications/read-all` → `200 { "updated": 2 }` (number that were unread)

**Push notifications** (Expo): sent to the `expoPushToken` given at onboarding. Transaction push payload:
```json
{ "type": "TRANSACTION_ALERT", "transactionId": 345, "deepLink": "transaction/345" }
```
Open `GET /api/plus/transaction-records/{transactionId}` on tap.

KYC decision push payload (see §7.3):
```json
{ "type": "KYC_STATUS", "kycStatus": "VERIFIED", "deepLink": "account" }
```
`kycStatus` is `VERIFIED` or `REJECTED`. On tap (or on receipt), refresh `GET /api/user-account`. The stored notification has `transaction: null` and `data` = `{"type":"KYC_STATUS","kycStatus":"VERIFIED"}`.

Route on `data.type`: `TRANSACTION_ALERT` → transaction screen, `KYC_STATUS` → account screen; ignore unknown types (more will be added).

**Push token** — `PUT /api/plus/push-token`
```json
{ "expoPushToken": "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]" }
```
- `204`. Call it after login and every time `getExpoPushTokenAsync()` returns a different value (reinstall, token rotation, permission granted later)
- `{ "expoPushToken": null }` clears it: call it on **logout** (before discarding the tokens) or when the user turns notifications off
- Must look like `ExponentPushToken[…]` / `ExpoPushToken[…]`, otherwise `400`. Before onboarding: `404 noaccount`
- One device token belongs to one user: when another account registers the same token (account switch on the same phone), it is removed from the previous account, so its pushes stop reaching this phone

---

## 11. Sessions and devices screen 🚧

- `GET /api/account/sessions` →
  ```json
  [{ "sessionId": "6f1c...", "type": "DEVICE", "deviceId": "...", "deviceName": "Jane's Pixel 8", "platform": "android",
     "ip": "...", "createdAt": "...", "lastUsedAt": "...", "expiresAt": "...", "revokedAt": null, "revokeReason": null, "current": true }]
  ```
- `DELETE /api/account/sessions/{sid}` → `204` (logs that device out; `404 sessionnotfound`)
- `POST /api/account/sessions/revoke-all?keepCurrent=true` → `{ "revoked": 3 }` ("log out all other devices")

---

## 12. Rate limits ✅

Exceeding them returns `429 ratelimited` + `Retry-After` (seconds).

| Endpoint | Limit | Per |
|---|---|---|
| `POST /api/authenticate` | 10 / min | IP |
| `GET /api/auth/challenge` | 30 / min | IP |
| `POST /api/auth/refresh` | 30 / min | IP |
| `POST /api/register` | 5 / hour | IP |
| `POST /api/account/reset-password/init` | 5 / hour | IP |
| `POST /api/account/reset-password/finish` | 10 / hour | IP |
| `GET …/accounts/{n}/verify` | 30 / min | user |
| `POST …/send`, `POST …/withdrawal` | 10 / min | user |
| `PUT /api/plus/security/pin` | 5 / hour | user |

Plus the login lockout: 5 wrong passwords in 15 min → `429 loginlocked`.

---

## 13. Error code reference

`message` = `error.<code>`.

### Auth / device (section 3–6)
| Code | Status | Meaning → app action |
|---|---|---|
| `device_signature_missing` | 401 | signing headers missing → bug in the client |
| `device_signature_invalid` | 401 | bad signature/nonce/timestamp format → check canonical string; if persistent, log in again |
| `device_signature_expired` | 401 | clock skew → set offset from `serverTime`, retry once |
| `device_nonce_reused` | 401 | nonce reused → always generate a new UUID |
| `device_mismatch` | 401 | token used from another device → log in again |
| `transaction_signature_invalid` | 401 | biometric signature missing/invalid → retry prompt or re-register transaction key |
| `session_invalid` | 401 | token without session (pre-update token) → log in again |
| `session_revoked` | 401 | session ended → log in again |
| `challenge_invalid` | 401 | login nonce expired/used → get a new challenge |
| `refresh_token_invalid` / `refresh_token_expired` / `refresh_token_reused` | 401 | → log in again |
| `refresh_in_progress` | 409 | parallel refresh → wait for the first one |
| `account_blocked` / `device_blocked` | 403 | "Contact support" |
| `device_binding_required` | 400/401 | app too old → force update |
| `invalid_public_key` | 400 | key not P-256 SPKI base64 |
| `loginlocked` | 429 | too many wrong passwords |
| `invalidpassword` | 400 | wrong password (PIN set / transaction key) |

### Onboarding
`accountalreadyexists` 409 · `kycdocumentsrequired` 400 · `filetoolarge` 400 · `invalidimagetype` 400 · `emptyfile` 400 · `unreadablefile` 400 · `uploadfailed` 502

### PIN
`pinnotset` 400 · `invalidpin` 400 · `invalidpinformat` 400 · `weakpin` 400 · `pinlocked` 423

### Money
`kycnotverified` 403 · `accountnotactive` 403 · `receivernotactive` 400 · `currencymismatch` 400 · `senderaccountrequired` 400 · `senderaccountnotfound` 400 · `accountnotowned` 403 · `insufficientbalance` 400 · `minimumbalance` 400 · `dailylimitexceeded` 400 · `monthlylimitexceeded` 400 · `invalidamount` 400 · `receivernotfound` 400 · `agentnotfound` 400 · `notanagent` 400 · `sendtomyself` 400 · `noaccount` 400 · `idempotencykeyreused` 409 · `requestinprogress` 409 · `transactionblocked` 403

### Other
`transactionnotfound` 404 · `notificationnotfound` 404 · `sessionnotfound` 404 · `ratelimited` 429 · `validation` 400 · `unauthorized` 401

---

## 14. Changelog

| Date | Change | App impact |
|---|---|---|
| 2026-11-12 | Guide created (consolidates Phase 2 + item 13 contracts) | — |
| 2026-11-12 | **Device-bound sessions** (🚧): challenge login, signed requests, 15-min access tokens + rotating refresh tokens, sessions screen, transaction key | **Breaking**: all old tokens invalid after deploy (everyone logs in once). New login flow + signing required (old app works only while server is `optional`) |
| 2026-11-12 | **Phase 2 security** (🚧): `pin` replaces `password` on send/withdrawal; transaction PIN endpoints; `TransactionReceiptDTO` responses; KYC `PENDING` + admin review; both KYC photos required (≤ 500 KB, JPEG/PNG/WEBP); $5 bonus via ledger; limits enforced; per-user idempotency keys (8–128 chars); rate limits | **Breaking**: ship the app together with the backend. Existing users must create a PIN before sending |
| 2026-11-12 | Admin deposits (🚧) now go through the ledger (from the ZaraMoney settlement account) and are idempotent | None for the app: deposits still appear as `DEPOSIT` / `RECEIVED` from `"ZaraMoney"`; the description is now `Dépôt de <amount> USD` |
| 2026-11-12 | Optional `senderAccountNumber` on `/send` and `/withdrawal` (🚧); fees now credited to a ZaraMoney fee account in the ledger (no change in what the user pays) | Optional field; new errors `senderaccountrequired` / `senderaccountnotfound` / `accountnotowned`. Users with one account: nothing to change |
| 2026-11-12 | **Phase 3** (🚧): withdrawal fee 5.01–20 is **0.8 %** (was 8 % by mistake); `transactionType` and currencies optional on `/send` (type set by the server); amounts limited to 2 decimals; account status and currency checks; `GET /api/user-account` returns `404 noaccount` before onboarding (was 500); pushes are sent only after the transaction is committed | Update the fee preview; handle `accountnotactive`, `receivernotactive`, `currencymismatch`, `404 noaccount`. The app can stop sending `transactionType` / currencies (optional) |
| 2026-11-12 | Admin CRUD `/api/bank-accounts`: `balance` is read-only (create starts at 0, PUT/PATCH keep the current balance); an account with a balance or history keeps its number, currency, owner and type and cannot be deleted (`409 accountinuse`); balances change only through deposits, transfers, withdrawals and bonuses | None: admin-only endpoint, never used by the app |
| 2026-11-12 | Ledger integrity (Phase 4): entries record the balance after each movement and the account currency; the ledger can no longer be edited or deleted; daily reconciliation | None: `/api/ledger-entries/plus/*` responses are unchanged |
| 2026-11-12 | KYC approval / rejection now sends a push + stored notification (`type: "KYC_STATUS"` in the push data, `deepLink: "account"`) | Handle the `KYC_STATUS` push: refresh `GET /api/user-account`. Notifications list may now contain items with `transaction: null` |
| 2026-11-12 | New: `PUT /api/plus/push-token`, `GET /api/plus/notifications/unread-count`, `PUT /api/plus/notifications/{id}/read`, `PUT /api/plus/notifications/read-all` | Send the push token after login / when it changes, `null` on logout; mark notifications read when opened; badge from `unread-count` |
| 2026-11-12 | Push delivery: retried on network/5xx/429 errors; notification `status` is now `PENDING` → `SENT` / `FAILED` and `sentAt` is set only when Expo accepted the push (was always `SENT`); a token Expo reports as `DeviceNotRegistered` is removed | None required. After a reinstall the old token is dropped automatically: send the new one with `PUT /api/plus/push-token` |
| 2026-11-12 | Fraud scoring on send / withdrawal (starts in **monitor** mode: scores and flags, blocks nothing). Customer history now lists only `COMPLETED` transactions | Handle `403 transactionblocked` (needed before the server switches to `enforce`). No other change |
| 2026-11-12 | Server-side audit trail of admin actions and sensitive changes (PIN, password, blocked payments) | None |
| 2026-11-12 | **Privacy fix**: `counterpartyName` is now `"Firstname L."` as documented (the full name was returned by mistake). `GET /api/plus/notifications`: newest first, exactly the documented fields (the extra `user` object and the null fields inside `transaction` are gone) | Don't parse a surname from `counterpartyName`; don't read `notification.user` (removed) |
