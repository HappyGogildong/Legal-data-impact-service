package com.lia.core.pipeline.discover;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.profile.HousingType;
import com.lia.core.profile.UserProfile;
import com.lia.core.store.Chunk;
import com.lia.core.store.ChunkStore;
import com.lia.core.store.LawSource;
import org.junit.jupiter.api.Test;

/** LawDiscovery 단위 — 가짜 ChunkStore·LawSource·LawSummaries. 임베딩·DB·LLM 없음. */
class LawDiscoveryTest {

    static final LawDiscovery.Settings SETTINGS =
            new LawDiscovery.Settings(40, 10, 0.3, 20, Set.of(Law.Status.시행예정));

    /** 받은 질의·하한을 기록하고 정해진 히트를 돌려준다. */
    static final class FakeChunkStore implements ChunkStore {
        final List<Chunk> hits = new ArrayList<>();
        String lastQuery;
        int lastTopK;
        double lastMinSimilarity;
        @Override public void replaceVersion(String lawId, String efYd, List<Chunk> chunks) {}
        @Override public List<Chunk> search(String query, int topK, double minSimilarity) {
            lastQuery = query;
            lastTopK = topK;
            lastMinSimilarity = minSimilarity;
            return hits.stream().limit(topK).toList();
        }
    }

    static final class FakeLawSource implements LawSource {
        final Map<String, Law> versions = new HashMap<>();
        public Optional<Law> find(String lawId, LocalDate efYd) {
            return Optional.ofNullable(versions.get(lawId + "@" + efYd));
        }
        public Optional<Law> findBaseline(String lawId) { return Optional.empty(); }
    }

    final FakeChunkStore chunks = new FakeChunkStore();
    final FakeLawSource laws = new FakeLawSource();
    final Map<String, ImpactResult> summaries = new HashMap<>();

    LawDiscovery discovery(LawDiscovery.Settings settings) {
        return new LawDiscovery(chunks, laws, law -> Optional.ofNullable(summaries.get(law.lawId())), settings);
    }

    static Law law(String lawId, String title, String efYd, Law.Status status, String amendReason) {
        List<Article> articles = List.of(
                new Article("46", "입주자의 거주의무", "제46조 본문", true, Article.ChangeType.개정,
                        null, null, null, true, null),
                new Article("105의2", null, "제105조의2 본문", true, Article.ChangeType.신설,
                        null, null, null, true, null));
        return new Law(lawId, "mst-" + lawId, title, status, Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                LocalDate.of(2026, 2, 3), "21323", LocalDate.parse(efYd), "공포 후 6개월", Law.EnforcementType.유예,
                amendReason, "개정문", List.of(), articles, List.of(), null, null, "rev1", Instant.now());
    }

    static Chunk article(String lawId, String efYd, String articleNo, String part) {
        return new Chunk("LAW:" + lawId + "@" + efYd + ":art:" + articleNo + part, "본문",
                Map.of("kind", "article", "lawId", lawId, "efYd", efYd, "articleNo", articleNo, "namespace", "pending"));
    }

    static Chunk summaryChunk(String lawId, String efYd) {
        return new Chunk("LAW:" + lawId + "@" + efYd, "개정이유",
                Map.of("kind", "summary", "lawId", lawId, "efYd", efYd, "namespace", "pending"));
    }

    static ImpactResult summary(String text) {
        return new ImpactResult("LAW:x", "SUMMARY", text, List.of(), List.of(), List.of(), null, List.of(), "참고", null);
    }

    @Test
    void 같은_법령의_여러_청크는_가장_유사한_하나로_묶고_순서를_지킨다() {
        laws.versions.put("001809@2026-08-04", law("001809", "주택법", "2026-08-04", Law.Status.시행예정, "이유"));
        laws.versions.put("002000@2026-09-01", law("002000", "주택임대차보호법", "2026-09-01", Law.Status.시행예정, "이유"));
        chunks.hits.addAll(List.of(
                article("001809", "2026-08-04", "46", ""),
                article("002000", "2026-09-01", "46", ""),
                summaryChunk("001809", "2026-08-04")));

        DiscoveryResult result = discovery(SETTINGS).discover("전세 관련 바뀌는 법", Optional.empty());

        assertEquals(List.of("001809", "002000"), result.candidates().stream().map(DiscoveredLaw::lawId).toList());
        assertEquals("LAW:001809@2026-08-04:art:46", result.candidates().get(0).matched().sourceId(),
                "가장 유사한(먼저 온) 청크가 근거");
        assertEquals(40, chunks.lastTopK);
        assertEquals(0.3, chunks.lastMinSimilarity);
    }

    @Test
    void 정본이_없거나_시행예정이_아니면_뺀다() {
        laws.versions.put("001809@2026-08-04", law("001809", "주택법", "2026-08-04", Law.Status.시행중, "이유"));
        laws.versions.put("003000@2026-10-01", law("003000", "건축법", "2026-10-01", Law.Status.시행예정, "이유"));
        chunks.hits.addAll(List.of(
                article("001809", "2026-08-04", "46", ""),    // 이미 시행 — 제외
                article("009999", "2026-08-04", "46", ""),    // 정본 없음 — 제외
                article("003000", "2026-10-01", "46", "")));

        DiscoveryResult result = discovery(SETTINGS).discover("질문", Optional.empty());

        assertEquals(List.of("003000"), result.candidates().stream().map(DiscoveredLaw::lawId).toList());
    }

    @Test
    void 메타에_lawId나_efYd가_없는_청크는_건너뛴다() {
        laws.versions.put("003000@2026-10-01", law("003000", "건축법", "2026-10-01", Law.Status.시행예정, "이유"));
        chunks.hits.addAll(List.of(
                new Chunk("LAW:x", "본문", Map.of("kind", "article")),
                new Chunk("LAW:y", "본문", Map.of("kind", "article", "lawId", "003000", "efYd", "날짜아님")),
                article("003000", "2026-10-01", "46", "")));

        assertEquals(1, discovery(SETTINGS).discover("질문", Optional.empty()).candidates().size());
    }

    @Test
    void 최대_개수까지만() {
        for (int i = 0; i < 5; i++) {
            String id = "00000" + i;
            laws.versions.put(id + "@2026-10-01", law(id, "법" + i, "2026-10-01", Law.Status.시행예정, "이유"));
            chunks.hits.add(article(id, "2026-10-01", "46", ""));
        }
        LawDiscovery.Settings three = new LawDiscovery.Settings(40, 3, 0.3, 20, Set.of(Law.Status.시행예정));

        assertEquals(3, discovery(three).discover("질문", Optional.empty()).candidates().size());
    }

    @Test
    void 쉬운_요약이_있으면_첫_문장을_쓴다() {
        laws.versions.put("001809@2026-08-04", law("001809", "주택법", "2026-08-04", Law.Status.시행예정, "이유"));
        summaries.put("001809", summary("분양 주택의 거주 의무가 바뀌어요. 1.5배 늘어납니다. 자세한 내용은 조문을 보세요."));
        chunks.hits.add(article("001809", "2026-08-04", "46", ""));

        DiscoveredLaw card = discovery(SETTINGS).discover("질문", Optional.empty()).candidates().get(0);

        assertEquals("분양 주택의 거주 의무가 바뀌어요.", card.summary());
        assertTrue(card.summaryReady());
        assertEquals("주택법", card.title());
        assertEquals(LocalDate.of(2026, 8, 4), card.effectiveDate());
        assertEquals("국토교통부", card.ministry());
    }

    @Test
    void 요약_첫_문장은_소수점에서_자르지_않고_120자를_넘으면_줄인다() {
        assertEquals("1.5배 늘어나요.", LawDiscovery.firstSentence("1.5배 늘어나요. 다음 문장"));
        String longText = "가".repeat(130);
        assertEquals("가".repeat(120) + "…", LawDiscovery.firstSentence(longText));
        assertNull(LawDiscovery.firstSentence("  "));
    }

    @Test
    void 요약이_없으면_개정_이유_앞부분과_준비_중() {
        laws.versions.put("001809@2026-08-04",
                law("001809", "주택법", "2026-08-04", Law.Status.시행예정, "  분양가상한제   적용주택의 거주의무 기간을 조정하려는 것임  "));
        chunks.hits.add(article("001809", "2026-08-04", "46", ""));

        DiscoveredLaw card = discovery(SETTINGS).discover("질문", Optional.empty()).candidates().get(0);

        assertEquals("분양가상한제 적용주택의 거주의무 기간…", card.summary(), "공백 정규화 후 snippet-chars(20)자 + …");
        assertFalse(card.summaryReady());
    }

    @Test
    void 요약도_개정_이유도_없으면_summary_null() {
        laws.versions.put("001809@2026-08-04", law("001809", "주택법", "2026-08-04", Law.Status.시행예정, null));
        chunks.hits.add(article("001809", "2026-08-04", "46", ""));

        DiscoveredLaw card = discovery(SETTINGS).discover("질문", Optional.empty()).candidates().get(0);

        assertNull(card.summary());
        assertFalse(card.summaryReady());
    }

    @Test
    void 근거_라벨_조문은_번호와_제목_가지조는_제N조의M_요약_청크는_개정_이유() {
        laws.versions.put("001809@2026-08-04", law("001809", "주택법", "2026-08-04", Law.Status.시행예정, "이유"));
        laws.versions.put("002000@2026-09-01", law("002000", "주택임대차보호법", "2026-09-01", Law.Status.시행예정, "이유"));
        laws.versions.put("003000@2026-10-01", law("003000", "건축법", "2026-10-01", Law.Status.시행예정, "이유"));
        chunks.hits.addAll(List.of(
                article("001809", "2026-08-04", "46", "#1"),
                article("002000", "2026-09-01", "105의2", ""),
                summaryChunk("003000", "2026-10-01")));

        List<DiscoveredLaw> cards = discovery(SETTINGS).discover("질문", Optional.empty()).candidates();

        assertEquals(new DiscoveredLaw.MatchedSource("LAW:001809@2026-08-04:art:46", "제46조 입주자의 거주의무"),
                cards.get(0).matched(), "분할 접미사 #1 제거");
        assertEquals("제105조의2", cards.get(1).matched().label(), "제목 없으면 번호만");
        assertEquals(new DiscoveredLaw.MatchedSource("LAW:003000@2026-10-01", "개정 이유"), cards.get(2).matched());
    }

    @Test
    void 프로필이_있으면_질문_뒤에_라벨_줄을_붙이고_적용_표시() {
        UserProfile profile = new UserProfile(null, 34, null, null, null, HousingType.전세, null);

        DiscoveryResult result = discovery(SETTINGS).discover("전세 관련 법", Optional.of(profile));

        assertEquals("전세 관련 법\n나이: 34\n주거: 전세\n", chunks.lastQuery);
        assertTrue(result.profileApplied());
    }

    @Test
    void 프로필이_없으면_질문만() {
        DiscoveryResult result = discovery(SETTINGS).discover("전세 관련 법", Optional.empty());

        assertEquals("전세 관련 법", chunks.lastQuery);
        assertFalse(result.profileApplied());
        assertTrue(result.candidates().isEmpty(), "히트 없으면 빈 목록(오류 아님)");
    }
}
