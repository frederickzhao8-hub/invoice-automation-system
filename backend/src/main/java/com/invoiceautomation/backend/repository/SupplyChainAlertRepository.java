package com.invoiceautomation.backend.repository;

import com.invoiceautomation.backend.entity.AlertStatus;
import com.invoiceautomation.backend.entity.SupplyChainAlert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyChainAlertRepository extends JpaRepository<SupplyChainAlert, Long> {

    List<SupplyChainAlert> findAllByStatusOrderByTriggeredAtDesc(AlertStatus status);

    List<SupplyChainAlert> findAllByOrderIdOrderByTriggeredAtDesc(Long orderId);

    List<SupplyChainAlert> findAllByOrderIdIn(List<Long> orderIds);

    void deleteByOrderId(Long orderId);
}
