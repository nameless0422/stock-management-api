ALTER TABLE payments
    ADD COLUMN card_company VARCHAR(30) NULL,
    ADD COLUMN card_number VARCHAR(30) NULL,
    ADD COLUMN installment_plan_months INT NULL;
