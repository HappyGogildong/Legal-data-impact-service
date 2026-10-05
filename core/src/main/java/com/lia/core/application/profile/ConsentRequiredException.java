package com.lia.core.application.profile;

/** 현재 처리방침 버전의 프로필 동의 없이 수정 시도 — web 이 409 {@code consent_required} 로 매핑(D61). */
public class ConsentRequiredException extends RuntimeException {

    public ConsentRequiredException() {
        super("현재 처리방침에 대한 프로필 동의가 필요합니다. PUT /api/v1/consents/profile 후 다시 시도하세요.");
    }
}
