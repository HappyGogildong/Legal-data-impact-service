package com.lia.core.pipeline.connector;

import java.util.Map;

/**
 * 커넥터가 내보내는 출처 비종속 원자료(정규화 전).
 * Python 참조 구현: pipeline/src/lia_pipeline/models.py::RawBill
 */
public record RawBill(
        String sourceType,   // "assembly" | "moleg" | ...
        String sourceId,     // 출처 내 식별자 (BILL_ID 등)
        String billNo,       // 의안번호 (없으면 null)
        String title,
        Map<String, Object> raw  // 출처 원본 페이로드
) {}
