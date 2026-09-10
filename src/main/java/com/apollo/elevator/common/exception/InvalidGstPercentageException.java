package com.apollo.elevator.common.exception;

public class InvalidGstPercentageException extends RuntimeException {

    private final String errorCode;

    public InvalidGstPercentageException(String message) {
        this("INVALID_GST_PERCENTAGE", message);
    }

    public InvalidGstPercentageException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
