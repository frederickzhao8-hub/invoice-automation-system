package com.invoiceautomation.backend.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SupplyChainSeedDataInitializer implements ApplicationRunner {

    private final SupplyChainService supplyChainService;

    public SupplyChainSeedDataInitializer(SupplyChainService supplyChainService) {
        this.supplyChainService = supplyChainService;
    }

    @Override
    public void run(ApplicationArguments args) {
        supplyChainService.initializeDefaultSlaRules();
        supplyChainService.initializeSampleOrders();
    }
}
