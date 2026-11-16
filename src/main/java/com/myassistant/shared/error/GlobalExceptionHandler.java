package com.myassistant.shared.error;

import jakarta.validation.ConstraintViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Turns the application's exceptions into {@link ProblemDetail} responses, in one place, so the controllers only
 * describe the happy path.
 * <p>
 * It extends {@link ResponseEntityExceptionHandler}, so the framework's errors (unreadable body, wrong content type,
 * unknown path, ...) are still answered as problem details. Spring Boot's own handler steps aside when this one
 * exists; we need our own to add the per-field errors to a failed {@code @Valid}.
 * <p>
 * The 401 answers come from here too: a failed login, and (through SecurityConfig's entry point) a request without a
 * valid token. Anything unexpected becomes a 500 problem detail that says nothing about the cause.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** The property of a 400 problem detail that maps each invalid field to its message. */
    public static final String FIELD_ERRORS = "fieldErrors";

    static final String VALIDATION_FAILED = "Some fields are invalid.";
    static final String STALE_VERSION = "This item was changed in another tab. Reload it and try again.";
    static final String DUPLICATE_KEY = "An item with the same unique value already exists.";
    static final String TOKEN_REQUIRED = "A valid login token is required. Log in and try again.";
    static final String UNEXPECTED = "Something went wrong. Try again later.";

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(CONFLICT, exception.getMessage());
        exception.properties().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(OptimisticLockingFailureException exception) {
        return ProblemDetail.forStatusAndDetail(CONFLICT, STALE_VERSION);
    }

    // The driver's message names the index and repeats the duplicated value (an email, say): never sent back.
    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail handleDuplicateKey(DuplicateKeyException exception) {
        return ProblemDetail.forStatusAndDetail(CONFLICT, DUPLICATE_KEY);
    }

    // Thrown by the entity validation before a save (MongoConfig): the same shape as an invalid request body.
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            fieldErrors.merge(violation.getPropertyPath().toString(), violation.getMessage(), GlobalExceptionHandler::join);
        }
        return validationProblem(fieldErrors);
    }

    // Our own login failure (InvalidCredentialsException): its message is written for the user.
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException exception) {
        return ProblemDetail.forStatusAndDetail(UNAUTHORIZED, exception.getMessage());
    }

    // No token, or a broken, expired or foreign one. The security filter's message (why the token was refused) stays
    // out of the body; the WWW-Authenticate header already carries the standard error code.
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException exception) {
        return ProblemDetail.forStatusAndDetail(UNAUTHORIZED, TOKEN_REQUIRED);
    }

    // Spring Security decides 403 vs 401 itself (ExceptionTranslationFilter): hand it back, never make it a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException exception) {
        throw exception;
    }

    // The last resort. The cause goes to the log for us, never to the client: it may name tables, hosts or values.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ProblemDetail.forStatusAndDetail(INTERNAL_SERVER_ERROR, UNEXPECTED);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.merge(error.getField(), Objects.toString(error.getDefaultMessage(), "is invalid"),
                    GlobalExceptionHandler::join);
        }
        return handleExceptionInternal(exception, validationProblem(fieldErrors), headers, status, request);
    }

    private static ProblemDetail validationProblem(Map<String, String> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(BAD_REQUEST, VALIDATION_FAILED);
        problem.setProperty(FIELD_ERRORS, fieldErrors);
        return problem;
    }

    private static String join(String first, String second) {
        return first + "; " + second;
    }
}
