package com.lia.core.pipeline.resolve;

import java.util.List;

import com.lia.core.pipeline.connector.RawLaw;

/**
 * SourceAnalyzer 해소 결과 (D23 — 4상태, fail-closed).
 *
 * <p>MVP 대상은 의안이 아니라 <b>시행예정 법령</b>이므로 RawLaw 를 담는다(D42).
 */
public record ResolutionResult(
        ResolutionState state,
        RawLaw resolved,           // RESOLVED 일 때만
        List<RawLaw> candidates,   // AMBIGUOUS 후보
        List<RawLaw> similar,      // UNVERIFIED 대조용
        String message
) {
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
}
