package com.lia.core.pipeline.resolve;

/** 해소 4상태 (D23). fail-closed — 신뢰 출처에서 확인 안 되면 분석하지 않는다. */
public enum ResolutionState {
    RESOLVED,        // 출처에서 1건 확인 → 분석 진행
    AMBIGUOUS,       // 후보 여럿 → 사용자 확인
    NOT_FOUND_YET,   // 미등록(아직 발의 전/지연) → 거부+안내
    UNVERIFIED       // 허위 의심 → 거부+(유사 법안 대조)
}
