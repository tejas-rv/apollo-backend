ALTER TABLE lift
    ADD COLUMN IF NOT EXISTS manufactured_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS year_of_manufacture INTEGER,
    ADD COLUMN IF NOT EXISTS no_of_grooves INTEGER,
    ADD COLUMN IF NOT EXISTS friction_sheave_diameter INTEGER,
    ADD COLUMN IF NOT EXISTS no_of_ropes INTEGER,
    ADD COLUMN IF NOT EXISTS dia_of_the_rope_mm INTEGER,
    ADD COLUMN IF NOT EXISTS length_of_the_rope_mm INTEGER,
    ADD COLUMN IF NOT EXISTS is_deflector_pulley BOOLEAN,
    ADD COLUMN IF NOT EXISTS deflector_pulley_diameter INTEGER,
    ADD COLUMN IF NOT EXISTS deflector_pulley_no_of_grooves INTEGER,
    ADD COLUMN IF NOT EXISTS roping VARCHAR(50),
    ADD COLUMN IF NOT EXISTS main_motor_kw VARCHAR(50),
    ADD COLUMN IF NOT EXISTS main_motor_amps VARCHAR(50),
    ADD COLUMN IF NOT EXISTS main_motor_speed VARCHAR(50),
    ADD COLUMN IF NOT EXISTS main_motor_voltage VARCHAR(50),
    ADD COLUMN IF NOT EXISTS main_motor_frequency VARCHAR(50),
    ADD COLUMN IF NOT EXISTS main_motor_no_of_poles INTEGER,
    ADD COLUMN IF NOT EXISTS battery_make VARCHAR(100),
    ADD COLUMN IF NOT EXISTS battery_voltage VARCHAR(50),
    ADD COLUMN IF NOT EXISTS battery_no_of_batteries INTEGER;

CREATE TABLE client_representative (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    name VARCHAR(100),
    mobile_number VARCHAR(20),
    CONSTRAINT fk_client_representative_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_client_representative_customer_id ON client_representative (customer_id);
