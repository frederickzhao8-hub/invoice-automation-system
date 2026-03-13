package com.invoiceautomation.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record SlaRuleUpdateRequest(
        @NotNull(message = "Target days are required.")
        @Positive(message = "Target days must be greater than zero.")
                Integer targetDays,
        @NotNull(message = "Warning days are required.")
        @PositiveOrZero(message = "Warning days must be zero or greater.")
                Integer warningDays,
        @NotNull(message = "Active flag is required.") Boolean active) {
}
