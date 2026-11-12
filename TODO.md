# ZaraMoney Backend — Security & Correctness TODO

Findings from the code review of 2026-11-11. Work top to bottom. Tick each item (`[x]`) once it is fixed **and** covered by a test.

Legend: 🔴 Critical · 🟠 High · 🟡 Bug · 🔵 Improvement

---

## Phase 1 — Do immediately

### 🔴 1. Lock down the JHipster CRUD endpoints (anyone logged in can edit balances) — ✅ DONE 2026-11-11
- [x] `/api/bank-accounts/**` lets any authenticated user POST, PUT, PATCH or DELETE, so `PATCH {"id":X,"balance":1000000}` works (`web/rest/BankAccountResource.java`)
- [x] Same problem on `/api/transaction-records/**`, `/api/ledger-entries/**`, `/api/transaction-fees/**`, `/api/transaction-limits/**`, `/api/idempotency-records/**`, `/api/notifications/**` and `/api/user-details-accounts/**`
- [x] Fix, in two layers:
  - [x] URL rules in `SecurityConfiguration`: these paths are now `ROLE_ADMIN`; `/api/bank-accounts/plus/**` and `/api/ledger-entries/plus/**` stay open to authenticated users
  - [x] Class-level `@PreAuthorize(ADMIN)` on the 8 generated resources
- [x] Integration test `AdminOnlyCrudResourcesIT`:
  - [x] `ROLE_USER` gets 403 on every verb of the 8 endpoints
  - [x] The nested `/plus` endpoints still return 200
- [x] Generated `*ResourceIT` tests now run as admin
- [ ] Check the mobile app doesn't call any of these generated endpoints (it should use the `/plus` ones)

### 🔴 2. Remove and rotate the committed secrets — code part DONE 2026-11-11, manual steps left
- [x] Prod DB password: `application-prod.yml` now reads `${SPRING_DATASOURCE_PASSWORD}` (URL and username can also be overridden)
- [x] JWT secret:
  - [x] prod reads `${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET}` (no default → fails fast)
  - [x] dev and test got new dev-only / test-only secrets
  - [x] removed from `.yo-rc.json` and `src/main/docker/jhipster-control-center.yml`
- [x] Cloudinary: prod reads `CLOUDINARY_CLOUD_NAME/API_KEY/API_SECRET` (required); dev reads the same with empty defaults
- [x] Dev loads a git-ignored `.env` (`spring.config.import: optional:file:.env[.properties]`); template in `.env.example`; `.env` added to `.gitignore`
- [x] TLS keystore password → `${SSL_KEYSTORE_PASSWORD:password}` (the default only fits the committed self-signed dev keystore)
- [x] Use a different JWT secret in dev and prod
- [x] **Manual: set the env vars on the server BEFORE the next push to `develop`** (a push deploys automatically, and the app now refuses to start without them). E.g. an `EnvironmentFile=/etc/zaramoney/zaramoney.env` (chmod 600) in the `zaramoney` systemd unit
- [x] **Manual: rotate everything** (old values must be treated as leaked):
  - [x] Postgres `zaramoney` user password
  - [x] New prod JWT secret (`openssl rand -base64 64`); this logs everyone out
  - [x] Cloudinary API secret (regenerate in the Cloudinary console)
- [x] Purge the old values from git history (git-filter-repo / BFG; requires a force-push and every clone to re-clone), then add gitleaks to CI (add it after the purge, or it will flag history)

### 🟡 Test infrastructure (found while running Phase 1 tests)
- [ ] Testcontainers 1.20.6 (managed by Spring Boot 3.4.5) can't talk to Docker 29 ("client version 1.32 is too old"). Fix: pin `<testcontainers.version>1.21.4</testcontainers.version>` (or newer) in `pom.xml`. For now tests run with `./mvnw verify -Dtestcontainers.version=1.21.4`
- [ ] 119 generated tests were already failing before the Phase 1 changes (verified on the original code). They fail on `JSON path "$.[*].createdAt"` (timestamp format/precision) in `BankAccountResourceIT`, `IdempotencyRecordResourceIT`, `LedgerEntryResourceIT` and `NotificationResourceIT`

---

## Phase 2 — Money logic and data leaks

> **Phase 2 (items 3–12) done 2026-11-12, not yet deployed.** Product decisions: KYC starts PENDING + admin review; keep the $5 bonus but fund it through the ledger; separate transaction PIN. See "Deploying Phase 2" below before pushing.

### 🔴 3. The balance check ignores the fee (balances can go negative) — ✅ DONE
- [x] `checkAmountValidation(amount, fee, accounts)` checks `balance - (amount + fee) >= MIN_BALANCE`; the fee is computed before validating
- [x] DB constraint `chk_bank_account_balance_non_negative`: `CHECK (balance >= 0 OR account_type = 'SYSTEM')`, added `NOT VALID` so the migration can't fail on old rows
- [ ] **Manual (prod):** look for negative balances (`select id, account_number, balance from bank_account where balance < 0 and account_type = 'CUSTOMER'`), fix them, then `ALTER TABLE bank_account VALIDATE CONSTRAINT chk_bank_account_balance_non_negative;`

### 🔴 4. The KYC / account-creation step hands out free money — ✅ DONE
- [x] `POST /api/user-account/create` returns 409 `accountalreadyexists` if the user already has a wallet or KYC record (checked before any upload)
- [x] The $5 bonus is kept but funded by a SYSTEM promo account (`999900000001`, auto-created at startup): BONUS transaction + DEBIT promo / CREDIT customer ledger entries. Accounts open at 0.00
- [x] KYC starts `PENDING`; `/send` and `/withdrawal` return 403 `kycnotverified` until approved
- [x] Admin review: `GET /api/admin/backoffice/kyc?status=PENDING`, `GET /kyc/{id}` (with 5-minute signed document links), `POST /kyc/{id}/approve`, `POST /kyc/{id}/reject`
- [ ] Notify the user (push) when KYC is approved or rejected
- [ ] Persist KYC decisions in an audit log (currently application log only; Phase 5)

### 🔴 5. Enforce the transaction limits — ✅ DONE
- [x] `TransactionLimitServicePlus.checkLimits` checks daily and monthly totals (all COMPLETED outgoing transactions, Kinshasa time) while the sender row is locked; 400 `dailylimitexceeded` / `monthlylimitexceeded`
- [x] Accounts without a limit row fall back to the defaults (1000 / 10000)

### 🟠 6. Every user's transactions are visible to everyone — ✅ DONE
- [x] `GET /api/plus/transaction-records` only lists the current user's transactions; `GET /{id}` returns 404 if it isn't theirs
- [x] `/api/ledger-entries/plus/transaction/{id}` returns 404 instead of 500 when not the user's

### 🟠 7. Responses leak other users' data — ✅ DONE
- [x] New `TransactionReceiptDTO` (reference, type, direction SENT/RECEIVED, amount, fee, total, status, date, own account number, counterparty name + masked account number). Used by `/send`, `/withdrawal`, the transaction list/detail and the ledger endpoints
- [x] Never contains the other party's balance, login/email or internal ids

### 🟠 8. Idempotency keys are global, not per user — ✅ DONE
- [x] Stored hash is `SHA-256(userId + ":" + key)`: another user's key can never match
- [x] Real endpoint stored; `request_hash` column (business fields, never the PIN) → 409 `idempotencykeyreused` on a different payload or endpoint
- [x] Replays are rebuilt from the transaction reference (old stored JSON bodies are no longer parsed)
- [x] Parallel duplicates: the reservation is flushed before the accounts are locked, so the second request blocks on the unique index and gets 409 (`DataIntegrityViolationException` → 409 in `ExceptionTranslator`)

### 🟠 9. Path traversal and unsafe uploads — ✅ DONE
- [x] Bytes uploaded directly to Cloudinary (no temp file, the client file name is never used)
- [x] SVG dropped; real type checked from magic bytes (JPEG/PNG/WEBP); 500KB max; both pictures required
- [x] KYC files uploaded as `type=private`; DB stores `"<publicId>.<format>"`; admins get 5-minute signed links. Uploads are deleted again if account creation rolls back
- [ ] **Manual:** documents uploaded before this change are still public on Cloudinary (old rows hold `http://...` URLs). Move them to private (or re-upload) and update the rows

### 🟠 10. Transaction password and brute force — ✅ DONE
- [x] Separate transaction PIN (`transaction_pin` table, BCrypt): `GET /api/plus/security/pin` → `{pinSet}`, `PUT /api/plus/security/pin {password, pin}` (requires the login password; rejects 1111/1234/4321-style PINs)
- [x] `/send` and `/withdrawal` take `pin` instead of `password`
- [x] PIN lockout: 5 wrong PINs → locked 30 min (423 `pinlocked`); the counter is saved in its own transaction so a rollback can't erase it. The PIN is verified **before** the transfer transaction opens (no nested transaction → no connection-pool starvation)
- [x] Login lockout: 5 failures in 15 min → 429 `loginlocked` for that login (in-memory)
- [x] Rate limiting (`RateLimitFilter`, in-memory): login 10/min/IP, register 5/h/IP, reset-password init 5/h + finish 10/h per IP, verify-account 30/min/user, send & withdrawal 10/min/user, set-PIN 5/h/user → 429 + `Retry-After`. Switch: `application.security.rate-limit-enabled`
- [ ] With more than one app instance, move the counters to Redis (Bucket4j + Redis)

### 🟠 11. Account number generation and enumeration — ✅ DONE
- [x] `SecureRandom` + retry on collision (unique constraint still the final guard)
- [x] `/verify` never reports SYSTEM accounts and is rate-limited; `GET /api/users` is admin-only
- [ ] (Optional) Luhn check digit so the app can catch typos — would need a format change for new accounts

### 🟠 12. Exposure through config and endpoints — ✅ DONE
- [x] `/management/prometheus` now requires ROLE_ADMIN (falls under `/management/**`). If Prometheus scrapes it, give the scraper an admin token or expose metrics on an internal port only
- [x] `server.forward-headers-strategy: native` in prod so rate limits see the real client IP behind nginx
- [ ] JWT validity is 24h, or 30 days with remember-me → replaced by the token design in item 13

### 🚀 Deploying Phase 2 (checklist)
- [ ] **Mobile app must ship together with the backend**, because the API changed:
  - [ ] `/send` and `/withdrawal`: send `pin` (not `password`); `idempotencyKey` is 8–128 chars (use a UUID per attempt, reuse it only for retries)
  - [ ] New "create your PIN" screen (`GET`/`PUT /api/plus/security/pin`), shown when `pinSet` is false. **Existing users have no PIN yet and can't send until they set one**
  - [ ] Responses of `/send`, `/withdrawal`, `GET /api/plus/transaction-records[/{id}]` and the ledger endpoints are now `TransactionReceiptDTO`s (no `sender`/`receiver` objects)
  - [ ] Onboarding: both pictures required; show "verification pending" (KYC `PENDING`) and handle 403 `kycnotverified`, 423 `pinlocked`, 429 `loginlocked` / `ratelimited`, 409 `accountalreadyexists` / `idempotencykeyreused`
- [ ] nginx sends `X-Forwarded-For` (see `application-prod.yml`), otherwise all users share one IP for the rate limits
- [ ] Liquibase runs automatically on startup (new columns `account_type`, `request_hash`, table `transaction_pin`, `NOT VALID` check constraint); the promo account is created at startup
- [ ] After deploy: approve the KYC of users who should keep sending (existing users keep their current status)

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

- [x] 🟡 Block self-transfers on `/send` (sender == receiver) (done in Phase 2)
- [ ] 🟡 Check sender and receiver `AccountStatus` (reject FROZEN or CLOSED accounts) and the sender's KYC status
- [ ] 🟡 Validate currencies: `currencySendAmount` and `currencyReceiveAmount` are ignored
- [ ] 🟡 Set the server side `transactionType` itself (it is currently client-supplied in `TransactionRecordVM`)
- [ ] 🟡 Store the fee in its own column instead of `exchangeRate` (`TransactionValidatorService.java:132`); deposits also store a fake `2.81` there
- [ ] 🟡 Check the withdrawal fee tiers in `calculateWithdrawalFee`: 5–20 → **8%** looks like a typo (0.8%?). It is non-monotonic: 20 costs $1.60, 21 costs $0.53
- [x] 🟡 `calculateWithdrawalFee` throws `IllegalArgumentException` → 500; return a 400 instead (done in Phase 2)
- [ ] 🟡 Move fee rates, the minimum balance and FX rates out of the code into `BigDecimal` config or DB values (no `double`)
- [ ] 🟡 The sender is picked with `findByUserIsCurrentUser().stream().findFirst()`, which is arbitrary when a user has several accounts → choose explicitly
- [ ] 🟡 The ownership check is useless (the account was already fetched for the current user); check it against the account that is actually locked
- [ ] 🟡 Several duplicated blocks to factor out:
  - [ ] `process()` / `processWithdrawal()`
  - [ ] `createTransactionRecord()` / `withdrawalTransaction()`
- [ ] 🟡 Send notifications and Expo pushes **after commit** (`@TransactionalEventListener(phase = AFTER_COMMIT)`); today a push can be sent for a transaction that later rolls back
- [ ] 🟡 Replace bare `orElseThrow()` calls with proper 4xx errors (unknown account → 404 or 400, not 500)
- [x] 🟡 `LedgerEntryServicePlus.getCurrentUserEntryById` throws an NPE when the ledger entry is null (404 since Phase 2)
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
