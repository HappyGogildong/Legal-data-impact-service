package com.lia.core.pipeline.analyze;

/**
 * 재생성(≤N)에도 인용 검증을 통과하지 못함 — <b>환각을 노출하지 않고</b> "근거 부족"으로 실패시킨다(§6.4).
 * 상위(오케스트레이터)가 422 "근거 부족" 폴백으로 처리한다(component-specs §3.1). fail-closed.
 */
public class InsufficientGroundingException extends RuntimeException {

    public InsufficientGroundingException(String message) {
        super(message);
    }
}
