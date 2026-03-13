package com.invoiceautomation.backend.repository;

import com.invoiceautomation.backend.entity.MilestoneType;
import com.invoiceautomation.backend.entity.OrderMilestone;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderMilestoneRepository extends JpaRepository<OrderMilestone, Long> {

    Optional<OrderMilestone> findByOrderIdAndMilestoneType(Long orderId, MilestoneType milestoneType);
}
