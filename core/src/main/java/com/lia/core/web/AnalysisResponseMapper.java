package com.lia.core.web;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.lia.core.application.analysis.AnalysisOutcome;
import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.pipeline.resolve.ResolutionResult;
import com.lia.core.store.LawSource;
import com.lia.core.web.AnalysisApiResponse.LawBrief;
import com.lia.core.web.AnalysisApiResponse.LawHeader;

/**
 * 도메인 결과({@link AnalysisOutcome}) → 공개 API 응답({@link AnalysisApiResponse}, [[service-api-spec]] §3.0).
 * <b>표현 관심사</b> — 화면 표시용 참조 데이터(법령 헤더·근거 원문)는 {@link LawSource}에서 읽는다.
 * ponytail: 디스패처도 같은 정본을 읽어 요청당 조회가 1~2회 늘어난다. 문제가 되면 디스패치 결과에 정본을 실어 재사용.
 */
@Component
public class AnalysisResponseMapper {

    private final LawSource laws;
    private final CitedSourceResolver sources;

    public AnalysisResponseMapper(LawSource laws, CitedSourceResolver sources) {
        this.laws = laws;
        this.sources = sources;
    }

    public AnalysisApiResponse toBody(AnalysisOutcome outcome) {
        return switch (outcome) {
            case AnalysisOutcome.Analyzed a -> analyzed(a);
            case AnalysisOutcome.Unresolved u -> unresolved(u.resolution());
        };
    }

    private AnalysisApiResponse analyzed(AnalysisOutcome.Analyzed a) {
        LawHeader law = a.query().target() instanceof Target.Reference ref ? header(ref.lawRef()) : null;

        // 차원 키는 QueryType 선언 순(summary·diff·impact·action) — 화면의 카드 순서와 같게
        Map<String, ImpactResult> answer = new LinkedHashMap<>();
        Map<String, Unmet> unmet = new LinkedHashMap<>();
        for (QueryType dim : QueryType.values()) {
            AnalyzeResponse filled = a.result().filled().get(dim);
            if (filled != null) answer.put(key(dim), filled.result());
            Unmet reason = a.result().unmet().get(dim);
            if (reason != null) unmet.put(key(dim), reason);
        }

        // 면책만 응답 전역(상수)으로 올린다. 불확실성은 차원별 내용이라 answer.<차원>에만 둔다(중복 방지).
        AnalyzeResponse primary = a.result().filled().getOrDefault(a.result().primaryType(),
                a.result().filled().values().stream().findFirst().orElse(null));
        String disclaimer = primary == null ? null : primary.result().disclaimer();

        List<LawBrief> alternatives = a.alternatives().stream().map(AnalysisResponseMapper::brief).toList();
        return AnalysisApiResponse.analyzed(law, alternatives, answer, unmet,
                sources.resolve(citations(answer.values())), disclaimer);
    }

    private static AnalysisApiResponse unresolved(ResolutionResult r) {
        return AnalysisApiResponse.unresolved(r.state().name(), r.message(),
                r.candidates().stream().map(AnalysisResponseMapper::brief).toList());
    }

    /** 정본이 있으면 법령명·종류·소관부처까지, 없으면 참조 값만. */
    private LawHeader header(LawRef ref) {
        String refKey = "LAW:" + ref.lawId() + "@" + ref.effectiveDate();
        return laws.find(ref.lawId(), ref.effectiveDate())
                .map(l -> new LawHeader(refKey, l.lawId(), l.title(), l.effectiveDate(),
                        l.lawType() == null ? null : l.lawType().name(), l.ministry()))
                .orElse(new LawHeader(refKey, ref.lawId(), null, ref.effectiveDate(), null, null));
    }

    /** answer 안 모든 인용키 — 주장·영향의 citations, 대응의 basis(등장 순). */
    private static Set<String> citations(Collection<ImpactResult> results) {
        Set<String> ids = new LinkedHashSet<>();
        for (ImpactResult r : results) {
            if (r.claims() != null) r.claims().forEach(c -> addAll(ids, c.citations()));
            if (r.impacts() != null) r.impacts().forEach(i -> addAll(ids, i.citations()));
            if (r.actions() != null) r.actions().forEach(act -> addAll(ids, act.basis()));
        }
        return ids;
    }

    private static void addAll(Set<String> ids, List<String> values) {
        if (values != null) ids.addAll(values);
    }

    private static LawBrief brief(RawLaw law) {
        return new LawBrief(law.lawId(), law.title(), law.effectiveDate());
    }

    /** 차원 → 응답 answer 키(소문자). */
    private static String key(QueryType dim) {
        return dim.name().toLowerCase();
    }
}
