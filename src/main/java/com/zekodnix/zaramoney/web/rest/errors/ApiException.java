package com.zekodnix.zaramoney.web.rest.errors;

import java.time.Duration;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Business error with an explicit HTTP status, rendered as Problem Details by {@link ExceptionTranslator}
 * ({@code {"status": 403, "title": "...", "message": "error.<errorKey>"}}).
 * Use it instead of bare {@code orElseThrow()} / {@code IllegalArgumentException}, which end up as 500.
 */
public class ApiException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    private final String errorKey;

    public ApiException(HttpStatus status, String title, String errorKey) {
        super(
            status,
            ProblemDetailWithCause.ProblemDetailWithCauseBuilder.instance()
                .withStatus(status.value())
                .withType(ErrorConstants.DEFAULT_TYPE)
                .withTitle(title)
                .withDetail(title)
                .withProperty("message", "error." + errorKey)
                .build(),
            null
        );
        this.errorKey = errorKey;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public static ApiException badRequest(String title, String errorKey) {
        return new ApiException(HttpStatus.BAD_REQUEST, title, errorKey);
    }

    public static ApiException forbidden(String title, String errorKey) {
        return new ApiException(HttpStatus.FORBIDDEN, title, errorKey);
    }

    public static ApiException notFound(String title, String errorKey) {
        return new ApiException(HttpStatus.NOT_FOUND, title, errorKey);
    }

    public static ApiException conflict(String title, String errorKey) {
        return new ApiException(HttpStatus.CONFLICT, title, errorKey);
    }

    public static ApiException tooManyRequests(String title, String errorKey, Duration retryAfter) {
        ApiException ex = new ApiException(HttpStatus.TOO_MANY_REQUESTS, title, errorKey);
        ex.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfter.toSeconds())));
        return ex;
    }
}
