SET NOCOUNT ON;
SET XACT_ABORT ON;

GRANT SELECT ON dbo.vw_PublicEvents TO tc_buyer;
GRANT SELECT ON dbo.vw_ZoneInventory TO tc_buyer;
GRANT SELECT ON dbo.vw_TicketDetails TO tc_buyer;
GRANT SELECT ON dbo.vw_RefundRequestOverview TO tc_buyer;
GRANT SELECT ON dbo.vw_CouponUsage TO tc_buyer;
GRANT SELECT ON dbo.vw_OrganizationMembers TO tc_buyer;
GRANT SELECT ON OBJECT::dbo.fn_GetZoneAvailability TO tc_buyer;
GRANT SELECT ON OBJECT::dbo.fn_GetRefundableTickets TO tc_buyer;
GRANT SELECT ON OBJECT::dbo.fn_GetCouponEligibility TO tc_buyer;

GRANT SELECT ON dbo.vw_PublicEvents TO tc_manager;
GRANT SELECT ON dbo.vw_ZoneInventory TO tc_manager;
GRANT SELECT ON dbo.vw_EventSalesReport TO tc_manager;
GRANT SELECT ON dbo.vw_CheckInHistory TO tc_manager;
GRANT SELECT ON dbo.vw_TicketDetails TO tc_manager;
GRANT SELECT ON dbo.vw_RefundRequestOverview TO tc_manager;
GRANT SELECT ON dbo.vw_SettlementPayoutBalance TO tc_manager;
GRANT SELECT ON dbo.vw_CouponUsage TO tc_manager;
GRANT SELECT ON dbo.vw_OrganizationMembers TO tc_manager;
GRANT SELECT ON OBJECT::dbo.fn_GetEventCheckInWindow TO tc_manager;
GRANT SELECT ON OBJECT::dbo.fn_GetOrganizationRevenue TO tc_manager;
GRANT SELECT ON OBJECT::dbo.fn_GetOrganizationCashFlow TO tc_manager;

GRANT SELECT ON dbo.vw_CheckInHistory TO tc_checkin;
GRANT SELECT ON OBJECT::dbo.fn_GetEventCheckInWindow TO tc_checkin;
DENY SELECT ON dbo.vw_OrderFinancialSummary TO tc_checkin;
DENY SELECT ON dbo.vw_EventSalesReport TO tc_checkin;
DENY SELECT ON dbo.vw_RefundRequestOverview TO tc_checkin;
DENY SELECT ON dbo.vw_SettlementPayoutBalance TO tc_checkin;
DENY SELECT ON OBJECT::dbo.fn_GetOrganizationRevenue TO tc_checkin;
DENY SELECT ON OBJECT::dbo.fn_GetOrganizationCashFlow TO tc_checkin;

GRANT SELECT ON dbo.vw_PublicEvents TO tc_platform_admin;
GRANT SELECT ON dbo.vw_ZoneInventory TO tc_platform_admin;
GRANT SELECT ON dbo.vw_OrderFinancialSummary TO tc_platform_admin;
GRANT SELECT ON dbo.vw_EventSalesReport TO tc_platform_admin;
GRANT SELECT ON dbo.vw_CheckInHistory TO tc_platform_admin;
GRANT SELECT ON dbo.vw_TicketDetails TO tc_platform_admin;
GRANT SELECT ON dbo.vw_RefundRequestOverview TO tc_platform_admin;
GRANT SELECT ON dbo.vw_SettlementPayoutBalance TO tc_platform_admin;
GRANT SELECT ON dbo.vw_CouponUsage TO tc_platform_admin;
GRANT SELECT ON dbo.vw_OrganizationMembers TO tc_platform_admin;
GRANT SELECT ON OBJECT::dbo.fn_GetSettlementBlockers TO tc_platform_admin;
GRANT SELECT ON OBJECT::dbo.fn_GetOrganizationRevenue TO tc_platform_admin;
GRANT SELECT ON OBJECT::dbo.fn_GetOrganizationCashFlow TO tc_platform_admin;

GRANT EXECUTE ON dbo.usp_CreateTicketHold TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ApplyOrderCoupon TO tc_buyer;
GRANT EXECUTE ON dbo.usp_CreateRefundRequest TO tc_buyer;
GRANT EXECUTE ON dbo.usp_CreateOrderFromHold TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ReleaseTicketHold TO tc_buyer;
GRANT EXECUTE ON dbo.usp_BeginOrderPayment TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ApplyPaymentResult TO tc_buyer;

GRANT EXECUTE ON dbo.usp_CheckInTicket TO tc_manager;
GRANT EXECUTE ON dbo.usp_CheckInTicket TO tc_checkin;

GRANT EXECUTE ON dbo.usp_ApproveOrganizationRequest TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_ReviewRefundRequest TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_PublishEvent TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_CancelEvent TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_CalculateEventSettlement TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_ConfirmEventSettlement TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_ExecutePayout TO tc_platform_admin;

GRANT EXECUTE ON dbo.usp_ReleaseTicketHold TO tc_worker;
GRANT EXECUTE ON dbo.usp_ApplyPaymentResult TO tc_worker;
GRANT EXECUTE ON dbo.usp_ApplyRefundResult TO tc_worker;
GRANT EXECUTE ON dbo.usp_ReviewRefundRequest TO tc_worker;
GRANT EXECUTE ON dbo.usp_ProcessCancelledOrder TO tc_worker;
