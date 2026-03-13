package com.invoiceautomation.backend.controller;

import com.invoiceautomation.backend.dto.SlaRuleResponse;
import com.invoiceautomation.backend.dto.SlaRuleUpdateRequest;
import com.invoiceautomation.backend.service.SupplyChainService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/supply-chain/sla-rules")
public class SlaRuleController {

    private final SupplyChainService supplyChainService;

    public SlaRuleController(SupplyChainService supplyChainService) {
        this.supplyChainService = supplyChainService;
    }

    @GetMapping
    public List<SlaRuleResponse> getSlaRules() {
        return supplyChainService.getSlaRules();
    }

    @PutMapping("/{id}")
    public SlaRuleResponse updateRule(
            @PathVariable Long id,
            @Valid @RequestBody SlaRuleUpdateRequest request) {
        return supplyChainService.updateSlaRule(id, request);
    }
}
