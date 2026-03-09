package com.invoiceautomation.backend.dto;

import com.invoiceautomation.backend.entity.InvoiceStatus;
import com.invoiceautomation.backend.entity.InvoiceParseStatus;
import com.invoiceautomation.backend.entity.InvoiceProcessingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InvoiceResponse(
        Long id,
        String vendor,
        String vendorName,
        String invoiceNumber,
        BigDecimal amount,
        BigDecimal totalAmount,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotalAmount,
        BigDecimal taxAmount,
        String currency,
        LocalDate invoiceDate,
        LocalDate issueDate,
        LocalDate dueDate,
        String paymentTerms,
        String invoiceDescription,
        InvoiceStatus status,
        InvoiceParseStatus parseStatus,
        InvoiceProcessingStatus processingStatus,
        BigDecimal parseConfidence,
        String rawExtractedText,
        boolean needsReview,
        boolean duplicateFlag,
        String duplicateReason,
        String extractionError,
        String originalFileName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
