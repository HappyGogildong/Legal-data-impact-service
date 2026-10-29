package com.lia.core.pipeline.dispatch;

/**
 * 못 채운 차원의 사유 — 웹이 사유별 안내(로그인·프로필 유도, 분석 준비 중 …)를 고르는 <b>코드</b>와
 * 사람이 읽는 설명. 설명은 로그·디버그용이고 화면 문구는 웹이 코드로 정한다.
 *
 * @param reason  사유 코드
 * @param message 설명(대상 법령·차원 등 맥락 포함)
 */
public record Unmet(Reason reason, String message) {

    public enum Reason {
        /** 해소된 시행예정본이 아직 정본에 없다(배치 적재 전). */
        LAW_NOT_LOADED,
        /** 개정본인데 대조할 시행중본이 없다(데이터 이상, fail-closed). */
        BASELINE_NOT_LOADED,
        /** 개인화 차원(Layer B)인데 프로필이 없다 — 익명이거나 프로필 미입력. */
        PROFILE_REQUIRED,
        /** 개인화 차원(Layer B)인데 프로필 동의가 옛 처리방침 버전이다 — 재동의 필요(D69). */
        CONSENT_OUTDATED,
        /** 오늘 개인화 분석 한도를 다 썼다(D69). */
        DAILY_LIMIT,
        /** 재생성 후에도 인용 검증을 통과하지 못했다 — 환각을 내보내지 않는다(D69, Layer A·B 공통). */
        INSUFFICIENT_GROUNDING,
        /** 그 차원·경로를 아직 구현하지 않았다(핸들러 없음). */
        NOT_IMPLEMENTED
    }
}
