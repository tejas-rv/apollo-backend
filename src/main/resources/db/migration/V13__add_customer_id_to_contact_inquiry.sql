ALTER TABLE contact_inquiry ADD COLUMN customer_id BIGINT;
CREATE INDEX idx_contact_inquiry_customer_id ON contact_inquiry (customer_id);
