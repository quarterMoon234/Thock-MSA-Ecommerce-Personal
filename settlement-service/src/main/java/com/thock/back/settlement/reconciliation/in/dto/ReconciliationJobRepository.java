package com.thock.back.settlement.reconciliation.in.dto;

import com.thock.back.settlement.reconciliation.domain.ReconciliationJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationJobRepository extends JpaRepository<ReconciliationJob, Long> {
}
