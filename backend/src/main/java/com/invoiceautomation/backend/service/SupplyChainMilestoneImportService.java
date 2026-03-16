package com.invoiceautomation.backend.service;

import com.invoiceautomation.backend.dto.MilestoneImportHistoryResponse;
import com.invoiceautomation.backend.dto.SupplyChainMilestoneImportItemResult;
import com.invoiceautomation.backend.dto.SupplyChainMilestoneImportResult;
import com.invoiceautomation.backend.entity.MilestoneType;
import com.invoiceautomation.backend.entity.OrderMilestone;
import com.invoiceautomation.backend.entity.OrderMilestoneImportHistory;
import com.invoiceautomation.backend.entity.SupplyChainOrder;
import com.invoiceautomation.backend.repository.OrderMilestoneImportHistoryRepository;
import com.invoiceautomation.backend.repository.SupplyChainOrderRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SupplyChainMilestoneImportService {

    private static final Logger log = LoggerFactory.getLogger(SupplyChainMilestoneImportService.class);
    private static final Set<String> EXCEL_CONTENT_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/octet-stream");
    private static final Set<String> ORDER_NUMBER_HEADERS = Set.of(
            "ordernumber",
            "orderno",
            "ponumber",
            "purchaseno",
            "purchaseordernumber");
    private static final Set<String> CUSTOMER_HEADERS = Set.of("customer", "customername");
    private static final Set<String> SUPPLIER_HEADERS = Set.of("supplier", "suppliername");
    private static final Set<String> PRODUCT_HEADERS = Set.of("product", "productname", "item");
    private static final Set<String> ORIGIN_HEADERS = Set.of("origincountry", "origin");
    private static final Set<String> DESTINATION_HEADERS = Set.of("destinationcountry", "destination");
    private static final Set<String> QUANTITY_HEADERS = Set.of("quantity", "qty");
    private static final Set<String> NOTES_HEADERS = Set.of("notes", "comment", "comments");
    private static final Map<String, MilestoneType> MILESTONE_HEADERS = Map.ofEntries(
            Map.entry("poreceived", MilestoneType.PO_RECEIVED),
            Map.entry("poreceivedat", MilestoneType.PO_RECEIVED),
            Map.entry("productioncompleted", MilestoneType.PRODUCTION_COMPLETED),
            Map.entry("productioncomplete", MilestoneType.PRODUCTION_COMPLETED),
            Map.entry("productioncompletedat", MilestoneType.PRODUCTION_COMPLETED),
            Map.entry("shipped", MilestoneType.SHIPPED),
            Map.entry("shippedat", MilestoneType.SHIPPED),
            Map.entry("arrivedport", MilestoneType.ARRIVED_PORT),
            Map.entry("arriveport", MilestoneType.ARRIVED_PORT),
            Map.entry("arrivedatport", MilestoneType.ARRIVED_PORT),
            Map.entry("customscleared", MilestoneType.CUSTOMS_CLEARED),
            Map.entry("customsclearedat", MilestoneType.CUSTOMS_CLEARED),
            Map.entry("delivered", MilestoneType.DELIVERED),
            Map.entry("deliveredat", MilestoneType.DELIVERED));
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("M/d/uuuu H:mm"),
            DateTimeFormatter.ofPattern("M/d/uuuu HH:mm"),
            DateTimeFormatter.ofPattern("MM/dd/uuuu HH:mm"),
            DateTimeFormatter.ofPattern("d/M/uuuu H:mm"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"));
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("uuuu/MM/dd"),
            DateTimeFormatter.ofPattern("M/d/uuuu"),
            DateTimeFormatter.ofPattern("MM/dd/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"));

    private final SupplyChainOrderRepository orderRepository;
    private final OrderMilestoneImportHistoryRepository historyRepository;
    private final TransactionTemplate transactionTemplate;

    public SupplyChainMilestoneImportService(
            SupplyChainOrderRepository orderRepository,
            OrderMilestoneImportHistoryRepository historyRepository,
            PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
    }

    public SupplyChainMilestoneImportResult importWorkbook(MultipartFile file) {
        validateWorkbook(file);

        try (InputStream inputStream = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter dataFormatter = new DataFormatter();
            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
            ImportTemplate importTemplate = buildImportTemplate(sheet.getRow(sheet.getFirstRowNum()), dataFormatter);
            List<SupplyChainMilestoneImportItemResult> results = new ArrayList<>();

            int totalRows = 0;
            int successCount = 0;
            int skippedCount = 0;
            int failureCount = 0;
            int historyEntriesCreated = 0;
            String sourceFileName = normalizeNullable(file.getOriginalFilename());

            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlankRow(row, dataFormatter, formulaEvaluator)) {
                    continue;
                }

                totalRows++;
                final int excelRowNumber = rowIndex + 1;
                SupplyChainMilestoneImportItemResult itemResult = transactionTemplate.execute(status -> {
                    try {
                        return importRow(row, importTemplate, dataFormatter, formulaEvaluator, sourceFileName);
                    } catch (RuntimeException exception) {
                        status.setRollbackOnly();
                        String orderNumber = readCellText(row.getCell(importTemplate.orderNumberColumnIndex()), dataFormatter, formulaEvaluator);
                        log.warn("Failed to import supply-chain milestone row {}", excelRowNumber, exception);
                        return new SupplyChainMilestoneImportItemResult(
                                excelRowNumber,
                                normalizeNullable(orderNumber),
                                "FAILED",
                                List.of(),
                                0,
                                exception.getMessage() == null ? "Unable to import row." : exception.getMessage());
                    }
                });

                results.add(itemResult);
                historyEntriesCreated += itemResult.historyEntriesCreated();

                switch (itemResult.status()) {
                    case "SUCCESS" -> successCount++;
                    case "SKIPPED" -> skippedCount++;
                    default -> failureCount++;
                }
            }

            return new SupplyChainMilestoneImportResult(
                    totalRows,
                    successCount,
                    skippedCount,
                    failureCount,
                    historyEntriesCreated,
                    List.copyOf(results));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the uploaded Excel file.", exception);
        }
    }

    public List<MilestoneImportHistoryResponse> getRecentHistory(int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 50));
        return historyRepository.findTop50ByOrderByImportedAtDesc().stream()
                .limit(cappedLimit)
                .map(history -> new MilestoneImportHistoryResponse(
                        history.getId(),
                        history.getOrder().getId(),
                        history.getOrder().getOrderNumber(),
                        history.getMilestoneType(),
                        history.getMilestoneType().displayName(),
                        history.getPreviousOccurredAt(),
                        history.getNewOccurredAt(),
                        history.getPreviousNotes(),
                        history.getNewNotes(),
                        history.getSourceFileName(),
                        history.getImportedAt()))
                .toList();
    }

    private SupplyChainMilestoneImportItemResult importRow(
            Row row,
            ImportTemplate importTemplate,
            DataFormatter dataFormatter,
            FormulaEvaluator formulaEvaluator,
            String sourceFileName) {
        String orderNumber = normalizeNullable(
                readCellText(row.getCell(importTemplate.orderNumberColumnIndex()), dataFormatter, formulaEvaluator));
        if (orderNumber == null) {
            throw new IllegalArgumentException("Order Number is required for each Excel row.");
        }

        SupplyChainOrder order = orderRepository.findByOrderNumberIgnoreCase(orderNumber)
                .orElseGet(() -> buildImportedOrder(
                        row,
                        importTemplate,
                        dataFormatter,
                        formulaEvaluator,
                        orderNumber));
        boolean createdOrder = order.getId() == null;

        List<OrderMilestoneImportHistory> historyEntries = new ArrayList<>();
        List<String> updatedMilestones = new ArrayList<>();
        boolean anyMilestoneValueProvided = false;

        for (MilestoneType milestoneType : MilestoneType.flow()) {
            Integer columnIndex = importTemplate.milestoneColumns().get(milestoneType);
            if (columnIndex == null) {
                continue;
            }

            Cell cell = row.getCell(columnIndex);
            LocalDateTime importedTimestamp = readCellDateTime(cell, dataFormatter, formulaEvaluator);
            if (importedTimestamp == null) {
                continue;
            }

            anyMilestoneValueProvided = true;
            OrderMilestone existingMilestone = findMilestone(order, milestoneType);
            LocalDateTime previousOccurredAt = existingMilestone == null ? null : existingMilestone.getOccurredAt();
            String previousNotes = existingMilestone == null ? null : existingMilestone.getNotes();

            if (Objects.equals(previousOccurredAt, importedTimestamp)) {
                continue;
            }

            upsertMilestone(order, milestoneType, importedTimestamp);

            OrderMilestoneImportHistory historyEntry = new OrderMilestoneImportHistory();
            historyEntry.setOrder(order);
            historyEntry.setMilestoneType(milestoneType);
            historyEntry.setPreviousOccurredAt(previousOccurredAt);
            historyEntry.setNewOccurredAt(importedTimestamp);
            historyEntry.setPreviousNotes(previousNotes);
            historyEntry.setNewNotes(previousNotes);
            historyEntry.setSourceFileName(sourceFileName == null ? "supply-chain-import.xlsx" : sourceFileName);
            historyEntries.add(historyEntry);
            updatedMilestones.add(milestoneType.displayName());
        }

        if (!anyMilestoneValueProvided) {
            return new SupplyChainMilestoneImportItemResult(
                    row.getRowNum() + 1,
                    order.getOrderNumber(),
                    "SKIPPED",
                    List.of(),
                    0,
                    "No milestone timestamps were provided in this row.");
        }

        if (updatedMilestones.isEmpty()) {
            return new SupplyChainMilestoneImportItemResult(
                    row.getRowNum() + 1,
                    order.getOrderNumber(),
                    "SKIPPED",
                    List.of(),
                    0,
                    "No milestone changes were detected for this order.");
        }

        validateMilestoneFlow(order);
        orderRepository.save(order);
        historyRepository.saveAll(historyEntries);

        return new SupplyChainMilestoneImportItemResult(
                row.getRowNum() + 1,
                order.getOrderNumber(),
                "SUCCESS",
                List.copyOf(updatedMilestones),
                historyEntries.size(),
                createdOrder
                        ? "Created missing order and imported milestone timestamps successfully."
                        : "Imported milestone timestamps successfully.");
    }

    private ImportTemplate buildImportTemplate(Row headerRow, DataFormatter dataFormatter) {
        if (headerRow == null) {
            throw new IllegalArgumentException("The Excel file must contain a header row.");
        }

        Map<String, Integer> headerIndexes = new java.util.HashMap<>();
        for (Cell cell : headerRow) {
            String headerName = normalizeHeader(dataFormatter.formatCellValue(cell));
            if (!headerName.isEmpty()) {
                headerIndexes.putIfAbsent(headerName, cell.getColumnIndex());
            }
        }

        Integer orderNumberColumnIndex = ORDER_NUMBER_HEADERS.stream()
                .map(headerIndexes::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Excel import requires a PO Number or Order Number column."));

        Map<MilestoneType, Integer> milestoneColumns = new EnumMap<>(MilestoneType.class);
        MILESTONE_HEADERS.forEach((headerName, milestoneType) -> {
            Integer columnIndex = headerIndexes.get(headerName);
            if (columnIndex != null) {
                milestoneColumns.putIfAbsent(milestoneType, columnIndex);
            }
        });

        if (milestoneColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Excel import requires at least one milestone column such as PO Received, Production Completed, Shipped, or Arrived Port.");
        }

        return new ImportTemplate(
                orderNumberColumnIndex,
                milestoneColumns,
                findFirstHeaderIndex(headerIndexes, CUSTOMER_HEADERS),
                findFirstHeaderIndex(headerIndexes, SUPPLIER_HEADERS),
                findFirstHeaderIndex(headerIndexes, PRODUCT_HEADERS),
                findFirstHeaderIndex(headerIndexes, ORIGIN_HEADERS),
                findFirstHeaderIndex(headerIndexes, DESTINATION_HEADERS),
                findFirstHeaderIndex(headerIndexes, QUANTITY_HEADERS),
                findFirstHeaderIndex(headerIndexes, NOTES_HEADERS));
    }

    private SupplyChainOrder buildImportedOrder(
            Row row,
            ImportTemplate importTemplate,
            DataFormatter dataFormatter,
            FormulaEvaluator formulaEvaluator,
            String orderNumber) {
        LocalDateTime poReceivedAt = readOptionalMilestone(
                row,
                importTemplate,
                dataFormatter,
                formulaEvaluator,
                MilestoneType.PO_RECEIVED);
        if (poReceivedAt == null) {
            throw new IllegalArgumentException(
                    "Order not found: " + orderNumber + ". Add PO Received and base order fields to create it.");
        }

        SupplyChainOrder order = new SupplyChainOrder();
        order.setOrderNumber(orderNumber);
        order.setCustomerName(readRequiredMetadata(row, importTemplate.customerColumnIndex(), dataFormatter, formulaEvaluator, "Customer"));
        order.setSupplierName(readRequiredMetadata(row, importTemplate.supplierColumnIndex(), dataFormatter, formulaEvaluator, "Supplier"));
        order.setProductName(readRequiredMetadata(row, importTemplate.productColumnIndex(), dataFormatter, formulaEvaluator, "Product"));
        order.setOriginCountry(readRequiredMetadata(row, importTemplate.originColumnIndex(), dataFormatter, formulaEvaluator, "Origin Country"));
        order.setDestinationCountry(readRequiredMetadata(row, importTemplate.destinationColumnIndex(), dataFormatter, formulaEvaluator, "Destination Country"));
        order.setQuantity(readOptionalDecimal(
                row,
                importTemplate.quantityColumnIndex(),
                dataFormatter,
                formulaEvaluator,
                BigDecimal.ZERO));
        order.setOrderValue(BigDecimal.ZERO);
        order.setNotes(readOptionalMetadata(row, importTemplate.notesColumnIndex(), dataFormatter, formulaEvaluator));
        return order;
    }

    private void validateWorkbook(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("An Excel file is required.");
        }

        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        boolean hasExcelExtension = fileName.endsWith(".xlsx") || fileName.endsWith(".xls");
        if (!hasExcelExtension && !EXCEL_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only .xlsx and .xls files are supported.");
        }
    }

    private boolean isBlankRow(Row row, DataFormatter dataFormatter, FormulaEvaluator formulaEvaluator) {
        if (row == null) {
            return true;
        }

        for (int cellIndex = row.getFirstCellNum(); cellIndex < row.getLastCellNum(); cellIndex++) {
            if (cellIndex < 0) {
                continue;
            }
            String value = normalizeNullable(readCellText(row.getCell(cellIndex), dataFormatter, formulaEvaluator));
            if (value != null) {
                return false;
            }
        }
        return true;
    }

    private String readCellText(Cell cell, DataFormatter dataFormatter, FormulaEvaluator formulaEvaluator) {
        if (cell == null) {
            return null;
        }

        String value = dataFormatter.formatCellValue(cell, formulaEvaluator);
        return value == null ? null : value.trim();
    }

    private LocalDateTime readCellDateTime(
            Cell cell,
            DataFormatter dataFormatter,
            FormulaEvaluator formulaEvaluator) {
        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }

        if (cell.getCellType() == CellType.FORMULA
                && cell.getCachedFormulaResultType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }

        String rawValue = normalizeNullable(readCellText(cell, dataFormatter, formulaEvaluator));
        if (rawValue == null) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(rawValue, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported pattern.
            }
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(rawValue, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // Try the next supported pattern.
            }
        }

        throw new IllegalArgumentException("Unsupported timestamp format: " + rawValue);
    }

    private LocalDateTime readOptionalMilestone(
            Row row,
            ImportTemplate importTemplate,
            DataFormatter dataFormatter,
            FormulaEvaluator formulaEvaluator,
            MilestoneType milestoneType) {
        Integer columnIndex = importTemplate.milestoneColumns().get(milestoneType);
        if (columnIndex == null) {
            return null;
        }
        return readCellDateTime(row.getCell(columnIndex), dataFormatter, formulaEvaluator);
    }

    private String readRequiredMetadata(
            Row row,
            Integer columnIndex,
            DataFormatter dataFormatter,
            FormulaEvaluator formulaEvaluator,
            String fieldName) {
        String value = readOptionalMetadata(row, columnIndex, dataFormatter, formulaEvaluator);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required to create a missing order from Excel.");
        }
        return value;
    }

    private String readOptionalMetadata(
            Row row,
            Integer columnIndex,
            DataFormatter dataFormatter,
            FormulaEvaluator formulaEvaluator) {
        if (columnIndex == null) {
            return null;
        }
        return normalizeNullable(readCellText(row.getCell(columnIndex), dataFormatter, formulaEvaluator));
    }

    private BigDecimal readOptionalDecimal(
            Row row,
            Integer columnIndex,
            DataFormatter dataFormatter,
            FormulaEvaluator formulaEvaluator,
            BigDecimal defaultValue) {
        if (columnIndex == null) {
            return defaultValue;
        }

        String rawValue = normalizeNullable(readCellText(row.getCell(columnIndex), dataFormatter, formulaEvaluator));
        if (rawValue == null) {
            return defaultValue;
        }

        try {
            String normalized = rawValue.replace(",", "");
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Unsupported quantity format: " + rawValue);
        }
    }

    private OrderMilestone findMilestone(SupplyChainOrder order, MilestoneType milestoneType) {
        return order.getMilestones().stream()
                .filter(candidate -> candidate.getMilestoneType() == milestoneType)
                .findFirst()
                .orElse(null);
    }

    private void upsertMilestone(SupplyChainOrder order, MilestoneType milestoneType, LocalDateTime occurredAt) {
        OrderMilestone milestone = findMilestone(order, milestoneType);
        if (milestone == null) {
            milestone = new OrderMilestone();
            milestone.setMilestoneType(milestoneType);
            milestone.setOccurredAt(occurredAt);
            order.addMilestone(milestone);
            return;
        }

        milestone.setOccurredAt(occurredAt);
    }

    private void validateMilestoneFlow(SupplyChainOrder order) {
        Map<MilestoneType, OrderMilestone> milestonesByType = new EnumMap<>(MilestoneType.class);
        order.getMilestones().forEach(milestone -> milestonesByType.put(milestone.getMilestoneType(), milestone));

        LocalDateTime previousOccurredAt = null;
        boolean gapFound = false;

        for (MilestoneType milestoneType : MilestoneType.flow()) {
            OrderMilestone milestone = milestonesByType.get(milestoneType);
            if (milestone == null) {
                gapFound = true;
                continue;
            }

            if (gapFound) {
                throw new IllegalArgumentException(
                        milestoneType.displayName() + " cannot be recorded before prior milestones.");
            }

            if (previousOccurredAt != null && milestone.getOccurredAt().isBefore(previousOccurredAt)) {
                throw new IllegalArgumentException(
                        milestoneType.displayName() + " must be on or after the previous milestone timestamp.");
            }

            previousOccurredAt = milestone.getOccurredAt();
        }
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private Integer findFirstHeaderIndex(Map<String, Integer> headerIndexes, Set<String> aliases) {
        return aliases.stream()
                .map(headerIndexes::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record ImportTemplate(
            int orderNumberColumnIndex,
            Map<MilestoneType, Integer> milestoneColumns,
            Integer customerColumnIndex,
            Integer supplierColumnIndex,
            Integer productColumnIndex,
            Integer originColumnIndex,
            Integer destinationColumnIndex,
            Integer quantityColumnIndex,
            Integer notesColumnIndex) {
    }
}
