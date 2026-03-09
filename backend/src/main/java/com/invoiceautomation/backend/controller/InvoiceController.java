package com.invoiceautomation.backend.controller;

import com.invoiceautomation.backend.dto.BatchUploadResult;
import com.invoiceautomation.backend.dto.InvoiceResponse;
import com.invoiceautomation.backend.dto.InvoiceReviewUpdateRequest;
import com.invoiceautomation.backend.dto.InvoiceStatusUpdateRequest;
import com.invoiceautomation.backend.dto.InvoiceUploadRequest;
import com.invoiceautomation.backend.entity.InvoiceStatus;
import com.invoiceautomation.backend.service.InvoiceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<InvoiceResponse> createInvoice(
            @Valid @ModelAttribute InvoiceUploadRequest request) {
        InvoiceResponse response = invoiceService.createInvoice(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/bulk-upload", consumes = {"multipart/form-data"})
    public ResponseEntity<BatchUploadResult> bulkUploadInvoices(
            @RequestParam("files") List<MultipartFile> files) {
        BatchUploadResult response = invoiceService.bulkUploadInvoices(files);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<InvoiceResponse> getInvoices(
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) InvoiceStatus status) {
        return invoiceService.findInvoices(vendor, status);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportInvoices(
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) InvoiceStatus status) {
        byte[] workbook = invoiceService.exportInvoices(vendor, status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices-export.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbook);
    }

    @PutMapping("/{id}/review")
    public InvoiceResponse saveReviewedInvoice(
            @PathVariable Long id,
            @Valid @RequestBody InvoiceReviewUpdateRequest request) {
        return invoiceService.saveReviewedInvoice(id, request);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteInvoices(@RequestParam("ids") List<Long> ids) {
        invoiceService.deleteInvoices(ids);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public InvoiceResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody InvoiceStatusUpdateRequest request) {
        return invoiceService.updateStatus(id, request.getStatus());
    }
}
