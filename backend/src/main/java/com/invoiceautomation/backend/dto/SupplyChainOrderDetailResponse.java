package com.invoiceautomation.backend.dto;

import com.invoiceautomation.backend.entity.MilestoneType;
import com.invoiceautomation.backend.entity.OrderHealthStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SupplyChainOrderDetailResponse(
        Long id,
        String orderNumber,
        String customerName,
        String supplierName,
        String productName,
        String originCountry,
        String destinationCountry,
        BigDecimal quantity,
        BigDecimal orderValue,
        String notes,
        MilestoneType currentMilestone,
        String currentMilestoneLabel,
        MilestoneType nextMilestone,
        String nextMilestoneLabel,
        LocalDateTime nextExpectedAt,
        OrderHealthStatus healthStatus,
        boolean completed,
        int openAlertCount,
        List<OrderMilestoneResponse> timeline,
        List<AlertResponse> alerts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
