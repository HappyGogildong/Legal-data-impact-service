package com.lia.core.pipeline.connector;

import java.util.List;
import java.util.Objects;

/**
 * 모든 출처 어댑터의 계약. 인증·페이징·필드명은 구현체 안에 가두고 RawBill만 내보낸다.
 * 새 출처 = 구현체 1개 추가(하류 무수정). 설계: docs/components/SourceConnector.md
 */
public interface SourceConnector {

    /** 출처 식별자 (RawBill.sourceType 에 기록) */
    String sourceType();

    /** 키워드/조건으로 법안 목록 조회 */
    List<RawBill> search(String query, int limit);

    /** 단건 상세(원문 포함) 조회 */
    RawBill fetch(String sourceId);

    /**
     * 의안번호 정확 조회. 기본 구현은 search 결과에서 정확 일치를 찾는다.
     * 출처가 전용 파라미터를 지원하면 오버라이드.
     */
    default RawBill getByBillNo(String billNo) {
        for (RawBill raw : search(String.valueOf(billNo), 20)) {
            if (raw.billNo() != null && Objects.equals(String.valueOf(raw.billNo()), String.valueOf(billNo))) {
                return raw;
            }
        }
        return null;
    }
}
