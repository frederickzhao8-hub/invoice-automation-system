package com.invoiceautomation.backend.controller;

import com.invoiceautomation.backend.dto.MilestoneRecordRequest;
import com.invoiceautomation.backend.dto.SupplyChainOrderDetailResponse;
import com.invoiceautomation.backend.dto.SupplyChainOrderRequest;
import com.invoiceautomation.backend.dto.SupplyChainOrderSummaryResponse;
import com.invoiceautomation.backend.entity.MilestoneType;
import com.invoiceautomation.backend.entity.OrderHealthStatus;
import com.invoiceautomation.backend.service.SupplyChainService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/supply-chain/orders")
public class SupplyChainOrderController {

    private final SupplyChainService supplyChainService;

    public SupplyChainOrderController(SupplyChainService supplyChainService) {
        this.supplyChainService = supplyChainService;
    }

    @GetMapping
    public List<SupplyChainOrderSummaryResponse> getOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OrderHealthStatus healthStatus) {
        return supplyChainService.getOrders(search, healthStatus);
    }

    @GetMapping("/{id}")
    public SupplyChainOrderDetailResponse getOrder(@PathVariable Long id) {
        return supplyChainService.getOrder(id);
    }

    @PostMapping
    public ResponseEntity<SupplyChainOrderDetailResponse> createOrder(
            @Valid @RequestBody SupplyChainOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplyChainService.createOrder(request));
    }

    @PutMapping("/{id}")
    public SupplyChainOrderDetailResponse updateOrder(
            @PathVariable Long id,
            @Valid @RequestBody SupplyChainOrderRequest request) {
        return supplyChainService.updateOrder(id, request);
    }

    @PutMapping("/{id}/milestones/{milestoneType}")
    public SupplyChainOrderDetailResponse recordMilestone(
            @PathVariable Long id,
            @PathVariable MilestoneType milestoneType,
            @Valid @RequestBody MilestoneRecordRequest request) {
        return supplyChainService.recordMilestone(id, milestoneType, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        supplyChainService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
