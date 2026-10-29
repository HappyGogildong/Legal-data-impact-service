package com.lia.core.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lia.core.domain.analysis.ImpactResult;
import com.lia.core.domain.analysis.StepNote;
import com.lia.core.pipeline.dispatch.Unmet;
import com.lia.core.web.AnalysisApiResponse.CitedSource;

/**
 * 대응 방안 재생성 응답(D71) — 다시 만든 대응 방안·단계 설명·근거 원문, 또는 못 만든 사유. 값이 없는 필드는 생략.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActionRevisionApiResponse(ImpactResult action, List<StepNote> stepNotes,
                                        List<CitedSource> sources, Unmet unmet) {

    public static ActionRevisionApiResponse revised(ImpactResult action, List<StepNote> stepNotes, List<CitedSource> sources) {
        return new ActionRevisionApiResponse(action, stepNotes, sources, null);
    }

    public static ActionRevisionApiResponse notRevised(Unmet unmet) {
        return new ActionRevisionApiResponse(null, null, null, unmet);
    }
}
