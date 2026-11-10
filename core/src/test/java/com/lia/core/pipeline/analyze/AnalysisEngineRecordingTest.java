package com.lia.core.pipeline.analyze;

import static com.lia.core.pipeline.analyze.AnalysisEngineTest.claim;
import static com.lia.core.pipeline.analyze.AnalysisEngineTest.housing;
import static com.lia.core.pipeline.analyze.AnalysisEngineTest.result;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.analysis.ConfirmedFact;
import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Law;
import com.lia.core.observability.llm.LlmCall;
import com.lia.core.observability.llm.LlmPurpose;
import com.lia.core.observability.llm.LlmUsage;
import com.lia.core.observability.llm.UsageCapture;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.profile.UserProfile;

/** AnalysisEngine 의 호출 기록(D83) — 시도마다 한 행, 재시도 묶음·인용 검증 결과·목적. 실 Opus 없음. */
class AnalysisEngineRecordingTest {

    private static final UserProfile PROFILE = new UserProfile(null, 34, null, null, null, null, null);
    private static final LlmUsage USAGE = new LlmUsage("claude-opus-5-5-20261001", 1200, 300, 0, 0, "end_turn");

    private final List<LlmCall> calls = new ArrayList<>();

    /** 스크립트된 결과를 차례로 내며 사용량을 담는 가짜 추론기. */
    private static Reasoner recordingReasoner(ImpactResult... results) {
        Deque<ImpactResult> queue = new ArrayDeque<>(List.of(results));
        return new Reasoner() {
            public ImpactResult reason(AnalysisContext ctx) { throw new AssertionError("사용량 기록판을 불러야 한다"); }
            public ImpactResult reason(AnalysisContext ctx, UsageCapture usage) {
                usage.capture(USAGE);
                return queue.isEmpty() ? results[results.length - 1] : queue.poll();
            }
            public PersonalizedOutput reasonPersonalized(AnalysisContext ctx, UsageCapture usage) {
                usage.capture(USAGE);
                return new PersonalizedOutput(queue.isEmpty() ? results[results.length - 1] : queue.poll(),
                        List.of(), List.of());
            }
            public String model() { return "claude-opus-5-5"; }
            public String promptVersion() { return "0.3"; }
        };
    }

    private AnalysisEngine engine(Reasoner reasoner) {
        return new AnalysisEngine(new ContextBuilder(), reasoner, 3, calls::add);
    }

    private static String cite(Law law) {
        return law.sourceId(law.article("18"));
    }

    @Test
    void 한_번에_통과하면_한_행을_남긴다() {
        Law law = housing();

        engine(recordingReasoner(result(claim("주장", cite(law))))).analyze(new AnalyzeRequest(QueryType.SUMMARY, law, null));

        assertEquals(1, calls.size());
        LlmCall call = calls.get(0);
        assertEquals(LlmPurpose.SUMMARY, call.purpose());
        assertEquals("claude-opus-5-5-20261001", call.model(), "응답의 모델 ID");
        assertEquals("0.3", call.promptVersion());
        assertNotNull(call.groupKey());
        assertEquals(1, call.attempt());
        assertEquals(Boolean.TRUE, call.accepted());
        assertTrue(call.succeeded());
        assertEquals(USAGE, call.usage());
        assertNull(call.requested(), "Layer A 는 요청 차원을 남기지 않는다");
        assertEquals(new ContextBuilder().build(new AnalyzeRequest(QueryType.SUMMARY, law, null)).blocks().size(),
                call.contextSources());
    }

    @Test
    void 재시도는_같은_묶음에_시도_번호와_통과_여부로_남는다() {
        Law law = housing();
        String hallucinated = "LAW:999@2099-01-01:art:1";

        engine(recordingReasoner(result(claim("환각", hallucinated)), result(claim("정상", cite(law)))))
                .analyze(new AnalyzeRequest(QueryType.DIFF, law, null));

        assertEquals(2, calls.size());
        assertEquals(calls.get(0).groupKey(), calls.get(1).groupKey());
        assertEquals(List.of(1, 2), calls.stream().map(LlmCall::attempt).toList());
        assertEquals(List.of(false, true), calls.stream().map(LlmCall::accepted).toList());
        assertTrue(calls.stream().allMatch(c -> c.purpose() == LlmPurpose.DIFF));
    }

    @Test
    void 끝까지_실패하면_시도마다_통과하지_못한_행() {
        AnalysisEngine engine = engine(recordingReasoner(result(claim("환각", "LAW:999@2099-01-01:art:1"))));

        assertThrows(InsufficientGroundingException.class,
                () -> engine.analyze(new AnalyzeRequest(QueryType.SUMMARY, housing(), null)));

        assertEquals(3, calls.size());
        assertTrue(calls.stream().noneMatch(LlmCall::accepted));
    }

    @Test
    void 분석_호출마다_묶음_키가_다르다() {
        Law law = housing();
        AnalysisEngine engine = engine(recordingReasoner(result(claim("주장", cite(law)))));

        engine.analyze(new AnalyzeRequest(QueryType.SUMMARY, law, null));
        engine.analyze(new AnalyzeRequest(QueryType.SUMMARY, law, null));

        assertNotEquals(calls.get(0).groupKey(), calls.get(1).groupKey());
    }

    @Test
    void 추론기가_던지면_실패_행을_남기고_같은_예외를_다시_던진다() {
        RuntimeException boom = new IllegalStateException("API 시간 초과");
        Reasoner failing = new Reasoner() {
            public ImpactResult reason(AnalysisContext ctx) { throw new AssertionError(); }
            public ImpactResult reason(AnalysisContext ctx, UsageCapture usage) { throw boom; }
            public String model() { return "claude-opus-5-5"; }
        };

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> engine(failing).analyze(new AnalyzeRequest(QueryType.SUMMARY, housing(), null)));

        assertSame(boom, thrown);
        assertEquals(1, calls.size());
        LlmCall call = calls.get(0);
        assertFalse(call.succeeded());
        assertNull(call.accepted(), "결과가 없으니 판정도 없다");
        assertEquals("claude-opus-5-5", call.model(), "응답이 없으면 요청한 모델");
        assertEquals(LlmUsage.NONE, call.usage());
    }

    @Test
    void 첫_Layer_B는_PERSONALIZED_와_나갈_차원을_남긴다() {
        engine(recordingReasoner(housingPersonalized()))
                .analyze(new AnalyzeRequest(QueryType.IMPACT, housing(), null, PROFILE, "질문"), Set.of(QueryType.ACTION));

        LlmCall call = calls.get(0);
        assertEquals(LlmPurpose.PERSONALIZED, call.purpose());
        assertEquals("ACTION", call.requested());
    }

    @Test
    void 영향과_대응을_함께_요청하면_정해진_순서로_남긴다() {
        engine(recordingReasoner(housingPersonalized()))
                .analyze(new AnalyzeRequest(QueryType.IMPACT, housing(), null, PROFILE, "질문"));

        assertEquals("IMPACT,ACTION", calls.get(0).requested());
    }

    @Test
    void 대응_재생성은_ACTION_REVISION_이고_요청_차원을_남기지_않는다() {
        engine(recordingReasoner(housingPersonalized())).analyze(new AnalyzeRequest(QueryType.ACTION, housing(), null,
                PROFILE, null, List.of(new ConfirmedFact("q", "예"))));

        LlmCall call = calls.get(0);
        assertEquals(LlmPurpose.ACTION_REVISION, call.purpose());
        assertNull(call.requested());
        assertEquals(Boolean.TRUE, call.accepted());
    }

    private static ImpactResult housingPersonalized() {
        String art = "LAW:001809@2026-08-04:art:18";
        return new ImpactResult("LAW:001809@2026-08-04", "IMPACT", "요약",
                List.of(new ImpactResult.Claim("주장", List.of(art), 0.9)),
                List.of(new ImpactResult.Impact("주거", "영향 있음", "상세", List.of(art))),
                List.of(new ImpactResult.Action("확인", null, List.of(art))),
                null, List.of(), "참고용", null);
    }
}
