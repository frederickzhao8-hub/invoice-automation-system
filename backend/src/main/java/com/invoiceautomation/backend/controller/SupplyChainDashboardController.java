package com.invoiceautomation.backend.controller;

import com.invoiceautomation.backend.dto.SupplyChainDashboardResponse;
import com.invoiceautomation.backend.service.SupplyChainService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/supply-chain/dashboard")
public class SupplyChainDashboardController {

    private final SupplyChainService supplyChainService;

    public SupplyChainDashboardController(SupplyChainService supplyChainService) {
        this.supplyChainService = supplyChainService;
    }

    @GetMapping
    public SupplyChainDashboardResponse getDashboard() {
        return supplyChainService.getDashboard();
    }
}
