package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.eval.GoldenSet.RetrievalCase;
import com.lia.core.eval.Retriever.Retrieved;

/**
 * 러너가 골든셋을 돌려 지표를 평균 내는지 — FakeRetriever(합성)로 검증.
 */
class RagEvalRunnerTest {

    private final List<RetrievalCase> gold = GoldenSet.retrieval("eval/retrieval-gold.json");

    /** 정답을 rank 1에 두는 완벽 리트리버. */
    private Retriever perfect() {
        return (q, cfg) -> find(q).stream().map(id -> new Retrieved(id, 1.0)).toList();
    }

    /** 정답을 rank 3에 두는(앞에 노이즈 2개) 열화 리트리버. */
    private Retriever rankThree() {
        return (q, cfg) -> {
            List<Retrieved> out = new ArrayList<>();
            out.add(new Retrieved("noise-1", 0.9));
            out.add(new Retrieved("noise-2", 0.8));
            find(q).forEach(id -> out.add(new Retrieved(id, 0.7)));
            return out;
        };
    }

    private List<String> find(String query) {
        return gold.stream().filter(c -> c.query().equals(query)).findFirst()
                .map(RetrievalCase::expectedSourceIds).orElse(List.of());
    }

    @Test
    @DisplayName("완벽 리트리버 → recall@5·hit@1·mrr = 1.0")
    void 완벽() {
        var r = new RagEvalRunner(perfect()).run(RagConfig.defaults(), gold);
        assertEquals(1.0, r.metric("recall@5"), 1e-9);
        assertEquals(1.0, r.metric("hit@1"), 1e-9);
        assertEquals(1.0, r.metric("mrr"), 1e-9);
    }

    @Test
    @DisplayName("정답이 rank 3이면 hit@1=0·hit@3=1·mrr=1/3, recall@5는 여전히 1")
    void 열화() {
        var r = new RagEvalRunner(rankThree()).run(RagConfig.defaults(), gold);
        assertEquals(0.0, r.metric("hit@1"), 1e-9);
        assertEquals(1.0, r.metric("hit@3"), 1e-9);
        assertEquals(1.0 / 3, r.metric("mrr"), 1e-9);
        assertEquals(1.0, r.metric("recall@5"), 1e-9);
    }
}
