package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.web.HttpSessionRecentAnalyses.AnsweredQuestion;

/** HttpSessionRecentAnalyses 단위 — 보관·LRU 20·사용자 묶기·재생성 연결(같은 법령·같은 질문 묶음만, 덮어쓰기). */
class HttpSessionRecentAnalysesTest {

    static final LawRef REF = new LawRef("001809", LocalDate.of(2026, 11, 8), null);
    static final UUID USER = UUID.randomUUID();
    static final UUID OTHER_USER = UUID.randomUUID();
    static final String SET_ID = "set-1";
    static final AnalysisApiResponse BODY = AnalysisApiResponse.unresolved("NOT_FOUND_YET", "x", List.of());
    /** 상황 질문 묶음(setId)이 있는 분석 — 재생성 연결 대상. */
    static final AnalysisApiResponse BODY_WITH_SITUATION = AnalysisApiResponse.analyzed(
            new AnalysisApiResponse.LawHeader("LAW:001809@2026-11-08", "001809", "주택법", LocalDate.of(2026, 11, 8), "법률", "국토교통부"),
            List.of(), java.util.Map.of(), java.util.Map.of(), List.of(), "참고용",
            new AnalysisApiResponse.SituationBody(SET_ID, List.of(), List.of()));
    static final ActionRevisionApiResponse REVISION = ActionRevisionApiResponse.notRevised(null);

    @Test
    void 보관하면_새_id_로_찾고_재생성본은_비어_있다() {
        HttpSessionRecentAnalyses recent = new HttpSessionRecentAnalyses(new MockHttpSession());
        String id = recent.remember(USER, "주택법 바뀌면?", REF, BODY);

        HttpSessionRecentAnalyses.Entry entry = recent.find(id, USER).orElseThrow();
        assertEquals("주택법 바뀌면?", entry.query());
        assertEquals(REF, entry.lawRef());
        assertSame(BODY, entry.body());
        assertNull(entry.revision());
        assertTrue(entry.answers().isEmpty());
        assertEquals(USER, entry.userId());
        assertNotEquals(id, recent.remember(USER, "다른 질문", REF, BODY), "매번 새 id");
    }

    @Test
    void 최근_20개만_남는다() {
        HttpSessionRecentAnalyses recent = new HttpSessionRecentAnalyses(new MockHttpSession());
        String first = recent.remember(USER, "q0", REF, BODY);
        for (int i = 1; i <= 20; i++) recent.remember(USER, "q" + i, REF, BODY);

        assertTrue(recent.find(first, USER).isEmpty());
    }

    @Test
    void 같은_법령이면_재생성본과_답을_붙이고_다시_붙이면_덮어쓴다() {
        HttpSessionRecentAnalyses recent = new HttpSessionRecentAnalyses(new MockHttpSession());
        String id = recent.remember(USER, "q", REF, BODY_WITH_SITUATION);
        LawRef sameLawWithArticle = new LawRef("001809", LocalDate.of(2026, 11, 8), "18");

        recent.attachRevision(id, USER, sameLawWithArticle, SET_ID, REVISION, List.of(new AnsweredQuestion("계약했나요?", "예")));
        recent.attachRevision(id, USER, REF, SET_ID, REVISION, List.of(new AnsweredQuestion("계약했나요?", "아니오")));

        HttpSessionRecentAnalyses.Entry entry = recent.find(id, USER).orElseThrow();
        assertSame(REVISION, entry.revision());
        assertEquals(List.of(new AnsweredQuestion("계약했나요?", "아니오")), entry.answers());
    }

    @Test
    void 다른_법령이거나_없는_id_면_붙이지_않는다() {
        HttpSessionRecentAnalyses recent = new HttpSessionRecentAnalyses(new MockHttpSession());
        String id = recent.remember(USER, "q", REF, BODY_WITH_SITUATION);

        recent.attachRevision(id, USER, new LawRef("000001", LocalDate.of(2026, 11, 8), null), SET_ID, REVISION, List.of());
        recent.attachRevision("없는-id", USER, REF, SET_ID, REVISION, List.of());

        assertNull(recent.find(id, USER).orElseThrow().revision());
    }

    @Test
    void 질문_묶음_setId_가_다르거나_분석에_상황이_없으면_붙이지_않는다() {
        HttpSessionRecentAnalyses recent = new HttpSessionRecentAnalyses(new MockHttpSession());
        String withSituation = recent.remember(USER, "q", REF, BODY_WITH_SITUATION);
        String withoutSituation = recent.remember(USER, "q", REF, BODY);

        recent.attachRevision(withSituation, USER, REF, "다른-묶음", REVISION, List.of(new AnsweredQuestion("q", "예")));
        recent.attachRevision(withoutSituation, USER, REF, SET_ID, REVISION, List.of(new AnsweredQuestion("q", "예")));

        assertNull(recent.find(withSituation, USER).orElseThrow().revision());
        assertTrue(recent.find(withSituation, USER).orElseThrow().answers().isEmpty());
        assertNull(recent.find(withoutSituation, USER).orElseThrow().revision());
    }

    @Test
    void 다른_사용자는_찾지도_붙이지도_못한다() {
        // 로그아웃 없이 같은 브라우저에서 다른 계정으로 로그인하면 세션 속성이 남는다
        HttpSessionRecentAnalyses recent = new HttpSessionRecentAnalyses(new MockHttpSession());
        String id = recent.remember(USER, "q", REF, BODY_WITH_SITUATION);

        assertTrue(recent.find(id, OTHER_USER).isEmpty());
        recent.attachRevision(id, OTHER_USER, REF, SET_ID, REVISION, List.of(new AnsweredQuestion("q", "예")));

        assertNull(recent.find(id, USER).orElseThrow().revision());
    }
}
