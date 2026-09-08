package com.lia.core.pipeline.analyze;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.plan.QueryType;

/** SpringAiReasoner.stampMeta 단위 — meta는 LLM이 아니라 우리가 확정(모델명·버전·계층). ChatModel 불필요. */
class SpringAiReasonerTest {

    private static ImpactResult withMeta(String model) {
        return new ImpactResult("LAW:x@y", "DIFF", "요약",
                List.of(), List.of(), List.of(), null, List.of(), "면책",
                new ImpactResult.Meta(model, "?", "?"));
    }

    @Test
    void stampMeta는_실제_모델명과_버전으로_덮어쓴다() {
        ImpactResult out = SpringAiReasoner.stampMeta(withMeta("gpt"), QueryType.DIFF);
        assertEquals("claude-opus-4-8", out.meta().model(), "LLM의 잘못된 모델명 교정");
        assertEquals("0.2", out.meta().promptVersion());
    }

    @Test
    void 차원에서_계층을_도출한다() {
        assertEquals("A", SpringAiReasoner.stampMeta(withMeta("x"), QueryType.SUMMARY).meta().layer());
        assertEquals("A", SpringAiReasoner.stampMeta(withMeta("x"), QueryType.DIFF).meta().layer());
        assertEquals("B", SpringAiReasoner.stampMeta(withMeta("x"), QueryType.IMPACT).meta().layer());
        assertEquals("B", SpringAiReasoner.stampMeta(withMeta("x"), QueryType.ACTION).meta().layer());
    }

    @Test
    void content은_보존된다() {
        ImpactResult out = SpringAiReasoner.stampMeta(withMeta("gpt"), QueryType.SUMMARY);
        assertEquals("요약", out.summary());
        assertEquals("면책", out.disclaimer());
    }
}
