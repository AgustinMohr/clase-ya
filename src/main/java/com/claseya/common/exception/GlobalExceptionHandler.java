package com.claseya.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Central error handling. Never leaks stack traces or internal details to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidRoleException.class)
    public ResponseEntity<ApiError> handleInvalidRole(InvalidRoleException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    /** SEC-04: a concurrent duplicate must look exactly like a successful registration. */
    @ExceptionHandler(RegistrationConflictException.class)
    public ResponseEntity<Void> handleRegistrationConflict(RegistrationConflictException ex) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @ExceptionHandler(InvalidAssociationException.class)
    public ResponseEntity<ApiError> handleInvalidAssociation(InvalidAssociationException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ApiError> handlePayloadTooLarge(PayloadTooLargeException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large", ex.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                                        HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large",
                "Document exceeds the 10 MB limit", request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleServiceUnavailable(ServiceUnavailableException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Conflict", "Resource already exists or conflicts with existing data", request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid credentials", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", "Access denied", request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimitExceeded(RateLimitExceededException ex,
                                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiError.of(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests",
                        ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleMalformedBody(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Malformed request", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", "Resource not found", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", "Method not allowed", request);
    }

    /**
     * A body sent with an unsupported Content-Type is a client error (415), not a server failure.
     * Without this handler it fell through to the catch-all and answered a misleading 500.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type",
                "Content-Type is not supported", request);
    }

    /**
     * A required request value (parameter, header or path variable) is missing: a client error
     * (400), not a server failure. Without this handler it fell through to the catch-all and
     * answered a misleading 500 — the same class of bug as the unsupported Content-Type above.
     */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiError> handleRequestBinding(ServletRequestBindingException ex,
                                                         HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Required request data is missing", request);
    }

    /**
     * A required multipart part (e.g. the uploaded file) is missing. It extends
     * {@link jakarta.servlet.ServletException}, not {@code ServletRequestBindingException}, so it
     * needs its own handler to avoid the catch-all turning a client error into a 500.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex,
                                                      HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Required request data is missing", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), error, message, request.getRequestURI()));
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message,
                                           HttpServletRequest request, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), error, message, request.getRequestURI(), fieldErrors));
    }
}
