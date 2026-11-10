package com.lia.core.eval;

import java.util.Collections;
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
 */
public record EvalReport(RagConfig config, long indexedLaws, int cases, int excludedCases,
                         Map<String, Measure> metrics) {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    public EvalReport {
        metrics = Collections.unmodifiableMap(new TreeMap<>(metrics));
    }

    public String toJson() {
        return JSON.writeValueAsString(this);
    }

    public static EvalReport fromJson(String json) {
        return JSON.readValue(json, EvalReport.class);
    }
}
