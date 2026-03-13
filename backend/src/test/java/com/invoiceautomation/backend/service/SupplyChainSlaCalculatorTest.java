package com.invoiceautomation.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invoiceautomation.backend.entity.AlertType;
import com.invoiceautomation.backend.entity.MilestoneSlaStatus;
import com.invoiceautomation.backend.entity.MilestoneType;
import com.invoiceautomation.backend.entity.OrderHealthStatus;
import com.invoiceautomation.backend.entity.OrderMilestone;
import com.invoiceautomation.backend.entity.SlaRule;
import com.invoiceautomation.backend.entity.SupplyChainOrder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupplyChainSlaCalculatorTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-09T17:00:00Z"), ZoneOffset.UTC);
    private final SupplyChainSlaCalculator calculator = new SupplyChainSlaCalculator(fixedClock);

    @Test
    void marksOrderAtRiskWhenNextMilestoneEntersWarningWindow() {
        SupplyChainOrder order = baseOrder("SC-1003");
        addMilestone(order, MilestoneType.PO_RECEIVED, LocalDateTime.of(2026, 2, 10, 9, 0));

        SupplyChainSlaCalculator.TrackingSnapshot snapshot = calculator.buildSnapshot(order, defaultRules());

        assertEquals(OrderHealthStatus.AT_RISK, snapshot.healthStatus());
        assertEquals(MilestoneType.PRODUCTION_COMPLETED, snapshot.nextMilestone());
        assertEquals(1, snapshot.activeAlerts().size());
        assertEquals(AlertType.AT_RISK, snapshot.activeAlerts().get(0).alertType());
        assertEquals(MilestoneSlaStatus.AT_RISK, snapshot.timeline().get(1).slaStatus());
    }

    @Test
    void marksOrderDelayedWhenCompletedMilestoneMissesSla() {
        SupplyChainOrder order = baseOrder("SC-1002");
        addMilestone(order, MilestoneType.PO_RECEIVED, LocalDateTime.of(2026, 1, 1, 9, 0));
        addMilestone(order, MilestoneType.PRODUCTION_COMPLETED, LocalDateTime.of(2026, 2, 5, 9, 0));

        SupplyChainSlaCalculator.TrackingSnapshot snapshot = calculator.buildSnapshot(order, defaultRules());

        assertEquals(OrderHealthStatus.DELAYED, snapshot.healthStatus());
        assertTrue(snapshot.timeline().get(1).breached());
        assertEquals(MilestoneSlaStatus.COMPLETED_LATE, snapshot.timeline().get(1).slaStatus());
        assertEquals(AlertType.SLA_BREACH, snapshot.activeAlerts().get(0).alertType());
    }

    @Test
    void keepsFutureMilestonesPendingWhenCurrentMilestoneIsOverdue() {
        SupplyChainOrder order = baseOrder("SC-1010");
        addMilestone(order, MilestoneType.PO_RECEIVED, LocalDateTime.of(2026, 1, 1, 9, 0));

        SupplyChainSlaCalculator.TrackingSnapshot snapshot = calculator.buildSnapshot(order, defaultRules());

        assertEquals(OrderHealthStatus.DELAYED, snapshot.healthStatus());
        assertEquals(MilestoneSlaStatus.OVERDUE, snapshot.timeline().get(1).slaStatus());
        assertEquals(MilestoneSlaStatus.PENDING, snapshot.timeline().get(2).slaStatus());
        assertFalse(snapshot.timeline().get(2).breached());
        assertEquals(1, snapshot.activeAlerts().size());
    }

    private SupplyChainOrder baseOrder(String orderNumber) {
        SupplyChainOrder order = new SupplyChainOrder();
        order.setOrderNumber(orderNumber);
        order.setCustomerName("Demo Customer");
        order.setSupplierName("Demo Supplier");
        order.setProductName("Fiber Cable");
        order.setOriginCountry("China");
        order.setDestinationCountry("Mexico");
        order.setQuantity(new BigDecimal("100.00"));
        order.setOrderValue(new BigDecimal("1000.00"));
        return order;
    }

    private void addMilestone(SupplyChainOrder order, MilestoneType milestoneType, LocalDateTime occurredAt) {
        OrderMilestone milestone = new OrderMilestone();
        milestone.setMilestoneType(milestoneType);
        milestone.setOccurredAt(occurredAt);
        order.addMilestone(milestone);
    }

    private List<SlaRule> defaultRules() {
        return List.of(
                rule(MilestoneType.PO_RECEIVED, MilestoneType.PRODUCTION_COMPLETED, 30, 3),
                rule(MilestoneType.PRODUCTION_COMPLETED, MilestoneType.SHIPPED, 5, 2),
                rule(MilestoneType.SHIPPED, MilestoneType.ARRIVED_PORT, 23, 3),
                rule(MilestoneType.ARRIVED_PORT, MilestoneType.CUSTOMS_CLEARED, 7, 2),
                rule(MilestoneType.CUSTOMS_CLEARED, MilestoneType.DELIVERED, 2, 1));
    }

    private SlaRule rule(
            MilestoneType startMilestone,
            MilestoneType endMilestone,
            int targetDays,
            int warningDays) {
        SlaRule rule = new SlaRule();
        rule.setStartMilestone(startMilestone);
        rule.setEndMilestone(endMilestone);
        rule.setTargetDays(targetDays);
        rule.setWarningDays(warningDays);
        rule.setActive(true);
        return rule;
    }
}
