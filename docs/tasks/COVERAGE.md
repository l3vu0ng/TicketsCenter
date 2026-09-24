# Ma trận truy vết SPEC → task → minh chứng

Mọi mục dưới đây ở trạng thái **đã phân task, chưa triển khai/chưa nghiệm thu**. Đây là ma trận kế hoạch. Khi thực hiện, bổ sung link evidence và trạng thái PASS/FAIL/BLOCKED; không đổi ý nghĩa thành đã hoàn thành chỉ vì có tên object.

Nguồn: [SPEC](../references/SPEC.md). Lịch: [README](README.md). Mỗi task phải tuân [CONVENTIONS](CONVENTIONS.md). File bằng chứng ngày `docs/evidence/day-NN.md`; lớp integration `DayNNIT`; SQL test `database/tests/day-NN.sql`. Cuối cùng D21-T02 chạy lại catalog/behavior trên bản bàn giao.

## 23 lớp nghiệp vụ (đúng 23, không cộng DTO/bảng kỹ thuật)

| Lớp | Mapping nền | Hành vi hoàn thành | Ca nghiệm thu |
|---|---|---|---|
| User | D02/D03 | D04/D05 | Verified trước Hold, hash/session/authVersion/reset |
| Organization | D02/D03 | D06 | Approve tạo một org, có rule và manager |
| OrganizationMembership | D02/D03 | D06 | Unique pair, role scope, giữ manager cuối |
| OrganizationRequest | D02/D03 | D06 | PENDING→APPROVED/REJECTED, approve lặp |
| EventCategory | D02/D03 | D02 seed/D07 đọc | FK category và bộ lọc Event |
| Event | D02/D03 | D07/D16 | Lịch, publish/rule, cancel trước start |
| Zone | D02/D03 | D07/D08/D15 | Seated/standing, quota và trả kho |
| Seat | D02/D03 | D07/D08/D11/D15 | hold→sold→release/refund, layout protection |
| TicketHold | D02/D03 | D08/D11 | ACTIVE duy nhất,user TTL10,release/consume |
| TicketHoldItem | D02/D03 | D08 | seat/quantity/event/price snapshot |
| Order | D02/D03 | D09/D11 | Một Order/Hold, total, markPaid kể cả0đ |
| OrderItem | D02/D03 | D09/D11 | Snapshot giá/nhãn, một item N tickets |
| Payment | D02/D03 | D10/D11/D12 | PENDING/CAPTURED/FAILED/UNKNOWN, txnRef idempotent |
| Coupon | D02/D03 | D09 | ≤30%, quota, reserved/consumed lịch sử |
| Ticket | D02/D03 | D11/D13/D14/D15 | QR riêng, paidAmount, trạng thái bất biến |
| CheckIn | D02/D03 | D13 | Một success, history invalid code nullable ticket |
| RefundRequest | D02/D03 | D14/D16 | Open unique ticket, reasonType, approve/reject |
| Refund | D02/D03 | D11/D15 | Customer/compensation, unknown/retry, amount cap |
| CommissionRule | D02/D03 | D06/D07/D17 | Hiệu lực, cùng org, khóa applied terms |
| Settlement | D02/D03 | D17 | DRAFT→CONFIRMED/PAID, blockers và freeze |
| SettlementItem | D02/D03 | D17 | Một Order/Item, tổng và freeze |
| Payout | D02/D03 | D17 | Pending reserves, no overspend, idempotent |
| AuditLog | D02/D03 | D06…D18 | Actor context, decision lookup, append-only |

## 20 nhóm constraint

| Mã | Tên theo SPEC | Task cài đặt | Kiểm chứng |
|---|---|---|---|
| C01 | UQ_User_NormalizedEmail | D02-T02; D04-T01 | Email normalized duplicate/concurrent |
| C02 | UQ_Membership_User_Organization | D02-T02; D06-T03 | Duplicate kể cả inactive |
| C03 | UQ_Seat_Zone_Row_Number | D02-T02; D07-T01 | Cùng nhãn/khu bị chặn |
| C04 | CK_Event_TimeRange | D02-T02; D07-T01 | saleStart<saleEnd<=start<end |
| C05 | CK_Zone_Price_Quota | D02-T02; D08-T04 | Price/quota, held+sold<=capacity |
| C06 | CK_Coupon_Discount_Limit | D02-T02; D09-T02 | Đúng một kiểu, tỷ lệ/thời hạn/maxUses |
| C07 | CK_HoldItem_Seat_Quantity + CK_OrderItem_Seat_Quantity | D02-T02; D08/D09 | Seat quantity1; standing>0; cross-table SP |
| C08 | CK_Order_Amounts | D02-T02; D09-T04 | Total=subtotal-discount, cap30% |
| C09 | UQ_Payment_TxnRef | D02-T02; D10/D11 | Unique NOT NULL, IPN lặp |
| C10 | DF_OrganizationRequest_Status + status CHECK | D02-T02; D06-T01 | Default PENDING và reject enum lạ |
| C11 | CK_TicketHold_TimeRange | D02-T02; D08 | expires>created, SP đặt10 phút |
| C12 | CK_HoldItem_UnitPrice + CK_OrderItem_UnitPrice | D02-T02; D08/D09 | Snapshot>=0, NOT NULL |
| C13 | CK_Ticket_PaidAmount | D02-T02; D11-T01 | >=0, tổng do F06/SP09 kiểm |
| C14 | CK_Payment_PositiveAmount | D02-T02; D10-T01 | Amount>0; không Payment zero |
| C15 | CK_Refund_PositiveAmount | D02-T02; D14-T02 | Amount>0; zero không Refund |
| C16 | CK_CommissionRule_Terms | D02-T02; D06/D17 | Rate/fee>=0, thời gian hợp lệ |
| C17 | CK_SettlementItem_Amounts | D02-T02; D17 | Net equation, refund/commission cap |
| C18 | CK_Settlement_Amounts | D02-T02; D17 | Header equation và sum Item |
| C19 | CK_Payout_PositiveAmount | D02-T02; D17-T03 | Amount>0, tổng kiểm SP16 |
| C20 | CK_Refund_Purpose_Request | D02-T02; D11/D15 | Purpose/request/payment linkage |

## 10 View

| Mã | Object | Task sở hữu | Caller/ca kiểm chứng |
|---|---|---|---|
| V01 | vw_PublicEvents | D07-T04 | EventQueryRepository → GET /events; public PUBLISHED only |
| V02 | vw_ZoneInventory | D07-T04 | Zone read/F03/report; hai loại khu, không join nhân dòng |
| V03 | vw_OrderFinancialSummary | D17-T01 | Order/report/settlement query; tách compensation |
| V04 | vw_EventSalesReport | D18-T01 | ReportRepository; zero Event/confirmed snapshot |
| V05 | vw_CheckInHistory | D13-T02 | CheckInRepository/report; LEFT JOIN giữ mã lạ |
| V06 | vw_TicketDetails | D11-T03 | TicketService/query; không code/QR, owner scope |
| V07 | vw_RefundRequestOverview | D14-T03 | RefundRepository; nhiều attempts không nhân tiền |
| V08 | vw_SettlementPayoutBalance | D17-T03 | Settlement/report; pending khác paid |
| V09 | vw_CouponUsage | D09-T02 | Coupon API/F08/SP03; RESERVED không tự biến mất |
| V10 | vw_OrganizationMembers | D06-T03 | Membership/profile; không auth fields |

## 17 Stored Procedure và đường gọi ứng dụng

| Mã | Object | Task sở hữu | Caller / kiểm tra nổi bật |
|---|---|---|---|
| SP01 | usp_ApproveOrganizationRequest | D06-T02 | OrganizationService→Repository; approve lặp |
| SP02 | usp_CreateTicketHold | D08-T02 | HoldService→Repository; 1–8/TTL/quota/user |
| SP03 | usp_ApplyOrderCoupon | D09-T03 | OrderService→Repository; đổi mã atomic |
| SP04 | usp_CheckInTicket | D13-T01 | CheckInService; một success |
| SP05 | usp_CreateRefundRequest | D14-T01 | RefundService; open unique/race scan |
| SP06 | usp_CreateOrderFromHold | D09-T01 | OrderService; same Hold→same Order |
| SP07 | usp_ReleaseTicketHold | D08-T01 | HoldService/HoldExpiryJob/SP02/SP17; một lần trả |
| SP08 | usp_BeginOrderPayment | D10-T01 | PaymentService; pending reuse/zero |
| SP09 | usp_ApplyPaymentResult | D11-T02 | Verified IPN/query worker/zero buyer; late compensation |
| SP10 | usp_DecideRefundRequest | D14-T02 | Admin RefundService/SP17; explicit failed retry |
| SP11 | usp_ApplyRefundResult | D15-T02 | RefundJob; no public result setter |
| SP12 | usp_PublishEvent | D07-T03 | EventService admin; rule snapshot |
| SP13 | usp_CancelEvent | D16-T01 | CancellationService; cancel+outbox |
| SP14 | usp_RecalculateSettlement | D17-T02 | SettlementService/SP15; DRAFT only |
| SP15 | usp_ConfirmSettlement | D17-T02 | SettlementService; F09+recalc under lock |
| SP16 | usp_RecordPayout | D17-T03 | SettlementService + simulated adapter; balance |
| SP17 | usp_ProcessCancelledOrder | D16-T02 | EventCancellationJob; one Order/transaction |

## 10 Function

| Mã | Object | Task sở hữu | Caller / ca biên |
|---|---|---|---|
| F01 | fn_CalculateCouponDiscount | D09-T02 | SP03/preview; floor đồng, invalid→NULL |
| F02 | fn_CalculateCommission | D17-T01 | SP14/report; HALF_UP/cap/remaining0 |
| F03 | fn_GetZoneAvailability | D07-T04 | EventQueryRepository; V02 shared, not reservation |
| F04 | fn_GetRefundableTickets | D14-T01 | RefundRepository/SP05 đối chiếu; before start |
| F05 | fn_GetOrganizationRevenue | D18-T01 | ReportRepository/CSV; cohort historical cutoff |
| F06 | fn_AllocateTicketPaidAmounts | D11-T01 | SP09; largest remainder/tie/zero |
| F07 | fn_GetEventCheckInWindow | D07-T04 | CheckIn reads/SP04; [start−60,end) |
| F08 | fn_GetCouponEligibility | D09-T02 | Coupon preview/SP03; quota/org/validity |
| F09 | fn_GetSettlementBlockers | D17-T01 | Settlement reads/SP15; resolved FAILED not blocker |
| F10 | fn_GetOrganizationCashFlow | D18-T01 | ReportRepository/CSV; phát sinh thu/hoàn trong kỳ |

## 10 Trigger

| Mã | Object | Task sở hữu | Kiểm chứng |
|---|---|---|---|
| TR01 | trg_Zone_ProtectPublishedLayout | D07-T03 | Cả parent cũ/mới, giá được đổi, layout khóa |
| TR02 | trg_Seat_ProtectPublishedLayout | D07-T03 | Nhãn/khu khóa, status đổi được |
| TR03 | trg_CommissionRule_ProtectAppliedTerms | D07-T03 | Applied fee/rate/org không sửa; effectivity hợp lệ |
| TR04 | trg_SettlementItem_ProtectConfirmedAmounts | D17-T02 | Insert/update/delete/move parents confirmed |
| TR05 | trg_Event_AuditStatusChange | D07-T03 | Một log/đổi thật, actor context, rollback |
| TR06 | trg_Membership_ProtectLastManager | D06-T03 | Multirow và hai manager race |
| TR07 | trg_Coupon_ProtectUsageLimit | D09-T02 | Quota change đua reserve; no retroactive discount |
| TR08 | trg_Ticket_ProtectIssuedSnapshot | D11-T03 | Snapshot khóa, state machine vẫn chạy |
| TR09 | trg_Settlement_ProtectConfirmedSnapshot | D17-T02 | Header freeze, cho CONFIRMED→PAID hợp lệ |
| TR10 | trg_AuditLog_AppendOnly | D18-T03 | UPDATE/DELETE blocked; transaction rollback allowed |

## 15 index hiệu năng

| Mã | Object | Query dùng / ngày có caller | Đo và nghiệm thu |
|---|---|---|---|
| IX01 | IX_Event_Status_StartTime | Catalog D07 | D19-T03 baseline; D20-T01 after |
| IX02 | IX_Seat_Zone_Status | V02/F03 D07 | D19-T03; D20-T01 |
| IX03 | IX_Order_User_CreatedAt | Lịch sử buyer D09 | D19-T03; D20-T01 |
| IX04 | IX_Payment_Status_CreatedAt | PaymentReconciliationJob D12 | D19-T03; D20-T01 |
| IX05 | IX_CheckIn_Event_ScannedAt | Check-in history D13 | D19-T03; D20-T01 |
| IX06 | IX_TicketHold_Status_ExpiresAt | HoldExpiryJob D12 | D19-T03; D20-T01 |
| IX07 | IX_Order_Event_Status_PaidAt | Settlement/cohort D17/D18 | D19-T03; D20-T01 |
| IX08 | IX_Refund_Status_CreatedAt | RefundJob D15 | D19-T03; D20-T01 |
| IX09 | IX_CouponRedemption_Coupon_Status | V09/F08/SP03 D09 | D19-T03; D20-T01 |
| IX10 | IX_RefundRequest_Status_RequestedAt | Hàng chờ admin D14 | D19-T03; D20-T01 |
| IX11 | IX_Ticket_OrderItem_Status | Ticket/refundable reads D11/D14 | D19-T03; D20-T01 |
| IX12 | IX_Payout_Settlement_Status | V08/SP16 D17 | D19-T03; D20-T01 |
| IX13 | IX_AuditLog_Aggregate_CreatedAt | Decision/audit D06…D18 | D19-T03; D20-T01 |
| IX14 | IX_CommissionRule_Organization_EffectiveFrom | Policy/publish D06/D07 | D19-T03; D20-T01 |
| IX15 | IX_Event_Organization_Status_StartTime | Internal Event/check-in D07/D13 | D19-T03; D20-T01 |

## 17 transaction nghiệp vụ

| Mã | Task/SP | Lỗi sau thay đổi để chứng minh rollback | Cạnh tranh/khôi phục |
|---|---|---|---|
| TX01 | D06-T02/SP01 | Sau tạo org trước membership | Hai approve cùng request |
| TX02 | D08-T02/T04/SP02 | Item cuối hết chỗ hoặc insert lỗi | Seat/quota/user ACTIVE, cross-Event expired hold |
| TX03 | D09-T03/T04/SP03 | Sau trả coupon cũ trước giữ mới | Quota1, coupon swap order |
| TX04 | D13-T01/T04/SP04 | Sau USED trước history insert | Hai scan cùng ticket |
| TX05 | D14-T01/T04/SP05 | Sau một ticket đổi pending trước bảng nối | Hai request hoặc check-in/refund |
| TX06 | D09-T01/T04/SP06 | Order insert xong, Item sau lỗi | Hai tab cùng Hold |
| TX07 | D08-T01/T04/SP07 | Trả ghế xong, trả coupon lỗi | Buyer/job release và payment race |
| TX08 | D10-T01/T04/SP08 | Payment insert xong, bước sau lỗi | Hai khởi tạo, commit trước gateway |
| TX09 | D11-T02/T04/SP09 | Vé thứN/outbox lỗi | Duplicate IPN, expiry/cancel race |
| TX10 | D14-T02/T04/SP10 | Approve status xong, Refund insert lỗi | Approve lặp/reject đua cancel |
| TX11 | D15-T02/T04/SP11 | Refund success xong, trả kho lỗi | Duplicate/query retry/crash |
| TX12 | D07-T03/SP12 | Status publish xong, audit lỗi | Publish vs layout/rule edits |
| TX13 | D16-T01/T04/SP13 | Status cancelled xong, outbox lỗi | Callback/check-in đua cancel |
| TX14 | D17-T02/T04/SP14 | Snapshot item đang thay, Item sau lỗi | Hai recalc cùng Event |
| TX15 | D17-T02/T04/SP15 | Recalc xong, confirm lỗi | Payment/refund/Item edit cùng lock |
| TX16 | D17-T03/T04/SP16 | Payout thay đổi xong, Settlement update lỗi | Hai payouts, same id replay |
| TX17 | D16-T02/T04/SP17 | Vé/request sau lỗi trong Order | Restart batch; Order trước giữ commit |

## Quyền database và kỹ thuật

| Mã | Principal/role | Task dựng/rà | Bằng chứng |
|---|---|---|---|
| R01 | tc_buyer / tc_buyer_login / tc_buyer_user | D03-T03; grant mỗi ngày; D19-T01 | Read own data; free SP09 only; paid branch reject |
| R02 | tc_manager / tc_manager_login / tc_manager_user | D03-T03; D06/D07/D09; D19-T01 | Own org CRUD/report; không approve/refund/payout |
| R03 | tc_checkin / tc_checkin_login / tc_checkin_user | D03-T03; D13; D19-T01 | SP04/V05/F07; DENY finance, không V06 |
| R04 | tc_platform_admin / tc_admin_login / tc_admin_user | D03-T03; D06…D18; D19-T01 | Admin operations; không tự fake paid từ UI |
| TECH | Auth, worker, migration tách quyền | D03-T03; D12/D15/D16; D19-T04 | Auth hẹp, worker SP07/09/11/17 và cancel path SP10, DDL riêng |

## Yêu cầu xuyên suốt và phạm vi còn lại

| Nhóm SPEC | Task | Tiêu chí kiểm chứng |
|---|---|---|
| §2–3 kiến trúc/build | D01–D03, D20–D21 | WAR trên Tomcat, Jakarta, không Spring, cấu trúc nhóm theo tầng |
| §5 diagram | D01-T01, D02-T01/T04, D03-T01, D18-T04, D21-T04 | Đủ attributes/methods/relations, chưa có diagram thì blocked |
| §6.1 OTP/session | D04–D05 | 6 chữ số/5 phút/60 giây/5 lần sai/one-use/purpose/authVersion |
| §6.2 tổ chức/membership | D06 | Approve atomic, unique membership/manager cuối |
| §6.3 event/ảnh/layout | D07 | Publish/rule, schedule, ảnh ngoài container, khóa layout |
| §6.4 Hold | D08–D09 | Một ACTIVE/user,10 phút,1–8 vé,all-or-nothing,snapshot |
| §6.5 coupon | D09/D11/D14 | Cap30%,quota,snapshot,consume không trả khi refund |
| §6.6 thanh toán | D10–D12/D15 | Verified IPN/query,idempotency,UNKNOWN,late compensation,zero |
| §6.7 tiền | D09-T04,D11-T01,D17-T01,D18-T01 | BigDecimal/decimal,rounding,paidAmount sum,history |
| §6.8 QR/check-in | D11-T03,D13 | QR riêng/chia sẻ ảnh,owner reads,one check-in/window |
| §6.9 refund | D14–D15 | Chọn vé/duyệt/retry/zero/refund pending chặn scan/trả kho |
| §6.10 hủy Event | D16 | Immediate block,batch outbox,reuse request,USED exception |
| §6.11 settlement/payout | D17 | Gross/refund/commission/net,blocker,freeze,cap payout |
| §6.12 search/report/CSV | D07-T04,D18 | Scope,date cohort/cashflow,CSV injection,tiếng Việt/VND |
| §7 trạng thái | D03 + từng ngày nghiệp vụ | Test tất cả transition hợp lệ/không hợp lệ; SQL enum domain |
| §8 transaction/restart | D03,D08–D17,D20 | Ownership,locks,EM refresh,outbox recovery |
| §9 contracts24 UI | API-MAP.md + D04–D18 | Đủ endpoint/DTO/quyền; frontend ngoài lịch |
| §10 email/storage/provider | D05,D07,D10–D12,D15,D17 | Real integration riêng adapter test; secret ngoài repo |
| §11 local/Docker/Azure | D01,D03,D19–D21 | Fresh local run,container smoke,Azure kiểm riêng,quota xác minh |
| §12 acceptance | D21 | Build/unit/IT/concurrency/security/E2E/recovery có evidence |
| §14.3 ERD/3NF | D02-T01/T04,D21-T03 | Candidate keys/FD và lý do snapshots, không chỉ PK/FK |
| §14.8 index benchmark | D19-T03,D20-T01 |15 actual plans/reads/timing before-after,write cost |
| §14.11 CRUD | D07-T01,D09-T02 | Xóa bản nháp/coupon chưa tham chiếu; không xóa lịch sử tài chính |
| §14.12–13 hồ sơ/backend evidence | D18–D21 | Runbook/SQL/scripts/source/evidence; full báo cáo/slides/UI ngoài lịch |

Các unique constraints/filtered indexes ngoài C01–C20 và ngoài15 index hiệu năng vẫn phải có từ D02: một Hold ACTIVE/user, một Order/Hold, một request mở/ticket, Settlement/Event, SettlementItem/Order, ticketCode/orderCode/txnRef và redemption/order. Không để tới ngày 20 mới bảo vệ bất biến.

Đối với TR01–TR10: tất cả phải có kiểm thử nhiều dòng; đối với SP01–SP17: tất cả có lỗi sau mutation, outer transaction rollback và đường gọi thực. Đối với IX01–IX15: lưu key/include đúng SPEC và chứng minh query không đổi kết quả. Các rule số lượng là ngưỡng của dự án, không hạ về mức tối thiểu môn học 5.
