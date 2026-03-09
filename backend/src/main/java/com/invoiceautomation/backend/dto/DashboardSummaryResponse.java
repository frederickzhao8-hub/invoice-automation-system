package com.invoiceautomation.backend.dto;

import java.math.BigDecimal;

public record DashboardSummaryResponse(
        long totalInvoices,
        BigDecimal totalAmount,
        long pendingCount,
        long approvedCount,
        long paidCount) {
}

