package com.thock.back.settlement.reconciliation.app.UseCase;

import com.thock.back.settlement.reconciliation.domain.*;
import com.thock.back.settlement.reconciliation.domain.enums.*;
import com.thock.back.settlement.reconciliation.in.dto.ReconciliationJobRepository;
import com.thock.back.settlement.reconciliation.in.dto.ReconciliationMismatchLogRepository;
import com.thock.back.settlement.reconciliation.out.PgSalesRawRepository;
import com.thock.back.settlement.reconciliation.out.SalesLogRepository;
import com.thock.back.settlement.shared.enums.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor; // [추가] 저장이 잘 됐나 낚아채서 확인하는 도구
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat; // [핵심] AssertJ import
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) //
class RunReconciliationUseCaseTest {

    @InjectMocks
    private RunReconciliationUseCase useCase;

    @Mock
    private SalesLogRepository salesLogRepository;
    @Mock
    private PgSalesRawRepository pgSalesRawRepository;
    @Mock
    private ReconciliationJobRepository jobRepository;
    @Mock
    private ReconciliationMismatchLogRepository mismatchLogRepository;

    @Test
    @DisplayName("정상 대사: PG와 내부 데이터가 일치할 때")
    void success_match() {
        // given
        LocalDate date = LocalDate.now();

        PgSalesRaw pgData = PgSalesRaw.builder()
                .merchantUid("ORD-001").pgStatus(PgStatus.PAID).paymentAmount(1000L).build();

        SalesLog internalData = SalesLog.builder()
                .orderNo("ORD-001").transactionType(TransactionType.PAYMENT).paymentAmount(1000L).build();

        // when
        when(pgSalesRawRepository.findAllByTransactedAtBetween(any(), any())).thenReturn(List.of(pgData));
        when(salesLogRepository.findByOrderNoAndTransactionType("ORD-001", TransactionType.PAYMENT))
                .thenReturn(List.of(internalData));

        // 역방향 체크용: 빈 리스트 반환 (유령 데이터 없음)
        when(salesLogRepository.findAllBySnapshotAtBetweenAndReconciliationStatus(any(), any(), eq(ReconciliationStatus.PENDING)))
                .thenReturn(Collections.emptyList());

        useCase.execute(date);

        // then
        // 1. Job이 1번 저장되었는지
        verify(jobRepository, times(1)).save(any(ReconciliationJob.class));

        // 2. Mismatch 로그는 저장되면 안 됨 (성공이니까)
        verify(mismatchLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("금액 불일치: PG(1000원) vs 내부(500원)")
    void fail_amount_mismatch() {
        // given
        LocalDate date = LocalDate.now();

        PgSalesRaw pgData = PgSalesRaw.builder()
                .merchantUid("ORD-DIFF").pgStatus(PgStatus.PAID).paymentAmount(1000L).build();

        SalesLog internalData = SalesLog.builder()
                .orderNo("ORD-DIFF").transactionType(TransactionType.PAYMENT).paymentAmount(500L).build();

        when(pgSalesRawRepository.findAllByTransactedAtBetween(any(), any())).thenReturn(List.of(pgData));
        when(salesLogRepository.findByOrderNoAndTransactionType("ORD-DIFF", TransactionType.PAYMENT))
                .thenReturn(List.of(internalData));

        // when
        useCase.execute(date);

        // then
        ArgumentCaptor<ReconciliationMismatchLog> captor = ArgumentCaptor.forClass(ReconciliationMismatchLog.class);
        verify(mismatchLogRepository, times(1)).save(captor.capture());
        ReconciliationMismatchLog savedLog = captor.getValue();

        assertThat(savedLog.getType()).isEqualTo(MismatchType.AMOUNT_DIFF); // 타입 확인
        assertThat(savedLog.getPgAmount()).isEqualTo(1000L); // PG 금액 확인
        assertThat(savedLog.getInternalAmount()).isEqualTo(500L); // 내부 금액 확인
        assertThat(savedLog.getReason()).contains("금액 불일치"); // 메시지 확인
    }

    @Test
    @DisplayName("주문서 누락: PG에는 있는데 내부 DB에 없을 때")
    void fail_pg_only() {
        // given
        LocalDate date = LocalDate.now();
        PgSalesRaw pgData = PgSalesRaw.builder()
                .merchantUid("ORD-GHOST")
                .pgStatus(PgStatus.PAID)
                .paymentAmount(5000L)
                .build();

        when(pgSalesRawRepository.findAllByTransactedAtBetween(any(), any())).thenReturn(List.of(pgData));
        // 빈 리스트 반환 (누락 시)
        when(salesLogRepository.findByOrderNoAndTransactionType("ORD-GHOST", TransactionType.PAYMENT))
                .thenReturn(Collections.emptyList());

        // when
        useCase.execute(date);

        // then
        ArgumentCaptor<ReconciliationMismatchLog> captor = ArgumentCaptor.forClass(ReconciliationMismatchLog.class);
        verify(mismatchLogRepository, times(1)).save(captor.capture());

        ReconciliationMismatchLog savedLog = captor.getValue();

        assertThat(savedLog.getType()).isEqualTo(MismatchType.PG_ONLY);
        assertThat(savedLog.getReason()).isEqualTo("주문서 누락");
    }

    @Test
    @DisplayName("역방향 체크: 내부 DB에만 PENDING 데이터가 남아있을 때")
    void fail_internal_only() {
        // given
        LocalDate date = LocalDate.now();

        // PG 데이터는 없음
        when(pgSalesRawRepository.findAllByTransactedAtBetween(any(), any())).thenReturn(Collections.emptyList());

        // 내부 PENDING 데이터 1건 존재 (유령 데이터)
        SalesLog ghostLog = SalesLog.builder()
                .orderNo("ORD-InternalOnly").paymentAmount(1000L).build();

        when(salesLogRepository.findAllBySnapshotAtBetweenAndReconciliationStatus(any(), any(), eq(ReconciliationStatus.PENDING)))
                .thenReturn(List.of(ghostLog));

        // when
        useCase.execute(date);

        // then
        ArgumentCaptor<ReconciliationMismatchLog> captor = ArgumentCaptor.forClass(ReconciliationMismatchLog.class);
        verify(mismatchLogRepository, times(1)).save(captor.capture());

        ReconciliationMismatchLog savedLog = captor.getValue();

        assertThat(savedLog.getType()).isEqualTo(MismatchType.INTERNAL_ONLY);
        assertThat(savedLog.getOrderNo()).isEqualTo("ORD-InternalOnly");
    }
}