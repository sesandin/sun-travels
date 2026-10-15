# Customer Management implementation

Implemented on 15 October 2026 as a minimal authenticated customer directory for the existing Spring Boot and Angular application.

## Outcome

ADMIN and AGENT staff can list, search, register, view and update customers. The module maps the existing `customer` table explicitly and does not create, alter, delete or migrate database records or tables. There is no delete endpoint, public/customer login, reservation workflow, history, loyalty or payment functionality.

The accessible schema reference was inspected without execution. It confirms `customer_id` as an identity primary key, required `name VARCHAR(150)`, nullable `phone VARCHAR(20)` and nullable `email VARCHAR(150)`. Hibernate `ddl-auto=validate` is unchanged.

## REST API

All endpoints require an authenticated ADMIN or AGENT session. Spring Security remains authoritative and CSRF remains required for POST and PUT.

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/customers?query=&page=0&size=20` | Paginated, name-sorted directory; query matches name, phone, email, and an exact numeric customer ID |
| GET | `/api/customers/{id}` | One customer or 404 |
| POST | `/api/customers` | Creates a customer and returns 201 with the assigned customer ID |
| PUT | `/api/customers/{id}` | Updates contact fields while retaining the customer ID |

The default page size is 20 and the maximum is 50. Page numbers are zero based. Search text is trimmed and capped at 150 characters. An empty result is a successful page with an empty `customers` array.

Search predicates are built with the JPA Criteria API for MySQL portability. Name, phone and email use case-insensitive contains matching; numeric input additionally matches that exact customer ID. `%`, `_` and backslash in user input are escaped and treated as literal search text.

Request body:

```json
{
  "name": "Alice Perera",
  "phone": "+94 77 123 4567",
  "email": "alice@example.com"
}
```

`phone` or `email` may be null, but at least one must contain a value. Response:

```json
{
  "customerId": 7,
  "name": "Alice Perera",
  "phone": "+94 77 123 4567",
  "email": "alice@example.com"
}
```

List response:

```json
{
  "customers": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Invalid input returns 400 with the existing message/field-errors convention, missing customers return 404, anonymous requests return 401, and unsupported roles or endpoints remain denied. Database details and stack traces are not returned.

## Validation

- Name is trimmed, required, cannot be whitespace only, and is limited to 150 characters.
- Surrounding phone/email whitespace is trimmed. Blank optional values are stored as null.
- At least one of phone or email is required; phone-only, email-only and both are supported.
- Phone accepts 5 to 20 characters from digits, spaces, `+`, parentheses, period and hyphen, and must contain a digit. It does not impose a Sri Lanka-only format.
- Email is validated when supplied and limited to 150 characters.
- Controller DTO validation and independent service validation enforce the core invariants.
- Matching names or contact values are not merged, overwritten or treated as unique because the existing schema defines no such uniqueness rule.
- Updates load the identified row and never change `customer_id`.

## Angular interface

`/customers` is guarded by the existing authenticated route guard and appears once in the sidebar for both ADMIN and AGENT. The previous Customers “Soon” item was removed; Reservations remains marked as future work.

The responsive directory provides debounced server-backed search as the user types (with Enter/Search button support), pagination, loading/error/retry/empty states, contact details, Add Customer and View / Edit actions, field-level validation, duplicate-submit prevention and success notices that include the persisted customer ID. The typed `CustomerService` can be reused later to search/select a customer or register one during reservation confirmation. It does not create a reservation or introduce a pending state.

## Added files

Backend:

- `entity/Customer.java`
- `dto/CustomerRequest.java`, `CustomerResponse.java`, `CustomerPageResponse.java`
- `repository/CustomerRepository.java`
- `service/CustomerService.java`
- `controller/CustomerController.java`
- `exception/CustomerNotFoundException.java`, `InvalidCustomerException.java`, `CustomerExceptionHandler.java`
- `service/CustomerServiceTest.java`, `controller/CustomerControllerTest.java`

Frontend:

- `models/customer.model.ts`
- `services/customer.service.ts`
- `features/customers/customer-list.ts`, `.html`, `.scss`, `.spec.ts`

## Modified files

- Backend `SecurityConfig.java` and `AuthenticationSecurityTest.java`
- Frontend `app.routes.ts`, `app.html`, `app.ts` and `app.spec.ts`

## Verification

- Focused customer/backend security tests: 16 passed (6 service, 3 controller, 7 authentication/RBAC including the new access case).
- Database-independent backend package: 216 tests passed, zero failures/errors; compilation, JAR creation and Spring Boot repackaging passed.
- Full backend suite: 265 discovered; 235 passed, 29 opt-in MySQL integration tests skipped, zero assertion failures, and one application-context error. The context error is environmental because database credentials are unavailable, so Hibernate cannot obtain MySQL metadata. Live Customer entity validation and repository query execution are therefore not claimed as verified.
- Angular: 116 tests passed across 16 files, including 8 customer screen/API tests and direct coverage of debounced field input.
- Angular production build passed (462.62 kB initial bundle, estimated transfer 109.02 kB).
- `git diff --check` passed; only line-ending notices were reported.

## Manual verification

1. Supply the existing `DB_USERNAME` and `DB_PASSWORD`, start Spring Boot with `ddl-auto=validate`, and confirm the application validates the manually maintained `customer` table. Do not import a dump or enable schema creation.
2. Start Angular through `npm.cmd start` so the checked-in `/api` proxy is used. Log in as ADMIN and AGENT separately and confirm both see and can open Customers.
3. Confirm an anonymous `/api/customers` request returns 401. Confirm authenticated POST/PUT requests without a valid CSRF token are rejected.
4. Register phone-only, email-only and dual-contact customers. Verify whitespace normalization, null storage for blank optional fields, returned IDs, refresh persistence and clear 400 messages for invalid names/contacts/lengths.
5. Search by partial name, phone and email and by exact numeric customer ID. Exercise multiple pages with more than 20 controlled records.
6. Open an existing customer, update their details and verify the ID stays unchanged. Confirm no delete control or DELETE API exists.
7. Regression-check ADMIN Hotel, Room Type, Contract, Offering and Seasonal Rate operations, and check Search & Quotations as both roles.

No customer integration fixtures were run against MySQL, no existing customer data was changed, no schema change or dependency was added, and no Git commit or push was made. Reservation confirmation, customer selection within that workflow and customer reservation history remain deferred.
