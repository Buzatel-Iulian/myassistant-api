package com.myassistant.configuration;

import com.myassistant.configuration.MaxTokensBelowContextWindow.TokenBudget;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The WebLLM generation settings of a configuration, embedded in {@link LlmConfiguration} (a value object, like
 * {@code Venue} inside {@code Event}). The limits are the ones the web client's sliders use (Phase 9):
 * {@code contextWindowSize} is applied when the model is loaded, the others with every request; {@code maxTokens}
 * must be smaller than {@code contextWindowSize}.
 */
@MaxTokensBelowContextWindow
public record GenerationSettings(@NotNull @DecimalMin("0") @DecimalMax("2") Double temperature,
                                 @NotNull @DecimalMin("0") @DecimalMax("1") Double topP,
                                 @NotNull @Min(1) @Max(4096) Integer maxTokens,
                                 @NotNull @Min(512) @Max(32768) Integer contextWindowSize,
                                 @NotNull @DecimalMin("-2") @DecimalMax("2") Double frequencyPenalty,
                                 @NotNull @DecimalMin("-2") @DecimalMax("2") Double presencePenalty)
        implements TokenBudget {
}
