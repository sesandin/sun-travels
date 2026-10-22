# Database-aligned form validation

Audited 2026-10-22 against MySQL `information_schema.columns` in the configured `suntravels` database, read-only. [Exact column metadata](mysql-columns.tsv) records types, nullability, precision and collation. No complete schema creation script or migration runner exists in the repository. `spring.jpa.hibernate.ddl-auto=validate` makes the existing SQL schema authoritative; entity annotations describe it rather than changing it. Reviewed both V001 location-search migration files and the optional hotel-closure removal script. Neither changes editable field sizes. V001 remains unapplied to development.

N = NOT NULL; Y = nullable. Sizes below are actual SQL character limits, not guessed defaults. All current editable stored strings are VARCHAR or CHAR; there are no TEXT-backed form fields. `CHAR(3)` currency is exactly three uppercase letters by business rule. Signed INT spans -2,147,483,648 through 2,147,483,647; identifiers/counts use only the positive range. DECIMAL limits below show the nonnegative business range, although SQL itself can represent negative values.

## Validation matrix

Backend refers to request DTO Bean Validation plus existing service checks. Frontend describes the final state, with changes noted explicitly. Create/update share the same limits.

| Page | Field | Database column | Database limit / null | Backend validation | Frontend validation | Required change / result |
|---|---|---|---|---|---|---|
| Hotel create/edit | Name | hotel.name | VARCHAR(150), N | NotBlank, Size(150) | notBlank, maxLength(150), maxlength 150 | Added native maxlength; retained model limit; explicit API message |
| Hotel create/edit | Address | hotel.address | VARCHAR(255), Y | NotBlank, Size(255) | notBlank, maxLength(255), maxlength 255 | Added native maxlength; business-required despite nullable SQL |
| Hotel create/edit | City | hotel.city | VARCHAR(100), Y | NotBlank, Size(100) | notBlank, maxLength(100), maxlength 100 | Added native maxlength; business-required despite nullable SQL |
| Hotel create/edit | Country | hotel.country | VARCHAR(100), Y | NotBlank, Size(100), recognized CountryValue | Required ISO selection, maxLength(100); canonical name sent | Retained; arbitrary 100-character values fail country business validation |
| Hotel create/edit | Phone | hotel.phone | VARCHAR(20), Y | Size(20) | maxLength(20), maxlength 20 | Added native maxlength |
| Hotel create/edit | Email | hotel.email | VARCHAR(100), Y | Email, Size(100) | email, maxLength(100), maxlength 100 | Added native maxlength |
| Hotel create/edit | Star rating | hotel.star_rating | signed TINYINT (-128..127), Y | NotNull, Min(1), Max(5), Byte | Required selection 1..5, min/max | Retained stricter business range |
| Hotel create/status action | Status | hotel.status | VARCHAR(20), N | NotNull HotelStatus enum | Required enum selection | Retained; no arbitrary input string |
| Room type create/edit | Name | room_type.name | VARCHAR(100), N | NotBlank, Size(100) | notBlank, maxLength/maxlength 100, field message | Already compliant |
| Room type create/edit | Description | room_type.description | VARCHAR(255), Y | Size(255) | textarea maxLength/maxlength 255, field message | Already compliant; this is not TEXT |
| Contract create/edit | Hotel | contract.hotel_id | signed INT, N, FK | NotNull, Positive Integer; hotel existence | Required positive integer <= INT_MAX selection | Added shared positiveInteger validator for programmatic values |
| Contract create/edit | From/to | contract.valid_from / valid_to | DATE, N | NotNull; ordered dates and lifecycle checks | Required dates, date-range validation | Retained; no string-length restriction |
| Contract create/edit/markup | Markup | contract.markup_percentage | DECIMAL(5,2), N | NotNull, DecimalMin(0), Digits(3,2) | decimal validator, min 0, max 999.99, step .01 | Already compliant: 0..999.99, at most two decimal places |
| Contract create/edit | Currency | contract.currency_code | CHAR(3), N | NotNull, [A-Z]{3} | Required, exact pattern, maxlength 3, maxLength(3) | Added explicit model maxLength; pattern already prevented bypass |
| Contract lifecycle | Status | contract.status | VARCHAR(20), N | Server-owned lifecycle enum/actions | Action buttons | No free-text status |
| Contract | Code | contract.contract_code | VARCHAR(45), N | Server-generated C + ID (temporary UUID code before flush) | Read-only | No user-entry validation needed; generated values fit |
| Offering create/edit | Room type | contract_room_offering.room_type_id | signed INT, N, FK | NotNull Positive Integer, existence | positiveInteger selection | Already compliant |
| Offering create/edit | Room quantity | contract_room_offering.contracted_room_count | signed INT, N | NotNull Positive Integer | required positiveInteger <= INT_MAX | Already compliant; numeric range, not maxlength |
| Offering create/edit | Adult capacity | contract_room_offering.max_adults | signed INT, N | NotNull Positive Integer | required positiveInteger <= INT_MAX | Already compliant |
| Offering create/edit/base rate | Hotelier rate | contract_room_offering.rate_per_person_per_night | DECIMAL(10,2), N | NotNull, >0, Digits(8,2) | money validator, positive, max 99999999.99, step .01 | Already compliant |
| Offering status | Status | contract_room_offering.status | VARCHAR(20), N | NotNull OfferingStatus enum | Enum selection | Already compliant |
| Seasonal rate create/edit | From/to | offering_seasonal_rate.valid_from / valid_to | DATE, N | NotNull, ordered period, contract containment/overlap checks | Required, ordered period and contract constraints | Retained |
| Seasonal rate create/edit | Rate | offering_seasonal_rate.rate_per_person_per_night | DECIMAL(10,2), N | NotNull, >0, Digits(8,2) | money validator, positive, max 99999999.99 | Already compliant |
| Customer create/edit AND booking registration | Name | customer.name | VARCHAR(150), N | NotBlank, Size(150) | notBlank, maxLength/maxlength 150 | Retained limits; added booking field message |
| Customer create/edit AND booking registration | Phone | customer.phone | VARCHAR(20), Y | Size(20), phone pattern | maxLength/maxlength 20, phone pattern | Retained limits; added booking field message; phone OR email required |
| Customer create/edit AND booking registration | Email | customer.email | VARCHAR(150), Y | Size(150), Email | maxLength/maxlength 150, email | Retained limits; added booking field message |
| Search / customization / booking | Hotel/contract/customer/offering/room-type IDs | matching FK/PK INT columns | signed INT; required FKs N | Positive Integer, strict integer JSON where used, existence/eligibility checks | Selected IDs, existing integer checks | Retained except contract hotel selector fix above; no arbitrary text limit |
| Search / customization / booking | Adults per room | reservation_item.number_of_adults | signed INT, N | Integer >=1; offering maxAdults and business limits | Existing integer, minimum, occupancy guidance | Retained |
| Search / booking | Stay dates | reservation.check_in_date / check_out_date | DATE, N | Required strict dates; exclusive checkout; 1..31 nights and advance limit | Required dates/nights, integer 1..31, existing business limits | Retained |
| Booking confirmation | Currency | reservation.currency_code | CHAR(3), Y | acceptedCurrency required [A-Z]{3}; recalculated currency comparison | Quotation-owned, not editable | Retained stricter business requirement |
| Booking confirmation | Accepted total | No directly persisted total column | Calculated sum, not a single DECIMAL column | >0, Digits(14,2), compared with recalculated quotation | Quotation-owned, acceptance checkbox | Do not incorrectly cap aggregate to one nightly-price column |
| Login | Username | user.username | VARCHAR(100), N | NotBlank, Size(100) | required, non-whitespace, maxLength/maxlength 100 | Added whitespace rejection and length-specific message |
| Login | Password | Compared against user.password_hash | hash VARCHAR(255), N; plaintext not stored | NotBlank, Size(200) existing login policy | required, non-whitespace, maxLength/maxlength 200 | Preserved policy; added whitespace rejection and length message; never trim password |
| Hotel/contract/room-type directories | Free-text filters | Not stored | No SQL column limit for a multi-field query | Existing specification query | Existing filter controls | No invented database limit; these are queries, not stored values |
| Customers / booking customer search | Query | Not stored | Existing API policy 150 | Service rejects >150 | maxLength/maxlength 150 | Already compliant |
| Reservations directory | Query | Not stored | Existing API policy 150 | Repository rejects >150 | maxlength 150 + maxLength(150), field message | Added model validation and message for programmatic values |
| Accommodation search | City/country | Matches hotel.city / country | VARCHAR(100), Y | DTO Size(100), service location rules | City model maxLength(100), native city limit, selected country | Retained |
| City/hotel autocomplete | Term | Not stored; matches hotel.city/name | Existing API term policy 100, distinct from name storage 150 | Service rejects terms >100 | native maxlength 100 and value-change guard/message | Retained; selected hotel display can contain the full 150-character name |
| Hotel form | Country catalogue filter | Local-only, not stored/sent | No SQL column | N/A | Local country filtering | No arbitrary storage limit |
| Reservation filters | Status/stay period/dates | SQL predicates, not new values | Enum/date rules | Existing validated filter enums/dates | Select controls and date validation | Retained |

All primary IDs and created-at timestamps are generated. No user-management form exists for `user.full_name VARCHAR(150)`, `role/status VARCHAR(20)` or the password hash. Reservation state is assigned by confirmation/cancellation actions. Historical snapshots are server-calculated, not editable: `reservation_item` rate DECIMAL(12,2), markup DECIMAL(7,4), selling price DECIMAL(14,2); nightly snapshots use the same types. Their storage ranges are not input limits for aggregate quotation totals.

## Semantics and schema findings

- Entity string lengths and decimal precisions match inspected columns. Hotel nullable address/city/country/rating and reservation nullable currency intentionally have stricter existing business rules. No column sizes or nullability were changed.
- Native maxlength helps typing/paste; reactive validators also reject overlength programmatic values. Inputs are not silently truncated. Existing edit forms use the same validators as creation.
- Size is checked on raw input before submission trimming, consistent with DTO validation occurring before service normalization. Room-type/customer services trim values; hotel UI trims its payload while hotel service preserves direct API text except country canonicalization. This existing normalization difference remains; neither path can exceed SQL limits. Blank required text remains rejected. Optional customer contacts become null after trimming.
- MySQL VARCHAR limits count characters; Java/Angular size validators count UTF-16 units, conservatively rejecting unsupported supplementary characters earlier. The inspected legacy utf8mb3 columns do not support supplementary Unicode characters. Charset migration is outside this task. No TEXT byte limit was incorrectly treated as a character limit.
- A syntactically invalid email or unrecognized country is not accepted merely because it fits its length. Boundary tests distinguish independent format/country constraints from size constraints.

## Files changed in this task

- Hotel form TS/HTML: expose one field-length mapping to model and native input validation.
- Contract list TS: explicit currency maxLength and signed-INT-safe hotel ID validation.
- Login TS/HTML: align whitespace-only rejection with NotBlank and show actual limits.
- Reservation booking TS/HTML: field-level customer registration messages.
- Reservation directory TS/HTML: enforce existing 150-character query policy in the model and show errors.
- HotelRequest and HotelUpdateRequest: explicit field-specific Size messages; existing limits unchanged.
- HotelValidationExceptionHandler: hotel-scoped 400 response with fieldErrors, matching existing management APIs; no effects on other controllers.
- HotelRegistrationValidationTest: direct API create/update boundaries and fieldErrors response.
- RequestFieldLimitsTest: DTO string, required text, rate and markup boundaries.
- ValidationMySqlIntegrationTest: actual SQL varchar boundary checks in a guarded disposable database with rollback.
- shared/form-limits.spec.ts: native/model/pasted/edit/customer/login/numeric boundaries.
- This report and mysql-columns.tsv: authoritative audit and evaluator evidence. Other working-tree changes predate this task.

## Verification

- Angular: `npm test -- --watch=false`: **225 passed, 20 test files**. The initial new fixture used an email domain label exceeding email format rules; corrected the fixture to valid labels, then reran the entire suite successfully.
- Backend: `./mvnw.cmd '-Dtest=*Test,!*MySqlIntegrationTest' test`: **371 reported, 353 passed, 18 skipped, zero failures/errors**. Includes 38 new DTO boundary cases and direct hotel create/update validation tests. Skipped tests are existing opt-in integration tests; this is not a claim that every database/security integration suite ran.
- Isolated MySQL: `ValidationMySqlIntegrationTest,HotelCountryMySqlIntegrationTest,QuotationSearchMySqlIntegrationTest`: **41 passed, zero skips/failures/errors** (7 boundary tests, 1 country test, 33 search tests). Strict-mode database boundaries, case/country behavior, prices and inventory search regressions passed.
- `git diff --check` passed, with only existing line-ending conversion notices.

The isolated database was `suntravels_validation_20261022_test`, cloned with schema only; no development records were copied or changed. V001 was applied only there for existing search regressions. Destructive tests require an explicit opt-in and the new test additionally asserts the exact disposable database name and strict SQL mode. Test fixtures roll back and the disposable database was removed after verification. Reservation MySQL confirmation/cancellation suites were not rerun in this focused task; their production code was not changed. The known reservation exception-wrapper assertion documented in the performance report was not part of these passing runs.

Evaluator explanation: Database field sizes are now reflected in both browser inputs and reactive form rules. Spring already enforced the sizes and now gives clearer hotel errors. Validation applies to pasted values, edits and direct API calls, while numerical precision, financial calculations and booking rules remain intact.
