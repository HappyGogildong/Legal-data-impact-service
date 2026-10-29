package com.lia.core.pipeline.dispatch;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.plan.QueryType;

/**
 * 프로필을 쓸 수 있는 요청이 Layer B 엔진 호출로 가도 되는지(D69·D71) — 디스패처의 첫 Layer B 와 상황 재생성이 같은 규칙을 쓴다.
 * 순서: 개정본인데 기준선 없음 → 하루 한도(통과하면 1회 차감 — 실패해도 환불 없음).
 */
public final class PersonalAdmission {

    private PersonalAdmission() {}

    public static Optional<Unmet> admit(UUID userId, Law law, Law baseline, PersonalizationQuota quota) {
        if (baseline == null && law.amendKind() != Law.AmendKind.제정) {
            return Optional.of(baselineMissing(law));
        }
        if (!quota.tryConsume(userId)) {
            return Optional.of(new Unmet(Unmet.Reason.DAILY_LIMIT, "오늘 개인화 분석 한도 초과"));
        }
        return Optional.empty();
    }

    public static Unmet baselineMissing(Law law) {
        return new Unmet(Unmet.Reason.BASELINE_NOT_LOADED,
                "기준선 미적재 — 개정본(" + law.amendKind() + ")인데 시행중본 없음");
    }

    public static Unmet insufficientGrounding(QueryType type) {
        return new Unmet(Unmet.Reason.INSUFFICIENT_GROUNDING, "근거 부족 — 재생성 후에도 인용 검증 실패: " + type);
    }

    public static Unmet lawNotLoaded(String lawId, LocalDate efYd) {
        return new Unmet(Unmet.Reason.LAW_NOT_LOADED, "정본 미적재: " + lawId + "@" + efYd);
    }
}
