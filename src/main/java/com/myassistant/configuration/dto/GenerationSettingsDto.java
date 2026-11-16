package com.myassistant.configuration.dto;

import com.myassistant.configuration.MaxTokensBelowContextWindow;
import com.myassistant.configuration.MaxTokensBelowContextWindow.TokenBudget;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The generation settings as the client sends and receives them; the same limits as the stored value object. */
@MaxTokensBelowContextWindow
public record GenerationSettingsDto(@NotNull @DecimalMin("0") @DecimalMax("2") Double temperature,
                                    @NotNull @DecimalMin("0") @DecimalMax("1") Double topP,
                                    @NotNull @Min(1) @Max(4096) Integer maxTokens,
                                    @NotNull @Min(512) @Max(32768) Integer contextWindowSize,
                                    @NotNull @DecimalMin("-2") @DecimalMax("2") Double frequencyPenalty,
                                    @NotNull @DecimalMin("-2") @DecimalMax("2") Double presencePenalty)
        implements TokenBudget {
}
