package com.lia.core.domain.analysis;

import java.time.LocalDate;
import java.util.List;

/**
 * 분석 결과 — LLM이 구조화 JSON으로 산출(프롬프트 정의서 §4, component-specs §1.3).
 *
 * <p><b>record는 관대하다</b>(LLM 출력이라 차원별 미사용 필드 생략 허용). 불변식(인용 존재성)은
 * record가 아니라 {@code FaithfulnessGate}가 강제한다 — 인용 없는 주장은 무효→재생성(D08).
 */
public record ImpactResult(
        String lawRef,                 // "LAW:{lawId}@{efYd}"
        String command,                // 차원(SUMMARY·DIFF·…)
        String summary,
        List<Claim> claims,
        List<Impact> impacts,          // Layer B(내 영향). 사용자 본인 관점 — 타 대상군 나열 안 함
        List<Action> actions,
        EffectiveInfo effectiveInfo,
        List<String> uncertainties,
        String disclaimer,
        Meta meta
) {
    /** 주장 — 진술 + 인용키(주입 source_id) + 신뢰도. citations 비면 무효(게이트). */
    public record Claim(String statement, List<String> citations, double confidence) {}

    public record Impact(String aspect, String direction, String detail, List<String> citations) {}

    public record Action(String what, String deadline, List<String> basis) {}

    public record EffectiveInfo(String status, LocalDate effectiveDate, String enforcement) {}

    public record Meta(String model, String promptVersion, String layer) {}
}
