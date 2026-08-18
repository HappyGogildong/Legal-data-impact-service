package com.lia.core.eval;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ClassPathResource;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.lia.core.pipeline.resolve.ResolutionState;

/**
 * 골든셋 — 레포에 <b>버전 고정</b>되는 평가 정답 픽스처(D14: 규칙+사람검수).
 *
 * <p>세 종류:
 * <ul>
 *   <li>{@link RetrievalCase} — 질의 → 기대 인용키(source_id). 법 구조에서 반자동 생성 가능.</li>
 *   <li>{@link RefusalCase} — 답할 수 없는 질의 → 기대 거부 상태(fail-closed 안전 게이트, S8/S9).</li>
 *   <li>Answer 골든(참조답+필수인용)은 <b>보조층</b>(RAGAS/사람)이라 포맷만 두고 이번 범위 밖.</li>
 * </ul>
 */
public final class GoldenSet {

    private static final ObjectMapper JSON = new ObjectMapper();

    private GoldenSet() {}

    /** 검색 평가 케이스 — 정답 조문의 인용키 집합. */
    public record RetrievalCase(String query, List<String> expectedSourceIds) {}

    /** 거부 평가 케이스 — 답하면 안 되는 질의와 기대 거부 상태. */
    public record RefusalCase(String query, ResolutionState expectedState) {}

    public static List<RetrievalCase> retrieval(String resourcePath) {
        return load(resourcePath, new TypeReference<List<RetrievalCase>>() {});
    }

    public static List<RefusalCase> refusal(String resourcePath) {
        return load(resourcePath, new TypeReference<List<RefusalCase>>() {});
    }

    private static <T> T load(String resourcePath, TypeReference<T> type) {
        try (InputStream in = new ClassPathResource(resourcePath).getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JSON.readValue(body, type);
        } catch (IOException e) {
            throw new UncheckedIOException("골든셋 로드 실패: " + resourcePath, e);
        }
    }
}
