package com.invoiceautomation.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record MilestoneRecordRequest(
        @NotNull(message = "Milestone timestamp is required.") LocalDateTime occurredAt,
        String notes) {
}
