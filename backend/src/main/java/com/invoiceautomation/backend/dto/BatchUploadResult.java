package com.invoiceautomation.backend.dto;

import java.util.List;

public record BatchUploadResult(
        int totalFiles,
        int successCount,
        int duplicateCount,
        int failureCount,
        List<BatchUploadItemResult> results,
        List<InvoiceResponse> savedInvoices) {
}
