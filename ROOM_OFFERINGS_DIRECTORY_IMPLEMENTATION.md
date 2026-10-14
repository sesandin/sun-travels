# Room Offerings Directory

Implemented on 14 October 2026 in the existing Sun Travels application.

## Feature

The active Room Offerings sidebar link opens /room-offerings. It replaces the previous Soon entry and retains the existing sidebar styling, active-page state and mobile-menu close behavior. Existing Hotel, Room Type, Contract, approval/reapproval, offering and seasonal-rate workflows remain available.

The directory uses real backend data from all contracts, including DRAFT, APPROVED, SUSPENDED, TERMINATED and hotels marked INACTIVE. It shows hotel name/status, contract code/status and inclusive validity, room type, base hotelier rate/currency per adult per night, contracted room count, maximum adults per room, offering status, seasonal-period count and View / Manage.

The inventory explanation explicitly distinguishes contracted room inventory from current availability. No available-room count, reservation search, selling-price calculation or booking workflow is implemented. Currency is displayed without conversion; rate sorting groups by currency before comparing amounts.

Search covers hotel name, contract code and room type, case-insensitively. Hotel, contract-status, room-type and offering-status filters combine. Sorting supports hotel, natural contract code order, room type, base rate grouped by currency, contracted rooms and validity start, in ascending/descending order. Reset returns all filters/sorting to defaults. Hotel/room options come from actual directory records and use their IDs, not hardcoded operational values.

Loading, empty, no-matches, error, retry and refresh states are implemented. Results use local pagination with 20 rows per page, count/range announcements and bounded page controls. Desktop uses a scrollable table; at narrow widths it becomes labeled offering cards while retaining table headers for assistive technology. Form labels, table caption, descriptive links, status/error announcements and existing focus/design tokens are reused. No additional editor exists in the directory.

## Added API

GET /api/offerings returns 200 with an array of OfferingDirectoryResponse DTOs; [] means no offerings. Existing GET /api/contracts/{id}/offerings, GET /api/offerings/{id} and all offering/seasonal write APIs remain unchanged.

Directory DTO fields:

- offeringId
- hotelId, hotelName, hotelStatus
- contractId, contractCode, contractStatus
- validFrom, validTo
- roomTypeId, roomTypeName
- ratePerPersonPerNight, currencyCode
- contractedRoomCount, maxAdults
- status (offering ENABLED/SUSPENDED)
- seasonalRateCount

Enums serialize as strings, dates as ISO dates, rates as JSON numbers from BigDecimal. No JPA entity is serialized. Contract/room/hotel associations are not exposed as entity graphs in the response.

The service is read-only and uses at most TWO queries independent of offering count:

1. A fetch-join of offering -> contract -> hotel and offering -> room type loads every required to-one association. No per-row lazy lookup is needed.
2. A grouped seasonal-rate count produces one aggregate per offering; absent groups default to zero. The count query is skipped when the offering list is empty.

No per-row seasonal API call is made by the directory. Full seasonal periods are loaded only through the existing management panel after navigation. No schema/index/table changes, reservation access or pricing/lifecycle mutations are introduced.

This is an initial full-result listing, consistent with existing array-based administrative endpoints. Pagination/filtering/sorting are CLIENT-SIDE: they bound DOM rendering but not API payload, database result-set size or browser memory. The fetch/count queries and seasonal index avoid N+1 reads, but do not make the endpoint infinitely scalable. Server-side paging/filtering/search, result-size controls and query benchmarks are deferred until dataset size justifies the additional API design.

## Reused management and deep links

View / Manage links to /contracts?contractId=<actual ID>&offeringId=<actual ID>. IDs are read-only navigation context, not permissions. Contracts fetches the current parent DTO rather than trusting the directory status. The existing OfferingPanel then fetches that contract's offerings and the existing SeasonalRates components load periods.

The relevant offering gets an outline, focus and scroll into view after rendering. DRAFT editing, APPROVED controlled-rate/status actions, seasonal permissions and SUSPENDED/TERMINATED read-only behavior remain implemented by the existing components and backend. The directory never changes those business rules or grants extra permissions.

Query IDs must be positive decimal signed-INT values. Invalid links produce a readable error without arbitrary requests. A valid offering ID belonging to another contract produces a panel warning and does not open an editor. Missing-contract API errors are displayed. Older detail requests are cancelled when query context changes.

Angular can reuse a component across query changes. OfferingPanel now cancels stale reads/save subscriptions and reloads when the parent contract ID changes, clearing the prior editor/context. It also reacts to a changed focus target and closes an editor when parent lifecycle status changes. This prevents a reused detail view from showing the previous contract's offerings/controls. Cancelling an HTTP subscription does not guarantee a server write already received was rolled back; the backend remains authoritative and the new context reloads its own records.

## Inspection

Inspected routing, sidebar/shell styles and tests, shared CSS tokens, ContractList/detail/API conventions, OfferingPanel, SeasonalRates, offering models/service, Spring controller/DTO/service/repositories and existing service/controller/MySQL test patterns. There was no suitable global listing endpoint; existing listing was contract-scoped only. No separate SeasonalRatePanel was rebuilt: the existing component is named SeasonalRates.

## Added files

- backend/src/main/java/com/suntravels/dto/OfferingDirectoryResponse.java
- frontend/src/app/features/room-offerings/offering-directory.ts
- frontend/src/app/features/room-offerings/offering-directory.html
- frontend/src/app/features/room-offerings/offering-directory.scss
- frontend/src/app/features/room-offerings/offering-directory.spec.ts
- frontend/src/app/features/contracts/contract-offering-link.spec.ts
- ROOM_OFFERINGS_DIRECTORY_IMPLEMENTATION.md

## Modified files

- backend/src/main/java/com/suntravels/controller/OfferingController.java ? global GET mapping.
- backend/src/main/java/com/suntravels/service/OfferingService.java ? directory DTO assembly only; existing management logic unchanged.
- backend/src/main/java/com/suntravels/repository/OfferingRepository.java ? to-one fetch query.
- backend/src/main/java/com/suntravels/repository/SeasonalRateRepository.java ? aggregate period-count projection/query.
- backend/src/test/java/com/suntravels/service/OfferingServiceTest.java ? global listing, history, inactive hotels, counts and empty results.
- backend/src/test/java/com/suntravels/controller/OfferingControllerTest.java ? endpoint DTO/empty response tests.
- backend/src/test/java/com/suntravels/integration/OfferingMySqlIntegrationTest.java ? opt-in actual fetch/count query test.
- frontend/src/app/models/offering.model.ts ? typed directory entry.
- frontend/src/app/services/offering.service.ts ? directory API method.
- frontend/src/app/app.routes.ts ? /room-offerings route.
- frontend/src/app/app.html ? active Room Offerings link.
- frontend/src/app/app.ts ? remove Room Offerings from upcoming items.
- frontend/src/app/app.spec.ts ? sidebar navigation/active state/mobile close tests.
- frontend/src/app/features/contracts/contract-list.ts ? safe query handling and detail loading.
- frontend/src/app/features/contracts/contract-list.html ? link errors and focus target binding.
- frontend/src/app/features/contracts/offering-panel.ts ? focus and reused-context handling only.
- frontend/src/app/features/contracts/offering-panel.html ? offering anchors/highlight/missing-target warning.
- frontend/src/app/features/contracts/offering-panel.scss ? relevant-offering highlight.

Existing SeasonalRates implementation, ContractService/backend lifecycle/reservation/pricing logic, Hotel/Room Type modules, dependency manifests and database configuration are unchanged. Existing uncommitted work was preserved without staging, resetting, committing or pushing.

## Verification

- Database-independent backend suite: 158 passed, zero failures/errors, including 46 offering-service cases and 12 offering-controller tests. Existing contract approval/reapproval/code-generation/termination, Hotel and Room Type suites pass.
- Backend executable JAR package passed, including compilation, these 158 tests and Spring Boot repackaging.
- Full backend suite: 174 discovered; 158 passed; zero assertion failures; 1 application-context error; 15 discovered MySQL integration tests skipped. DB_USERNAME/DB_PASSWORD remain unset and MySQL rejects the unresolved username placeholder. Live schema validation and the new JPQL fetch/count query execution remain unverified.
- Angular: 83 passed across 11 files. Includes 10 directory tests, 9 deep-link/lifecycle-context tests, 2 shell tests and all existing contract/offering/seasonal/Hotel/Room Type tests.
- Angular production build passed: approximately 416.39 kB initial bundle, estimated 101.38 kB transfer.
- Git whitespace checks cover tracked changes and all added/untracked source/report files with CRLF permitted. Existing line-ending conversion notices are not whitespace failures.
- A sidebar duplicate caught by the initial Angular run was fixed; all final tests pass.
- No manual real-browser viewport session or live MySQL query/concurrency session was performed. Responsive and keyboard/screen-reader behavior still needs the checks below.

Commands from backend/:

    .\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" test
    .\mvnw.cmd -o "-Daether.syncContext.named.factory=noop" "-Dtest=*ServiceTest,*ControllerTest,HotelRegistrationValidationTest" package

Logs are in ignored backend/target/directory-full-test.log and directory-package.log. The cache-lock option does not change Maven configuration.

Commands from frontend/:

    npm.cmd test -- --watch=false
    npm.cmd run build

## Manual verification

1. Configure database credentials and start the backend with the existing ddl-auto=validate/schema. Do not import a dump or run a migration. Call GET /api/offerings; verify DTO data and seasonal counts against actual records, including inactive hotels and suspended/terminated contracts.
2. On a disposable MySQL 8 database that already has the existing schema, enable SUNTRAVELS_MYSQL_TESTS=true and run OfferingMySqlIntegrationTest using a test datasource URL/credentials. Its new directory test validates real fetch/count query execution on dedicated historical/inactive fixtures and is currently skipped. Never run fixture tests against operational data.
3. Open /room-offerings through the sidebar. Verify the active-page styling, no Room Offerings Soon duplicate, mobile menu close and browser refresh/direct route loading.
4. Test each search field, combined filters, natural code sorting, currency-grouped base-rate sorting, numeric inventory sorting, reset and paging. Confirm contracted rooms are never labeled as currently available rooms.
5. Test loading, no records, no matches, backend failure and retry. Only real APIs supply production records; test fixtures are confined to tests.
6. Click View / Manage for DRAFT, APPROVED, SUSPENDED and TERMINATED contracts. Verify fetched details, offering focus/highlight, correct existing permissions and seasonal forms. Check malformed IDs, nonexistent contract and an offering ID from another contract. Switch links while the same detail component is reused and confirm no stale offering/editor remains.
7. Inspect at desktop, tablet and narrow/mobile widths. Check long hotel/room names, large rates, dates, horizontal table scrolling/cards, tab order, visible focus, screen-reader labels/caption/announcements and reduced-motion behavior.
8. Regression-check Hotel, Room Type, contract creation/generated code, approval/reapproval/suspension/guarded termination, offering editing/rate/status and seasonal overlap/date constraints/base fallback/shared inventory. No directory action should directly update an offering.
9. Measure API query count and latency with representative data. Confirm the listing uses one fetch plus one aggregate query rather than per-row association/season queries. Plan server pagination before full-result payloads become impractical.

## Deferred and unchanged scope

Server-side pagination/filtering/search and production-size performance benchmarks are deferred. Live availability, date-specific inventory, reservation booking, selling-price calculation and reservation workflows remain outside this administrative directory. No database schema changes, existing-record changes, SQL imports, unnecessary dependencies, commits or pushes were made.
