package com.lia.core.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.discover.LawDiscovery;
import com.lia.core.pipeline.plan.AnalysisQueryDraft;
import com.lia.core.pipeline.plan.AnalysisQueryDraft.TargetKind;
import com.lia.core.pipeline.plan.QueryPlanner;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.resolve.LawLookup;
import com.lia.core.pipeline.resolve.SourceAnalyzer;
import com.lia.core.store.Chunk;
import com.lia.core.store.ChunkStore;
import com.lia.core.store.LawSource;

/** 서비스 경로(QueryPlanner → LawDiscovery)를 그대로 지나는지 — 가짜 번역기·조회·색인(D83). LLM·DB 없음. */
class OfflineEvaluatorTest {

    static final RawLaw HOUSING = new RawLaw("001809", "283191", "주택법", "시행예정",
            LocalDate.of(2026, 8, 4), LocalDate.of(2026, 2, 3), "21323", null);
    static final RawLaw LEASE = new RawLaw("002000", "283200", "주택임대차보호법", "시행예정",
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 3, 1), "21400", null);

    final Map<String, AnalysisQueryDraft> drafts = new HashMap<>();
    final List<Chunk> hits = new ArrayList<>();
    final Map<String, Law> laws = new HashMap<>();

    OfflineEvaluator evaluator(Function<String, List<RawLaw>> semanticSearch) {
        LawLookup lookup = (query, limit) -> query != null && query.contains("주택법") ? List.of(HOUSING) : List.of();
        QueryPlanner planner = new QueryPlanner(drafts::get, new SourceAnalyzer(lookup, semanticSearch, 88.0, 60.0));
        ChunkStore chunks = new ChunkStore() {
            @Override public void replaceVersion(String lawId, String efYd, List<Chunk> c) {}
            @Override public List<Chunk> search(String query, int topK, double minSimilarity) { return hits; }
        };
        LawSource source = new LawSource() {
            @Override public Optional<Law> find(String lawId, LocalDate efYd) {
                return Optional.ofNullable(laws.get(lawId + "@" + efYd));
            }
            @Override public Optional<Law> findBaseline(String lawId) { return Optional.empty(); }
        };
        LawDiscovery discovery = new LawDiscovery(chunks, source, law -> Optional.empty(),
                new LawDiscovery.Settings(40, 10, 0.3, 20, Set.of(Law.Status.시행예정)));
        return new OfflineEvaluator(planner, discovery);
    }

    static AnalysisQueryDraft reference(String lawName, String articleNo, QueryType type) {
        return new AnalysisQueryDraft(type, Set.of(type), true, TargetKind.REFERENCE, lawName, articleNo,
                List.of(), List.of(), List.of(), "의도");
    }

    static AnalysisQueryDraft topic() {
        return new AnalysisQueryDraft(QueryType.LOOKUP, Set.of(QueryType.LOOKUP), true, TargetKind.DISCOVERY,
                null, null, List.of("전세"), List.of(), List.of(), "찾기");
    }

    static AnalysisQueryDraft offTopic() {
        return new AnalysisQueryDraft(QueryType.SUMMARY, Set.of(), false, TargetKind.DISCOVERY,
                null, null, List.of(), List.of(), List.of(), "잡담");
    }

    static Law pending(String lawId, String efYd) {
        return new Law(lawId, "mst-" + lawId, "법-" + lawId, Law.Status.시행예정, Law.AmendKind.일부개정, Law.LawType.법률,
                "국토교통부", LocalDate.of(2026, 2, 3), "21323", LocalDate.parse(efYd), "공포 후 6개월",
                Law.EnforcementType.유예, "개정 이유", "개정문", List.of(),
                List.of(new Article("1", null, "제1조 본문", true, Article.ChangeType.개정,
                        null, null, null, true, null)),
                List.of(), null, null, "rev1", Instant.now());
    }

    static Chunk chunkOf(String lawId, String efYd) {
        return new Chunk("LAW:" + lawId + "@" + efYd, "개정이유",
                Map.of("kind", "summary", "lawId", lawId, "efYd", efYd, "namespace", "pending"));
    }

    @Test
    @DisplayName("법령을 지목하면 해소한 법령 하나와 조문·차원을 낸다")
    void 법령_지목() {
        drafts.put("주택법 제18조 뭐가 바뀌었어?", reference("주택법", "제18조", QueryType.DIFF));

        CaseOutcome o = evaluator(null).evaluate("주택법 제18조 뭐가 바뀌었어?");

        assertFalse(o.refused());
        assertTrue(o.resolved());
        assertEquals(List.of("001809"), o.candidateLawIds());
        assertEquals("18", o.articleNo());
        assertEquals(QueryType.DIFF, o.primaryType());
    }

    @Test
    @DisplayName("주제 질문은 법령 발견 후보를 순서대로 낸다")
    void 주제_질문() {
        drafts.put("전세 제도 바뀌어?", topic());
        laws.put("B@2026-10-01", pending("B", "2026-10-01"));
        laws.put("A@2026-09-01", pending("A", "2026-09-01"));
        hits.add(chunkOf("B", "2026-10-01"));
        hits.add(chunkOf("A", "2026-09-01"));

        CaseOutcome o = evaluator(null).evaluate("전세 제도 바뀌어?");

        assertFalse(o.refused());
        assertFalse(o.resolved());
        assertEquals(List.of("B", "A"), o.candidateLawIds());
        assertEquals(QueryType.LOOKUP, o.primaryType());
    }

    @Test
    @DisplayName("발견 후보가 없으면 후보 없음이지 거부가 아니다")
    void 발견_후보_없음() {
        drafts.put("우주 면허 생기나?", topic());

        CaseOutcome o = evaluator(null).evaluate("우주 면허 생기나?");

        assertFalse(o.refused());
        assertEquals(List.of(), o.candidateLawIds());
    }

    @Test
    @DisplayName("비법령 질문은 거부한다")
    void 비법령_거부() {
        drafts.put("점심 뭐 먹지", offTopic());

        CaseOutcome o = evaluator(null).evaluate("점심 뭐 먹지");

        assertTrue(o.refused());
        assertEquals(List.of(), o.candidateLawIds());
        assertNull(o.primaryType());
    }

    @Test
    @DisplayName("찾을 수 없는 법령은 거부한다")
    void 없는_법령_거부() {
        drafts.put("가상의무슨법 바뀌어?", reference("가상의무슨법", null, QueryType.SUMMARY));

        CaseOutcome o = evaluator(null).evaluate("가상의무슨법 바뀌어?");

        assertTrue(o.refused());
        assertFalse(o.resolved());
    }

    @Test
    @DisplayName("해소 후보가 여럿이면 후보로 내고 거부하지 않는다")
    void 해소_후보_여럿() {
        drafts.put("집 구하는 법 바뀌어?", reference("집 구하는 법", null, QueryType.SUMMARY));

        CaseOutcome o = evaluator(text -> List.of(HOUSING, LEASE)).evaluate("집 구하는 법 바뀌어?");

        assertFalse(o.refused());
        assertFalse(o.resolved());
        assertEquals(List.of("001809", "002000"), o.candidateLawIds());
        assertNull(o.primaryType());
    }
}
