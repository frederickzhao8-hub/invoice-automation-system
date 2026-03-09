package com.invoiceautomation.backend.config;

import com.invoiceautomation.backend.dto.ApiErrorResponse;
import com.invoiceautomation.backend.service.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "Validation failed",
                        extractErrors(exception.getBindingResult().getFieldErrors())));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(BindException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "Validation failed",
                        extractErrors(exception.getFieldErrors())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("Invalid request payload.", Map.of()));
    }

    private Map<String, String> extractErrors(Iterable<FieldError> fieldErrors) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : fieldErrors) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return errors;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(exception.getMessage(), Map.of()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {
        String message = "Unable to save the invoice record.";
        Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(exception);
        String rootMessage = rootCause == null ? "" : rootCause.getMessage();

        if (rootMessage.contains("invoice_number")) {
            message = "An invoice with the same invoice number already exists.";
        }

        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        message,
                        Map.of()));
    }
}
