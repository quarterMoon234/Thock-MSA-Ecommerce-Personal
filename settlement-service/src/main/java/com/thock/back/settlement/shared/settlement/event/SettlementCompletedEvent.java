package com.thock.back.settlement.shared.settlement.event;

import java.util.List;

public record SettlementCompletedEvent (
        Long dailySettlementId,
        List<Long> snapshotIds
){
}
