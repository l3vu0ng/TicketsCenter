IF OBJECT_ID(N'dbo.tc_coupons', N'U') IS NOT NULL
BEGIN
    IF OBJECT_ID(N'dbo.CK_Coupon_Discount_Limit', N'C') IS NOT NULL
        ALTER TABLE dbo.tc_coupons DROP CONSTRAINT CK_Coupon_Discount_Limit;

    ALTER TABLE dbo.tc_coupons ADD CONSTRAINT CK_Coupon_Discount_Limit CHECK (
        max_uses > 0 AND valid_from < valid_to AND
        (max_discount_amount IS NULL OR max_discount_amount >= 0) AND
        ((discount_type = 'PERCENTAGE' AND percentage_value IS NOT NULL
            AND percentage_value > 0 AND percentage_value <= 30 AND fixed_amount IS NULL)
         OR (discount_type = 'FIXED_AMOUNT' AND fixed_amount IS NOT NULL
            AND fixed_amount > 0 AND percentage_value IS NULL))
    );
END;
