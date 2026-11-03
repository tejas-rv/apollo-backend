package com.apollo.elevator.common.validation;

import java.util.regex.Pattern;

/**
 * Shared Indian contact/tax format rules used by DTO {@code @Pattern} constraints and services.
 * Keep these in sync with {@code apollo-frontend/src/utils/validation.js}.
 */
public final class IndianFormats {

    /** 10-digit Indian mobile number starting with 6, 7, 8 or 9. */
    public static final String MOBILE = "^[6-9]\\d{9}$";
    public static final String OPTIONAL_MOBILE = "^$|" + MOBILE;
    public static final String MOBILE_MESSAGE = "must be a valid 10-digit Indian mobile number starting with 6-9";

    /** 6-digit Indian PIN code; first digit cannot be 0. */
    public static final String PINCODE = "^[1-9]\\d{5}$";
    public static final String OPTIONAL_PINCODE = "^$|" + PINCODE;
    public static final String PINCODE_MESSAGE = "must be a valid 6-digit Indian pincode";

    public static final String GSTIN = "^\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";
    public static final String OPTIONAL_GSTIN = "^$|" + GSTIN;
    public static final String GSTIN_MESSAGE = "must be a valid 15-character GSTIN";

    public static final String PAN = "^[A-Z]{5}\\d{4}[A-Z]$";
    public static final String OPTIONAL_PAN = "^$|" + PAN;
    public static final String PAN_MESSAGE = "must be a valid 10-character PAN";

    /** Person / place names: letters, spaces and common punctuation only. */
    public static final String NAME = "^$|^[\\p{L} .'&()/,-]+$";
    public static final String NAME_MESSAGE = "may contain only letters, spaces and . ' & ( ) / , -";

    /** Stricter than the default {@code @Email}: requires a dotted domain with a 2+ letter TLD. */
    public static final String EMAIL = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    /** Optional free-text address: blank, or 5+ characters containing at least one letter or digit. */
    public static final String OPTIONAL_ADDRESS = "^$|^(?s)(?=.*[\\p{L}\\d]).{5,}$";
    public static final String ADDRESS_MESSAGE = "must be at least 5 characters";

    private static final Pattern MOBILE_PATTERN = Pattern.compile(MOBILE);

    private IndianFormats() {
    }

    /**
     * Strips spaces, dashes, brackets and an optional +91 / 91 / 0 prefix.
     * Returns the bare 10-digit number, or {@code null} if the input is not a valid Indian mobile.
     */
    public static String normalizeMobile(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return MOBILE_PATTERN.matcher(digits).matches() ? digits : null;
    }

    public static boolean isValidMobile(String raw) {
        return normalizeMobile(raw) != null;
    }
}
