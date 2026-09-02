package com.lia.core.pipeline.dispatch;

import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.plan.QueryType;

/**
 * 한 분석 차원(QueryType)을 실행하는 핸들러(D47) — 사용자 선택 모드가 아니라
 * 플래너가 고른 내부 분해 + 그라운딩 가드레일(D46). 실행 본체는 {@code AnalysisEngine}에 위임한다.
 *
 * <p>{@code needsProfile}/{@code needsRag}는 {@code QueryDispatcher}의 게이트가 읽는 메타데이터다
 * — Layer A(SUMMARY·DIFF)는 둘 다 false, Layer B(IMPACT·ACTION)가 프로필을 요구한다.
 */
public interface DimensionHandler {

    /** 이 핸들러가 담당하는 차원. 레지스트리 키. */
    QueryType type();

    /** 프로필이 있어야 실행 가능한가(Layer B). 기본 false. */
    default boolean needsProfile() { return false; }

    /** RAG 검색이 필요한가. 기본 false(정본 정확 조회로 충분한 Layer A). */
    default boolean needsRag() { return false; }

    /** 해소된 정본 컨텍스트로 이 차원을 실행. */
    AnalyzeResponse handle(DispatchContext ctx);
}
