package com.thock.back.settlement.settlement.domain;

import com.github.f4b6a3.tsid.TsidCreator;
import com.thock.back.settlement.settlement.domain.enums.PayoutStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "finance_settlement_monthly")
public class MonthlySettlement {

    @Id
    private Long id; // TSID

    // --- [누구의 돈인가?] ---
    @Column(name = "seller_id")
    private Long sellerId; // 판매자 ID

    @Column(name = "target_year_month")
    private String targetYearMonth; // 예: "202602" (2월 정산분)

    // --- [얼마 줄 것인가? (집계)] ---
    @Column(name = "total_count")
    private Long totalCount; // 이번 달 정산 건수 (Daily 개수 합)

    @Column(name = "total_payment_amount")
    private Long totalPaymentAmount; // 총 거래액

    @Column(name = "total_fee_amount")
    private Long totalFeeAmount; // 총 수수료

    @Column(name = "total_payout_amount")
    private Long totalPayoutAmount; // ⭐ 최종 입금액 (원금 - 수수료)

    // --- [지급 상태] ---
    @Column(name = "monthly_settlement_status")
    @Enumerated(EnumType.STRING)
    private PayoutStatus status; // PENDING(집계완료) -> PAID(입금완료)

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            // 저장하기 전에 TSID를 생성해서 넣음
            this.id = TsidCreator.getTsid().toLong();
        }
    }
}