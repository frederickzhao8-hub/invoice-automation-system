package com.invoiceautomation.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class InvoiceExtractionResult {

    private String vendorName;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal totalAmount;
    private String currency;
    private BigDecimal taxAmount;
    private LocalDate dueDate;
    private String paymentTerms;
    private String invoiceDescription;
    private String rawExtractedText;
    private Boolean extractionSucceeded;
    private String extractionError;

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
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

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public String getInvoiceDescription() {
        return invoiceDescription;
    }

    public void setInvoiceDescription(String invoiceDescription) {
        this.invoiceDescription = invoiceDescription;
    }

    public String getRawExtractedText() {
        return rawExtractedText;
    }

    public void setRawExtractedText(String rawExtractedText) {
        this.rawExtractedText = rawExtractedText;
    }

    public Boolean getExtractionSucceeded() {
        return extractionSucceeded;
    }

    public void setExtractionSucceeded(Boolean extractionSucceeded) {
        this.extractionSucceeded = extractionSucceeded;
    }

    public String getExtractionError() {
        return extractionError;
    }

    public void setExtractionError(String extractionError) {
        this.extractionError = extractionError;
    }

    public boolean hasAnyStructuredData() {
        return vendorName != null
                || invoiceNumber != null
                || invoiceDate != null
                || totalAmount != null
                || currency != null
                || taxAmount != null
                || dueDate != null
                || paymentTerms != null
                || invoiceDescription != null;
    }

    public boolean hasCriticalFields() {
        return vendorName != null
                && invoiceNumber != null
                && invoiceDate != null
                && totalAmount != null
                && currency != null;
    }

    public int populatedFieldCount() {
        int count = 0;
        count += vendorName != null ? 1 : 0;
        count += invoiceNumber != null ? 1 : 0;
        count += invoiceDate != null ? 1 : 0;
        count += totalAmount != null ? 1 : 0;
        count += currency != null ? 1 : 0;
        count += taxAmount != null ? 1 : 0;
        count += dueDate != null ? 1 : 0;
        count += paymentTerms != null ? 1 : 0;
        count += invoiceDescription != null ? 1 : 0;
        return count;
    }

    @Override
    public String toString() {
        return "InvoiceExtractionResult{"
                + "vendorName='" + vendorName + '\''
                + ", invoiceNumber='" + invoiceNumber + '\''
                + ", invoiceDate=" + invoiceDate
                + ", totalAmount=" + totalAmount
                + ", currency='" + currency + '\''
                + ", taxAmount=" + taxAmount
                + ", dueDate=" + dueDate
                + ", extractionSucceeded=" + extractionSucceeded
                + ", extractionError='" + extractionError + '\''
                + '}';
    }
}
