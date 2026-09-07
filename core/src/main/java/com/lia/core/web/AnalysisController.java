package com.lia.core.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.plan.LawRef;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.pipeline.plan.Target;
import com.lia.core.pipeline.resolve.ResolutionResult;

/**
 * {@code POST /api/v1/analyses} — 자연어 질의 분석 진입점([[service-api-spec]] §3.0).
 * 요청 검증(빈 query→400) → {@link AnalysisService} → 응답 매핑. 해소 4상태·분석은 모두 200
 * (4xx/5xx는 시스템 오류 전용, §4.1). 응답 키는 스펙(snake_case)에 맞춰 명시 조립한다.
 */
@RestController
@RequestMapping("/api/v1/analyses")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody AnalyzeApiRequest req) {
        if (req == null || req.query() == null || req.query().isBlank()) {
            throw new IllegalArgumentException("query는 필수입니다.");
        }
        AnalysisOutcome outcome = service.analyze(req.query().strip(), req.explicitRef());
        Map<String, Object> body = switch (outcome) {
            case AnalysisOutcome.Analyzed a -> analyzed(a);
            case AnalysisOutcome.Unresolved u -> unresolved(u.resolution());
        };
        return ResponseEntity.ok(body); // 해소 결과·분석 모두 200
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
        body.put("unmet", a.result().unmet().keySet().stream().map(AnalysisController::key).toList());

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
            body.put("candidates", r.candidates().stream().map(AnalysisController::lawBrief).toList());
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
