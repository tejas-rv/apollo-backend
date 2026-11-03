-- HR / contact profile for ENGINEER accounts (1:1 with app_user).
CREATE TABLE engineer_profile (
    user_id BIGINT PRIMARY KEY,
    employee_number VARCHAR(30),
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(150),
    address VARCHAR(500),
    designation VARCHAR(100),
    date_of_joining DATE,
    specialization VARCHAR(200),
    emergency_contact_name VARCHAR(100),
    emergency_contact_phone VARCHAR(20),
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_engineer_profile_user
        FOREIGN KEY (user_id)
        REFERENCES app_user (id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_engineer_profile_employee_number
    ON engineer_profile (LOWER(employee_number));

-- Scheduled AMC visits are matched to engineers by name.
CREATE INDEX idx_amc_service_history_engineer_status
    ON amc_service_history (LOWER(TRIM(engineer_name)), status);

CREATE INDEX idx_service_report_engineer_status
    ON service_report (engineer_user_id, status);
