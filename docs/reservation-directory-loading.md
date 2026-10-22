# Reservation directory summaries

The directory previously selected ten reservation IDs, then called `detail()` for every ID. Each detail read loaded a header, rooms, one nightly-snapshot query per room and (for applicable future confirmed stays) operational warnings. Opening the list therefore did work needed only by the detail popup.

## Current data flow

`GET /api/reservations` still uses the same SQL filters, exact count, ten-record LIMIT/OFFSET and newest-ID-first ordering. Its `reservations` array now contains **Summary**, not full ReservationResponse objects:

- reservation/customer IDs and customer name;
- check-in, checkout, status and currency;
- hotel/room-type/adult summaries for its rooms;
- historical booked total, or null when unavailable;
- `hasOperationalWarnings` for the existing list indicator.

Contact details, creation timestamp, full warning messages, hotelier rates, markups and individual nightly prices are absent from the list. Angular has a separate ReservationSummary type so the list does not depend on detail fields.

For a nonempty page the repository performs four SELECTs: matching count, paged IDs, one page-scoped room/total aggregation, and one page-scoped header/customer read. Empty pages perform only count and ID queries. Both extra reads bind only the selected IDs; no per-reservation or per-room read is issued by the list. Placeholder SQL is generated from the bounded ID count; IDs and filter values remain parameters. The existing service read-only transaction still contains all reads.

Opening View / Manage now calls the unchanged `GET /api/reservations/{id}`. The popup shows loading/error/retry states and renders complete nightly breakdowns and warning messages only after that response arrives. Opening another reservation or closing the popup cancels the previous request. Manage navigation, cancellation and confirmation keep their existing endpoints and behavior.

## Historical totals

The batched query groups by reservation item and LEFT JOINs stored `reservation_item_nightly_rate` rows. A room total is SUM(selling_price) only when:

1. Checkout is after check-in.
2. Snapshot count equals DATEDIFF(checkout, check-in).
3. Minimum occupied date is at least check-in and maximum occupied date is strictly before checkout.

These checks reproduce detail()'s existing count/date completeness semantics. The existing unique `(reservation_item_id,occupied_date)` index prevents duplicate occupied dates. If any room is incomplete, or the reservation has no rooms, its total is null. Complete room totals are added with BigDecimal; no current contract/offering rates are used. Aggregation reads snapshot values inside MySQL but does not transfer individual snapshots into Java. Legacy aggregate columns are not used to guess totals.

The same room query derives the warning flag from current hotel/contract/offering status. It is true only for CONFIRMED reservations with checkout after the injected business date and an inactive hotel, suspended contract or suspended offering. Detail warning messages and deduplication remain unchanged.

## Measurements and plans

[Raw captured evidence](reservation-summary-evidence.txt) comes from a disposable schema with eleven reservations, two rooms per reservation and three occupied nights per room. Current offering rates deliberately differ from historical nightly selling prices. Every complete summary total matched full detail at 90.09.

| Check | Actual result |
|---|---:|
| Ten-record summary page SELECTs | 4 |
| Separate full-detail expansion for those ten IDs | 50 |
| Empty list SELECTs | 2 |
| Serialized summary page | 3,936 bytes |
| Serialized full-detail array for the same reservations | 20,691 bytes |

SELECT counts were measured through `SHOW SESSION STATUS LIKE 'Com_select'` on the transaction's connection. The former list would add its existing count and ID queries to the measured detail expansion: 52 SELECTs for this fixture. That 52 is derived from the previous code path, not a separately timed old application deployment. Byte measurements are uncompressed UTF-8 Jackson serialization; the summary includes page metadata while the full-detail comparison is an array. These are not HTTP timings or a production capacity benchmark.

EXPLAIN ANALYZE of the actual aggregate SQL with page parameters showed 20 grouped room rows, 60 nightly rows visited through `uq_reservation_item_occupied_date`, primary-key joins and a temporary aggregate/sort of 20 result rows. On this tiny fixture the optimizer started with its single offering and visited 22 item rows before filtering to the selected 20. That small scan is not evidence of a large-catalogue problem. Existing foreign-key and nightly indexes were reused; no speculative index/migration was added. Large-data plans, concurrent throughput and p50/p95 were not measured.

## Changed files

| File | Change |
|---|---|
| backend/src/main/java/com/suntravels/dto/ReservationResponse.java | Adds Summary/RoomSummary and changes Page's element type; full response untouched |
| backend/src/main/java/com/suntravels/repository/ReservationRepository.java | Replaces per-ID detail expansion with two batched summary reads; existing filters/pagination untouched |
| frontend/src/app/models/reservation.model.ts | Separates summary and detail types |
| frontend/src/app/features/reservations/reservation-directory.ts | Fetches details on demand with cancellation, loading/error/retry state |
| frontend/src/app/features/reservations/reservation-directory.html | Uses warning flag and room summaries; popup loading/error/retry handling |
| backend/src/test/java/com/suntravels/controller/ReservationControllerTest.java | Verifies summary API fields and absence of snapshots/contact details; existing detail security assertions retained |
| backend/src/test/java/com/suntravels/integration/ReservationSummaryMySqlIntegrationTest.java | Historical completeness, totals, warning flags, pagination, constant query count, response size and actual SQL plan |
| backend/src/test/java/com/suntravels/integration/ReservationMySqlIntegrationTest.java | Updates list assertions for summary warning flag/room representation |
| backend/src/test/java/com/suntravels/integration/ReservationStayPeriodMySqlIntegrationTest.java | Uses summary IDs in existing filter/page assertions |
| frontend/src/app/features/reservations/reservations.spec.ts | Summary-only rendering, lazy detail fetch, error/retry, stale-request cancellation and warning behavior |
| docs/reservation-directory-loading.md and reservation-summary-evidence.txt | Explanation and measured evidence |

Controller/service routing required no production change. Booking locks, inventory, pricing, snapshot writes, full-detail reads and cancellation implementations were not changed.

## Verification and limits

Angular: **228 passed in 20 files**, including the new on-demand detail regression.

The broader backend run executed **74 tests: 73 passed, one failed, zero errors/skips**, including the new isolated summary test, all reservation MySQL tests, stay-period filtering, reservation service/controller, availability and nightly pricing. The failure is the previously documented `failureAfterItemInsertionRollsBackHeaderItemsAndSnapshots`: its IllegalStateException assertion receives Spring's InvalidDataAccessApiUsageException wrapper. This unrelated baseline assertion remains unchanged; that run exits with failure. After adding the explicit summary API shape/security assertion, the focused final rerun of ReservationControllerTest and ReservationSummaryMySqlIntegrationTest passed **all 12 tests**, zero errors/failures/skips. `git diff --check` passed.

Only `suntravels_reservation_summary_20261022_test` was used for database fixtures. It was cloned with schema only; the clone already contained V001 columns/indexes, so the migration preflight correctly rejected reapplication. No development schema or records were changed. Fixtures ran in rollback transactions and the disposable schema was removed after verification.

Remaining costs: exact counts, contains-text searches and deep OFFSET pages remain unchanged. Aggregation scales with rooms and stored nights for the selected ten reservations; legacy bookings can exceed current booking limits. The optimizer can choose different join orders with different statistics, so large-data plans still need checking. Full detail retains its per-room nightly reads, now confined to a single opened reservation. External consumers of the list endpoint must adopt the intentional summary DTO; full details remain available by ID.
