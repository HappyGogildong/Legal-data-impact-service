package com.lia.core.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lia.core.store.LawSubscriptionStore.SubscribedLaw;

/** {@code /api/v1/subscriptions} 요청·응답(D74). */
final class SubscriptionApiDtos {

    private SubscriptionApiDtos() {}

    /** @param notifyBefore 0·1·7·30(일) */
    public record SubscriptionRequest(Integer notifyBefore) {}

    /** @param nextEffectiveDate 가장 이른 시행예정본 — 없으면 생략(다음 개정을 기다리는 중) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubscriptionItem(String lawId, String title, int notifyBefore, OffsetDateTime createdAt,
                                   LocalDate nextEffectiveDate) {

        static SubscriptionItem from(SubscribedLaw law) {
            return new SubscriptionItem(law.lawId(), law.title(), law.notifyBefore(), law.createdAt(),
                    law.nextEffectiveDate());
        }
    }

    public record SubscriptionListResponse(List<SubscriptionItem> items) {}
}
