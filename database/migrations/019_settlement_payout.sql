SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_SettlementPayoutBalance
AS
WITH payouts AS (
    SELECT settlement_id,
           SUM(CASE WHEN status = ''SUCCEEDED'' THEN amount ELSE 0 END) paid_amount,
           SUM(CASE WHEN status = ''PENDING'' THEN amount ELSE 0 END) pending_amount
    FROM dbo.tc_payouts
    GROUP BY settlement_id
)
SELECT s.id settlement_id, s.event_id, s.gross_revenue, s.total_refund, s.total_commission,
       s.net_payable, s.status, s.confirmed_at,
       COALESCE(p.paid_amount, 0) paid_amount,
       COALESCE(p.pending_amount, 0) pending_amount,
       CASE WHEN s.net_payable > COALESCE(p.paid_amount, 0)
            THEN s.net_payable - COALESCE(p.paid_amount, 0) ELSE 0 END remaining_amount,
       CASE WHEN s.status = ''CONFIRMED''
                 AND s.net_payable > COALESCE(p.paid_amount, 0) + COALESCE(p.pending_amount, 0)
            THEN s.net_payable - COALESCE(p.paid_amount, 0) - COALESCE(p.pending_amount, 0)
            ELSE 0 END available_amount
FROM dbo.tc_settlements s
LEFT JOIN payouts p ON p.settlement_id = s.id;
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_RecordPayout
    @settlement_id uniqueidentifier,
    @payout_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @amount decimal(19,0),
    @reference varchar(100),
    @verified_result varchar(20)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp16;
    BEGIN TRY
        IF @settlement_id IS NULL OR @payout_id IS NULL OR @actor_id IS NULL
           OR @amount IS NULL OR @amount <= 0 OR @reference IS NULL OR LEN(TRIM(@reference)) = 0
           OR @verified_result NOT IN (''PENDING'', ''SUCCEEDED'', ''FAILED'')
            THROW 51730, ''Invalid payout input'', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = ''ADMIN'')
            THROW 51731, ''Administrator role required'', 1;

        DECLARE @event_id uniqueidentifier, @settlement_status varchar(20), @net decimal(19,0),
                @current_status varchar(20), @current_settlement uniqueidentifier,
                @current_amount decimal(19,0), @current_reference varchar(100),
                @paid decimal(19,0), @pending decimal(19,0), @lock_count bigint;
        SELECT @event_id = event_id FROM dbo.tc_settlements WHERE id = @settlement_id;
        IF @event_id IS NULL THROW 51732, ''Settlement not found'', 1;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @settlement_status = status, @net = net_payable
        FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @settlement_id AND event_id = @event_id;
        SELECT @current_settlement = settlement_id, @current_status = status,
               @current_amount = amount, @current_reference = reference
        FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @payout_id;

        IF @current_status IS NOT NULL BEGIN
            IF @current_settlement <> @settlement_id OR @current_amount <> @amount OR @current_reference <> @reference
                THROW 51733, ''Payout replay payload mismatch'', 1;
            IF @current_status = ''SUCCEEDED'' BEGIN
                IF @verified_result <> ''SUCCEEDED'' THROW 51734, ''Successful payout is immutable'', 1;
                IF @started = 1 COMMIT TRANSACTION;
                SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
                RETURN;
            END;
            IF @current_status = ''FAILED'' THROW 51735, ''Failed payout requires a new payout id'', 1;
            UPDATE dbo.tc_payouts
            SET status = @verified_result,
                paid_at = CASE WHEN @verified_result = ''SUCCEEDED'' THEN SYSUTCDATETIME() ELSE NULL END,
                version = version + 1
            WHERE id = @payout_id AND status = ''PENDING'';
        END ELSE BEGIN
            IF @settlement_status <> ''CONFIRMED''
                THROW 51736, ''Only a confirmed settlement accepts a new payout'', 1;
            SELECT @paid = COALESCE(SUM(CASE WHEN status = ''SUCCEEDED'' THEN amount ELSE 0 END), 0),
                   @pending = COALESCE(SUM(CASE WHEN status = ''PENDING'' THEN amount ELSE 0 END), 0)
            FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK)
            WHERE settlement_id = @settlement_id;
            IF @paid + @pending + @amount > @net
                THROW 51737, ''Payout exceeds available settlement balance'', 1;
            INSERT dbo.tc_payouts(id, settlement_id, amount, reference, status, paid_at)
            VALUES (@payout_id, @settlement_id, @amount, @reference, @verified_result,
                    CASE WHEN @verified_result = ''SUCCEEDED'' THEN SYSUTCDATETIME() END);
        END;

        SELECT @paid = COALESCE(SUM(amount), 0)
        FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK)
        WHERE settlement_id = @settlement_id AND status = ''SUCCEEDED'';
        IF @paid = @net
            UPDATE dbo.tc_settlements
            SET status = ''PAID'', version = version + 1
            WHERE id = @settlement_id AND status = ''CONFIRMED'';
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, CONCAT(''PAYOUT_'', @verified_result), ''PAYOUT'', @payout_id, @reference);

        IF @started = 1 COMMIT TRANSACTION;
        SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp16;
        THROW;
    END CATCH
END;
');

GRANT SELECT ON dbo.vw_SettlementPayoutBalance TO tc_manager;
GRANT SELECT ON dbo.vw_SettlementPayoutBalance TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_RecordPayout TO tc_platform_admin;
