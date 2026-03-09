package com.invoiceautomation.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceautomation.backend.config.InvoiceLlmProperties;
import com.invoiceautomation.backend.dto.InvoiceExtractionResult;
import com.invoiceautomation.backend.util.InvoiceFieldNormalizer;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InvoiceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceExtractionService.class);
    private static final String EXTRACTION_PROMPT = """
            You are an invoice extraction system.

            Extract structured invoice information from the text below.

            Return ONLY valid JSON with the following fields:
            vendor_name
            invoice_number
            invoice_date
            total_amount
            currency
            tax_amount
            due_date
            payment_terms
            invoice_description

            Rules:
            - If a field is missing, return null.
            - invoice_date and due_date must use ISO format: YYYY-MM-DD when possible.
            - total_amount and tax_amount must be numeric values when possible.
            - Do not include markdown.
            - Do not include explanations.
            - Return JSON only.

            TEXT:
            %s
            """;

    private final ObjectMapper objectMapper;
    private final InvoiceLlmClient invoiceLlmClient;
    private final InvoiceLlmProperties properties;

    public InvoiceExtractionService(
            ObjectMapper objectMapper,
            InvoiceLlmClient invoiceLlmClient,
            InvoiceLlmProperties properties) {
        this.objectMapper = objectMapper;
        this.invoiceLlmClient = invoiceLlmClient;
        this.properties = properties;
    }

    public InvoiceExtractionResult extractInvoiceData(String rawText, ParsedInvoiceData fallbackData) {
        InvoiceExtractionResult fallbackResult = buildFallbackResult(rawText, fallbackData);
        if (rawText == null || rawText.isBlank()) {
            fallbackResult.setExtractionSucceeded(false);
            fallbackResult.setExtractionError("No extractable PDF text was found.");
            return fallbackResult;
        }

        if (!properties.isEnabled()) {
            fallbackResult.setExtractionSucceeded(fallbackResult.hasAnyStructuredData());
            if (!fallbackResult.hasAnyStructuredData()) {
                fallbackResult.setExtractionError("LLM extraction is disabled and fallback parsing found no structured data.");
            }
            log.info("LLM extraction is disabled. Using heuristic fallback extraction only.");
            return fallbackResult;
        }

        try {
            String llmResponse = invoiceLlmClient.extractStructuredInvoiceJson(buildPrompt(rawText));
            InvoiceExtractionResult llmResult = parseLlmResponse(llmResponse, rawText);
            InvoiceExtractionResult mergedResult = mergeWithFallback(llmResult, fallbackResult);
            mergedResult.setExtractionSucceeded(mergedResult.hasAnyStructuredData());
            if (!mergedResult.hasAnyStructuredData()) {
                mergedResult.setExtractionError("Unable to parse invoice data.");
            }
            log.info("Extracted invoice data: {}", mergedResult);
            return mergedResult;
        } catch (Exception exception) {
            log.warn("LLM extraction failed. Falling back to heuristic extraction.", exception);
            fallbackResult.setExtractionSucceeded(fallbackResult.hasAnyStructuredData());
            if (!fallbackResult.hasAnyStructuredData()) {
                fallbackResult.setExtractionError("LLM extraction failed and fallback parsing found no structured data.");
            }
            return fallbackResult;
        }
    }

    private String buildPrompt(String rawText) {
        return EXTRACTION_PROMPT.formatted(rawText);
    }

    private InvoiceExtractionResult parseLlmResponse(String llmResponse, String rawText) throws Exception {
        String jsonPayload = InvoiceFieldNormalizer.extractJsonObject(llmResponse);
        JsonNode rootNode = objectMapper.readTree(jsonPayload);

        InvoiceExtractionResult result = new InvoiceExtractionResult();
        result.setVendorName(InvoiceFieldNormalizer.normalizeVendorName(readRawText(rootNode, "vendor_name")));
        result.setInvoiceNumber(readNormalizedText(rootNode, "invoice_number"));
        result.setInvoiceDate(readNormalizedDate(rootNode, "invoice_date"));
        result.setTotalAmount(readNormalizedDecimal(rootNode, "total_amount"));
        result.setCurrency(InvoiceFieldNormalizer.normalizeCurrency(readRawText(rootNode, "currency")));
        result.setTaxAmount(readNormalizedDecimal(rootNode, "tax_amount"));
        result.setDueDate(readNormalizedDate(rootNode, "due_date"));
        result.setPaymentTerms(readNormalizedText(rootNode, "payment_terms"));
        result.setInvoiceDescription(readNormalizedText(rootNode, "invoice_description"));
        result.setRawExtractedText(rawText);
        result.setExtractionSucceeded(result.hasAnyStructuredData());
        return result;
    }

    private InvoiceExtractionResult buildFallbackResult(String rawText, ParsedInvoiceData fallbackData) {
        InvoiceExtractionResult result = new InvoiceExtractionResult();
        result.setRawExtractedText(rawText);

        if (fallbackData == null) {
            result.setExtractionSucceeded(false);
            return result;
        }

        result.setVendorName(InvoiceFieldNormalizer.normalizeVendorName(fallbackData.vendorName()));
        result.setInvoiceNumber(InvoiceFieldNormalizer.normalizeText(fallbackData.invoiceNumber()));
        result.setInvoiceDate(fallbackData.issueDate());
        result.setTotalAmount(fallbackData.totalAmount());
        result.setCurrency(InvoiceFieldNormalizer.normalizeCurrency(fallbackData.currency()));
        result.setTaxAmount(fallbackData.taxAmount());
        result.setExtractionSucceeded(result.hasAnyStructuredData());
        return result;
    }

    private InvoiceExtractionResult mergeWithFallback(
            InvoiceExtractionResult primary,
            InvoiceExtractionResult fallback) {
        if (primary == null) {
            return fallback;
        }

        if (primary.getVendorName() == null) {
            primary.setVendorName(fallback.getVendorName());
        }
        if (primary.getInvoiceNumber() == null) {
            primary.setInvoiceNumber(fallback.getInvoiceNumber());
        }
        if (primary.getInvoiceDate() == null) {
            primary.setInvoiceDate(fallback.getInvoiceDate());
        }
        if (primary.getTotalAmount() == null) {
            primary.setTotalAmount(fallback.getTotalAmount());
        }
        if (primary.getCurrency() == null) {
            primary.setCurrency(fallback.getCurrency());
        }
        if (primary.getTaxAmount() == null) {
            primary.setTaxAmount(fallback.getTaxAmount());
        }
        if (primary.getDueDate() == null) {
            primary.setDueDate(fallback.getDueDate());
        }
        if (primary.getPaymentTerms() == null) {
            primary.setPaymentTerms(fallback.getPaymentTerms());
        }
        if (primary.getInvoiceDescription() == null) {
            primary.setInvoiceDescription(fallback.getInvoiceDescription());
        }
        if (primary.getRawExtractedText() == null) {
            primary.setRawExtractedText(fallback.getRawExtractedText());
        }

        return primary;
    }

    private String readRawText(JsonNode rootNode, String fieldName) {
        JsonNode fieldNode = rootNode.path(fieldName);
        return fieldNode.isMissingNode() || fieldNode.isNull() ? null : fieldNode.asText(null);
    }

    private String readNormalizedText(JsonNode rootNode, String fieldName) {
        return InvoiceFieldNormalizer.normalizeText(readRawText(rootNode, fieldName));
    }

    private LocalDate readNormalizedDate(JsonNode rootNode, String fieldName) {
        return InvoiceFieldNormalizer.normalizeDate(readRawText(rootNode, fieldName));
    }

    private java.math.BigDecimal readNormalizedDecimal(JsonNode rootNode, String fieldName) {
        return InvoiceFieldNormalizer.normalizeDecimal(readRawText(rootNode, fieldName));
    }
}
