-- Users (admin / engineer / customer logins)
CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,     -- BCrypt hash only, never plaintext
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'ENGINEER', 'CUSTOMER')),
    email VARCHAR(255),
    whatsapp VARCHAR(20),
    enabled BOOLEAN NOT NULL DEFAULT true
);

-- App-level secrets/config (JWT signing key, expiry, issuer, etc.)
-- config_value is ALWAYS encrypted (AES-256-GCM) by the application before
-- being written here — never insert plaintext secrets manually.
CREATE TABLE system_secret (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value TEXT NOT NULL,
    description VARCHAR(255),
    updated_at TIMESTAMP
);
