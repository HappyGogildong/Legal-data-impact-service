package com.lia.core.pipeline.analyze;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.analysis.Situation;
import com.lia.core.domain.analysis.SituationQuestion;
import com.lia.core.domain.analysis.StepNote;
import com.lia.core.eval.FaithfulnessGate;
import com.lia.core.pipeline.plan.QueryType;

/**
 * 해석 오케스트레이터(얇음) — {@code 조립 → 추론 → 인용검증 → 재생성(≤N) → 폴백}(D37 명시적 워크플로).
 * 검색 없음(정합화) — {@link ContextBuilder}가 정확 조회된 정본에서 context를 조립한다.
 *
 * <p>스펙: docs/components/AnalysisEngine.md · docs/prompts/analysis-prompt-spec.md.
 */
public class AnalysisEngine {

    private final ContextBuilder contextBuilder;
    private final Reasoner reasoner;
    private final int maxAttempts;

    public AnalysisEngine(ContextBuilder contextBuilder, Reasoner reasoner, int maxAttempts) {
        this.contextBuilder = contextBuilder;
        this.reasoner = reasoner;
        this.maxAttempts = maxAttempts;
    }

    public AnalyzeResponse analyze(AnalyzeRequest req) {
        AnalysisContext ctx = contextBuilder.build(req);
        Set<String> injected = ctx.injectedSourceIds();

        // 조립된 근거로 추론 → 1차 인용검증. 실패 시 재생성(≤N), 끝까지 실패면 근거부족 폴백(환각 미노출).
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (req.dimension().isLayerB()) {
                PersonalizedOutput out = reasoner.reasonPersonalized(ctx);
                if (grounded(req.dimension(), out.result(), injected)) {
                    // 재생성(ACTION)은 새 질문을 내지 않는다 — 질문은 첫 응답에서 세션에 보관된 것을 쓴다(D71)
                    List<SituationQuestion> questions = req.dimension() == QueryType.ACTION
                            ? List.of() : admissibleQuestions(out.questions(), injected);
                    return new AnalyzeResponse(out.result(), injected, new Situation(questions,
                            admissibleStepNotes(out.stepNotes(), out.result().actions().size())));
                }
                continue;
            }
            ImpactResult result = reasoner.reason(ctx);
            if (grounded(req.dimension(), result, injected)) {
                return new AnalyzeResponse(result, injected);
            }
        }
        throw new InsufficientGroundingException(
                "인용 검증 %d회 실패 — 근거 부족(환각 노출 금지)".formatted(maxAttempts));
    }

    static final int MAX_QUESTIONS = 3;

    /** 화면에 낼 상황 질문 — 형식이 맞고 근거가 주입 집합 안인 것만, 최대 3개, id 를 q1·q2·q3 로 다시 붙인다(D71). */
    static List<SituationQuestion> admissibleQuestions(List<SituationQuestion> raw, Set<String> injectedSourceIds) {
        List<SituationQuestion> kept = raw.stream()
                .filter(q -> isAdmissible(q, injectedSourceIds))
                .limit(MAX_QUESTIONS)
                .toList();
        List<SituationQuestion> renumbered = new ArrayList<>();
        for (int i = 0; i < kept.size(); i++) renumbered.add(kept.get(i).withId("q" + (i + 1)));
        return List.copyOf(renumbered);
    }

    /** 모델이 낸 날것 질문 하나 — 검사 중 예외가 나면 그 질문만 버린다(엔진은 죽지 않는다). */
    private static boolean isAdmissible(SituationQuestion question, Set<String> injectedSourceIds) {
        try {
            return question != null && question.wellFormed() && injectedSourceIds.contains(question.basis());
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    /** 단계 설명 — 대응 범위 안·비지 않음·한 줄 200자 이내, 같은 단계는 처음 것만. */
    static List<StepNote> admissibleStepNotes(List<StepNote> raw, int actionCount) {
        Map<Integer, StepNote> byIndex = new LinkedHashMap<>();
        for (StepNote n : raw) {
            if (n == null || n.index() < 0 || n.index() >= actionCount) continue;
            String note = n.note();
            if (note == null || note.isBlank() || note.length() > SituationQuestion.MAX_TEXT
                    || note.indexOf('\n') >= 0) continue;
            byIndex.putIfAbsent(n.index(), n);
        }
        return List.copyOf(byIndex.values());
    }

    /**
     * 인용 존재성(§6.2) — 결과의 <b>모든</b> 인용 source_id가 주입 컨텍스트에만 실재해야 한다.
     * 실패·스키마불일치는 예외가 아니라 {@code false}(→재생성/폴백)로 흘려 환각을 노출하지 않는다.
     *
     * <p>① {@code claims}: 누락(null)·공백은 근거 없는 결과이므로 실패(NPE·공허통과 방지).
     * 각 주장은 ≥1 인용 + 모두 실재(eval {@link FaithfulnessGate} 규칙 재사용).
     * ② {@code impacts}·{@code actions}가 인용한 source_id도 모두 실재해야 한다 — 환각 인용이
     * 이 필드로 새는 것을 차단(DIFF는 impacts를 산출한다, §5).
     *
     * <p>현재 Layer A(SUMMARY·DIFF)는 claims를 필수로 본다. Layer B 는
     * {@link #grounded(QueryType, ImpactResult, Set)}가 더 엄격히 본다(D69).
     * 저장된 요약 재검증({@link GroundedSummaries})도 이 규칙을 그대로 쓴다.
     */
    static boolean grounded(ImpactResult r, Set<String> injectedSourceIds) {
        if (r == null || r.claims() == null || r.claims().isEmpty()) {
            return false;   // 스키마 불일치(누락)·무근거 → 재생성 대상
        }
        List<FaithfulnessGate.Claim> claims = r.claims().stream()
                .map(c -> new FaithfulnessGate.Claim(c.statement(), c.citations()))
                .toList();
        if (!FaithfulnessGate.passes(claims, injectedSourceIds)) {
            return false;
        }
        return injectedSourceIds.containsAll(citedSourceIds(r));
    }

    /**
     * 차원별 인용 검증 — Layer A 는 {@link #grounded(ImpactResult, Set)} 그대로, Layer B 는 거기에 더해
     * 영향·대응이 <b>둘 다</b> 있고 항목마다 인용(영향 citations·대응 basis)이 있어야 한다(D69).
     * 둘 다 요구하는 건 결과를 IMPACT·ACTION 으로 나눴을 때 두 차원이 늘 채워지게 하려는 것 — 해당이 없으면
     * 모델이 "영향 없음"·"따로 할 일 없음"을 조문 인용과 함께 쓴다.
     */
    static boolean grounded(QueryType dimension, ImpactResult r, Set<String> injectedSourceIds) {
        if (dimension == QueryType.ACTION) return actionGrounded(r, injectedSourceIds);   // 재생성(D71)
        if (!grounded(r, injectedSourceIds)) return false;
        return !dimension.isLayerB() || personalizedComplete(r);
    }

    /** 대응 방안 재생성 — 대응 ≥1·각 근거 ≥1·모든 인용이 주입 집합 안(주장은 필수 아님). 상황 사실은 근거 집합 밖. */
    private static boolean actionGrounded(ImpactResult r, Set<String> injectedSourceIds) {
        return r != null && r.actions() != null && !r.actions().isEmpty()
                && r.actions().stream().allMatch(a -> a.basis() != null && !a.basis().isEmpty())
                && injectedSourceIds.containsAll(citedSourceIds(r));
    }

    private static boolean personalizedComplete(ImpactResult r) {
        return r.impacts() != null && !r.impacts().isEmpty()
                && r.actions() != null && !r.actions().isEmpty()
                && r.impacts().stream().allMatch(i -> i.citations() != null && !i.citations().isEmpty())
                && r.actions().stream().allMatch(a -> a.basis() != null && !a.basis().isEmpty());
    }

    /** 결과가 인용한 모든 source_id 수집(claims·impacts·actions.basis) — null 안전. */
    static Set<String> citedSourceIds(ImpactResult r) {
        Set<String> ids = new HashSet<>();
        if (r.claims() != null) {
            r.claims().forEach(c -> { if (c.citations() != null) ids.addAll(c.citations()); });
        }
        if (r.impacts() != null) {
            r.impacts().forEach(i -> { if (i.citations() != null) ids.addAll(i.citations()); });
        }
        if (r.actions() != null) {
            r.actions().forEach(a -> { if (a.basis() != null) ids.addAll(a.basis()); });
        }
        return ids;
    }
}
