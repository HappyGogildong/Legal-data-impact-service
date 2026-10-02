package com.lia.core.application.profile;

import java.time.OffsetDateTime;

/** 프로필 동의 상태 — {@code upToDate} = 동의한 버전이 현재 처리방침 버전과 같음. */
public record ProfileConsent(String policyVersion, OffsetDateTime consentedAt, boolean upToDate) {
}
