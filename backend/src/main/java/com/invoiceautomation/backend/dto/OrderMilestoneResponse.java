package com.invoiceautomation.backend.dto;

import com.invoiceautomation.backend.entity.MilestoneSlaStatus;
import com.invoiceautomation.backend.entity.MilestoneType;
import java.time.LocalDateTime;

public record OrderMilestoneResponse(
        MilestoneType milestoneType,
        String milestoneLabel,
        LocalDateTime actualAt,
        LocalDateTime expectedAt,
        Integer targetDays,
        Integer warningDays,
        MilestoneSlaStatus slaStatus,
        boolean completed,
        boolean breached,
        boolean atRisk,
        String notes) {
}
