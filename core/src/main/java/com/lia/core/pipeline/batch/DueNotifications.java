package com.lia.core.pipeline.batch;

import java.time.LocalDate;

/**
 * 구독 알림 생성(D74) — 배치 notify 단계가 부른다. 구현은 {@code NotificationStore}.
 * 규칙: 구독 법령의 시행예정 버전 중 {@code 시행일 − 알림 시점 ≤ today ≤ 시행일}, 이미 만든 알림은 건너뛴다(정확히 한 번).
 */
public interface DueNotifications {

    /** @return 새로 만든 알림 수 */
    int createDue(LocalDate today);

    /** @return 지금 만들면 새로 생길 알림 수(--dry-run) */
    int countDue(LocalDate today);
}
