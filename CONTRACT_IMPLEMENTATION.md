# Contract Management foundation

Implemented on 14 October 2026. This foundation follows the existing Spring Boot controller/service/repository structure and Angular standalone component, signal, Reactive Forms, HttpClient and API-error conventions. Approval is intentionally unavailable. This is not a complete reservation or contract offering implementation.

## Inspection and schema

Inspected the existing Hotel and Room Type entities, DTOs, repositories, controllers, services, scoped/global exception handlers, tests, Angular routing, shell, forms, services and shared error handling. Read the supplied SQL export at C:/Users/sesandin/Downloads/suntravels.sql as reference; did not execute it.

The export confirms:

- contract: INT identity ID, unique VARCHAR(45) code, DATE validity, DECIMAL(5,2) markup, CHAR(3) currency, VARCHAR(20) status, hotel foreign key.
- contract_room_offering: identity ID, DECIMAL(10,2) rate, room count, max adults, status, contract and room-type foreign keys; UNIQUE(contract_id, room_type_id).
- reservation: check_in_date, check_out_date, status, customer foreign key, currency and creation timestamp.
- reservation_item: foreign keys to reservation and offering, number_of_adults, booked_rate_per_person_per_night DECIMAL(12,2), booked_markup_percentage DECIMAL(7,4), booked_selling_price DECIMAL(14,2).

No offering or reservation entities, repositories or services exist in the application. Those relationships must be available before safe approval can be enabled.

Contract mappings explicitly use CHAR for currency and VARCHAR for the status enum rather than generating a MySQL ENUM. Hibernate ddl-auto=validate is unchanged. Live validation could not run because database credentials are unset.

## Added and modified files

Paths relative to the repository root.

Added backend main files under backend/src/main/java/com/suntravels/:

- entity/Contract.java
- entity/ContractStatus.java
- repository/ContractRepository.java
- dto/ContractRequest.java
- dto/ContractMarkupRequest.java
- dto/ContractResponse.java
- service/ContractService.java
- controller/ContractController.java
- exception/ContractException.java
- exception/ContractExceptionHandler.java

Added backend tests under backend/src/test/java/com/suntravels/:

- service/ContractServiceTest.java
- controller/ContractControllerTest.java

Added frontend files under frontend/src/app/:

- models/contract.model.ts
- services/contract.service.ts
- features/contracts/contract-list.ts
- features/contracts/contract-list.html
- features/contracts/contract-list.scss
- features/contracts/contract-list.spec.ts

Modified frontend/src/app/app.routes.ts, app.html, app.ts and app.spec.ts to enable /contracts, remove Contracts from the upcoming list and verify navigation. Added this report. Existing Hotel and Room Type implementation files, database configuration and dependency manifests remain unchanged.

## API contract

Base URL: http://localhost:8080/api/contracts.

| Method | Path | Request body | Success |
| --- | --- | --- | --- |
| GET | /api/contracts | None | 200, array of response objects |
| GET | /api/contracts/{id} | None | 200, one response object |
| POST | /api/contracts | Header payload below | 201, DRAFT response |
| PUT | /api/contracts/{id} | Complete header payload below | 200, DRAFT response |
| PATCH | /api/contracts/{id}/markup | Markup payload below | 200, APPROVED response |
| PATCH | /api/contracts/{id}/suspend | None (frontend sends {}) | 200, SUSPENDED response |
| PATCH | /api/contracts/{id}/terminate | None (frontend sends {}) | 200, TERMINATED response |

Header request, used by POST and PUT:

```json
{
  "contractCode": "ST-2026-001",
  "hotelId": 7,
  "validFrom": "2026-01-01",
  "validTo": "2026-12-31",
  "markupPercentage": 15.00,
  "currencyCode": "USD"
}
```

Hotel ID 7 is an example only: use an ID returned by GET /api/hotels. The frontend loads hotels from that endpoint. There is no status field in the header DTO; any extra client-supplied status is ignored by deserialization and creation explicitly sets DRAFT.

Markup request:

```json
{ "markupPercentage": 20.50 }
```

Response shape (numbers may serialize without trailing decimal zeros):

```json
{
  "contractId": 1,
  "contractCode": "ST-2026-001",
  "hotelId": 7,
  "hotelName": "Example Hotel",
  "validFrom": "2026-01-01",
  "validTo": "2026-12-31",
  "markupPercentage": 15.00,
  "currencyCode": "USD",
  "status": "DRAFT"
}
```

Errors use {"message":"..."}; DTO validation additionally returns fieldErrors as a field-to-message map (dateRangeValid identifies a reversed date range). Invalid JSON, dates or numeric IDs return 400. Missing contracts or referenced hotels return 404. Duplicate codes, disallowed lifecycle operations, integrity conflicts and translated lock contention return 409. Database exception details are never returned.

No DELETE, approval, reapproval or general status endpoint exists. Calling PATCH /api/contracts/{id}/approve or /reapprove returns 404.

## Implemented rules and test evidence

- Creation always starts DRAFT, with no room offerings required.
- Codes are mandatory, trimmed, at most 45 characters and checked for uniqueness; the existing database unique index handles races and collation-dependent comparisons.
- Hotel existence is checked, without hardcoded operational IDs.
- LocalDate validity is inclusive, permits equal dates and rejects a reversed range.
- BigDecimal markup accepts 0 through 999.99, at most two fractional digits. 15.00 represents 15 percent. No currency conversion is performed.
- Currency must match three uppercase letters; this validates format rather than an ISO currency registry.
- All header fields may change only in DRAFT. APPROVED, SUSPENDED and TERMINATED header edits return 409.
- Only APPROVED contracts allow the dedicated markup operation.
- APPROVED -> SUSPENDED; DRAFT/APPROVED/SUSPENDED -> TERMINATED. Repeating termination is rejected. Suspension from any other status is rejected.
- Suspension, termination and markup operations write only the contract; no reservation is cancelled, transferred or repriced.
- Historical contracts remain listable and retrievable. There is no deletion.
- Service validation also runs independently of controller DTO validation.
- All existing-contract write operations read the current row under a pessimistic write lock within a transaction, before validating lifecycle status.
- Approval service calls fail closed without mutating or saving the contract. Tests assert unavailability; they do not claim offering, overlap or inventory rules pass.

The Angular screen provides listing, search, status filtering, create and DRAFT edit forms, APPROVED markup edits, fetched details, safe status changes with explicit confirmations, and real loading/error/empty/success states. Forms are typed. Backend field/conflict messages are displayed and edits survive failures. The editor receives focus when opened. APPROVED headers and historical commercial fields are not editable. Approval is disabled in eligible contract details and an explanatory availability message is always visible. The room offering section explains its missing dependency rather than showing invented data or an empty array as if loaded.

## Deliberately deferred approval and reapproval

Before enabling any endpoint, implement and test all of the following together:

1. Validate the persisted header and at least one valid offering, including offering status eligibility, positive rate/count/capacity and uniqueness. Offering lifecycle semantics must be defined by that module.
2. Permit consideration only from DRAFT or SUSPENDED. TERMINATED has no outgoing transitions.
3. Serialize approval decisions for a hotel by locking the existing HOTEL row. A lock on only the candidate contract does not stop two different candidates being approved simultaneously. Adopt a consistent locking order across approval, offering changes and reservation confirmation.
4. Re-read candidate state and offerings under that coordination. Reject another APPROVED contract for the same hotel where existing.validFrom <= candidate.validTo AND existing.validTo >= candidate.validFrom. Return 409 identifying the conflicting contract code/ID.
5. Enforce the conservative replacement policy through reservation_item -> contract_room_offering -> old contract and reservation (which belongs to customer). Match the same hotel and room types present in the candidate offerings. Consider only CONFIRMED reservations belonging to other contracts, including suspended or terminated old contracts.
6. Check actual occupied nights, independently of the old contract validity: assuming checkout is exclusive, reservation.checkIn <= candidate.validTo AND reservation.checkOut > candidate.validFrom. Confirm that night convention in the reservation module and reject inconsistent reservation data rather than silently ignoring it. Checkout exactly on candidate.validFrom occupies no candidate night; check-in on candidate.validTo does overlap.
7. Block approval when any relevant confirmed reservation consumes the overlapping hotel/room-type inventory, even if a numeric capacity calculation might appear to permit replacement. Do not cancel, migrate or transfer reservations.
8. Coordinate reservation confirmations with the same hotel lock and re-read contract eligibility and inventory. Otherwise a confirmation could race the replacement approval check. Offering mutations must use compatible coordination and preserve references.
9. Make validation and status change atomic. Failed approval must leave the original status unchanged. Verify simultaneous approvals and confirmation/approval races against MySQL using separate transactions.

Future tests must include inclusive date boundaries, different hotels, non-APPROVED old contracts, different room types, cancelled reservations, checkout boundaries, confirmed stays outside old contract dates, no valid offerings and concurrency races. These tests and rules are not implemented or reported as passing here.

Quotation and confirmation services must read current markup for new pricing and preserve existing booked snapshots; those services do not yet exist. This foundation never writes reservation_item snapshots, but end-to-end pricing semantics cannot yet be verified.

## Verification

- Full backend suite was attempted: 71 tests, 70 passed, 0 assertion failures, 1 application-context error. MySQL rejected the unresolved DB_USERNAME placeholder because DB_USERNAME/DB_PASSWORD are unset. Consequently live schema validation and repository integration are unverified.
- Database-independent backend suite: 70 passed, 0 failures/errors, including 27 contract service cases and 7 contract controller tests. Existing Hotel and Room Type tests passed.
- Backend package (compilation, these 70 tests, executable JAR repackaging): passed.
- Angular unit tests: 36 passed across 7 files, including 11 contract screen tests.
- Angular production build: passed.
- Git diff/check: checked tracked changes and all added files for whitespace errors. No schema changes or commits were made.

Commands from backend/ (offline because dependencies are already cached):

```powershell
.\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" test
.\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" "-Dtest=*ServiceTest,*ControllerTest,HotelRegistrationValidationTest" package
```

The noop Maven cache-lock option avoids sandbox access to lock files outside the workspace; no Maven configuration was changed. The normal command initially failed on sandboxed .m2 lock-file creation. npm.cmd is used because PowerShell's execution policy blocks npm.ps1; no execution policy was changed.

Commands from frontend/:

```powershell
npm.cmd test -- --watch=false
npm.cmd run build
```

## Manual integration checks

1. Set DB_USERNAME and DB_PASSWORD to your existing MySQL credentials; run the full backend tests and start the backend with ddl-auto=validate. Verify the existing contract table validates successfully. Do not import the dump or change the schema.
2. Open /contracts with both applications running. Confirm active navigation, responsive table scrolling, hotel dropdown, no mock records and loading/error/retry behavior.
3. Create a contract using a real hotel ID. Confirm the 201 response is DRAFT and the record survives refresh. Add an extra status=APPROVED in a direct POST and verify it still creates DRAFT.
4. Exercise code lengths 45/46, blank code, equal/reversed dates, markup 0/999.99/1000/-0.01/1.001, lowercase currency and nonexistent hotel. Expect the documented 400/404/409 responses and no successful writes for rejected requests.
5. Create duplicate codes and run simultaneous creates using the same code; verify only one succeeds and errors contain no database details. Check case variants according to your existing database collation.
6. Edit each DRAFT header field and reload. With an existing APPROVED test contract supplied through your normal data setup, verify PUT returns 409 while markup PATCH succeeds. Do not bypass approval in operational data just to test this foundation.
7. For an existing APPROVED test contract with confirmed reservations, record all reservation statuses and booked snapshot values. Update markup, suspend, then terminate; verify those reservation values and offering links are unchanged. Reject markup on SUSPENDED/TERMINATED, suspension on DRAFT and every outgoing TERMINATED operation.
8. Verify historical details remain retrievable, approval/reapproval endpoints are absent, UI approval actions are unavailable, and Hotel/Room Type management still works.
9. Use two database transactions to exercise lock contention and concurrent markup/suspend/terminate requests. Mocked tests establish application behavior, not real MySQL isolation or lock-timeout behavior. There is no added version column, so sequential DRAFT edits may overwrite earlier edits; reload records before editing in another client.

No database tables were modified, dropped, recreated or migrated. No SQL dump was imported. No Git commit was created.
