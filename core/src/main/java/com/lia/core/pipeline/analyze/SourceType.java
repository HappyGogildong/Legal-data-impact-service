package com.lia.core.pipeline.analyze;

/**
 * 근거 블록의 종류 — 문자열 대신 타입으로(오타 컴파일 차단·프롬프트/필터 안전).
 *
 * <p>{@code BASELINE}은 엄밀히는 "비교 기준으로 쓰인 조문"이라는 <i>역할</i>이지만, MVP에서는 종류로 둔다
 * (type/role 분리는 소비 코드가 복잡해질 때 후속).
 */
public enum SourceType {
    ARTICLE,    // 변경 조문
    AMEND,      // 개정문
    ADDENDA,    // 부칙
    BASELINE    // 시행중(기준선) 대응 조문
}
