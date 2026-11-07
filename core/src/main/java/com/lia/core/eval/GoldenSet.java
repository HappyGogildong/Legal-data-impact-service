package com.lia.core.eval;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.DefaultResourceLoader;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정답 세트 로더 — 레포에 <b>버전 고정</b>되는 평가 픽스처(D83). 형식: {@link EvalCase}.
 * 설계: docs/eval/rag-evaluation-framework.md §5.1.
 */
public final class GoldenSet {

    /** 생략한 불리언(거부 문항의 {@code lawNamed} 등)은 false 로 읽는다 — Jackson 3 기본값은 거부. */
    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private GoldenSet() {}

    /**
     * 정답 세트 — JSON 배열. 형식이 틀린 문항이 하나라도 있으면 전체를 거부한다.
     *
     * @param location 접두사 없으면 클래스패스, {@code file:} 이면 파일(운영에서 이미지 밖 파일을 줄 때)
     */
    public static List<EvalCase> cases(String location) {
        return load(location, new TypeReference<List<EvalCase>>() {});
    }

    private static <T> T load(String location, TypeReference<T> type) {
        try (InputStream in = new DefaultResourceLoader().getResource(location).getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JSON.readValue(body, type);
        } catch (IOException e) {
            throw new UncheckedIOException("정답 세트 로드 실패: " + location, e);
        }
    }
}
