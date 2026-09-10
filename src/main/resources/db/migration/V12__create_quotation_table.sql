CREATE TABLE quotation (
    id BIGSERIAL PRIMARY KEY,
    quotation_number VARCHAR(32) NOT NULL,
    enquiry_id BIGINT NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(150),
    updated_by VARCHAR(150),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_quotation_enquiry
        FOREIGN KEY (enquiry_id) REFERENCES contact_inquiry(id)
);

CREATE UNIQUE INDEX uk_quotation_number ON quotation (quotation_number);
CREATE INDEX idx_quotation_enquiry_id ON quotation (enquiry_id);
CREATE INDEX idx_quotation_status ON quotation (status);
CREATE INDEX idx_quotation_created_at ON quotation (created_at);
