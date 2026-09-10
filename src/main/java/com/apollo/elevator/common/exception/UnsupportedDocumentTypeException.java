package com.apollo.elevator.common.exception;

public class UnsupportedDocumentTypeException extends RuntimeException {

    private final String errorCode;

    public UnsupportedDocumentTypeException(String message) {
        this("UNSUPPORTED_DOCUMENT_TYPE", message);
    }

    public UnsupportedDocumentTypeException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
