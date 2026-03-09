package com.invoiceautomation.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceautomation.backend.config.InvoiceLlmProperties;
import com.invoiceautomation.backend.dto.InvoiceExtractionResult;
import com.invoiceautomation.backend.entity.InvoiceParseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvoiceExtractionServiceTest {

    @Test
    void normalizesLlmJsonPayload() {
        InvoiceLlmProperties properties = new InvoiceLlmProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setModel("test-model");

        InvoiceExtractionService extractionService = new InvoiceExtractionService(
                new ObjectMapper(),
                prompt -> """
                        {
                          "vendor_name": "  Amazon Mexico  ",
                          "invoice_number": " inv-1001 ",
                          "invoice_date": "2026-03-01",
                          "total_amount": "$1,234.50",
                          "currency": "usd",
                          "tax_amount": "197.52",
                          "due_date": "2026/03/31",
                          "payment_terms": " Net 30 ",
                          "invoice_description": " Cloud hosting "
                        }
                        """,
                properties);

        InvoiceExtractionResult result = extractionService.extractInvoiceData("invoice text", null);

        assertEquals("Amazon Mexico", result.getVendorName());
        assertEquals("inv-1001", result.getInvoiceNumber());
        assertEquals(LocalDate.of(2026, 3, 1), result.getInvoiceDate());
        assertEquals(new BigDecimal("1234.50"), result.getTotalAmount());
        assertEquals("USD", result.getCurrency());
        assertEquals(new BigDecimal("197.52"), result.getTaxAmount());
        assertEquals(LocalDate.of(2026, 3, 31), result.getDueDate());
        assertEquals("Net 30", result.getPaymentTerms());
        assertEquals("Cloud hosting", result.getInvoiceDescription());
        assertTrue(Boolean.TRUE.equals(result.getExtractionSucceeded()));
        assertNull(result.getExtractionError());
    }

    @Test
    void fallsBackToHeuristicParsingWhenLlmIsDisabled() {
        InvoiceLlmProperties properties = new InvoiceLlmProperties();
        properties.setEnabled(false);

        ParsedInvoiceData fallbackData = new ParsedInvoiceData(
                "CFDI-1001",
                "Fallback Vendor",
                null,
                null,
                null,
                new BigDecimal("16.00"),
                new BigDecimal("116.00"),
                "mxn",
                LocalDate.of(2026, 3, 1),
                InvoiceParseStatus.SUCCESS,
                BigDecimal.ONE,
                "raw text",
                false);

        InvoiceExtractionService extractionService = new InvoiceExtractionService(
                new ObjectMapper(),
                prompt -> {
                    throw new AssertionError("LLM client should not be called when disabled");
                },
                properties);

        InvoiceExtractionResult result = extractionService.extractInvoiceData("invoice text", fallbackData);

        assertEquals("Fallback Vendor", result.getVendorName());
        assertEquals("CFDI-1001", result.getInvoiceNumber());
        assertEquals(LocalDate.of(2026, 3, 1), result.getInvoiceDate());
        assertEquals(new BigDecimal("116.00"), result.getTotalAmount());
        assertEquals("MXN", result.getCurrency());
        assertEquals(new BigDecimal("16.00"), result.getTaxAmount());
        assertTrue(Boolean.TRUE.equals(result.getExtractionSucceeded()));
        assertNull(result.getExtractionError());
    }
}
