package com.invoiceautomation.backend.dto;

public record SupplyChainDashboardResponse(
        long totalOrders,
        long onTimeOrders,
        long atRiskOrders,
        long delayedOrders,
        long deliveredOrders,
        long openAlerts) {
}
