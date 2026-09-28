package com.lia.core.web;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.lia.core.application.account.AccountUseCase;
import com.lia.core.auth.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /api/v1/account} — 계정 조회·삭제(파기, D41·D61). <b>HTTP 경계만</b>: 삭제는 {@link AccountUseCase},
 * 세션 무효화·쿠키 삭제는 HTTP 관심사라 여기서(SecurityConfig 로그아웃과 같은 처리). 설계: docs/components/web/AccountApi.md
 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    /** 로그아웃과 같은 정리 — SecurityContext 비우고 세션 무효화 + JSESSIONID 만료. */
    private static final LogoutHandler SESSION_TEARDOWN = new CompositeLogoutHandler(
            new SecurityContextLogoutHandler(), new CookieClearingLogoutHandler("JSESSIONID"));

    private final AccountUseCase accounts;

    public AccountController(AccountUseCase accounts) {
        this.accounts = accounts;
    }

    /** provider·가입일만(userId·email 없음). 세션이 가리키는 계정이 없으면 401 — SPA 로그인 상태 확인 겸용. */
    @GetMapping
    public AccountResponse get(HttpServletRequest request) {
        return accounts.find(CurrentUser.require(request))
                .map(account -> new AccountResponse(account.provider(), account.createdAt()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "세션이 가리키는 계정이 없습니다."));
    }

    /** 계정 파기 — 프로필 cascade, 세션 무효화, 쿠키 삭제. 되돌릴 수 없다. */
    @DeleteMapping
    public ResponseEntity<Void> delete(HttpServletRequest request, HttpServletResponse response) {
        accounts.delete(CurrentUser.require(request));
        SESSION_TEARDOWN.logout(request, response, null);
        return ResponseEntity.noContent().build();
    }

    public record AccountResponse(String provider, OffsetDateTime createdAt) {}
}
