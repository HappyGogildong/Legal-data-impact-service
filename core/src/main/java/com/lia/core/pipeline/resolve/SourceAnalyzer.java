package com.lia.core.pipeline.resolve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lia.core.pipeline.connector.RawBill;
import com.lia.core.pipeline.connector.SourceConnector;

/**
 * 사용자 입력(식별자/법안명/모호 자연어) → 법안 식별(resolve).
 *
 * 원칙 (설계: docs/components/SourceAnalyzer.md, D23):
 *  - LLM/규칙은 '분석가'가 아니라 '식별자(resolver)' — 입력 내용을 사실로 받지 않는다.
 *  - fail-closed: 신뢰 출처에서 확인되지 않으면 분석하지 않는다.
 *  - 해소 4상태(ResolutionState)로 구분. 미등록(NOT_FOUND_YET) vs 허위(UNVERIFIED) 안내 다름.
 *
 * Python 참조 구현: pipeline/.../ingest/source_analyzer.py (동일 임계값·판정 순서)
 */
public class SourceAnalyzer {

    private static final Pattern BILL_NO = Pattern.compile("(?:의안\\s*번호\\s*)?(\\d{6,})");
    private static final Pattern BILLISH =
            Pattern.compile("(법률안|개정안|제정안|법안|개정법률안|일부개정|전부개정|「.+?」)");

    private final List<SourceConnector> connectors;
    private final Function<String, List<RawBill>> semanticSearch; // 법안 의미검색(Vector). 미구현 시 null
    private final double confident;
    private final double ambiguousMin;
    private final boolean strict;

    public SourceAnalyzer(List<SourceConnector> connectors) {
        this(connectors, null, 88.0, 60.0, false);
    }

    public SourceAnalyzer(List<SourceConnector> connectors,
                          Function<String, List<RawBill>> semanticSearch,
                          double confident, double ambiguousMin, boolean strict) {
        this.connectors = connectors;
        this.semanticSearch = semanticSearch;
        this.confident = confident;
        this.ambiguousMin = ambiguousMin;
        this.strict = strict;
    }

    public ResolutionResult resolve(String input) {
        String text = input == null ? "" : input.trim();

        // 1) 의안번호 정확 조회
        Matcher m = Pattern.compile("의안\\s*번호\\s*(\\d{6,})").matcher(text);
        if (m.find()) {
            String billNo = m.group(1);
            RawBill raw = getByBillNo(billNo);
            if (raw != null) return ResolutionResult.resolved(raw);
            return ResolutionResult.notFoundYet(
                    "의안번호 " + billNo + "를 신뢰 출처에서 확인하지 못했습니다(아직 발의 전이거나 미등록일 수 있음).");
        }

        // 2) 법안명/키워드 정확·퍼지 매칭
        String query = text.lines().findFirst().orElse("").trim();
        if (query.length() > 60) query = query.substring(0, 60);
        List<RawBill> candidates = query.isEmpty() ? List.of() : searchAll(query);
        if (!candidates.isEmpty()) {
            final String q = query;
            List<RawBill> scored = candidates.stream()
                    .sorted(Comparator.comparingDouble((RawBill b) -> TokenSimilarity.ratio(b.title(), q)).reversed())
                    .toList();
            List<RawBill> strong = scored.stream()
                    .filter(b -> TokenSimilarity.ratio(b.title(), q) >= confident).toList();
            if (strong.size() == 1) return ResolutionResult.resolved(strong.get(0));
            if (strong.size() >= 2) {   // 동명/유사 법안 다수 → 단정 금지
                return ResolutionResult.ambiguous(strong.subList(0, Math.min(5, strong.size())),
                        "동일·유사 제목 법안이 여럿입니다. 어느 법안을 말씀하시나요?");
            }
            List<RawBill> decent = scored.stream()
                    .filter(b -> TokenSimilarity.ratio(b.title(), q) >= ambiguousMin)
                    .limit(5).toList();
            if (!decent.isEmpty()) {
                return ResolutionResult.ambiguous(decent, "여러 법안이 후보로 잡혔습니다. 어느 법안을 말씀하시나요?");
            }
        }

        // 3) 의미검색(법안 네임스페이스) — Embedder/Vector 구현 후 주입
        if (semanticSearch != null) {
            List<RawBill> sims = semanticSearch.apply(text);
            if (sims != null && !sims.isEmpty()) {
                return ResolutionResult.ambiguous(sims.subList(0, Math.min(5, sims.size())),
                        "유사 법안을 찾았습니다. 의도하신 법안인지 확인해 주세요.");
            }
        }

        // 4) 미등록 vs 허위 구분 (fail-closed — 지어내지 않는다)
        if (BILLISH.matcher(text).find()) {
            return ResolutionResult.notFoundYet(
                    "해당 법안을 신뢰 출처에서 확인하지 못했습니다(아직 발의 전이거나 미등록일 수 있음).");
        }
        return ResolutionResult.unverified(List.of(),
                "확인되지 않은 정보입니다. 실재하는 법안과 매칭되지 않습니다.");
    }

    // --- 내부 ------------------------------------------------------------

    private List<RawBill> searchAll(String query) {
        List<RawBill> out = new ArrayList<>();
        for (SourceConnector conn : connectors) {
            try {
                out.addAll(conn.search(query, 10));
            } catch (RuntimeException e) {
                if (strict) throw e;   // 아니면 스킵(무키·오프라인 견고)
            }
        }
        return out;
    }

    private RawBill getByBillNo(String billNo) {
        for (SourceConnector conn : connectors) {
            try {
                RawBill raw = conn.getByBillNo(billNo);
                if (raw != null) return raw;
            } catch (RuntimeException e) {
                if (strict) throw e;
            }
        }
        return null;
    }
}
