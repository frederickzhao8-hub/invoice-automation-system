package com.invoiceautomation.backend.repository;

import com.invoiceautomation.backend.entity.SlaRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaRuleRepository extends JpaRepository<SlaRule, Long> {

    List<SlaRule> findByActiveTrueOrderByIdAsc();
}
