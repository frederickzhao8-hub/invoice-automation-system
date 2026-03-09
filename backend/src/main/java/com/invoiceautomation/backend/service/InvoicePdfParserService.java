package com.invoiceautomation.backend.service;

import com.invoiceautomation.backend.entity.InvoiceParseStatus;
import com.invoiceautomation.backend.util.InvoiceFieldNormalizer;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InvoicePdfParserService {

    private static final Set<String> PDF_CONTENT_TYPES = Set.of("application/pdf");
    private static final String DECIMAL_VALUE_PATTERN = "([0-9]+(?:\\.[0-9]{1,6})?)";
    private static final String AMOUNT_VALUE_PATTERN =
            "((?:[$€£¥]|MX\\$|[A-Z]{3})?\\s*[0-9][0-9,]*(?:\\.[0-9]{1,6})?)";

    private static final List<Pattern> INVOICE_NUMBER_PATTERNS = List.of(
            Pattern.compile("(?im)\\binvoice\\s*(?:number|no\\.?|#)?\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9\\-/]{2,})"),
            Pattern.compile("(?im)\\binv\\s*#\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9\\-/]{2,})"),
            Pattern.compile("(?im)\\bfactura\\s*(?:n[uú]mero|no\\.?|#)?\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9_./\\-]{2,})"),
            Pattern.compile("(?im)\\b(?:folio|serie\\s+y\\s+folio)\\b\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9_./\\-]{2,})"),
            Pattern.compile("(?im)^(?:ingreso|egreso|traslado|factura)\\s+([A-Z0-9][A-Z0-9_./\\-]{2,})\\s*$"));

    private static final List<Pattern> VENDOR_PATTERNS = List.of(
            Pattern.compile("(?im)\\b(?:vendor|supplier|seller|bill\\s+from|from|proveedor|emisor|raz[oó]n\\s+social)\\b\\s*[:\\-]\\s*(.+)$"));

    private static final List<Pattern> QUANTITY_PATTERNS = List.of(
            Pattern.compile("(?im)\\b(?:quantity|qty|cantidad)\\b\\s*[:\\-]?\\s*" + DECIMAL_VALUE_PATTERN));

    private static final List<Pattern> UNIT_PRICE_PATTERNS = List.of(
            Pattern.compile("(?im)\\b(?:unit\\s*price|price\\s*per\\s*unit|rate|valor\\s+unitario|precio\\s+unitario)\\b\\s*[:\\-]?\\s*"
                    + AMOUNT_VALUE_PATTERN));

    private static final List<Pattern> SUBTOTAL_PATTERNS = List.of(
            Pattern.compile("(?im)\\b(?:subtotal|sub\\s*total)\\b\\s*[:\\-]?\\s*" + AMOUNT_VALUE_PATTERN));

    private static final List<Pattern> TAX_PATTERNS = List.of(
            Pattern.compile("(?im)\\biva\\b[^\\n]{0,200}?\\bimporte\\b\\s*[:\\-]?\\s*" + AMOUNT_VALUE_PATTERN),
            Pattern.compile("(?im)\\b(?:impuesto\\s+trasladado|impuesto(?:\\s+al\\s+valor\\s+agregado)?)\\b[^\\n]{0,200}?\\bimporte\\b\\s*[:\\-]?\\s*"
                    + AMOUNT_VALUE_PATTERN),
            Pattern.compile("(?im)" + AMOUNT_VALUE_PATTERN + "\\s*iva\\b(?:\\s*\\d{1,2}(?:\\.\\d{1,2})?\\s*%)?"),
            Pattern.compile("(?im)\\b(?:tax|vat|gst|sales\\s*tax|iva)\\b(?:\\s*\\d{1,2}(?:\\.\\d{1,2})?%?)?\\s*[:\\-]?\\s*"
                    + AMOUNT_VALUE_PATTERN));

    private static final List<Pattern> TOTAL_PATTERNS = List.of(
            Pattern.compile("(?im)\\b(?:grand\\s*total|total\\s*amount|amount\\s*due|importe\\s+total)\\b\\s*[:\\-]?\\s*"
                    + AMOUNT_VALUE_PATTERN),
            Pattern.compile("(?im)^\\s*total\\b\\s*[:\\-]?\\s*" + AMOUNT_VALUE_PATTERN + "\\s*$"),
            Pattern.compile("(?im)" + AMOUNT_VALUE_PATTERN + "\\s*total\\b"));

    private static final List<Pattern> CURRENCY_PATTERNS = List.of(
            Pattern.compile("(?im)\\b(?:currency|moneda)(?:\\s*/\\s*tipo\\s+de\\s+cambio)?\\b\\s*[:\\-]?\\s*(MXN|USD|EUR|GBP|JPY|M\\.N\\.)"),
            Pattern.compile("(?im)\\b(?:total|subtotal|amount\\s*due|unit\\s*price|valor\\s+unitario)\\b.*?\\b(MXN|USD|EUR|GBP|JPY)\\b"));

    private static final List<Pattern> ISSUE_DATE_PATTERNS = List.of(
            Pattern.compile("(?im)\\b(?:issue\\s*date|invoice\\s*date|date|fecha(?:\\s+de\\s+emisi[oó]n)?)\\b\\s*[:\\-]?\\s*([A-Za-z0-9,./\\- ]{6,24})"));

    private static final Pattern DATE_TOKEN_PATTERN =
            Pattern.compile("\\b(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|[A-Za-z]{3,9}\\s+\\d{1,2},\\s*\\d{4}|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4})\\b");

    private static final Pattern MONEY_TOKEN_PATTERN =
            Pattern.compile("([$€£¥]|USD|EUR|GBP|JPY|MXN|MX\\$)?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2,6})|[0-9]+(?:\\.[0-9]{2,6}))");

    private static final Pattern RFC_PATTERN =
            Pattern.compile("^[A-Z&Ñ]{3,4}\\d{6}[A-Z0-9]{3}$");

    private static final Pattern LEADING_QUANTITY_PATTERN =
            Pattern.compile("^([0-9][0-9,]*(?:\\.[0-9]{1,6})?)\\b");

    private static final Pattern LABELED_CURRENCY_PATTERN =
            Pattern.compile("(?i)(?:[$€£¥]|MX\\$|USD|EUR|GBP|JPY|MXN)\\s*[0-9][0-9,]*(?:\\.[0-9]{1,6})?");

    private static final Set<String> CFDI_LINE_ITEM_STOP_PREFIXES = Set.of(
            "impuesto trasladado",
            "información aduanera",
            "informacion aduanera",
            "subtotal",
            "forma de pago",
            "método de pago",
            "metodo de pago");

    private static final Set<String> CFDI_CUSTOMER_SECTION_MARKERS = Set.of(
            "uso de cfdi",
            "uso del cfdi",
            "receptor");

    private static final Set<String> COMPANY_NAME_SKIP_PREFIXES = Set.of(
            "uso de cfdi",
            "fecha",
            "domicilio fiscal",
            "lugar de emisión",
            "lugar de emision",
            "versión",
            "version",
            "régimen fiscal",
            "regimen fiscal",
            "uuid",
            "orden de compra",
            "nota de recepción",
            "nota de recepcion",
            "cantidad",
            "subtotal",
            "forma de pago",
            "método de pago",
            "metodo de pago",
            "sello digital",
            "cadena original",
            "no. serie",
            "este documento");

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/uuuu"),
            DateTimeFormatter.ofPattern("MM/dd/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("M-d-uuuu"),
            DateTimeFormatter.ofPattern("MM-dd-uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US),
            DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.US),
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.US));

    public ParsedInvoiceData parse(MultipartFile file) {
        validatePdf(file);
        return parseText(extractRawText(file));
    }

    String extractPreferredCustomerName(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        String normalizedText = rawText.replace('\r', '\n');
        List<String> lines = normalizedText.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();

        String priorityCustomerName = extractPriorityCustomerName(lines);
        if (priorityCustomerName != null) {
            return priorityCustomerName;
        }

        return InvoiceFieldNormalizer.normalizeVendorName(extractCfdiCustomerName(lines));
    }

    ParsedInvoiceData parseText(String rawText) {
        if (rawText.isBlank()) {
            return new ParsedInvoiceData(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    InvoiceParseStatus.FAILED,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    rawText,
                    true);
        }

        String normalizedText = rawText.replace('\r', '\n');
        List<String> lines = normalizedText.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();

        CfdiLineItemData cfdiLineItemData = extractCfdiLineItemData(lines);
        String invoiceNumber = extractFirst(normalizedText, INVOICE_NUMBER_PATTERNS);
        String vendorName = InvoiceFieldNormalizer.normalizeVendorName(extractVendorName(normalizedText, lines));
        BigDecimal quantity = extractDecimal(normalizedText, QUANTITY_PATTERNS);
        if (quantity == null && cfdiLineItemData != null) {
            quantity = cfdiLineItemData.quantity();
        }

        BigDecimal unitPrice = extractAmount(normalizedText, UNIT_PRICE_PATTERNS);
        if (unitPrice == null && cfdiLineItemData != null) {
            unitPrice = cfdiLineItemData.unitPrice();
        }

        BigDecimal subtotalAmount = extractAmount(normalizedText, SUBTOTAL_PATTERNS);
        if (subtotalAmount == null && cfdiLineItemData != null) {
            subtotalAmount = cfdiLineItemData.subtotalAmount();
        }

        BigDecimal taxAmount = extractTaxAmount(normalizedText, lines);
        BigDecimal totalAmount = extractAmount(normalizedText, TOTAL_PATTERNS);
        String currency = extractCurrency(normalizedText);
        LocalDate issueDate = extractDate(normalizedText);

        if (totalAmount == null) {
            totalAmount = extractLargestAmount(normalizedText);
        }

        if (subtotalAmount == null && totalAmount != null && taxAmount != null) {
            BigDecimal derivedSubtotal = totalAmount.subtract(taxAmount);
            if (derivedSubtotal.compareTo(BigDecimal.ZERO) >= 0) {
                subtotalAmount = derivedSubtotal.setScale(2, RoundingMode.HALF_UP);
            }
        }

        if (unitPrice == null && subtotalAmount != null && quantity != null
                && quantity.compareTo(BigDecimal.ZERO) > 0) {
            unitPrice = subtotalAmount.divide(quantity, 2, RoundingMode.HALF_UP);
        }

        BigDecimal confidence = computeConfidence(
                invoiceNumber,
                vendorName,
                quantity,
                unitPrice,
                subtotalAmount,
                taxAmount,
                totalAmount,
                currency,
                issueDate);

        InvoiceParseStatus parseStatus = determineParseStatus(confidence, invoiceNumber, vendorName, totalAmount);
        boolean needsReview = parseStatus != InvoiceParseStatus.SUCCESS || issueDate == null;

        return new ParsedInvoiceData(
                invoiceNumber,
                vendorName,
                quantity,
                unitPrice,
                subtotalAmount,
                taxAmount,
                totalAmount,
                currency,
                issueDate,
                parseStatus,
                confidence,
                rawText,
                needsReview);
    }

    private void validatePdf(MultipartFile file) {
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);

        if (!fileName.endsWith(".pdf") && !PDF_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Bulk import only supports PDF files.");
        }
    }

    private String extractRawText(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            return textStripper.getText(document)
                    .replace("\u0000", "")
                    .trim();
        } catch (IOException exception) {
            return "";
        }
    }

    private String extractFirst(String text, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return normalizeTextFragment(matcher.group(1));
            }
        }
        return null;
    }

    private String extractVendorName(String text, List<String> lines) {
        String labeledVendor = extractFirst(text, VENDOR_PATTERNS);
        if (labeledVendor != null) {
            return labeledVendor;
        }

        String priorityCustomerName = extractPriorityCustomerName(lines);
        if (priorityCustomerName != null) {
            return priorityCustomerName;
        }

        String cfdiCustomerName = extractCfdiCustomerName(lines);
        if (cfdiCustomerName != null) {
            return cfdiCustomerName;
        }

        String cfdiIssuerName = extractCfdiIssuerName(lines);
        if (cfdiIssuerName != null) {
            return cfdiIssuerName;
        }

        for (String line : lines) {
            String lowerCaseLine = line.toLowerCase(Locale.ROOT);
            if (lowerCaseLine.contains("invoice")
                    || lowerCaseLine.contains("factura")
                    || lowerCaseLine.contains("bill to")
                    || lowerCaseLine.contains("ship to")
                    || lowerCaseLine.contains("date")
                    || lowerCaseLine.contains("fecha")
                    || lowerCaseLine.contains("page")
                    || lowerCaseLine.startsWith("subtotal")
                    || lowerCaseLine.startsWith("iva")
                    || lowerCaseLine.startsWith("moneda")
                    || lowerCaseLine.startsWith("cantidad")
                    || lowerCaseLine.startsWith("valor unitario")
                    || lowerCaseLine.startsWith("total")) {
                continue;
            }

            if (line.length() >= 3) {
                return normalizeTextFragment(line);
            }
        }

        return null;
    }

    private String extractPriorityCustomerName(List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            String line = normalizeTextFragment(lines.get(index));
            String matchedLine = extractPriorityCustomerFromLine(line);
            if (matchedLine != null) {
                return matchedLine;
            }

            if (index + 1 >= lines.size()) {
                continue;
            }

            String combinedLines = normalizeTextFragment(line + " " + lines.get(index + 1));
            String matchedPair = extractPriorityCustomerFromCombinedLines(combinedLines);
            if (matchedPair != null) {
                return matchedPair;
            }
        }

        return null;
    }

    private String extractPriorityCustomerFromLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        return InvoiceFieldNormalizer.resolveVendorAlias(line);
    }

    private String extractPriorityCustomerFromCombinedLines(String combinedLines) {
        if (combinedLines == null || combinedLines.isBlank()) {
            return null;
        }

        return InvoiceFieldNormalizer.resolveVendorAlias(combinedLines);
    }

    private String extractCfdiCustomerName(List<String> lines) {
        int customerMarkerIndex = findLineIndexContaining(lines, CFDI_CUSTOMER_SECTION_MARKERS);
        if (customerMarkerIndex < 1) {
            return null;
        }

        int rfcIndex = findPreviousRfcLine(lines, customerMarkerIndex - 1, Math.max(0, customerMarkerIndex - 6));
        if (rfcIndex < 1) {
            return null;
        }

        return collectCompanyName(lines, rfcIndex - 1);
    }

    private String extractCfdiIssuerName(List<String> lines) {
        int uuidIndex = findLineIndex(lines, "uuid");
        if (uuidIndex >= 0) {
            String issuerName = findCompanyNameNearRfc(lines, uuidIndex + 1, Math.min(lines.size(), uuidIndex + 8));
            if (issuerName != null) {
                return issuerName;
            }
        }

        return findCompanyNameNearRfc(lines, 0, lines.size());
    }

    private String findCompanyNameNearRfc(List<String> lines, int startInclusive, int endExclusive) {
        for (int index = startInclusive; index < endExclusive; index++) {
            if (!isRfcLine(lines.get(index))) {
                continue;
            }

            String companyName = collectCompanyName(lines, index - 1);
            if (companyName != null) {
                return companyName;
            }
        }

        return null;
    }

    private String collectCompanyName(List<String> lines, int startIndex) {
        List<String> companyNameLines = new ArrayList<>();

        for (int index = startIndex; index >= 0; index--) {
            String line = normalizeTextFragment(lines.get(index));
            if (!isCompanyNameLine(line)) {
                if (!companyNameLines.isEmpty()) {
                    break;
                }
                continue;
            }

            companyNameLines.add(0, line);
        }

        if (companyNameLines.isEmpty()) {
            return null;
        }

        return String.join(" ", companyNameLines);
    }

    private boolean isCompanyNameLine(String line) {
        if (line == null || line.length() < 3 || !containsLetter(line)) {
            return false;
        }

        if (isPriorityCustomerName(line)) {
            return true;
        }

        String lowerCaseLine = line.toLowerCase(Locale.ROOT);
        for (String prefix : COMPANY_NAME_SKIP_PREFIXES) {
            if (lowerCaseLine.startsWith(prefix)) {
                return false;
            }
        }

        return line.equals(line.toUpperCase(Locale.ROOT));
    }

    private boolean isPriorityCustomerName(String line) {
        return InvoiceFieldNormalizer.resolveVendorAlias(line) != null;
    }

    private boolean isRfcLine(String line) {
        return RFC_PATTERN.matcher(line.replace(" ", "")).matches();
    }

    private boolean containsLetter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isLetter(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private int findLineIndex(List<String> lines, String target) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).equalsIgnoreCase(target)) {
                return index;
            }
        }
        return -1;
    }

    private int findLineIndexContaining(List<String> lines, Set<String> targets) {
        for (int index = 0; index < lines.size(); index++) {
            String normalizedLine = lines.get(index).toLowerCase(Locale.ROOT);
            for (String target : targets) {
                if (normalizedLine.contains(target)) {
                    return index;
                }
            }
        }
        return -1;
    }

    private int findPreviousRfcLine(List<String> lines, int startInclusive, int minInclusive) {
        for (int index = startInclusive; index >= minInclusive; index--) {
            if (isRfcLine(lines.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private CfdiLineItemData extractCfdiLineItemData(List<String> lines) {
        int headerIndex = -1;
        for (int index = 0; index < lines.size(); index++) {
            String lowerCaseLine = lines.get(index).toLowerCase(Locale.ROOT);
            if (lowerCaseLine.contains("cantidad")
                    && (lowerCaseLine.contains("valor unitario") || lowerCaseLine.contains("importe"))) {
                headerIndex = index;
                break;
            }
        }

        if (headerIndex < 0) {
            return null;
        }

        List<String> rowLines = new ArrayList<>();
        for (int index = headerIndex + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            String lowerCaseLine = line.toLowerCase(Locale.ROOT);

            if (startsWithAny(lowerCaseLine, CFDI_LINE_ITEM_STOP_PREFIXES)) {
                break;
            }

            if (rowLines.isEmpty()) {
                Matcher quantityMatcher = LEADING_QUANTITY_PATTERN.matcher(line);
                if (!quantityMatcher.find()) {
                    continue;
                }
            }

            rowLines.add(line);
        }

        if (rowLines.isEmpty()) {
            return null;
        }

        Matcher quantityMatcher = LEADING_QUANTITY_PATTERN.matcher(rowLines.get(0));
        BigDecimal quantity = quantityMatcher.find() ? parseDecimal(quantityMatcher.group(1)) : null;

        List<BigDecimal> lineAmounts = extractLabeledMoneyAmounts(String.join(" ", rowLines));
        BigDecimal unitPrice = lineAmounts.size() >= 1 ? lineAmounts.get(0) : null;
        BigDecimal subtotalAmount = lineAmounts.size() >= 2 ? lineAmounts.get(1) : null;

        return new CfdiLineItemData(quantity, unitPrice, subtotalAmount);
    }

    private boolean startsWithAny(String value, Set<String> prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private List<BigDecimal> extractLabeledMoneyAmounts(String value) {
        List<BigDecimal> amounts = new ArrayList<>();
        Matcher matcher = LABELED_CURRENCY_PATTERN.matcher(value);

        while (matcher.find()) {
            BigDecimal amount = parseAmount(matcher.group());
            if (amount != null) {
                amounts.add(amount);
            }
        }

        return amounts;
    }

    private BigDecimal extractDecimal(String text, List<Pattern> patterns) {
        return parseDecimal(extractFirst(text, patterns));
    }

    private BigDecimal extractAmount(String text, List<Pattern> patterns) {
        return parseAmount(extractFirst(text, patterns));
    }

    private BigDecimal extractTaxAmount(String text, List<String> lines) {
        for (String line : lines) {
            BigDecimal taxAmount = extractAmount(line, TAX_PATTERNS);
            if (taxAmount != null) {
                return taxAmount;
            }
        }

        for (int index = 0; index < lines.size() - 1; index++) {
            BigDecimal taxAmount = extractAmount(lines.get(index) + " " + lines.get(index + 1), TAX_PATTERNS);
            if (taxAmount != null) {
                return taxAmount;
            }
        }

        return extractAmount(text, TAX_PATTERNS);
    }

    private BigDecimal extractLargestAmount(String text) {
        Matcher matcher = MONEY_TOKEN_PATTERN.matcher(text);
        BigDecimal largestAmount = null;

        while (matcher.find()) {
            BigDecimal candidate = parseAmount(matcher.group());
            if (candidate != null && (largestAmount == null || candidate.compareTo(largestAmount) > 0)) {
                largestAmount = candidate;
            }
        }

        return largestAmount;
    }

    private String extractCurrency(String text) {
        String detectedCode = extractFirst(text, CURRENCY_PATTERNS);
        if (detectedCode != null) {
            return normalizeCurrencyCode(detectedCode);
        }

        String upperCaseText = text.toUpperCase(Locale.ROOT);
        if (upperCaseText.contains("MXN") || upperCaseText.contains("M.N.")) {
            return "MXN";
        }
        if (text.contains("$") || text.toUpperCase(Locale.ROOT).contains("USD")) {
            return "USD";
        }
        if (text.contains("€") || text.toUpperCase(Locale.ROOT).contains("EUR")) {
            return "EUR";
        }
        if (text.contains("£") || text.toUpperCase(Locale.ROOT).contains("GBP")) {
            return "GBP";
        }
        if (text.contains("¥") || text.toUpperCase(Locale.ROOT).contains("JPY")) {
            return "JPY";
        }

        return null;
    }

    private LocalDate extractDate(String text) {
        LocalDate labeledDate = parseDate(extractFirst(text, ISSUE_DATE_PATTERNS));
        if (labeledDate != null) {
            return labeledDate;
        }

        Matcher matcher = DATE_TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            LocalDate parsedDate = parseDate(matcher.group());
            if (parsedDate != null) {
                return parsedDate;
            }
        }

        return null;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalizedValue = value.trim().replaceAll("\\s+", " ");
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalizedValue, formatter);
            } catch (DateTimeParseException ignored) {
                // Continue until a supported date format matches.
            }
        }

        return null;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(value.replace(",", ""))
                    .setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalizedValue = value
                .replace(",", "")
                .replace("USD", "")
                .replace("EUR", "")
                .replace("GBP", "")
                .replace("JPY", "")
                .replace("MXN", "")
                .replace("MX$", "")
                .replace("$", "")
                .replace("€", "")
                .replace("£", "")
                .replace("¥", "")
                .trim();

        return parseDecimal(normalizedValue);
    }

    private String normalizeTextFragment(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeCurrencyCode(String value) {
        String normalizedValue = value.trim().toUpperCase(Locale.ROOT);
        if ("M.N.".equals(normalizedValue)) {
            return "MXN";
        }
        return normalizedValue;
    }

    private BigDecimal computeConfidence(
            String invoiceNumber,
            String vendorName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal subtotalAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String currency,
            LocalDate issueDate) {
        double score = 0.0;
        score += invoiceNumber != null ? 0.15 : 0.0;
        score += vendorName != null ? 0.15 : 0.0;
        score += quantity != null ? 0.10 : 0.0;
        score += unitPrice != null ? 0.10 : 0.0;
        score += subtotalAmount != null ? 0.10 : 0.0;
        score += taxAmount != null ? 0.05 : 0.0;
        score += totalAmount != null ? 0.20 : 0.0;
        score += currency != null ? 0.05 : 0.0;
        score += issueDate != null ? 0.10 : 0.0;

        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private InvoiceParseStatus determineParseStatus(
            BigDecimal confidence,
            String invoiceNumber,
            String vendorName,
            BigDecimal totalAmount) {
        boolean missingCriticalField = invoiceNumber == null || vendorName == null || totalAmount == null;
        if (confidence.compareTo(BigDecimal.valueOf(0.20)) <= 0) {
            return InvoiceParseStatus.FAILED;
        }
        if (missingCriticalField || confidence.compareTo(BigDecimal.valueOf(0.85)) < 0) {
            return InvoiceParseStatus.PARTIAL;
        }
        return InvoiceParseStatus.SUCCESS;
    }

    private record CfdiLineItemData(
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal subtotalAmount) {
    }
}
