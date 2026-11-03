package com.apollo.elevator.common.api;

import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.common.exception.InvalidGstPercentageException;
import com.apollo.elevator.common.exception.NotificationDeliveryException;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.common.exception.UnauthorizedException;
import com.apollo.elevator.common.exception.UnsupportedDocumentTypeException;
import com.apollo.elevator.common.util.LogMaskingUtil;
import com.apollo.elevator.googledrive.exception.GoogleDriveAuthenticationException;
import com.apollo.elevator.googledrive.exception.GoogleDriveOperationException;
import com.apollo.elevator.googledrive.exception.GoogleDrivePermissionException;
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
import org.springframework.web.servlet.HandlerMapping;

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
            route(request),
            LogMaskingUtil.mask("errorMessage", exception.getMessage())
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
            route(request),
            LogMaskingUtil.mask("errorMessage", exception.getMessage())
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
            route(request),
            LogMaskingUtil.mask("errorMessage", exception.getMessage())
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
            route(request),
            LogMaskingUtil.mask("errorMessage", exception.getMessage())
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
            log.warn("Unsupported document type requested for path={}: {}", route(request), rawValue);
            return buildResponse(HttpStatus.BAD_REQUEST, "UNSUPPORTED_DOCUMENT_TYPE", "Only GST_BILL is supported.", request, Map.of());
        }
        log.warn("Type mismatch for path={}: {}", route(request),
                LogMaskingUtil.mask("errorMessage", exception.getMessage()));
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidGstPercentageException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidGstPercentage(
            InvalidGstPercentageException exception,
            HttpServletRequest request
    ) {
        log.warn(
            "Invalid GST percentage for path={}: {}",
            route(request),
            LogMaskingUtil.mask("errorMessage", exception.getMessage())
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
            route(request),
            LogMaskingUtil.mask("errorMessage", exception.getMessage())
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
            route(request),
            LogMaskingUtil.mask("errorMessage", exception.getMessage())
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
            route(request),
            LogMaskingUtil.mask("errorMessage", exception.getMessage())
        );
        return buildResponse(HttpStatus.FORBIDDEN, "You are not allowed to access this resource", request, Map.of());
    }

    @ExceptionHandler(GoogleDrivePermissionException.class)
    public ResponseEntity<ApiErrorResponse> handleGoogleDrivePermission(
            GoogleDrivePermissionException exception,
            HttpServletRequest request
    ) {
        log.warn("Google Drive permission denied for path={}", route(request));
        return buildResponse(HttpStatus.FORBIDDEN, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({GoogleDriveAuthenticationException.class, GoogleDriveOperationException.class})
    public ResponseEntity<ApiErrorResponse> handleGoogleDriveFailure(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        log.error("Google Drive operation failed for path={}, errorType={}",
                route(request), exception.getClass().getSimpleName());
        return buildResponse(HttpStatus.BAD_GATEWAY, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException exception,
            HttpServletRequest request
    ) {
        log.warn(
            "Illegal state for path={}: {}",
            route(request),
            LogMaskingUtil.mask("errorMessage", exception.getMessage())
        );
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
            "Unexpected error for path={}, errorType={}",
            route(request),
            exception.getClass().getSimpleName()
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

    private String route(HttpServletRequest request) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return route == null ? "<unmatched>" : route.toString();
    }
}
