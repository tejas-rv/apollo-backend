CREATE TABLE work_order (
    id BIGSERIAL PRIMARY KEY,
    work_order_number VARCHAR(32) NOT NULL,
    enquiry_id BIGINT NOT NULL,
    quotation_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,
    created_by VARCHAR(150),
    updated_by VARCHAR(150),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_work_order_enquiry FOREIGN KEY (enquiry_id) REFERENCES contact_inquiry(id),
    CONSTRAINT fk_work_order_quotation FOREIGN KEY (quotation_id) REFERENCES quotation(id)
);

CREATE UNIQUE INDEX uk_work_order_number ON work_order (work_order_number);
CREATE UNIQUE INDEX uk_work_order_enquiry ON work_order (enquiry_id);
CREATE INDEX idx_work_order_quotation_id ON work_order (quotation_id);
CREATE INDEX idx_work_order_status ON work_order (status);
CREATE INDEX idx_work_order_created_at ON work_order (created_at);
