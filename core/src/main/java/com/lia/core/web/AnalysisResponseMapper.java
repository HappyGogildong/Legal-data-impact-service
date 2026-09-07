package com.lia.core.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.pipeline.resolve.ResolutionResult;

/**
 * 도메인 결과({@link AnalysisOutcome}) → 공개 API 응답 Map 매핑([[service-api-spec]] §3.0).
 * <b>표현(presentation) 관심사만</b> — HTTP는 {@link AnalysisController} 몫이라 분리했다.
 * 응답 키(snake_case)를 스펙대로 명시 조립한다.
 */
@Component
public class AnalysisResponseMapper {

    public Map<String, Object> toBody(AnalysisOutcome outcome) {
        return switch (outcome) {
            case AnalysisOutcome.Analyzed a -> analyzed(a);
            case AnalysisOutcome.Unresolved u -> unresolved(u.resolution());
        };
    }

    private static Map<String, Object> analyzed(AnalysisOutcome.Analyzed a) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resolution", "RESOLVED");
        if (a.query().target() instanceof Target.Reference ref) {
            body.put("law_ref", lawRef(ref.lawRef()));
        }
        Map<String, ImpactResult> answer = new LinkedHashMap<>();
        a.result().filled().forEach((dim, resp) -> answer.put(key(dim), resp.result()));
        body.put("answer", answer);
        body.put("unmet", a.result().unmet().keySet().stream().map(AnalysisResponseMapper::key).toList());

        // 불확실성·면책은 주 차원(없으면 아무 채워진 차원)의 결과에서 가져온다.
        AnalyzeResponse primary = a.result().filled().getOrDefault(a.result().primaryType(),
                a.result().filled().values().stream().findFirst().orElse(null));
        if (primary != null) {
            body.put("uncertainties", primary.result().uncertainties());
            body.put("disclaimer", primary.result().disclaimer());
        }
        return body;
    }

    private static Map<String, Object> unresolved(ResolutionResult r) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resolution", r.state().name());
        body.put("message", r.message());
        if (!r.candidates().isEmpty()) {
            body.put("candidates", r.candidates().stream().map(AnalysisResponseMapper::lawBrief).toList());
        }
        return body;
    }

    private static Map<String, Object> lawBrief(RawLaw law) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lawId", law.lawId());
        m.put("title", law.title());
        m.put("effectiveDate", law.effectiveDate());
        return m;
    }

    private static String lawRef(LawRef ref) {
        return "LAW:" + ref.lawId() + "@" + ref.effectiveDate();
    }

    /** 차원 → 응답 answer 키(소문자). */
    private static String key(QueryType dim) {
        return dim.name().toLowerCase();
    }
}
