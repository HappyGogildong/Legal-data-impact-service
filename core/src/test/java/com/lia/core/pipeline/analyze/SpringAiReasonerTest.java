package com.lia.core.pipeline.analyze;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.plan.QueryType;

/** SpringAiReasoner 단위 — meta 확정(모델명·버전·계층)과 응답에서 thinking 블록 거르기. 실 API 없음(고정 응답 ChatModel). */
class SpringAiReasonerTest {

    private static final String JSON = """
            {"lawRef":"LAW:A@2027-01-01","command":"SUMMARY","summary":"절차가 짧아져요",
             "claims":[{"statement":"통합심의","citations":["LAW:A@2027-01-01:art:18"],"confidence":0.9}],
             "impacts":[],"actions":[],"uncertainties":[],"disclaimer":"참고용"}
            """;

    /** 정해 둔 generation 목록을 그대로 돌려주는 ChatModel — Anthropic 응답의 블록 순서를 흉내 낸다. */
    private static ChatModel replying(Generation... generations) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(generations));
            }
        };
    }

    /** Spring AI 가 thinking 블록을 옮기는 모양 — 내용은 thinking 원문(Opus 5.5 기본은 빈 문자열), 메타데이터에 signature. */
    private static Generation thinking(String text) {
        return new Generation(AssistantMessage.builder().content(text).properties(Map.of("signature", "sig")).build());
    }

    private static Generation text(String text) {
        return new Generation(AssistantMessage.builder().content(text).build());
    }

    private static AnalysisContext context() {
        return new AnalysisContext(QueryType.SUMMARY, "LAW:A@2027-01-01", List.of());
    }

    @Test
    void thinking_블록이_앞에_와도_본문_JSON을_파싱한다() {
        // 실 Opus 5.5 응답: [thinking(빈 내용), text(JSON)] — 첫 generation 만 보면 "No content to map" 으로 실패했다
        ImpactResult out = new SpringAiReasoner(replying(thinking(""), text(JSON))).reason(context());

        assertEquals("절차가 짧아져요", out.summary());
        assertEquals(List.of("LAW:A@2027-01-01:art:18"), out.claims().get(0).citations());
    }

    @Test
    void 요약된_thinking_내용이_있어도_본문만_쓴다() {
        ImpactResult out = new SpringAiReasoner(replying(thinking("먼저 제18조를 본다…"), text(JSON))).reason(context());

        assertEquals("절차가 짧아져요", out.summary());
    }

    private static ImpactResult withMeta(String model) {
        return new ImpactResult("LAW:x@y", "DIFF", "요약",
                List.of(), List.of(), List.of(), null, List.of(), "면책",
                new ImpactResult.Meta(model, "?", "?"));
    }

    @Test
    void stampMeta는_실제_모델명과_버전으로_덮어쓴다() {
        ImpactResult out = SpringAiReasoner.stampMeta(withMeta("gpt"), QueryType.DIFF);
        assertEquals("claude-opus-5-5", out.meta().model(), "LLM의 잘못된 모델명 교정");
        assertEquals("0.3", out.meta().promptVersion());
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
