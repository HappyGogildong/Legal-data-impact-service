package com.lia.core.web;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import com.lia.core.application.analysis.AnalysisOutcome;
import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.discover.DiscoveredLaw;
import com.lia.core.pipeline.discover.DiscoveryResult;
import com.lia.core.pipeline.dispatch.DispatchResult;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.pipeline.plan.AnalysisQuery;
import com.lia.core.pipeline.plan.ArticleScope;
import com.lia.core.pipeline.plan.DiscoveryCriteria;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.pipeline.resolve.ResolutionResult;
import com.lia.core.store.LawSource;

/** AnalysisResponseMapper 단위 — 도메인 결과 → typed 응답(§3.0). 정본은 메모리 LawSource. */
class AnalysisResponseMapperTest {

    static final LocalDate EF = LocalDate.of(2026, 8, 4);
    static final String CITE = "LAW:001809@2026-08-04:art:18";

    static final class MemoryLawSource implements LawSource {
        final Map<String, Law> versions = new HashMap<>();
        public Optional<Law> find(String lawId, LocalDate efYd) { return Optional.ofNullable(versions.get(lawId + "@" + efYd)); }
        public Optional<Law> findBaseline(String lawId) { return Optional.empty(); }
    }

    static Law law() {
        return new Law("001809", "283191", "주택법", Law.Status.시행예정, Law.AmendKind.일부개정, Law.LawType.법률,
                "국토교통부", LocalDate.of(2026, 2, 3), "21323", EF, "공포 후 6개월", Law.EnforcementType.유예,
                "이유", "개정문", List.of(),
                List.of(new Article("18", "통합심의", "① 통합심의한다.", true, Article.ChangeType.개정, null, null, EF, true, null)),
                List.of(), null, "url", "rev", Instant.now());
    }

    private static AnalysisResponseMapper mapper(boolean lawLoaded) {
        MemoryLawSource source = new MemoryLawSource();
        if (lawLoaded) source.versions.put("001809@" + EF, law());
        return new AnalysisResponseMapper(source, new CitedSourceResolver(source));
    }

    private static AnalysisOutcome.Analyzed analyzed() {
        LawRef ref = new LawRef("001809", EF, null);
        AnalysisQuery query = new AnalysisQuery(QueryType.SUMMARY, Set.of(QueryType.SUMMARY, QueryType.DIFF),
                new Target.Reference(ref), "요약", ArticleScope.CHANGED_ONLY, false, null);
        ImpactResult summary = new ImpactResult("LAW:001809@2026-08-04", "SUMMARY", "주택법 개정 요약",
                List.of(new ImpactResult.Claim("개정된다", List.of(CITE), 0.9)),
                List.of(), List.of(), null, List.of("일부 위임"), "참고용", null);
        ImpactResult diff = new ImpactResult("LAW:001809@2026-08-04", "DIFF", "바뀐 점",
                List.of(new ImpactResult.Claim("제18조가 바뀐다", List.of(CITE), 0.9)),
                List.of(), List.of(), null, List.of(), "참고용", null);
        DispatchResult result = new DispatchResult(QueryType.SUMMARY,
                Map.of(QueryType.DIFF, new AnalyzeResponse(diff, Set.of(CITE)),
                       QueryType.SUMMARY, new AnalyzeResponse(summary, Set.of(CITE))),
                Map.of(QueryType.IMPACT, new Unmet(Unmet.Reason.PROFILE_REQUIRED, "프로필 필요 (Layer B)")));
        RawLaw later = new RawLaw("001809", "290000", "주택법", "시행예정", LocalDate.of(2027, 2, 1), null, null, null);
        return new AnalysisOutcome.Analyzed(query, result, List.of(later));
    }

    @Test
    @DisplayName("Analyzed — 법령 헤더·다른 시행일·answer(차원 선언 순)·unmet·sources·disclaimer")
    void 분석결과() {
        AnalysisApiResponse body = mapper(true).toBody(analyzed());

        assertEquals("RESOLVED", body.resolution());
        assertEquals(new AnalysisApiResponse.LawHeader("LAW:001809@2026-08-04", "001809", "주택법", EF, "법률", "국토교통부"), body.law());
        assertEquals(List.of(new AnalysisApiResponse.LawBrief("001809", "주택법", LocalDate.of(2027, 2, 1))), body.alternatives());
        assertEquals(List.of("summary", "diff"), List.copyOf(body.answer().keySet()), "QueryType 선언 순");
        assertEquals(new Unmet(Unmet.Reason.PROFILE_REQUIRED, "프로필 필요 (Layer B)"), body.unmet().get("impact"));
        assertEquals(1, body.sources().size(), "같은 인용은 한 번");
        assertEquals("주택법 제18조(통합심의)", body.sources().get(0).label());
        assertEquals("참고용", body.disclaimer());
        assertNull(body.message());
    }

    @Test
    @DisplayName("정본 미적재 — 헤더는 참조 값만(법령명 null), 인용은 label=sourceId")
    void 정본미적재() {
        AnalysisApiResponse body = mapper(false).toBody(analyzed());

        assertEquals("001809", body.law().lawId());
        assertNull(body.law().title());
        assertEquals(CITE, body.sources().get(0).label());
    }

    @Test
    @DisplayName("Unresolved — resolution·message·candidates, 분석 필드 없음")
    void 미해소() {
        RawLaw candidate = new RawLaw("001809", "283191", "주택법", "시행예정", EF, LocalDate.of(2026, 2, 3), "21323", null);

        AnalysisApiResponse body = mapper(true).toBody(new AnalysisOutcome.Unresolved(
                ResolutionResult.ambiguous(List.of(candidate), "여러 후보가 있어요.")));

        assertEquals("AMBIGUOUS", body.resolution());
        assertEquals("여러 후보가 있어요.", body.message());
        assertEquals(List.of(new AnalysisApiResponse.LawBrief("001809", "주택법", EF)), body.candidates());
        assertNull(body.answer());
        assertNull(body.law());
    }

    @Test
    @DisplayName("JSON — camelCase, 값 없는 최상위 필드는 생략(law_ref 없음)")
    void 직렬화() {
        String json = JsonMapper.builder().build().writeValueAsString(mapper(true).toBody(analyzed()));

        assertTrue(json.contains("\"law\":{\"ref\":\"LAW:001809@2026-08-04\""), json);
        assertFalse(JsonMapper.builder().build().readTree(json).has("message"), "RESOLVED 엔 최상위 message 없음(unmet 값 안의 message는 별개)");
        assertFalse(json.contains("law_ref"));
        assertTrue(json.contains("\"unmet\":{\"impact\":{\"reason\":\"PROFILE_REQUIRED\""), json);
    }

    @Test
    @DisplayName("JSON — 발견 응답은 discovery 만(answer·law 키 없음), 카드는 matched 근거를 가진다")
    void 발견_직렬화() {
        AnalysisQuery query = new AnalysisQuery(QueryType.LOOKUP, Set.of(QueryType.LOOKUP),
                new Target.Discovery(new DiscoveryCriteria(List.of(), List.of(), List.of(), false)),
                "찾기", ArticleScope.CHANGED_ONLY, false, null);
        DiscoveredLaw card = new DiscoveredLaw("001809", "주택법", EF, "국토교통부", "거주 의무가 바뀌어요.", true,
                new DiscoveredLaw.MatchedSource("LAW:001809@2026-08-04:art:46", "제46조"));
        DispatchResult result = new DispatchResult(QueryType.LOOKUP, Map.of(), Map.of(),
                new DispatchResult.Discovery(new DiscoveryResult(List.of(card), false),
                        new Unmet(Unmet.Reason.PROFILE_REQUIRED, "프로필 기준으로 찾을 수 없음")));

        tools.jackson.databind.JsonNode json = JsonMapper.builder().build().readTree(
                JsonMapper.builder().build().writeValueAsString(
                        mapper(true).toBody(new AnalysisOutcome.Analyzed(query, result))));

        assertEquals("RESOLVED", json.get("resolution").asString());
        assertEquals("제46조", json.at("/discovery/candidates/0/matched/label").asString());
        assertTrue(json.at("/discovery/candidates/0/summaryReady").asBoolean());
        assertFalse(json.at("/discovery/profileApplied").asBoolean());
        assertEquals("PROFILE_REQUIRED", json.at("/discovery/profileBlocked/reason").asString());
        assertFalse(json.has("answer"));
        assertFalse(json.has("law"));
    }

    @Test
    @DisplayName("JSON — 프로필 차단 사유가 없으면 discovery.profileBlocked 키를 생략한다")
    void 발견_직렬화_차단없음() {
        AnalysisQuery query = new AnalysisQuery(QueryType.LOOKUP, Set.of(QueryType.LOOKUP),
                new Target.Discovery(new DiscoveryCriteria(List.of(), List.of(), List.of(), false)),
                "찾기", ArticleScope.CHANGED_ONLY, false, null);
        DispatchResult result = new DispatchResult(QueryType.LOOKUP, Map.of(), Map.of(),
                new DispatchResult.Discovery(new DiscoveryResult(List.of(), true), null));

        tools.jackson.databind.JsonNode json = JsonMapper.builder().build().readTree(
                JsonMapper.builder().build().writeValueAsString(
                        mapper(true).toBody(new AnalysisOutcome.Analyzed(query, result))));

        assertTrue(json.at("/discovery/profileApplied").asBoolean());
        assertFalse(json.get("discovery").has("profileBlocked"), json.toString());
    }
}
