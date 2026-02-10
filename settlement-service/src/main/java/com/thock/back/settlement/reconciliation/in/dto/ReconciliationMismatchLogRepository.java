package com.thock.back.settlement.reconciliation.in.dto;

import com.thock.back.settlement.reconciliation.domain.ReconciliationMismatchLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationMismatchLogRepository extends JpaRepository<ReconciliationMismatchLog, Long> {
}
