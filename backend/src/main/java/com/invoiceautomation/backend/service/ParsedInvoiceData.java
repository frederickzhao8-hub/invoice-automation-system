package com.invoiceautomation.backend.service;

import com.invoiceautomation.backend.entity.InvoiceParseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedInvoiceData(
        String invoiceNumber,
        String vendorName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotalAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String currency,
        LocalDate issueDate,
        InvoiceParseStatus parseStatus,
        BigDecimal parseConfidence,
        String rawExtractedText,
        boolean needsReview) {
}

