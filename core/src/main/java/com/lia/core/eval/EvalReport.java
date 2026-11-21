package com.lia.core.eval;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 오프라인 평가 보고서(D83) — 기준선 파일과 같은 형식. 한 줄 JSON 으로 표준 출력에 낸다
 * (일회성 컨테이너가 지워져도 로그에 남게).
 *
 * @param config      평가 당시 법령 발견 설정
 * @param indexedLaws 색인된 법령 수 — 경쟁 법령이 적으면 점수가 부풀려지므로 함께 본다
 * @param cases         평가한 문항 수
 * @param excludedCases 정답 법령이 더 이상 시행예정이 아니어서 뺀 문항 수
 * @param metrics     지표 키 → 값·분모(키 순서 고정)
 * @param caseResults 문항별 결과(평가 순서) — 기준선 파일에는 없어도 된다(빈 목록). 회귀 판정은 {@code metrics} 만 본다
 */
public record EvalReport(RagConfig config, long indexedLaws, int cases, int excludedCases,
                         Map<String, Measure> metrics, List<CaseResult> caseResults) {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    public EvalReport {
        metrics = Collections.unmodifiableMap(new TreeMap<>(metrics));
        caseResults = caseResults == null ? List.of() : List.copyOf(caseResults);
    }

    /** 문항별 결과 없이 — 기준선처럼 지표만 있을 때. */
    public EvalReport(RagConfig config, long indexedLaws, int cases, int excludedCases, Map<String, Measure> metrics) {
        this(config, indexedLaws, cases, excludedCases, metrics, List.of());
    }

    public String toJson() {
        return JSON.writeValueAsString(this);
    }

    public static EvalReport fromJson(String json) {
        return JSON.readValue(json, EvalReport.class);
    }
}
