package com.lia.core.eval;

import java.util.List;

import com.lia.core.pipeline.plan.QueryType;

/**
 * 정답 세트 1문항(D83). 공개 레포에 커밋되므로 실제 사용자 질문을 넣지 않는다.
 *
 * @param query             질문
 * @param kind              답이 있음 · 관련 법령 없음 · 거부해야 함
 * @param lawNamed          질문이 법령 이름을 댔는가 — 지표를 나누는 기준(플래너가 고른 경로가 아니다)
 * @param expectedLawIds    정답 법령ID — {@code ANSWERABLE} 이면 1개 이상, 아니면 없음
 * @param acceptableLawIds  정답은 아니지만 후보로 나와도 잘못이 아닌 법령ID(무관 후보율의 분모를 정직하게)
 * @param articleNo         질문이 지목한 조문({@code no} 형식, "18"·"105의2") — 없으면 null
 * @param expectedDimension 기대하는 주 차원 — 의도 분류를 재지 않는 문항은 null
 */
public record EvalCase(String query, Kind kind, boolean lawNamed, List<String> expectedLawIds,
                       List<String> acceptableLawIds, String articleNo, QueryType expectedDimension) {

    public enum Kind { ANSWERABLE, NO_MATCH, REFUSE }

    public EvalCase {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("질문은 비어 있을 수 없다.");
        if (kind == null) throw new IllegalArgumentException("문항 종류(kind)는 필수다: " + query);
        expectedLawIds = expectedLawIds == null ? List.of() : List.copyOf(expectedLawIds);
        acceptableLawIds = acceptableLawIds == null ? List.of() : List.copyOf(acceptableLawIds);
        if (kind == Kind.ANSWERABLE && expectedLawIds.isEmpty()) {
            throw new IllegalArgumentException("답이 있는 문항은 정답 법령이 필요하다: " + query);
        }
        if (kind != Kind.ANSWERABLE && !expectedLawIds.isEmpty()) {
            throw new IllegalArgumentException(kind + " 문항은 정답 법령을 가질 수 없다: " + query);
        }
    }
}
