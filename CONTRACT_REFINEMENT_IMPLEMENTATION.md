# Contract refinement, approval and reapproval

Implemented on 14 October 2026 using the existing Spring Boot/Angular structure. This report supersedes the earlier counter-table audit and the earlier reports' disabled-approval/manual-code behavior.

## Outcome

New contracts receive backend-generated C001/C002/... codes using their actual MySQL IDENTITY IDs. Contract codes are immutable through the APIs. Existing codes are preserved exactly, including manually assigned codes unrelated to their IDs. No counter table or schema change is needed. The proposed database/proposed/2026-10-14_contract_code_counter.sql was deleted without execution; CONTRACT_REFINEMENT_AUDIT.md is marked superseded.

DRAFT approval and SUSPENDED reapproval now have validated, transactionally coordinated endpoints. Termination checks actual outstanding CONFIRMED reservation rows instead of blindly changing status. Suspension remains immediate and preserves commitments. Angular provides lifecycle confirmations and server conflict messages, removes code entry, and displays the generated reference after creation. Offering/seasonal business rules and screens remain intact.

No SQL dump was imported, live table modified/migrated/dropped/recreated, dependency added, operational mock record created, Git commit made or push performed. Hibernate ddl-auto=validate and existing database configuration remain unchanged. Tests use fixtures; opt-in database tests were not executed here.

## Repository and schema audit

Read ContractService/controller/repository/entity/DTOs, OfferingService and its repositories, Hotel entity/status/repository/service, scoped/global error handling, Angular ContractList/API service/models/panels, and service/controller/Angular/integration test conventions. There is no separate SeasonalRateService: seasonal maintenance remains in OfferingService.

Read the accessible newer C:/Users/sesandin/Downloads/suntravels (1).sql as schema reference only. It confirms the contract NOT NULL/UNIQUE code, INT identity, offering and seasonal mappings, and reservation -> reservation_item -> offering relationships. No Reservation/ReservationItem application entities or booking services exist. The implementation adds read-only JDBC access to their existing tables, not reservation CRUD or a fake zero-result checker. Customer/nightly/closure services remain absent.

## Exact contract endpoints

Base URL: http://localhost:8080.

| Method | Path | Success and rule |
| --- | --- | --- |
| GET | /api/contracts | 200, list including history |
| GET | /api/contracts/{id} | 200, contract DTO |
| POST | /api/contracts | 201, generated code and DRAFT status |
| PUT | /api/contracts/{id} | 200, DRAFT header update; code unchanged |
| PATCH | /api/contracts/{id}/markup | 200, APPROVED-only markup update |
| PATCH | /api/contracts/{id}/approve | 200, DRAFT -> APPROVED after all guards |
| PATCH | /api/contracts/{id}/reapprove | 200, SUSPENDED -> APPROVED after the same guards |
| PATCH | /api/contracts/{id}/suspend | 200, APPROVED -> SUSPENDED |
| PATCH | /api/contracts/{id}/terminate | 200, DRAFT/APPROVED/SUSPENDED -> TERMINATED only without outstanding commitments |

POST/PUT header payload now excludes contractCode and status:

~~~json
{
  "hotelId": 7,
  "validFrom": "2026-01-01",
  "validTo": "2026-12-31",
  "markupPercentage": 15.00,
  "currencyCode": "USD"
}
~~~

Use a real hotel ID from the existing hotel API. Additional client-supplied code/status fields are ignored by existing deserialization conventions; they cannot set or rename either server-owned field. Markup PATCH remains { "markupPercentage": 20.50 }. Lifecycle PATCH operations need no request body; Angular sends {}.

Response shape remains contractId, contractCode, hotelId, hotelName, validFrom, validTo, markupPercentage, currencyCode and status. No entity is serialized. No DELETE or unrestricted status endpoint exists. Existing offering/seasonal API endpoints and payloads remain supported.

Invalid input returns 400 with the existing fieldErrors format. Missing contracts/hotels return 404. Invalid transitions, invalid persisted approval terms, inclusive overlaps, reservation commitments, generated-code collisions and translated database/lock conflicts return 409 with business messages. Conflicting approved contracts identify code/ID; reservation conflicts identify a reservation ID. SQL, stack trace and temporary-code details are not exposed in API error messages.

## Automatic code generation and risks

1. Validate the header and lock the referenced hotel.
2. Insert DRAFT with a temporary unique TMP-UUID value of 40 characters, satisfying VARCHAR(45), NOT NULL and UNIQUE before obtaining the generated contract_id.
3. Construct C + the positive ID, padded to at least three digits using Locale.ROOT. Examples: ID 4 -> C004, ID 42 -> C042, ID 1000 -> C1000. Longer numbers are retained, not truncated.
4. Check whether a different contract already owns that final code. On conflict throw 409 and roll back the new row; never rewrite the existing row. The database unique index remains the final protection for races/collation differences.
5. Replace the temporary code and flush in the SAME transaction. Spring commits before the controller receives the successful DTO. Only the final reference is returned.

No MAX + 1, NULL-code insertion, yearly counter, manual code input or business UUID format is used. UUID is only an uncommitted temporary value. Header update never sets contractCode, so C001/legacy codes stay unchanged.

Technical risks disclosed before implementation: historical codes may collide with a future generated C+ID; that creation fails safely, consuming an ID without creating a committed contract. Failed insert/flush/rollback operations can leave identity gaps. Such gaps are expected and are not filled or reused by this implementation. A temporary UUID collision also fails safely under the unique constraint. Existing contract IDs are signed INT and finite; eventual database identity exhaustion is not silently worked around. External manual writes, triggers, identity resets or database rebuilds are outside the application's invariant and must not be used to rename/reuse references. No production code deletes contracts or reseeds identities.

Committed readers under normal MySQL isolation should never see temporary codes. Separate-transaction rollback and concurrency tests are provided but were skipped without credentials, so this behavior has not been demonstrated against live MySQL in this environment. Do not use READ UNCOMMITTED or expose insert-trigger/audit events containing temporary references as successful business references. Unknown commit outcomes from connection failures require reloading before retrying; generic database errors do not promise that a lost commit acknowledgement means no write occurred.

## Approval and reapproval rules

- Initial approval requires DRAFT; reapproval requires SUSPENDED. TERMINATED has no outgoing transition. APPROVED cannot approve/reapprove again.
- Associated hotel must exist and be ACTIVE. Contract approval never activates an INACTIVE hotel or modifies hotel status.
- Persisted contract code, date range, currency and markup must be valid. Past/present/future validity is accepted; approval does not require validFrom <= today.
- Require at least one ENABLED offering. Validate every persisted offering's room-type ID, positive DECIMAL(10,2) rate, positive inventory/capacity and valid enum status. Existing database foreign keys preserve the referenced room types. Duplicate room types are rejected defensively.
- Validate persisted seasonal rates, inclusive contract containment and same-offering non-overlap before approval, including data inserted outside the application. Offering/seasonal maintenance retains its existing validations.
- Requery other APPROVED contracts for the same hotel using inclusive existing.validFrom <= candidate.validTo AND existing.validTo >= candidate.validFrom. Exclude candidate. Adjacent dates are allowed; a shared boundary is not.
- Multiple DRAFT contracts may overlap. No other contract is automatically suspended, terminated or otherwise modified.
- Run invalid-reservation-data and replacement-commitment guards. Only after ALL checks pass set candidate APPROVED and save. Any validation failure leaves its original status unchanged.

## Reservation protection using existing tables

ReservationCommitmentRepository contains SELECT ... FOR UPDATE JDBC queries. It requires an existing transaction with Propagation.MANDATORY. It never inserts/updates/deletes reservations or snapshots, and never catches a query failure to return zero. Database verification failures propagate to a safe conflict response.

Termination joins reservation -> reservation_item -> contract_room_offering -> target contract and blocks where status is CONFIRMED and check_out_date > businessToday. This includes ongoing and future stays. Checkout today is completed because checkout is exclusive. Completed CONFIRMED history, PENDING and CANCELLED records do not block. The existing tested DRAFT -> TERMINATED transition is preserved with the same defensive guard. Staff may suspend immediately even when confirmed commitments remain.

Replacement approval uses the SAME hotel, other contract IDs and room types from freshly locked candidate offerings. It checks CONFIRMED, check_out_date > businessToday, check_in_date <= candidate.validTo and check_out_date > candidate.validFrom. Old contract status and old validity dates are deliberately not filters: old SUSPENDED/TERMINATED contracts and confirmed stays outside old contract dates still carry commitments. Completed history is excluded. Checkout exactly at candidate.validFrom does not overlap; check-in at candidate.validTo does. Unrelated room types/hotels do not block.

All candidate offering room types are checked, including suspended offerings. Otherwise later enabling an existing offering could bypass replacement protection. Candidate's own reservations are excluded from replacement conflicts during reapproval, but are checked for invalid data. Relevant confirmed records with checkout <= check-in or non-positive adult counts fail closed. Database NOT NULL/foreign-key constraints protect the required relationships; disabling those constraints is unsupported.

Candidate room IDs are passed as named SQL parameters from the locked offering rows. No earlier snapshot subquery supplies them. Duplicate reservation IDs from multiple reservation items do not weaken protection: any match blocks. No reservation is counted as additional sellable inventory, cancelled, transferred, repriced or migrated. Future availability must still deduct its own confirmed inventory commitments.

## Locking and concurrency boundaries

All contract writes now follow HOTEL -> CONTRACT; approval/termination then lock OFFERING rows and use current seasonal/approved-contract/reservation reads. Read only the scalar hotel ID before locking; retain no managed contract from before the hotel lock. After locking the contract, verify its hotel still matches that scalar lookup, or return a reload conflict. DRAFT hotel reassignment locks source/target hotels in ascending ID order before the contract, preventing a cross-hotel reversal. Its later access to the target hotel reacquires a lock already held, not a new lock in reverse order.

The hotel pessimistic-write lock serializes same-hotel approval/reapproval decisions, so two overlapping candidates cannot both pass the approved-overlap query through these APIs. Queries use pessimistic/current reads to avoid an earlier MySQL REPEATABLE READ snapshot. Existing offering/seasonal writes start at CONTRACT -> OFFERING -> SEASONAL and never subsequently acquire HOTEL; this remains compatible. They finish before an approval can re-read the locked contract/offering terms.

Future confirmation, reservation date/status/item mutations and hotel closure operations MUST use the SAME HOTEL lock before contract/offering/inventory/reservation locks, re-read eligibility and apply compatible ordering. No production confirmation service exists yet. A cooperative JDBC fixture test demonstrates the intended convention, not a completed booking workflow. Direct writers that bypass this convention can invalidate the application's coordination; a hotel lock alone is not a database constraint preventing arbitrary outside reservation inserts. Do not claim confirmation/closure race safety is already proven.

Lock timeout/deadlock errors are translated to 409 without internal details. No lock timeout configuration or version column was added. Long reservation joins can hold locks broadly and should be measured on real production-sized data; no supporting index/schema change was made. Sequential edits may still overwrite earlier completed edits.

## Dates, hotel policy and deferred sales eligibility

BusinessTimeConfig provides an injectable Clock in Asia/Colombo. Termination/replacement calculations use LocalDate.now(clock), with a fixed-clock unit test crossing UTC midnight. Contract validTo is inclusive, checkout exclusive, and no condition depends on booking creation time.

An expired APPROVED contract remains APPROVED as historical lifecycle state but has no eligible occupied nights after validTo. It is not automatically TERMINATED. Future-dated contracts can be approved before validity starts. Rejecting newly booked past occupied dates belongs to Reservation Management, which remains unimplemented.

Hotel and contract statuses are independent. Existing hotel emergency INACTIVE updates remain allowed regardless of bookings and do not cancel reservations or alter pricing. Contract suspension is not a planned hotel closure. Future closure maintenance must reject overlap with confirmed hotel occupancy using compatible hotel coordination. Future reservation views must warn when a confirmed booking's hotel is INACTIVE. No closure, relocation, compensation, booking/search/payment or reservation UI workflow was added.

APPROVED is only one eligibility condition for future sales. Reservation Management must recheck hotel activity, contract/occupied-night validity, offering status, closures, capacity, availability and pricing under the shared coordination before confirmation. Existing confirmed prices remain stored snapshots; later markup/base/seasonal maintenance must not recalculate them. No nightly-snapshot CRUD was introduced.

## Angular behavior

DRAFT rows/details expose Approve; SUSPENDED rows/details expose Reapprove; APPROVED exposes Suspend. Non-TERMINATED contracts expose the implemented guarded Terminate operation. Each action opens an explicit confirmation; the server makes final eligibility decisions. In-flight guards prevent duplicate submission and editor/action changes. Success updates list/detail status from the returned DTO; failure preserves the original UI state and displays the business conflict. Approval does not require the frontend to preload offerings to decide eligibility.

The create/edit form has no contractCode control or editable code input. Creation explains automatic generation; existing edits display the current immutable code. Successful creation displays the saved code in its notice. Typed forms retain all header validation and existing hotel loading/error behavior. Offering and seasonal components remain embedded in details and receive updated lifecycle state on later detail opening. Existing focus, label, loading, alert/status and responsive design conventions remain intact.

## Added files in this stage

- backend/src/main/java/com/suntravels/config/BusinessTimeConfig.java
- backend/src/main/java/com/suntravels/repository/ReservationCommitmentRepository.java
- backend/src/test/java/com/suntravels/integration/ContractMySqlIntegrationTest.java
- CONTRACT_REFINEMENT_IMPLEMENTATION.md

## Modified files in this stage

- backend/src/main/java/com/suntravels/service/ContractService.java
- backend/src/main/java/com/suntravels/controller/ContractController.java
- backend/src/main/java/com/suntravels/dto/ContractRequest.java
- backend/src/main/java/com/suntravels/repository/ContractRepository.java
- backend/src/main/java/com/suntravels/repository/HotelRepository.java
- backend/src/main/java/com/suntravels/repository/OfferingRepository.java
- backend/src/main/java/com/suntravels/repository/SeasonalRateRepository.java
- backend/src/main/java/com/suntravels/exception/ContractExceptionHandler.java
- backend/src/test/java/com/suntravels/service/ContractServiceTest.java
- backend/src/test/java/com/suntravels/controller/ContractControllerTest.java
- frontend/src/app/models/contract.model.ts
- frontend/src/app/services/contract.service.ts
- frontend/src/app/features/contracts/contract-list.ts
- frontend/src/app/features/contracts/contract-list.html
- frontend/src/app/features/contracts/contract-list.spec.ts
- CONTRACT_REFINEMENT_AUDIT.md (superseded notice only)

Deleted: database/proposed/2026-10-14_contract_code_counter.sql, the previous unexecuted proposal. No existing contract data was deleted. Existing uncommitted shell and module files were preserved; no staging/reset/commit occurred. HotelService, RoomTypeService, OfferingService, offering/seasonal DTOs and panels, manifests and database settings are unchanged.

## Verification

- Final database-independent backend suite: 150 passed, zero failures/errors. Includes 55 contract-service cases, 9 contract-controller tests, 40 offering-service cases and 10 offering-controller tests. Existing Hotel/Room Type suites pass.
- Backend compilation and executable JAR package: passed, including the final 150 application tests and Spring Boot repackaging.
- Full backend suite: 165 discovered; 150 passed; zero assertion failures; one application-context error; 14 discovered integration tests skipped (11 contract methods, 3 offering methods). Parameterized integration cases expand only when enabled. MySQL rejects unresolved DB_USERNAME because DB_USERNAME/DB_PASSWORD are unset. Live schema validation and actual repository SQL execution are not verified.
- Angular: 63 passed across 9 files, including 17 contract-screen tests, 11 offering-panel tests and 10 seasonal-panel tests.
- Angular production build passed, approximately 390.53 kB initial bundle.
- Git tracked/untracked whitespace checks performed; CRLF allowed. No commits made.
- No full manual browser/MySQL integration session was performed by Codex. Prior user manual testing does not establish the new lifecycle/concurrency behavior.

Service/controller tests verify server-owned IDs/codes, minimum padding including >999, collision/error paths, immutable legacy codes, header/rate validations, hotel-first call order, stale hotel lookup protection, reassignment lock order, DRAFT/future/expired approval, same guards on reapproval, offering/seasonal integrity, overlap/conflict status preservation, no changes to another contract/hotel, immediate suspension, terminal immutability and failure propagation rather than assumed empty commitments. Angular tests verify visibility, payloads, confirmations, duplicate-submit prevention, generated-code display, successful status updates and preserved state/conflict reasons on failure.

Mocked guards establish service policy, not real SQL date semantics or transaction isolation. ContractMySqlIntegrationTest provides opt-in actual table tests for concurrent creation, rollback of temporary insert on final-code collision, concurrent same-hotel approvals, outer transaction rollback, inclusive contract boundaries/future approval, old APPROVED/SUSPENDED/TERMINATED commitments, actual dates outside old validity, room/hotel scoping, completed/PENDING/CANCELLED stays, checkout boundaries, outstanding/ongoing termination, self reapproval, unchanged snapshots/hotel state, malformed stays and a coordinated pending-to-confirmed fixture writer racing termination. These tests were compiled but SKIPPED, not reported as proven concurrency or snapshot safety.

Commands from backend/:

    .\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" test
    .\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" "-Dtest=*ServiceTest,*ControllerTest,HotelRegistrationValidationTest" package

Logs: ignored backend/target/refinement-full-test.log and refinement-package.log. Maven's cache-lock option permits cached dependencies under the sandbox without changing configuration.

Commands from frontend/:

    npm.cmd test -- --watch=false
    npm.cmd run build

## Required manual/MySQL verification

1. Supply DB_USERNAME/DB_PASSWORD and start with ddl-auto=validate against the existing schema. No migration or dump import is required. Verify new JPQL/native queries initialize and execute successfully.
2. Use a disposable MySQL 8 database with the existing schema for opt-in tests. Set SPRING_DATASOURCE_URL, credentials and SUNTRAVELS_MYSQL_TESTS=true; run the command below. Tests create dedicated fixture rows and clean up only their fixture data. Never run fixture tests against operational data.
3. Create real contracts from Angular; verify generated code matches the actual ID, survives refresh, is never TMP-prefixed and is not editable. Directly submit code/status extras to POST/PUT and verify server ownership. Preserve old C001/other legacy values. Exercise final-code collision on a controlled test fixture, verify failed creation rolls back completely and retry may skip the consumed ID. Do not reseed production identities.
4. Approve DRAFT with valid enabled offerings. Reject no-enabled/invalid terms, INACTIVE hotel and inclusive same-hotel overlaps. Allow different hotels, adjacent non-overlapping periods and future validity. Confirm no other contract/hotel changes and failure leaves status unchanged.
5. Suspend an APPROVED contract with bookings; confirm bookings/prices are unchanged. Reapprove only SUSPENDED and repeat all eligibility/conflict checks. Reject every outgoing TERMINATED transition.
6. Seed relevant confirmed reservations in controlled test data under old suspended/terminated contracts, including ongoing stays and stays outside old contract validity. Verify same hotel/room/date overlap blocks replacement, different rooms/hotels do not, exclusive checkout boundaries work, and completed history/PENDING/CANCELLED do not block.
7. Exercise guarded termination under DRAFT/APPROVED/SUSPENDED with ongoing/future/completed/PENDING data. Compare reservation statuses, references, booked item snapshots and nightly snapshots before/after all allowed lifecycle and rate operations. No cancellation/repricing is acceptable.
8. Use separate transactions for approval/reapproval, concurrent code creation, header hotel reassignment, offering changes, coordinated future confirmation and termination races, rollback, lock timeout and deadlock behavior. Arbitrary uncoordinated writers are not a valid supported test of the application protocol.
9. Browser-check action visibility, confirmation/focus, in-flight guards, conflicts, status refresh and generated-code notices. Regression-check Hotel, Room Type, contract markup, offering structure/rates/status, seasonal overlap/bounds/base fallback/shared inventory, loading/retry and accessibility.
10. Measure reservation-query execution/locking on representative data. Any future index migration needs separate review; none was introduced here.

Opt-in command, from backend/ after setting the disposable test environment:

    .\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" "-Dtest=ContractMySqlIntegrationTest,OfferingMySqlIntegrationTest" test

## Deferred work

No requested contract lifecycle endpoint is deliberately deferred. Real MySQL verification remains outstanding because credentials are unavailable. Future Reservation Management must implement coordinated confirmation, inventory/availability, past-booking rejection, current pricing and immutable snapshot creation, and operational warnings for INACTIVE hotels. Hotel Closure must implement its separate confirmed-occupancy check with the compatible lock order. Search, Customer/Booking/Reservation CRUD, closures, relocation, compensation, Payments and optimistic edit-versioning remain outside this task. No schema migration or counter approval is now pending.
