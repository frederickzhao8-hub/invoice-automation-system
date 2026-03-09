package com.invoiceautomation.backend.dto;

import com.invoiceautomation.backend.entity.InvoiceStatus;
import jakarta.validation.constraints.NotNull;

public class InvoiceStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private InvoiceStatus status;

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }
}

