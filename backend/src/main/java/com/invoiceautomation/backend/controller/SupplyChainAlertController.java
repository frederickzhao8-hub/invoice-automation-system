package com.invoiceautomation.backend.controller;

import com.invoiceautomation.backend.dto.AlertResponse;
import com.invoiceautomation.backend.entity.AlertStatus;
import com.invoiceautomation.backend.service.SupplyChainService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/supply-chain/alerts")
public class SupplyChainAlertController {

    private final SupplyChainService supplyChainService;

    public SupplyChainAlertController(SupplyChainService supplyChainService) {
        this.supplyChainService = supplyChainService;
    }

    @GetMapping
    public List<AlertResponse> getAlerts(@RequestParam(required = false) AlertStatus status) {
        return supplyChainService.getAlerts(status);
    }
}
