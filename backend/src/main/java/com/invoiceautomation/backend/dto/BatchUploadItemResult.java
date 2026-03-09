package com.invoiceautomation.backend.dto;

import com.invoiceautomation.backend.entity.InvoiceProcessingStatus;

public record BatchUploadItemResult(
        String fileName,
        InvoiceProcessingStatus status,
        Long invoiceId,
        String vendorName,
        String invoiceNumber,
        boolean duplicate,
        String message) {
}
