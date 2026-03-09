package com.invoiceautomation.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public class InvoiceReviewUpdateRequest {

    private String invoiceNumber;
    private String vendorName;

    @DecimalMin(value = "0.00", inclusive = true, message = "Quantity must be positive")
    private BigDecimal quantity;

    @DecimalMin(value = "0.00", inclusive = true, message = "Unit price must be positive")
    private BigDecimal unitPrice;

    @DecimalMin(value = "0.00", inclusive = true, message = "Subtotal must be positive")
    private BigDecimal subtotalAmount;

    @DecimalMin(value = "0.00", inclusive = true, message = "Tax must be positive")
    private BigDecimal taxAmount;

    @DecimalMin(value = "0.00", inclusive = true, message = "Total must be positive")
    private BigDecimal totalAmount;

    private String currency;
    private String issueDate;

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public void setSubtotalAmount(BigDecimal subtotalAmount) {
        this.subtotalAmount = subtotalAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(String issueDate) {
        this.issueDate = issueDate;
    }
}
