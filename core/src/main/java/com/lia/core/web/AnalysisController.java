package com.lia.core.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lia.core.application.analysis.AnalysisOutcome;
import com.lia.core.application.analysis.AnalyzeUseCase;

/**
 * {@code POST /api/v1/analyses} — 자연어 질의 분석 진입점([[service-api-spec]] §3.0).
 * <b>HTTP 경계만</b> 담당한다: 요청 검증(빈 query→400) → {@link AnalysisService} 위임 →
 * 응답 매핑은 {@link AnalysisResponseMapper}에 맡기고 상태코드만 얹는다. 해소 4상태·분석은 모두 200
 * (4xx/5xx는 시스템 오류 전용, §4.1).
 */
@RestController
@RequestMapping("/api/v1/analyses")
public class AnalysisController {

    private final AnalyzeUseCase useCase;
    private final AnalysisResponseMapper mapper;

    public AnalysisController(AnalyzeUseCase useCase, AnalysisResponseMapper mapper) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody AnalyzeApiRequest req) {
        if (req == null || req.query() == null || req.query().isBlank()) {
            throw new IllegalArgumentException("query는 필수입니다.");
        }
        AnalysisOutcome outcome = useCase.analyze(req.query().strip(), req.explicitRef());
        return ResponseEntity.ok(mapper.toBody(outcome)); // 해소 결과·분석 모두 200
    }
}
