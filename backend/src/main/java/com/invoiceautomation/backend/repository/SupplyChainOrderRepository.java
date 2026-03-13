package com.invoiceautomation.backend.repository;

import com.invoiceautomation.backend.entity.SupplyChainOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyChainOrderRepository extends JpaRepository<SupplyChainOrder, Long> {

    @EntityGraph(attributePaths = "milestones")
    List<SupplyChainOrder> findAllByOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = "milestones")
    Optional<SupplyChainOrder> findById(Long id);

    boolean existsByOrderNumberIgnoreCase(String orderNumber);

    boolean existsByOrderNumberIgnoreCaseAndIdNot(String orderNumber, Long id);

    Optional<SupplyChainOrder> findByOrderNumberIgnoreCase(String orderNumber);
}
