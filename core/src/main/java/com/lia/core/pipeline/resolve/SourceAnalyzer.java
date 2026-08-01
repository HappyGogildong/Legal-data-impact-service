package com.lia.core.pipeline.resolve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.lia.core.pipeline.connector.RawLaw;

/**
 * 사용자 입력(법령명 / 모호 자연어) → <b>시행예정 법령</b> 식별(resolve).
 *
 * <p>원칙 (설계: docs/components/SourceAnalyzer.md, D23):
 * <ul>
 *   <li>LLM·규칙은 '분석가'가 아니라 <b>식별자(resolver)</b> — 입력 내용을 사실로 받지 않는다.</li>
 *   <li><b>fail-closed</b>: 신뢰 출처에서 확인되지 않으면 분석하지 않는다.</li>
 *   <li>해소 4상태로 구분. 미등록({@code NOT_FOUND_YET})과 허위 의심({@code UNVERIFIED})은
 *       사용자 안내 문구가 다르다.</li>
 * </ul>
 *
 * <p><b>D42 이관:</b> 대상이 의안 → 시행예정 법령으로 바뀌면서 의안번호 분기가 사라졌다.
 * 법령에는 의안번호가 없고 사용자도 법령ID(예: {@code 001809})를 입력하지 않는다.
 * 대신 <b>법령명 매칭 → 의미검색 → fail-closed</b> 3단계다.
 */
public class SourceAnalyzer {

    /** "법령스러운" 입력인지 — 미등록(NOT_FOUND_YET)과 허위(UNVERIFIED)를 가르는 신호. */
    private static final Pattern LAWISH = Pattern.compile(
            "(법률|법령|시행령|시행규칙|조례|특별법|개정|제정|「.+?」|[가-힣]+법)");

    private final LawLookup lookup;                              // 없으면 정확매칭 생략(견고)
    private final Function<String, List<RawLaw>> semanticSearch; // 의미검색(pending ns). 미구현 시 null
    private final double confident;
    private final double ambiguousMin;

    public SourceAnalyzer(LawLookup lookup) {
        this(lookup, null, 88.0, 60.0);
    }

    public SourceAnalyzer(LawLookup lookup,
                          Function<String, List<RawLaw>> semanticSearch,
                          double confident, double ambiguousMin) {
        this.lookup = lookup;
        this.semanticSearch = semanticSearch;
        this.confident = confident;
        this.ambiguousMin = ambiguousMin;
    }

    public ResolutionResult resolve(String input) {
        String text = input == null ? "" : input.trim();

        // 1) 법령명 정확·퍼지 매칭
        String query = text.lines().findFirst().orElse("").trim();
        if (query.length() > 60) query = query.substring(0, 60);

        List<RawLaw> candidates = query.isEmpty() ? List.of() : search(query);
        if (!candidates.isEmpty()) {
            final String q = query;
            List<RawLaw> scored = candidates.stream()
                    .sorted(Comparator.comparingDouble((RawLaw l) -> TokenSimilarity.ratio(l.title(), q)).reversed())
                    .toList();

            List<RawLaw> strong = scored.stream()
                    .filter(l -> TokenSimilarity.ratio(l.title(), q) >= confident).toList();

            if (strong.size() == 1) return ResolutionResult.resolved(strong.get(0));
            if (strong.size() >= 2) {
                // 제목이 같은 여러 건 = 대개 같은 법령의 시행예정본이 겹친 것(D43).
                // 어느 시점 기준인지 단정할 수 없으므로 사용자에게 되묻는다.
                return ResolutionResult.ambiguous(top(strong), ambiguityMessage(strong));
            }

            List<RawLaw> decent = scored.stream()
                    .filter(l -> TokenSimilarity.ratio(l.title(), q) >= ambiguousMin).toList();
            if (!decent.isEmpty()) {
                return ResolutionResult.ambiguous(top(decent), "여러 법령이 후보로 잡혔습니다. 어느 것을 말씀하시나요?");
            }
        }

        // 2) 의미검색(pending 네임스페이스) — Embedder/VectorStore 구현 후 주입
        if (semanticSearch != null) {
            List<RawLaw> sims = semanticSearch.apply(text);
            if (sims != null && !sims.isEmpty()) {
                return ResolutionResult.ambiguous(top(sims), "유사한 법령을 찾았습니다. 의도하신 것인지 확인해 주세요.");
            }
        }

        // 3) 미등록 vs 허위 구분 (fail-closed — 지어내지 않는다)
        if (LAWISH.matcher(text).find()) {
            return ResolutionResult.notFoundYet(
                    "해당 법령을 신뢰 출처에서 확인하지 못했습니다"
                    + "(아직 공포되지 않았거나, 이미 시행 중이어서 분석 대상이 아닐 수 있습니다).");
        }
        return ResolutionResult.unverified(List.of(),
                "확인되지 않은 정보입니다. 실재하는 법령과 매칭되지 않습니다.");
    }

    // --- 내부 ------------------------------------------------------------

    private List<RawLaw> search(String query) {
        if (lookup == null) return List.of();
        try {
            List<RawLaw> found = lookup.searchByName(query, 10);
            return found == null ? List.of() : found;
        } catch (RuntimeException e) {
            return List.of();   // 무키·오프라인에서도 fail-closed 로 떨어지게
        }
    }

    private static List<RawLaw> top(List<RawLaw> list) {
        return list.subList(0, Math.min(5, list.size()));
    }

    /**
     * 같은 법령ID가 시행일만 달리해 겹친 경우와, 서로 다른 법령이 겹친 경우를
     * 구분해 안내한다(D43 — 어느 시행예정본 기준인지는 아직 정책 미정).
     */
    private static String ambiguityMessage(List<RawLaw> strong) {
        Map<String, List<RawLaw>> byLawId = new LinkedHashMap<>();
        for (RawLaw l : strong) {
            byLawId.computeIfAbsent(String.valueOf(l.lawId()), k -> new ArrayList<>()).add(l);
        }
        if (byLawId.size() == 1) {
            return "같은 법령에 시행 예정인 개정이 여러 건입니다. 어느 시행일 기준으로 보시겠습니까?";
        }
        return "제목이 유사한 법령이 여럿입니다. 어느 것을 말씀하시나요?";
    }
}
