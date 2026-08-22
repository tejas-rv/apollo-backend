CREATE TABLE lift (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    lift_type VARCHAR(50),
    drive_type VARCHAR(50),
    number_of_floors INTEGER,
    capacity_in_kg INTEGER,
    capacity_in_persons INTEGER,
    brand VARCHAR(100),
    lift_model VARCHAR(100),
    installation_type VARCHAR(100),
    year_of_installation INTEGER,
    serial_number VARCHAR(100),
    CONSTRAINT fk_lift_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_lift_customer_id ON lift (customer_id);

CREATE TABLE amc_contract (
    id BIGSERIAL PRIMARY KEY,
    lift_id BIGINT NOT NULL,
    contract_number VARCHAR(50),
    status VARCHAR(20),
    start_date DATE,
    end_date DATE,
    amc_type VARCHAR(50),
    amc_amount NUMERIC(12,2),
    payment_frequency VARCHAR(20),
    next_payment_date DATE,
    next_service_date DATE,
    total_services INTEGER,
    completed_services INTEGER,
    terms_and_conditions VARCHAR(500),
    remarks VARCHAR(500),
    CONSTRAINT fk_amc_contract_lift
        FOREIGN KEY (lift_id)
        REFERENCES lift (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_amc_contract_lift_id ON amc_contract (lift_id);
