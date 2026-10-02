package com.lia.core.web;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lia.core.application.account.AccountUseCase;
import com.lia.core.application.profile.ProfileConsent;
import com.lia.core.application.profile.ProfileUseCase;
import com.lia.core.auth.Account;
import com.lia.core.auth.CurrentUser;
import com.lia.core.auth.OAuth2Identity;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code /api/v1/consents} — 동의라는 행위를 프로필 수정과 분리한 엔드포인트(D61). <b>HTTP 경계만</b>:
 * 규칙은 {@link ProfileUseCase}·{@link AccountUseCase}. 프로필 동의 철회는 {@code DELETE /api/v1/profile}(= 파기).
 * 설계: docs/components/web/ConsentApi.md
 */
@RestController
@RequestMapping("/api/v1/consents")
public class ConsentController {

    private final ProfileUseCase profiles;
    private final AccountUseCase accounts;

    public ConsentController(ProfileUseCase profiles, AccountUseCase accounts) {
        this.profiles = profiles;
        this.accounts = accounts;
    }

    /** 두 동의의 상태. 동의 안 한 항목은 null. */
    @GetMapping
    public ConsentsResponse status(HttpServletRequest request) {
        UUID userId = CurrentUser.require(request);
        return new ConsentsResponse(
                profiles.consentStatus(userId).orElse(null),
                accounts.find(userId).filter(account -> account.email() != null)
                        .map(ConsentController::notificationEmail).orElse(null));
    }

    /** 프로필 수집 동의·재동의. over14 가 true 가 아니면 400. */
    @PutMapping("/profile")
    public ProfileConsent agreeProfile(@RequestBody ProfileConsentRequest body, HttpServletRequest request) {
        return profiles.agree(CurrentUser.require(request), Boolean.TRUE.equals(body.over14()));
    }

    /** 알림 이메일 수신 동의 — 세션 principal 의 IdP 이메일을 저장. 없으면 400. */
    @PutMapping("/notification-email")
    public NotificationEmailConsent agreeNotificationEmail(Authentication authentication, HttpServletRequest request) {
        Account account = accounts.agreeNotificationEmail(CurrentUser.require(request), idpEmail(authentication));
        return notificationEmail(account);
    }

    /** 알림 동의 철회. 멱등. */
    @DeleteMapping("/notification-email")
    public ResponseEntity<Void> withdrawNotificationEmail(HttpServletRequest request) {
        accounts.withdrawNotificationEmail(CurrentUser.require(request));
        return ResponseEntity.noContent().build();
    }

    /** 세션 principal 의 IdP 이메일 — 로그인 핸들러와 같은 provider 분기(OAuth2Identity). OAuth2 가 아니면 null. */
    private static String idpEmail(Authentication authentication) {
        return authentication instanceof OAuth2AuthenticationToken token ? OAuth2Identity.of(token).email() : null;
    }

    private static NotificationEmailConsent notificationEmail(Account account) {
        return new NotificationEmailConsent(account.email(), account.emailConsentedAt());
    }

    public record ProfileConsentRequest(Boolean over14) {}

    public record NotificationEmailConsent(String email, OffsetDateTime consentedAt) {}

    public record ConsentsResponse(ProfileConsent profile, NotificationEmailConsent notificationEmail) {}
}
