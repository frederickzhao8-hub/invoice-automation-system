package com.invoiceautomation.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DeliveryRecordResponse(
        Long id,
        String itemName,
        BigDecimal quantity,
        String date,
        String location,
        String poNumber,
        String entryNote,
        String rawText,
        String originalFileName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
