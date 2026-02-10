package com.thock.back.settlement.reconciliation.app.UseCase;

import com.thock.back.settlement.reconciliation.domain.PgSalesRaw;
import com.thock.back.settlement.reconciliation.domain.ReconciliationJob;
import com.thock.back.settlement.reconciliation.domain.ReconciliationMismatchLog;
import com.thock.back.settlement.reconciliation.domain.SalesLog;
import com.thock.back.settlement.reconciliation.domain.enums.MismatchType;
import com.thock.back.settlement.reconciliation.domain.enums.PgStatus;
import com.thock.back.settlement.reconciliation.domain.enums.ReconciliationStatus;
import com.thock.back.settlement.reconciliation.in.dto.ReconciliationJobRepository;
import com.thock.back.settlement.reconciliation.in.dto.ReconciliationMismatchLogRepository;
import com.thock.back.settlement.reconciliation.out.PgSalesRawRepository;
import com.thock.back.settlement.reconciliation.out.SalesLogRepository;
import com.thock.back.settlement.shared.enums.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunReconciliationUseCase {
    private final SalesLogRepository salesLogRepository;
    private final PgSalesRawRepository pgSalesRawRepository;
    private final ReconciliationJobRepository jobRepository;
    private final ReconciliationMismatchLogRepository mismatchLogRepository;

    @Transactional
    public void execute(LocalDate date) {
        log.info("=========[대사 시작] 기준일: {} =========", date);

        // 대사 결과를 담는 job 생성
        ReconciliationJob job = ReconciliationJob.builder().
                baseDate(date).build();
        jobRepository.save(job);

        // PG 데이터 조회
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        List<PgSalesRaw> pgList = pgSalesRawRepository.findAllByTransactedAtBetween(startOfDay, endOfDay);

        int successCount = 0;
        int mismatchCount = 0;

        for (PgSalesRaw pgSale : pgList) {

            // 1. PgStatus - TransactionType의 관계는 PAID-PAYMENT, CANCELED-CANCEL 두개 밖에 없다 가정
            // 서로 매핑 시켜줌
            TransactionType targetType = convertToTransactionType(pgSale.getPgStatus());
            if (targetType == null) continue;

            // 2. PG데이터 기준으로 내부 데이터 검색
            List<SalesLog> internalLogs = salesLogRepository.findByOrderNoAndTransactionType(
                    pgSale.getMerchantUid(),
                    targetType
            );

            // saveMismatchLog를 서비스단에서 구현한 이유는?
            // 주문서 누락시
            if (internalLogs.isEmpty()) {
                saveMismatchLog(job, pgSale, null, MismatchType.PG_ONLY, "주문서 누락");
                mismatchCount++;
                continue;
            }

            // stream을 이용에 한번에 변환
            long dbSum = internalLogs.stream().mapToLong(SalesLog::getPaymentAmount).sum();

            // 결제 or 환불 관계없이 금액 일치/여부에따라 성공,실패 처리
            if (pgSale.getPaymentAmount() == Math.abs(dbSum)) {
                internalLogs.forEach(SalesLog::matchReconciliation);
                successCount++;
            } else {
                internalLogs.forEach(SalesLog::mismatchReconciliation);
                saveMismatchLog(job, pgSale, internalLogs.get(0), MismatchType.AMOUNT_DIFF, "금액 불일치");
            }
        }
        // 3. 2번 과정을 거쳐도 혹시 모르게 남아있는 내부 데이터를 찾아내야함
        // 내부 데이터 중, date & PENDING 인 것들을 찾아야함
        List<SalesLog> remainLogs = salesLogRepository.findAllBySnapshotAtBetweenAndReconciliationStatus(
                startOfDay,
                endOfDay,
                ReconciliationStatus.PENDING
        );

        // mismatch 처리
        for (SalesLog remainLog : remainLogs) {
            remainLog.mismatchReconciliation();
            saveMismatchLog(job, null, remainLog, MismatchType.INTERNAL_ONLY, "PG 내역 없음");
            mismatchCount++;
        }
        job.finish(pgList.size(), successCount, mismatchCount);
        log.info("=========[대사 종료] 성공: {}, 실패: {}", successCount, mismatchCount);
    }

    private TransactionType convertToTransactionType(PgStatus pgStatus) {
        if (pgStatus == PgStatus.PAID) {
            return TransactionType.PAYMENT;
        }
        if (pgStatus == PgStatus.CANCELED) {
            return TransactionType.REFUND;
        }
        return null;
    }

    private void saveMismatchLog(ReconciliationJob job, PgSalesRaw pg, SalesLog internal,
                                 MismatchType type, String reason) {

        // 실패 시에 null일 수도 있으니까 npe 오류 대비
        String orderNo = (pg != null) ? pg.getMerchantUid() : internal.getOrderNo();
        String pgKey = (pg != null) ? pg.getPgKey() : null;
        Long pgAmount = (pg != null && pg.getPaymentAmount() != null) ? pg.getPaymentAmount() : 0L;
        Long internalAmount = (internal != null && internal.getPaymentAmount() != null) ? internal.getPaymentAmount() : 0L;

        ReconciliationMismatchLog log = ReconciliationMismatchLog.builder()
                .job(job)
                .orderNo(orderNo)
                .pgKey(pgKey)
                .type(type)
                .pgAmount(pgAmount)
                .internalAmount(internalAmount)
                .reason(reason)
                .build();
        mismatchLogRepository.save(log);
    }
}

