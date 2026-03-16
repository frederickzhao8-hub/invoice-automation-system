package com.invoiceautomation.backend.repository;

import com.invoiceautomation.backend.entity.OrderMilestoneImportHistory;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderMilestoneImportHistoryRepository extends JpaRepository<OrderMilestoneImportHistory, Long> {

    @EntityGraph(attributePaths = "order")
    List<OrderMilestoneImportHistory> findTop50ByOrderByImportedAtDesc();
}
