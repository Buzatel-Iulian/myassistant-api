package com.myassistant.configuration;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The answer ({@code maxTokens}) must fit in the context window with room left for the prompt: the web client keeps
 * the conversation within {@code contextWindowSize - maxTokens} (Phase 10), which must stay positive. The violation is
 * reported on {@code maxTokens}, so the client shows it next to that field ({@code fieldErrors["settings.maxTokens"]}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxTokensBelowContextWindow.Validator.class)
public @interface MaxTokensBelowContextWindow {

    String message() default "must be less than the context window size";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** The two settings the rule compares; implemented by the stored settings and by their DTO. */
    interface TokenBudget {
        Integer maxTokens();

        Integer contextWindowSize();
    }

    class Validator implements ConstraintValidator<MaxTokensBelowContextWindow, TokenBudget> {

        // A missing value is reported by @NotNull on its own field.
        @Override
        public boolean isValid(TokenBudget settings, ConstraintValidatorContext context) {
            if (settings == null || settings.maxTokens() == null || settings.contextWindowSize() == null
                    || settings.maxTokens() < settings.contextWindowSize()) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("maxTokens")
                    .addConstraintViolation();
            return false;
        }
    }
}
