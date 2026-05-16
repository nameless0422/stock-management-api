ALTER TABLE payments
    ADD COLUMN virtual_account_bank VARCHAR(20) NULL,
    ADD COLUMN virtual_account_number VARCHAR(30) NULL,
    ADD COLUMN virtual_account_due_date DATETIME(6) NULL;
