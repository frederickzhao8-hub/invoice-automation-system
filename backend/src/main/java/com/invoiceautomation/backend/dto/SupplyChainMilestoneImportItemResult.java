package com.invoiceautomation.backend.dto;

import java.util.List;

public record SupplyChainMilestoneImportItemResult(
        int rowNumber,
        String orderNumber,
        String status,
        List<String> updatedMilestones,
        int historyEntriesCreated,
        String message) {
}
