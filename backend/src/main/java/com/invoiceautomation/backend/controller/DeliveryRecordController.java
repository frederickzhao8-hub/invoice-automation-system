package com.invoiceautomation.backend.controller;

import com.invoiceautomation.backend.dto.DeliveryRecordCreateRequest;
import com.invoiceautomation.backend.dto.DeliveryRecordResponse;
import com.invoiceautomation.backend.service.DeliveryRecordService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/delivery-records")
public class DeliveryRecordController {

    private final DeliveryRecordService deliveryRecordService;

    public DeliveryRecordController(DeliveryRecordService deliveryRecordService) {
        this.deliveryRecordService = deliveryRecordService;
    }

    @PostMapping
    public ResponseEntity<DeliveryRecordResponse> createRecord(
            @Valid @RequestBody DeliveryRecordCreateRequest request) {
        DeliveryRecordResponse response = deliveryRecordService.createRecord(request);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping
    public List<DeliveryRecordResponse> getRecords() {
        return deliveryRecordService.findRecords();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportRecords() {
        byte[] workbook = deliveryRecordService.exportRecords();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"delivery-records-export.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbook);
    }
}
