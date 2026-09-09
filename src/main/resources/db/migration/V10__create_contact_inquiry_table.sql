CREATE TABLE contact_inquiry (
                          id BIGSERIAL PRIMARY KEY,

                          inquiry_type VARCHAR(20) NOT NULL,

                          full_name VARCHAR(150) NOT NULL,

                          phone_number VARCHAR(20) NOT NULL,

                          email VARCHAR(150),

                          city VARCHAR(100),

                          requirement_type VARCHAR(100),

                          message VARCHAR(2000),

                          consent_accepted BOOLEAN NOT NULL DEFAULT FALSE,

                          source_page VARCHAR(100),

                          status VARCHAR(20) NOT NULL DEFAULT 'NEW',

                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_contact_inquiry_status ON contact_inquiry (status);

CREATE INDEX idx_contact_inquiry_created_at ON contact_inquiry (created_at);
