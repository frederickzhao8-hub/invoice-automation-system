package com.invoiceautomation.backend.entity;

import java.util.List;

public enum MilestoneType {
    PO_RECEIVED,
    PRODUCTION_COMPLETED,
    SHIPPED,
    ARRIVED_PORT,
    CUSTOMS_CLEARED,
    DELIVERED;

    private static final List<MilestoneType> FLOW = List.of(values());

    public static List<MilestoneType> flow() {
        return FLOW;
    }

    public boolean isFirst() {
        return this == PO_RECEIVED;
    }

    public MilestoneType previous() {
        int index = FLOW.indexOf(this);
        return index <= 0 ? null : FLOW.get(index - 1);
    }

    public MilestoneType next() {
        int index = FLOW.indexOf(this);
        return index < 0 || index >= FLOW.size() - 1 ? null : FLOW.get(index + 1);
    }

    public String displayName() {
        return switch (this) {
            case PO_RECEIVED -> "PO Received";
            case PRODUCTION_COMPLETED -> "Production Completed";
            case SHIPPED -> "Shipped";
            case ARRIVED_PORT -> "Arrived at Port";
            case CUSTOMS_CLEARED -> "Customs Cleared";
            case DELIVERED -> "Delivered";
        };
    }
}
