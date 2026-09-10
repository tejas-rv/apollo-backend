package com.apollo.elevator.common.api;

import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.common.exception.InvalidGstPercentageException;
import com.apollo.elevator.common.exception.NotificationDeliveryException;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.common.exception.UnauthorizedException;
import com.apollo.elevator.common.exception.UnsupportedDocumentTypeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        log.warn(
            "Validation failed for path={}: {}",
            request.getRequestURI(),
            exception.getMessage()
        );
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", request, errors);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        log.warn(
            "Resource not found for path={}: {}",
            request.getRequestURI(),
            exception.getMessage()
        );
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            ConflictException exception,
            HttpServletRequest request
    ) {
        log.warn(
            "Conflict for path={}: {}",
            request.getRequestURI(),
            exception.getMessage()
        );
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedDocumentType(
            UnsupportedDocumentTypeException exception,
            HttpServletRequest request
    ) {
        log.warn(
            "Unsupported document type for path={}: {}",
            request.getRequestURI(),
            exception.getMessage()
        );
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getErrorCode(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        Object value = exception.getValue();
        String rawValue = value == null ? "" : value.toString();
        if ("WITHOUT_GST_BILL".equalsIgnoreCase(rawValue)) {
            log.warn("Unsupported document type requested for path={}: {}", request.getRequestURI(), rawValue);
            return buildResponse(HttpStatus.BAD_REQUEST, "UNSUPPORTED_DOCUMENT_TYPE", "Only GST_BILL is supported.", request, Map.of());
        }
        log.warn("Type mismatch for path={}: {}", request.getRequestURI(), exception.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidGstPercentageException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidGstPercentage(
            InvalidGstPercentageException exception,
            HttpServletRequest request
    ) {
        log.warn(
            "Invalid GST percentage for path={}: {}",
            request.getRequestURI(),
            exception.getMessage()
        );
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getErrorCode(), exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({IllegalArgumentException.class, NotificationDeliveryException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        log.warn(
            "Bad request for path={}: {}",
            request.getRequestURI(),
            exception.getMessage()
        );
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(
            UnauthorizedException exception,
            HttpServletRequest request
    ) {
        log.warn(
            "Unauthorized for path={}: {}",
            request.getRequestURI(),
            exception.getMessage()
        );
        return buildResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        log.warn(
            "Access denied for path={}: {}",
            request.getRequestURI(),
            exception.getMessage()
        );
        return buildResponse(HttpStatus.FORBIDDEN, "You are not allowed to access this resource", request, Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException exception,
            HttpServletRequest request
    ) {
        log.warn(
            "Illegal state for path={}: {}",
            request.getRequestURI(),
            exception.getMessage()
        );
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
            "Unexpected error for path={}",
            request.getRequestURI(),
            exception
        );
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        return buildResponse(status, status.getReasonPhrase(), message, request, validationErrors);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                errorCode,
                message,
                request.getRequestURI(),
                validationErrors
        );
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
