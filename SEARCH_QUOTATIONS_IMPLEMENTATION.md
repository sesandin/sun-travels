# Accommodation search, availability and nightly quotations

Implemented 15 October 2026. The /search page and API compute complete accommodation quotations from existing business records. This is not Reservation Management: no rooms are held, booked, confirmed or cancelled.

## Audit and design

Inspected configuration, the Asia/Colombo Clock, CORS, Hotel/Contract/Offering/Seasonal mappings and repositories, approval and reservation-commitment locking queries, DTO/error conventions, test infrastructure, Angular routes/sidebar/forms/services/tokens, and existing implementation reports. Read C:/Users/sesandin/Downloads/suntravels (1).sql as reference only. It confirms the existing hotel_closure, reservation, reservation_item and offering/seasonal columns and foreign keys. No closure or reservation CRUD service exists; the new repository uses non-locking, read-only JDBC queries against those tables. Existing lifecycle queries and locks are unchanged.

Search uses a read-only REPEATABLE_READ transaction with a 15-second transaction timeout. All healthy candidate searches use six bulk SELECT queries, plus up to two optional reference-existence checks. There is no per-offering/per-night database round trip, FOR UPDATE, write or snapshot recalculation. The snapshot is informational; data can change immediately after it is read. Large-data SQL latency/timeout behavior still needs real MySQL measurements.

## API and limits

POST http://localhost:8080/api/search/quotations

~~~json
{
  "checkIn": "2026-10-24",
  "checkOut": "2026-10-27",
  "rooms": [{ "adults": 2 }, { "adults": 1 }],
  "city": "Colombo",
  "hotelId": null,
  "roomTypeId": null
}
~~~

City, hotelId and roomTypeId are optional. City is an exact, trimmed, case-insensitive match. Explicit missing hotel/room-type references return 404. Dates must be real YYYY-MM-DD strings; adults and IDs must be JSON whole numbers, without fractional truncation or string coercion. Nested/null room entries and missing fields are rejected. Validation runs in both controller and service.

GET /api/search/limits returns businessDate from the existing injected Clock, maxRooms=8, maxAdultsPerRoom=10, maxNights=31, maxAdvanceDays=730, maxResults=20 and allocationPolicy=LOWEST_TOTAL_PER_CONTRACT. Angular uses this date/these limits rather than browser-local today. The server remains authoritative if the page stays open across midnight.

These documented initial limits bound call-center party size, occupied-night calculations, response size and allocation work: 1-8 rooms, 1-10 adults per room, 1-31 occupied nights, and check-in from today through today+730 days. Further processing caps are 500 eligible offerings, 10,000 seasonal periods and 20,000 relevant confirmed reservation items. Queries fetch one extra row to detect overflow. Overflow returns a clear 409 requesting narrower filters; it never returns a falsely complete empty or partial search. Limits are fixed initial policy constants/Bean Validation constraints, not runtime configuration; change them together if business policy changes.

Response fields (all returned as DTOs, not entities):

~~~text
QuotationSearchResponse {
 businessDate, checkIn, checkOut: ISO dates; nights: integer;
 quotations: Quotation[]; matchingOptions: integer; truncated: boolean;
 allocationPolicy: "LOWEST_TOTAL_PER_CONTRACT"; notice: string
}
Quotation {
 hotelId, hotelName, city, contractId, contractCode, currencyCode;
 checkIn, checkOut, nights; rooms: RoomAllocation[];
 totalPrice: decimal; eligibility: "INFORMATIONAL"
}
RoomAllocation {
 roomNumber, adults, offeringId, roomTypeId, roomTypeName;
 contractedRooms, availableRoomsForStay, allocatedRoomsForOffering,
 remainingRoomsAfterAllocation; totalPrice: decimal;
 nightlyPrices: NightlyPrice[]
}
NightlyPrice {
 occupiedDate; source: "BASE" | "SEASONAL"; seasonalRateId: integer | null;
 hotelierRate, markupPercentage, sellingPrice: decimals
}
~~~

Room numbers preserve the request order, starting at 1. Availability is before this requested allocation; allocated/remaining counts refer to the whole offering within that option and may repeat across rooms sharing it. matchingOptions counts feasible contract options, not every possible room-type permutation. Results are ordered by currency, then exact total, hotel ID and contract ID; currencies are not converted or meaningfully compared against each other.

200 returns the envelope, including an empty quotations array for a valid search with no complete match. Invalid input returns 400; missing selected references 404; inconsistent commercial/inventory data or processing-limit conflicts 409; database/transaction failures 503. Errors use message and, for structural validation, fieldErrors. SQL/stack details are not returned. Existing /api/** CORS permits the Angular development origin http://localhost:4200.

## Eligibility and safety guards

Only ACTIVE hotels, APPROVED contracts and ENABLED positive-inventory offerings qualify. Entire stays must satisfy validFrom <= checkIn and validTo >= checkOut-1 day. Checkout is exclusive; validTo is inclusive for occupied nights. Future-approved contracts can qualify. Rooms never span different contracts, hotels or currencies.

Recorded closures exclude a hotel where closure.start < checkout and (closure.end is null OR closure.end >= check-in). Closure end is inclusive; null means indefinite. No closure management is added.

Bulk guards reject other overlapping APPROVED contracts for candidate hotels, malformed closure intervals, and relevant confirmed commitments under OTHER contracts for the same hotel/room type. The latter use actual stay dates, checkout > businessToday and overlap with the candidate contract's inclusive validity, regardless of old contract status/validity. This rechecks the conservative replacement invariant rather than treating an old suspended/terminated contract's rooms as extra stock. Invalid confirmed stay/occupancy data also fails closed. One detected inconsistency fails the search rather than silently dropping unsafe data and presenting misleading results; narrower filters can isolate unaffected hotels.

Persisted base rate, markup, currency, capacity and reference terms are validated with existing DTO conventions. All seasonal periods for candidate offerings must be positive DECIMAL(10,2), ordered, non-overlapping including shared boundaries, and wholly inside contract validity, even if a corrupt period lies outside the requested stay. No arbitrary rate wins a conflict.

## Availability, allocation and money

Only CONFIRMED reservation items consume rooms. One item is one room, regardless of its adults. SQL reads relevant items using existing.checkIn < requestedCheckout AND existing.checkOut > requestedCheckIn, also retaining malformed data for rejection. PENDING/CANCELLED are ignored. Unrelated offerings do not consume a candidate's inventory.

StayAvailability clips each item's interval to the requested stay and uses daily +1/-1 events. availableRoomsForStay = contractedRooms - maximum occupied room-item count on any requested night. Adjacent stays therefore do not get incorrectly summed across nights. Exactly full inventory gives zero availability; occupancy above contracted stock fails as inconsistent data. Seasonal periods never add inventory.

RoomAllocationEngine implements exact minimum-cost capacitated bipartite matching using successive shortest augmenting paths and BigDecimal costs. Each requested room has one unit of demand; eligible room-to-offering edges respect adults, and offering capacity is the minimum available across the stay. Reverse edges can reassign an earlier flexible room so a later larger room is not wrongly rejected. At most eight units are allocated over at most 500 offerings: polynomial bounded work, not an exponential enumeration or a lossy greedy/beam approximation.

Return ONE cheapest complete allocation per eligible contract, then at most 20 options in documented order. Alternative equal/more-expensive combinations within the same contract are intentionally not enumerated. No partial allocation or inventory reuse above capacity is returned. Offering IDs provide deterministic tie order.

NightlyQuotationPricing selects a seasonal hotelier rate for inclusive period dates, otherwise base rate. It computes rate * roomAdults * (1 + markup.movePointLeft(2)), with no premature multiplier rounding, then rounds EACH room-night to scale 2 HALF_UP. Room totals sum those rounded nights; quotation total sums rooms. All monetary computation is Java BigDecimal. Source, applied rate/markup and rounded selling price are returned per night. Grand totals are bounded BigDecimal sums, not written to any database column. Angular displays server totals and does not recompute money.

The pure pricing, inventory and matching components can be reused by future confirmation code. Do not reuse this unlocked informational service as a confirmation guarantee: a future writer must take the shared HOTEL -> CONTRACT -> OFFERING/inventory coordination, perform fresh eligibility/occupancy reads, then atomically create reservations and immutable snapshots.

## Angular

Activated Search & Quotations at /search and removed its Soon item. Real Hotel/Room Type APIs populate optional selections; no sample operational results are embedded. The typed Reactive Form maintains individual requested rooms, add/remove controls, consistent limits/dates, city/hotel/type filters, loading/setup retry/search retry, validation and empty/conflict states. In-flight guards prevent duplicate searches and room changes. Edits after success mark displayed results as belonging to the previous submitted request.

Responsive result cards show the complete allocation, contract/currency, distinct contracted/available/allocated/remaining inventory, total selling prices and native keyboard-accessible expandable nightly tables with BASE/SEASONAL labels. Labels, status/error announcements, focus styles and existing design tokens are reused. No booking/confirm button is present. Actual browser viewport/screen-reader checks remain manual.

## Files

Added backend main files under backend/src/main/java/com/suntravels/:

- controller/QuotationSearchController.java
- dto/QuotationSearchRequest.java, QuotationSearchResponse.java, StrictSearchDate.java, StrictSearchInteger.java
- exception/QuotationSearchException.java, QuotationSearchExceptionHandler.java
- repository/QuotationSearchRepository.java
- service/QuotationSearchService.java, StayAvailability.java, NightlyQuotationPricing.java, RoomAllocationEngine.java

Added tests under backend/src/test/java/com/suntravels/:

- controller/QuotationSearchControllerTest.java
- service/QuotationSearchServiceTest.java, StayAvailabilityTest.java, NightlyQuotationPricingTest.java, RoomAllocationEngineTest.java
- integration/QuotationSearchMySqlIntegrationTest.java

Added frontend/src/app/models/quotation.model.ts, services/quotation-search.service.ts and features/search/quotation-search.ts, .html, .scss, .spec.ts. Modified only frontend/src/app/app.routes.ts, app.html, app.ts and app.spec.ts. Added this root report. Existing backend implementation/configuration/manifests and all prior management, lifecycle, directory and seasonal components are unchanged.

## Verification

- Backend application tests: 207 passed, zero failures/errors (158 existing + 49 new). New cases: 23 search service, 7 controller, 10 inventory, 5 pricing, 4 allocator tests. The allocator also agrees with an independent exhaustive oracle on 150 small randomized cases.
- Final executable JAR packaging: passed, including all 207 application tests and Spring Boot repackaging.
- Full backend run: 237 discovered, 207 passed, 0 assertion failures, 1 credentials-related application-context error, 29 discovered integration methods skipped (14 new search methods plus 15 prior methods; parameterized methods expand when enabled). DB_USERNAME/DB_PASSWORD are unset; MySQL rejects the unresolved username placeholder. Live schema validation and search SQL execution are unverified here.
- Angular: 97 passed across 12 files, including 13 search tests and an added sidebar test; previous management/directory tests pass. Production build passed, approximately 439.08 kB initial bundle.
- Git tracked/untracked whitespace checks run with CRLF permitted. No manual browser session or live MySQL performance/concurrency test was performed.

Backend commands:

    .\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" test
    .\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" "-Dtest=*ServiceTest,*ControllerTest,HotelRegistrationValidationTest,RoomAllocationEngineTest,StayAvailabilityTest,NightlyQuotationPricingTest" package

Frontend commands: npm.cmd test -- --watch=false; npm.cmd run build. Logs: ignored backend/target/search-full-test.log and search-package.log. The Maven cache option changes no project configuration.

New integration tests are compiled but SKIPPED, not claimed as verified SQL behavior. They exercise status eligibility, future contracts/end boundaries, bounded/open closures, confirmed versus pending/cancelled consumption, nightly turnover, item counts and checkout boundaries, mixed allocation, filters, replacement/duplicate approval violations, seasonal conflicts and unchanged confirmed snapshots. They require SUNTRAVELS_SEARCH_MYSQL_TESTS=true and refuse fixtures unless the actual database name ends _test. Fixtures run in rollback transactions; identity gaps can remain in that disposable database.

## Manual checks and deferred work

1. Supply credentials and start with the existing schema and ddl-auto=validate. Run real searches at http://localhost:4200/search; verify CORS, reference loading, room controls, filters, error/retry, empty results, responsive layouts, tab order and expandable nightly tables.
2. For SQL tests use an already-provisioned disposable MySQL 8 database with the existing schema, named with suffix _test. Set its SPRING_DATASOURCE_URL and credentials, set SUNTRAVELS_SEARCH_MYSQL_TESTS=true, then run the command below. Do not point fixtures at the working operational database. No schema or dump import is performed by these tests.
3. Exercise Dec 31 -> Jan 1 checkout, closure boundaries, successive confirmed stays occupying different nights, multiple items on one reservation, mixed adults/types, sold-out/full-request failure, old suspended-contract commitments, seasonal inclusive ends and exact half-cent rounding. Compare every displayed nightly sum with room/grand totals and confirm reservation/snapshot rows are unchanged.
4. Regression-check Hotel, Room Type, contract code generation, approval/reapproval/suspension/termination, Room Offerings Directory and existing offering/seasonal maintenance. Measure bulk-query latency and bounds on representative data.

    .\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" "-Dtest=QuotationSearchMySqlIntegrationTest" test

Deferred: atomic Reservation Management/confirmation and snapshot creation, inventory guarantees/holds, cancellation/customer/payment/invoice/relocation workflows, closure CRUD, and alternative allocations within one contract. Search is complete within its documented bounds, not end-to-end reservation functionality. No schema changes, SQL imports, business-data mutations, dependencies, commits or pushes were made.
