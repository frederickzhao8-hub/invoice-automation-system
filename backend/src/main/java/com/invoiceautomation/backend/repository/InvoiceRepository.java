package com.invoiceautomation.backend.repository;

import com.invoiceautomation.backend.entity.Invoice;
import com.invoiceautomation.backend.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findAllByOrderByCreatedAtDesc();

    List<Invoice> findByVendorContainingIgnoreCaseOrderByCreatedAtDesc(String vendor);

    List<Invoice> findByStatusOrderByCreatedAtDesc(InvoiceStatus status);

    List<Invoice> findByVendorContainingIgnoreCaseAndStatusOrderByCreatedAtDesc(
            String vendor,
            InvoiceStatus status);

    Invoice findFirstByVendorIgnoreCaseAndInvoiceNumberIgnoreCaseOrderByCreatedAtDesc(
            String vendor,
            String invoiceNumber);

    Invoice findFirstByVendorIgnoreCaseAndAmountAndInvoiceDateOrderByCreatedAtDesc(
            String vendor,
            BigDecimal amount,
            java.time.LocalDate invoiceDate);

    long countByStatus(InvoiceStatus status);

    @Query("select coalesce(sum(i.amount), 0) from Invoice i")
    BigDecimal sumTotalAmount();
}
