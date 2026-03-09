package com.invoiceautomation.backend.service;

import com.invoiceautomation.backend.dto.InvoiceExtractionResult;
import com.invoiceautomation.backend.entity.Invoice;
import com.invoiceautomation.backend.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InvoiceDuplicateDetectionService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceDuplicateDetectionService.class);

    private final InvoiceRepository invoiceRepository;

    public InvoiceDuplicateDetectionService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public DuplicateDetectionResult detectDuplicate(InvoiceExtractionResult extractionResult) {
        if (extractionResult == null || extractionResult.getVendorName() == null) {
            return DuplicateDetectionResult.notDuplicate();
        }

        if (extractionResult.getInvoiceNumber() != null) {
            Invoice existingInvoice = invoiceRepository
                    .findFirstByVendorIgnoreCaseAndInvoiceNumberIgnoreCaseOrderByCreatedAtDesc(
                            extractionResult.getVendorName(),
                            extractionResult.getInvoiceNumber());

            if (existingInvoice != null) {
                String reason = "Matching vendor name and invoice number already exist.";
                log.warn("Duplicate invoice detected: {}", reason);
                return DuplicateDetectionResult.duplicate(existingInvoice.getId(), reason);
            }
        }

        if (extractionResult.getTotalAmount() != null && extractionResult.getInvoiceDate() != null) {
            Invoice existingInvoice = invoiceRepository
                    .findFirstByVendorIgnoreCaseAndAmountAndInvoiceDateOrderByCreatedAtDesc(
                            extractionResult.getVendorName(),
                            extractionResult.getTotalAmount(),
                            extractionResult.getInvoiceDate());

            if (existingInvoice != null) {
                String reason = "Matching vendor name, total amount, and invoice date already exist.";
                log.warn("Duplicate invoice detected: {}", reason);
                return DuplicateDetectionResult.duplicate(existingInvoice.getId(), reason);
            }
        }

        return DuplicateDetectionResult.notDuplicate();
    }

    public record DuplicateDetectionResult(boolean duplicate, Long existingInvoiceId, String reason) {

        public static DuplicateDetectionResult duplicate(Long existingInvoiceId, String reason) {
            return new DuplicateDetectionResult(true, existingInvoiceId, reason);
        }

        public static DuplicateDetectionResult notDuplicate() {
            return new DuplicateDetectionResult(false, null, null);
        }
    }
}
