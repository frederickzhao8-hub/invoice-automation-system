package com.invoiceautomation.backend.dto;

import com.invoiceautomation.backend.entity.MilestoneType;
import java.time.LocalDateTime;

public record SlaRuleResponse(
        Long id,
        MilestoneType startMilestone,
        String startMilestoneLabel,
        MilestoneType endMilestone,
        String endMilestoneLabel,
        Integer targetDays,
        Integer warningDays,
        boolean active,
        LocalDateTime updatedAt) {
}
