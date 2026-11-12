# CLAUDE.md — ZaraMoney Backend

Fintech wallet backend (P2P transfers, agent withdrawals, admin deposits) for a mobile app (Expo / React Native client, not in this repo).

## Stack
- JHipster 8.11.0 monolith, **no client** (`skipClient: true`); Spring Boot 3.4.5, Java 17, Maven (`./mvnw`)
- PostgreSQL + Liquibase, Hibernate L2 cache (Ehcache)
- JWT auth (HS512, JHipster `SecurityJwtConfiguration`), stateless
- Cloudinary for KYC images (`CloudinaryConfig`, `UploadFileService`)
- Expo push notifications (`ExpoPushService`, WebClient → `https://exp.host/--/api/v2`)
- Package root: `com.zekodnix.zaramoney`

## Commands
- Run dev: `./mvnw` (profile `dev`); DB: `npm run docker:db:up`
- Tests: `./mvnw verify` (or `npm run backend:unit:test`). Integration tests (`*IT`) use Testcontainers Postgres; with Docker 29 add `-Dtestcontainers.version=1.21.4` until the pom pins it. Single IT: `-Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=SomeIT -Dfailsafe.failIfNoSpecifiedTests=false`
- Local secrets: copy `.env.example` → `.env` (git-ignored, loaded by the dev profile). Prod reads them from environment variables (no defaults)
- Prod jar: `./mvnw clean package -Pprod -DskipTests`
- Format: `npm run prettier:format` (Prettier + prettier-plugin-java; husky pre-commit runs lint-staged)

## Code conventions — the "Plus" pattern
JHipster-generated files (`*Service`, `*Resource`, `*QueryService`, `*Mapper`, `*Criteria`, domain, DTOs, Liquibase changelogs from `.jhipster/*.json` / `app.jdl`) are kept close to generated output.
**Custom business logic lives in `*Plus` classes**, which wrap the generated services:
- `service/*ServicePlus.java`, `web/rest/*ResourcePlus.java`
- Other custom code: `BackOfficeService`/`BackOfficeResource` (`/api/admin/backoffice`), `UserAccountService`/`UserAccountController` (`/api/user-account`), `TransactionValidatorService`, `UploadFileService`, `CloudinaryService`, `ExpoPushService`, `service/utils/MaskingUtils`
- Custom request bodies are in `web/rest/vm/` (`TransactionRecordVM`, `WithdrawalTransactionRecordVM`, `UserAccountVM`)

Prefer putting new logic in `*Plus` classes rather than editing generated files, unless the generated file itself is the problem (e.g. security on generated CRUD resources).

## Domain
Entities: `User` (login = email), `UserDetailsAccount` (KYC, phone, isAgent, expoPushToken), `BankAccount` (accountNumber unique, balance decimal(21,2), currency, status), `TransactionRecord`, `LedgerEntry` (DEBIT/CREDIT), `TransactionFee`, `TransactionLimit`, `IdempotencyRecord` (key_hash unique), `Notification`.

## Money flows (key files)
- **P2P send** — `POST /api/plus/transaction-records/send` → `TransactionRecordServicePlus.createTransactionRecord`
  idempotency check → transaction password → `BankAccountServicePlus.lockAccounts` (PESSIMISTIC_WRITE, ordered by account number) → `TransactionValidatorService.checkAmountValidation` → reserve idempotency → fee → `process()` → fee record → ledger entries → complete idempotency → notifications
- **Withdrawal to agent** — `POST /api/plus/transaction-records/withdrawal` → `withdrawalTransaction` (receiver must have `isAgent`)
- **Admin deposit** — `POST /api/admin/backoffice/user/deposit` → `BackOfficeService.addMoneyToUserAccount`
- **Onboarding/KYC** — `POST /api/user-account/create` (multipart) → creates USD `BankAccount` (+ default `TransactionLimit`) and `UserDetailsAccount`
- Rules: `MIN_BALANCE = 5` (`TransactionValidatorService`); fees in `TransactionRecordServicePlus.calculateTransactionFee` / `calculateWithdrawalFee`
- All amounts are `BigDecimal`, scale 2, `RoundingMode.HALF_UP`.

## Security config
`config/SecurityConfiguration.java`: `/api/admin/**` → ROLE_ADMIN. The 8 JHipster-generated CRUD paths (`/api/bank-accounts/**`, `/api/transaction-records/**`, `/api/ledger-entries/**`, `/api/transaction-fees/**`, `/api/transaction-limits/**`, `/api/idempotency-records/**`, `/api/notifications/**`, `/api/user-details-accounts/**`) → ROLE_ADMIN, both by URL rule and by class-level `@PreAuthorize`; `AdminOnlyCrudResourcesIT` guards this. The nested customer paths `/api/bank-accounts/plus/**` and `/api/ledger-entries/plus/**` are matched first and stay authenticated-only. Everything else under `/api/**` → just authenticated. Put new customer endpoints under `/api/plus/...` and check ownership in the service; when adding a matcher, keep the specific `/plus` rules before the admin-only CRUD rules.

## Deployment
`.github/workflows/deploy.yml`: push to `develop` → build `-Pprod -DskipTests` → scp the jar to `/opt/zaramoney/` → `systemctl restart zaramoney`. `develop` is the main branch.

## Known issues / roadmap
See **`TODO.md`** — the prioritized list of security issues and bugs from the 2026-11-11 review (critical: open CRUD endpoints, committed secrets, fee excluded from balance check). Check it before working on transfers, ledger, uploads or auth, and tick items there as they get fixed.

## Working rules
- Never commit secrets; config secrets should come from environment variables.
- Any change to money movement needs: row locking, idempotency, a balanced ledger, and tests (including concurrency).
- Schema changes go through new Liquibase changesets — never edit an already-applied changelog.
- Never commit changes unless i ask you to do so.
