# ZaraMoney Backend — Security & Correctness TODO

Findings from the code review of 2026-11-11. Work top to bottom. Tick each item (`[x]`) once it is fixed **and** covered by a test.

Legend: 🔴 Critical · 🟠 High · 🟡 Bug · 🔵 Improvement

---

## Phase 1 — Do immediately

### 🔴 1. Lock down the JHipster CRUD endpoints (anyone logged in can edit balances)
- [ ] `/api/bank-accounts/**` lets any authenticated user POST, PUT, PATCH or DELETE, so `PATCH {"id":X,"balance":1000000}` works (`web/rest/BankAccountResource.java`)
- [ ] Same problem on `/api/transaction-records/**`, `/api/ledger-entries/**`, `/api/transaction-fees/**`, `/api/transaction-limits/**`, `/api/idempotency-records/**`, `/api/notifications/**` and `/api/user-details-accounts/**`
- [ ] Fix: delete these resources, or add `@PreAuthorize("hasAuthority('ROLE_ADMIN')")` at class level, or move them under `/api/admin/**`
- [ ] Add an integration test: a `ROLE_USER` call to each of these endpoints returns 403

### 🔴 2. Remove and rotate the committed secrets
- [ ] Prod DB password: `src/main/resources/config/application-prod.yml:38`
- [ ] JWT base64 secret, identical in dev and prod: `application-prod.yml:102`, `application-dev.yml:85`, `.yo-rc.json` (`jwtSecretKey`)
- [ ] TLS keystore password `password`: `application-tls.yml:13`
- [ ] Rotate all of them (the old values must be treated as leaked)
- [ ] Load them from environment variables, e.g. `${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET}` or `${SPRING_DATASOURCE_PASSWORD}`
- [ ] Purge them from git history (git-filter-repo / BFG) and add gitleaks to CI
- [ ] Use a different JWT secret in dev and prod

---

## Phase 2 — Money logic and data leaks

### 🔴 3. The balance check ignores the fee (balances can go negative)
- [ ] `TransactionValidatorService.checkAmountValidation` checks `balance - amount` but the code debits `amount + fee`. Example: balance 2505, send 2500, fee 23.75 → balance −18.75
- [ ] Compute the fee **before** validating and check `balance - (amount + fee) >= MIN_BALANCE`
- [ ] Add a DB constraint `CHECK (balance >= 0)` on `bank_account` (Liquibase changeset)

### 🔴 4. The KYC / account-creation step hands out free money
- [ ] `POST /api/user-account/create` can be called repeatedly. Each call creates a new bank account with $5.00 (`BankAccountServicePlus.createBankAccount`, line 43) and a new user-details row → allow only one per user
- [ ] The initial $5 has no ledger entry → fund it from a "promo" system account through the ledger, or start at 0
- [ ] KYC is set to `VERIFIED` automatically (`UserAccountService.java:77`) → start at `PENDING` and add an admin approve/reject flow

### 🔴 5. Enforce the transaction limits
- [ ] The daily and monthly limits created by `TransactionLimitServicePlus` are never checked → enforce them in `/send` and `/withdrawal`

### 🟠 6. Every user's transactions are visible to everyone
- [ ] `GET /api/plus/transaction-records` and `GET /api/plus/transaction-records/{id}` return all users' transactions (`TransactionRecordResourcePlus.java:47-59`) → filter by the current user's accounts, or restrict to admins

### 🟠 7. Responses leak other users' data
- [ ] Transfer responses embed the full `BankAccountDTO` for sender and receiver, including the **receiver's balance** and login (email)
- [ ] Create customer-facing response DTOs (reference, amount, fee, masked counterparty name and account number, status, date)

### 🟠 8. Idempotency keys are global, not per user
- [ ] `IdempotencyRecordServicePlus.check(key)` looks up by key hash only, so user B can replay user A's key and read A's transaction
- [ ] Scope by `(user_id, key_hash)`: change the unique constraint and the lookup
- [ ] Store the real endpoint (it is hard-coded to `/send` even for withdrawals)
- [ ] Also store a hash of the request body and reject a reused key that comes with a different payload

### 🟠 9. Path traversal and unsafe uploads
- [ ] `UploadFileService.uploadFile` writes to `Path.of(tmpdir, originalFilename)`: a name like `../../x` lets the client write anywhere, and users with the same file name overwrite each other's files
- [ ] Upload the bytes directly (`cloudinary.uploader().upload(file.getBytes(), ...)`), or use a random UUID temp file and delete it in `finally`
- [ ] Drop SVG (stored-XSS risk)
- [ ] Verify the real image type from the file's magic bytes instead of the client-supplied MIME type
- [ ] Store `secure_url`, not `url`
- [ ] Make KYC documents private (authenticated / signed URLs)

### 🟠 10. Transaction password and brute force
- [ ] The transaction "password" is just the login password (`UserService.validateTransactionPassword`) → add a separate hashed PIN
- [ ] Lock the account after N failed PIN or login attempts
- [ ] Add rate limiting (e.g. Bucket4j) on `/api/authenticate`, `/api/register`, `/api/account/reset-password/*`, `/verify` and `/send`

### 🟠 11. Account number generation and enumeration
- [ ] `generateAccountNumber` uses `Math.random()` for 8 digits with no retry on collision → use `SecureRandom`, retry on unique violation, and consider a check digit (Luhn)
- [ ] `/api/bank-accounts/plus/accounts/{n}/verify` and `GET /api/users` make enumeration easy → rate-limit them and return the minimum data needed

### 🟠 12. Exposure through config and endpoints
- [ ] `/management/prometheus` is publicly accessible (`SecurityConfiguration.java:54`) → restrict it to admin or the internal network
- [ ] JWT validity is 24h, or 30 days with remember-me → replaced by the token design in item 13

### 🟠 13. Refresh tokens, revocation and device binding (for mobile local authentication)
Goal: the app uses local authentication (Face ID / fingerprint via `expo-local-authentication`) to unlock a refresh token kept in the device's secure storage. Any token can be revoked or blocked, and a copied token is useless on another device.

**Token model**
- [ ] Access token: JWT, short-lived (10–15 min). Add these claims:
  - [ ] `sid`: session / refresh-token family ID
  - [ ] `did`: device ID
  - [ ] `jti`: unique token ID
- [ ] Remove the 30-day `remember-me` validity (`token-validity-in-seconds-for-remember-me`)
- [ ] Refresh token: opaque random value (≥ 256 bits, `SecureRandom`), **not** a JWT, returned only at login and on refresh
- [ ] Store only the SHA-256 hash of the refresh token in the DB, never the raw value
- [ ] New `refresh_token` / `user_session` table (Liquibase), with columns:
  - [ ] `id`, `user_id`, `token_hash` (unique)
  - [ ] `family_id` (= `sid`), `device_id`, `device_public_key`, `device_name`, `platform`
  - [ ] `created_at`, `expires_at` (e.g. 30 days, sliding), `last_used_at`, `ip`, `user_agent`
  - [ ] `revoked_at`, `revoke_reason`, `replaced_by_id`

**Login: `POST /api/authenticate`**
- [ ] The request also carries `deviceId` (a UUID generated once on first install and kept in SecureStore), `deviceName`, `platform` and `devicePublicKey` (see binding below)
- [ ] The response returns `{ id_token / access_token, refresh_token, expires_in }`
- [ ] Create a new session row for each login; on the same device, revoke that device's previous session
- [ ] Optional: limit active devices per user (e.g. 3), and notify the user by push or email when a new device logs in

**Refresh: `POST /api/auth/refresh`** (public endpoint, rate-limited)
- [ ] Body: `refresh_token`. Headers: `X-Device-Id`, plus the device signature (see binding)
- [ ] Check that the token hash exists, is not expired or revoked, belongs to the same `device_id` and that the user is still active / not locked
- [ ] **Rotation:** each refresh issues a new refresh token and marks the old one used (`replaced_by_id`)
- [ ] **Reuse detection:** if a token that was already rotated is presented again, it has been stolen → revoke the **whole family** (all tokens with that `sid`), and log and alert
- [ ] Handle races: two parallel refreshes from the same app must not trigger a false reuse alarm (short grace window, or row lock on the token)

**Device binding: device-key signature (DECIDED 2026-11-11)**
Proof of possession: every authenticated request and every refresh must be signed by a private key that never leaves the phone. A stolen access or refresh token is useless without that key. This replaces the simpler "device-ID header only" check.

*Keys (generated on the phone, non-exportable)*
- [ ] Algorithm: **ECDSA P-256 / SHA-256** (`SHA256withECDSA`), the only curve the iOS Secure Enclave supports; Android Keystore supports it too (StrongBox when available)
- [ ] **Device key**: no user-presence requirement; signs every API request and refresh
- [ ] **Transaction key**: created with biometric user presence required (Face ID / fingerprint); signs only money operations (`/send`, `/withdrawal`, PIN change, adding a device), so each money operation shows a biometric prompt while normal calls don't
- [ ] The public keys are sent as base64 **X.509 SubjectPublicKeyInfo (DER)**. The server parses them with `KeyFactory.getInstance("EC")` + `X509EncodedKeySpec`, rejects any curve other than P-256, and stores them on the session (`device_public_key`, `transaction_public_key`)
- [ ] Mobile note: Expo Go can't do this; it needs a dev build with a native module that uses the Secure Enclave / Keystore (e.g. `react-native-biometrics` or a custom Expo module). `expo-secure-store` only holds the refresh token and `deviceId`

*Registering the keys at login (proves the key exists, not just a pasted string)*
- [ ] `GET /api/auth/challenge` (public, rate-limited) → `{ nonce, expiresAt }`, single use, stored in the cache with a 2-minute TTL
- [ ] `POST /api/authenticate` body: `username`, `password`, `deviceId`, `deviceName`, `platform`, `devicePublicKey`, `transactionPublicKey`, `nonce`, `signature`. The signature is made with the device key over `"login\n" + nonce + "\n" + deviceId + "\n" + username`
- [ ] The server checks the password, consumes the nonce, verifies the signature with `devicePublicKey`, then creates the session holding both public keys and returns the access and refresh tokens
- [ ] On the same `deviceId`, a new login replaces the old keys and session (old session revoked)

*Request signing (every authenticated call + `/api/auth/refresh` + `/api/auth/logout`)*
- [ ] Headers:
  - [ ] `X-Device-Id`
  - [ ] `X-Timestamp` (epoch millis)
  - [ ] `X-Nonce` (UUID v4)
  - [ ] `X-Signature` (base64 DER ECDSA signature)
  - [ ] Money operations add `X-Tx-Signature` (signed by the transaction key)
- [ ] Canonical string to sign (UTF-8, `\n`-separated, defined once and shared with the mobile team):
  ```
  METHOD
  PATH?QUERY            (exact path + raw query string, e.g. /api/plus/transaction-records/send)
  X-Timestamp
  X-Nonce
  X-Device-Id
  base64(SHA-256(raw request body))   (hash of an empty string when there is no body)
  ```
- [ ] New `DeviceSignatureFilter`, placed after JWT authentication in `SecurityConfiguration`:
  - [ ] Load the session via the JWT `sid` claim (for refresh: via the refresh-token hash)
  - [ ] Check that the session is active and that `X-Device-Id` == `did` claim == `session.device_id`
  - [ ] Reject the timestamp if it is outside ±60 s of server time; return the server time in the error so the app can correct clock skew
  - [ ] Reject a reused nonce: store `(sid, nonce)` in the cache with TTL = 2 × the time window
  - [ ] Verify `X-Signature` with the session's device public key over the canonical string
  - [ ] Read the body through a caching request wrapper (`ContentCachingRequestWrapper` is not enough before the controller; use a custom wrapper that buffers the body) so the hash matches the bytes the controller receives
  - [ ] For `/send`, `/withdrawal` and PIN or device changes, also verify `X-Tx-Signature` with the transaction public key
  - [ ] Any failure → 401 with a specific error code (`device_signature_invalid`, `device_signature_expired`, `device_nonce_reused`, `device_mismatch`); count failures in metrics and log them for fraud review
- [ ] Endpoints exempt from signing: `/api/auth/challenge`, `/api/authenticate`, `/api/register`, `/api/activate`, `/api/account/reset-password/*`, public `/management/health`
- [ ] Multipart upload (`/api/user-account/create`): hash the raw multipart body the same way, or sign the SHA-256 of each file plus the fields; decide and document the choice

*Lifecycle*
- [ ] Lost or stolen phone: revoking the session from another device or by an admin also invalidates its keys
- [ ] Reinstalling the app or a keystore reset produces new keys, so the user must log in again (expected behaviour)
- [ ] Changing biometrics (new fingerprint added) invalidates the biometric-bound transaction key on iOS/Android → the app must detect it and re-register the key after password + PIN check (`POST /api/auth/transaction-key`, signed with the device key)
- [ ] Optional, later: Play Integrity / App Attest at login to prove requests come from the genuine app

**Revocation / blocking**
- [ ] Revoking a session (`revoked_at`) must also kill its access tokens right away:
  - [ ] Keep a revoked-`sid` deny-list in the cache (Ehcache / Redis) with TTL = access-token lifetime, checked in the JWT filter
  - [ ] Alternative: a `token_version` on the user, embedded in the JWT and compared on each request
- [ ] User endpoints:
  - [ ] `POST /api/auth/logout` → revoke the current session
  - [ ] `GET /api/account/sessions` → list devices (name, platform, last used, current one)
  - [ ] `DELETE /api/account/sessions/{id}` → log out a specific device
  - [ ] `POST /api/account/sessions/revoke-all` → log out everywhere
- [ ] Admin endpoints (`/api/admin/**`):
  - [ ] Revoke one session or all sessions of a user
  - [ ] Block or unblock a user (block = revoke everything and refuse login and refresh)
  - [ ] Block a device ID
- [ ] Automatically revoke all sessions on:
  - [ ] Password change / reset
  - [ ] PIN change
  - [ ] Account lock after too many failed attempts
  - [ ] Account frozen / closed
  - [ ] Detected token reuse
- [ ] Scheduled job to purge expired and revoked tokens (keep an audit trail)

**Local authentication flow (mobile side, for reference)**
- [ ] Store the refresh token in `expo-secure-store` with `requireAuthentication: true`, so biometrics are needed to read it
- [ ] App start → biometric prompt → read the refresh token → `/api/auth/refresh` → new access token
- [ ] Biometrics only unlock the device; the server never sees them. For money operations still require the transaction PIN, or a device-key signature with user presence (item 10)
- [ ] Fall back to email + password login when biometrics fail or the refresh token is revoked or expired

**Tests**
- [ ] Login returns both tokens
- [ ] Refresh rotates the refresh token
- [ ] Reusing an old refresh token revokes the whole family
- [ ] A token used with a different device ID (or a bad signature) is rejected
- [ ] A revoked session's access token is rejected before it expires
- [ ] A blocked user can't log in or refresh
- [ ] A password change kills all sessions

---

## Phase 3 — Transaction-flow bugs (`TransactionRecordServicePlus`, `TransactionValidatorService`)

- [ ] 🟡 Block self-transfers on `/send` (sender == receiver) (withdrawal already blocks them)
- [ ] 🟡 Check sender and receiver `AccountStatus` (reject FROZEN or CLOSED accounts) and the sender's KYC status
- [ ] 🟡 Validate currencies: `currencySendAmount` and `currencyReceiveAmount` are ignored
- [ ] 🟡 Set the server side `transactionType` itself (it is currently client-supplied in `TransactionRecordVM`)
- [ ] 🟡 Store the fee in its own column instead of `exchangeRate` (`TransactionValidatorService.java:132`); deposits also store a fake `2.81` there
- [ ] 🟡 Check the withdrawal fee tiers in `calculateWithdrawalFee`: 5–20 → **8%** looks like a typo (0.8%?). It is non-monotonic: 20 costs $1.60, 21 costs $0.53
- [ ] 🟡 `calculateWithdrawalFee` throws `IllegalArgumentException` → 500; return a 400 instead
- [ ] 🟡 Move fee rates, the minimum balance and FX rates out of the code into `BigDecimal` config or DB values (no `double`)
- [ ] 🟡 The sender is picked with `findByUserIsCurrentUser().stream().findFirst()`, which is arbitrary when a user has several accounts → choose explicitly
- [ ] 🟡 The ownership check is useless (the account was already fetched for the current user); check it against the account that is actually locked
- [ ] 🟡 Several duplicated blocks to factor out:
  - [ ] `process()` / `processWithdrawal()`
  - [ ] `createTransactionRecord()` / `withdrawalTransaction()`
- [ ] 🟡 Send notifications and Expo pushes **after commit** (`@TransactionalEventListener(phase = AFTER_COMMIT)`); today a push can be sent for a transaction that later rolls back
- [ ] 🟡 Replace bare `orElseThrow()` calls with proper 4xx errors (unknown account → 404 or 400, not 500)
- [ ] 🟡 `LedgerEntryServicePlus.getCurrentUserEntryById` throws an NPE when the ledger entry is null
- [ ] 🟡 Add `@Version` (optimistic locking) to `BankAccount` to cover the write paths that are not pessimistically locked

## Phase 3b — Back office deposit (`BackOfficeService.addMoneyToUserAccount`)
- [ ] 🟡 Validate `amount > 0` (`@DecimalMin` on `DepositDto`)
- [ ] 🟡 Add idempotency (a double click currently deposits twice)
- [ ] 🟡 Lock the account row (`findByAccountNumberForUpdate`)
- [ ] 🟡 Use Spring's `org.springframework.transaction.annotation.Transactional`, not `jakarta.transaction.Transactional`
- [ ] 🟡 `user` can be null (unknown email) → NPE
- [ ] 🟡 Fix the injected constructor dependencies (`UserService` is injected twice and `UserAccountService` is unused)
- [ ] 🟡 Write the admin ID to an audit log

---

## Phase 4 — Ledger / accounting integrity

- [ ] 🔵 Create system accounts: fee revenue, cash/settlement, promo
- [ ] 🔵 Every transaction must produce balanced entries:
  - [ ] Transfer: debit the sender (amount + fee), credit the receiver (amount), credit fee revenue (fee)
  - [ ] Deposit: debit cash/settlement, credit the user
  - [ ] Withdrawal: debit the user, credit the agent, credit fee revenue
- [ ] 🔵 Make `validateDoubleEntry` check Σdebits == Σcredits across all entries (it currently compares a value with itself)
- [ ] 🔵 Use the account's currency in ledger entries (USD is hard-coded)
- [ ] 🔵 Add `balance_after` to ledger entries and make the ledger append-only (no update or delete)
- [ ] 🔵 Add a daily reconciliation job that checks Σ balances == Σ ledger

---

## Phase 5 — Additions and hardening

- [ ] 🔵 Tests (none exist for custom code):
  - [ ] Fee math
  - [ ] Balance and minimum balance
  - [ ] Self-transfer
  - [ ] Concurrent transfers (parallel threads)
  - [ ] Idempotency replay
  - [ ] Authorization (user vs admin)
- [ ] 🔵 Fraud: compute `riskScore` and `FraudStatus` instead of hard-coding them to 0 / `CLEAN`; add velocity checks
- [ ] 🔵 Audit log for admin and sensitive actions
- [ ] 🔵 Customer-facing DTOs that never expose other users' balances, logins or internal IDs
- [ ] 🔵 CI/CD (`.github/workflows/deploy.yml`):
  - [ ] Stop deploying with `-DskipTests`; add a test job before deploying
  - [ ] Add gitleaks and a dependency scan (OWASP / Dependabot)
  - [ ] Add a health check after restart, plus a rollback (keep the previous jar)
- [ ] 🔵 `ExpoPushService`: use an SLF4J logger instead of `System.out`/`System.err`, and add retries / dead-letter handling
- [ ] 🔵 Cleanup:
  - [ ] Remove commented-out code (`TransactionRecordServicePlus`)
  - [ ] Remove unused injected beans
  - [ ] Make error messages consistent (FR/EN mix)
