# Feature-Based Packages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the current Java package refactor so `config` is the only shared technical root, behavior remains unchanged, and JPA explicitly maps every entity at its final feature-owned package.

**Architecture:** Business code lives under `admin`, `audit`, `event`, `fulfillment`, `identity`, `order`, `payment`, `settlement`, or `ticketing`, with technical subpackages created only when populated. Shared persistence and HTTP plumbing live under `config`; existing authentication and voucher code are absorbed into `identity` and `order` respectively.

**Tech Stack:** JDK 25, Maven WAR, Jakarta Servlet 6.1, Jakarta Persistence 3.1, Hibernate 6.6, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-27-feature-based-packages-design.md`

## Global Constraints

- Preserve current HTTP paths, JSON contracts, database mappings, validation, authorization, and security behavior.
- Preserve the user's existing uncommitted refactor; never reset or overwrite unrelated changes.
- Do not add or upgrade dependencies.
- Keep only populated feature subpackages; remove empty and speculative placeholders.
- Keep `persistence.xml` explicit with `exclude-unlisted-classes=true`, schema validation only, and UTC JDBC time.
- Use filesystem moves plus package/import changes; do not introduce new abstractions.

## Review Focus

- A stale compiled class in `target` must not hide an invalid source package; the final verification starts with `clean`.
- Every `@Entity`, including technical entity `Otp`, must appear exactly once in `persistence.xml`; Task 1 pins the exact 24-class set.
- Servlet annotations and endpoint paths must remain unchanged after package moves; Tasks 2–4 run the existing acceptance/controller tests.
- Authentication, CSRF, and request-size/content-type filters must retain their current mappings and denial behavior; Tasks 2–3 run `HttpSecurityTest` and auth acceptance tests.
- Shared enum moves must preserve every enum constant and JPA enum mapping; Task 5 runs invariant and model-mapping tests before the full suite.

---

### Task 1: Add package and JPA structure regression checks

**Files:**
- Create: `src/test/java/vn/ticketscenter/config/FeaturePackageStructureTest.java`
- Modify: `src/test/java/vn/ticketscenter/config/PersistenceMetadataTest.java`
- Test: `src/test/java/vn/ticketscenter/config/FeaturePackageStructureTest.java`
- Test: `src/test/java/vn/ticketscenter/config/PersistenceMetadataTest.java`

**Interfaces:**
- Consumes: repository source tree and `/META-INF/persistence.xml`.
- Produces: executable constraints for allowed top-level packages and the exact final entity allowlist.

- [ ] **Step 1: Write the failing structure test**

Add `productionPackagesAreFeatureBased()` using `java.nio.file.Files.list(Path.of("src/main/java/vn/ticketscenter"))`. Assert the directory names equal:

```java
Set.of("admin", "audit", "config", "event", "fulfillment",
        "identity", "order", "payment", "settlement", "ticketing")
```

Also walk those roots and assert no `.gitkeep` file exists under `src/main/java`.

- [ ] **Step 2: Strengthen the JPA metadata test**

Change `PersistenceMetadataTest.persistenceUnitListsBusinessEntitiesAndOnlyValidatesSchema()` to collect `<class>` text into a `Set<String>` and compare it with the exact 24 final class names. The set must contain `vn.ticketscenter.order.model.Coupon` and no `voucher`, `auth`, or old flat model package.

- [ ] **Step 3: Run the tests and confirm the intended red state**

Run: `mvn -B -Dtest=FeaturePackageStructureTest,PersistenceMetadataTest test`

Expected: FAIL because legacy top-level packages still exist and `Coupon` is still configured under `voucher`; configuration-property assertions continue to pass.

- [ ] **Step 4: Commit the regression checks**

```bash
git add src/test/java/vn/ticketscenter/config/FeaturePackageStructureTest.java src/test/java/vn/ticketscenter/config/PersistenceMetadataTest.java
git commit -m "test(architecture): enforce feature packages"
```

### Task 2: Consolidate shared plumbing under config

**Files:**
- Move: `src/main/java/vn/ticketscenter/transaction/DatabasePrincipal.java` → `src/main/java/vn/ticketscenter/config/persistence/DatabasePrincipal.java`
- Move: `src/main/java/vn/ticketscenter/transaction/TransactionManager.java` → `src/main/java/vn/ticketscenter/config/persistence/TransactionManager.java`
- Move: `src/main/java/vn/ticketscenter/controller/HealthServlet.java` → `src/main/java/vn/ticketscenter/config/web/HealthServlet.java`
- Move: `src/main/java/vn/ticketscenter/controller/OpenApiServlet.java` → `src/main/java/vn/ticketscenter/config/web/OpenApiServlet.java`
- Move: `src/main/java/vn/ticketscenter/controller/ReadinessServlet.java` → `src/main/java/vn/ticketscenter/config/web/ReadinessServlet.java`
- Move: `src/main/java/vn/ticketscenter/controller/HttpResponses.java` → `src/main/java/vn/ticketscenter/config/web/HttpResponses.java`
- Move: `src/main/java/vn/ticketscenter/filter/RequestValidationFilter.java` → `src/main/java/vn/ticketscenter/config/web/RequestValidationFilter.java`
- Move: `src/main/java/vn/ticketscenter/util/JsonObjectParser.java` → `src/main/java/vn/ticketscenter/config/web/JsonObjectParser.java`
- Move: `src/main/java/vn/ticketscenter/util/InputParser.java` → `src/main/java/vn/ticketscenter/config/util/InputParser.java`
- Move: `src/test/java/vn/ticketscenter/transaction/TransactionManagerTest.java` → `src/test/java/vn/ticketscenter/config/persistence/TransactionManagerTest.java`
- Modify: all production and test imports of the moved types.
- Test: `src/test/java/vn/ticketscenter/config/persistence/TransactionManagerTest.java`
- Test: `src/test/java/vn/ticketscenter/security/HttpSecurityTest.java`
- Test: `src/test/java/vn/ticketscenter/security/JsonObjectParserTest.java`
- Test: `src/test/java/vn/ticketscenter/acceptance/Day01IT.java`
- Test: `src/test/java/vn/ticketscenter/acceptance/OpenApiServletTest.java`
- Test: `src/test/java/vn/ticketscenter/acceptance/ReadinessServletTest.java`

**Interfaces:**
- Consumes: existing constructors and static helper methods unchanged.
- Produces: `config.persistence.DatabasePrincipal`, `config.persistence.TransactionManager`, `config.web.*`, and `config.util.InputParser` at their final packages.

- [ ] **Step 1: Move files and update package declarations**

Use `mv` for the listed files, then change only package declarations and imports. Keep class names, public signatures, Servlet/filter annotations, and URL patterns unchanged.

- [ ] **Step 2: Update every caller**

Run `rg -n 'vn\.ticketscenter\.(transaction|controller|filter\.RequestValidationFilter|util)' src/main/java src/test/java` and update every matching import for the moved shared types. Leave authentication-specific filters for Task 3.

- [ ] **Step 3: Run focused shared-plumbing tests**

Run: `mvn -B -Dtest=TransactionManagerTest,HttpSecurityTest,JsonObjectParserTest,InputParserTest,Day01IT,OpenApiServletTest,ReadinessServletTest test`

Expected: PASS with zero failures and unchanged endpoint/filter behavior.

- [ ] **Step 4: Commit the shared package move**

```bash
git add src/main/java src/test/java
git commit -m "refactor(config): consolidate shared plumbing"
```

### Task 3: Consolidate authentication into identity

**Files:**
- Move: `src/main/java/vn/ticketscenter/auth/controller/AuthServlet.java` → `src/main/java/vn/ticketscenter/identity/controller/AuthServlet.java`
- Move: `src/main/java/vn/ticketscenter/auth/controller/OtpServlet.java` → `src/main/java/vn/ticketscenter/identity/controller/OtpServlet.java`
- Move: `src/main/java/vn/ticketscenter/auth/dto/AuthDtos.java` → `src/main/java/vn/ticketscenter/identity/dto/AuthDtos.java`
- Move: every file in `src/main/java/vn/ticketscenter/auth/service/` → `src/main/java/vn/ticketscenter/identity/service/`
- Move: `src/main/java/vn/ticketscenter/filter/AuthenticationFilter.java` → `src/main/java/vn/ticketscenter/identity/filter/AuthenticationFilter.java`
- Move: `src/main/java/vn/ticketscenter/filter/CsrfFilter.java` → `src/main/java/vn/ticketscenter/identity/filter/CsrfFilter.java`
- Move: `src/main/java/vn/ticketscenter/integration/mail/` → `src/main/java/vn/ticketscenter/identity/integration/mail/`
- Modify: `src/main/java/vn/ticketscenter/config/AdminSeeder.java`
- Modify: `src/main/java/vn/ticketscenter/config/PersistenceListener.java`
- Modify: `src/main/java/vn/ticketscenter/admin/controller/AdminServlet.java`
- Modify: all production and test imports from `auth`, root `filter`, and `integration.mail`.
- Test: existing identity, security, admin, `Day04IT`, `Day05IT`, and `AuthFlowAcceptanceTest` classes.

**Interfaces:**
- Consumes: existing auth services, filters, mail adapter, and endpoint mappings.
- Produces: the same types and behavior under `vn.ticketscenter.identity` subpackages; no production `auth`, root `filter`, or root `integration` package remains.

- [ ] **Step 1: Move authentication-owned files**

Use filesystem moves, update package declarations, and keep all public class names and method signatures unchanged.

- [ ] **Step 2: Update all callers and package-qualified references**

Update imports plus direct references such as `vn.ticketscenter.filter.AuthenticationFilter.ACCOUNT_ATTRIBUTE`. Verify with:

`rg -n 'vn\.ticketscenter\.(auth|filter|integration\.mail)' src/main/java src/test/java`

Expected: no matches after the move.

- [ ] **Step 3: Run identity/security regression tests**

Run: `mvn -B -Dtest='vn.ticketscenter.identity.*,vn.ticketscenter.security.*,vn.ticketscenter.admin.*,AuthFlowAcceptanceTest,Day04IT,Day05IT' test`

Expected: PASS with zero failures; login, OTP, password reset, session, authorization, CSRF, admin, and mail-adapter tests retain their behavior.

- [ ] **Step 4: Commit the identity consolidation**

```bash
git add src/main/java src/test/java
git commit -m "refactor(identity): absorb authentication"
```

### Task 4: Consolidate voucher into order and update JPA

**Files:**
- Move: `src/main/java/vn/ticketscenter/voucher/controller/VoucherServlet.java` → `src/main/java/vn/ticketscenter/order/controller/VoucherServlet.java`
- Move: `src/main/java/vn/ticketscenter/voucher/dto/VoucherDtos.java` → `src/main/java/vn/ticketscenter/order/dto/VoucherDtos.java`
- Move: `src/main/java/vn/ticketscenter/voucher/model/Coupon.java` → `src/main/java/vn/ticketscenter/order/model/Coupon.java`
- Move: `src/main/java/vn/ticketscenter/voucher/repository/CouponRepository.java` → `src/main/java/vn/ticketscenter/order/repository/CouponRepository.java`
- Move: `src/main/java/vn/ticketscenter/voucher/service/VoucherService.java` → `src/main/java/vn/ticketscenter/order/service/VoucherService.java`
- Move: `src/test/java/vn/ticketscenter/voucher/VoucherServiceTest.java` → `src/test/java/vn/ticketscenter/order/VoucherServiceTest.java`
- Modify: `src/main/resources/META-INF/persistence.xml`
- Modify: `src/test/java/vn/ticketscenter/model/ModelMappingTest.java`

**Interfaces:**
- Consumes: existing voucher API route, DTO records, repository methods, and service constructor.
- Produces: identical behavior owned by `order`; `persistence.xml` lists `vn.ticketscenter.order.model.Coupon`.

- [ ] **Step 1: Move the voucher files and update references**

Keep `VoucherServlet`, DTO record names, service/repository APIs, and URL mappings unchanged. Update packages and imports only.

- [ ] **Step 2: Update explicit JPA and mapping metadata**

Replace `vn.ticketscenter.voucher.model.Coupon` with `vn.ticketscenter.order.model.Coupon` in `persistence.xml` and the model mapping test.

- [ ] **Step 3: Run order and metadata tests**

Run: `mvn -B -Dtest=VoucherServiceTest,PersistenceMetadataTest,ModelMappingTest test`

Expected: PASS; the exact JPA class-set assertion now accepts `order.model.Coupon`.

- [ ] **Step 4: Commit the order consolidation**

```bash
git add -A src/main/java/vn/ticketscenter/order src/main/java/vn/ticketscenter/voucher src/test/java/vn/ticketscenter/order src/test/java/vn/ticketscenter/voucher src/main/resources/META-INF/persistence.xml src/test/java/vn/ticketscenter/model/ModelMappingTest.java
git commit -m "refactor(order): absorb voucher feature"
```

### Task 5: Move shared domain enums to their owning features

**Files:**
- Create: `src/main/java/vn/ticketscenter/identity/model/IdentityEnums.java`
- Create: `src/main/java/vn/ticketscenter/event/model/EventEnums.java`
- Create: `src/main/java/vn/ticketscenter/ticketing/model/TicketingEnums.java`
- Create: `src/main/java/vn/ticketscenter/order/model/OrderEnums.java`
- Create: `src/main/java/vn/ticketscenter/fulfillment/model/FulfillmentEnums.java`
- Create: `src/main/java/vn/ticketscenter/settlement/model/SettlementEnums.java`
- Delete: `src/main/java/vn/ticketscenter/model/ModelEnums.java`
- Move: `src/test/java/vn/ticketscenter/model/ModelInvariantTest.java` → `src/test/java/vn/ticketscenter/config/persistence/ModelInvariantTest.java`
- Move: `src/test/java/vn/ticketscenter/model/ModelMappingTest.java` → `src/test/java/vn/ticketscenter/config/persistence/ModelMappingTest.java`
- Modify: every entity, DTO, service, and test importing or fully qualifying `ModelEnums`.
- Test: `src/test/java/vn/ticketscenter/config/persistence/ModelInvariantTest.java`
- Test: `src/test/java/vn/ticketscenter/config/persistence/ModelMappingTest.java`

**Interfaces:**
- Consumes: all existing enum names and constants.
- Produces: feature-owned enum containers with unchanged nested enum names and constant sets.

- [ ] **Step 1: Create the six enum containers**

Move enums without changing values:

- `IdentityEnums`: `UserStatus`, `OrganizationRole`, `OrganizationRequestStatus`
- `EventEnums`: `EventStatus`, `ZoneType`, `SeatStatus`
- `TicketingEnums`: `TicketHoldStatus`
- `OrderEnums`: `OrderStatus`, `PaymentStatus`, `DiscountType`
- `FulfillmentEnums`: `TicketStatus`, `CheckInResult`, `RefundRequestStatus`, `RefundRequestReason`, `RefundPurpose`, `RefundStatus`
- `SettlementEnums`: `SettlementStatus`, `PayoutStatus`

- [ ] **Step 2: Update references and remove ModelEnums**

Replace each import with its owning feature container. Run `rg -n 'ModelEnums|vn\.ticketscenter\.model' src/main/java src/test/java` and require no production references before deleting `ModelEnums.java`.

- [ ] **Step 3: Pin enum compatibility**

Extend `ModelInvariantTest` with assertions using `Enum.values()` that list every constant from the six new containers in its original order. This makes accidental loss or renaming fail independently of compilation.

- [ ] **Step 4: Run enum, entity, and feature tests**

Run: `mvn -B -Dtest=ModelInvariantTest,ModelMappingTest,VoucherServiceTest,AdminServiceTest,AuthorizationServiceTest,Day05IT test`

Expected: PASS with unchanged enum-backed entity behavior and mapping annotations.

- [ ] **Step 5: Commit the feature enum move**

```bash
git add src/main/java src/test/java
git commit -m "refactor(model): localize feature enums"
```

### Task 6: Remove obsolete packages and complete verification

**Files:**
- Delete: every remaining `.gitkeep` under `src/main/java`.
- Delete: now-empty `src/main/java/vn/ticketscenter/{auth,controller,filter,integration,job,model,transaction,util,voucher}` directories.
- Modify: `docs/tasks/CONVENTIONS.md`
- Verify: all changed production, resource, test, and documentation files.

**Interfaces:**
- Consumes: final package layout from Tasks 2–5.
- Produces: clean feature-based source tree and verified Maven WAR.

- [ ] **Step 1: Remove empty placeholders and align conventions**

Delete only confirmed-empty obsolete directories and `.gitkeep` files. Update the package-by-feature list in `CONVENTIONS.md` to include `admin` while retaining `identity` and `order`; do not list `auth` or `voucher` as features.

- [ ] **Step 2: Prove no stale package references remain**

Run:

```bash
rg -n 'vn\.ticketscenter\.(auth|controller|filter|integration|job|model|transaction|util|voucher)' src/main/java src/test/java src/main/resources
find src/main/java/vn/ticketscenter -name .gitkeep -print
```

Expected: both commands produce no output. Review any match before changing it; do not blindly replace package fragments embedded in unrelated text.

- [ ] **Step 3: Run focused architecture and JPA checks**

Run: `mvn -B -Dtest=FeaturePackageStructureTest,PersistenceMetadataTest,ModelMappingTest test`

Expected: PASS with the ten allowed roots and exact 24-entity allowlist.

- [ ] **Step 4: Run the clean full build**

Run: `mvn -B clean verify`

Expected: `BUILD SUCCESS`, zero test failures/errors, and a newly compiled WAR with no reliance on stale `target` classes.

- [ ] **Step 5: Check the SQL Server integration environment**

If the documented SQL Server credentials/environment are available, run `mvn -B -Psqlserver-it verify`. Otherwise record the integration profile as not run; do not claim it passed.

- [ ] **Step 6: Review the final diff and commit cleanup**

Run: `git diff --check` and `git status --short`, verify no unrelated user changes were added, then commit only the intended paths:

```bash
git add docs/tasks/CONVENTIONS.md src/main/java src/main/resources src/test/java
git commit -m "refactor: finish feature package layout"
```
