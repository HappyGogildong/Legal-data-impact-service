package com.lia.core.pipeline.connector;

import java.time.LocalDate;
import java.util.Map;

/**
 * 국가법령정보 커넥터가 내보내는 원자료(정규화 전).
 *
 * <p>MVP 분석 대상은 의안이 아니라 <b>공포 후 시행 대기 법령</b>이다(D42).
 * 시행중본(target=law)과 시행예정본(target=eflaw)은 응답 스키마가 같으므로
 * 같은 레코드로 담고 {@code status} 로만 구분한다.
 *
 * <p>설계: docs/components/SourceConnector.md §MVP 본문 경로
 */
public record RawLaw(
        String lawId,             // 법령ID — 버전 불변. 시행중↔시행예정 연결키
        String mst,               // 법령일련번호 — 버전마다 다름. 연결키로 쓰지 말 것
        String title,             // 법령명(한글)
        String status,            // "시행중" | "시행예정" (현행연혁코드)
        LocalDate effectiveDate,  // 시행일자
        LocalDate promulgateDate, // 공포일자
        String promulgateNo,      // 공포번호 — 이번 개정분 부칙 필터 키
        Map<String, Object> raw   // 법령 > {기본정보, 조문, 부칙, 개정문, 제개정이유}
) {
    /** 목록 조회 결과처럼 본문이 없는 경우 true. */
    public boolean hasBody() {
        return raw != null && raw.containsKey("조문");
    }
}
