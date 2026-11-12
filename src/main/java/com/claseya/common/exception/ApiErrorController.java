package com.claseya.common.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Boot's default {@code /error} body (which reports status 999 when no error dispatch
 * attributes are present) with the shared {@link ApiError} contract.
 *
 * <p>A direct {@code GET /error} is not a resource, so it answers 404. A real error dispatch keeps
 * its original status. Messages stay generic: no stack traces, SQL or exception details.
 */
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int rawStatus = attribute instanceof Integer value ? value : HttpStatus.NOT_FOUND.value();
        HttpStatus status = HttpStatus.resolve(rawStatus);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String message = status == HttpStatus.NOT_FOUND
                ? "Resource not found"
                : status.is5xxServerError()
                        ? "An unexpected error occurred"
                        : status.getReasonPhrase();
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message,
                        request.getRequestURI()));
    }
}
