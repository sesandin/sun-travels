> Superseded: the counter-table proposal was discarded without execution. The approved design uses C + database-generated ID. See CONTRACT_REFINEMENT_IMPLEMENTATION.md for the current implementation.

# Contract refinement audit and mandatory migration checkpoint

14 October 2026. This is an audit and review proposal, not a completed implementation report. Work is paused at the database-migration approval checkpoint required by the user. No application source files or tests were edited in this stage. The completed Hotel, Room Type, Contract foundation, Offering and Seasonal Rate implementation is preserved.

## Files added in this stage

- database/proposed/2026-10-14_contract_code_counter.sql ? proposed MySQL 8 counter migration, saved for manual review only.
- CONTRACT_REFINEMENT_AUDIT.md ? this audit, plan and checkpoint report.

No migration or SQL dump was executed. No live tables were modified, created, dropped or recreated. No contracts were deleted or rewritten. No commit/push, dependency installation, booking workflow or closure implementation was performed. Hibernate ddl-auto=validate is unchanged.

## Actual repository findings

Backend follows controller/service/repository with record DTOs, Jakarta validation, BigDecimal money, LocalDate validity and scoped exception advice. ContractService owns header editing, markup, suspension, termination and an unavailable approval method. ContractRepository provides a pessimistic contract-row lock. Contract creation still requires a manually supplied code; DRAFT updates currently permit changing it. Both behaviors must change for the finalized immutable/generated-code policy.

OfferingService owns BOTH offering and seasonal operations; there is no separate SeasonalRateService. Existing writes acquire CONTRACT before OFFERING; seasonal updates/current overlap queries use pessimistic locks. Offering creation, DRAFT structural editing, approved rate/status maintenance, shared inventory, seasonal non-overlap and base-rate fallback already exist and must remain intact.

Hotel has ACTIVE/INACTIVE status independent of contract status. HotelRepository currently has no explicit lock method. HotelService updates the hotel only; it does not cancel reservations or rewrite prices. Reservation, ReservationItem, customer, nightly snapshot and hotel closure application mappings/services are absent. Do not mistake test-fixture JDBC inserts for production reservation infrastructure.

The accessible newer export at C:/Users/sesandin/Downloads/suntravels (1).sql contains the required reservation/reservation_item links and dates. It also confirms contract_code VARCHAR(45) NOT NULL with a unique index, contract INT IDENTITY, offering uniqueness, seasonal constraints and the nightly snapshot table. It contains C001/C002/C003 contract codes. It has no yearly code counter. The export was read only; its contents are schema evidence, not a live database inspection.

Angular ContractList has typed Reactive Forms, signal state, detail fetches, explicit lifecycle confirmation, in-flight guards and API-error rendering. It embeds real OfferingPanel and SeasonalRates components. Approval/reapproval controls are disabled; termination is exposed for non-TERMINATED contracts. ContractService's Angular API client has suspension/termination but no approval/reapproval methods. Existing tests use Mockito plus real Bean Validation, standalone MockMvc controllers, Angular TestBed/HttpTestingController and opt-in MySQL integration fixtures.

## Important current policy gap

The existing terminateContract implementation writes TERMINATED without querying reservations. Its tests assert DRAFT/APPROVED/SUSPENDED termination without a commitment guard. This is NOT sufficient for the finalized policy. The current application has not been made safe by this audit: guarded termination must replace this path, or it must become unavailable if coordinated reservation checks cannot be established. Existing approval/reapproval remain unavailable; no new lifecycle endpoint was enabled at this checkpoint.

DRAFT termination is explicitly permitted by existing tested behavior and will be preserved, with the same outstanding-reservation guard applied defensively. APPROVED/SUSPENDED termination must reject outstanding CONFIRMED commitments. No transition may cancel bookings or alter historical pricing.

## Brief implementation plan after checkpoint approval

1. Introduce an injectable business Clock using Asia/Colombo and fixed-clock tests. Preserve past/present/future validity dates, inclusive valid_to and exclusive checkout. No automatic expiry transition.
2. Add read-only reservation commitment access against existing tables and validate actual stay data. Add hotel-row locking and current-read overlap queries. Establish HOTEL -> CONTRACT -> OFFERING -> SEASONAL/RESERVATION lock ordering before enabling lifecycle writes, with stable ID ordering when locking multiple rows.
3. Add atomic DRAFT approval and SUSPENDED reapproval: validate persisted header, hotel eligibility, offerings and seasonal integrity; recheck other APPROVED overlaps and relevant reservation commitments under coordination; change only the candidate status after all checks pass.
4. Replace unguarded termination with the outstanding-confirmed-reservation policy. Preserve immediate suspension, DRAFT termination where allowed, terminal immutability and narrow commercial updates.
5. Implement the approved counter allocator, creation DTO without user-entered code, immutable codes on all edits and non-NULL contract insertion. Remove manual code entry only alongside safe backend behavior. Existing code values remain untouched.
6. Add lifecycle UI confirmations/status refresh/conflict handling, meaningful unit and HTTP tests, and opt-in MySQL separate-transaction tests. Preserve the current offering/seasonal panels and existing visual/accessibility conventions.

## Reservation query design and missing coordination

The existing schema is sufficient for read-only guards without a new reservation table or full reservation CRUD module:

reservation_item -> contract_room_offering -> contract and reservation

Termination examines any reservation item tied to the target contract, with reservation.status = CONFIRMED and check_out_date > current business date. This includes ongoing stays; checkout exactly today is completed. PENDING and historical completed CONFIRMED stays do not block. Count/join duplicates must not affect correctness; return a concrete conflicting reservation ID when possible.

Replacement approval examines other contracts for the SAME hotel and room types present in the candidate offerings, regardless of old contract status or validity dates. Use CONFIRMED, check_out_date > today, check_in_date <= candidate.valid_to and check_out_date > candidate.valid_from. This excludes completed history while protecting actual ongoing/future occupied nights. Candidate self is excluded for reapproval; its own commitments remain its inventory obligations. Checkout at candidate.valid_from does not overlap; check-in at candidate.valid_to does. All candidate offering room types should be considered so later enabling an already-present suspended offering cannot bypass replacement protection.

Malformed relevant confirmed stays (checkout <= check-in), invalid occupancy quantities or broken required references must fail closed rather than disappearing through the overlap filter. A zero result must come from an actual successful database query; there will be no placeholder checker and no exception-to-zero fallback.

There is no production confirmation writer to inspect or coordinate today. Before exposing these transitions, implement and test fresh reservation reads under the shared coordination, and specify the mandatory HOTEL-first convention for every future confirmation/cancellation/date or offering change affecting commitments. Hotel closure operations must use the compatible convention later. Arbitrary direct database writers bypassing application locks are not made safe by a hotel lock alone. Without evidence of compatible writers, do not claim confirmation-versus-approval concurrency proven. Missing production reservation infrastructure is a coordination/testing dependency, not missing reservation columns.

## Lock-order concerns

Approval/reapproval must first obtain a scalar hotel ID without retaining stale managed contract state, lock HOTEL, lock CONTRACT, verify the contract still references that hotel, then lock/re-read offerings and query conflicts. A DRAFT hotel reassignment that raced the scalar lookup must be rejected/retried rather than continuing under the wrong hotel lock.

Existing offering/seasonal operations start at CONTRACT and never later acquire HOTEL, so they do not currently introduce a CONTRACT -> HOTEL reversal. Any refinement that makes them hotel-aware must change entry order consistently, including creation/header mutation and relevant future writer paths. Re-read APPROVED conflicts with a current/locking query after the hotel lock; do not reuse a prior MySQL REPEATABLE READ snapshot. Lock multiple records in deterministic ID order and test lock-timeout/deadlock translation to safe 409 errors.

The counter allocation transaction is independent and finishes BEFORE hotel/contract write locks are held. It must not acquire application row locks while holding the counter lock. This avoids introducing a counter/hotel lock-order cycle.

## Proposed automatic-code migration

Review database/proposed/2026-10-14_contract_code_counter.sql. The smallest proposed schema addition is ONE InnoDB table:

- business_year SMALLINT UNSIGNED primary key (four-digit business year).
- last_issued DECIMAL(37,0) NOT NULL, non-negative.

A 37-digit suffix plus ST-YYYY- fits the existing VARCHAR(45) limit. This supports numbers beyond four digits without imposing a long overflow limit smaller than the current code column. Runtime uses exact integer/BigInteger semantics, not floating point, with explicit exhaustion handling.

Why required: contract_id is assigned only after insertion, contract_code cannot be NULL, and its business number resets by creation year. A persistent locked counter supports this format without relying on an unsafe runtime MAX + 1 or changing codes to UUIDs/timestamps. Existing contract rows are not a durable issuance ledger because the old implementation allows renaming; a separate high-water mark prevents reuse going forward.

The script has a read-only preflight, creates only the new counter table and seeds each existing ST year from its highest numeric suffix under a stopped-writer deployment. MAX is only migration reconciliation, never runtime allocation. C001 and every other existing contract code remain unchanged. Case variants, legacy zero-padding and surrounding spaces are conservatively included in seeding. No new application JPA mapping will be introduced before manual migration: otherwise ddl-auto=validate could break startup on the existing schema.

All legacy/manual contract writers must be stopped during seeding and stay stopped until the new immutable-code backend is deployed. MySQL DDL implicitly commits; the script is not wholly rollbackable. If bootstrap fails, reconcile the newly created counter before reopening writers. Do not blindly rerun/reset a deployed counter.

Live rows cannot recover codes previously deleted or renamed. Before applying the migration, reconcile historical ST issuance records if any exist, and seed above their high-water marks as well. The inspected export cannot prove that no such historical issuance occurred.

Planned runtime allocation: ensure the year row safely, lock it FOR UPDATE, increment and commit in an independent transaction, then insert the new contract using the already-generated non-NULL code. Later creation failures burn the reserved number. Format with a minimum of four digits and retain longer suffixes. Creation year comes from business date, not contract validity. The existing unique index remains a final protection, and collisions must never overwrite or rename another contract.

If the table is missing, the application must remain able to start/read/edit existing contracts with ddl-auto=validate; new creation must fail closed with an understandable migration-required error and no manual/UUID fallback. A JDBC allocator/capability check can support this compatibility without mapping an absent counter table as an entity. Implementation is pending approval, not claimed complete.

## Endpoints and behavior at this checkpoint

No new endpoint is implemented by this stage. Existing APIs remain as inspected:

- GET /api/contracts and /api/contracts/{id}
- POST /api/contracts (existing manual-code implementation)
- PUT /api/contracts/{id} (existing DRAFT header implementation)
- PATCH /api/contracts/{id}/markup
- PATCH /api/contracts/{id}/suspend
- PATCH /api/contracts/{id}/terminate (existing unguarded implementation; policy gap above)
- Existing offering/seasonal APIs unchanged.

Deferred new endpoints: PATCH /api/contracts/{id}/approve and /reapprove. Guarded /terminate, generated-code POST, immutable-code PUT and their UI refinements are pending implementation. The checkpoint does not claim finalized policies enforced merely because baseline tests pass.

## Hotel and date policy decisions for the implementation

Hotel ACTIVE is the proposed operational eligibility for approval; an INACTIVE hotel will produce a conflict and will never be activated automatically. Contract status and hotel status stay independent. Hotel deactivation remains immediate and must not rewrite bookings/prices; future reservation views will show the operational warning. If approval of INACTIVE hotels is intended, this eligibility choice needs explicit correction before implementation.

An expired APPROVED contract remains APPROVED in stored lifecycle state but has no eligible occupied nights after inclusive valid_to. It is not TERMINATED and is never auto-terminated. A future-dated contract may be approved before valid_from. Past dates remain valid for contract administration; preventing newly booked past stays belongs to Reservation Management.

Planned closure/reservation overlap protection, emergency relocation, compensation, reservation warnings, booking inventory/availability, pricing and nightly confirmation snapshots belong to later modules. Approval will not pretend to implement those sales workflows. No policy permits automatic cancellation because of suspension, termination, hotel deactivation or closure.

## Baseline verification (existing code, not the pending refinement)

- Backend full suite: 125 discovered; 121 passed; 0 assertion failures; 1 application-context error; 3 MySQL integration tests skipped. Unset DB_USERNAME/DB_PASSWORD cause MySQL to reject the unresolved username placeholder. Live schema validation is unverified.
- Database-independent backend package: passed, including 121 tests and executable JAR repackaging.
- Angular unit tests: 57 passed across 9 files.
- Angular production build: passed; approximately 389.46 kB initial bundle.
- No new approval, termination-guard, code allocator or migration tests are claimed as passing. Existing MySQL tests concern offering/seasonal behavior and were skipped.
- Git whitespace checks run for existing tracked changes and both newly added proposal/report files. SQL was reviewed as text only, not syntax/execution-tested against MySQL.

Commands from backend/:

    .\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" test
    .\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" "-Dtest=*ServiceTest,*ControllerTest,HotelRegistrationValidationTest" package

Logs: ignored backend/target/contract-refinement-audit-test.log and contract-refinement-audit-package.log. The cache-lock option does not change Maven configuration.

Commands from frontend/:

    npm.cmd test -- --watch=false
    npm.cmd run build

## Manual review and testing after implementation

1. Review/approve the counter design and rollout; reconcile any previously renamed/deleted ST codes. Manually apply the approved script on an appropriate database while legacy writers are stopped. Codex will not apply it automatically.
2. Supply MySQL credentials and validate the existing schema plus approved counter. Use a disposable test database for integration fixtures; never import the dump as part of this task.
3. Test concurrent yearly allocation, year rollover, numbers above 9999, existing-code preservation, failed-create number burning, code immutability, missing-counter behavior and unique-index conflicts.
4. Verify initial approval/reapproval with valid enabled offerings, invalid/missing offerings/hotel, inclusive same-hotel overlap, different hotels, adjacent dates and future validity. No failed approval may modify candidate or other contracts/hotel.
5. Seed outstanding/ongoing/completed/PENDING reservations through normal test setup. Check replacement by actual nights and room types under old APPROVED/SUSPENDED/TERMINATED contracts; checkout boundaries and malformed records must be exercised.
6. Verify guarded termination, immediate suspension, unchanged reservation statuses/references/prices and DRAFT transition preservation. Never use a production approval shortcut for fixture setup.
7. Use separate MySQL transactions for same-hotel concurrent approvals, reapproval races, reservation confirmation versus approval/termination, offering edits, contract hotel reassignment, lock timeout/deadlock behavior and rollback. Confirming writers must follow the same hotel coordination; mocks cannot establish it.
8. Browser-check action visibility, confirmation, in-flight guards, conflict text, unchanged UI state on failure, refreshed status and generated code display. Regression-check Hotel, Room Type, offerings, seasonal dates/base fallback/shared inventory and accessibility.

## Required confirmation

Approve or revise the proposed counter-table design before dependent code changes. Applying SQL remains a separate manual deployment step. This stop follows the user's explicit section 8 migration checkpoint and section 12 instruction to stop before mandatory migration or unresolved reservation-safety decisions; it is not a sandbox permission failure.
