CREATE TABLE service_report (
    id BIGSERIAL PRIMARY KEY,
    amc_contract_id BIGINT NOT NULL,
    engineer_user_id BIGINT NOT NULL,
    engineer_name VARCHAR(100) NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    visit_date DATE NOT NULL,
    overall_notes VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    submitted_at TIMESTAMP,
    pdf_sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_report_amc_contract
        FOREIGN KEY (amc_contract_id)
        REFERENCES amc_contract (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_service_report_engineer_user
        FOREIGN KEY (engineer_user_id)
        REFERENCES app_user (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_service_report_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_service_report_engineer_user_id
    ON service_report (engineer_user_id, visit_date);

CREATE INDEX idx_service_report_amc_contract_id
    ON service_report (amc_contract_id);

CREATE TABLE service_check_item (
    id BIGSERIAL PRIMARY KEY,
    service_report_id BIGINT NOT NULL,
    item_order INTEGER NOT NULL,
    question VARCHAR(300) NOT NULL,
    answer_type VARCHAR(20) NOT NULL,
    answer_yn BOOLEAN,
    answer_text VARCHAR(1000),
    CONSTRAINT fk_service_check_item_report
        FOREIGN KEY (service_report_id)
        REFERENCES service_report (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_service_check_item_report_id
    ON service_check_item (service_report_id);

CREATE INDEX idx_service_check_item_order
    ON service_check_item (service_report_id, item_order);
