package com.lia.core.eval;

import java.util.ArrayList;
import java.util.List;

import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.eval.GoldenSet.RetrievalCase;

/**
 * <b>자기검색 골든셋</b> 생성기 — 적재된 코퍼스로 retrieval 평가 기준선을 자동 확보(D53, OpenAI 임시확정).
 *
 * <p>각 법령: query=제개정이유(요약), expected = 그 법령의 <b>색인된 source_ids</b>(요약 {@code ref} + 변경조문).
 * 요약과 조문은 서로 다른 텍스트로 따로 임베딩되므로, 요약 질의로 그 법령의 조문까지 끌어오는지는 <b>비자명</b>하다
 * — 의미검색이 실제로 도는지(그리고 <b>다른 법령보다 자기 법령을 위로</b> 올리는지) 증명한다.
 *
 * <p>[[RAGIndexer]]의 색인 규칙과 일치해야 한다: 요약 {@code LAW:{lawId}@{efYd}}, 조문 {@code …:art:{no}}.
 */
public final class SelfRetrievalGold {

    private SelfRetrievalGold() {}

    public static List<RetrievalCase> fromLaws(List<Law> laws) {
        List<RetrievalCase> cases = new ArrayList<>();
        for (Law law : laws) {
            String summary = law.amendReason();
            if (summary == null || summary.isBlank()) continue;   // 요약 없으면 자기검색 불가
            List<String> expected = new ArrayList<>();
            expected.add(law.ref());                              // 요약 청크
            for (Article a : law.changedArticles()) expected.add(law.sourceId(a));
            cases.add(new RetrievalCase(summary, expected));
        }
        return cases;
    }
}
