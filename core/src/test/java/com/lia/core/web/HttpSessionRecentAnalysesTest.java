package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.web.HttpSessionRecentAnalyses.AnsweredQuestion;

/** HttpSessionRecentAnalyses 단위 — 보관·LRU 20·재생성 연결(같은 법령만, 덮어쓰기). */
class HttpSessionRecentAnalysesTest {

    static final LawRef REF = new LawRef("001809", LocalDate.of(2026, 11, 8), null);
    static final AnalysisApiResponse BODY = AnalysisApiResponse.unresolved("NOT_FOUND_YET", "x", List.of());
    static final ActionRevisionApiResponse REVISION = ActionRevisionApiResponse.notRevised(null);

    @Test
    void 보관하면_새_id_로_찾고_재생성본은_비어_있다() {
        HttpSessionRecentAnalyses recent = new HttpSessionRecentAnalyses(new MockHttpSession());
        String id = recent.remember("주택법 바뀌면?", REF, BODY);

        HttpSessionRecentAnalyses.Entry entry = recent.find(id).orElseThrow();
        assertEquals("주택법 바뀌면?", entry.query());
        assertEquals(REF, entry.lawRef());
        assertSame(BODY, entry.body());
        assertNull(entry.revision());
        assertTrue(entry.answers().isEmpty());
        assertNotEquals(id, recent.remember("다른 질문", REF, BODY), "매번 새 id");
    }

    @Test
    void 최근_20개만_남는다() {
        HttpSessionRecentAnalyses recent = new HttpSessionRecentAnalyses(new MockHttpSession());
        String first = recent.remember("q0", REF, BODY);
        for (int i = 1; i <= 20; i++) recent.remember("q" + i, REF, BODY);

        assertTrue(recent.find(first).isEmpty());
    }

    @Test
    void 같은_법령이면_재생성본과_답을_붙이고_다시_붙이면_덮어쓴다() {
        HttpSessionRecentAnalyses recent = new HttpSessionRecentAnalyses(new MockHttpSession());
        String id = recent.remember("q", REF, BODY);
        LawRef sameLawWithArticle = new LawRef("001809", LocalDate.of(2026, 11, 8), "18");

        recent.attachRevision(id, sameLawWithArticle, REVISION, List.of(new AnsweredQuestion("계약했나요?", "예")));
        recent.attachRevision(id, REF, REVISION, List.of(new AnsweredQuestion("계약했나요?", "아니오")));

        HttpSessionRecentAnalyses.Entry entry = recent.find(id).orElseThrow();
        assertSame(REVISION, entry.revision());
        assertEquals(List.of(new AnsweredQuestion("계약했나요?", "아니오")), entry.answers());
    }

    @Test
    void 다른_법령이거나_없는_id_면_붙이지_않는다() {
        HttpSessionRecentAnalyses recent = new HttpSessionRecentAnalyses(new MockHttpSession());
        String id = recent.remember("q", REF, BODY);

        recent.attachRevision(id, new LawRef("000001", LocalDate.of(2026, 11, 8), null), REVISION, List.of());
        recent.attachRevision("없는-id", REF, REVISION, List.of());

        assertNull(recent.find(id).orElseThrow().revision());
    }
}
