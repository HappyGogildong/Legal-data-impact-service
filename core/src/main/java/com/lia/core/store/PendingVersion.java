package com.lia.core.store;

import java.time.LocalDate;

/**
 * 탐색용 시행예정 버전 요약 — payload 를 역직렬화하지 않고 배치가 비교에 쓰는 값만(D62).
 * {@code mst} 는 목록의 MST 와 비교해 본문 fetch 생략 여부를 정한다. {@code missingSince} 는 사라짐 판정.
 */
public record PendingVersion(String lawId, LocalDate effectiveDate, String mst, LocalDate missingSince) {
}
