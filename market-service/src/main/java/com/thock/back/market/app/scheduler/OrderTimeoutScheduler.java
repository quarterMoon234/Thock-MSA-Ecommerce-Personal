package com.thock.back.market.app.scheduler;

import com.thock.back.market.domain.Order;
import com.thock.back.market.domain.OrderState;
import com.thock.back.market.out.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderTimeoutScheduler {

    private final OrderRepository orderRepository;
    private final OrderTimeoutCancelWorker orderTimeoutCancelWorker;

    // TODO : Policy로 변경
    private static final int PAYMENT_TIMEOUT_MINUTES = 30;

    /**
     * 미결제 주문 타임아웃 처리
     * 결제 요청 후 30분 경과한 주문을 자동으로 취소
     *
     * 중요:
     * - 메서드 전체를 하나의 트랜잭션으로 묶지 않는다.
     *   - 30분 경과한 주문들이 많다고 가정
     *   - 그 중에서 1개라도 어떤 사용자가 명시적 취소하다가 동시에 취소 처리가 된다면 ? 전부 롤백 되면 안되잖음
     * - 주문 1건은 worker에서 REQUIRES_NEW 트랜잭션으로 처리한다.
     * - 따라서 한 건 충돌/실패가 나도 나머지 주문 처리는 계속된다.
     */
    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    public void cancelExpiredOrders() {
        LocalDateTime expiredTime = LocalDateTime.now().minusMinutes(PAYMENT_TIMEOUT_MINUTES);

        List<Order> expiredOrders = orderRepository.findByStateAndRequestPaymentDateBefore(
                OrderState.PENDING_PAYMENT, expiredTime);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("⏰ 타임아웃 주문 처리 시작: {}건", expiredOrders.size());

        for (Order order : expiredOrders) {
            try {
                // 주문 1건은 별도 트랜잭션에서 처리
                orderTimeoutCancelWorker.cancelOne(order.getId());
            } catch (Exception e) {
                // worker 밖으로 올라온 예외는 해당 건만 로그 후 다음 주문 처리
                log.error("⏰ 타임아웃 주문 취소 실패: orderId={}, error={}", order.getId(), e.getMessage(), e);
            }
        }

        log.info("⏰ 타임아웃 주문 처리 완료: {}건", expiredOrders.size());
    }
}
