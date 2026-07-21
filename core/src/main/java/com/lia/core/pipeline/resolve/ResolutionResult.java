package com.lia.core.pipeline.resolve;

import java.util.List;

import com.lia.core.pipeline.connector.RawBill;

/** SourceAnalyzer 해소 결과. */
public record ResolutionResult(
        ResolutionState state,
        RawBill resolved,           // RESOLVED 일 때만
        List<RawBill> candidates,   // AMBIGUOUS 후보
        List<RawBill> similar,      // UNVERIFIED 대조용
        String message
) {
    public static ResolutionResult resolved(RawBill bill) {
        return new ResolutionResult(ResolutionState.RESOLVED, bill, List.of(), List.of(), null);
    }

    public static ResolutionResult ambiguous(List<RawBill> candidates, String message) {
        return new ResolutionResult(ResolutionState.AMBIGUOUS, null, candidates, List.of(), message);
    }

    public static ResolutionResult notFoundYet(String message) {
        return new ResolutionResult(ResolutionState.NOT_FOUND_YET, null, List.of(), List.of(), message);
    }

    public static ResolutionResult unverified(List<RawBill> similar, String message) {
        return new ResolutionResult(ResolutionState.UNVERIFIED, null, List.of(), similar, message);
    }
}
