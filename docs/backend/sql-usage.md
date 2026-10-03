# Bản đồ sử dụng SQL

Mỗi dòng ghi object chuẩn, migration sở hữu, caller thực tế, đường chạy và kiểm chứng gần nhất. `SQL-only` nghĩa là object được gọi bởi View/SP đã có caller ứng dụng.

| Mã | Object | Migration | Caller | Endpoint/job | Test |
|---|---|---|---|---|---|
| V01 | `vw_PublicEvents` | 007 | `EventRepository` | `GET /events` | `Day07IT` |
| V02 | `vw_ZoneInventory` | 007 | `EventRepository` | `GET /events/{id}` | `day-07.sql` |
| V03 | `vw_OrderFinancialSummary` | 017 | SQL-only: V04/SP14/F05 | report/settlement | `day-17.sql`, `day-18.sql` |
| V04 | `vw_EventSalesReport` | 020 | `ReportRepository`, `OrganizationRepository` | reports/overview/CSV | `day-18.sql` |
| V05 | `vw_CheckInHistory` | 007 | Chưa có caller Java | UI-16/UI-17 còn thiếu | open: no scoped endpoint |
| V06 | `vw_TicketDetails` | 007 | `FulfillmentRepository` | `GET /me/tickets` | `Day11IT` |
| V07 | `vw_RefundRequestOverview` | 007 | Chưa có caller Java | UI-08/UI-21 còn thiếu | gap D19 |
| V08 | `vw_SettlementPayoutBalance` | 019 | `SettlementRepository` | settlement/payout | `day-17.sql` |
| V09 | `vw_CouponUsage` | 007 | SQL-only: F08 | coupon preview/apply | `day-09.sql` |
| V10 | `vw_OrganizationMembers` | 007 | `OrganizationRepository` | members/profile | `Day06IT` |
| SP01 | `usp_ApproveOrganizationRequest` | 009 | `OrganizationRepository` | approve organization | `day-06.sql` |
| SP02 | `usp_CreateTicketHold` | 009 | `HoldRepository` | `POST /holds` | `day-08.sql` |
| SP03 | `usp_ApplyOrderCoupon` | 009 | `CouponRepository` | order coupon | `day-09.sql` |
| SP04 | `usp_CheckInTicket` | 009 | `FulfillmentRepository` | check-in | `business-sql-objects.sql` |
| SP05 | `usp_CreateRefundRequest` | 009 | `RefundRepository` | create refund request | `day-15.sql` |
| SP06 | `usp_CreateOrderFromHold` | 009 | `OrderRepository` | `POST /orders` | `day-09.sql` |
| SP07 | `usp_ReleaseTicketHold` | 009 | `HoldRepository`, jobs, SP17 | cancel/expiry | `day-08.sql`, `day-16.sql` |
| SP08 | `usp_BeginOrderPayment` | — | Không tồn tại trong schema | legacy `/vnpayajax` đã retired; cần thiết kế use case mới | open: missing SQL object |
| SP09 | `usp_ApplyPaymentResult` | 010 | `PaymentService`, `FulfillmentRepository` | IPN/payment worker | `day-11.sql` |
| SP10 | `usp_DecideRefundRequest` | 021 | `RefundRepository`, `OperationsRepository` | refund decision/retry | `RefundServiceTest` |
| SP11 | `usp_ApplyRefundResult` | 016 | `RefundRepository` | `RefundJob` | `day-15.sql` |
| SP12 | `usp_PublishEvent` | 009 | `EventRepository` | publish event | `day-07.sql` |
| SP13 | `usp_CancelEvent` | 015 | `EventCancellationRepository` | cancel event | `day-16.sql` |
| SP14 | `usp_RecalculateSettlement` | 018 | `SettlementRepository` | recalculate settlement | `day-17.sql` |
| SP15 | `usp_ConfirmSettlement` | 018 | `SettlementRepository` | confirm settlement | `day-17.sql` |
| SP16 | `usp_RecordPayout` | 019 | `SettlementRepository` | payout | `day-17.sql` |
| SP17 | `usp_ProcessCancelledOrder` | 015 | `EventCancellationRepository` | cancellation worker | `day-16.sql` |
| F01 | `fn_CalculateCouponDiscount` | 007 | SQL-only: F08/SP03 | coupon | `day-09.sql` |
| F02 | `fn_CalculateCommission` | 017 | SQL-only: V04/SP14 | report/settlement | `day-17.sql`, `day-18.sql` |
| F03 | `fn_GetZoneAvailability` | 007 | `EventRepository` | `GET /events/{id}/zones` | Day19 regression |
| F04 | `fn_GetRefundableTickets` | 007 | `RefundRepository` | refund selection | `day-15.sql` |
| F05 | `fn_GetOrganizationRevenue` | 020 | `ReportRepository` | JSON/CSV reports | `day-18.sql` |
| F06 | `fn_AllocateTicketPaidAmounts` | 007 | SQL-only: SP09 | issue tickets | `day-11.sql` |
| F07 | `fn_GetEventCheckInWindow` | 007 | SQL-only: SP04 | check-in | `business-sql-objects.sql` |
| F08 | `fn_GetCouponEligibility` | 007 | SQL-only: SP03 | coupon | `day-09.sql` |
| F09 | `fn_GetSettlementBlockers` | 017 | `SettlementRepository`, SP15 | settlement blockers | `day-17.sql` |
| F10 | `fn_GetOrganizationCashFlow` | 020 | `ReportRepository` | JSON/CSV reports | `day-18.sql` |
| TR01 | `trg_Zone_ProtectPublishedLayout` | 008 | SQL Server lifecycle | Event write | `day-07.sql` |
| TR02 | `trg_Seat_ProtectPublishedLayout` | 008 | SQL Server lifecycle | Event write | `day-07.sql` |
| TR03 | `trg_CommissionRule_ProtectAppliedTerms` | 008 | SQL Server lifecycle | commission rule write | `day-07.sql` |
| TR04 | `trg_SettlementItem_ProtectConfirmedAmounts` | 018 | SQL Server lifecycle | settlement | `day-17.sql` |
| TR05 | `trg_Event_AuditStatusChange` | 008 | SQL Server lifecycle | Event status write | `day-07.sql` |
| TR06 | `trg_Membership_ProtectLastManager` | 008 | SQL Server lifecycle | membership write | `day-06.sql` |
| TR07 | `trg_Coupon_ProtectUsageLimit` | 008 | SQL Server lifecycle | coupon write | `day-09.sql` |
| TR08 | `trg_Ticket_ProtectIssuedSnapshot` | 008 | SQL Server lifecycle | ticket/refund | `day-11.sql`, `day-15.sql` |
| TR09 | `trg_Settlement_ProtectConfirmedSnapshot` | 018 | SQL Server lifecycle | settlement | `day-17.sql` |
| TR10 | `trg_AuditLog_AppendOnly` | 021 | SQL Server lifecycle | every audit writer | `day-18.sql` |

## Lỗ hổng chuyển sang ngày 19

- V05 và V07 chưa có scoped Repository/endpoint cho UI-16/UI-21; không thêm endpoint chỉ để lấp inventory.
- SP08 không có trong schema dù kế hoạch cũ ghi migration 010. `/vnpayajax` nhận tiền/URL từ client đã retired; cần một SP khởi tạo payment và contract mới trước khi mở lại thanh toán.
