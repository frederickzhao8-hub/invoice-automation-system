package com.invoiceautomation.backend.service;

import com.invoiceautomation.backend.entity.AlertSeverity;
import com.invoiceautomation.backend.entity.AlertType;
import com.invoiceautomation.backend.entity.MilestoneSlaStatus;
import com.invoiceautomation.backend.entity.MilestoneType;
import com.invoiceautomation.backend.entity.OrderHealthStatus;
import com.invoiceautomation.backend.entity.OrderMilestone;
import com.invoiceautomation.backend.entity.SlaRule;
import com.invoiceautomation.backend.entity.SupplyChainOrder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SupplyChainSlaCalculator {

    private final Clock clock;

    public SupplyChainSlaCalculator(Clock clock) {
        this.clock = clock;
    }

    public TrackingSnapshot buildSnapshot(SupplyChainOrder order, List<SlaRule> rules) {
        Map<MilestoneType, OrderMilestone> actualMilestones = new EnumMap<>(MilestoneType.class);
        order.getMilestones().stream()
                .sorted(Comparator.comparing(OrderMilestone::getOccurredAt))
                .forEach(milestone -> actualMilestones.put(milestone.getMilestoneType(), milestone));

        Map<SlaRuleKey, SlaRule> rulesByKey = new java.util.HashMap<>();
        for (SlaRule rule : rules) {
            rulesByKey.put(new SlaRuleKey(rule.getStartMilestone(), rule.getEndMilestone()), rule);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime anchorForNextMilestone = null;
        MilestoneType currentMilestone = null;
        MilestoneType nextMilestone = null;
        LocalDateTime nextExpectedAt = null;
        boolean pendingMilestoneEvaluated = false;
        boolean anyBreach = false;
        boolean anyAtRisk = false;
        List<TimelineEntry> timeline = new ArrayList<>();
        List<AlertCandidate> activeAlerts = new ArrayList<>();

        for (MilestoneType milestoneType : MilestoneType.flow()) {
            OrderMilestone actualMilestone = actualMilestones.get(milestoneType);
            LocalDateTime actualAt = actualMilestone == null ? null : actualMilestone.getOccurredAt();
            String notes = actualMilestone == null ? null : actualMilestone.getNotes();
            LocalDateTime expectedAt = actualAt;
            Integer targetDays = null;
            Integer warningDays = null;
            boolean breached = false;
            boolean atRisk = false;
            MilestoneSlaStatus slaStatus;

            if (milestoneType.isFirst()) {
                if (actualAt == null) {
                    slaStatus = MilestoneSlaStatus.PENDING;
                } else {
                    slaStatus = MilestoneSlaStatus.COMPLETED_ON_TIME;
                    currentMilestone = milestoneType;
                }
                anchorForNextMilestone = actualAt;
            } else {
                MilestoneType previousMilestone = milestoneType.previous();
                SlaRule rule = rulesByKey.get(new SlaRuleKey(previousMilestone, milestoneType));
                if (rule == null) {
                    throw new IllegalStateException(
                            "Missing SLA rule for "
                                    + previousMilestone
                                    + " -> "
                                    + milestoneType);
                }

                targetDays = rule.getTargetDays();
                warningDays = rule.getWarningDays();
                expectedAt = anchorForNextMilestone == null ? null : anchorForNextMilestone.plusDays(targetDays);

                if (actualAt != null) {
                    currentMilestone = milestoneType;
                    if (expectedAt != null && actualAt.isAfter(expectedAt)) {
                        breached = true;
                        anyBreach = true;
                        slaStatus = MilestoneSlaStatus.COMPLETED_LATE;
                        activeAlerts.add(buildBreachAlert(order, milestoneType, expectedAt, actualAt, true));
                    } else {
                        slaStatus = MilestoneSlaStatus.COMPLETED_ON_TIME;
                    }
                } else if (!pendingMilestoneEvaluated && expectedAt != null) {
                    pendingMilestoneEvaluated = true;
                    nextMilestone = milestoneType;
                    nextExpectedAt = expectedAt;

                    if (now.isAfter(expectedAt)) {
                        breached = true;
                        anyBreach = true;
                        slaStatus = MilestoneSlaStatus.OVERDUE;
                        activeAlerts.add(buildBreachAlert(order, milestoneType, expectedAt, now, false));
                    } else if (!now.isBefore(expectedAt.minusDays(warningDays))) {
                        atRisk = true;
                        anyAtRisk = true;
                        slaStatus = MilestoneSlaStatus.AT_RISK;
                        activeAlerts.add(buildAtRiskAlert(order, milestoneType, expectedAt, warningDays));
                    } else {
                        slaStatus = MilestoneSlaStatus.PENDING;
                    }
                } else {
                    if (nextMilestone == null) {
                        nextMilestone = milestoneType;
                        nextExpectedAt = expectedAt;
                    }
                    slaStatus = MilestoneSlaStatus.PENDING;
                }

                anchorForNextMilestone = actualAt != null ? actualAt : expectedAt;
            }

            timeline.add(new TimelineEntry(
                    milestoneType,
                    actualAt,
                    expectedAt,
                    targetDays,
                    warningDays,
                    slaStatus,
                    actualAt != null,
                    breached,
                    atRisk,
                    notes));
        }

        boolean completed = actualMilestones.containsKey(MilestoneType.DELIVERED);
        OrderHealthStatus healthStatus = anyBreach
                ? OrderHealthStatus.DELAYED
                : anyAtRisk ? OrderHealthStatus.AT_RISK : OrderHealthStatus.ON_TIME;

        return new TrackingSnapshot(
                order,
                healthStatus,
                currentMilestone,
                nextMilestone,
                nextExpectedAt,
                completed,
                List.copyOf(timeline),
                List.copyOf(activeAlerts));
    }

    private AlertCandidate buildAtRiskAlert(
            SupplyChainOrder order,
            MilestoneType milestoneType,
            LocalDateTime expectedAt,
            Integer warningDays) {
        return new AlertCandidate(
                milestoneType,
                AlertType.AT_RISK,
                AlertSeverity.WARNING,
                "Milestone approaching SLA limit",
                "Order " + order.getOrderNumber()
                        + " is within the "
                        + warningDays
                        + "-day warning window for "
                        + milestoneType.displayName()
                        + ".",
                expectedAt);
    }

    private AlertCandidate buildBreachAlert(
            SupplyChainOrder order,
            MilestoneType milestoneType,
            LocalDateTime expectedAt,
            LocalDateTime comparisonTime,
            boolean completedLate) {
        long lateDays = elapsedSlaDays(expectedAt, comparisonTime);
        String actionPhrase = completedLate ? "completed late" : "is overdue";

        return new AlertCandidate(
                milestoneType,
                AlertType.SLA_BREACH,
                AlertSeverity.CRITICAL,
                "SLA breached for " + milestoneType.displayName(),
                "Order " + order.getOrderNumber()
                        + " "
                        + actionPhrase
                        + " by "
                        + lateDays
                        + " day"
                        + (lateDays == 1 ? "" : "s")
                        + " for "
                        + milestoneType.displayName()
                        + ".",
                expectedAt);
    }

    private long elapsedSlaDays(LocalDateTime expectedAt, LocalDateTime actualAt) {
        long days = ChronoUnit.DAYS.between(expectedAt.toLocalDate(), actualAt.toLocalDate());
        if (days == 0 && actualAt.isAfter(expectedAt)) {
            return 1;
        }
        return Math.max(days, 0);
    }

    private record SlaRuleKey(MilestoneType startMilestone, MilestoneType endMilestone) {
    }

    public record TimelineEntry(
            MilestoneType milestoneType,
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

    public record AlertCandidate(
            MilestoneType milestoneType,
            AlertType alertType,
            AlertSeverity severity,
            String title,
            String message,
            LocalDateTime expectedAt) {
    }

    public record TrackingSnapshot(
            SupplyChainOrder order,
            OrderHealthStatus healthStatus,
            MilestoneType currentMilestone,
            MilestoneType nextMilestone,
            LocalDateTime nextExpectedAt,
            boolean completed,
            List<TimelineEntry> timeline,
            List<AlertCandidate> activeAlerts) {

        public int openAlertCount() {
            return activeAlerts.size();
        }
    }
}
