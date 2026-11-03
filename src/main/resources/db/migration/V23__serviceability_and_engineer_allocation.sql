-- V22 is reserved for the customer-code Java migration.
CREATE TABLE serviceability_zone (
    zone_id VARCHAR(30) PRIMARY KEY,
    zone_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO serviceability_zone (zone_id, zone_name, description) VALUES
    ('BLR-EAST', 'Bengaluru East', 'Apollo service territory for eastern Bengaluru'),
    ('BLR-SOUTH', 'Bengaluru South', 'Apollo service territory for southern Bengaluru'),
    ('BLR-WEST', 'Bengaluru West', 'Apollo service territory for western Bengaluru'),
    ('BLR-NORTH', 'Bengaluru North', 'Apollo service territory for northern Bengaluru');

CREATE TABLE pincode_serviceability (
    id BIGSERIAL PRIMARY KEY,
    pincode VARCHAR(6) NOT NULL CHECK (pincode ~ '^[0-9]{6}$'),
    locality VARCHAR(120) NOT NULL,
    district VARCHAR(100),
    state VARCHAR(100),
    postal_division VARCHAR(120),
    zone_id VARCHAR(30) REFERENCES serviceability_zone (zone_id) ON DELETE SET NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to DATE,
    source VARCHAR(200) NOT NULL,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED'
        CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED', 'AMBIGUOUS', 'REJECTED')),
    last_verified_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_pincode_serviceability_lookup
    ON pincode_serviceability (pincode, active, effective_from, effective_to);
CREATE INDEX idx_pincode_serviceability_locality
    ON pincode_serviceability (LOWER(locality));
CREATE INDEX idx_pincode_serviceability_zone
    ON pincode_serviceability (zone_id, active);
CREATE UNIQUE INDEX uk_pincode_serviceability_active_locality
    ON pincode_serviceability (pincode, LOWER(locality))
    WHERE active = TRUE;

CREATE TABLE pincode_serviceability_audit (
    id BIGSERIAL PRIMARY KEY,
    pincode_record_id BIGINT NOT NULL
        REFERENCES pincode_serviceability (id) ON DELETE RESTRICT,
    changed_by VARCHAR(100) NOT NULL,
    change_summary VARCHAR(1000) NOT NULL,
    previous_value TEXT,
    new_value TEXT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pincode_serviceability_audit_record
    ON pincode_serviceability_audit (pincode_record_id, changed_at DESC);

CREATE TABLE engineer_serviceability (
    engineer_id BIGINT PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    daily_capacity INTEGER NOT NULL DEFAULT 5 CHECK (daily_capacity > 0),
    available BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE engineer_zone_assignment (
    id BIGSERIAL PRIMARY KEY,
    engineer_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    zone_id VARCHAR(30) NOT NULL REFERENCES serviceability_zone (zone_id) ON DELETE RESTRICT,
    assignment_type VARCHAR(20) NOT NULL CHECK (assignment_type IN ('PRIMARY', 'BACKUP')),
    effective_from DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE UNIQUE INDEX uk_engineer_zone_assignment_active
    ON engineer_zone_assignment (engineer_id, zone_id, assignment_type)
    WHERE active = TRUE;
CREATE UNIQUE INDEX uk_engineer_primary_zone_active
    ON engineer_zone_assignment (engineer_id)
    WHERE active = TRUE AND assignment_type = 'PRIMARY';
CREATE INDEX idx_engineer_zone_assignment_zone
    ON engineer_zone_assignment (zone_id, assignment_type, active);

CREATE TABLE service_assignment (
    id BIGSERIAL PRIMARY KEY,
    service_visit_id BIGINT NOT NULL REFERENCES amc_service_history (id) ON DELETE CASCADE,
    engineer_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    zone_id VARCHAR(30) NOT NULL REFERENCES serviceability_zone (zone_id) ON DELETE RESTRICT,
    assignment_date DATE NOT NULL DEFAULT CURRENT_DATE,
    service_due_date DATE NOT NULL,
    scheduled_date DATE NOT NULL,
    priority VARCHAR(20) NOT NULL CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'NORMAL', 'LOW')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'REASSIGNED', 'COMPLETED', 'CANCELLED')),
    assigned_by VARCHAR(100) NOT NULL,
    assignment_method VARCHAR(20) NOT NULL CHECK (assignment_method IN ('AUTOMATIC', 'MANUAL')),
    assignment_remarks VARCHAR(500),
    visit_sequence INTEGER NOT NULL DEFAULT 0 CHECK (visit_sequence >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_service_assignment_one_active_per_visit
    ON service_assignment (service_visit_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_service_assignment_plan
    ON service_assignment (scheduled_date, engineer_id, status, visit_sequence);
CREATE INDEX idx_service_assignment_zone
    ON service_assignment (zone_id, scheduled_date, status);
