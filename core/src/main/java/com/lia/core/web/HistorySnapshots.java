package com.lia.core.web;

import java.util.List;

import com.lia.core.web.HistoryApiDtos.HistorySnapshot;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이력 스냅샷(D73) 직렬화 — 앱 기본 매퍼가 아니라 <b>전용 관대한 매퍼</b>로 쓰고 읽는다. 저장한 행은 DTO 가 바뀐 뒤에도
 * 읽혀야 하므로, 알 수 없는 필드·모르는 enum 상수·primitive 자리의 null 로는 실패하지 않는다(그 밖의 읽기 실패는
 * {@link tools.jackson.core.JacksonException}). 앱 매퍼 설정과 무관하게 동작해 단위 테스트로 고정할 수 있다.
 * 설계: docs/components/web/HistoryApi.md "스냅샷 버전·관대한 읽기".
 */
final class HistorySnapshots {

    /** 지금 쓰는 스냅샷 형식 버전 — 없는 옛 스냅샷도 1 로 본다. */
    static final int CURRENT_VERSION = 1;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
            .build();

    private HistorySnapshots() {}

    static HistorySnapshot snapshot(AnalysisApiResponse body, ActionRevisionApiResponse revision,
                                    List<HttpSessionRecentAnalyses.AnsweredQuestion> answers) {
        return new HistorySnapshot(CURRENT_VERSION, body, revision, answers);
    }

    static String write(HistorySnapshot snapshot) {
        return MAPPER.writeValueAsString(snapshot);
    }

    static HistorySnapshot read(String json) {
        return MAPPER.readValue(json, HistorySnapshot.class);
    }
}
