package com.invoiceautomation.backend.dto;

import java.util.List;

public record SupplyChainMilestoneImportResult(
        int totalRows,
        int successCount,
        int skippedCount,
        int failureCount,
        int historyEntriesCreated,
        List<SupplyChainMilestoneImportItemResult> results) {
}
