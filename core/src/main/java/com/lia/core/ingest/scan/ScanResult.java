package com.lia.core.ingest.scan;

import java.util.Map;

import com.lia.core.ingest.task.TaskKind;

/**
 * 탐색 결과.
 *
 * @param listed        받은 목록 행 수(목록 실패면 0)
 * @param listComplete  목록을 끝까지 받았는가 — false 면 사라짐 판정을 하지 않았다
 * @param planned       종류별 계획 건수(dry-run 에서도 채워짐)
 * @param enqueued      종류별 실제 등록 건수(중복은 제외)
 * @param markedMissing 첫 미확인으로 missing_since 를 기록한 건수
 * @param mergedKeys    같은 (lawId, 시행일)에 MST 가 둘 이상이라 공포본을 묶은 키 수(D85) — 정보, 경보 아님
 * @param malformedRows lawId·시행일·MST 중 하나라도 없어 건너뛴 행 수 — 0 이 아니면 목록을 미완주로 본다
 */
public record ScanResult(int listed, boolean listComplete, Map<TaskKind, Integer> planned,
                              Map<TaskKind, Integer> enqueued, int markedMissing, int mergedKeys,
                              int malformedRows) {
}
