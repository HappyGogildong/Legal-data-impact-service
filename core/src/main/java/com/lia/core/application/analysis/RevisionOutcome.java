package com.lia.core.application.analysis;

import com.lia.core.pipeline.analyze.AnalyzeResponse;
import com.lia.core.pipeline.dispatch.Unmet;

/** 대응 방안 재생성 결과(D71) — 다시 만든 대응 방안 | 못 만든 사유. HTTP 타입 비의존. */
public sealed interface RevisionOutcome permits RevisionOutcome.Revised, RevisionOutcome.NotRevised {
    record Revised(AnalyzeResponse response) implements RevisionOutcome {}
    record NotRevised(Unmet unmet) implements RevisionOutcome {}
}
