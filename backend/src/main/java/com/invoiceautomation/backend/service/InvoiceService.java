package com.invoiceautomation.backend.service;

import com.invoiceautomation.backend.dto.BatchUploadItemResult;
import com.invoiceautomation.backend.dto.BatchUploadResult;
import com.invoiceautomation.backend.dto.DashboardSummaryResponse;
import com.invoiceautomation.backend.dto.InvoiceExtractionResult;
import com.invoiceautomation.backend.dto.InvoiceResponse;
import com.invoiceautomation.backend.dto.InvoiceReviewUpdateRequest;
import com.invoiceautomation.backend.dto.InvoiceUploadRequest;
import com.invoiceautomation.backend.entity.Invoice;
import com.invoiceautomation.backend.entity.InvoiceParseStatus;
import com.invoiceautomation.backend.entity.InvoiceProcessingStatus;
import com.invoiceautomation.backend.entity.InvoiceStatus;
import com.invoiceautomation.backend.repository.InvoiceRepository;
import com.invoiceautomation.backend.util.InvoiceFieldNormalizer;
import jakarta.transaction.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final List<DateTimeFormatter> REVIEW_DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("uuuu/MM/dd"),
            DateTimeFormatter.ofPattern("M/d/uuuu"),
            DateTimeFormatter.ofPattern("MM/dd/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"));

    private final InvoiceRepository invoiceRepository;
    private final InvoicePdfParserService invoicePdfParserService;
    private final PdfTextExtractionService pdfTextExtractionService;
    private final InvoiceExtractionService invoiceExtractionService;
    private final InvoiceDuplicateDetectionService invoiceDuplicateDetectionService;
    private final Path uploadPath;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoicePdfParserService invoicePdfParserService,
            PdfTextExtractionService pdfTextExtractionService,
            InvoiceExtractionService invoiceExtractionService,
            InvoiceDuplicateDetectionService invoiceDuplicateDetectionService,
            @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.invoiceRepository = invoiceRepository;
        this.invoicePdfParserService = invoicePdfParserService;
        this.pdfTextExtractionService = pdfTextExtractionService;
        this.invoiceExtractionService = invoiceExtractionService;
        this.invoiceDuplicateDetectionService = invoiceDuplicateDetectionService;
        this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.uploadPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize upload directory", exception);
        }
    }

    @Transactional
    public InvoiceResponse createInvoice(InvoiceUploadRequest request) {
        MultipartFile file = request.getFile();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Invoice file is required");
        }

        StoredFile storedFile = storeFile(file);

        Invoice invoice = new Invoice();
        invoice.setVendorName(normalizeVendor(request.getVendor()));
        invoice.setInvoiceNumber(normalizeNullable(request.getInvoiceNumber()));
        invoice.setTotalAmount(request.getAmount());
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setStatus(request.getStatus() == null ? InvoiceStatus.PENDING : request.getStatus());
        invoice.setParseStatus(InvoiceParseStatus.SUCCESS);
        invoice.setParseConfidence(BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP));
        invoice.setProcessingStatus(InvoiceProcessingStatus.SUCCESS);
        invoice.setNeedsReview(false);
        invoice.setDuplicateFlag(false);
        invoice.setOriginalFileName(storedFile.originalFileName());
        invoice.setStoredFileName(storedFile.storedFileName());
        invoice.setFilePath(storedFile.targetLocation().toString());

        try {
            Invoice savedInvoice = invoiceRepository.save(invoice);
            log.info("Saved manual invoice record: id={}, file={}", savedInvoice.getId(), savedInvoice.getOriginalFileName());
            return toResponse(savedInvoice);
        } catch (RuntimeException exception) {
            cleanupStoredFile(storedFile.targetLocation(), exception);
            throw exception;
        }
    }

    @Transactional
    public BatchUploadResult bulkUploadInvoices(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one PDF file is required.");
        }

        List<BatchUploadItemResult> results = new ArrayList<>();
        List<InvoiceResponse> savedInvoices = new ArrayList<>();

        for (MultipartFile file : files) {
            results.add(processBulkUploadFile(file, savedInvoices));
        }

        long successCount = results.stream().filter(result -> result.status() == InvoiceProcessingStatus.SUCCESS).count();
        long duplicateCount = results.stream().filter(result -> result.status() == InvoiceProcessingStatus.DUPLICATE).count();
        long failureCount = results.stream().filter(result -> result.status() == InvoiceProcessingStatus.FAILED).count();

        return new BatchUploadResult(
                results.size(),
                (int) successCount,
                (int) duplicateCount,
                (int) failureCount,
                List.copyOf(results),
                List.copyOf(savedInvoices));
    }

    @Transactional
    public InvoiceResponse saveReviewedInvoice(Long id, InvoiceReviewUpdateRequest request) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));

        invoice.setInvoiceNumber(normalizeNullable(request.getInvoiceNumber()));
        invoice.setVendorName(normalizeVendor(request.getVendorName()));
        invoice.setQuantity(request.getQuantity());
        invoice.setUnitPrice(request.getUnitPrice());
        invoice.setSubtotalAmount(request.getSubtotalAmount());
        invoice.setTaxAmount(request.getTaxAmount());
        invoice.setTotalAmount(request.getTotalAmount());
        invoice.setInvoiceDate(parseReviewDate(request.getIssueDate()));
        invoice.setCurrency(normalizeCurrency(request.getCurrency()));
        invoice.setProcessingStatus(InvoiceProcessingStatus.SUCCESS);
        invoice.setDuplicateFlag(false);
        invoice.setDuplicateReason(null);
        invoice.setExtractionError(null);

        boolean needsReview = requiresReview(invoice);
        invoice.setNeedsReview(needsReview);
        invoice.setParseStatus(needsReview ? InvoiceParseStatus.PARTIAL : InvoiceParseStatus.SUCCESS);
        invoice.setParseConfidence(computeParseConfidence(invoice));

        Invoice savedInvoice = invoiceRepository.save(invoice);
        log.info("Saved reviewed invoice record: id={}", savedInvoice.getId());
        return toResponse(savedInvoice);
    }

    @Transactional
    public void deleteInvoices(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Select at least one invoice to delete.");
        }

        List<Invoice> invoices = invoiceRepository.findAllById(ids);
        if (invoices.isEmpty()) {
            throw new ResourceNotFoundException("No invoices found for deletion.");
        }

        invoiceRepository.deleteAllInBatch(invoices);
        for (Invoice invoice : invoices) {
            deleteStoredFileQuietly(invoice.getFilePath());
        }
    }

    @Transactional
    public InvoiceResponse updateStatus(Long id, InvoiceStatus status) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
        invoice.setStatus(status);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public List<InvoiceResponse> findInvoices(String vendor, InvoiceStatus status) {
        List<Invoice> invoices = findInvoiceEntities(vendor, status);
        backfillDerivedCustomerNames(invoices);
        return invoices.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public byte[] exportInvoices(String vendor, InvoiceStatus status) {
        List<Invoice> invoices = findInvoiceEntities(vendor, status);
        backfillDerivedCustomerNames(invoices);

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Invoices");
            Row headerRow = sheet.createRow(0);

            String[] headers = {
                    "Vendor",
                    "Invoice Number",
                    "Total Amount",
                    "Quantity",
                    "Unit Price",
                    "Subtotal",
                    "Tax",
                    "Currency",
                    "Issue Date",
                    "Due Date",
                    "Payment Terms",
                    "Description",
                    "Status",
                    "Parse Status",
                    "Processing Status",
                    "Parse Confidence",
                    "Needs Review",
                    "Duplicate",
                    "Duplicate Reason",
                    "Extraction Error",
                    "Original File Name",
                    "Created At",
                    "Updated At"
            };

            var headerStyle = workbook.createCellStyle();
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            for (int index = 0; index < headers.length; index++) {
                headerRow.createCell(index).setCellValue(headers[index]);
                headerRow.getCell(index).setCellStyle(headerStyle);
            }

            for (int rowIndex = 0; rowIndex < invoices.size(); rowIndex++) {
                Invoice invoice = invoices.get(rowIndex);
                Row row = sheet.createRow(rowIndex + 1);
                row.createCell(0).setCellValue(defaultString(invoice.getVendorName()));
                row.createCell(1).setCellValue(defaultString(invoice.getInvoiceNumber()));
                row.createCell(2).setCellValue(defaultString(formatDecimal(invoice.getTotalAmount())));
                row.createCell(3).setCellValue(defaultString(formatDecimal(invoice.getQuantity())));
                row.createCell(4).setCellValue(defaultString(formatDecimal(invoice.getUnitPrice())));
                row.createCell(5).setCellValue(defaultString(formatDecimal(invoice.getSubtotalAmount())));
                row.createCell(6).setCellValue(defaultString(formatDecimal(invoice.getTaxAmount())));
                row.createCell(7).setCellValue(defaultString(invoice.getCurrency()));
                row.createCell(8).setCellValue(defaultString(formatDate(invoice.getInvoiceDate())));
                row.createCell(9).setCellValue(defaultString(formatDate(invoice.getDueDate())));
                row.createCell(10).setCellValue(defaultString(invoice.getPaymentTerms()));
                row.createCell(11).setCellValue(defaultString(invoice.getInvoiceDescription()));
                row.createCell(12).setCellValue(invoice.getStatus().name());
                row.createCell(13).setCellValue(invoice.getParseStatus().name());
                row.createCell(14).setCellValue(invoice.getProcessingStatus().name());
                row.createCell(15).setCellValue(defaultString(formatDecimal(invoice.getParseConfidence())));
                row.createCell(16).setCellValue(invoice.isNeedsReview() ? "Yes" : "No");
                row.createCell(17).setCellValue(invoice.isDuplicateFlag() ? "Yes" : "No");
                row.createCell(18).setCellValue(defaultString(invoice.getDuplicateReason()));
                row.createCell(19).setCellValue(defaultString(invoice.getExtractionError()));
                row.createCell(20).setCellValue(defaultString(invoice.getOriginalFileName()));
                row.createCell(21).setCellValue(invoice.getCreatedAt().toString());
                row.createCell(22).setCellValue(invoice.getUpdatedAt().toString());
            }

            for (int index = 0; index < headers.length; index++) {
                sheet.autoSizeColumn(index);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to export invoices to Excel.", exception);
        }
    }

    @Transactional
    public DashboardSummaryResponse getDashboardSummary() {
        return new DashboardSummaryResponse(
                invoiceRepository.count(),
                defaultIfNull(invoiceRepository.sumTotalAmount()),
                invoiceRepository.countByStatus(InvoiceStatus.PENDING),
                invoiceRepository.countByStatus(InvoiceStatus.APPROVED),
                invoiceRepository.countByStatus(InvoiceStatus.PAID));
    }

    private BatchUploadItemResult processBulkUploadFile(
            MultipartFile file,
            List<InvoiceResponse> savedInvoices) {
        String fileName = file == null ? "unknown.pdf" : Objects.requireNonNullElse(file.getOriginalFilename(), "invoice.pdf");

        if (file == null || file.isEmpty()) {
            return new BatchUploadItemResult(
                    fileName,
                    InvoiceProcessingStatus.FAILED,
                    null,
                    null,
                    null,
                    false,
                    "Uploaded file is empty.");
        }

        log.info("Processing invoice file: {}", fileName);

        try {
            String rawText = pdfTextExtractionService.extractText(file);
            ParsedInvoiceData fallbackData = invoicePdfParserService.parseText(rawText == null ? "" : rawText);
            InvoiceExtractionResult extractionResult = invoiceExtractionService.extractInvoiceData(rawText, fallbackData);
            log.info("Extracted invoice data: {}", extractionResult);

            if (!shouldPersistExtractedInvoice(extractionResult, fallbackData)) {
                return new BatchUploadItemResult(
                        fileName,
                        InvoiceProcessingStatus.FAILED,
                        null,
                        extractionResult.getVendorName(),
                        extractionResult.getInvoiceNumber(),
                        false,
                        normalizeNullable(extractionResult.getExtractionError()) != null
                                ? extractionResult.getExtractionError()
                                : "Unable to parse invoice data.");
            }

            InvoiceDuplicateDetectionService.DuplicateDetectionResult duplicateResult =
                    invoiceDuplicateDetectionService.detectDuplicate(extractionResult);
            if (duplicateResult.duplicate()) {
                String message = duplicateResult.reason();
                if (duplicateResult.existingInvoiceId() != null) {
                    message = message + " Existing invoice ID: " + duplicateResult.existingInvoiceId() + '.';
                }
                log.warn("Duplicate invoice detected for file {}: {}", fileName, message);
                return new BatchUploadItemResult(
                        fileName,
                        InvoiceProcessingStatus.DUPLICATE,
                        null,
                        extractionResult.getVendorName(),
                        extractionResult.getInvoiceNumber(),
                        true,
                        message);
            }

            StoredFile storedFile = storeFile(file);
            Invoice invoice = buildExtractedInvoice(storedFile, extractionResult, fallbackData);

            try {
                Invoice savedInvoice = invoiceRepository.save(invoice);
                InvoiceResponse response = toResponse(savedInvoice);
                savedInvoices.add(response);
                log.info("Saved processed invoice: id={}, file={}", savedInvoice.getId(), fileName);
                return new BatchUploadItemResult(
                        fileName,
                        InvoiceProcessingStatus.SUCCESS,
                        savedInvoice.getId(),
                        savedInvoice.getVendorName(),
                        savedInvoice.getInvoiceNumber(),
                        false,
                        savedInvoice.isNeedsReview()
                                ? "Invoice processed and saved for review."
                                : "Invoice processed successfully.");
            } catch (RuntimeException exception) {
                cleanupStoredFile(storedFile.targetLocation(), exception);
                throw exception;
            }
        } catch (IllegalArgumentException exception) {
            log.warn("Invoice processing failed for file {}: {}", fileName, exception.getMessage());
            return new BatchUploadItemResult(
                    fileName,
                    InvoiceProcessingStatus.FAILED,
                    null,
                    null,
                    null,
                    false,
                    exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Invoice processing failed for file: {}", fileName, exception);
            return new BatchUploadItemResult(
                    fileName,
                    InvoiceProcessingStatus.FAILED,
                    null,
                    null,
                    null,
                    false,
                    "Unable to process invoice file.");
        }
    }

    private Invoice buildExtractedInvoice(
            StoredFile storedFile,
            InvoiceExtractionResult extractionResult,
            ParsedInvoiceData fallbackData) {
        Invoice invoice = new Invoice();
        invoice.setVendorName(normalizeVendor(extractionResult.getVendorName()));
        invoice.setInvoiceNumber(normalizeNullable(extractionResult.getInvoiceNumber()));
        invoice.setQuantity(fallbackData == null ? null : fallbackData.quantity());
        invoice.setUnitPrice(fallbackData == null ? null : fallbackData.unitPrice());
        invoice.setSubtotalAmount(fallbackData == null ? null : fallbackData.subtotalAmount());
        invoice.setTaxAmount(extractionResult.getTaxAmount() != null
                ? extractionResult.getTaxAmount()
                : fallbackData == null ? null : fallbackData.taxAmount());
        invoice.setTotalAmount(extractionResult.getTotalAmount() != null
                ? extractionResult.getTotalAmount()
                : fallbackData == null ? null : fallbackData.totalAmount());
        invoice.setCurrency(normalizeCurrency(extractionResult.getCurrency()));
        invoice.setInvoiceDate(extractionResult.getInvoiceDate() != null
                ? extractionResult.getInvoiceDate()
                : fallbackData == null ? null : fallbackData.issueDate());
        invoice.setDueDate(extractionResult.getDueDate());
        invoice.setPaymentTerms(normalizeNullable(extractionResult.getPaymentTerms()));
        invoice.setInvoiceDescription(normalizeNullable(extractionResult.getInvoiceDescription()));
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setParseStatus(determineParseStatus(extractionResult));
        invoice.setParseConfidence(computeParseConfidence(extractionResult));
        invoice.setProcessingStatus(InvoiceProcessingStatus.SUCCESS);
        invoice.setRawExtractedText(extractionResult.getRawExtractedText());
        invoice.setNeedsReview(requiresReview(invoice));
        invoice.setDuplicateFlag(false);
        invoice.setDuplicateReason(null);
        invoice.setExtractionError(normalizeNullable(extractionResult.getExtractionError()));
        invoice.setOriginalFileName(storedFile.originalFileName());
        invoice.setStoredFileName(storedFile.storedFileName());
        invoice.setFilePath(storedFile.targetLocation().toString());
        return invoice;
    }

    private boolean shouldPersistExtractedInvoice(
            InvoiceExtractionResult extractionResult,
            ParsedInvoiceData fallbackData) {
        if (extractionResult != null && extractionResult.hasAnyStructuredData()) {
            return true;
        }

        return fallbackData != null
                && (fallbackData.vendorName() != null
                || fallbackData.invoiceNumber() != null
                || fallbackData.totalAmount() != null
                || fallbackData.issueDate() != null);
    }

    private List<Invoice> findInvoiceEntities(String vendor, InvoiceStatus status) {
        String normalizedVendor = vendor == null ? "" : vendor.trim();

        if (!normalizedVendor.isBlank() && status != null) {
            return invoiceRepository.findByVendorContainingIgnoreCaseAndStatusOrderByCreatedAtDesc(
                    normalizedVendor,
                    status);
        }
        if (!normalizedVendor.isBlank()) {
            return invoiceRepository.findByVendorContainingIgnoreCaseOrderByCreatedAtDesc(
                    normalizedVendor);
        }
        if (status != null) {
            return invoiceRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return invoiceRepository.findAllByOrderByCreatedAtDesc();
    }

    private void backfillDerivedCustomerNames(List<Invoice> invoices) {
        List<Invoice> invoicesToUpdate = new ArrayList<>();

        for (Invoice invoice : invoices) {
            if (invoice.getRawExtractedText() == null || invoice.getRawExtractedText().isBlank()) {
                continue;
            }

            String derivedCustomerName = invoicePdfParserService.extractPreferredCustomerName(invoice.getRawExtractedText());
            if (derivedCustomerName == null || derivedCustomerName.equals(invoice.getVendorName())) {
                continue;
            }

            invoice.setVendorName(derivedCustomerName);
            invoicesToUpdate.add(invoice);
        }

        if (!invoicesToUpdate.isEmpty()) {
            invoiceRepository.saveAll(invoicesToUpdate);
        }
    }

    private BigDecimal defaultIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private StoredFile storeFile(MultipartFile file) {
        String originalFileName = StringUtils.cleanPath(
                Objects.requireNonNullElse(file.getOriginalFilename(), "invoice"));
        String storedFileName = UUID.randomUUID() + "-" + originalFileName.replace(" ", "_");
        Path targetLocation = uploadPath.resolve(storedFileName).normalize();

        if (!targetLocation.startsWith(uploadPath)) {
            throw new IllegalArgumentException("Invalid file name");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store invoice file", exception);
        }

        return new StoredFile(originalFileName, storedFileName, targetLocation);
    }

    private void cleanupStoredFile(Path filePath, RuntimeException exception) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException cleanupException) {
            exception.addSuppressed(cleanupException);
        }
    }

    private void deleteStoredFileQuietly(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException ignored) {
            // File cleanup should not block invoice deletion.
        }
    }

    private boolean requiresReview(Invoice invoice) {
        return invoice.getInvoiceNumber() == null
                || invoice.getVendorName() == null
                || invoice.getTotalAmount() == null
                || invoice.getInvoiceDate() == null
                || invoice.getCurrency() == null;
    }

    private String normalizeNullable(String value) {
        return InvoiceFieldNormalizer.normalizeText(value);
    }

    private String normalizeVendor(String value) {
        return InvoiceFieldNormalizer.normalizeVendorName(value);
    }

    private String normalizeCurrency(String value) {
        return InvoiceFieldNormalizer.normalizeCurrency(value);
    }

    private LocalDate parseReviewDate(String value) {
        String normalizedValue = normalizeNullable(value);
        if (normalizedValue == null) {
            return null;
        }

        String alternateCandidate = normalizedValue.replace('/', '-').replace('.', '-');
        for (DateTimeFormatter formatter : REVIEW_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalizedValue, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported review date format.
            }

            try {
                return LocalDate.parse(alternateCandidate, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported review date format.
            }
        }

        throw new IllegalArgumentException("Issue date must be a valid date.");
    }

    private InvoiceParseStatus determineParseStatus(InvoiceExtractionResult extractionResult) {
        if (extractionResult == null || !extractionResult.hasAnyStructuredData()) {
            return InvoiceParseStatus.FAILED;
        }
        if (extractionResult.hasCriticalFields()) {
            return InvoiceParseStatus.SUCCESS;
        }
        return InvoiceParseStatus.PARTIAL;
    }

    private BigDecimal computeParseConfidence(InvoiceExtractionResult extractionResult) {
        if (extractionResult == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal populatedFields = BigDecimal.valueOf(extractionResult.populatedFieldCount());
        return populatedFields
                .divide(BigDecimal.valueOf(9), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeParseConfidence(Invoice invoice) {
        int populatedFieldCount = 0;
        populatedFieldCount += invoice.getVendorName() != null ? 1 : 0;
        populatedFieldCount += invoice.getInvoiceNumber() != null ? 1 : 0;
        populatedFieldCount += invoice.getInvoiceDate() != null ? 1 : 0;
        populatedFieldCount += invoice.getTotalAmount() != null ? 1 : 0;
        populatedFieldCount += invoice.getCurrency() != null ? 1 : 0;
        populatedFieldCount += invoice.getTaxAmount() != null ? 1 : 0;
        populatedFieldCount += invoice.getDueDate() != null ? 1 : 0;
        populatedFieldCount += invoice.getPaymentTerms() != null ? 1 : 0;
        populatedFieldCount += invoice.getInvoiceDescription() != null ? 1 : 0;
        return BigDecimal.valueOf(populatedFieldCount)
                .divide(BigDecimal.valueOf(9), 2, RoundingMode.HALF_UP);
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String formatDate(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getVendor(),
                invoice.getVendorName(),
                invoice.getInvoiceNumber(),
                invoice.getAmount(),
                invoice.getTotalAmount(),
                invoice.getQuantity(),
                invoice.getUnitPrice(),
                invoice.getSubtotalAmount(),
                invoice.getTaxAmount(),
                invoice.getCurrency(),
                invoice.getInvoiceDate(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getPaymentTerms(),
                invoice.getInvoiceDescription(),
                invoice.getStatus(),
                invoice.getParseStatus(),
                invoice.getProcessingStatus(),
                invoice.getParseConfidence(),
                invoice.getRawExtractedText(),
                invoice.isNeedsReview(),
                invoice.isDuplicateFlag(),
                invoice.getDuplicateReason(),
                invoice.getExtractionError(),
                invoice.getOriginalFileName(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }

    private record StoredFile(String originalFileName, String storedFileName, Path targetLocation) {
    }
}
