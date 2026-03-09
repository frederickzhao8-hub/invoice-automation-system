package com.invoiceautomation.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.invoiceautomation.backend.dto.InvoiceExtractionResult;
import com.invoiceautomation.backend.entity.Invoice;
import com.invoiceautomation.backend.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvoiceDuplicateDetectionServiceTest {

    @Test
    void detectsDuplicateByVendorAndInvoiceNumber() {
        InvoiceRepository repository = mock(InvoiceRepository.class);
        Invoice existingInvoice = new Invoice();
        existingInvoice.setId(42L);

        when(repository.findFirstByVendorIgnoreCaseAndInvoiceNumberIgnoreCaseOrderByCreatedAtDesc(
                        "Amazon", "INV-1001"))
                .thenReturn(existingInvoice);

        InvoiceDuplicateDetectionService service = new InvoiceDuplicateDetectionService(repository);

        InvoiceExtractionResult result = new InvoiceExtractionResult();
        result.setVendorName("Amazon");
        result.setInvoiceNumber("INV-1001");

        InvoiceDuplicateDetectionService.DuplicateDetectionResult duplicateResult = service.detectDuplicate(result);

        assertTrue(duplicateResult.duplicate());
        assertEquals(42L, duplicateResult.existingInvoiceId());
    }

    @Test
    void ignoresNonDuplicateInvoices() {
        InvoiceRepository repository = mock(InvoiceRepository.class);
        InvoiceDuplicateDetectionService service = new InvoiceDuplicateDetectionService(repository);

        InvoiceExtractionResult result = new InvoiceExtractionResult();
        result.setVendorName("Amazon");
        result.setTotalAmount(new BigDecimal("100.00"));
        result.setInvoiceDate(LocalDate.of(2026, 3, 1));

        InvoiceDuplicateDetectionService.DuplicateDetectionResult duplicateResult = service.detectDuplicate(result);

        assertFalse(duplicateResult.duplicate());
    }
}
