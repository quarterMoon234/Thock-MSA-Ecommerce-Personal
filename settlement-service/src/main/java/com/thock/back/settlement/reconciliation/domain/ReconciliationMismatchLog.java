package com.thock.back.settlement.reconciliation.domain;

import com.github.f4b6a3.tsid.TsidCreator;
import com.thock.back.settlement.reconciliation.domain.enums.MismatchType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reconciliation_mismatch_log")
public class ReconciliationMismatchLog {

    @Id
    private Long id; // TSID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private ReconciliationJob job;

    @Column(nullable = false)
    private String orderNo;

    private String pgKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MismatchType type;

    // [수정] BigDecimal -> Long (원화 기준)
    private Long internalAmount;
    private Long pgAmount;
    private Long diffAmount;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = TsidCreator.getTsid().toLong();
        }
    }

    @Builder
    public ReconciliationMismatchLog(ReconciliationJob job, String orderNo, String pgKey,
                                     MismatchType type, Long internalAmount, Long pgAmount, String reason) {
        this.job = job;
        this.orderNo = orderNo;
        this.pgKey = pgKey;
        this.type = type;
        this.internalAmount = internalAmount;
        this.pgAmount = pgAmount;
        this.reason = reason;

        // [수정] Long 타입 Null Safe 연산
        // 값이 없으면 0원으로 취급해서 계산
        long safePg = pgAmount != null ? pgAmount : 0L;
        long safeInternal = internalAmount != null ? internalAmount : 0L;
        this.diffAmount = safePg - safeInternal;
    }
}