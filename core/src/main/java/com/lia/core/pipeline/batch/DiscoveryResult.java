package com.lia.core.pipeline.batch;

import java.util.Map;

/**
 * 탐색 결과.
 *
 * @param listed        받은 목록 행 수(목록 실패면 0)
 * @param listComplete  목록을 끝까지 받았는가 — false 면 사라짐 판정을 하지 않았다
 * @param planned       종류별 계획 건수(dry-run 에서도 채워짐)
 * @param enqueued      종류별 실제 등록 건수(중복은 제외)
 * @param markedMissing 첫 미확인으로 missing_since 를 기록한 건수
 * @param duplicateKeys 같은 (lawId, 시행일)이 목록에 또 나온 건수 — 0 이 아니면 식별 키 가정(D43)이 깨졌다
 * @param malformedRows lawId·시행일·MST 중 하나라도 없어 건너뛴 행 수 — 0 이 아니면 목록을 미완주로 본다
 */
public record DiscoveryResult(int listed, boolean listComplete, Map<TaskKind, Integer> planned,
                              Map<TaskKind, Integer> enqueued, int markedMissing, int duplicateKeys,
                              int malformedRows) {
}
