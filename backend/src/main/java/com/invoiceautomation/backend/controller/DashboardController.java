package com.invoiceautomation.backend.controller;

import com.invoiceautomation.backend.dto.DashboardSummaryResponse;
import com.invoiceautomation.backend.service.InvoiceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final InvoiceService invoiceService;

    public DashboardController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public DashboardSummaryResponse getSummary() {
        return invoiceService.getDashboardSummary();
    }
}

