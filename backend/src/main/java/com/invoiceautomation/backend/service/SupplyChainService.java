package com.invoiceautomation.backend.service;

import com.invoiceautomation.backend.dto.AlertResponse;
import com.invoiceautomation.backend.dto.MilestoneRecordRequest;
import com.invoiceautomation.backend.dto.OrderMilestoneResponse;
import com.invoiceautomation.backend.dto.SlaRuleResponse;
import com.invoiceautomation.backend.dto.SlaRuleUpdateRequest;
import com.invoiceautomation.backend.dto.SupplyChainDashboardResponse;
import com.invoiceautomation.backend.dto.SupplyChainOrderDetailResponse;
import com.invoiceautomation.backend.dto.SupplyChainOrderRequest;
import com.invoiceautomation.backend.dto.SupplyChainOrderSummaryResponse;
import com.invoiceautomation.backend.entity.AlertStatus;
import com.invoiceautomation.backend.entity.AlertType;
import com.invoiceautomation.backend.entity.MilestoneType;
import com.invoiceautomation.backend.entity.OrderMilestone;
import com.invoiceautomation.backend.entity.OrderHealthStatus;
import com.invoiceautomation.backend.entity.SlaRule;
import com.invoiceautomation.backend.entity.SupplyChainAlert;
import com.invoiceautomation.backend.entity.SupplyChainOrder;
import com.invoiceautomation.backend.repository.SlaRuleRepository;
import com.invoiceautomation.backend.repository.SupplyChainAlertRepository;
import com.invoiceautomation.backend.repository.SupplyChainOrderRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SupplyChainService {

    private final SupplyChainOrderRepository orderRepository;
    private final SlaRuleRepository slaRuleRepository;
    private final SupplyChainAlertRepository alertRepository;
    private final SupplyChainSlaCalculator supplyChainSlaCalculator;
    private final Clock clock;

    public SupplyChainService(
            SupplyChainOrderRepository orderRepository,
            SlaRuleRepository slaRuleRepository,
            SupplyChainAlertRepository alertRepository,
            SupplyChainSlaCalculator supplyChainSlaCalculator,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.slaRuleRepository = slaRuleRepository;
        this.alertRepository = alertRepository;
        this.supplyChainSlaCalculator = supplyChainSlaCalculator;
        this.clock = clock;
    }

    @Transactional
    public List<SupplyChainOrderSummaryResponse> getOrders(String search, OrderHealthStatus healthStatus) {
        List<SupplyChainSlaCalculator.TrackingSnapshot> snapshots = buildSnapshotsAndSyncAlerts();
        return snapshots.stream()
                .filter(snapshot -> matchesSearch(snapshot.order(), search))
                .filter(snapshot -> healthStatus == null || snapshot.healthStatus() == healthStatus)
                .sorted(Comparator.comparing(
                                (SupplyChainSlaCalculator.TrackingSnapshot snapshot) ->
                                        snapshot.order().getUpdatedAt())
                        .reversed())
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional
    public SupplyChainOrderDetailResponse getOrder(Long id) {
        SupplyChainSlaCalculator.TrackingSnapshot snapshot = buildSnapshotAndSyncAlerts(findOrder(id));
        return toDetailResponse(snapshot, getOpenAlerts(snapshot.order().getId()));
    }

    @Transactional
    public SupplyChainOrderDetailResponse createOrder(SupplyChainOrderRequest request) {
        validateOrderNumber(request.orderNumber(), null);

        SupplyChainOrder order = new SupplyChainOrder();
        applyOrderRequest(order, request);
        upsertMilestone(order, MilestoneType.PO_RECEIVED, request.poReceivedAt(), null);
        validateMilestoneFlow(order);

        SupplyChainOrder savedOrder = orderRepository.save(order);
        SupplyChainSlaCalculator.TrackingSnapshot snapshot = buildSnapshotAndSyncAlerts(savedOrder);
        return toDetailResponse(snapshot, getOpenAlerts(savedOrder.getId()));
    }

    @Transactional
    public SupplyChainOrderDetailResponse updateOrder(Long id, SupplyChainOrderRequest request) {
        SupplyChainOrder order = findOrder(id);
        validateOrderNumber(request.orderNumber(), id);

        applyOrderRequest(order, request);
        upsertMilestone(order, MilestoneType.PO_RECEIVED, request.poReceivedAt(), null);
        validateMilestoneFlow(order);

        SupplyChainOrder savedOrder = orderRepository.save(order);
        SupplyChainSlaCalculator.TrackingSnapshot snapshot = buildSnapshotAndSyncAlerts(savedOrder);
        return toDetailResponse(snapshot, getOpenAlerts(savedOrder.getId()));
    }

    @Transactional
    public SupplyChainOrderDetailResponse recordMilestone(
            Long orderId,
            MilestoneType milestoneType,
            MilestoneRecordRequest request) {
        SupplyChainOrder order = findOrder(orderId);
        if (!milestoneType.isFirst()) {
            ensurePreviousMilestoneExists(order, milestoneType);
        }

        upsertMilestone(order, milestoneType, request.occurredAt(), request.notes());
        validateMilestoneFlow(order);

        SupplyChainOrder savedOrder = orderRepository.save(order);
        SupplyChainSlaCalculator.TrackingSnapshot snapshot = buildSnapshotAndSyncAlerts(savedOrder);
        return toDetailResponse(snapshot, getOpenAlerts(savedOrder.getId()));
    }

    @Transactional
    public void deleteOrder(Long id) {
        SupplyChainOrder order = findOrder(id);
        alertRepository.deleteByOrderId(order.getId());
        orderRepository.delete(order);
    }

    @Transactional
    public List<AlertResponse> getAlerts(AlertStatus status) {
        buildSnapshotsAndSyncAlerts();
        AlertStatus targetStatus = status == null ? AlertStatus.OPEN : status;

        return alertRepository.findAllByStatusOrderByTriggeredAtDesc(targetStatus).stream()
                .map(this::toAlertResponse)
                .toList();
    }

    @Transactional
    public SupplyChainDashboardResponse getDashboard() {
        List<SupplyChainSlaCalculator.TrackingSnapshot> snapshots = buildSnapshotsAndSyncAlerts();

        long totalOrders = snapshots.size();
        long onTimeOrders = snapshots.stream().filter(snapshot -> snapshot.healthStatus() == OrderHealthStatus.ON_TIME).count();
        long atRiskOrders = snapshots.stream().filter(snapshot -> snapshot.healthStatus() == OrderHealthStatus.AT_RISK).count();
        long delayedOrders = snapshots.stream().filter(snapshot -> snapshot.healthStatus() == OrderHealthStatus.DELAYED).count();
        long deliveredOrders = snapshots.stream().filter(SupplyChainSlaCalculator.TrackingSnapshot::completed).count();
        long openAlerts = alertRepository.findAllByStatusOrderByTriggeredAtDesc(AlertStatus.OPEN).size();

        return new SupplyChainDashboardResponse(
                totalOrders,
                onTimeOrders,
                atRiskOrders,
                delayedOrders,
                deliveredOrders,
                openAlerts);
    }

    @Transactional
    public List<SlaRuleResponse> getSlaRules() {
        return slaRuleRepository.findAll().stream()
                .sorted(Comparator.comparing(SlaRule::getId))
                .map(this::toSlaRuleResponse)
                .toList();
    }

    @Transactional
    public SlaRuleResponse updateSlaRule(Long id, SlaRuleUpdateRequest request) {
        if (!Boolean.TRUE.equals(request.active())) {
            throw new IllegalArgumentException("Standard supply-chain SLA rules must remain active.");
        }

        SlaRule rule = slaRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SLA rule not found: " + id));
        rule.setTargetDays(request.targetDays());
        rule.setWarningDays(request.warningDays());
        rule.setActive(request.active());

        SlaRule savedRule = slaRuleRepository.save(rule);
        buildSnapshotsAndSyncAlerts();
        return toSlaRuleResponse(savedRule);
    }

    @Transactional
    public void initializeDefaultSlaRules() {
        if (slaRuleRepository.count() > 0) {
            return;
        }

        List<SlaRule> rules = List.of(
                buildRule(MilestoneType.PO_RECEIVED, MilestoneType.PRODUCTION_COMPLETED, 30, 3),
                buildRule(MilestoneType.PRODUCTION_COMPLETED, MilestoneType.SHIPPED, 5, 2),
                buildRule(MilestoneType.SHIPPED, MilestoneType.ARRIVED_PORT, 23, 3),
                buildRule(MilestoneType.ARRIVED_PORT, MilestoneType.CUSTOMS_CLEARED, 7, 2),
                buildRule(MilestoneType.CUSTOMS_CLEARED, MilestoneType.DELIVERED, 2, 1));

        slaRuleRepository.saveAll(rules);
    }

    @Transactional
    public void initializeSampleOrders() {
        if (orderRepository.count() > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock).withSecond(0).withNano(0);

        SupplyChainOrder onTimeOrder = sampleOrder(
                "SC-1001",
                "Atlas Telecom",
                "FiberHome Wuhan",
                "24-Core Fiber Cable",
                "China",
                "Mexico",
                "18000",
                "124500.00",
                "On-water shipment with no active exceptions.",
                now.minusDays(20));
        upsertMilestone(onTimeOrder, MilestoneType.PRODUCTION_COMPLETED, now.minusDays(2), "Production finished on schedule.");
        upsertMilestone(onTimeOrder, MilestoneType.SHIPPED, now, "Container departed Shanghai.");

        SupplyChainOrder delayedOrder = sampleOrder(
                "SC-1002",
                "MetroNet",
                "FiberHome Wuhan",
                "96-Core ADSS Cable",
                "China",
                "Mexico",
                "24000",
                "208900.00",
                "Production overran the original factory plan.",
                now.minusDays(52));
        upsertMilestone(delayedOrder, MilestoneType.PRODUCTION_COMPLETED, now.minusDays(18), "Factory completed production after escalation.");

        SupplyChainOrder atRiskOrder = sampleOrder(
                "SC-1003",
                "Total Box",
                "FiberHome Wuhan",
                "Outdoor Distribution Boxes",
                "China",
                "Mexico",
                "300",
                "14616.00",
                "Production is nearing the contractual SLA cutoff.",
                now.minusDays(28));

        SupplyChainOrder deliveredOrder = sampleOrder(
                "SC-1004",
                "Total Play",
                "FiberHome Wuhan",
                "ONU XPON Devices",
                "China",
                "Mexico",
                "8280",
                "403401.60",
                "Delivered order used as a clean reference timeline.",
                now.minusDays(80));
        upsertMilestone(deliveredOrder, MilestoneType.PRODUCTION_COMPLETED, now.minusDays(54), "Factory handoff completed.");
        upsertMilestone(deliveredOrder, MilestoneType.SHIPPED, now.minusDays(50), "Loaded on vessel.");
        upsertMilestone(deliveredOrder, MilestoneType.ARRIVED_PORT, now.minusDays(28), "Vessel arrived in Manzanillo.");
        upsertMilestone(deliveredOrder, MilestoneType.CUSTOMS_CLEARED, now.minusDays(22), "Customs release received.");
        upsertMilestone(deliveredOrder, MilestoneType.DELIVERED, now.minusDays(21), "Final mile delivery signed off.");

        List<SupplyChainOrder> savedOrders = orderRepository.saveAll(List.of(
                onTimeOrder,
                delayedOrder,
                atRiskOrder,
                deliveredOrder));
        savedOrders.forEach(this::buildSnapshotAndSyncAlerts);
    }

    private SupplyChainOrder sampleOrder(
            String orderNumber,
            String customerName,
            String supplierName,
            String productName,
            String originCountry,
            String destinationCountry,
            String quantity,
            String orderValue,
            String notes,
            LocalDateTime poReceivedAt) {
        SupplyChainOrder order = new SupplyChainOrder();
        order.setOrderNumber(orderNumber);
        order.setCustomerName(customerName);
        order.setSupplierName(supplierName);
        order.setProductName(productName);
        order.setOriginCountry(originCountry);
        order.setDestinationCountry(destinationCountry);
        order.setQuantity(new java.math.BigDecimal(quantity));
        order.setOrderValue(new java.math.BigDecimal(orderValue));
        order.setNotes(notes);
        upsertMilestone(order, MilestoneType.PO_RECEIVED, poReceivedAt, "Purchase order received.");
        return order;
    }

    private SlaRule buildRule(
            MilestoneType startMilestone,
            MilestoneType endMilestone,
            Integer targetDays,
            Integer warningDays) {
        SlaRule rule = new SlaRule();
        rule.setStartMilestone(startMilestone);
        rule.setEndMilestone(endMilestone);
        rule.setTargetDays(targetDays);
        rule.setWarningDays(warningDays);
        rule.setActive(true);
        return rule;
    }

    private List<SlaRule> getActiveRules() {
        List<SlaRule> rules = slaRuleRepository.findByActiveTrueOrderByIdAsc();
        if (rules.isEmpty()) {
            throw new IllegalStateException("Supply-chain SLA rules are not configured.");
        }
        return rules;
    }

    private List<SupplyChainSlaCalculator.TrackingSnapshot> buildSnapshotsAndSyncAlerts() {
        List<SupplyChainOrder> orders = orderRepository.findAllByOrderByUpdatedAtDesc();
        List<SlaRule> activeRules = getActiveRules();
        List<SupplyChainSlaCalculator.TrackingSnapshot> snapshots = orders.stream()
                .map(order -> supplyChainSlaCalculator.buildSnapshot(order, activeRules))
                .toList();
        syncAlerts(snapshots);
        return snapshots;
    }

    private SupplyChainSlaCalculator.TrackingSnapshot buildSnapshotAndSyncAlerts(SupplyChainOrder order) {
        SupplyChainSlaCalculator.TrackingSnapshot snapshot =
                supplyChainSlaCalculator.buildSnapshot(order, getActiveRules());
        syncAlerts(List.of(snapshot));
        return snapshot;
    }

    private void syncAlerts(List<SupplyChainSlaCalculator.TrackingSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }

        List<Long> orderIds = snapshots.stream().map(snapshot -> snapshot.order().getId()).toList();
        Map<Long, List<SupplyChainAlert>> existingAlertsByOrderId = alertRepository.findAllByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(alert -> alert.getOrder().getId()));

        LocalDateTime now = LocalDateTime.now(clock);
        List<SupplyChainAlert> alertsToPersist = new ArrayList<>();

        for (SupplyChainSlaCalculator.TrackingSnapshot snapshot : snapshots) {
            Map<AlertKey, SupplyChainAlert> openAlertsByKey = new HashMap<>();
            List<SupplyChainAlert> existingAlerts = existingAlertsByOrderId.getOrDefault(snapshot.order().getId(), List.of());
            for (SupplyChainAlert alert : existingAlerts) {
                if (alert.getStatus() == AlertStatus.OPEN) {
                    openAlertsByKey.put(new AlertKey(alert.getMilestoneType(), alert.getAlertType()), alert);
                }
            }

            Set<AlertKey> desiredKeys = snapshot.activeAlerts().stream()
                    .map(candidate -> new AlertKey(candidate.milestoneType(), candidate.alertType()))
                    .collect(Collectors.toSet());

            for (SupplyChainSlaCalculator.AlertCandidate candidate : snapshot.activeAlerts()) {
                AlertKey key = new AlertKey(candidate.milestoneType(), candidate.alertType());
                SupplyChainAlert existingOpenAlert = openAlertsByKey.remove(key);
                if (existingOpenAlert == null) {
                    SupplyChainAlert alert = new SupplyChainAlert();
                    alert.setOrder(snapshot.order());
                    alert.setMilestoneType(candidate.milestoneType());
                    alert.setAlertType(candidate.alertType());
                    alert.setSeverity(candidate.severity());
                    alert.setStatus(AlertStatus.OPEN);
                    alert.setTitle(candidate.title());
                    alert.setMessage(candidate.message());
                    alert.setExpectedAt(candidate.expectedAt());
                    alert.setTriggeredAt(now);
                    alert.setResolvedAt(null);
                    alertsToPersist.add(alert);
                } else {
                    existingOpenAlert.setSeverity(candidate.severity());
                    existingOpenAlert.setTitle(candidate.title());
                    existingOpenAlert.setMessage(candidate.message());
                    existingOpenAlert.setExpectedAt(candidate.expectedAt());
                    existingOpenAlert.setResolvedAt(null);
                    alertsToPersist.add(existingOpenAlert);
                }
            }

            for (SupplyChainAlert staleAlert : openAlertsByKey.values()) {
                if (!desiredKeys.contains(new AlertKey(staleAlert.getMilestoneType(), staleAlert.getAlertType()))) {
                    staleAlert.setStatus(AlertStatus.RESOLVED);
                    staleAlert.setResolvedAt(now);
                    alertsToPersist.add(staleAlert);
                }
            }
        }

        if (!alertsToPersist.isEmpty()) {
            alertRepository.saveAll(alertsToPersist);
        }
    }

    private SupplyChainOrder findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    private void applyOrderRequest(SupplyChainOrder order, SupplyChainOrderRequest request) {
        order.setOrderNumber(normalizeRequired(request.orderNumber(), "Order number"));
        order.setCustomerName(normalizeRequired(request.customerName(), "Customer name"));
        order.setSupplierName(normalizeRequired(request.supplierName(), "Supplier name"));
        order.setProductName(normalizeRequired(request.productName(), "Product name"));
        order.setOriginCountry(normalizeRequired(request.originCountry(), "Origin country"));
        order.setDestinationCountry(normalizeRequired(request.destinationCountry(), "Destination country"));
        order.setQuantity(request.quantity());
        order.setOrderValue(request.orderValue() == null ? BigDecimal.ZERO : request.orderValue());
        order.setNotes(normalizeNullable(request.notes()));
    }

    private void upsertMilestone(
            SupplyChainOrder order,
            MilestoneType milestoneType,
            LocalDateTime occurredAt,
            String notes) {
        OrderMilestone milestone = order.getMilestones().stream()
                .filter(candidate -> candidate.getMilestoneType() == milestoneType)
                .findFirst()
                .orElse(null);

        if (milestone == null) {
            milestone = new OrderMilestone();
            milestone.setMilestoneType(milestoneType);
            milestone.setOccurredAt(occurredAt);
            milestone.setNotes(normalizeNullable(notes));
            order.addMilestone(milestone);
            return;
        }

        milestone.setOccurredAt(occurredAt);
        if (notes != null) {
            milestone.setNotes(normalizeNullable(notes));
        }
    }

    private void ensurePreviousMilestoneExists(SupplyChainOrder order, MilestoneType milestoneType) {
        MilestoneType previousMilestone = milestoneType.previous();
        boolean previousExists = order.getMilestones().stream()
                .anyMatch(candidate -> candidate.getMilestoneType() == previousMilestone);
        if (!previousExists) {
            throw new IllegalArgumentException(
                    "Record " + previousMilestone.displayName() + " before " + milestoneType.displayName() + ".");
        }
    }

    private void validateMilestoneFlow(SupplyChainOrder order) {
        Map<MilestoneType, OrderMilestone> milestonesByType = new EnumMap<>(MilestoneType.class);
        order.getMilestones().forEach(milestone -> milestonesByType.put(milestone.getMilestoneType(), milestone));

        LocalDateTime previousOccurredAt = null;
        boolean gapFound = false;

        for (MilestoneType milestoneType : MilestoneType.flow()) {
            OrderMilestone milestone = milestonesByType.get(milestoneType);
            if (milestone == null) {
                gapFound = true;
                continue;
            }

            if (gapFound) {
                throw new IllegalArgumentException(
                        milestoneType.displayName() + " cannot be recorded before prior milestones.");
            }

            if (previousOccurredAt != null && milestone.getOccurredAt().isBefore(previousOccurredAt)) {
                throw new IllegalArgumentException(
                        milestoneType.displayName() + " must be on or after the previous milestone timestamp.");
            }

            previousOccurredAt = milestone.getOccurredAt();
        }
    }

    private void validateOrderNumber(String orderNumber, Long existingOrderId) {
        String normalizedOrderNumber = normalizeRequired(orderNumber, "Order number");
        boolean exists = existingOrderId == null
                ? orderRepository.existsByOrderNumberIgnoreCase(normalizedOrderNumber)
                : orderRepository.existsByOrderNumberIgnoreCaseAndIdNot(normalizedOrderNumber, existingOrderId);
        if (exists) {
            throw new IllegalArgumentException("An order with the same order number already exists.");
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean matchesSearch(SupplyChainOrder order, String search) {
        String normalizedSearch = normalizeNullable(search);
        if (normalizedSearch == null) {
            return true;
        }

        String haystack = String.join(
                        " ",
                        order.getOrderNumber(),
                        order.getCustomerName(),
                        order.getSupplierName(),
                        order.getProductName(),
                        order.getOriginCountry(),
                        order.getDestinationCountry())
                .toLowerCase(Locale.ROOT);
        return haystack.contains(normalizedSearch.toLowerCase(Locale.ROOT));
    }

    private SupplyChainOrderSummaryResponse toSummaryResponse(SupplyChainSlaCalculator.TrackingSnapshot snapshot) {
        SupplyChainOrder order = snapshot.order();
        return new SupplyChainOrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getSupplierName(),
                order.getProductName(),
                order.getOriginCountry(),
                order.getDestinationCountry(),
                order.getQuantity(),
                order.getOrderValue(),
                snapshot.currentMilestone(),
                label(snapshot.currentMilestone()),
                snapshot.nextMilestone(),
                label(snapshot.nextMilestone()),
                snapshot.nextExpectedAt(),
                snapshot.healthStatus(),
                snapshot.completed(),
                snapshot.openAlertCount(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private SupplyChainOrderDetailResponse toDetailResponse(
            SupplyChainSlaCalculator.TrackingSnapshot snapshot,
            List<AlertResponse> alerts) {
        SupplyChainOrder order = snapshot.order();
        List<OrderMilestoneResponse> timeline = snapshot.timeline().stream()
                .map(entry -> new OrderMilestoneResponse(
                        entry.milestoneType(),
                        entry.milestoneType().displayName(),
                        entry.actualAt(),
                        entry.expectedAt(),
                        entry.targetDays(),
                        entry.warningDays(),
                        entry.slaStatus(),
                        entry.completed(),
                        entry.breached(),
                        entry.atRisk(),
                        entry.notes()))
                .toList();

        return new SupplyChainOrderDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getSupplierName(),
                order.getProductName(),
                order.getOriginCountry(),
                order.getDestinationCountry(),
                order.getQuantity(),
                order.getOrderValue(),
                order.getNotes(),
                snapshot.currentMilestone(),
                label(snapshot.currentMilestone()),
                snapshot.nextMilestone(),
                label(snapshot.nextMilestone()),
                snapshot.nextExpectedAt(),
                snapshot.healthStatus(),
                snapshot.completed(),
                snapshot.openAlertCount(),
                timeline,
                alerts,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private List<AlertResponse> getOpenAlerts(Long orderId) {
        return alertRepository.findAllByOrderIdOrderByTriggeredAtDesc(orderId).stream()
                .filter(alert -> alert.getStatus() == AlertStatus.OPEN)
                .map(this::toAlertResponse)
                .toList();
    }

    private AlertResponse toAlertResponse(SupplyChainAlert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getOrder().getId(),
                alert.getOrder().getOrderNumber(),
                alert.getOrder().getCustomerName(),
                alert.getMilestoneType(),
                alert.getMilestoneType().displayName(),
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getTitle(),
                alert.getMessage(),
                alert.getExpectedAt(),
                alert.getTriggeredAt(),
                alert.getResolvedAt());
    }

    private SlaRuleResponse toSlaRuleResponse(SlaRule rule) {
        return new SlaRuleResponse(
                rule.getId(),
                rule.getStartMilestone(),
                rule.getStartMilestone().displayName(),
                rule.getEndMilestone(),
                rule.getEndMilestone().displayName(),
                rule.getTargetDays(),
                rule.getWarningDays(),
                rule.isActive(),
                rule.getUpdatedAt());
    }

    private String label(MilestoneType milestoneType) {
        return milestoneType == null ? null : milestoneType.displayName();
    }

    private record AlertKey(MilestoneType milestoneType, AlertType alertType) {
    }
}
