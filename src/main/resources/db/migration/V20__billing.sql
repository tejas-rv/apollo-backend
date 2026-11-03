-- Fixed AMC plan prices (per lift, per year, before GST). Editable by admins.
CREATE TABLE amc_plan_price (
    contract_type VARCHAR(20)    PRIMARY KEY,
    amount        NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    updated_by    VARCHAR(150),
    updated_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO amc_plan_price (contract_type, amount, updated_by) VALUES
    ('SILVER',    8000.00, 'system'),
    ('GOLD',     12000.00, 'system'),
    ('PLATINUM', 20000.00, 'system');

-- Every generated bill is persisted so it can be tracked (sent / paid) and reported on the dashboard.
CREATE TABLE bill (
    id                 BIGSERIAL      PRIMARY KEY,
    invoice_number     VARCHAR(50)    NOT NULL,
    customer_id        BIGINT         NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    customer_name      VARCHAR(150)   NOT NULL,
    amc_contract_id    BIGINT         REFERENCES amc_contract (id) ON DELETE SET NULL,
    bill_type          VARCHAR(20)    NOT NULL,
    contract_type      VARCHAR(20),
    payment_frequency  VARCHAR(20),
    instalment_no      INTEGER,
    total_instalments  INTEGER,
    period_start       DATE,
    period_end         DATE,
    amc_period_start   DATE,
    plan_amount        NUMERIC(15, 2),
    amc_base_amount    NUMERIC(15, 2),
    invoice_date       DATE           NOT NULL,
    taxable_amount     NUMERIC(15, 2) NOT NULL,
    gst_percentage     NUMERIC(5, 2)  NOT NULL,
    gst_amount         NUMERIC(15, 2) NOT NULL,
    total_amount       NUMERIC(15, 2) NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    paid_date          DATE,
    payment_reference  VARCHAR(100),
    request_json       TEXT           NOT NULL,
    created_by         VARCHAR(150),
    updated_by         VARCHAR(150),
    created_at         TIMESTAMP      NOT NULL,
    updated_at         TIMESTAMP      NOT NULL,
    CONSTRAINT uk_bill_invoice_number UNIQUE (invoice_number)
);

CREATE INDEX idx_bill_customer ON bill (customer_id);
CREATE INDEX idx_bill_status ON bill (status);
CREATE INDEX idx_bill_invoice_date ON bill (invoice_date);
CREATE INDEX idx_bill_paid_date ON bill (paid_date);

-- One bill per AMC year (contract period) instalment; cancelled bills don't count.
CREATE UNIQUE INDEX uk_bill_amc_instalment
    ON bill (amc_contract_id, amc_period_start, instalment_no)
    WHERE amc_contract_id IS NOT NULL AND status <> 'CANCELLED';
