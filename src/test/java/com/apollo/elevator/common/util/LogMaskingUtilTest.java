package com.apollo.elevator.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogMaskingUtilTest {

    @Test
    void masksSensitiveFields() {
        assertThat(LogMaskingUtil.mask("access_token", "secret-value")).isEqualTo("[REDACTED]");
        assertThat(LogMaskingUtil.mask("phoneNumber", "+919876543210")).isEqualTo("[REDACTED]");
        assertThat(LogMaskingUtil.mask("email", "person@example.com")).isEqualTo("[REDACTED]");
        assertThat(LogMaskingUtil.mask("apiKey", "provider-key")).isEqualTo("[REDACTED]");
        assertThat(LogMaskingUtil.mask("errorMessage", "possibly sensitive text")).isEqualTo("[REDACTED]");
    }

    @Test
    void preservesNonSensitiveValuesAndNulls() {
        assertThat(LogMaskingUtil.mask("customerId", 42L)).isEqualTo("42");
        assertThat(LogMaskingUtil.mask("role", "ADMIN")).isEqualTo("ADMIN");
        assertThat(LogMaskingUtil.mask("password", null)).isNull();
    }
}
