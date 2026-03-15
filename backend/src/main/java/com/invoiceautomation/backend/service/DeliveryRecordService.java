package com.invoiceautomation.backend.service;

import com.invoiceautomation.backend.dto.DeliveryRecordCreateRequest;
import com.invoiceautomation.backend.dto.DeliveryRecordResponse;
import com.invoiceautomation.backend.entity.DeliveryRecord;
import com.invoiceautomation.backend.repository.DeliveryRecordRepository;
import com.invoiceautomation.backend.util.InvoiceFieldNormalizer;
import jakarta.transaction.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DeliveryRecordService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRecordService.class);

    private final DeliveryRecordRepository deliveryRecordRepository;

    public DeliveryRecordService(DeliveryRecordRepository deliveryRecordRepository) {
        this.deliveryRecordRepository = deliveryRecordRepository;
    }

    @Transactional
    public DeliveryRecordResponse createRecord(DeliveryRecordCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Delivery record payload is required.");
        }

        String originalFileName = normalizeText(request.getOriginalFileName());
        if (originalFileName == null) {
            throw new IllegalArgumentException("Original file name is required.");
        }

        if (!hasAnyStructuredField(request)) {
            throw new IllegalArgumentException("At least one extracted logistics field is required.");
        }

        DeliveryRecord deliveryRecord = new DeliveryRecord();
        deliveryRecord.setItemName(normalizeText(request.getItemName()));
        deliveryRecord.setQuantity(normalizeQuantity(request.getQuantity()));
        deliveryRecord.setDeliveryDate(normalizeText(request.getDate()));
        deliveryRecord.setLocation(normalizeText(request.getLocation()));
        deliveryRecord.setPoNumber(normalizeText(request.getPoNumber()));
        deliveryRecord.setEntryNote(normalizeText(request.getEntryNote()));
        deliveryRecord.setRawText(normalizeText(request.getRawText()));
        deliveryRecord.setOriginalFileName(originalFileName);

        DeliveryRecord savedRecord = deliveryRecordRepository.save(deliveryRecord);
        log.info("Saved delivery OCR record: id={}, file={}", savedRecord.getId(), savedRecord.getOriginalFileName());
        return toResponse(savedRecord);
    }

    @Transactional
    public List<DeliveryRecordResponse> findRecords() {
        return deliveryRecordRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public byte[] exportRecords() {
        List<DeliveryRecord> records = deliveryRecordRepository.findAllByOrderByCreatedAtDesc();

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Delivery OCR");
            Row headerRow = sheet.createRow(0);

            String[] headers = {
                    "Original File Name",
                    "Item Name",
                    "Quantity",
                    "Date",
                    "Location",
                    "PO Number",
                    "Entry Note",
                    "Raw Text",
                    "Created At",
                    "Updated At"
            };

            var headerStyle = workbook.createCellStyle();
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            for (int index = 0; index < headers.length; index++) {
                headerRow.createCell(index).setCellValue(headers[index]);
                headerRow.getCell(index).setCellStyle(headerStyle);
            }

            for (int rowIndex = 0; rowIndex < records.size(); rowIndex++) {
                DeliveryRecord record = records.get(rowIndex);
                Row row = sheet.createRow(rowIndex + 1);
                row.createCell(0).setCellValue(defaultString(record.getOriginalFileName()));
                row.createCell(1).setCellValue(defaultString(record.getItemName()));
                row.createCell(2).setCellValue(defaultString(formatDecimal(record.getQuantity())));
                row.createCell(3).setCellValue(defaultString(record.getDeliveryDate()));
                row.createCell(4).setCellValue(defaultString(record.getLocation()));
                row.createCell(5).setCellValue(defaultString(record.getPoNumber()));
                row.createCell(6).setCellValue(defaultString(record.getEntryNote()));
                row.createCell(7).setCellValue(defaultString(record.getRawText()));
                row.createCell(8).setCellValue(record.getCreatedAt().toString());
                row.createCell(9).setCellValue(record.getUpdatedAt().toString());
            }

            for (int index = 0; index < headers.length; index++) {
                sheet.autoSizeColumn(index);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to export delivery records to Excel.", exception);
        }
    }

    private boolean hasAnyStructuredField(DeliveryRecordCreateRequest request) {
        return normalizeText(request.getItemName()) != null
                || request.getQuantity() != null
                || normalizeText(request.getDate()) != null
                || normalizeText(request.getLocation()) != null
                || normalizeText(request.getPoNumber()) != null
                || normalizeText(request.getEntryNote()) != null;
    }

    private String normalizeText(String value) {
        return InvoiceFieldNormalizer.normalizeText(value);
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        return quantity == null ? null : quantity.stripTrailingZeros();
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private DeliveryRecordResponse toResponse(DeliveryRecord record) {
        return new DeliveryRecordResponse(
                record.getId(),
                record.getItemName(),
                record.getQuantity(),
                record.getDeliveryDate(),
                record.getLocation(),
                record.getPoNumber(),
                record.getEntryNote(),
                record.getRawText(),
                record.getOriginalFileName(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
