package com.invoiceautomation.backend.repository;

import com.invoiceautomation.backend.entity.DeliveryRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecord, Long> {

    List<DeliveryRecord> findAllByOrderByCreatedAtDesc();
}
