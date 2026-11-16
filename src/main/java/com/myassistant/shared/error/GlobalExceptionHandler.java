package com.myassistant.shared.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Turns the application's exceptions into {@link ProblemDetail} responses, in one place, so the controllers only
 * describe the happy path.
 * <p>
 * It extends {@link ResponseEntityExceptionHandler}, so the framework's errors (unreadable body, wrong content type,
 * unknown path, ...) are still answered as problem details. Spring Boot's own handler steps aside when this one
 * exists; we need our own to add the per-field errors to a failed {@code @Valid}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** The property of a 400 problem detail that maps each invalid field to its message. */
    public static final String FIELD_ERRORS = "fieldErrors";

    static final String VALIDATION_FAILED = "Some fields are invalid.";
    static final String STALE_VERSION = "This item was changed in another tab. Reload it and try again.";
    static final String DUPLICATE_KEY = "An item with the same unique value already exists.";

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
