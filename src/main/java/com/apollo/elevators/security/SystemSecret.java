package com.apollo.elevators.security;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Holds application-level secrets/config (e.g. JWT signing secret, token
 * expiry, issuer name) so they can be rotated without a redeploy.
 *
 * IMPORTANT: config_value is stored ENCRYPTED (AES-GCM) using the master key
 * from the APP_MASTER_KEY environment variable — see AesEncryptionUtil.
 * The master key itself must never be stored in this table or in this DB;
 * it lives only in the deployment environment (Render/Railway env var).
 */
@Entity
@Table(name = "system_secret")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSecret {

    @Id
    @Column(name = "config_key", nullable = false, updatable = false)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue; // encrypted (Base64 ciphertext)

    private String description;

    @Column(name = "updated_at")
    private Instant updatedAt;
}