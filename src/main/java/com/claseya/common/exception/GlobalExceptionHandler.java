package com.claseya.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Central error handling. Never leaks stack traces or internal details to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyExists(EmailAlreadyExistsException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRoleException.class)
    public ResponseEntity<ApiError> handleInvalidRole(InvalidRoleException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
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

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
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
