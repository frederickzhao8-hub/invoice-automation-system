package com.invoiceautomation.backend.dto;

import com.invoiceautomation.backend.entity.MilestoneType;
import java.time.LocalDateTime;

public record MilestoneImportHistoryResponse(
        Long id,
        Long orderId,
        String orderNumber,
        MilestoneType milestoneType,
        String milestoneLabel,
        LocalDateTime previousOccurredAt,
        LocalDateTime newOccurredAt,
        String previousNotes,
        String newNotes,
        String sourceFileName,
        LocalDateTime importedAt) {
}
