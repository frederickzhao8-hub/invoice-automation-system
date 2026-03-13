package com.invoiceautomation.backend.dto;

import com.invoiceautomation.backend.entity.AlertSeverity;
import com.invoiceautomation.backend.entity.AlertStatus;
import com.invoiceautomation.backend.entity.AlertType;
import com.invoiceautomation.backend.entity.MilestoneType;
import java.time.LocalDateTime;

public record AlertResponse(
        Long id,
        Long orderId,
        String orderNumber,
        String customerName,
        MilestoneType milestoneType,
        String milestoneLabel,
        AlertType alertType,
        AlertSeverity severity,
        AlertStatus status,
        String title,
        String message,
        LocalDateTime expectedAt,
        LocalDateTime triggeredAt,
        LocalDateTime resolvedAt) {
}
