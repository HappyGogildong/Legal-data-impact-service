package com.lia.core.pipeline.resolve;

import java.util.List;

import com.lia.core.pipeline.connector.RawLaw;

/**
 * SourceAnalyzer 해소 결과 — <b>4상태, fail-closed</b> (D23).
 *
 * <p>MVP 대상은 의안이 아니라 시행예정 법령이므로 {@link RawLaw} 를 담는다(D42).
 *
 * <p><b>불변식을 생성자에서 강제한다.</b> fail-closed 는 이 서비스의 안전 요건인데
 * 규율이 문서와 팩토리 메서드 관례에만 있으면
 * {@code new ResolutionResult(RESOLVED, null, ...)} 같은 상태가 조용히 만들어진다.
 * 아래를 어기면 생성 자체가 실패한다:
 *
 * <ul>
 *   <li>{@code RESOLVED} ⇒ {@code resolved} 필수, 후보·유사 목록은 비어야 한다.</li>
 *   <li>{@code RESOLVED} 가 아니면 ⇒ {@code resolved} 는 반드시 {@code null}
 *       — 미해소 입력이 분석으로 새는 경로를 막는다.</li>
 *   <li>{@code AMBIGUOUS} ⇒ 후보가 하나 이상. 빈 후보는 사실상 미해소다.</li>
 *   <li>{@code RESOLVED} 가 아니면 ⇒ 사용자 안내 {@code message} 필수.
 *       미등록과 허위 의심은 문구가 달라야 한다(D23).</li>
 * </ul>
 *
 * <p>목록은 항상 불변 복사본이다 — 생성 후 후보를 밀어넣지 못한다.
 */
public record ResolutionResult(
        ResolutionState state,
        RawLaw resolved,           // RESOLVED 일 때만
        List<RawLaw> candidates,   // AMBIGUOUS 후보
        List<RawLaw> similar,      // UNVERIFIED 대조용
        String message
) {
    public ResolutionResult {
        if (state == null) {
            throw new IllegalArgumentException("해소 상태(state)는 필수다.");
        }
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        similar = similar == null ? List.of() : List.copyOf(similar);

        if (state == ResolutionState.RESOLVED) {
            if (resolved == null) {
                throw new IllegalArgumentException("RESOLVED 인데 해소된 법령이 없다 — fail-closed 위반.");
            }
            if (!candidates.isEmpty() || !similar.isEmpty()) {
                throw new IllegalArgumentException("RESOLVED 는 단정 상태다 — 후보를 함께 둘 수 없다.");
            }
        } else {
            if (resolved != null) {
                throw new IllegalArgumentException(
                        state + " 인데 해소 결과가 딸려 있다 — 미해소 입력이 분석으로 새는 경로다.");
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException(state + " 는 사용자 안내 문구가 필요하다 (D23).");
            }
        }
        if (state == ResolutionState.AMBIGUOUS && candidates.isEmpty()) {
            throw new IllegalArgumentException("AMBIGUOUS 인데 후보가 없다 — 사실상 미해소다.");
        }
    }

    public static ResolutionResult resolved(RawLaw law) {
        return new ResolutionResult(ResolutionState.RESOLVED, law, List.of(), List.of(), null);
    }

    public static ResolutionResult ambiguous(List<RawLaw> candidates, String message) {
        return new ResolutionResult(ResolutionState.AMBIGUOUS, null, candidates, List.of(), message);
    }

    public static ResolutionResult notFoundYet(String message) {
        return new ResolutionResult(ResolutionState.NOT_FOUND_YET, null, List.of(), List.of(), message);
    }

    public static ResolutionResult unverified(List<RawLaw> similar, String message) {
        return new ResolutionResult(ResolutionState.UNVERIFIED, null, List.of(), similar, message);
    }

    /** 분석 파이프라인 진입 가능 여부 — 게이트가 이 한 줄만 보면 되게. */
    public boolean analyzable() {
        return state == ResolutionState.RESOLVED;
    }
}
