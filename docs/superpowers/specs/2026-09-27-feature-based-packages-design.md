# Feature-Based Package Refactor Design

## Goal

Complete the in-progress package refactor so production Java code is organized by business feature, with `config` as the only shared top-level technical package, while preserving all current HTTP behavior and JPA mappings.

## Package Structure

The allowed top-level packages under `vn.ticketscenter` are:

- `admin`
- `audit`
- `config`
- `event`
- `fulfillment`
- `identity`
- `order`
- `payment`
- `settlement`
- `ticketing`

Each feature creates only the subpackages it currently needs: `controller`, `dto`, `exception`, `filter`, `model`, `repository`, `service`, or `util`. Empty placeholders and speculative `.gitkeep` files are removed. A feature may have multiple Servlet classes inside its `controller` package when the HTTP flows are independently cohesive.

## Ownership Changes

### Identity

`identity` owns account, authentication, authorization, organization membership, OTP, password reset, session, and authentication-specific HTTP filtering.

- Move all current `auth/controller`, `auth/dto`, and `auth/service` classes under matching `identity` subpackages.
- Keep existing `identity/model`, `identity/repository`, `identity/service`, and `identity/dto` classes under `identity`.
- Move `AuthenticationFilter` and `CsrfFilter` to `identity/filter`.
- Move the mail gateway and configured SMTP implementation to `identity/integration/mail`, because OTP is their only current consumer.
- Preserve the existing Servlet names, URL mappings, validation, authorization checks, and responses.

### Order

`order` owns orders, order items, payments recorded on orders, coupons, and voucher validation.

- Move all current `voucher` classes into the matching `order` subpackages.
- Preserve voucher endpoint mappings and behavior; package ownership changes without renaming public HTTP routes.

### Admin

`admin` remains an independent feature because platform administration is a real use case spanning users, organizations, events, and orders. Its existing controller, DTO, and service remain under `admin`.

### Other Features

Existing entity and DTO packages remain grouped under `audit`, `event`, `fulfillment`, `payment`, `settlement`, and `ticketing`. No controller, repository, or service package is created until that feature has an implementation that needs it.

## Shared Configuration Boundary

`config` is the only shared top-level technical package.

- Move `TransactionManager` and `DatabasePrincipal` to `config/persistence` alongside the existing persistence bootstrap classes.
- Move application-wide HTTP response handling, JSON request parsing, and `RequestValidationFilter` to `config/web`.
- Keep application, database, mail, server, and VNPAY configuration classes under `config`.
- Move `InputParser` to `config/util`, matching its current configuration/input-validation use.
- Remove the old top-level `controller`, `filter`, `integration`, `job`, `model`, `transaction`, and `util` packages after their real classes are relocated.

Shared helpers are moved once rather than copied into features. No new interfaces, factories, or framework dependencies are introduced.

## Domain Enums

Replace the shared `ModelEnums` container with feature-owned enum containers so domain types do not remain in a top-level technical `model` package:

- identity and organization enums under `identity/model`
- event, zone, and seat enums under `event/model`
- hold enums under `ticketing/model`
- order, payment, and discount enums under `order/model`
- ticket, check-in, and refund enums under `fulfillment/model`
- settlement and payout enums under `settlement/model`

Only imports and owning type references change; enum names and values remain unchanged.

## JPA Configuration

`META-INF/persistence.xml` continues to use an explicit entity allowlist with `exclude-unlisted-classes=true`. Every `<class>` entry is updated to the final package name.

Verification must prove:

- all 24 currently annotated entity classes are listed exactly once;
- no listed class is missing or points to an old package;
- Hibernate remains configured with `hibernate.hbm2ddl.auto=validate`, no schema generation, and UTC JDBC time;
- the persistence unit name remains `ticketscenter` and transaction type remains `RESOURCE_LOCAL`.

## Tests and Verification

Tests move only where their feature ownership changes; acceptance tests remain under `acceptance`. Imports are updated without changing test intent.

The existing persistence metadata test is extended to compare the explicit JPA class list against the annotated entities in source output, preventing future missed entries after package moves. Verification is:

1. Run the focused metadata and mapping tests.
2. Run `mvn -B clean verify` to force compilation without stale `target` classes and execute the default test suite.
3. Run the SQL Server integration profile only when its required database environment is available; otherwise report it as not run rather than treating it as passing.

## Constraints

- Preserve the user's current uncommitted refactor and fold it into the final structure; do not reset or overwrite unrelated work.
- Do not add or upgrade dependencies.
- Do not change endpoint paths, JSON contracts, database schema, entity table/column mappings, authorization, validation, or security behavior.
- Prefer filesystem moves and import/package updates; behavior changes are outside this refactor.
