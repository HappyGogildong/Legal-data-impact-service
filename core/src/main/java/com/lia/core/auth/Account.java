package com.lia.core.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 계정 — 소셜 OAuth2 신원의 내부 표현(Auth, D60). 최소 PII(D41).
 *
 * <p>성명·비밀번호는 저장하지 않는다. {@code email} 은 <b>알림 수신 동의가 있을 때만</b> 존재하며
 * {@code emailConsentedAt} 과 항상 함께 있거나 함께 null 이다(D61, DB CHECK). {@code userId} 는
 * 프로필·세션의 계정 키이며 프롬프트에 주입하지 않는다.
 * 스키마: {@code db/migration/V2__accounts.sql}·{@code V4__account_email_consent.sql}. 설계: docs/components/auth/Auth.md
 */
public record Account(
        UUID userId,
        String provider,
        String providerId,
        String email,                     // nullable — 알림 동의 시에만
        OffsetDateTime emailConsentedAt,  // nullable — email 과 함께
        OffsetDateTime createdAt) {
}
