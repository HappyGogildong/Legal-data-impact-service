package com.lia.core.eval;

import java.util.List;

import com.lia.core.pipeline.plan.QueryType;

/**
 * 질문 1건을 서비스 경로로 돌린 결과 요약(D83).
 *
 * @param refused         미등록·비법령으로 거부했는가(해소 후보가 여럿인 것은 거부가 아니다)
 * @param candidateLawIds 화면에 나갈 법령 후보(순서 유지) — 해소되면 그 법령 하나
 * @param resolved        법령 하나로 해소됐는가
 * @param articleNo       해소된 참조의 조문 번호(`no` 형식) — 없으면 null
 * @param primaryType     주 차원 — 거부·미해소면 null, 발견이면 LOOKUP
 */
public record CaseOutcome(boolean refused, List<String> candidateLawIds, boolean resolved, String articleNo,
                          QueryType primaryType) {
    public CaseOutcome {
        candidateLawIds = candidateLawIds == null ? List.of() : List.copyOf(candidateLawIds);
    }
}
