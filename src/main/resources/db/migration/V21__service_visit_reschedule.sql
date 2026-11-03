-- Audit trail of AMC service visits rescheduled by an admin after an issue was raised
-- (by the customer or by an admin). A SCHEDULED visit is moved in place
-- (service_visit_id = new_service_visit_id); a COMPLETED/CANCELLED visit gets a new
-- follow-up visit (new_service_visit_id). Visit FKs are SET NULL so the audit survives.
CREATE TABLE service_visit_reschedule (
    id BIGSERIAL PRIMARY KEY,
    amc_contract_id BIGINT NOT NULL,
    service_visit_id BIGINT,
    new_service_visit_id BIGINT,
    raised_by VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    previous_status VARCHAR(20) NOT NULL,
    previous_date DATE NOT NULL,
    new_date DATE NOT NULL,
    previous_engineer_name VARCHAR(100),
    new_engineer_name VARCHAR(100),
    rescheduled_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_visit_reschedule_contract
        FOREIGN KEY (amc_contract_id) REFERENCES amc_contract (id) ON DELETE CASCADE,
    CONSTRAINT fk_visit_reschedule_visit
        FOREIGN KEY (service_visit_id) REFERENCES amc_service_history (id) ON DELETE SET NULL,
    CONSTRAINT fk_visit_reschedule_new_visit
        FOREIGN KEY (new_service_visit_id) REFERENCES amc_service_history (id) ON DELETE SET NULL
);

CREATE INDEX idx_visit_reschedule_visit ON service_visit_reschedule (service_visit_id);
CREATE INDEX idx_visit_reschedule_new_visit ON service_visit_reschedule (new_service_visit_id);
CREATE INDEX idx_visit_reschedule_contract ON service_visit_reschedule (amc_contract_id);
