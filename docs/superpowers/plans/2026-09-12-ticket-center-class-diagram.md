# Ticket Center Class Diagram Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild `diagram.md` as a valid, readable diagrams.net class diagram whose domain model matches `spec.md`, has no detached or miswired connectors, and highlights the twelve core classes in yellow.

**Architecture:** Replace the corrupted generated graph with a clean diagrams.net XML document organized into six module containers. Model account permissions through role assignments, keep UI classes out of the domain model, and connect aggregates using explicit composition, association, generalization, and multiplicity labels.

**Tech Stack:** diagrams.net `mxGraphModel` XML, UML class notation, PowerShell XML validation.

**Spec:** `docs/superpowers/specs/2026-09-12-ticket-center-class-diagram-design.md`

## Global Constraints

- Preserve `diagram.md` as editable diagrams.net XML.
- Use `#FDE68A` for the title fill and `#D97706` for the border of core classes only.
- Keep class bodies white and use a readable dark foreground color.
- Every relationship must have both a valid `source` and `target` that resolve to class cells.
- Domain classes must not depend on `CheckInScreen` or another UI class.
- Do not modify `docs/EA/TicketsCenter.qea`, `.codex-docx-work/`, or `.codex_doc_compare_20260911/`.

## File Structure

- Modify: `diagram.md` — complete editable class diagram and its visual legend.
- Reference: `spec.md` — approved product and domain rules.
- Reference: `docs/superpowers/specs/2026-09-12-ticket-center-class-diagram-design.md` — approved strengthening design.

---

### Task 1: Rebuild the class inventory and module layout

**Files:**
- Modify: `diagram.md`

**Interfaces:**
- Consumes: business rules in `spec.md` sections 6, 8, and 9; the approved class-diagram design.
- Produces: one `mxGraphModel` containing six module containers and 37 uniquely identified domain class cells.

- [ ] **Step 1: Capture the current structural failure as a baseline**

Run this PowerShell check against the existing file:

```powershell
$doc = [xml](Get-Content -Raw -LiteralPath 'diagram.md')
$root = $doc.mxfile.diagram.mxGraphModel.root
$classIds = @($root.UserObject | Where-Object { $_.mxCell.vertex -eq '1' } | ForEach-Object id)
$broken = @($root.UserObject | Where-Object {
    $_.mxCell.edge -eq '1' -and
    (-not $_.mxCell.source -or -not $_.mxCell.target -or
     $_.mxCell.source -notin $classIds -or $_.mxCell.target -notin $classIds)
})
if ($broken.Count -eq 0) { throw 'Expected the current diagram to contain detached connectors.' }
"Baseline detached connectors: $($broken.Count)"
```

Expected: the command reports one or more detached connectors.

- [ ] **Step 2: Replace the root graph with deterministic module containers**

Create six labeled containers in this reading order:

| Container ID | Label | Position `(x,y,w,h)` |
| --- | --- | --- |
| `pkg-identity` | `IDENTITY & ORGANIZATION` | `(40,80,1080,760)` |
| `pkg-event` | `EVENT & SEATING` | `(1160,80,2140,920)` |
| `pkg-ticketing` | `TICKETING & INVENTORY` | `(3340,80,1260,920)` |
| `pkg-order` | `ORDER, PAYMENT & PROMOTION` | `(40,1040,1900,1120)` |
| `pkg-fulfillment` | `TICKET, CHECK-IN & REFUND` | `(1980,1040,1280,1120)` |
| `pkg-settlement` | `SETTLEMENT & AUDIT` | `(3300,1040,1300,1120)` |

Use a light package background, a dashed border, `startSize=32`, and enough padding that class cells never overlap the package title.

- [ ] **Step 3: Add the exact target class inventory**

Create these 37 classes with stable IDs of the form `class-<kebab-name>`:

```text
Identity & Organization:
User, RefreshToken, Organization, OrganizationMembership, Notification

Event & Seating:
EventCategory, Event, EventApprovalHistory, Venue, SeatingPlan,
SeatingZone, SeatingRow, SeatingSeat, EventSession, ScheduleZone, SessionSeat

Ticketing & Inventory:
TicketType, TicketInventory, TicketHold, TicketHoldItem

Order, Payment & Promotion:
Order, OrderItem, Payment, VNPayPayment, Coupon, CouponCondition, CouponRedemption

Ticket, Check-in & Refund:
Ticket, CheckIn, RefundRequest, RefundRequestItem, Refund

Settlement & Audit:
CommissionRule, Settlement, SettlementItem, Payout, AuditLog
```

Remove `Customer`, `OrganizerMember`, `StaffMember`, `PlatformAdmin`, and `CheckInScreen`. Rename the former organizer-member concept to `OrganizationMembership`.

- [ ] **Step 4: Normalize class contents**

Retain the valid attributes and behaviors from the original classes, with these exact corrections:

```text
User: add Set<PlatformRole> platformRoles; remove role-specific UI operations
OrganizationMembership: UUID id, OrganizationRole role, Instant joinedAt, boolean active
Event.status: EventStatus
Event.refundPolicy: RefundPolicy
EventSession.status: EventSessionStatus
ScheduleZone.type: ZoneType
Settlement.status: SettlementStatus
Payout.method: PayoutMethod
Payout.status: PayoutStatus
Coupon.discountType: DiscountType
CouponCondition.conditionType: CouponConditionType
CouponCondition.operator: ComparisonOperator
RefundRequest: keep exactly one BigDecimal requestedAmount
Refund: UUID id, BigDecimal amount, RefundStatus status, String providerReference,
        Instant processedAt; process(), markSucceeded(), markFailed()
AuditLog: UUID id, UUID actorId, String action, String aggregateType,
          UUID aggregateId, String detail, Instant createdAt
```

For `OrderItem`, retain `quantity`, `unitPrice`, and `lineTotal`; document the invariant in its operation compartment as `+validateSeatQuantity()`.

- [ ] **Step 5: Validate class count, uniqueness, removals, and additions**

Run:

```powershell
$doc = [xml](Get-Content -Raw -LiteralPath 'diagram.md')
$classes = @($doc.mxfile.diagram.mxGraphModel.root.UserObject |
    Where-Object { $_.mxCell.vertex -eq '1' -and $_.id -like 'class-*' })
$names = @($classes | ForEach-Object {
    ([System.Net.WebUtility]::HtmlDecode([string]$_.label) -replace '<[^>]+>', '')
})
if ($classes.Count -ne 37) { throw "Expected 37 classes, found $($classes.Count)." }
if (($classes.id | Sort-Object -Unique).Count -ne 37) { throw 'Class IDs are not unique.' }
if ($names | Where-Object { $_ -in @('Customer','OrganizerMember','StaffMember','PlatformAdmin','CheckInScreen') }) {
    throw 'Legacy role/UI class remains.'
}
foreach ($required in @('OrganizationMembership','Refund','AuditLog')) {
    if ($required -notin $names) { throw "Missing $required." }
}
'Class inventory valid.'
```

Expected: `Class inventory valid.`

- [ ] **Step 6: Commit the structural rebuild**

```powershell
git add -- diagram.md
git commit -m "docs: rebuild ticket center class inventory"
```

---

### Task 2: Add correct UML relationships and multiplicities

**Files:**
- Modify: `diagram.md`

**Interfaces:**
- Consumes: the stable class IDs created by Task 1.
- Produces: fully attached relationship cells with IDs `rel-<source>-<target>` and child multiplicity labels.

- [ ] **Step 1: Add identity and organization relationships**

Add these relationships exactly:

```text
User "1" *-- "0..*" RefreshToken : owns
User "1" -- "0..*" OrganizationMembership : participates_as
Organization "1" *-- "0..*" OrganizationMembership : has_members
User "1" -- "0..*" Order : places
User "1" -- "0..*" TicketHold : creates
User "1" -- "0..*" Ticket : owns
User "1" *-- "0..*" Notification : receives
```

- [ ] **Step 2: Add event and seating relationships**

```text
Organization "1" *-- "0..*" Event : organizes
Organization "1" o-- "0..*" Venue : manages
Organization "1" o-- "0..*" SeatingPlan : manages
Organization "1" *-- "0..*" Coupon : issues
Organization "1" *-- "1..*" CommissionRule : defines
Event "0..*" --> "1" EventCategory : categorized_by
Event "1" *-- "1..*" EventSession : schedules
Event "1" *-- "0..*" EventApprovalHistory : tracks
EventApprovalHistory "0..*" --> "1" User : acted_by
Venue "1" o-- "0..*" SeatingPlan : provides
SeatingPlan "1" *-- "1..*" SeatingZone : sections
SeatingZone "1" *-- "0..*" SeatingRow : contains
SeatingRow "1" *-- "1..*" SeatingSeat : holds
EventSession "0..*" --> "1" Venue : hosted_at
EventSession "0..*" --> "0..1" SeatingPlan : adopts
EventSession "1" *-- "0..*" ScheduleZone : snapshots
EventSession "1" *-- "0..*" SessionSeat : snapshots
ScheduleZone "1" -- "0..*" SessionSeat : groups
SessionSeat "0..*" --> "0..1" SeatingSeat : based_on
```

- [ ] **Step 3: Add ticketing, ordering, and payment relationships**

```text
EventSession "1" *-- "1..*" TicketType : offers
TicketType "1" *-- "1" TicketInventory : controls
TicketHold "0..*" --> "1" EventSession : for_session
TicketHold "1" *-- "1..*" TicketHoldItem : reserves
TicketHoldItem "0..*" --> "1" TicketType : locks_type
TicketHoldItem "0..*" --> "0..1" SessionSeat : locks_seat
Order "1" *-- "1..*" OrderItem : contains
OrderItem "0..*" --> "1" TicketType : purchases
OrderItem "0..*" --> "0..1" SessionSeat : assigns
OrderItem "1" *-- "1..*" Ticket : issues
Ticket "0..*" --> "1" EventSession : valid_for
Ticket "0..*" --> "0..1" SessionSeat : assigned_to
Order "1" *-- "0..*" Payment : payment_attempts
VNPayPayment --|> Payment
Coupon "1" *-- "1..*" CouponCondition : requires
Coupon "1" -- "0..*" CouponRedemption : records
Order "1" -- "0..1" CouponRedemption : applies
CouponRedemption "0..*" --> "1" User : redeemed_by
```

- [ ] **Step 4: Add check-in, refund, settlement, and audit relationships**

```text
CheckIn "0..*" --> "1" Ticket : verifies
CheckIn "0..*" --> "1" EventSession : during
CheckIn "0..*" --> "1" OrganizationMembership : performed_by
RefundRequest "0..*" --> "1" Order : against
RefundRequest "1" *-- "1..*" RefundRequestItem : claims
RefundRequestItem "0..*" --> "1" Ticket : targets
RefundRequest "1" *-- "0..*" Refund : executes
Refund "0..*" --> "1" Payment : reverses
Settlement "0..*" --> "1" Organization : pays_to
Settlement "1" *-- "1..*" SettlementItem : aggregates
SettlementItem "0..*" --> "1" Order : reconciles
Settlement "1" *-- "0..*" Payout : distributes
Settlement "0..*" --> "1" CommissionRule : applies_rule
AuditLog "0..*" --> "1" User : actor
```

- [ ] **Step 5: Validate every relationship endpoint**

Run:

```powershell
$doc = [xml](Get-Content -Raw -LiteralPath 'diagram.md')
$root = $doc.mxfile.diagram.mxGraphModel.root
$classIds = @($root.UserObject | Where-Object { $_.id -like 'class-*' } | ForEach-Object { [string]$_.id })
$edges = @($root.mxCell | Where-Object { $_.edge -eq '1' })
$invalid = @($edges | Where-Object {
    -not $_.source -or -not $_.target -or
    [string]$_.source -notin $classIds -or [string]$_.target -notin $classIds
})
if ($invalid.Count -ne 0) { throw "Invalid relationship endpoints: $($invalid.Count)." }
if (($edges.id | Sort-Object -Unique).Count -ne $edges.Count) { throw 'Relationship IDs are not unique.' }
"Relationship endpoints valid: $($edges.Count)."
```

Expected: zero invalid endpoints and a positive relationship count.

- [ ] **Step 6: Commit the relationship model**

```powershell
git add -- diagram.md
git commit -m "docs: connect ticket center domain aggregates"
```

---

### Task 3: Apply hierarchy, highlighting, and visual cleanup

**Files:**
- Modify: `diagram.md`

**Interfaces:**
- Consumes: complete class and relationship graph from Tasks 1–2.
- Produces: readable layout with consistent styles and an explicit legend.

- [ ] **Step 1: Apply the core-class title style**

Apply `fillColor=#FDE68A;strokeColor=#D97706;strokeWidth=2` to exactly:

```text
User, Organization, Event, EventSession, TicketInventory, TicketHold,
Order, Payment, Ticket, RefundRequest, Coupon, Settlement
```

Use `swimlaneFillColor=#FFFFFF` so only the title compartment is yellow.

- [ ] **Step 2: Apply the supporting-class style**

Apply `fillColor=#E2E8F0;strokeColor=#64748B;strokeWidth=1` to all other class titles, with white class bodies. Use Arial 13px for members and Arial bold 14px for names.

- [ ] **Step 3: Normalize relationship rendering**

Use orthogonal routing, `rounded=0`, `jumpStyle=arc`, `jumpSize=10`, and `strokeColor=#475569`. Use:

```text
composition: startArrow=diamondThin;startFill=1
aggregation: startArrow=diamondThin;startFill=0
association: endArrow=open;endFill=0
generalization: endArrow=block;endFill=0
```

Place multiplicity labels at each end and relationship names near the center. No helper/free endpoint ellipses are allowed.

- [ ] **Step 4: Add title and legend**

Add `TICKET CENTER — STRENGTHENED DOMAIN CLASS DIAGRAM` at the top. Add a compact legend stating:

```text
Yellow title = aggregate root / critical class
◆ Composition   ◇ Aggregation   → Association   ▷ Generalization
```

- [ ] **Step 5: Validate highlighting and forbidden helper nodes**

Run:

```powershell
$doc = [xml](Get-Content -Raw -LiteralPath 'diagram.md')
$root = $doc.mxfile.diagram.mxGraphModel.root
$core = @('User','Organization','Event','EventSession','TicketInventory','TicketHold','Order','Payment','Ticket','RefundRequest','Coupon','Settlement')
$classes = @($root.UserObject | Where-Object { $_.id -like 'class-*' })
$yellow = @($classes | Where-Object { [string]$_.mxCell.style -match 'fillColor=#FDE68A' } | ForEach-Object {
    ([System.Net.WebUtility]::HtmlDecode([string]$_.label) -replace '<[^>]+>', '')
})
if ((Compare-Object ($core | Sort-Object) ($yellow | Sort-Object))) { throw 'Yellow class set is incorrect.' }
$free = @($root.mxCell | Where-Object { [string]$_.id -match '(source|target)-free' })
if ($free.Count -ne 0) { throw "Free endpoint helpers remain: $($free.Count)." }
'Highlight and helper-node validation passed.'
```

Expected: `Highlight and helper-node validation passed.`

- [ ] **Step 6: Commit the visual hierarchy**

```powershell
git add -- diagram.md
git commit -m "docs: highlight core ticket center classes"
```

---

### Task 4: Complete structural and visual verification

**Files:**
- Verify: `diagram.md`

**Interfaces:**
- Consumes: completed diagrams.net XML.
- Produces: evidence that the artifact is structurally valid, semantically complete, and visually usable.

- [ ] **Step 1: Parse the final XML and verify graph invariants**

Run:

```powershell
$doc = [xml](Get-Content -Raw -LiteralPath 'diagram.md')
if ($doc.mxfile.diagram.mxGraphModel.root.mxCell.id -notcontains '0') { throw 'Missing mxGraph root cell 0.' }
if ($doc.mxfile.diagram.mxGraphModel.root.mxCell.id -notcontains '1') { throw 'Missing mxGraph layer cell 1.' }
$allIds = @($doc.SelectNodes('//*[@id]') | ForEach-Object { [string]$_.id })
if (($allIds | Sort-Object -Unique).Count -ne $allIds.Count) { throw 'Duplicate XML IDs detected.' }
'XML and ID invariants passed.'
```

Expected: `XML and ID invariants passed.`

- [ ] **Step 2: Verify corrected domain rules**

Search the final artifact:

```powershell
rg -n "OrganizationMembership|Refund|AuditLog|PlatformRole|OrganizationRole|RefundPolicy" diagram.md
rg -n "Customer|OrganizerMember|StaffMember|PlatformAdmin|CheckInScreen|-BigDecimal amount.*-BigDecimal amount" diagram.md
```

Expected: the first search finds all strengthened concepts; the second search returns no matches.

- [ ] **Step 3: Open the file in diagrams.net and inspect at 100% and fit-to-page**

Verify visually that:

```text
1. All six module headers are visible.
2. No class overlaps another class or a module header.
3. No connector terminates in empty canvas.
4. Relationship names and multiplicities remain legible.
5. Exactly twelve class-title compartments are yellow.
6. The primary flow reads from identity/event through ticketing/order to fulfillment/settlement.
```

- [ ] **Step 4: Check repository scope**

Run:

```powershell
git status --short
git diff --check HEAD~3..HEAD -- diagram.md
```

Expected: the diagram commits contain no whitespace errors, and unrelated pre-existing files remain untouched.

- [ ] **Step 5: Record final verification**

If the visual inspection requires small routing or spacing adjustments, edit only geometry/style fields in `diagram.md`, rerun Steps 1–4, then commit:

```powershell
git add -- diagram.md
git commit -m "docs: finalize class diagram verification"
```
