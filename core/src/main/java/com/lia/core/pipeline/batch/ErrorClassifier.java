package com.lia.core.pipeline.batch;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.HttpClientErrorException;

import tools.jackson.core.JacksonException;

import com.lia.core.pipeline.connector.LawApiException;

/**
 * 작업 실패 분류(D62). 영구 = 다시 해도 같은 결과 → 즉시 DEAD(같은 실패로 API 를 계속 태우지 않게).
 * 그 밖(네트워크·타임아웃·408·429·모르는 예외)은 일시적 → 다음 실행에서 재시도.
 */
public final class ErrorClassifier {

    private ErrorClassifier() {}

    public static boolean isPermanent(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof LawApiException                       // API 논리 오류(봉투 오류)
                    || t instanceof IllegalArgumentException          // 정규화·도메인 검증 실패
                    || t instanceof DataIntegrityViolationException   // 제약 위반 — 코드 버그
                    || t instanceof JacksonException                  // 파싱 실패
                    || (t instanceof HttpClientErrorException http && !transientStatus(http.getStatusCode().value()))) {
                return true;
            }
        }
        return false;
    }

    /** 4xx 중 일시적인 것 — 408(요청 시간 초과)·429(호출 한도). 나머지 4xx 는 다시 해도 같다. */
    private static boolean transientStatus(int status) {
        return status == 408 || status == 429;
    }
}
