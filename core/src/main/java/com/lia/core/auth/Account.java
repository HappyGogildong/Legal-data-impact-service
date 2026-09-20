package com.lia.core.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 계정 — 소셜 OAuth2 신원의 내부 표현(Auth, D60). 최소 PII(D41).
 *
 * <p>성명·비밀번호는 저장하지 않는다. {@code email} 은 알림 채널로만 보관(nullable —
 * IdP 미제공·미동의 시 없음). {@code userId} 는 프로필·세션의 계정 키이며 프롬프트에 주입하지 않는다.
 * 스키마: {@code db/migration/V2__accounts.sql}. 설계: docs/components/auth/Auth.md
 */
public record Account(
        UUID userId,
        String provider,
        String providerId,
        String email,          // nullable
        OffsetDateTime createdAt) {
}
