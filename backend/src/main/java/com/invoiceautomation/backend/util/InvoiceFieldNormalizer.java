package com.invoiceautomation.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class InvoiceFieldNormalizer {

    private static final List<CustomerAliasRule> CUSTOMER_ALIAS_RULES = List.of(
            new CustomerAliasRule(Pattern.compile("(?i)\\b(?:A1|AME900814LM3)\\b"), "A1"),
            new CustomerAliasRule(Pattern.compile("(?i)\\b(?:T1|TBO140305DH0)\\b"), "T1"),
            new CustomerAliasRule(Pattern.compile("(?i)\\b(?:T2|TPT890516JP5)\\b"), "T2"));

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("uuuu/MM/dd"),
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

    private InvoiceFieldNormalizer() {
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim().replaceAll("\\s+", " ");
        return normalizedValue.isBlank() ? null : normalizedValue;
    }

    public static String normalizeVendorName(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }

        String alias = resolveVendorAlias(normalizedValue);
        return alias == null ? normalizedValue : alias;
    }

    public static String resolveVendorAlias(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }

        for (CustomerAliasRule rule : CUSTOMER_ALIAS_RULES) {
            if (rule.pattern().matcher(normalizedValue).find()) {
                return rule.alias();
            }
        }

        return null;
    }

    public static String normalizeCurrency(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }

        String upperCaseValue = normalizedValue.toUpperCase(Locale.ROOT);
        if ("M.N.".equals(upperCaseValue)) {
            return "MXN";
        }

        return upperCaseValue;
    }

    public static BigDecimal normalizeDecimal(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }

        String candidate = normalizedValue
                .replace(",", "")
                .replace("$", "")
                .replace("MX$", "")
                .replace("USD", "")
                .replace("EUR", "")
                .replace("GBP", "")
                .replace("JPY", "")
                .replace("MXN", "")
                .replace("(", "-")
                .replace(")", "")
                .trim();

        if (candidate.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(candidate).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static LocalDate normalizeDate(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }

        String candidate = normalizedValue;
        if (candidate.length() >= 10 && Character.isDigit(candidate.charAt(0)) && candidate.charAt(4) == '-') {
            try {
                return LocalDate.parse(candidate.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                // Continue with the broader formatter list.
            }
        }

        int firstWhitespace = candidate.indexOf(' ');
        if (firstWhitespace > 0) {
            LocalDate fromToken = normalizeDate(candidate.substring(0, firstWhitespace));
            if (fromToken != null) {
                return fromToken;
            }
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(candidate, formatter);
            } catch (DateTimeParseException ignored) {
                // Continue until a supported date format matches.
            }
        }

        return null;
    }

    public static String extractJsonObject(String value) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return null;
        }

        String candidate = normalizedValue;
        if (candidate.startsWith("```")) {
            candidate = candidate.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }

        int firstBrace = candidate.indexOf('{');
        int lastBrace = candidate.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            return candidate;
        }

        return candidate.substring(firstBrace, lastBrace + 1);
    }

    private record CustomerAliasRule(Pattern pattern, String alias) {
    }
}
