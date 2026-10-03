SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @expected TABLE(object_type varchar(2), object_name sysname PRIMARY KEY);
INSERT @expected(object_type,object_name) VALUES
('V','vw_PublicEvents'),('V','vw_ZoneInventory'),('V','vw_OrderFinancialSummary'),('V','vw_EventSalesReport'),
('V','vw_CheckInHistory'),('V','vw_TicketDetails'),('V','vw_RefundRequestOverview'),('V','vw_SettlementPayoutBalance'),
('V','vw_CouponUsage'),('V','vw_OrganizationMembers'),
('P','usp_ApproveOrganizationRequest'),('P','usp_CreateTicketHold'),('P','usp_ApplyOrderCoupon'),
('P','usp_CheckInTicket'),('P','usp_CreateRefundRequest'),('P','usp_CreateOrderFromHold'),
('P','usp_ReleaseTicketHold'),('P','usp_BeginOrderPayment'),('P','usp_ApplyPaymentResult'),
('P','usp_DecideRefundRequest'),('P','usp_ApplyRefundResult'),('P','usp_PublishEvent'),
('P','usp_CancelEvent'),('P','usp_RecalculateSettlement'),('P','usp_ConfirmSettlement'),
('P','usp_RecordPayout'),('P','usp_ProcessCancelledOrder'),
('FN','fn_CalculateCouponDiscount'),('FN','fn_CalculateCommission'),
('IF','fn_GetZoneAvailability'),('IF','fn_GetRefundableTickets'),('IF','fn_GetOrganizationRevenue'),
('IF','fn_AllocateTicketPaidAmounts'),('IF','fn_GetEventCheckInWindow'),('IF','fn_GetCouponEligibility'),
('IF','fn_GetSettlementBlockers'),('IF','fn_GetOrganizationCashFlow'),
('TR','trg_Zone_ProtectPublishedLayout'),('TR','trg_Seat_ProtectPublishedLayout'),
('TR','trg_CommissionRule_ProtectAppliedTerms'),('TR','trg_SettlementItem_ProtectConfirmedAmounts'),
('TR','trg_Event_AuditStatusChange'),('TR','trg_Membership_ProtectLastManager'),
('TR','trg_Coupon_ProtectUsageLimit'),('TR','trg_Ticket_ProtectIssuedSnapshot'),
('TR','trg_Settlement_ProtectConfirmedSnapshot'),('TR','trg_AuditLog_AppendOnly');

IF EXISTS (
    SELECT 1 FROM @expected e
    WHERE NOT EXISTS (
        SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
        WHERE s.name='dbo'
          AND o.name COLLATE DATABASE_DEFAULT=e.object_name COLLATE DATABASE_DEFAULT
          AND o.type COLLATE DATABASE_DEFAULT=e.object_type COLLATE DATABASE_DEFAULT
    )
) THROW 52120, 'Required SQL inventory contains a missing exact object name', 1;

IF (SELECT COUNT(*) FROM @expected)<>47
    THROW 52121, 'SQL inventory list is incomplete', 1;
