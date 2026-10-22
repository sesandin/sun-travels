# Contracts: bounded management hotel selector

The Contracts page no longer calls `GET /api/hotels` on startup. It loads only its existing contract page. Hotel choices are requested after the user enters at least two characters, with a 300 ms delay. Each keystroke immediately cancels the previous timer/request via switchMap. A selected ID is stored in the contract form; changing text clears that ID. Selecting or clearing a hotel cancels outstanding suggestions. Editing a DRAFT resolves its selected hotel independently by ID, including an inactive hotel; failures can be retried. Closing/reopening the editor cancels prior lookup work. Loading, no matches, field-length and API error states are shown.

## API and SQL

- `GET /api/hotels/options?term=Oc&status=INACTIVE`: optional enum status, at most 20 lightweight options with hotelId, name, city, country and status. Blank/single-character terms return an empty list without a repository call. Terms over 150 characters return 400. LIKE `%`, `_` and `!` are escaped so input remains a literal prefix. Case matching follows the existing case-insensitive MySQL collation.
- `GET /api/hotels/options/{id}`: a primary-key query returns those same five fields, independently of suggestion text/status/page. Missing IDs return the existing hotel 404 response.
- Single-status SQL uses `WHERE status=:status AND name LIKE :prefix ESCAPE '!' ORDER BY name,hotel_id LIMIT 20`.
- With no status filter, two subqueries select up to 20 ACTIVE and 20 INACTIVE matches in that same order. `UNION ALL ... ORDER BY name,hotelId LIMIT 20` produces the global first 20 across both statuses. This is correct because a row beyond the first 20 of its own status cannot belong to the first 20 overall. All term/status/ID values are bound parameters, not concatenated SQL. These are the two statuses in HotelStatus today.
- Native interface projections select only five columns. No complete Hotel entities or catalogue arrays are loaded for this selector. Integration tests verify JSON contains exactly five properties.

Both statuses remain available because draft creation/editing previously allowed selecting either; approval still enforces the existing ACTIVE-hotel business rule. The accommodation-search endpoints and their ACTIVE-only behavior were not modified. There was no separate exact-hotel filter on the contract directory; its existing text/contract-status filters remain intact. The new hotel-status control filters suggestions and does not change the contract directory or discard an already selected hotel.

## Query plan evidence

[Captured EXPLAIN ANALYZE and indexes](contract-hotel-lookup-plans.txt) came from an isolated schema with 10,000 synthetic hotels, split evenly between the statuses. No development records were copied or changed. V001 was applied only to the disposable schema.

The initial simple all-status prefix query scanned 10,000 hotel rows and sorted the 1,000 matching rows. The chosen two-range query used `ix_hotel_status_name(status,name,hotel_id)` in each branch, returned 20 rows per branch, and sorted the resulting 40 rows. The single-status query used the same index range; ID resolution used PRIMARY. No new schema migration or index was added. The fixture's prefix was `Management 00`; full iterator timings are retained in the raw artifact, but these single plan executions are not a latency benchmark, p50/p95 estimate or production throughput measurement.

The queries run without V001, but may scan/sort more rows without its index. That migration remains unapplied to development from the earlier performance task; deployment must review/apply it for the measured indexed behavior. No development schema was altered here.

## Modified files

| File | Change |
|---|---|
| backend/.../dto/ManagementHotelOption.java | Five-field read-only projection |
| backend/.../repository/HotelRepository.java | Bounded prefix queries and independent ID projection |
| backend/.../service/HotelService.java | Term validation, literal prefix escaping, status dispatch and missing-ID handling |
| backend/.../controller/HotelController.java | New additive management options endpoints |
| frontend/src/app/services/hotel.service.ts | Lightweight option type and HTTP methods |
| frontend/src/app/features/contracts/contract-list.ts | Debounced cancellable suggestions, selection/clear, ID resolution/retry; removes startup catalogue load |
| frontend/src/app/features/contracts/contract-list.html | Searchable options with city/country/status and explicit selection/error/empty states |
| backend/.../controller/ManagementHotelLookupTest.java | Route/JSON, invalid status, length, escaping and no findAll checks |
| backend/.../integration/ContractHotelLookupMySqlIntegrationTest.java | Actual native SQL: 25 tied names, 20 result bound, ID tie ordering, both statuses, status filter, independent ID outside suggestions, empty/literal wildcard matching and projection JSON |
| frontend/.../contracts/contract-list.spec.ts | Debounce, cancellation, selection/clear, errors/retry, selected inactive hotel resolution, existing create/edit/approval compatibility |
| frontend/.../contracts/contract-offering-link.spec.ts | Assert contract deep links do not request the full hotel catalogue |
| frontend/src/app/app.spec.ts | Assert Contracts navigation does not request the catalogue |
| docs/contract-hotel-lookup.md and contract-hotel-lookup-plans.txt | Explanation and actual query plans |

`DirectoryService`, `ContractRepository` and its hotel entity graph were unchanged, as were contract lifecycle, pricing, booking and reservation code. Repository-wide production caller search found ContractList was the only caller of Angular getAllHotels(); no other contract form still calls it. The legacy GET /api/hotels endpoint and client method remain available for compatibility, without a current production Angular caller.

## Tests and limits

Backend: **137 passed, zero failures/errors/skips**, including the isolated MySQL test, management lookup controller/service checks, HotelService, contract controller/service, directory controller/service and quotation search service tests. The initial controller test used a Mockito projection fixture whose mock internals were serialized; replaced it with a real projection fixture and verified the actual MySQL projection's exact JSON shape. No production serializer changes were needed.

Angular: **227 passed across 20 files**, `npm test -- --watch=false`, including the final retry/cancellation test. The first run exposed stale test expectations for the removed startup hotel request; those expectations now assert that the request is absent. `git diff --check` passed.

Only an explicitly named disposable `suntravels_contract_lookup_20261022_test` schema was used for integration fixtures/plans. Tests assert that database name and roll back their own fixtures. The schema was removed after verification. No development records were modified. The new endpoints inherit existing ADMIN-only `/api/hotels/**` authorization.

There are at most 20 returned suggestions, so users must refine broad prefixes. The all-status SQL deliberately follows today's two-value enum; adding another hotel status requires extending that query. Index storage/maintenance costs are unchanged. Synthetic query plans are not evidence for 2-million-hotel concurrent capacity; that requires representative load testing. Legacy unbounded endpoints remain a risk for future callers, but this page no longer uses them.
