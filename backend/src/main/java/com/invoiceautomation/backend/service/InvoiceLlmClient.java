package com.invoiceautomation.backend.service;

public interface InvoiceLlmClient {

    String extractStructuredInvoiceJson(String prompt);
}
