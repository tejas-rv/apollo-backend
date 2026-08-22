CREATE TABLE refresh_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
CREATE INDEX idx_refresh_token_expires_at ON refresh_token (expires_at);

CREATE TABLE notification_log (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(20) NOT NULL,
    recipient VARCHAR(20) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    reference_key VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(100),
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP
);

CREATE INDEX idx_notification_log_channel ON notification_log (channel);
CREATE INDEX idx_notification_log_status ON notification_log (status);
