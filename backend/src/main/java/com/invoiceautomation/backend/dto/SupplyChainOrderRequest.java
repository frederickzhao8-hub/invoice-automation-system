package com.invoiceautomation.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SupplyChainOrderRequest(
        @NotBlank(message = "Order number is required.") String orderNumber,
        @NotBlank(message = "Customer name is required.") String customerName,
        @NotBlank(message = "Supplier name is required.") String supplierName,
        @NotBlank(message = "Product name is required.") String productName,
        @NotBlank(message = "Origin country is required.") String originCountry,
        @NotBlank(message = "Destination country is required.") String destinationCountry,
        @NotNull(message = "Quantity is required.") @Positive(message = "Quantity must be greater than zero.")
                BigDecimal quantity,
        @NotNull(message = "Order value is required.") @Positive(message = "Order value must be greater than zero.")
                BigDecimal orderValue,
        String notes,
        @NotNull(message = "PO received timestamp is required.") LocalDateTime poReceivedAt) {
}
