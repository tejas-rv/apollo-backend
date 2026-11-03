package com.apollo.elevator.common.util;

import java.util.Locale;
import java.util.Set;

public final class LogMaskingUtil {

    private static final String MASKED_VALUE = "[REDACTED]";
    private static final Set<String> SENSITIVE_FIELD_MARKERS = Set.of(
            "password",
            "token",
            "secret",
            "authorization",
            "credential",
            "apikey",
            "accesskey",
            "privatekey",
            "username",
            "email",
            "phone",
            "mobile",
            "whatsapp",
            "recipient",
            "address",
            "name",
            "serialnumber",
            "employeenumber",
            "message",
            "body",
            "caption",
            "subject",
            "query",
            "filename",
            "response",
            "reason",
            "error"
    );

    private LogMaskingUtil() {}

    public static String mask(String fieldName, Object value) {
        if (value == null) {
            return null;
        }

        String normalizedFieldName = fieldName == null
                ? ""
                : fieldName.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (SENSITIVE_FIELD_MARKERS.stream().anyMatch(normalizedFieldName::contains)) {
            return MASKED_VALUE;
        }
        return String.valueOf(value);
    }
}
