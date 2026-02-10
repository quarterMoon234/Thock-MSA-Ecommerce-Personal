package com.thock.back.settlement.reconciliation.app.port;

public record SettlementCandidate(
        Long salesLogId,
        Long sellerId,
        long paymentAmount,
        String orderNo
) {

}