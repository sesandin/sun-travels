# Contract Room Offering and Seasonal Rate Management

Implemented on 14 October 2026 in the existing Spring Boot and Angular application.

## Outcome and scope

Contract details now load real room offerings and their seasonal periods. DRAFT contracts support adding and editing offerings. APPROVED contracts support dedicated base-rate and offering-status maintenance, and seasonal creation/editing. SUSPENDED and TERMINATED contracts retain readable history without maintenance actions. Approval/reapproval remain disabled and have no endpoints.

No schema changes, migrations, SQL dump execution, dependency changes, Git commits or changes to Hotel/Room Type implementation were made. Existing uncommitted contract foundation and shell changes were preserved. No operational mock records were added. Tests use isolated fixtures.

## Inspection and schema

Read the accessible, newer `C:/Users/sesandin/Downloads/suntravels (1).sql` as reference only. Its offering, seasonal and nightly-snapshot definitions match the requested schema, including monetary precision, inclusive date checks, offering/room-type uniqueness and the seasonal index. The application uses lowercase physical table names, matching this export.

Added mappings use Integer identity IDs, LocalDate dates, BigDecimal DECIMAL(10,2) rates and explicit VARCHAR(20) enum storage for offering status. Associations are required, lazy ManyToOne relationships without cascading to contracts, room types or reservation data. DTOs are returned instead of entities. `spring.jpa.hibernate.ddl-auto=validate` and database settings remain unchanged. Live schema validation is unverified because DB_USERNAME and DB_PASSWORD are unset.

Reservation entities/services and safe contract approval coordination still do not exist. These are dependencies of approval and confirmation, not blockers for offering/seasonal maintenance. No nightly-snapshot entity or CRUD endpoint was added.

## Implemented APIs

Base URL: `http://localhost:8080`. All successful writes return the saved DTO.

| Method | Endpoint | Success and restriction |
| --- | --- | --- |
| GET | /api/contracts/{contractId}/offerings | 200, array; missing contract returns 404 |
| GET | /api/offerings/{offeringId} | 200, offering DTO |
| POST | /api/contracts/{contractId}/offerings | 201; DRAFT only |
| PUT | /api/offerings/{offeringId} | 200; DRAFT structural editing only |
| PATCH | /api/offerings/{offeringId}/rate | 200; APPROVED only |
| PATCH | /api/offerings/{offeringId}/status | 200; DRAFT or APPROVED only |
| GET | /api/offerings/{offeringId}/seasonal-rates | 200, date-ordered array |
| POST | /api/offerings/{offeringId}/seasonal-rates | 201; DRAFT or APPROVED only |
| PUT | /api/offerings/{offeringId}/seasonal-rates/{seasonalRateId} | 200; DRAFT or APPROVED only; ID must belong to offering |

Offering POST/PUT payload (IDs must come from real API results):

```json
{
  "roomTypeId": 3,
  "ratePerPersonPerNight": 125.25,
  "contractedRoomCount": 12,
  "maxAdults": 3,
  "status": "ENABLED"
}
```

Offering responses include these fields plus `offeringId`, `contractId`, `roomTypeName` and `currencyCode`. Parent contract is derived from the URL or persisted offering; clients cannot reassign it.

Base-rate PATCH body:

```json
{ "ratePerPersonPerNight": 135.50 }
```

Status PATCH body:

```json
{ "status": "SUSPENDED" }
```

Seasonal POST/PUT body:

```json
{
  "validFrom": "2026-06-01",
  "validTo": "2026-06-30",
  "ratePerPersonPerNight": 165.50
}
```

Seasonal responses add `seasonalRateId` and `offeringId`. Numeric values may serialize without trailing decimal zeros. There are no offering/seasonal DELETE endpoints, priority/stacking controls, approval/reapproval endpoints or nightly snapshot CRUD operations.

Missing parents or a seasonal ID belonging to another offering return 404. Invalid fields, reversed/outside-contract dates, malformed JSON/dates/IDs or unknown enum values return 400. Duplicate offerings, inclusive overlaps, disallowed lifecycle operations, integrity conflicts and translated pessimistic-lock failures return 409. Errors use `{ "message": "..." }`; DTO validation additionally returns a `fieldErrors` map. SQL and lock exception details are not exposed.

## Business rules and transaction coordination

- Each offering has one contract and one existing generic room type. The existing UNIQUE(contract_id, room_type_id) remains the final database protection; application checks provide friendly conflicts.
- Rate must be strictly positive with at most eight integer and two fractional digits, up to 99999999.99. Inventory and maximum adults are positive Integers. Service validation runs independently of controller validation.
- DRAFT edits can change room type, rate, inventory, capacity and status. Duplicate/invalid updates are checked before mutation; no offering is deleted during any update.
- APPROVED structural PUT/add are rejected. Rate PATCH changes only base rate; status PATCH changes only ENABLED/SUSPENDED status. Neither status means sold out.
- SUSPENDED/TERMINATED contract maintenance is rejected. History remains retrievable.
- Seasonal periods belong to offerings. An inclusive overlap is `existing.validFrom <= requested.validTo AND existing.validTo >= requested.validFrom`. Different offering IDs are independently scoped. Consecutive periods and equal start/end dates are allowed.
- Every seasonal create/update validates the inclusive parent contract bounds and performs a fresh database overlap query, excluding the edited ID. Conflicts are detected before changing the entity.
- A DRAFT contract date edit now rejects a range that would exclude any existing seasonal period. This is the only added contract-header restriction and preserves the seasonal invariant when a parent changes.
- Offering/seasonal writes run in transactions. New offering creation locks CONTRACT. Existing-offering writes first look up a scalar contract ID, lock CONTRACT, then lock CONTRACT_ROOM_OFFERING. Seasonal updates additionally lock the edited seasonal row; overlap queries use a pessimistic current read to avoid a stale MySQL REPEATABLE READ snapshot. Parent association is immutable.
- Full future lock order is HOTEL -> CONTRACT -> OFFERING -> SEASONAL RATE. Current operations need no hotel lock and start at CONTRACT. This coordinates with existing contract lifecycle/header writes, but serializes maintenance of different offerings in the same contract. Future approval/confirmation operations must acquire HOTEL first and follow the same order. Direct database writers bypassing this convention are not protected by the application lock strategy.
- A read-only `OfferingService.effectiveRate(offeringId, occupiedDate)` helper selects the seasonal hotelier rate for an inclusive occupied date or falls back to base rate. It rejects dates outside contract validity and fails closed on overlapping persisted periods. It is not a quotation endpoint and does not enforce reservation eligibility, inventory, markup or confirmation coordination; future callers must implement those rules atomically.
- No production code reads or writes reservation items or nightly snapshots. Rate changes cannot recalculate snapshots through these APIs. Real end-to-end quotation/confirmation behavior remains deferred.

## Frontend

`ContractList` embeds `OfferingPanel` after fetching contract details. Each offering embeds its independently loaded `SeasonalRates` panel. Real APIs supply all records and identifiers. Room types load from the existing generic room-type API when a DRAFT editor opens.

Typed Reactive Forms validate positive rates, whole inventory/capacity values, required fields, date ordering and parent bounds. DRAFT structural, APPROVED base-rate and offering-status forms are separate; approved payloads contain only the permitted field. Status changes require an explicit form submission. Historical screens expose no maintenance controls. Seasonal maintenance remains available for a suspended offering when its parent contract is DRAFT/APPROVED, since offering suspension controls eligibility for new business rather than deleting seasonal terms.

Panels show loading, empty, retry, success and backend-error states. Failed saves preserve entered values and existing displayed rows. Seasonal periods show inclusive start/end dates, parent currency, rate override and shared inventory explanations. Forms have labels, legends, visible focus styling, editor focus and alert/status announcements. Existing approval buttons remain disabled with explanatory text. No full browser integration session was performed.

## Files added

Backend main, relative to `backend/src/main/java/com/suntravels/`:

- entity/OfferingStatus.java
- entity/ContractRoomOffering.java
- entity/OfferingSeasonalRate.java
- repository/OfferingRepository.java
- repository/SeasonalRateRepository.java
- dto/OfferingRequest.java
- dto/OfferingRateRequest.java
- dto/OfferingStatusRequest.java
- dto/OfferingResponse.java
- dto/SeasonalRateRequest.java
- dto/SeasonalRateResponse.java
- service/OfferingService.java
- controller/OfferingController.java
- exception/OfferingException.java
- exception/OfferingExceptionHandler.java

Backend tests, relative to `backend/src/test/java/com/suntravels/`:

- service/OfferingServiceTest.java
- controller/OfferingControllerTest.java
- integration/OfferingMySqlIntegrationTest.java

Frontend, relative to `frontend/src/app/`:

- models/offering.model.ts
- services/offering.service.ts
- features/contracts/offering-panel.ts
- features/contracts/offering-panel.html
- features/contracts/offering-panel.scss
- features/contracts/offering-panel.spec.ts
- features/contracts/offering-validators.ts
- features/contracts/seasonal-rates.ts
- features/contracts/seasonal-rates.html
- features/contracts/seasonal-rates.spec.ts

Project root: `OFFERING_SEASONAL_IMPLEMENTATION.md` (this report).

## Existing files modified by this extension

- backend/src/main/java/com/suntravels/service/ContractService.java — reject header dates excluding persisted seasonal periods.
- backend/src/test/java/com/suntravels/service/ContractServiceTest.java — repository injection and date-shrink conflict test.
- frontend/src/app/features/contracts/contract-list.ts — embed offering panel.
- frontend/src/app/features/contracts/contract-list.html — actual offering details and updated availability text.
- frontend/src/app/features/contracts/contract-list.spec.ts — actual offering request/empty-state verification; approval remains disabled.

Pre-existing modified/untracked shell and contract foundation files were not staged, reset or committed.

## Verification

- Database-independent backend suite: **121 passed**, zero failures/errors. Includes **40 offering service cases**, **10 offering controller tests**, and **28 contract service cases**. Existing Hotel/Room Type tests pass.
- Backend executable JAR package: **passed**, including compilation, the 121 tests and Spring Boot repackaging.
- Full backend suite: **125 discovered, 121 passed, 0 assertion failures, 1 application-context error, 3 skipped MySQL integration tests**. MySQL rejects unresolved `${DB_USERNAME}` because credentials are unset. This is an environmental context failure; live schema validation is not established.
- Angular: **57 passed across 9 files**, including **11 offering panel** and **10 seasonal panel** tests. Existing contract, Hotel, Room Type and shell tests pass.
- Angular production build: **passed** (initial bundle approximately 389.46 kB; estimated transfer 95.06 kB).
- Git whitespace checks: tracked changes and added/untracked source/report files checked with `git diff --check` and `git diff --no-index --check`; no whitespace errors. Git's existing LF/CRLF conversion notices are not whitespace failures.

Focused tests cover creation, duplicates, invalid rates/counts, missing parents, DRAFT edits, approved structural immutability and narrow maintenance, historical restrictions, seasonal bounds, inclusive query parameters, update exclusion, conflict preservation, nightly base/override selection and read-only behavior. Angular tests cover real request methods/payloads, lifecycle controls, forms, retries and conflict/field feedback.

Mocked tests verify application logic and locking call order, not executed SQL semantics or real MySQL isolation. The three opt-in MySQL tests exercise (1) two separate concurrent transactions sharing an inclusive seasonal boundary with exactly one committed winner, (2) actual overlap queries, self exclusion, adjacent periods and different offerings, and (3) unchanged confirmed reservation/item/nightly snapshots after approved rate, seasonal and status maintenance. They were compiled but **skipped**, so concurrency and persisted snapshot preservation are not claimed as verified.

Commands from `backend/`:

```powershell
.\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" test
.\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" "-Dtest=*ServiceTest,*ControllerTest,HotelRegistrationValidationTest" package
```

The cache-lock option permits the existing cached dependencies in the sandbox; no Maven configuration was changed. Full-suite and final package logs are under ignored `backend/target/offering-full-test.log` and `backend/target/offering-package.log`.

Commands from `frontend/`:

```powershell
npm.cmd test -- --watch=false
npm.cmd run build
```

## Required manual browser and MySQL verification

1. Configure DB_USERNAME/DB_PASSWORD for a database with the existing schema. Run the full backend tests and start the backend with ddl-auto=validate. Do not execute the dump or change tables.
2. For opt-in tests use a disposable MySQL 8 test database that already has the schema. Override `SPRING_DATASOURCE_URL`, set credentials and set `SUNTRAVELS_MYSQL_TESTS=true`, then run the command below. Tests create dedicated fixture rows and normally remove only their own rows. They use test-only approved-state setup, not a production approval shortcut. Do not run them against operational data.
3. Open `/contracts` with both servers running. Open real DRAFT details, add an offering from the fetched room types, refresh, edit all fields and confirm persistence. Check responsive layout, keyboard navigation, focus, labels and error announcements.
4. Create a duplicate room type and invalid monetary/count values. Verify 400/404/409 responses, unchanged existing rows and form retention. Exercise the same failures with direct API requests.
5. Add June 1–30 and July 1–31 periods within a real contract. Reject June 30–July 10 for the same offering, allow the same dates for another offering, and allow one-night periods. Test self-edit, another-offering seasonal ID, out-of-contract dates and rate limits.
6. Attempt to shrink contract dates across a seasonal period; expect 409 and an unchanged header. Extend dates or edit a range containing all periods; expect success.
7. With APPROVED contracts provided through normal test data setup, verify adding/structural PUT fails, base-rate PATCH and offering status updates succeed, and seasonal creation/editing succeeds. Verify SUSPENDED/TERMINATED contract writes fail and their history remains visible. Keep approval disabled.
8. Compare confirmed reservation statuses, offering references, booked item snapshots and nightly snapshots before/after allowed maintenance. Verify no cancellation, migration or repricing occurs. Future quotation/confirmation code still needs its own snapshot and inventory tests.
9. Exercise two transactions for same-offering seasonal insert/update races and contract suspension/date-edit races. Test lock timeout/deadlock conflict translation. Sequential form edits have no version column and can overwrite an earlier completed edit; refresh before editing from multiple clients.
10. Regression-check Hotel, Room Type, contract create/edit/markup/lifecycle screens. Verify no offering/seasonal deletion or approval/reapproval action is available.

Opt-in MySQL command, from `backend/`, after setting the test environment variables:

```powershell
.\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" "-Dtest=OfferingMySqlIntegrationTest" test
```

## Deferred work

Contract approval/reapproval and replacement-reservation protection; hotel-level coordination with confirmations; Search, Customer and Reservation workflows; inventory availability; selling-price calculation/markup application; immutable snapshot creation at confirmation; seasonal deletion; promotional priorities/discount stacking; currency conversion; optimistic version-based edit conflict detection. No production reservation or nightly-snapshot CRUD was implemented.
