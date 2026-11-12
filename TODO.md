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
- [ ] `TechnicalStructureTest` (ArchUnit layering) reports 0 tests run, so the layer rules are never checked; existing code already breaks them (services use `web.rest.errors` and VMs). Decide: fix the engine setup and relax the rules, or delete the test
- [ ] Testcontainers 1.20.6 (managed by Spring Boot 3.4.5) can't talk to Docker 29 ("client version 1.32 is too old"). Fix: pin `<testcontainers.version>1.21.4</testcontainers.version>` (or newer) in `pom.xml`. For now tests run with `./mvnw verify -Dtestcontainers.version=1.21.4`
- [ ] `DeviceSessionIT` is flaky: each run, one random test fails with 401 (seen 2026-11-12, also on a clean checkout of `develop`). The failing test reports ~3570 s elapsed in a ~35 s run, which points at a wall-clock jump (WSL) breaking the ±60 s signature window / token expiry; not yet confirmed
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
- [x] JWT validity is 24h, or 30 days with remember-me → replaced by the token design in item 13 (15-min tokens + refresh)

### 🚀 Deploying Phase 2 (checklist)
- [ ] **Mobile app must ship together with the backend**, because the API changed:
  - [ ] `/send` and `/withdrawal`: send `pin` (not `password`); `idempotencyKey` is 8–128 chars (use a UUID per attempt, reuse it only for retries)
  - [ ] New "create your PIN" screen (`GET`/`PUT /api/plus/security/pin`), shown when `pinSet` is false. **Existing users have no PIN yet and can't send until they set one**
  - [ ] Responses of `/send`, `/withdrawal`, `GET /api/plus/transaction-records[/{id}]` and the ledger endpoints are now `TransactionReceiptDTO`s (no `sender`/`receiver` objects)
  - [ ] Onboarding: both pictures required; show "verification pending" (KYC `PENDING`) and handle 403 `kycnotverified`, 423 `pinlocked`, 429 `loginlocked` / `ratelimited`, 409 `accountalreadyexists` / `idempotencykeyreused`
- [ ] nginx sends `X-Forwarded-For` (see `application-prod.yml`), otherwise all users share one IP for the rate limits
- [ ] Liquibase runs automatically on startup (new columns `account_type`, `request_hash`, table `transaction_pin`, `NOT VALID` check constraint); the promo account is created at startup
- [ ] After deploy: approve the KYC of users who should keep sending (existing users keep their current status)

### 🟠 13. Refresh tokens, revocation and device binding — ✅ DONE 2026-11-12 (backend), not yet deployed
Decisions: device-key signature (ECDSA P-256) on every customer request; admins (Postman / web tool) are exempt but get a 15-min session without refresh token; rollout behind the `device-binding: optional|enforce` flag.

**What was built**
- [x] Tables `user_session` (one per login = refresh-token family), `refresh_token` (SHA-256 hashes only, rotation chain) and `access_block` (blocked users / devices) — changeset `20261113000000_device_sessions.xml`
- [x] Three session types: `DEVICE` (keys, signed requests, refresh tokens), `ADMIN` (no keys, 15 min, no refresh), `LEGACY` (old app, 24h token, no refresh; only in `optional` mode)
- [x] Access token: 15 min, claims `sid`, `did`, `jti`. **Every access token must reference an active session**, checked on every request (`DeviceBindingFilter`) → revoking a session kills its access token immediately. Remember-me ignored
- [x] Refresh token: 256-bit random, rotated on every refresh, 30 days (capped by the 90-day absolute session lifetime)
- [x] Reuse detection: a rotated refresh token presented again after a 30 s grace → whole session revoked (409 `refresh_in_progress` inside the grace window for parallel refreshes). The refresh must be device-signed, so a stolen refresh token alone can neither refresh nor trigger the revocation
- [x] Device binding: login proves possession of the device key (challenge signed); every request is signed (headers below); money operations also need the biometric transaction-key signature (`X-Tx-Signature`): `POST /send`, `POST /withdrawal`, `PUT /api/plus/security/pin`
- [x] Replay protection: ±60 s timestamp window + nonce stored per session; the nonce is recorded only after a valid signature
- [x] One session per device (re-login on the same device replaces it); max 5 devices per user (oldest revoked)
- [x] Endpoints: `GET /api/auth/challenge`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `POST /api/auth/transaction-key` (password + PIN, re-register the biometric key), `GET /api/account/sessions`, `DELETE /api/account/sessions/{sid}`, `POST /api/account/sessions/revoke-all[?keepCurrent=true]`
- [x] Admin (`/api/admin/security`): `GET users/{login}/sessions`, `POST users/{login}/revoke-sessions`, `DELETE sessions/{sid}`, `POST|DELETE users/{login}/block`, `POST|DELETE devices/{deviceId}/block`
- [x] Automatic revocation: password change (other devices), password reset (all), PIN change (other devices), PIN locked after 5 wrong PINs (all), refresh-token reuse (that session), user/device blocked (all)
- [x] Daily purge of old refresh tokens (sessions are kept as audit trail)
- [x] Rate limits: challenge and refresh 30/min/IP
- [x] Tests: `DeviceSignatureTest` (7 unit), `DeviceSessionIT` (12 end-to-end with real keys and JWTs), `TokenAuthenticationIT` (token without/with unknown session refused)

**Mobile contract** (share with the app team)
- Keys: two EC P-256 keys in Secure Enclave / Android Keystore, non-exportable. Device key: no user presence. Transaction key: biometric user presence. Public keys sent as base64 X.509 SubjectPublicKeyInfo (DER). Signatures: base64 DER (raw 64-byte r||s also accepted). Needs an Expo dev build with a native module (not Expo Go)
- Login: `GET /api/auth/challenge` → `{nonce}`; `POST /api/authenticate {username, password, deviceId, deviceName, platform, devicePublicKey, transactionPublicKey, nonce, signature}` where `signature = sign_device("login\n" + nonce + "\n" + deviceId + "\n" + username)` → `{access_token, id_token, expires_in, refresh_token, refresh_expires_in, session_id, session_type}`
- Every authenticated request and `/api/auth/refresh`: headers `X-Device-Id`, `X-Timestamp` (epoch ms), `X-Nonce` (UUID), `X-Signature = sign_device(canonical)`, where
  `canonical = METHOD + "\n" + PATH[?QUERY] + "\n" + X-Timestamp + "\n" + X-Nonce + "\n" + X-Device-Id + "\n" + base64(SHA-256(raw body bytes))` (empty body → hash of empty bytes; multipart → the literal `MULTIPART`)
- Money operations add `X-Tx-Signature = sign_transaction(canonical)` (biometric prompt)
- Refresh: `POST /api/auth/refresh {"refresh_token": "..."}` (signed, no Authorization header). Store the refresh token in `expo-secure-store` with `requireAuthentication: true`
- Error codes (401 unless noted): `device_signature_missing`, `device_signature_invalid`, `device_signature_expired` (+ `serverTime` to fix clock skew), `device_nonce_reused`, `device_mismatch`, `transaction_signature_invalid`, `session_invalid`, `session_revoked`, `challenge_invalid`, `refresh_token_invalid|expired|reused`, 409 `refresh_in_progress`, 403 `account_blocked` / `device_blocked`, 400 `device_binding_required` (enforce mode, old app)
- After a new fingerprint is enrolled (transaction key invalidated): `POST /api/auth/transaction-key {transactionPublicKey, password, pin}` (device-signed)

**Deploying item 13**
- [ ] Deploy with `DEVICE_BINDING_MODE=optional` (default). **All existing tokens become invalid** (they have no `sid`): everyone logs in once more
- [ ] Ship the app version that implements the contract above
- [ ] When (almost) all users run it, set `DEVICE_BINDING_MODE=enforce` and restart
- [ ] Admins: log in without device fields (15-min session, log in again when it expires)
- [ ] Single instance only: challenges, nonces and rate-limit counters are in memory. With several instances, move them to Redis

**Left for later**
- [ ] Notify the user (push / email) when a new device logs in, and on blocks / KYC decisions
- [ ] Count signature failures in Prometheus metrics (currently WARN logs only)
- [ ] Revoke sessions when an account is frozen/closed (no status-change flow exists yet; Phase 3)
- [ ] Play Integrity / App Attest at login
- [ ] Use a monotonic clock in `RateLimiter` (WSL/NTP wall-clock jumps reset the windows)

---

## Housekeeping (CLAUDE.md rules) — ✅ DONE 2026-11-12
- [x] `app.jdl` + `.jhipster/` brought up to date: `BankAccount.accountType`, `IdempotencyRecord.requestHash`, enums `BankAccountType`/`SessionType`/`AccessBlockType`, and the hand-written entities `TransactionPin`, `UserSession`, `RefreshToken`, `AccessBlock` (marked `skipServer`, so regeneration never creates CRUD for them). Validated with `jhipster jdl --json-only` (8.11.0): the 6 other entities are unchanged
- [x] `MOBILE_INTEGRATION_GUIDE.md` created (auth/device signing, onboarding, PIN, money, history, sessions, rate limits, error codes, changelog)

## Phase 3 — Transaction-flow bugs (`TransactionRecordServicePlus`, `TransactionValidatorService`) — ✅ DONE 2026-11-12

- [x] 🟡 Block self-transfers on `/send` (sender == receiver) (done in Phase 2)
- [x] 🟡 Check sender and receiver `AccountStatus`: sender not ACTIVE → 403 `accountnotactive`; receiver/agent not ACTIVE → 400 `receivernotactive` (`TransactionValidatorService.checkAccounts`, on the locked rows). Sender KYC was done in Phase 2
- [x] 🟡 Validate currencies: no FX, so both accounts must have the same currency and `currencySendAmount` / `currencyReceiveAmount` (now optional) must match it → 400 `currencymismatch`
- [x] 🟡 `transactionType` is set by the server (TRANSFER / WITHDRAWAL); the VM field is optional and ignored
- [x] 🟡 Fee in its own column `transaction_record.fee` (changeset `20261115000000_transaction_fee_column.xml`, backfilled from `transaction_fee`; `exchange_rate` reset to 1 where both currencies are equal, incl. the fake `2.81` of deposits). Added to `app.jdl` / `.jhipster` (nullable, so generated CRUD stays compatible)
- [x] 🟡 Withdrawal fee tier 5–20 is **0.8%** (was 8%)
- [x] 🟡 `calculateWithdrawalFee` throws `IllegalArgumentException` → 500; return a 400 instead (done in Phase 2)
- [x] 🟡 Fee tiers and minimum balance moved to `application.fees` (`BigDecimal`, quoted in YAML), read by `FeeSchedule`, validated at startup (ordered tiers, open-ended last tier, rate in [0, 1)). The unused FX constants were deleted (there is no FX)
- [x] 🟡 The sender is picked with `findByUserIsCurrentUser().stream().findFirst()`, which is arbitrary when a user has several accounts → optional `senderAccountNumber` on `/send` and `/withdrawal`; required (400 `senderaccountrequired`) when the user has several accounts; must be their own (400 `senderaccountnotfound`) (done 2026-11-12)
- [x] 🟡 The ownership check is useless (the account was already fetched for the current user); check it against the account that is actually locked (`requireOwnedBy` on the locked sender row, 403 `accountnotowned`)
- [x] 🟡 Duplicated blocks factored out:
  - [x] `process()` / `processWithdrawal()` → `TransactionValidatorService.move()` (works on the locked managed entities; the old `bankAccountService.save(dto)` merges are gone)
  - [x] `createTransactionRecord()` / `withdrawalTransaction()` → one `execute(MoneyRequest)`
- [x] 🟡 Expo pushes are sent **after commit** (`PushNotificationRequestedEvent` + `@TransactionalEventListener(AFTER_COMMIT)`); the notification row itself is saved in the transaction and rolls back with it. A user without push token is skipped instead of failing the transfer
- [x] 🟡 Bare `orElseThrow()` replaced in custom code: `lockAccounts` → 404 `accountnotfound`; `GET /api/user-account` before onboarding → 404 `noaccount` (was 500); current user → 401. The unmapped `UserDetailsAccountNotFoundException` (always a 500) was deleted. Remaining ones in JHipster files are guarded by `isPresent()`
- [x] 🟡 `LedgerEntryServicePlus.getCurrentUserEntryById` throws an NPE when the ledger entry is null (404 since Phase 2)
- [x] Amounts with more than 2 decimals are rejected (`@Digits(fraction = 2)` on `/send` and `/withdrawal`) instead of being rounded silently
- [x] Tests: `FeeScheduleTest` (19 unit: tier boundaries, rounding, 0.8%, broken config), `Phase3RulesIT` (7), `PushAfterCommitIT` (3); `TransactionRecordResourceIT` (92 generated) still green with the new column
- [x] ~~`@Version` on `BankAccount`~~ — replaced (decision 2026-11-12) by making `balance` **read-only in the admin CRUD** `/api/bank-accounts`: create always starts at 0.00, PUT keeps the current balance, PATCH drops it (`BankAccountServicePlus.adminCreate/adminUpdate/adminPartialUpdate`; the row is locked with `findByIdForUpdate` first, so an update built from a stale read cannot write an old balance back; a supplied balance is logged as a warning). Balances now only change through ledger-backed flows (deposit, transfer, withdrawal, bonus). Tests: `AdminBankAccountBalanceReadOnlyIT` (incl. stale-read and parallel transfers + PUTs). Still open on the same CRUD: changing `currency`, `user` or `accountType` of an account that holds money, and `DELETE` of a funded account, are not blocked yet

## Phase 3b — Back office deposit (`BackOfficeService.addMoneyToUserAccount`) — ✅ DONE 2026-11-12
- [x] 🟡 Validate `amount > 0` (`@DecimalMin("0.01")` + `@Digits(fraction = 2)` on `DepositDto`)
- [x] 🟡 Add idempotency: `idempotencyKey` (8–128 chars) is now **required**; key scoped to the admin + request hash, same as `/send` (retry → original deposit, other payload → 409 `idempotencykeyreused`, parallel double click → credited once)
- [x] 🟡 Lock the account row (`findByAccountNumberForUpdate`), then the SYSTEM settlement account (lock order 2512… before 9999…)
- [x] 🟡 Use Spring's `@Transactional`, not `jakarta.transaction.Transactional`
- [x] 🟡 Unknown e-mail → 404 `usernotfound`; unknown or SYSTEM account → 404 `accountnotfound`; account of another user → 400 `accountusermismatch`; non-ACTIVE account → 400 `accountnotactive` (was NPE / 500 / 403). E-mail matched case-insensitively
- [x] 🟡 Constructor cleaned up (no duplicate `UserService`, no unused beans)
- [x] 🟡 Admin ID recorded: the deposit's idempotency record holds the admin's user id + transaction reference, and an `AUDIT deposit ...` INFO log line is written. A real audit-log table stays in Phase 5
- [x] Balanced ledger: new SYSTEM **settlement** account `999900000002` (auto-created at startup): DEBIT settlement / CREDIT customer (the old code only credited the customer). `exchangeRate` is 1 (no more fake 2.81)
- [x] Class-level `@PreAuthorize(ADMIN)` on `BackOfficeResource` (on top of the `/api/admin/**` URL rule)
- [x] Tests: `BackOfficeDepositIT` (8): balanced ledger + admin recorded + customer view, retry/conflict, parallel double click, parallel deposits, invalid amounts/keys, 4xx targets, case-insensitive e-mail, customer gets 403
- [ ] **Admin tooling (Postman) must now send `idempotencyKey`** (a new UUID per deposit; reuse it only to retry)
- [ ] Note: the test pool has 1 connection, so the "parallel" ITs serialize at the pool; they prove idempotency and no lost update, but don't race two open transactions. A real lock-contention test needs a pool ≥ 2 (Phase 5)
- [x] Push notification sent after commit (Phase 3)

---

## Phase 4 — Ledger / accounting integrity

### ✅ Fee revenue + revenue report — pulled forward, DONE 2026-11-12
- [x] SYSTEM fee revenue account `999900000003` (auto-created at startup and by changeset `20261114000000_fee_revenue.xml`)
- [x] Transfers and withdrawals: DEBIT sender `amount + fee`, CREDIT receiver `amount`, CREDIT fee revenue `fee` (fee account locked last, only when fee > 0)
- [x] `validateDoubleEntry(List)` checks Σ debits == Σ credits over all entries of a transaction
- [x] Backfill of past fees (changeset `20261114000000-2`): CREDIT fee revenue + sender DEBIT corrected to `amount + fee`; customer balances untouched. Only transactions whose sender DEBIT equals the amount are touched
- [x] `GET /api/admin/backoffice/reports/revenue?from=YYYY-MM-DD&to=YYYY-MM-DD&groupBy=day|month|year` (Kinshasa time): transfer / withdrawal fees, fee count, volumes, bonus cost, deposits (info, not income), net = fees − bonuses, all-time fee account balance
- [x] Tests: `FeeRevenueIT` (8): fee ledger for transfer/withdrawal, no-fee transfer, sender account selection, report deltas, grouping/validation, backfill SQL on old-style rows (idempotent)
- [ ] **Manual (prod, after deploy):** check the backfill: `select count(*) from transaction_fee f join transaction_record t on t.id = f.transaction_id where f.amount > 0 and t.transaction_status = 'COMPLETED' and not exists (select 1 from ledger_entry l join bank_account a on a.id = l.account_id where l.transaction_id = t.id and a.account_number = '999900000003');` should be 0 (otherwise those transactions had no `amount`-sized sender DEBIT and need a manual look)
- [ ] Every fee-bearing transfer now serializes on the fee account row (locked last, held until commit, ~ms). Fine at current volume; if it becomes a hotspot, split into N fee sub-accounts or post fees asynchronously
- [ ] Export (CSV) / dashboard for the report if needed

### Remaining

- [x] 🔵 System accounts: promo `999900000001`, settlement `999900000002`, fee revenue `999900000003`
- [x] 🔵 Every transaction must produce balanced entries:
  - [x] Transfer: debit the sender (amount + fee), credit the receiver (amount), credit fee revenue (fee)
  - [x] Deposit: debit cash/settlement, credit the user (Phase 3b)
  - [x] Withdrawal: debit the user, credit the agent, credit fee revenue
- [x] 🔵 Make `validateDoubleEntry` check Σdebits == Σcredits across all entries
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
- [ ] 🔵 App gaps found while writing `MOBILE_INTEGRATION_GUIDE.md`:
  - [ ] No endpoint to update the Expo push token after onboarding (reinstall / token rotation): add `PUT /api/plus/push-token`
  - [ ] No "mark notification as read" endpoint (`/api/plus/notifications`)
- [ ] 🔵 `ExpoPushService`: use an SLF4J logger instead of `System.out`/`System.err`, and add retries / dead-letter handling
- [ ] 🔵 Cleanup:
  - [ ] Remove commented-out code (`TransactionRecordServicePlus`)
  - [ ] Remove unused injected beans
  - [ ] Make error messages consistent (FR/EN mix)
