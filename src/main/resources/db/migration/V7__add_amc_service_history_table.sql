CREATE TABLE amc_service_history (
    id BIGSERIAL PRIMARY KEY,
    amc_contract_id BIGINT NOT NULL,
    service_date DATE NOT NULL,
    engineer_name VARCHAR(100) NOT NULL,
    work_done VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_amc_service_history_contract
        FOREIGN KEY (amc_contract_id)
        REFERENCES amc_contract (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_amc_service_history_contract_id
    ON amc_service_history (amc_contract_id);
