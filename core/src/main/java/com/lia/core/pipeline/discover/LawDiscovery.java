package com.lia.core.pipeline.discover;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.profile.UserProfile;
import com.lia.core.store.Chunk;
import com.lia.core.store.ChunkStore;
import com.lia.core.store.LawSource;
import com.lia.core.store.LawSummaries;

/**
 * 법령 발견(LOOKUP, D70) — 법령명 없는 질문으로 시행예정 색인을 검색해 후보 카드를 만든다. LLM 없음.
 *
 * <p>질문(토글 시 + 프로필 라벨 줄) 임베딩 → 유사도 하한 이상 청크 → 법령 단위로 가장 유사한 청크 하나 →
 * 정본이 지금 후보 상태(기본 시행예정)인 것만(fail-closed — 색인에만 남은 낡은 청크를 믿지 않는다) → 최대 N장.
 * 카드 본문은 쉬운 요약 저장본(D67) 첫 문장 — 법령 원문은 접근성이 낮아 근거 칩(→ S05)으로만 잇는다.
 *
 * <p>Spring 애노테이션 없음 — {@code PipelineConfig} 의 {@code @Bean} 이 배선한다.
 */
public class LawDiscovery {

    private static final int SUMMARY_MAX_CHARS = 120;
    private static final String ELLIPSIS = "…";
    private static final String AMEND_REASON_LABEL = "개정 이유";

    /**
     * @param chunkFetch        묶기 전에 가져올 청크 수(한 법령이 여러 청크를 차지하므로 넉넉히)
     * @param maxCandidates     후보 최대 장수
     * @param minSimilarity     코사인 유사도 하한 — 클라우드 전체 색인 뒤 실데이터로 조정
     * @param snippetChars      요약이 없을 때 개정 이유를 보여 줄 글자 수
     * @param candidateStatuses 후보로 남길 정본 상태(최근 시행 포함은 별도 하위 프로젝트)
     */
    public record Settings(int chunkFetch, int maxCandidates, double minSimilarity, int snippetChars,
                           Set<Law.Status> candidateStatuses) {
        public Settings {
            candidateStatuses = Set.copyOf(candidateStatuses);
        }
    }

    private final ChunkStore chunks;
    private final LawSource laws;
    private final LawSummaries summaries;
    private final Settings settings;

    public LawDiscovery(ChunkStore chunks, LawSource laws, LawSummaries summaries, Settings settings) {
        this.chunks = chunks;
        this.laws = laws;
        this.summaries = summaries;
        this.settings = settings;
    }

    /**
     * @param question 사용자 질문 원문
     * @param profile  프로필 기준으로 찾을 때만(토글 + 쓸 수 있는 프로필) — 닫힌 값 라벨만 검색어에 붙는다(userId 없음)
     */
    public DiscoveryResult discover(String question, Optional<UserProfile> profile) {
        String searchText = profile.map(p -> question + "\n" + p.labelLines()).orElse(question);
        List<Chunk> hits = chunks.search(searchText, settings.chunkFetch(), settings.minSimilarity());

        // 유사도 순 그대로 — 법령마다 처음 나온(가장 유사한) 청크만
        Map<String, Chunk> bestByLaw = new LinkedHashMap<>();
        for (Chunk hit : hits) {
            Object lawId = hit.metadata().get("lawId");
            // 시행일이 없거나 날짜가 아닌 청크는 법령의 대표 자리를 차지하기 전에 거른다
            if (lawId == null || efYdOf(hit).isEmpty()) continue;
            bestByLaw.putIfAbsent(String.valueOf(lawId), hit);
        }

        List<DiscoveredLaw> candidates = new ArrayList<>();
        for (Chunk best : bestByLaw.values()) {
            if (candidates.size() >= settings.maxCandidates()) break;
            canonical(best)
                    .filter(law -> settings.candidateStatuses().contains(law.status()))
                    .ifPresent(law -> candidates.add(card(law, best)));
        }
        return new DiscoveryResult(candidates, profile.isPresent());
    }

    /** 청크 메타의 시행일 — 없거나 날짜가 아니면 empty. */
    private static Optional<LocalDate> efYdOf(Chunk chunk) {
        Object efYd = chunk.metadata().get("efYd");
        if (efYd == null) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(String.valueOf(efYd)));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** 청크가 가리키는 정본. */
    private Optional<Law> canonical(Chunk chunk) {
        return efYdOf(chunk).flatMap(efYd -> laws.find(String.valueOf(chunk.metadata().get("lawId")), efYd));
    }

    private DiscoveredLaw card(Law law, Chunk matched) {
        String easySummary = summaries.findCurrent(law)
                .map(ImpactResult::summary)
                .map(LawDiscovery::firstSentence)
                .orElse(null);
        boolean summaryReady = easySummary != null;
        String summary = summaryReady ? easySummary : snippet(law.amendReason());
        return new DiscoveredLaw(law.lawId(), law.title(), law.effectiveDate(), law.ministry(),
                summary, summaryReady, matchedSource(law, matched));
    }

    /** 첫 문장 — 뒤가 공백이거나 끝인 `.`·`?` 까지(소수점 "1.5배" 는 자르지 않는다), 최대 120자. */
    static String firstSentence(String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.strip();
        int end = trimmed.length();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean boundary = i + 1 == trimmed.length() || Character.isWhitespace(trimmed.charAt(i + 1));
            if ((c == '.' || c == '?') && boundary) {
                end = i + 1;
                break;
            }
        }
        return truncate(trimmed.substring(0, end), SUMMARY_MAX_CHARS);
    }

    private String snippet(String amendReason) {
        if (amendReason == null || amendReason.isBlank()) return null;
        return truncate(amendReason.strip().replaceAll("\\s+", " "), settings.snippetChars());
    }

    private static String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + ELLIPSIS;
    }

    /** 근거 칩 — 조문이면 "제46조 입주자의 거주의무"(제목은 정본에서), 요약 청크면 "개정 이유". */
    private static DiscoveredLaw.MatchedSource matchedSource(Law law, Chunk chunk) {
        String sourceId = withoutPart(chunk.sourceId());
        Object articleNo = chunk.metadata().get("articleNo");
        if (articleNo == null) return new DiscoveredLaw.MatchedSource(sourceId, AMEND_REASON_LABEL);
        String no = String.valueOf(articleNo);
        Article article = law.article(no);
        String title = article == null || article.title() == null || article.title().isBlank() ? null : article.title();
        String label = title == null ? Article.labelOf(no) : Article.labelOf(no) + " " + title;
        return new DiscoveredLaw.MatchedSource(sourceId, label);
    }

    /** 긴 조문 분할 청크의 "#k" 접미사를 떼어 조문 인용키로. */
    private static String withoutPart(String sourceId) {
        int part = sourceId.indexOf('#');
        return part < 0 ? sourceId : sourceId.substring(0, part);
    }
}
