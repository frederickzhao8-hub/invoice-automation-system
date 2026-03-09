package com.invoiceautomation.backend.dto;

import java.util.Map;

public record ApiErrorResponse(String message, Map<String, String> errors) {
}

