package com.lia.core.pipeline.analyze;

import com.lia.core.domain.analysis.ImpactResult;

/**
 * 조립된 context → {@link ImpactResult} 추론 <b>포트</b>(Opus). 파이프라인의 LLM 경계.
 *
 * <p>포트인 이유는 "교체점"이 아니라 <b>재생성 루프 테스트 심</b>이다: AnalysisEngine의
 * 조립→추론→검증→재생성 로직을 실 Opus 없이(비용·비결정 배제) 검증하려면 추론 결과를 스크립트해야 한다.
 * 구현 {@code SpringAiReasoner}(Opus 4.8). 스펙: docs/components/AnalysisEngine.md.
 */
@FunctionalInterface
public interface Reasoner {

    ImpactResult reason(AnalysisContext context);
}
