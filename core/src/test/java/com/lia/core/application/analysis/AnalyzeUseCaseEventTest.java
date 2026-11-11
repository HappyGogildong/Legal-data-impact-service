package com.lia.core.application.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lia.core.application.profile.PersonalizationGate;
import com.lia.core.pipeline.analyze.AnalysisEngine;
import com.lia.core.pipeline.analyze.ContextBuilder;
import com.lia.core.pipeline.dispatch.DimensionHandlerRegistry;
import com.lia.core.pipeline.dispatch.PersonalizedHandler;
import com.lia.core.pipeline.dispatch.QueryDispatcher;
import com.lia.core.pipeline.dispatch.SummaryHandler;
import com.lia.core.pipeline.plan.AnalysisQueryDraft;
import com.lia.core.pipeline.plan.AnalysisQueryDraft.TargetKind;
import com.lia.core.pipeline.plan.QueryPlanner;
import com.lia.core.pipeline.plan.QueryTranslator;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.resolve.SourceAnalyzer;
import com.lia.core.profile.StoredProfile;
import com.lia.core.profile.UserProfileStore;

/** AnalyzeUseCase 의 질의 이벤트(D83) — 요청 1건 = 행 1개, 익명 상한·예외도 한 행, 기록이 응답을 막지 않는다. */
class AnalyzeUseCaseEventTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AnalysisQueryDraft SUMMARY = new AnalysisQueryDraft(QueryType.SUMMARY, Set.of(QueryType.SUMMARY),
            true, TargetKind.REFERENCE, "주택법", null, List.of(), List.of(), List.of(), "요약");
    private static final AnalysisQueryDraft OFF_TOPIC = new AnalysisQueryDraft(QueryType.SUMMARY,
            Set.of(QueryType.SUMMARY), false, TargetKind.REFERENCE, null, null, List.of(), List.of(), List.of(), "점심");

    private final List<QueryEvent> events = new ArrayList<>();

    private static PersonalizationGate noProfileGate() {
        UserProfileStore store = new UserProfileStore(null) {
            @Override public Optional<StoredProfile> find(UUID userId) { return Optional.empty(); }
        };
        return new PersonalizationGate(store, "v1");
    }

    private AnalyzeUseCase useCase(QueryTranslator translator, AnonymousAnalysisQuota quota, QueryEventRecorder recorder) {
        QueryPlanner planner = new QueryPlanner(translator, new SourceAnalyzer(new AnalyzeUseCaseTest.FakeLookup()));
        AnalyzeUseCaseTest.FakeLawSource source = new AnalyzeUseCaseTest.FakeLawSource();
        source.versions.put("001809@2026-08-04", AnalyzeUseCaseTest.law());
        AnalysisEngine engine = new AnalysisEngine(new ContextBuilder(), AnalyzeUseCaseTest.groundedReasoner(), 3);
        DimensionHandlerRegistry registry = new DimensionHandlerRegistry(List.of(
                new SummaryHandler(engine, law -> Optional.empty(), new ContextBuilder(), true)));
        QueryDispatcher dispatcher = new QueryDispatcher(source, registry, new PersonalizedHandler(null), userId -> true,
                AnalyzeUseCaseTest.NO_DISCOVERY);
        return new AnalyzeUseCase(planner, dispatcher, (lawId, efYd) -> true, noProfileGate(), quota, recorder, "0.3");
    }

    @Test
    void 답한_요청은_한_행() {
        useCase(q -> SUMMARY, () -> true, events::add).analyze("주택법 요약", null, Optional.of(USER));

        assertEquals(1, events.size());
        QueryEvent event = events.get(0);
        assertEquals(QueryEvent.Status.ANSWERED, event.status());
        assertTrue(event.signedIn());
        assertEquals(Set.of(QueryType.SUMMARY), event.filled());
        assertEquals("0.3", event.promptVersion());
    }

    @Test
    void 법령_질문이_아닌_거부는_OFF_TOPIC_으로_남는다() {
        useCase(q -> OFF_TOPIC, () -> true, events::add).analyze("오늘 점심 뭐 먹지", null, Optional.empty());

        assertEquals(QueryEvent.Status.UNRESOLVED, events.get(0).status());
        assertEquals(QueryEvent.OFF_TOPIC, events.get(0).resolutionState());
    }

    @Test
    void 익명_상한에_걸리면_한_행을_남기고_같은_예외를_던진다() {
        AnalyzeUseCase useCase = useCase(q -> { throw new AssertionError("분류 전에 막혀야 한다"); }, () -> false, events::add);

        assertThrows(AnonymousLimitException.class, () -> useCase.analyze("질문", null, Optional.empty()));

        assertEquals(1, events.size());
        assertEquals(QueryEvent.Status.ANONYMOUS_LIMIT, events.get(0).status());
    }

    @Test
    void 예외로_끝나면_ERROR_한_행을_남기고_같은_예외를_던진다() {
        RuntimeException boom = new IllegalStateException("Haiku 시간 초과");
        AnalyzeUseCase useCase = useCase(q -> { throw boom; }, () -> true, events::add);

        assertSame(boom, assertThrows(RuntimeException.class, () -> useCase.analyze("질문", null, Optional.of(USER))));

        assertEquals(1, events.size());
        assertEquals(QueryEvent.Status.ERROR, events.get(0).status());
        assertTrue(events.get(0).signedIn());
    }

    @Test
    void 기록이_실패해도_응답은_그대로() {
        AnalyzeUseCase useCase = useCase(q -> SUMMARY, () -> true, event -> { throw new IllegalStateException("기록 실패"); });

        AnalysisOutcome outcome = useCase.analyze("주택법 요약", null, Optional.of(USER));

        assertInstanceOf(AnalysisOutcome.Analyzed.class, outcome);
    }
}
